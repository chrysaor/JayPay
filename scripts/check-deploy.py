#!/usr/bin/env python3
import json
import os
import re
import subprocess
import sys

import network_policy

SECRET_REFERENCE = re.compile(r"^\$\{([A-Z][A-Z0-9_]*)")
QUANTITY = re.compile(r"^(\d+)([a-zA-Z]*)$")
UNITS = {"": 1, "b": 1, "k": 1024, "m": 1024**2, "g": 1024**3, "Ki": 1024, "Mi": 1024**2, "Gi": 1024**3}


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def compose_services(docker_dir):
    result = subprocess.run(
        ["docker", "compose", "config", "--no-interpolate", "--format", "json"],
        cwd=docker_dir, capture_output=True, text=True,
    )
    if result.returncode != 0:
        sys.exit(f"docker compose config failed:\n{result.stderr}")
    return json.loads(result.stdout)["services"]


def to_bytes(quantity):
    match = QUANTITY.match(str(quantity))
    return int(match.group(1)) * UNITS[match.group(2)] if match and match.group(2) in UNITS else None


def workloads(root, services):
    for name, service in sorted(services.items()):
        if os.path.isdir(os.path.join(root, "backend", "services", name)) or (
            name.startswith("frontend-") and os.path.isdir(os.path.join(root, "frontend", "apps", name[9:]))
        ):
            yield name, service


def rendered_pods(manifests_path, kind):
    pods = {}
    for line in read(manifests_path).splitlines():
        manifest = json.loads(line)
        if manifest.get("kind") == kind:
            pods[manifest["metadata"]["name"]] = manifest["spec"]["template"]["spec"]
    return pods


def pods_blind_to_their_files(manifests_path):
    problems = []
    for line in read(manifests_path).splitlines():
        manifest = json.loads(line)
        if manifest.get("kind") not in ("Deployment", "StatefulSet"):
            continue
        template = manifest["spec"]["template"]
        mounted = [volume["configMap"]["name"] for volume in template["spec"].get("volumes", []) if "configMap" in volume]
        if mounted and not template["metadata"].get("annotations", {}).get("checksum/files"):
            problems.append(f"{manifest['metadata']['name']}: mounts the ConfigMap {mounted[0]} without a checksum/files annotation on its pods, so a changed file would not restart them")
    return problems


def rendered_containers(manifests_path, kind):
    return {name: pod["containers"][0] for name, pod in rendered_pods(manifests_path, kind).items()}


def compose_hardening(services):
    problems = []
    for name, service in sorted(services.items()):
        for port in service.get("ports") or []:
            if port.get("host_ip") != "127.0.0.1":
                problems.append(f"{name}: compose.yaml publishes port {port.get('published')} on {port.get('host_ip') or 'every address'}, bind it to 127.0.0.1")
        if service.get("cap_drop") != ["ALL"]:
            problems.append(f"{name}: compose.yaml must drop every capability with cap_drop: [ALL]")
        if "no-new-privileges:true" not in (service.get("security_opt") or []):
            problems.append(f"{name}: compose.yaml must set security_opt no-new-privileges:true")
        if service.get("restart") != "no" and not re.fullmatch(r"[1-9][0-9]*:[1-9][0-9]*", str(service.get("user") or "")):
            problems.append(f"{name}: compose.yaml runs it as {service.get('user') or 'the image user'}, set a numeric user:group where neither is root")
    return problems


def compare_security(name, service, pod):
    container = pod["containers"][0]
    context = {**(pod.get("securityContext") or {}), **(container.get("securityContext") or {})}
    problems = []
    user = f"{context.get('runAsUser')}:{context.get('runAsGroup')}"
    if user != service.get("user"):
        problems.append(f"{name}: deploy/envs/local runs it as {user}, compose.yaml as {service.get('user')}")
    if context.get("runAsNonRoot") is not True:
        problems.append(f"{name}: deploy/envs/local does not set runAsNonRoot")
    if (context.get("seccompProfile") or {}).get("type") != "RuntimeDefault":
        problems.append(f"{name}: deploy/envs/local does not set seccompProfile RuntimeDefault")
    if context.get("allowPrivilegeEscalation") is not False:
        problems.append(f"{name}: deploy/envs/local does not set allowPrivilegeEscalation: false")
    if (context.get("capabilities") or {}).get("drop") != ["ALL"]:
        problems.append(f"{name}: deploy/envs/local does not drop every capability")
    if bool(context.get("readOnlyRootFilesystem")) != bool(service.get("read_only")):
        problems.append(f"{name}: readOnlyRootFilesystem is {bool(context.get('readOnlyRootFilesystem'))} in deploy/envs/local, read_only is {bool(service.get('read_only'))} in compose.yaml")
    empty_dirs = {volume["name"] for volume in pod.get("volumes") or [] if "emptyDir" in volume}
    scratch = {mount["mountPath"] for mount in container.get("volumeMounts") or [] if mount["name"] in empty_dirs}
    tmpfs = {entry.split(":", 1)[0] for entry in service.get("tmpfs") or []}
    if scratch != tmpfs:
        problems.append(f"{name}: deploy/envs/local mounts emptyDir volumes at {sorted(scratch)}, compose.yaml tmpfs at {sorted(tmpfs)}")
    return problems


