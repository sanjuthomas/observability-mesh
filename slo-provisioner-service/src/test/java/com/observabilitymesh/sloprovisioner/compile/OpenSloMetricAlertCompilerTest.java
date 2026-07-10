package com.observabilitymesh.sloprovisioner.compile;

import com.observabilitymesh.sloprovisioner.model.OpenSloDocumentView;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OpenSloMetricAlertCompilerTest {

    @Test
    void compilesPaymentApprovalSecurityAlert() {
        OpenSloDocumentView sli = document(
                "SLI",
                "payment-approval-security-sli",
                Map.of(
                        "description", "Payment APPROVE policy denials with ALERT severity",
                        "thresholdMetric", Map.of(
                                "metricSource", Map.of(
                                        "metricSourceRef", "payment-prometheus",
                                        "spec", Map.of(
                                                "query",
                                                "sum(increase(payment_security_events_total{action=\"APPROVE\",severity=\"ALERT\"}[5m]))")))));

        Map<String, Object> conditionContent = new LinkedHashMap<>();
        conditionContent.put("apiVersion", "openslo/v1");
        conditionContent.put("kind", "AlertCondition");
        conditionContent.put("metadata", Map.of(
                "name", "payment-approval-security-condition",
                "annotations", Map.of(
                        OpenSloMetricAlertCompiler.METRIC_THRESHOLD_ANNOTATION,
                        OpenSloMetricAlertCompiler.METRIC_THRESHOLD_VALUE,
                        OpenSloMetricAlertCompiler.SLI_REF_ANNOTATION,
                        "payment-approval-security-sli")));
        conditionContent.put("spec", Map.of(
                "severity", "page",
                "description", "Any payment APPROVE policy denial with ALERT severity",
                "condition", Map.of(
                        "kind", "burnrate",
                        "op", "gt",
                        "threshold", 0,
                        "lookbackWindow", "5m",
                        "alertAfter", "0m")));
        OpenSloDocumentView condition = view("AlertCondition", "payment-approval-security-condition", conditionContent);

        OpenSloDocumentView policy = document(
                "AlertPolicy",
                "payment-approval-security-alert",
                Map.of(
                        "description", "Email when payment approval is denied with ALERT severity",
                        "alertWhenBreaching", true,
                        "alertWhenResolved", true,
                        "alertWhenNoData", false,
                        "conditions", List.of(Map.of("conditionRef", "payment-approval-security-condition")),
                        "notificationTargets", List.of(Map.of("targetRef", "observability-mesh-email"))));

        String yaml = OpenSloMetricAlertCompiler.compile(
                policy, condition, sli, Set.of("payment-prometheus"));

        assertThat(yaml).contains("alert: \"PaymentApprovalSecurityAlert\"");
        assertThat(yaml).contains("payment_security_events_total");
        assertThat(yaml).contains("openslo_alert_policy: \"payment-approval-security-alert\"");
        assertThat(yaml).contains("for: \"0m\"");
    }

    @Test
    void compilesWithGteOperatorAndFractionalThreshold() {
        OpenSloDocumentView sli = document(
                "SLI",
                "demo-sli",
                Map.of("thresholdMetric", Map.of(
                        "metricSource", Map.of("spec", Map.of("query", "up")))));

        OpenSloDocumentView condition = conditionWithOp("gte", 0.5, "");
        OpenSloDocumentView policy = document("AlertPolicy", "demo-alert", Map.of(
                "conditions", List.of(Map.of("conditionRef", "demo-condition"))));

        String yaml = OpenSloMetricAlertCompiler.compile(policy, condition, sli, Set.of());

        assertThat(yaml).contains("up >= 0.5");
        assertThat(yaml).contains("for: \"0m\"");
        assertThat(yaml).contains("severity: \"page\"");
    }

    @Test
    void compilesLtAndLteOperators() {
        OpenSloDocumentView sli = document(
                "SLI",
                "demo-sli",
                Map.of("thresholdMetric", Map.of(
                        "metricSource", Map.of("spec", Map.of("query", "metric")))));

        OpenSloDocumentView ltCondition = conditionWithOp("lt", 1, "10m");
        OpenSloDocumentView lteCondition = conditionWithOp("lte", 2, "10m");
        OpenSloDocumentView policy = document("AlertPolicy", "demo-alert", Map.of(
                "conditions", List.of(Map.of("conditionRef", "demo-condition"))));

        assertThat(OpenSloMetricAlertCompiler.compile(policy, ltCondition, sli, Set.of())).contains("metric < 1");
        assertThat(OpenSloMetricAlertCompiler.compile(policy, lteCondition, sli, Set.of())).contains("metric <= 2");
    }

    @Test
    void rejectsWrongDocumentKinds() {
        OpenSloDocumentView sli = document("SLI", "sli", Map.of());
        OpenSloDocumentView condition = conditionWithOp("gt", 0, "5m");
        OpenSloDocumentView policy = document("AlertPolicy", "policy", Map.of());

        assertThatThrownBy(() -> OpenSloMetricAlertCompiler.compile(
                document("SLO", "policy", Map.of()), condition, sli, Set.of()))
                .isInstanceOf(OpenSloCompilationException.class)
                .hasMessageContaining("AlertPolicy");

        assertThatThrownBy(() -> OpenSloMetricAlertCompiler.compile(
                policy, document("AlertPolicy", "condition", Map.of()), sli, Set.of()))
                .isInstanceOf(OpenSloCompilationException.class)
                .hasMessageContaining("AlertCondition");

        assertThatThrownBy(() -> OpenSloMetricAlertCompiler.compile(
                policy, condition, document("AlertCondition", "sli", Map.of()), Set.of()))
                .isInstanceOf(OpenSloCompilationException.class)
                .hasMessageContaining("SLI");
    }

    @Test
    void rejectsMissingMetricThresholdAnnotation() {
        OpenSloDocumentView sli = document(
                "SLI",
                "sli",
                Map.of("thresholdMetric", Map.of(
                        "metricSource", Map.of("spec", Map.of("query", "up")))));
        OpenSloDocumentView condition = document(
                "AlertCondition",
                "condition",
                Map.of("condition", Map.of("kind", "burnrate", "op", "gt", "threshold", 0)));
        OpenSloDocumentView policy = document("AlertPolicy", "policy", Map.of());

        assertThatThrownBy(() -> OpenSloMetricAlertCompiler.compile(policy, condition, sli, Set.of()))
                .isInstanceOf(OpenSloCompilationException.class)
                .hasMessageContaining("metric-threshold");
    }

    @Test
    void rejectsUnknownDatasourceMissingQueryAndUnsupportedOp() {
        OpenSloDocumentView sli = document(
                "SLI",
                "sli",
                Map.of("thresholdMetric", Map.of(
                        "metricSource", Map.of(
                                "metricSourceRef", "unknown",
                                "spec", Map.of("query", "up")))));
        OpenSloDocumentView condition = conditionWithOp("gt", 0, "5m");
        OpenSloDocumentView policy = document("AlertPolicy", "policy", Map.of());

        assertThatThrownBy(() -> OpenSloMetricAlertCompiler.compile(policy, condition, sli, Set.of("payment-prometheus")))
                .isInstanceOf(OpenSloCompilationException.class)
                .hasMessageContaining("unknown metricSourceRef");

        OpenSloDocumentView blankQuerySli = document(
                "SLI",
                "sli",
                Map.of("thresholdMetric", Map.of("metricSource", Map.of("spec", Map.of()))));
        assertThatThrownBy(() -> OpenSloMetricAlertCompiler.compile(policy, condition, blankQuerySli, Set.of()))
                .isInstanceOf(OpenSloCompilationException.class)
                .hasMessageContaining("missing PromQL query");

        OpenSloDocumentView badOpCondition = conditionWithOp("eq", 0, "5m");
        assertThatThrownBy(() -> OpenSloMetricAlertCompiler.compile(policy, badOpCondition, sli, Set.of()))
                .isInstanceOf(OpenSloCompilationException.class)
                .hasMessageContaining("unsupported alert condition op");
    }

    @Test
    void usesDefaultSummaryWhenPolicyDescriptionMissing() {
        OpenSloDocumentView sli = document(
                "SLI",
                "sli",
                Map.of("thresholdMetric", Map.of(
                        "metricSource", Map.of("spec", Map.of("query", "up")))));
        OpenSloDocumentView condition = conditionWithOp("gt", 0, "5m");
        OpenSloDocumentView policy = document("AlertPolicy", "my-policy", Map.of());

        String yaml = OpenSloMetricAlertCompiler.compile(policy, condition, sli, Set.of());

        assertThat(yaml).contains("OpenSLO alert policy my-policy is firing");
    }

    private static OpenSloDocumentView conditionWithOp(String op, double threshold, String alertAfter) {
        Map<String, Object> conditionContent = new LinkedHashMap<>();
        conditionContent.put("apiVersion", "openslo/v1");
        conditionContent.put("kind", "AlertCondition");
        conditionContent.put("metadata", Map.of(
                "name", "demo-condition",
                "annotations", Map.of(
                        OpenSloMetricAlertCompiler.METRIC_THRESHOLD_ANNOTATION,
                        OpenSloMetricAlertCompiler.METRIC_THRESHOLD_VALUE,
                        OpenSloMetricAlertCompiler.SLI_REF_ANNOTATION,
                        "demo-sli")));
        conditionContent.put("spec", Map.of(
                "condition", Map.of(
                        "kind", "burnrate",
                        "op", op,
                        "threshold", threshold,
                        "lookbackWindow", "5m",
                        "alertAfter", alertAfter)));
        return view("AlertCondition", "demo-condition", conditionContent);
    }

    private static OpenSloDocumentView document(String kind, String name, Map<String, Object> spec) {
        Map<String, Object> content = new LinkedHashMap<>();
        content.put("apiVersion", "openslo/v1");
        content.put("kind", kind);
        content.put("metadata", Map.of("name", name));
        content.put("spec", spec);
        return view(kind, name, content);
    }

    private static OpenSloDocumentView view(String kind, String name, Map<String, Object> content) {
        return new OpenSloDocumentView(
                "id",
                "openslo/v1/" + kind + "/" + name,
                1,
                false,
                kind,
                name,
                content);
    }
}
