#!/usr/bin/env python3
import json
import os
import re
import subprocess
import sys

INTERPOLATION = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)")
PLACEHOLDER = re.compile(r"\$\{([A-Z][A-Z0-9_]*)(?::([^}]*))?\}")
GETENV = re.compile(r"getenv\(\s*\"([A-Z][A-Z0-9_]*)\"")
SOURCE_SUFFIXES = (".yml", ".yaml", ".properties", ".java")

FRAMEWORK_VARIABLES = {
    "org.springframework.boot:spring-boot-opentelemetry:": {
        "OTEL_EXPORTER_OTLP_ENDPOINT",
        "OTEL_EXPORTER_OTLP_TRACES_ENDPOINT",
        "OTEL_EXPORTER_OTLP_LOGS_ENDPOINT",
        "OTEL_EXPORTER_OTLP_METRICS_ENDPOINT",
        "OTEL_SERVICE_NAME",
        "OTEL_RESOURCE_ATTRIBUTES",
    },
    "software.amazon.awssdk:dynamodb:": {
        "AWS_ACCESS_KEY_ID",
        "AWS_SECRET_ACCESS_KEY",
        "AWS_REGION",
        "AWS_ENDPOINT_URL_DYNAMODB",
    },
}


def read(path):
    with open(path, encoding="utf-8") as handle:
        return handle.read()


def compose_config(docker_dir):
    result = subprocess.run(
        ["docker", "compose", "config", "--no-interpolate", "--format", "json"],
        cwd=docker_dir, capture_output=True, text=True,
    )
    if result.returncode != 0:
        sys.exit(f"docker compose config failed:\n{result.stderr}")
    return json.loads(result.stdout)


def source_files(*folders):
    for folder in folders:
        for current, _, names in os.walk(folder):
            for name in names:
                if name.endswith(SOURCE_SUFFIXES):
                    yield os.path.join(current, name)


def placeholders(paths):
    referenced, defaults, required = set(), {}, set()
    for path in paths:
        text = read(path)
        referenced.update(GETENV.findall(text))
        for match in PLACEHOLDER.finditer(text):
            referenced.add(match.group(1))
            if match.group(2) is None:
                required.add(match.group(1))
            else:
                defaults[match.group(1)] = match.group(2)
    return referenced, defaults, required


def framework_variables(lockfile):
    if not os.path.exists(lockfile):
        return set()
    text = read(lockfile)
    return set().union(*[names for coordinate, names in FRAMEWORK_VARIABLES.items() if coordinate in text])


def backend_service(root, name):
    service_dir = os.path.join(root, "backend", "services", name)
    common_dir = os.path.join(root, "backend", "libs", "common", "src", "main")
    referenced, defaults, required = placeholders(source_files(os.path.join(service_dir, "src", "main"), common_dir))
    referenced |= framework_variables(os.path.join(service_dir, "gradle.lockfile"))
    return referenced, defaults, required


def frontend_site(root):
    return set(INTERPOLATION.findall(read(os.path.join(root, "frontend", "site.conf.template")))), {}, set()


def main():
    root = os.path.abspath(sys.argv[1] if len(sys.argv) > 1 else ".")
    docker_dir = os.path.join(root, "infra", "docker")
    problems = []

    example = {}
    for number, line in enumerate(read(os.path.join(docker_dir, ".env.example")).splitlines(), 1):
        if "=" not in line or line.lstrip().startswith("#"):
            problems.append(f".env.example:{number}: expected KEY=value, found {line!r}")
            continue
        example[line.split("=", 1)[0]] = line.split("=", 1)[1]

    used = set(INTERPOLATION.findall(read(os.path.join(docker_dir, "compose.yaml"))))
    problems += [f"compose.yaml uses ${{{name}}} but .env.example has no {name}" for name in sorted(used - set(example))]
    problems += [f".env.example has {name} but compose.yaml never uses it" for name in sorted(set(example) - used)]

    for name, service in sorted(compose_config(docker_dir)["services"].items()):
        if os.path.isdir(os.path.join(root, "backend", "services", name)):
            referenced, defaults, required = backend_service(root, name)
        elif name.startswith("frontend-") and os.path.isdir(os.path.join(root, "frontend", "apps", name[9:])):
            referenced, defaults, required = frontend_site(root)
        else:
            continue
        environment = service.get("environment") or {}
        for key, value in sorted(environment.items()):
            if key not in referenced:
                problems.append(f"{name}: {key} is passed but nothing in the service reads it")
            elif key in defaults and value == defaults[key]:
                problems.append(f"{name}: {key}={value} repeats the default the service already has")
        for key in sorted(required - set(environment)):
            problems.append(f"{name}: the service needs {key} and compose.yaml does not pass it")

    for problem in problems:
        print(problem)
    if problems:
        print(f"check-env: {len(problems)} problem(s)")
        sys.exit(1)
    print("check-env: compose.yaml, .env.example and the services agree")


if __name__ == "__main__":
    main()
