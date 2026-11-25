#!/usr/bin/env python3
import json
import os
import re
import subprocess
import sys
from xml.sax.saxutils import escape

DIAGRAM_DIR = os.path.join("docs", "diagrams")
SERVICES_DOC = os.path.join("docs", "services.md")
CALLS_FILE = os.path.join("deploy", "envs", "local", "network-policy", "calls.yaml")
COMMON_LIB = os.path.join("backend", "libs", "common")
LOGGING_POINTCUT = '@Before("execution(* com.jaypay..adapter.in.web.*.*(..))")'

CALL_PURPOSES = {
    ("banking-service", "membership-service"): "member check",
    ("money-service", "membership-service"): "member check",
    ("money-service", "banking-service"): "registered account",
    ("remittance-service", "membership-service"): "sender and receiver check",
    ("remittance-service", "banking-service"): "account check, firm banking",
    ("remittance-service", "money-service"): "wallet debit, credit, refund",
    ("payment-service", "membership-service"): "payer and merchant check",
    ("payment-service", "money-service"): "wallet debit",
    ("money-aggregation-service", "membership-service"): "members by address",
    ("money-aggregation-service", "money-service"): "balances",
    ("money-query-service", "membership-service"): "member address",
    ("settlement-service", "payment-service"): "paid payments, finish settlement",
    ("settlement-service", "banking-service"): "merchant account, firm banking",
}
SITE_ROLES = {"frontend-web": "customer site", "frontend-admin": "admin console"}
STORE_PURPOSES = {"vault": "email encryption key"}

STYLE = """
.bg{fill:#ffffff}
.box{fill:#f6f8fa;stroke:#8c959f;stroke-width:1.2}
.t{fill:#1f2328;font-size:12.5px;font-weight:600;text-anchor:middle}
.s{fill:#59636e;font-size:11px;text-anchor:middle}
.l{font-size:11px}
.grid{stroke:#d1d9e0;stroke-width:1}
.cell{fill:#ddf4ff}
.blue{stroke:#0969da;fill:none;stroke-width:1.6}.bluef{fill:#0969da}.bluebox{fill:#ddf4ff;stroke:#0969da;stroke-width:1.4}
.orange{stroke:#bc4c00;fill:none;stroke-width:1.6}.orangef{fill:#bc4c00}.orangebox{fill:#fff1e5;stroke:#bc4c00;stroke-width:1.4}
.purple{stroke:#8250df;fill:none;stroke-width:1.6}.purplef{fill:#8250df}.purplebox{fill:#fbefff;stroke:#8250df;stroke-width:1.4}
.green{stroke:#1a7f37;fill:none;stroke-width:1.6}.greenf{fill:#1a7f37}.greenbox{fill:#dafbe1;stroke:#1a7f37;stroke-width:1.4}
.gray{stroke:#6e7781;fill:none;stroke-width:1.4}.grayf{fill:#59636e}
.dashed{stroke-dasharray:5 4}
@media (prefers-color-scheme: dark){
.bg{fill:#0d1117}
.box{fill:#161b22;stroke:#6e7681}.t{fill:#e6edf3}.s{fill:#9198a1}
.grid{stroke:#3d444d}.cell{fill:#0c2d6b}
.blue{stroke:#58a6ff}.bluef{fill:#58a6ff}.bluebox{fill:#0c2d6b;stroke:#58a6ff}
.orange{stroke:#f0883e}.orangef{fill:#f0883e}.orangebox{fill:#5a1e02;stroke:#f0883e}
.purple{stroke:#bc8cff}.purplef{fill:#bc8cff}.purplebox{fill:#2e1461;stroke:#bc8cff}
.green{stroke:#3fb950}.greenf{fill:#3fb950}.greenbox{fill:#04260f;stroke:#3fb950}
.gray{stroke:#8b949e}.grayf{fill:#9198a1}}
"""
FONT = "-apple-system,BlinkMacSystemFont,Segoe UI,Helvetica,Arial,sans-serif"


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def text_width(text, size, bold):
    return len(text) * size * (0.58 if bold else 0.55)


