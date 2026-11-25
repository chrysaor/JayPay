#!/usr/bin/env python3
import json
import os
import sys


def strict(schema):
    if "properties" in schema and "additionalProperties" not in schema and not schema.get("x-kubernetes-preserve-unknown-fields"):
        schema["additionalProperties"] = False
    for child in schema.get("properties", {}).values():
        strict(child)
    for key in ("items", "additionalProperties"):
        if isinstance(schema.get(key), dict):
            strict(schema[key])
    for key in ("allOf", "anyOf", "oneOf"):
        for child in schema.get(key, []):
            strict(child)
    return schema


def main():
    manifests, out = sys.argv[1], sys.argv[2]
    written = 0
    with open(manifests, encoding="utf-8") as handle:
        for line in handle:
            manifest = json.loads(line)
            if manifest.get("kind") != "CustomResourceDefinition":
                continue
            spec = manifest["spec"]
            folder = os.path.join(out, spec["group"])
            os.makedirs(folder, exist_ok=True)
            for version in spec["versions"]:
                schema = strict(version["schema"]["openAPIV3Schema"])
                path = os.path.join(folder, f"{spec['names']['kind'].lower()}_{version['name']}.json")
                with open(path, "w", encoding="utf-8") as target:
                    json.dump(schema, target)
                written += 1
    if not written:
        sys.exit(f"crd-schemas: {manifests} has no CustomResourceDefinition")
    print(f"crd-schemas: wrote {written} schema(s) to {out}")


if __name__ == "__main__":
    main()
