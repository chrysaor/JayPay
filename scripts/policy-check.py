#!/usr/bin/env python3
import json
import os
import re
import subprocess
import sys

import network_policy

PROBE = "policy-probe"
OUTSIDE_NAMESPACE = "jaypay-policy-probe"
OUTSIDE_LABEL = "frontend-admin"
CONTROL = "127.0.0.1:8079"
REACH = r'''
if command -v nc >/dev/null 2>&1; then tool=nc
elif command -v bash >/dev/null 2>&1 && command -v timeout >/dev/null 2>&1; then tool=bash
elif [ -x /busybox ]; then tool=busybox
else echo untestable; exit 0; fi
for target in "$@"; do
  (
    host=${target%:*} port=${target#*:}
    if [ "$tool" = nc ]; then nc -w 2 "$host" "$port" </dev/null >/dev/null 2>&1
    elif [ "$tool" = busybox ]; then /busybox nc -w 2 "$host" "$port" </dev/null >/dev/null 2>&1
    else timeout 2 bash -c "exec 3<>/dev/tcp/$host/$port" 2>/dev/null; fi && echo "$target open" || echo "$target closed"
  ) &
done
wait
'''


def kubectl(context, *args, stdin=None, check=True):
    result = subprocess.run(["kubectl", "--context", context, *args], input=stdin, capture_output=True, text=True)
    if check and result.returncode != 0:
        sys.exit(f"policy-check: FAILED: kubectl {' '.join(args)}: {result.stderr.strip()}")
    return result


def probe_pod(namespace, label, image):
    return {
        "apiVersion": "v1",
        "kind": "Pod",
        "metadata": {"name": PROBE, "namespace": namespace, "labels": {network_policy.NAME: label}},
        "spec": {
            "automountServiceAccountToken": False,
            "enableServiceLinks": False,
            "terminationGracePeriodSeconds": 0,
            "securityContext": {"runAsNonRoot": True, "runAsUser": 65534, "runAsGroup": 65534, "seccompProfile": {"type": "RuntimeDefault"}},
            "containers": [{
                "name": PROBE,
                "image": image,
                "command": ["sh", "-c", f"httpd -p {CONTROL} && exec sleep 600"],
                "resources": {"requests": {"cpu": "10m", "memory": "16Mi"}, "limits": {"memory": "16Mi"}},
                "securityContext": {"allowPrivilegeEscalation": False, "readOnlyRootFilesystem": True, "capabilities": {"drop": ["ALL"]}},
            }],
        },
    }


def outside_callers(manifests):
    found = {}
    for manifest in manifests:
        if manifest.get("kind") != "NetworkPolicy":
            continue
        for rule in manifest["spec"].get("ingress", []):
            for peer in rule["from"]:
                if "namespaceSelector" in peer:
                    labels = peer["podSelector"]["matchLabels"]
                    namespace = peer["namespaceSelector"]["matchLabels"]["kubernetes.io/metadata.name"]
                    found[network_policy.GATEWAY] = (namespace, ",".join(f"{key}={value}" for key, value in sorted(labels.items())))
    if not found:
        sys.exit(f"policy-check: FAILED: no NetworkPolicy admits {network_policy.GATEWAY}")
    return found


def first_running_pod(context, namespace, selector):
    args = ["--namespace", namespace, "get", "pods", "--selector", selector, "--field-selector", "status.phase=Running", "--output", "json"]
    pods = [pod["metadata"]["name"] for pod in json.loads(kubectl(context, *args).stdout)["items"] if pod["metadata"].get("deletionTimestamp") is None]
    if not pods:
        sys.exit(f"policy-check: FAILED: no running pod in {namespace} matches {selector}")
    return pods[0]


def running_pods(context, namespace, selector=None):
    args = ["--namespace", namespace, "get", "pods", "--field-selector", "status.phase=Running", "--output", "json"]
    pods = {}
    for pod in json.loads(kubectl(context, *args, *(["--selector", selector] if selector else [])).stdout)["items"]:
        name = pod["metadata"].get("labels", {}).get(network_policy.NAME)
        if name and pod["metadata"].get("deletionTimestamp") is None:
            pods.setdefault(name, pod["metadata"]["name"])
    return pods