class Svg:
    def __init__(self, width, height, title, description):
        self.width, self.height = width, height
        self.title, self.description = title, description
        self.items, self.segments, self.labels, self.boxes, self.problems = [], [], [], [], []

    def box(self, x, y, w, h, title, subtitle=None, cls="box", rx=8):
        self.items.append(f'<rect class="{cls}" x="{x:g}" y="{y:g}" width="{w:g}" height="{h:g}" rx="{rx}"/>')
        middle = y + h / 2
        title_y = middle + 4 if subtitle is None else middle - 3
        self.items.append(f'<text class="t" x="{x + w / 2:g}" y="{title_y:g}">{escape(title)}</text>')
        if subtitle is not None:
            self.items.append(f'<text class="s" x="{x + w / 2:g}" y="{middle + 13:g}">{escape(subtitle)}</text>')
        for text, size, bold in ((title, 12.5, True), (subtitle or "", 11, False)):
            if text_width(text, size, bold) > w - 8:
                self.problems.append(f"{self.title}: '{text}' does not fit its box of width {w:g}")
        self.boxes.append((x, y, x + w, y + h, title))

    def path(self, points, cls):
        d = f"M{points[0][0]:g} {points[0][1]:g}"
        for (x0, y0), (x1, y1) in zip(points, points[1:]):
            if x0 != x1 and y0 != y1:
                raise ValueError(f"{self.title}: segment {x0},{y0} to {x1},{y1} is not orthogonal")
            d += f" H{x1:g}" if y0 == y1 else f" V{y1:g}"
            self.segments.append((min(x0, x1), min(y0, y1), max(x0, x1), max(y0, y1)))
        self.items.append(f'<path class="{cls}" d="{d}"/>')

    def head(self, x, y, direction, cls):
        dx, dy = {"down": (0, -1), "up": (0, 1), "right": (-1, 0), "left": (1, 0)}[direction]
        bx, by = x + dx * 8, y + dy * 8
        px, py = -dy * 5, dx * 5
        self.items.append(f'<path class="{cls}" d="M{bx + px:g} {by + py:g} L{bx - px:g} {by - py:g} L{x:g} {y:g} Z"/>')

    def arrow(self, points, cls, head_cls, both=False):
        def direction(a, b):
            if a[0] == b[0]:
                return "down" if b[1] > a[1] else "up"
            return "right" if b[0] > a[0] else "left"

        self.path(points, cls)
        self.head(*points[-1], direction(points[-2], points[-1]), head_cls)
        if both:
            self.head(*points[0], direction(points[1], points[0]), head_cls)

    def label(self, x, y, text, cls, anchor="start", size=11, bold=False):
        width = text_width(text, size, bold)
        left = {"start": x, "middle": x - width / 2, "end": x - width}[anchor]
        weight = ' font-weight="600"' if bold else ""
        self.items.append(f'<text class="l {cls}" text-anchor="{anchor}" x="{x:g}" y="{y:g}"{weight}>{escape(text)}</text>')
        self.labels.append((left, y - size * 0.8, left + width, y + size * 0.25, text))

    def check_overlaps(self):
        def overlap(a, b):
            return a[0] < b[2] and b[0] < a[2] and a[1] < b[3] and b[1] < a[3]

        for label in self.labels:
            for segment in self.segments:
                if overlap(label, (segment[0] - 1, segment[1] - 1, segment[2] + 1, segment[3] + 1)):
                    self.problems.append(f"{self.title}: the label '{label[4]}' lies on a line")
            for box in self.boxes:
                if overlap(label, box):
                    self.problems.append(f"{self.title}: the label '{label[4]}' overlaps the box {box[4]}")
        for i, a in enumerate(self.labels):
            for b in self.labels[i + 1:]:
                if overlap(a, b):
                    self.problems.append(f"{self.title}: the labels '{a[4]}' and '{b[4]}' overlap")
        for i, a in enumerate(self.boxes):
            for b in self.boxes[i + 1:]:
                if overlap(a, b):
                    self.problems.append(f"{self.title}: the boxes {a[4]} and {b[4]} overlap")

    def render(self):
        self.check_overlaps()
        head = (
            f'<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 {self.width:g} {self.height:g}" '
            f'width="{self.width:g}" height="{self.height:g}" role="img" aria-labelledby="title desc" font-family="{FONT}">\n'
            f'<title id="title">{escape(self.title)}</title>\n<desc id="desc">{escape(self.description)}</desc>\n'
            f"<style>{STYLE}</style>\n"
            f'<rect class="bg" x="0" y="0" width="{self.width:g}" height="{self.height:g}" rx="12"/>'
        )
        return "\n".join([head, *self.items, "</svg>"]) + "\n"


