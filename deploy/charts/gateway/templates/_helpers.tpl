{{- define "gateway.labels" -}}
app.kubernetes.io/name: {{ .Release.Name }}
app.kubernetes.io/part-of: jaypay
{{- end }}

{{- define "gateway.backendRef" -}}
- group: gateway.envoyproxy.io
  kind: Backend
  name: {{ . }}
{{- end }}

{{- define "gateway.staffRoutes" -}}
{{- $names := dict }}
{{- range .Values.adminConsole.routes }}
{{- if not (hasKey $.Values.staff.roles .role) }}{{ fail (printf "the route %s %s needs the role %s, which staff.roles does not list" .method .path .role) }}{{ end }}
{{- if not (hasKey $.Values.adminConsole.services .service) }}{{ fail (printf "the route %s %s goes to %s, which adminConsole.services does not list" .method .path .service) }}{{ end }}
{{- $_ := set $names (printf "admin-%s-%s" .role .service) (dict "role" .role "service" .service) }}
{{- end }}
{{- toJson $names }}
{{- end }}
