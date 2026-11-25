#!/bin/sh
set -eu

out=$1
namespace=jaypay

render() {
  release=$1 chart=$2 target=$3
  shift 3
  if ! helm lint --strict --quiet "$chart" "$@" || ! helm template "$release" "$chart" --namespace "$namespace" "$@" >"$target"; then
    echo "render-charts: $release from $chart $* failed" >&2
    exit 1
  fi
}

mkdir -p "$out/ci"
for values in deploy/charts/*/ci/*.yaml; do
  [ -e "$values" ] || continue
  chart=${values%/ci/*}
  name=${values##*/}
  render "${chart##*/}" "$chart" "$out/ci/${chart##*/}-$name" -f "$values"
done

for workload in deploy/envs/*/*/*.yaml; do
  group=${workload%/*}
  env_dir=${group%/*}
  chart=${group##*/}
  name=${workload##*/}
  mkdir -p "$out/${env_dir##*/}"
  render "${name%.yaml}" "deploy/charts/$chart" "$out/${env_dir##*/}/$name" -f "$env_dir/$chart.yaml" -f "$workload"
done

converter=$(mktemp -d)
mkdir -p "$converter/templates"
printf 'apiVersion: v2\nname: to-json\nversion: 0.0.0\n' >"$converter/Chart.yaml"
cat >"$converter/templates/manifests.yaml" <<'EOF'
{{- range $path, $_ := .Files.Glob "rendered/*.yaml" }}
{{- range splitList "\n---\n" ($.Files.Get $path) }}
{{- with fromYaml . }}
---
{{ toJson . }}
{{- end }}
{{- end }}
{{- end }}
EOF
for env_dir in "$out"/*/; do
  env=$(basename "$env_dir")
  [ "$env" = ci ] && continue
  rm -rf "$converter/rendered"
  cp -R "$env_dir" "$converter/rendered"
  helm template to-json "$converter" | grep '^{' >"$out/$env.json"
done
