#!/usr/bin/env python3
import json
import os
import re
import subprocess
import sys

SECRET_REFERENCE = re.compile(r"^\$\{([A-Z][A-Z0-9_]*)")
UPSTREAM = re.compile(r"\$\{(JAYPAY_[A-Z0-9_]+_URL)\}")
UPSTREAM_SERVICE = re.compile(r"^http://([a-z0-9-]+)\.")
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
        if os.path.isdir(os.path.join(root, "backend", "services", name)):
            yield name, service, set()
        elif name.startswith("frontend-") and os.path.isdir(os.path.join(root, "frontend", "apps", name[9:])):
            template = read(os.path.join(root, "frontend", "apps", name[9:], "nginx.conf.template"))
            yield name, service, set(UPSTREAM.findall(template))


def rendered_containers(manifests_path, kind):
    containers = {}
    for line in read(manifests_path).splitlines():
        manifest = json.loads(line)
        if manifest.get("kind") == kind:
            containers[manifest["metadata"]["name"]] = manifest["spec"]["template"]["spec"]["containers"][0]
    return containers


def compare_dependency(name, service, container):
    problems = []
    if service is None:
        return [f"{name}: deploy/envs/local runs the dependency {name} but compose.yaml does not"]
    if container.get("image") != service.get("image"):
        problems.append(f"{name}: image {container.get('image')} differs from {service.get('image')} in compose.yaml")
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


def compare(name, service, upstreams, container, secret_keys, deployed):
    problems = []
    compose_env = service.get("environment") or {}
    deployed_env = {}
    for entry in container.get("env") or []:
        if entry["name"] in deployed_env:
            problems.append(f"{name}: deploy/envs/local sets {entry['name']} twice")
        deployed_env[entry["name"]] = entry

    expected = set(compose_env) | upstreams
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
        if key in upstreams:
            target = UPSTREAM_SERVICE.match(entry.get("value", ""))
            if not target or target.group(1) not in deployed:
                problems.append(f"{name}: {key} points at {entry.get('value')}, which is no Service in deploy/envs/local")

    limit = container.get("resources", {}).get("limits", {}).get("memory")
    if to_bytes(limit) != to_bytes(service.get("mem_limit")):
        problems.append(f"{name}: memory limit {limit} differs from mem_limit {service.get('mem_limit')} in compose.yaml")
    return problems


def main():
    root = os.path.abspath(sys.argv[1])
    rendered_dir = sys.argv[2]
    local = os.path.join(rendered_dir, "local.json")
    deployed = rendered_containers(local, "Deployment")
    services = compose_services(os.path.join(root, "infra", "docker"))
    secret_keys = {line.split("=", 1)[0] for line in read(os.path.join(root, "infra", "docker", ".env.example")).splitlines() if "=" in line}

    problems = []
    expected = set()
    for name, service, upstreams in workloads(root, services):
        expected.add(name)
        if name not in deployed:
            problems.append(f"{name}: compose.yaml runs it but deploy/envs/local has no workload {name}")
            continue
        problems += compare(name, service, upstreams, deployed[name], secret_keys, deployed)
    for name in sorted(set(deployed) - expected):
        problems.append(f"{name}: deploy/envs/local has it but compose.yaml does not run it")
    dependencies = rendered_containers(local, "StatefulSet")
    for name, container in sorted(dependencies.items()):
        problems += compare_dependency(name, services.get(name), container)
    problems += unpulled_images(rendered_dir)

    for problem in problems:
        print(problem)
    if problems:
        print(f"check-deploy: {len(problems)} problem(s)")
        sys.exit(1)
    print(f"check-deploy: deploy/envs/local and compose.yaml agree on {len(expected)} workloads and {len(dependencies)} dependencies")


if __name__ == "__main__":
    main()