def compose_services(root):
    result = subprocess.run(
        ["docker", "compose", "config", "--no-interpolate", "--format", "json"],
        cwd=os.path.join(root, "infra", "docker"), capture_output=True, text=True,
    )
    if result.returncode != 0:
        sys.exit(f"docker compose config failed:\n{result.stderr}")
    return json.loads(result.stdout)["services"]


def read_calls(path):
    calls, section, callee = set(), None, None
    for line in read(path).splitlines():
        if re.match(r"^[a-z]+:$", line):
            section = line[:-1]
        elif match := re.match(r"^  ([a-z0-9-]+):$", line):
            callee = match.group(1)
        elif (match := re.match(r"^    ([a-z0-9-]+): \[(.*)\]$", line)) and section == "calls":
            for caller in match.group(2).split(", "):
                calls.add((caller, callee, match.group(1)))
    return calls


def flatten_yaml(text):
    values, stack = {}, []
    for line in text.splitlines():
        match = re.match(r"^( *)([A-Za-z0-9_.-]+):\s*(.*)$", line)
        if not match:
            continue
        indent, key, value = len(match.group(1)), match.group(2), match.group(3)
        while stack and stack[-1][0] >= indent:
            stack.pop()
        stack.append((indent, key))
        if value:
            default = re.match(r"^\$\{[A-Z0-9_]+:(.*)\}$", value)
            values[".".join(name for _, name in stack)] = default.group(1) if default else value
    return values


def java_sources(directory):
    for base, _, files in os.walk(os.path.join(directory, "src", "main", "java")):
        for name in sorted(files):
            if name.endswith(".java"):
                yield os.path.join(base, name), read(os.path.join(base, name))


def kafka_topics(root, service):
    directory = os.path.join(root, "backend", "services", service)
    properties = flatten_yaml(read(os.path.join(root, COMMON_LIB, "src", "main", "resources", "jaypay-common.yml")))
    properties.update(flatten_yaml(read(os.path.join(directory, "src", "main", "resources", "application.yml"))))
    sources = list(java_sources(directory))
    has_web = any(f"{os.sep}adapter{os.sep}in{os.sep}web{os.sep}" in path for path, _ in sources)
    if has_web and any('@ComponentScan("com.jaypay.common")' in text for _, text in sources):
        sources += [(path, text) for path, text in java_sources(os.path.join(root, COMMON_LIB)) if path.endswith("LoggingProducer.java")]
    produces, consumes = set(), set()
    for _, text in sources:
        consumes.update(re.findall(r'@KafkaListener\(topics = "\$\{([\w.]+)\}"', text))
        if "kafkaTemplate.send(" in text:
            produces.update(re.findall(r'@Value\("\$\{([\w.]+)\}"\) String topic', text))
    return {properties[p] for p in produces}, {properties[p] for p in consumes}


def axon_roles(root, service):
    roles = []
    for _, text in java_sources(os.path.join(root, "backend", "services", service)):
        if "@Aggregate" in text:
            roles.append("aggregate")
        elif "@Saga" in text:
            roles.append("saga")
        else:
            roles += ["event handler"] * ("@EventHandler" in text) + ["query handler"] * ("@QueryHandler" in text)
    counted = []
    for role in ("aggregate", "saga", "event handler", "query handler"):
        count = roles.count(role)
        if count:
            counted.append(role if count == 1 else f"{count} {role}s")
    return ", ".join(counted)


def batch_period(root, service):
    for _, text in java_sources(os.path.join(root, "backend", "services", service)):
        if match := re.search(r"@Scheduled\(fixedRate = (\d+)\)", text):
            return int(match.group(1)) // 1000
    return None