def compose_image(service):
    build = service.get("build")
    if not build:
        return service.get("image")
    stages = re.findall(r"^FROM\s+(\S+)", read(os.path.join(build["context"], build.get("dockerfile", "Dockerfile"))), re.MULTILINE)
    return stages[-1] if stages else None


def compare_dependency(name, service, container):
    problems = []
    if service is None:
        return [f"{name}: deploy/envs/local runs the dependency {name} but compose.yaml does not"]
    image = compose_image(service)
    if container.get("image") != image:
        problems.append(f"{name}: image {container.get('image')} differs from {image} in compose.yaml")
    limit = container.get("resources", {}).get("limits", {}).get("memory")
    if to_bytes(limit) != to_bytes(service.get("mem_limit")):
        problems.append(f"{name}: memory limit {limit} differs from mem_limit {service.get('mem_limit')} in compose.yaml")
    return problems


def unpulled_images(rendered_dir):
    problems = []
    for file_name in sorted(os.listdir(rendered_dir)):
        env, extension = os.path.splitext(file_name)
        if extension != ".json" or env == "local":
            continue
        for kind in ("Deployment", "StatefulSet"):
            for name, container in sorted(rendered_containers(os.path.join(rendered_dir, file_name), kind).items()):
                if container.get("imagePullPolicy") == "Never":
                    problems.append(f"{env}: {name} uses imagePullPolicy Never, which only deploy/envs/local may use")
    return problems


def rendered_secrets(root, rendered_dir):
    problems = []
    for env in sorted(os.listdir(os.path.join(root, "deploy", "envs"))):
        for line in read(os.path.join(rendered_dir, f"{env}.json")).splitlines():
            manifest = json.loads(line)
            if manifest.get("kind") == "Secret":
                problems.append(f"{env}: renders the Secret {manifest['metadata']['name']}, but Secrets come from outside deploy/envs")
    return problems


def repo_server_mount(rendered_dir):
    for line in read(os.path.join(rendered_dir, "argocd.json")).splitlines():
        manifest = json.loads(line)
        if manifest.get("kind") == "Deployment" and manifest["metadata"]["name"] == "argocd-repo-server":
            volumes = {volume["name"] for volume in manifest["spec"]["template"]["spec"].get("volumes", [])}
            mounts = {mount["name"] for mount in manifest["spec"]["template"]["spec"]["containers"][0].get("volumeMounts", [])}
            if "jaypay-git" in volumes & mounts:
                return []
    return ["argocd: argocd-repo-server does not mount jaypay-git, the repository that deploy/envs/local/gitops.yaml points at"]


def compare(name, service, container, secret_keys):
    problems = []
    compose_env = service.get("environment") or {}
    deployed_env = {}
    for entry in container.get("env") or []:
        if entry["name"] in deployed_env:
            problems.append(f"{name}: deploy/envs/local sets {entry['name']} twice")
        deployed_env[entry["name"]] = entry

    expected = set(compose_env)
    for key in sorted(expected - set(deployed_env)):
        problems.append(f"{name}: compose.yaml passes {key} but deploy/envs/local does not")
    for key in sorted(set(deployed_env) - expected):
        problems.append(f"{name}: deploy/envs/local passes {key} but compose.yaml does not")

    for key, entry in sorted(deployed_env.items()):
        reference = SECRET_REFERENCE.match(compose_env.get(key, ""))
        secret_key = entry.get("valueFrom", {}).get("secretKeyRef", {}).get("key")
        if reference and secret_key != reference.group(1):
            problems.append(f"{name}: {key} must come from the Secret key {reference.group(1)}")
        elif not reference and secret_key:
            problems.append(f"{name}: {key} comes from the Secret but compose.yaml sets it in plain text")
        if secret_key and secret_key not in secret_keys:
            problems.append(f"{name}: {key} reads the Secret key {secret_key}, which infra/docker/.env.example does not have")

    limit = container.get("resources", {}).get("limits", {}).get("memory")
    if to_bytes(limit) != to_bytes(service.get("mem_limit")):
        problems.append(f"{name}: memory limit {limit} differs from mem_limit {service.get('mem_limit')} in compose.yaml")
    return problems


