const OPEN_SLO_TEMPLATES = {
  SLO: `apiVersion: openslo/v1
kind: SLO
metadata:
  name: my-service-availability
  displayName: My Service Availability
  labels:
    team: platform
spec:
  description: Availability SLO for my service
  service: my-service
  indicator:
    metadata:
      name: my-service-availability-sli
      displayName: Availability SLI
    spec:
      ratioMetric:
        counter: true
        good:
          metricSource:
            type: Prometheus
            spec:
              query: sum(rate(http_requests_total{status=~"2.."}[5m]))
        total:
          metricSource:
            type: Prometheus
            spec:
              query: sum(rate(http_requests_total[5m]))
  timeWindow:
    - duration: 28d
      isRolling: true
  budgetingMethod: Occurrences
  objectives:
    - displayName: Availability target
      target: 0.999
`,

  SLI: `apiVersion: openslo/v1
kind: SLI
metadata:
  name: payment-approval-security-sli
  displayName: Payment approval security events
spec:
  description: Count of payment APPROVE attempts denied with ALERT severity
  thresholdMetric:
    metricSource:
      metricSourceRef: payment-prometheus
      spec:
        query: sum(increase(payment_security_events_total{action="APPROVE",severity="ALERT"}[5m]))
`,

  Service: `apiVersion: openslo/v1
kind: Service
metadata:
  name: my-service
  displayName: My Service
spec:
  description: Core API service
`,

  DataSource: `apiVersion: openslo/v1
kind: DataSource
metadata:
  name: prometheus-datasource
  displayName: Prometheus
spec:
  description: Prometheus metrics source
  type: Prometheus
  connectionDetails:
    url: http://prometheus:9090
`,

  AlertPolicy: `apiVersion: openslo/v1
kind: AlertPolicy
metadata:
  name: payment-approval-security-alert
  displayName: Payment approval security ALERT
spec:
  description: Email when a payment APPROVE attempt is denied with ALERT severity
  alertWhenBreaching: true
  alertWhenResolved: true
  alertWhenNoData: false
  conditions:
    - conditionRef: payment-approval-security-condition
  notificationTargets:
    - targetRef: observability-mesh-email
`,

  AlertCondition: `apiVersion: openslo/v1
kind: AlertCondition
metadata:
  name: payment-approval-security-condition
  displayName: Payment approval security ALERT
  annotations:
    observability-mesh.alert-type: metric-threshold
    observability-mesh.sli-ref: payment-approval-security-sli
spec:
  description: Fire when any payment APPROVE attempt is denied with ALERT severity
  severity: page
  condition:
    kind: burnrate
    op: gt
    threshold: 0
    lookbackWindow: 5m
    alertAfter: 0m
`,

  AlertNotificationTarget: `apiVersion: openslo/v1
kind: AlertNotificationTarget
metadata:
  name: observability-mesh-email
  displayName: Observability Mesh email
spec:
  description: Tenant email route via Alertmanager (set ALERTMANAGER_EMAIL_TO to your email)
  target: email
`
};