def model(root):
    compose = compose_services(root)
    services = sorted(os.listdir(os.path.join(root, "backend", "services")))
    sites = [f"frontend-{app}" for app in sorted(os.listdir(os.path.join(root, "frontend", "apps")))]
    workloads = services + sites
    calls = read_calls(os.path.join(root, CALLS_FILE))
    http = {(a, b) for a, b, port in calls if port == "http" and a in workloads and b in workloads}
    common = read(os.path.join(root, COMMON_LIB, "src", "main", "java", "com", "jaypay", "common", "LoggingAspect.java"))
    problems = [] if LOGGING_POINTCUT in common else [f"{COMMON_LIB}: LoggingAspect no longer uses the pointcut {LOGGING_POINTCUT}"]
    info = {}
    for name in workloads:
        entry = compose.get(name, {})
        ports = entry.get("ports") or []
        env = entry.get("environment") or {}
        url = env.get("SPRING_DATASOURCE_URL", "")
        info[name] = {
            "port": ports[0]["published"] if ports else None,
            "memory": entry.get("mem_limit"),
            "database": url.rsplit("/", 1)[1] if url.startswith("jdbc:postgresql:") else None,
            "calls": sorted(b for a, b in http if a == name),
            "called_by": sorted(a for a, b in http if b == name),
            "stores": sorted(b for a, b, _ in calls if a == name and b in ("vault", "dynamodb")),
        }
        if name in services:
            info[name]["produces"], info[name]["consumes"] = kafka_topics(root, name)
            info[name]["axon"] = axon_roles(root, name)
            info[name]["batch"] = batch_period(root, name)
        if name not in compose:
            problems.append(f"{name}: compose.yaml has no service {name}")
    table = compose.get("money-query-service", {}).get("environment", {}).get("DYNAMODB_TABLE_NAME")
    missing = sorted(http - set(CALL_PURPOSES) - {(a, b) for a, b in http if a in sites})
    stale = sorted(set(CALL_PURPOSES) - http)
    problems += [f"scripts/check-docs.py: {a} calls {b} in {CALLS_FILE}, add its purpose to CALL_PURPOSES" for a, b in missing]
    problems += [f"scripts/check-docs.py: CALL_PURPOSES lists {a} calling {b}, which {CALLS_FILE} does not" for a, b in stale]
    return {"services": services, "sites": sites, "info": info, "http": http, "dynamodb_table": table}, problems


def short(name):
    return name.removesuffix("-service")


def request_path(m):
    info = m["info"]
    web, admin = "frontend-web", "frontend-admin"
    web_targets = set(info[web]["calls"])
    admin_targets = set(info[admin]["calls"]) - {"auth-service"}
    row = request_order(m)
    batches = [s for s in m["services"] if info[s]["batch"] and not info[s]["called_by"]]
    box_w, gap, x0 = 140, 12, 16
    width, height = x0 * 2 + len(row) * box_w + (len(row) - 1) * gap, 400
    cx = {name: x0 + i * (box_w + gap) + box_w / 2 for i, name in enumerate(row)}
    sy, sh, web_bus, admin_bus = 210, 48, 124, 156
    svg = Svg(width, height, "JayPay request path",
              f"The customer site forwards /api/<service>/ to {', '.join(sorted(web_targets))}. "
              f"The admin console asks auth-service before every call and then forwards to {', '.join(sorted(admin_targets))}. "
              + " ".join(f"{b} runs a batch against {' and '.join(info[b]['calls'])}." for b in batches))

    def site_center(targets):
        return cx[row[round(sum(row.index(t) for t in targets) / len(targets) - 0.01)]]

    web_c, admin_c = site_center(web_targets), site_center(admin_targets)
    auth_x = width - 16 - 150
    svg.box(web_c - 100, 24, 200, 52, web, f"{SITE_ROLES[web]}, port {info[web]['port']}", "bluebox")
    svg.box(admin_c - 100, 24, 200, 52, admin, f"{SITE_ROLES[admin]}, port {info[admin]['port']}", "orangebox")
    svg.box(auth_x, 24, 150, 52, "auth-service", f"staff, port {info['auth-service']['port']}")
    svg.arrow([(admin_c + 100, 50), (auth_x, 50)], "orange", "orangef")
    svg.label((admin_c + 100 + auth_x) / 2, 42, "auth_request", "orangef", "middle")
    svg.label((admin_c + 100 + auth_x) / 2, 68, "staff sign-in", "orangef", "middle")

    def drop_x(name, site):
        both = name in web_targets and name in admin_targets
        return cx[name] + (0 if not both else (-14 if site == web else 14))

    web_drops = sorted(drop_x(n, web) for n in web_targets)
    admin_drops = sorted(drop_x(n, admin) for n in admin_targets)
    svg.path([(web_c, 76), (web_c, web_bus)], "blue")
    svg.path([(web_drops[0], web_bus), (web_drops[-1], web_bus)], "blue")
    for x in web_drops:
        svg.arrow([(x, web_bus), (x, sy)], "blue", "bluef")
    trunk = admin_c + 14 if admin_c + 14 in admin_drops else admin_c
    svg.path([(trunk, 76), (trunk, admin_bus)], "orange")
    start = admin_drops[0]
    for x in [x for x in web_drops if admin_drops[0] < x < admin_drops[-1]] + [admin_drops[-1]]:
        end = x - 5 if x != admin_drops[-1] else x
        svg.path([(start, admin_bus), (end, admin_bus)], "orange")
        start = x + 5
    for x in admin_drops:
        svg.arrow([(x, admin_bus), (x, sy)], "orange", "orangef")
    svg.label(web_drops[0] + 8, web_bus - 8, "/api/<service>/", "bluef")
    svg.label(admin_drops[-1] - 8, admin_bus - 8, "forwarded once auth-service allows it", "orangef", "end")
    for name in row:
        svg.box(cx[name] - box_w / 2, sy, box_w, sh, short(name), f"port {info[name]['port']}")
    bus_y = sy + sh + 40
    for batch in batches:
        targets = [cx[t] for t in info[batch]["calls"]]
        center = cx[row[round(sum(row.index(t) for t in info[batch]["calls"]) / len(targets))]]
        svg.box(center - 100, bus_y + 30, 200, sh, batch, f"batch every {info[batch]['batch']} s, port {info[batch]['port']}")
        svg.path([(center, bus_y + 30), (center, bus_y)], "gray dashed")
        svg.path([(min(targets), bus_y), (max(targets), bus_y)], "gray dashed")
        for x in targets:
            svg.arrow([(x, bus_y), (x, sy + sh)], "gray dashed", "grayf")
    return svg