def network_policies(root, rendered_dir, write):
    problems = []
    for env in sorted(os.listdir(os.path.join(root, "deploy", "envs"))):
        manifests = network_policy.load(os.path.join(rendered_dir, f"{env}.json"))
        config_files = network_policy.config_files(os.path.join(rendered_dir, "config", f"{env}.json"))
        namespace = re.search(r"^namespace: *(\S+)", read(os.path.join(root, "deploy", "envs", env, "gitops.yaml")), re.MULTILINE).group(1)
        derived, derive_problems = network_policy.derived_calls(manifests, config_files, namespace)
        problems += [f"{env}: {problem}" for problem in derive_problems]
        if write:
            path = os.path.join(root, "deploy", "envs", env, "network-policy", "calls.yaml")
            os.makedirs(os.path.dirname(path), exist_ok=True)
            with open(path, "w", encoding="utf-8") as handle:
                handle.write(network_policy.values_yaml(derived))
            print(f"check-deploy: wrote {os.path.relpath(path, root)} with {len(derived)} allowed calls")
            continue
        allowed, allow_problems = network_policy.allowed_calls(manifests)
        problems += [f"{env}: {problem}" for problem in allow_problems]
        for callee, port, caller in sorted(derived - allowed, key=str):
            problems.append(f"{env}: {caller} calls {callee} on port {port}, but no NetworkPolicy allows it, run make network-policy")
        for callee, port, caller in sorted(allowed - derived, key=str):
            problems.append(f"{env}: a NetworkPolicy lets {caller} reach {callee} on port {port}, but nothing in deploy/envs/{env} makes that call, run make network-policy")
    return problems


def gateway_config(root, rendered_dir, write):
    rendered = "".join(line for line in read(os.path.join(rendered_dir, "compose", "gateway.yaml")).splitlines(keepends=True) if not line.startswith("# Source: "))
    path = os.path.join(root, "infra", "docker", "gateway", "config", "gateway.yaml")
    if write:
        with open(path, "w", encoding="utf-8") as handle:
            handle.write(rendered)
        print(f"check-deploy: wrote {os.path.relpath(path, root)}")
        return []
    if not os.path.exists(path) or read(path) != rendered:
        return [f"{os.path.relpath(path, root)} differs from deploy/charts/gateway rendered with infra/docker/gateway/values.yaml, run make gateway-config"]
    return []


def report(problems, summary):
    for problem in problems:
        print(problem)
    if problems:
        print(f"check-deploy: {len(problems)} problem(s)")
        sys.exit(1)
    print(summary)


def main():
    root = os.path.abspath(sys.argv[1])
    rendered_dir = sys.argv[2]
    if sys.argv[3:] == ["--write-network-policy"]:
        report(network_policies(root, rendered_dir, write=True), "check-deploy: run make verify STEPS=charts to check the result")
        return
    if sys.argv[3:] == ["--write-gateway-config"]:
        report(gateway_config(root, rendered_dir, write=True), "check-deploy: run make verify STEPS=charts to check the result")
        return
    local = os.path.join(rendered_dir, "local.json")
    deployed_pods = rendered_pods(local, "Deployment")
    dependency_pods = rendered_pods(local, "StatefulSet")
    deployed = {name: pod["containers"][0] for name, pod in deployed_pods.items()}
    dependencies = {name: pod["containers"][0] for name, pod in dependency_pods.items()}
    services = compose_services(os.path.join(root, "infra", "docker"))
    secret_keys = {line.split("=", 1)[0] for line in read(os.path.join(root, "infra", "docker", ".env.example")).splitlines() if "=" in line}

    problems = []
    expected = set()
    for name, service in workloads(root, services):
        expected.add(name)
        if name not in deployed:
            problems.append(f"{name}: compose.yaml runs it but deploy/envs/local has no workload {name}")
            continue
        problems += compare(name, service, deployed[name], secret_keys)
        problems += compare_security(name, service, deployed_pods[name])
    for name in sorted(set(deployed) - expected):
        problems.append(f"{name}: deploy/envs/local has it but compose.yaml does not run it")
    for name, container in sorted(dependencies.items()):
        problems += compare_dependency(name, services.get(name), container)
        if name in services:
            problems += compare_security(name, services[name], dependency_pods[name])
    problems += compose_hardening(services)
    problems += pods_blind_to_their_files(local)
    problems += unpulled_images(rendered_dir)
    problems += rendered_secrets(root, rendered_dir)
    problems += repo_server_mount(rendered_dir)
    problems += network_policies(root, rendered_dir, write=False)
    problems += gateway_config(root, rendered_dir, write=False)
    report(problems, f"check-deploy: deploy/envs/local and compose.yaml agree on {len(expected)} workloads and {len(dependencies)} dependencies, and every NetworkPolicy matches the calls")


if __name__ == "__main__":
    main()
