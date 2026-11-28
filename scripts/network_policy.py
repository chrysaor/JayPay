import json
import re

GATEWAY = "the gateway"
GATEWAY_POD_LABEL = "gateway.envoyproxy.io/owning-gateway-name"
NAME = "app.kubernetes.io/name"
MANAGEMENT_PORT = "management"
SCRAPER = "prometheus"
ADDRESS = re.compile(r"(^|//|[@,=\s\"'])([a-z0-9][a-z0-9-]*)((?:\.[a-z0-9-]+)*)(?::(\d+))?(?=$|[/?,;\s\"'])")


def load(path):
    with open(path, encoding="utf-8") as handle:
        return [json.loads(line) for line in handle if line.strip()]


def services(manifests):
    result = {}
    for manifest in manifests:
        if manifest.get("kind") == "Service":
            ports = {}
            for port in manifest["spec"].get("ports", []):
                target = port.get("targetPort", port["port"])
                ports[port["port"]] = target
                if "name" in port:
                    ports[port["name"]] = target
            result[manifest["metadata"]["name"]] = {"selector": manifest["spec"].get("selector", {}), "ports": ports}
    return result


def config_strings(node):
    if isinstance(node, dict):
        if isinstance(node.get("host"), str) and isinstance(node.get("port"), (int, str)):
            yield f"{node['host']}:{node['port']}"
        for value in node.values():
            yield from config_strings(value)
    elif isinstance(node, list):
        for value in node:
            yield from config_strings(value)
    elif isinstance(node, str):
        yield node


def targets(text, known, namespace, bare):
    for prefix, host, suffix, port in ADDRESS.findall(text):
        if host not in known or (suffix and not f"{suffix}.".startswith(f".{namespace}.")):
            continue
        if port:
            yield host, int(port)
        elif bare or prefix == "//":
            yield host, None


def derived_calls(manifests, config_files, namespace):
    known = services(manifests)
    edges, problems = set(), []

    def add(caller, callee, port, source):
        ports = known[callee]["ports"]
        if port is None:
            edges.update((callee, target, caller) for key, target in ports.items() if isinstance(key, int))
        elif port in ports:
            edges.add((callee, ports[port], caller))
        else:
            problems.append(f"{caller}: {source} points at {callee}:{port}, a port that the Service {callee} does not expose")

    for manifest in manifests:
        if manifest.get("kind") not in ("Deployment", "StatefulSet"):
            continue
        pod = manifest["spec"]["template"]
        caller = pod["metadata"]["labels"][NAME]
        for container in pod["spec"]["containers"]:
            for entry in container.get("env") or []:
                for callee, port in targets(entry.get("value", ""), known, namespace, bare=True):
                    add(caller, callee, port, entry["name"])
        for volume in pod["spec"].get("volumes", []):
            config_map = volume.get("configMap", {}).get("name")
            for key, content in config_files.get(config_map, {}).items():
                for text in config_strings(content):
                    for callee, port in targets(text, known, namespace, bare=False):
                        add(caller, callee, port, f"{key} of the ConfigMap {config_map}")
    for manifest in manifests:
        source = f"the {manifest.get('kind')} {manifest.get('metadata', {}).get('name')}"
        if manifest.get("kind") == "Backend":
            addresses = [f"{endpoint['fqdn']['hostname']}:{endpoint['fqdn']['port']}" for endpoint in manifest["spec"].get("endpoints", []) if "fqdn" in endpoint]
        elif manifest.get("kind") == "SecurityPolicy":
            addresses = [provider["remoteJWKS"]["uri"] for provider in manifest["spec"].get("jwt", {}).get("providers", []) if "remoteJWKS" in provider]
        else:
            continue
        for address in addresses:
            for callee, port in targets(address, known, namespace, bare=False):
                add(GATEWAY, callee, port, source)
    return edges, problems


def config_files(path):
    result = {}
    for entry in load(path):
        result.setdefault(entry["configMap"], {})[entry["key"]] = entry["content"]
    return result


def allowed_calls(manifests):
    known = services(manifests)
    edges, problems, default_deny = set(), [], False
    for manifest in manifests:
        if manifest.get("kind") != "NetworkPolicy":
            continue
        name, spec = manifest["metadata"]["name"], manifest["spec"]
        if spec.get("policyTypes") != ["Ingress"]:
            problems.append(f"NetworkPolicy {name}: policyTypes must be [Ingress]")
        selector = spec.get("podSelector", {})
        if not selector:
            if spec.get("ingress"):
                problems.append(f"NetworkPolicy {name}: selects every pod and allows traffic to them")
            default_deny = True
            continue
        callee = selector.get("matchLabels", {}).get(NAME)
        if callee not in known or selector != {"matchLabels": known[callee]["selector"]}:
            problems.append(f"NetworkPolicy {name}: selects {selector}, which is not the selector of a Service")
            continue
        for rule in spec.get("ingress", []):
            if not rule.get("from") or not rule.get("ports"):
                problems.append(f"NetworkPolicy {name}: a rule without from or ports lets anything reach {callee}")
                continue
            for peer in rule["from"]:
                caller = peer.get("podSelector", {}).get("matchLabels", {}).get(NAME)
                if set(peer) == {"namespaceSelector", "podSelector"} and GATEWAY_POD_LABEL in peer["podSelector"].get("matchLabels", {}):
                    caller = GATEWAY
                elif set(peer) != {"podSelector"} or not caller:
                    problems.append(f"NetworkPolicy {name}: the peer {peer} is neither a workload nor the gateway")
                    continue
                for port in rule["ports"]:
                    if port["port"] == MANAGEMENT_PORT and caller != SCRAPER:
                        problems.append(f"NetworkPolicy {name}: lets {caller} reach the {MANAGEMENT_PORT} port of {callee}, which only {SCRAPER} may reach")
                    edges.add((callee, port["port"], caller))
    if not default_deny:
        problems.append("no NetworkPolicy denies ingress to every pod by default")
    return edges, problems


def values_yaml(edges):
    gateway, calls = {}, {}
    for callee, port, caller in sorted(edges, key=str):
        if caller == GATEWAY:
            gateway.setdefault(callee, []).append(port)
        else:
            calls.setdefault(callee, {}).setdefault(port, []).append(caller)
    lines = ["gateway:" if gateway else "gateway: {}"]
    lines += [f"  {callee}: [{', '.join(str(port) for port in sorted(ports, key=str))}]" for callee, ports in sorted(gateway.items())]
    lines.append("calls:")
    for callee, ports in sorted(calls.items()):
        lines.append(f"  {callee}:")
        lines += [f"    {port}: [{', '.join(sorted(callers))}]" for port, callers in sorted(ports.items(), key=lambda item: str(item[0]))]
    return "\n".join(lines) + "\n"