def service_calls(m):
    info = m["info"]
    edges = sorted((a, b) for a, b in m["http"] if a in m["services"])
    callers = [s for s in m["services"] if info[s]["calls"]]
    order = {name: i for i, name in enumerate(request_order(m))}
    callers.sort(key=lambda s: order.get(s, len(order)))
    callees = sorted({b for _, b in edges}, key=lambda s: (-len(info[s]["called_by"]), s))
    head_w, cell_w, cell_h, x0, y0 = 196, 200, 44, 16, 16
    width = x0 * 2 + head_w + cell_w * len(callees)
    height = y0 * 2 + cell_h * (len(callers) + 1)
    svg = Svg(width, height, "JayPay calls between services",
              " ".join(f"{a} calls {b} for {CALL_PURPOSES.get((a, b), "an unknown purpose")}." for a, b in edges))
    for j, callee in enumerate(callees):
        x = x0 + head_w + j * cell_w
        svg.items.append(f'<text class="t" x="{x + cell_w / 2:g}" y="{y0 + 26:g}">{escape(callee)}</text>')
    svg.items.append(f'<text class="s" x="{x0 + head_w / 2:g}" y="{y0 + 26:g}">caller, then the service it calls</text>')
    for i, caller in enumerate(callers):
        y = y0 + cell_h * (i + 1)
        svg.items.append(f'<text class="t" x="{x0 + 8:g}" y="{y + 26:g}" style="text-anchor:start">{escape(caller)}</text>')
        for j, callee in enumerate(callees):
            if (caller, callee) not in m["http"]:
                continue
            x = x0 + head_w + j * cell_w
            svg.items.append(f'<rect class="cell" x="{x + 3:g}" y="{y + 3:g}" width="{cell_w - 6:g}" height="{cell_h - 6:g}" rx="4"/>')
            lines = CALL_PURPOSES.get((caller, callee), "unknown").split(", ")
            lines = [", ".join(lines)] if text_width(", ".join(lines), 11, False) < cell_w - 16 else [lines[0] + ",", ", ".join(lines[1:])]
            for k, text in enumerate(lines):
                ty = y + cell_h / 2 + 4 + (k - (len(lines) - 1) / 2) * 13
                svg.label(x + cell_w / 2, ty, text, "bluef", "middle")
    for i in range(len(callers) + 2):
        y = y0 + cell_h * i
        svg.items.append(f'<path class="grid" d="M{x0:g} {y:g} H{width - x0:g}"/>')
    for j in range(len(callees) + 1):
        x = x0 + head_w + j * cell_w
        svg.items.append(f'<path class="grid" d="M{x:g} {y0:g} V{height - y0:g}"/>')
    return svg


