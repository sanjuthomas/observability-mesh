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
  name: my-service-availability-sli
  displayName: Availability SLI
spec:
  description: Ratio of successful HTTP requests
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
  name: availability-burn-rate
  displayName: Availability burn rate alert
spec:
  description: Alert when error budget burn rate is high
  alertWhenBreaching: true
  alertWhenResolved: true
  alertWhenNoData: false
  conditions:
    - conditionRef: high-burn-rate
  notificationTargets:
    - targetRef: on-call-email
`,

  AlertCondition: `apiVersion: openslo/v1
kind: AlertCondition
metadata:
  name: high-burn-rate
  displayName: High burn rate
spec:
  description: Burn rate exceeds threshold
  severity: page
  condition:
    kind: burnrate
    op: gte
    threshold: 2
    lookbackWindow: 1h
    alertAfter: 5m
`,

  AlertNotificationTarget: `apiVersion: openslo/v1
kind: AlertNotificationTarget
metadata:
  name: on-call-email
  displayName: On-call email
spec:
  description: Email notification for on-call
  target: email
`
};
