#!/bin/sh
set -eu

out=$1

messages=$(mktemp)
render() {
  release=$1 chart=$2 target=$3
  shift 3
  if ! helm lint --strict --quiet "$chart" "$@" >"$messages" 2>&1 ||
    ! helm template "$release" "$chart" --namespace "$namespace" "$@" >"$target" 2>"$messages"; then
    cat "$messages" >&2
    echo "render-charts: $release from $chart $* failed" >&2
    exit 1
  fi
}

mkdir -p "$out/ci"
namespace=ci
for values in deploy/charts/*/ci/*.yaml; do
  [ -e "$values" ] || continue
  chart=${values%/ci/*}
  name=${values##*/}
  render "${chart##*/}" "$chart" "$out/ci/${chart##*/}-$name" -f "$values"
done

for env_dir in deploy/envs/*; do
  env=${env_dir##*/}
  namespace=$(sed -n 's/^namespace: *//p' "$env_dir/gitops.yaml" 2>/dev/null)
  [ -n "$namespace" ] || { echo "render-charts: $env_dir/gitops.yaml sets no namespace" >&2; exit 1; }
  mkdir -p "$out/$env"
  render gitops deploy/charts/gitops "$out/$env/gitops.yaml" -f "$env_dir/gitops.yaml"
  for workload in "$env_dir"/*/*.yaml; do
    group=${workload%/*}
    chart=${group##*/}
    name=${workload##*/}
    render "${name%.yaml}" "deploy/charts/$chart" "$out/$env/$name" -f "$env_dir/$chart.yaml" -f "$workload"
  done
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
cat >"$converter/templates/config-files.yaml" <<'EOF'
{{- range $path, $_ := .Files.Glob "rendered/*.yaml" }}
{{- range splitList "\n---\n" ($.Files.Get $path) }}
{{- with fromYaml . }}
{{- if eq (get . "kind") "ConfigMap" }}
{{- $configMap := .metadata.name }}
{{- range $key, $text := .data }}
{{- $content := fromYaml $text }}
{{- if not (hasKey $content "Error") }}
---
{{ toJson (dict "configMap" $configMap "key" $key "content" $content) }}
{{- end }}
{{- end }}
{{- end }}
{{- end }}
{{- end }}
{{- end }}
EOF
mkdir -p "$out/config"
for env_dir in "$out"/*/; do
  env=$(basename "$env_dir")
  [ "$env" = ci ] || [ "$env" = config ] && continue
  rm -rf "$converter/rendered"
  cp -R "$env_dir" "$converter/rendered"
  helm template to-json "$converter" | grep '^{' >"$converter.json"
  grep -v '^{"configMap":' "$converter.json" >"$out/$env.json"
  grep '^{"configMap":' "$converter.json" >"$out/config/$env.json" || true
done
