{{- define "pulsegate.name" -}}
{{- default .Chart.Name .Values.nameOverride | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "pulsegate.fullname" -}}
{{- printf "%s-%s" .Release.Name (include "pulsegate.name" .) | trunc 63 | trimSuffix "-" -}}
{{- end -}}

{{- define "pulsegate.labels" -}}
app.kubernetes.io/name: {{ include "pulsegate.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
app.kubernetes.io/managed-by: {{ .Release.Service }}
helm.sh/chart: {{ .Chart.Name }}-{{ .Chart.Version }}
{{- end -}}

{{- define "pulsegate.selectorLabels" -}}
app.kubernetes.io/name: {{ include "pulsegate.name" . }}
app.kubernetes.io/instance: {{ .Release.Name }}
{{- end -}}
