{{/* Standard labels. app.kubernetes.io/name is what Services select on. */}}
{{- define "ecommerce.labels" -}}
app.kubernetes.io/name: {{ .name }}
app.kubernetes.io/part-of: ecommerce
app.kubernetes.io/component: {{ .component }}
app.kubernetes.io/managed-by: Helm
{{- end }}

{{- define "ecommerce.selector" -}}
app.kubernetes.io/name: {{ .name }}
{{- end }}