def request_order(m):
    info = m["info"]
    web, admin = set(info["frontend-web"]["calls"]), set(info["frontend-admin"]["calls"]) - {"auth-service"}
    return sorted(web - admin) + sorted(web & admin) + sorted(admin - web)


def kafka(m):
    info = m["info"]
    topics = {}
    for service in m["services"]:
        for topic in info[service]["produces"]:
            topics.setdefault(topic, (set(), set()))[0].add(service)
        for topic in info[service]["consumes"]:
            topics.setdefault(topic, (set(), set()))[1].add(service)
    requests = [t for t, (writers, readers) in topics.items()
                if len(writers) == 1 and len(readers) == 1 and info[next(iter(writers))]["port"]
                and any(topics[u] == (readers, writers) for u in topics)]
    fan_in = [t for t in topics if t not in requests and not any(topics[r] == (topics[t][1], topics[t][0]) for r in requests)]
    if len(requests) != 1 or len(fan_in) != 1 or len(topics[fan_in[0]][1]) != 1:
        return None, [f"scripts/check-docs.py: the Kafka diagram expects one request and result topic pair and one topic that many services write to, found {sorted(topics)}"]
    request = requests[0]
    requester, worker = next(iter(topics[request][0])), next(iter(topics[request][1]))
    result = next(t for t in topics if topics[t] == ({worker}, {requester}))
    log_topic = fan_in[0]
    log_consumer = next(iter(topics[log_topic][1]))
    producers = [s for s in m["services"] if s in topics[log_topic][0]]
    if requester not in producers:
        return None, [f"scripts/check-docs.py: the Kafka diagram expects {requester} to write to {log_topic} too"]
    others = [s for s in m["services"] if s in producers and s != requester]
    pitch, box_h, top = 48, 36, 24
    left_x, left_w, topic_x, topic_w, right_x, right_w = 16, 210, 300, 250, 640, 180
    width = right_x + right_w + 16
    rows = 3 + len(others)
    height = top * 2 + pitch * (rows - 1) + box_h

    def y(row):
        return top + row * pitch + box_h / 2

    svg = Svg(width, height, "JayPay Kafka topics",
              f"{requester} writes {request}, {worker} reads it and writes {result}, which {requester} reads. "
              f"{', '.join(producers)} write one record for each web request to {log_topic}, which {log_consumer} reads.")
    svg.box(left_x, top, left_w, pitch * 2 + box_h, requester)
    svg.box(right_x, top, right_w, pitch + box_h, worker)
    svg.box(topic_x, top, topic_w, box_h, request, cls="purplebox", rx=18)
    svg.box(topic_x, top + pitch, topic_w, box_h, result, cls="purplebox", rx=18)
    svg.arrow([(left_x + left_w, y(0)), (topic_x, y(0))], "purple", "purplef")
    svg.arrow([(topic_x + topic_w, y(0)), (right_x, y(0))], "purple", "purplef")
    svg.arrow([(right_x, y(1)), (topic_x + topic_w, y(1))], "purple", "purplef")
    svg.arrow([(topic_x, y(1)), (left_x + left_w, y(1))], "purple", "purplef")
    bus = (left_x + left_w + topic_x) / 2
    for i, name in enumerate(others):
        svg.box(left_x, top + (3 + i) * pitch, left_w, box_h, name)
    last = 2 + len(others)
    middle = (2 + last) // 2
    for row in range(2, last + 1):
        if row != middle:
            svg.path([(left_x + left_w, y(row)), (bus, y(row))], "purple")
    svg.path([(bus, y(2)), (bus, y(last))], "purple")
    svg.arrow([(left_x + left_w, y(middle)), (topic_x, y(middle))], "purple", "purplef")
    svg.box(topic_x, top + middle * pitch, topic_w, box_h, log_topic, cls="purplebox", rx=18)
    svg.arrow([(topic_x + topic_w, y(middle)), (right_x, y(middle))], "purple", "purplef")
    svg.box(right_x, top + middle * pitch, right_w, box_h, log_consumer)
    svg.label(topic_x + topic_w / 2, top + middle * pitch - 6, "one record per web request", "purplef", "middle")
    return svg, []


