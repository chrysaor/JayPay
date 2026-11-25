{{- define "dependency.selectorLabels" -}}
app.kubernetes.io/name: {{ .Release.Name }}
{{- end }}

{{- define "dependency.labels" -}}
{{ include "dependency.selectorLabels" . }}
app.kubernetes.io/component: dependency
app.kubernetes.io/part-of: jaypay
helm.sh/chart: {{ printf "%s-%s" .Chart.Name .Chart.Version }}
{{- end }}

{{- define "dependency.metadata" -}}
metadata:
  name: {{ .Release.Name }}
  labels:
    {{- include "dependency.labels" . | nindent 4 }}
{{- end }}

{{- define "dependency.service" -}}
apiVersion: v1
kind: Service
{{ include "dependency.metadata" . }}
spec:
  selector:
    {{- include "dependency.selectorLabels" . | nindent 4 }}
  ports:
{{- end }}

{{- define "dependency.statefulSet" -}}
apiVersion: apps/v1
kind: StatefulSet
{{ include "dependency.metadata" . }}
spec:
  serviceName: {{ .Release.Name }}
  replicas: 1
  selector:
    matchLabels:
      {{- include "dependency.selectorLabels" . | nindent 6 }}
  template:
    metadata:
      labels:
        {{- include "dependency.labels" . | nindent 8 }}
    spec:
      automountServiceAccountToken: false
      enableServiceLinks: false
      securityContext:
        seccompProfile:
          type: RuntimeDefault
{{- end }}

{{- define "dependency.container" -}}
name: {{ .Release.Name }}
image: {{ .Values.image }}
env:
  {{- range $name, $value := .Values.env }}
  - name: {{ $name }}
    value: {{ $value | quote }}
  {{- end }}
  {{- range $name, $key := .Values.secretEnv }}
  - name: {{ $name }}
    valueFrom:
      secretKeyRef:
        name: {{ $.Values.secretName }}
        key: {{ $key }}
  {{- end }}
resources:
  {{- toYaml .Values.resources | nindent 2 }}
{{- end }}

{{- define "dependency.dataClaim" -}}
volumeClaimTemplates:
  - metadata:
      name: data
      labels:
        {{- include "dependency.labels" . | nindent 8 }}
    spec:
      accessModes: [ReadWriteOnce]
      resources:
        requests:
          storage: {{ .Values.storage }}
{{- end }}

{{- define "dependency.files" -}}
apiVersion: v1
kind: ConfigMap
metadata:
  name: {{ .root.Release.Name }}-files
  labels:
    {{- include "dependency.labels" .root | nindent 4 }}
data:
  {{- range $key, $path := .files }}
  {{ $key }}: {{ $.root.Files.Get $path | required (printf "%s is empty or missing" $path) | quote }}
  {{- end }}
{{- end }}