def reach(context, namespace, pod, targets):
    for shell in (["sh"], ["/busybox", "sh"]):
        result = kubectl(context, "--namespace", namespace, "exec", pod, "--", *shell, "-c", REACH, "reach", *targets, check=False)
        if result.returncode not in (126, 127):
            break
    if result.returncode != 0 or result.stdout.strip() == "untestable":
        return None
    return dict(line.rsplit(" ", 1) for line in result.stdout.splitlines() if line.strip())


def main():
    root, rendered, namespace, context = sys.argv[1:5]
    manifests = network_policy.load(rendered)
    allowed, problems = network_policy.allowed_calls(manifests)
    if problems:
        sys.exit("policy-check: FAILED: " + "; ".join(problems))
    with open(os.path.join(root, "infra", "docker", "otel-collector", "Dockerfile"), encoding="utf-8") as handle:
        image = re.search(r"^FROM (busybox:\S+)", handle.read(), re.MULTILINE).group(1)
    targets = {}
    for service, spec in sorted(network_policy.services(manifests).items()):
        for port, target in spec["ports"].items():
            if isinstance(port, int):
                targets[f"{service}.{namespace}.svc.cluster.local:{port}"] = (service, target)

    outside = outside_callers(manifests)
    kubectl(context, "apply", "--filename", "-", stdin=json.dumps({"apiVersion": "v1", "kind": "Namespace", "metadata": {"name": OUTSIDE_NAMESPACE}}))
    try:
        for probe_namespace, label in ((namespace, PROBE), (OUTSIDE_NAMESPACE, OUTSIDE_LABEL)):
            kubectl(context, "apply", "--filename", "-", stdin=json.dumps(probe_pod(probe_namespace, label, image)))
        for probe_namespace in (namespace, OUTSIDE_NAMESPACE):
            kubectl(context, "--namespace", probe_namespace, "wait", "--for=condition=Ready", f"pod/{PROBE}", "--timeout=180s")

        callers = [(name, namespace, pod, name) for name, pod in sorted(running_pods(context, namespace).items())]
        callers.append((f"a pod labelled {OUTSIDE_LABEL} in the namespace {OUTSIDE_NAMESPACE}", OUTSIDE_NAMESPACE, PROBE, None))
        stranger = callers[-1][0]
        for caller, (caller_namespace, selector) in sorted(outside.items()):
            callers.append((caller, caller_namespace, first_running_pod(context, caller_namespace, selector), caller))

        failures, untestable, opened, refused = [], [], 0, 0
        for name, pod_namespace, pod, identity in callers:
            outcome = reach(context, pod_namespace, pod, [*targets, *([CONTROL] if pod == PROBE else [])])
            if outcome is None:
                untestable.append(name)
                continue
            if pod == PROBE and outcome.get(CONTROL) != "open":
                failures.append(f"{name} cannot open its own listener on {CONTROL}, so its refused connections prove nothing")
            for target, (service, port) in targets.items():
                want = "open" if (service, port, identity) in allowed else "closed"
                got = outcome.get(target, "missing")
                if got != want:
                    failures.append(f"{name} -> {target}: {got}, expected {want}")
                elif want == "open":
                    opened += 1
                else:
                    refused += 1
            print(f"policy-check: {name}: reaches {sorted({service for target, (service, port) in targets.items() if outcome.get(target) == 'open'})}")
        required = {PROBE, "frontend-web", "frontend-admin", stranger}
        for name in sorted(required & set(untestable)):
            failures.append(f"{name} has neither nc nor bash, so the check could not run from it")
        if untestable:
            print(f"policy-check: no nc or bash in {', '.join(untestable)}, skipped as callers")
        for failure in failures:
            print(f"policy-check: {failure}")
        if failures:
            sys.exit(f"policy-check: FAILED: {len(failures)} connection(s) differ from the NetworkPolicies")
        print(f"policy-check: PASSED: {opened} allowed connections opened and {refused} other connections refused, "
              f"from {len(callers) - len(untestable)} callers to {len(targets)} Service ports")
    finally:
        kubectl(context, "--namespace", namespace, "delete", "pod", PROBE, "--ignore-not-found", "--wait=false", check=False)
        kubectl(context, "delete", "namespace", OUTSIDE_NAMESPACE, "--ignore-not-found", "--wait=false", check=False)


if __name__ == "__main__":
    main()