def storage(m):
    info = m["info"]
    with_db = [s for s in m["services"] if info[s]["database"]]
    vault_users = [s for s in with_db if "vault" in info[s]["stores"]]
    axon_users = [s for s in with_db if info[s]["axon"]]
    dynamo_users = [s for s in axon_users if "dynamodb" in info[s]["stores"]]
    axon_users = [s for s in axon_users if s not in dynamo_users] + dynamo_users
    rest = [s for s in with_db if s not in vault_users + axon_users]
    problems = []
    if any("dynamodb" in info[s]["stores"] for s in with_db if s not in axon_users) or set(vault_users) & set(axon_users):
        problems.append("scripts/check-docs.py: the storage diagram expects DynamoDB users to use Axon and Vault users not to")
    order, spans = [], {}
    for name in vault_users + axon_users + rest:
        spans[name] = (len(order), 2 if name in dynamo_users else 1)
        order += [name] * spans[name][1]
    pitch, box_h, top = 48, 36, 24
    store_x, store_w, service_x, service_w, pg_x, pg_w = 16, 196, 416, 210, 786, 170
    width, height = pg_x + pg_w + 16, top * 2 + pitch * (len(order) - 1) + box_h

    def y(row):
        return top + row * pitch + box_h / 2

    svg = Svg(width, height, "JayPay Axon Server and data stores",
              "; ".join(f"{s} uses Axon Server as {info[s]['axon']}" for s in axon_users) + ". "
              + " ".join(f"{s} uses the PostgreSQL database {info[s]['database']}." for s in with_db)
              + f" {', '.join(vault_users)} reads the {STORE_PURPOSES['vault']} from Vault."
              + f" {', '.join(dynamo_users)} writes the DynamoDB table {m['dynamodb_table']}.")
    for name, (row, size) in spans.items():
        svg.box(service_x, top + row * pitch, service_w, pitch * (size - 1) + box_h, name)
        center = top + row * pitch + (pitch * (size - 1) + box_h) / 2
        svg.arrow([(service_x + service_w, center), (pg_x, center)], "gray", "grayf")
        svg.label((service_x + service_w + pg_x) / 2, center - 6, info[name]["database"], "grayf", "middle")
    svg.box(pg_x, top, pg_w, height - top * 2, "PostgreSQL", "one database per service")
    first, last = spans[axon_users[0]][0], spans[axon_users[-1]][0]
    kinds = "commands, events, queries" if any("query" in info[s]["axon"] for s in axon_users) else "commands and events"
    svg.box(store_x, top + first * pitch, store_w, pitch * (last - first) + box_h, "Axon Server", kinds, "greenbox")
    for name in axon_users:
        row = spans[name][0]
        svg.arrow([(store_x + store_w, y(row)), (service_x, y(row))], "green", "greenf", both=True)
        svg.label((store_x + store_w + service_x) / 2, y(row) - 6, info[name]["axon"], "greenf", "middle")
    for name in vault_users:
        row = spans[name][0]
        svg.box(store_x, top + row * pitch, store_w, box_h, "Vault")
        svg.arrow([(service_x, y(row)), (store_x + store_w, y(row))], "gray", "grayf")
        svg.label((store_x + store_w + service_x) / 2, y(row) - 6, STORE_PURPOSES["vault"], "grayf", "middle")
    for name in dynamo_users:
        row = spans[name][0] + 1
        svg.box(store_x, top + row * pitch, store_w, box_h, "DynamoDB")
        svg.arrow([(service_x, y(row)), (store_x + store_w, y(row))], "gray", "grayf")
        svg.label((store_x + store_w + service_x) / 2, y(row) - 6, m["dynamodb_table"], "grayf", "middle")
    return svg, problems


def diagrams(m):
    problems = []
    kafka_svg, kafka_problems = kafka(m)
    storage_svg, storage_problems = storage(m)
    problems += kafka_problems + storage_problems
    result = {"request-path.svg": request_path(m), "service-calls.svg": service_calls(m)}
    if kafka_svg:
        result["kafka.svg"] = kafka_svg
    result["axon-and-storage.svg"] = storage_svg
    rendered = {name: svg.render() for name, svg in result.items()}
    for svg in result.values():
        problems += svg.problems
    return rendered, problems


def table_rows(text):
    rows = {}
    for line in text.splitlines():
        cells = [cell.strip() for cell in line.strip().strip("|").split("|")]
        if line.startswith("|") and len(cells) >= 2:
            rows[cells[0].strip("`")] = cells[1:]
    return rows


def names_in(cell, workloads):
    return sorted(set(re.findall(r"`([a-z0-9-]+)`", cell)) & set(workloads))


def expected_port(value):
    return value if value else "none"


def check_services_doc(root, m):
    path = os.path.join(root, SERVICES_DOC)
    if not os.path.exists(path):
        return [f"{SERVICES_DOC} is missing"]
    workloads = m["services"] + m["sites"]
    sections = dict(re.findall(r"^## ([a-z0-9-]+)\n(.*?)(?=^## |\Z)", read(path), re.MULTILINE | re.DOTALL))
    problems = []
    for name in workloads:
        if name not in sections:
            problems.append(f"{SERVICES_DOC}: no section ## {name}")
            continue
        rows = table_rows(sections[name])
        info = m["info"][name]
        checks = {
            "Host port": (lambda cell: re.findall(r"`(\d+)`", cell)[:1] or (["none"] if cell.startswith("none") else []), [expected_port(info["port"])] if info["port"] else ["none"]),
            "Memory limit": (lambda cell: re.findall(r"`([0-9]+[a-z]*)`", cell)[:1], [info["memory"]]),
            "Calls": (lambda cell: names_in(cell, workloads), info["calls"]),
            "Called by": (lambda cell: names_in(cell, workloads), info["called_by"]),
        }
        for key, (parse, want) in checks.items():
            if key not in rows:
                problems.append(f"{SERVICES_DOC}: {name} has no row {key}")
            elif parse(rows[key][0]) != want:
                problems.append(f"{SERVICES_DOC}: {name}: {key} says {rows[key][0]}, the sources say {', '.join(want) or 'none'}")
    for name in sections:
        if name not in workloads:
            problems.append(f"{SERVICES_DOC}: section ## {name} names no service or site")
    return problems


def check_readme(root, m):
    text = read(os.path.join(root, "README.md"))
    header = re.search(r"^\| Service \| (.*)\|$", text, re.MULTILINE)
    if not header:
        return ["README.md: no table that starts with | Service |"]
    columns = [c.strip() for c in header.group(1).split("|")]
    if "Host port" not in columns:
        return ["README.md: the Service table has no Host port column"]
    index = columns.index("Host port")
    rows = table_rows(text[header.start():])
    problems = []
    for name in m["services"] + m["sites"]:
        row = rows.get(f"[{name}](docs/services.md#{name})") or rows.get(name)
        if row is None:
            problems.append(f"README.md: the Service table has no row {name}")
        elif row[index] != expected_port(m["info"][name]["port"]):
            problems.append(f"README.md: {name}: Host port says {row[index]}, compose.yaml says {expected_port(m['info'][name]['port'])}")
    return problems


def report(problems, message):
    if problems:
        print("\n".join(problems), file=sys.stderr)
        sys.exit(1)
    print(message)


def main():
    root = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 and not sys.argv[1].startswith("-") else ".")
    write = "--write-diagrams" in sys.argv
    m, problems = model(root)
    rendered, diagram_problems = diagrams(m)
    problems += diagram_problems
    directory = os.path.join(root, DIAGRAM_DIR)
    if write:
        os.makedirs(directory, exist_ok=True)
        for name, content in rendered.items():
            with open(os.path.join(directory, name), "w", encoding="utf-8") as handle:
                handle.write(content)
        report(problems, f"check-docs: wrote {len(rendered)} diagrams to {DIAGRAM_DIR}")
        return
    for name, content in rendered.items():
        path = os.path.join(directory, name)
        if not os.path.exists(path) or read(path) != content:
            problems.append(f"{DIAGRAM_DIR}/{name} differs from what scripts/check-docs.py draws, run make diagrams")
    known = set(rendered)
    for name in sorted(os.listdir(directory)) if os.path.isdir(directory) else []:
        if name not in known:
            problems.append(f"{DIAGRAM_DIR}/{name} is not drawn by scripts/check-docs.py")
    problems += check_services_doc(root, m)
    problems += check_readme(root, m)
    report(problems, f"check-docs: {len(rendered)} diagrams, {SERVICES_DOC} and the README Service table match the sources")


if __name__ == "__main__":
    main()
