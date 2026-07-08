package com.srecatalog.harness.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.srecatalog.auth.KeycloakLoginClient;
import com.srecatalog.harness.client.InstructionServiceClient;
import com.srecatalog.harness.client.PaymentServiceClient;
import com.srecatalog.harness.client.ServiceResponse;
import com.srecatalog.harness.config.HarnessProperties;
import com.srecatalog.harness.model.SessionCredentials;
import com.srecatalog.harness.seed.InstructionPayloadFactory;
import com.srecatalog.harness.seed.SeedFile;
import com.srecatalog.harness.seed.SeedUser;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;

@Component
public class HarnessHelpers {

    private static final List<String> INSTRUCTION_LOBS = List.of("FICC", "FX", "DESK_RATES");
    private static final List<String> INSTRUCTION_TYPES = List.of("SINGLE_USE", "STANDING");
    private static final Map<String, String> LOB_CURRENCIES = Map.of(
            "FICC", "USD",
            "FX", "EUR",
            "DESK_RATES", "USD");
    private static final Map<String, Set<String>> APPROVAL_MATRIX = Map.of(
            "Analyst", Set.of("Associate", "Vice President", "Managing Director", "Partner"),
            "Associate", Set.of("Vice President", "Managing Director", "Partner"),
            "Vice President", Set.of("Managing Director", "Partner"),
            "Managing Director", Set.of("Partner"));
    private static final Map<String, Double> AMOUNT_CLUB_LIMITS = Map.of(
            "UP_TO_100_MILLION_CLUB", 100_000_000.0,
            "UP_TO_1_BILLION_CLUB", 1_000_000_000.0,
            "UP_TO_100_BILLION_CLUB", 100_000_000_000.0);
    private static final List<Double> PAYMENT_AMOUNT_TIERS = List.of(
            500_000.0, 1_000_000.0, 2_000_000.0, 5_000_000.0,
            10_000_000.0, 50_000_000.0, 100_000_000.0);

    private final HarnessProperties properties;
    private final KeycloakLoginClient loginClient;
    private final InstructionServiceClient instructionClient;
    private final PaymentServiceClient paymentClient;
    private final Random random = new Random();

    public HarnessHelpers(
            HarnessProperties properties,
            KeycloakLoginClient loginClient,
            InstructionServiceClient instructionClient,
            PaymentServiceClient paymentClient) {
        this.properties = properties;
        this.loginClient = loginClient;
        this.instructionClient = instructionClient;
        this.paymentClient = paymentClient;
    }

    public enum Operation {
        CREATE, GET, LIST, SUBMIT, APPROVE, LIST_VERSIONS
    }

    public enum PaymentOperation {
        CREATE_PAYMENT, SUBMIT_PAYMENT, APPROVE_PAYMENT, REJECT_PAYMENT
    }

    public record ScenarioStep(Operation operation, String userId, boolean expectSuccess, String description) {
    }

    public record PaymentScenarioStep(
            PaymentOperation operation, String userId, boolean expectSuccess, String description) {
    }

    public record SeedPlanRow(String userId, String owningLob, String instructionType, String currency) {
    }

    public List<ScenarioStep> buildScenario() {
        return List.of(
                new ScenarioStep(Operation.CREATE, "mo-100", true, "middle office creates FICC instruction"),
                new ScenarioStep(Operation.GET, "mo-100", true, "creator reads instruction"),
                new ScenarioStep(Operation.CREATE, "ficc-201", false, "approver cannot create (ALERT)"),
                new ScenarioStep(Operation.SUBMIT, "mo-100", true, "middle office submits instruction"),
                new ScenarioStep(Operation.LIST, "mo-100", true, "middle office lists instructions"),
                new ScenarioStep(Operation.APPROVE, "mo-100", false, "creator cannot approve (ALERT)"),
                new ScenarioStep(Operation.APPROVE, "ficc-300", true, "FICC VP approves instruction"),
                new ScenarioStep(Operation.GET, "ficc-300", true, "approver reads instruction"),
                new ScenarioStep(Operation.LIST_VERSIONS, "fx-201", false, "FX user cannot read FICC versions (ALERT)"));
    }

    public List<PaymentScenarioStep> buildPaymentScenario() {
        return List.of(
                new PaymentScenarioStep(
                        PaymentOperation.CREATE_PAYMENT, "pay-101", true,
                        "middle office creates FICC payment (→ DRAFT)"),
                new PaymentScenarioStep(
                        PaymentOperation.CREATE_PAYMENT, "pay-201", false,
                        "funding approver cannot create payment (ALERT)"),
                new PaymentScenarioStep(
                        PaymentOperation.SUBMIT_PAYMENT, "pay-101", false,
                        "middle office cannot submit — not front-office LOB (ALERT)"),
                new PaymentScenarioStep(
                        PaymentOperation.SUBMIT_PAYMENT, "fo-ficc-101", true,
                        "front office submits payment for approval (→ SUBMITTED)"),
                new PaymentScenarioStep(
                        PaymentOperation.APPROVE_PAYMENT, "pay-101", false,
                        "creator cannot approve own payment (ALERT)"),
                new PaymentScenarioStep(
                        PaymentOperation.APPROVE_PAYMENT, "pay-203", false,
                        "FX-only approver cannot approve FICC payment (ALERT)"),
                new PaymentScenarioStep(
                        PaymentOperation.APPROVE_PAYMENT, "pay-201", true,
                        "FICC/FX VP approver approves payment (→ APPROVED)"));
    }

    public SessionCredentials sessionForUser(SeedFile seed, String userId) {
        String password = seed.defaults().getOrDefault("password", properties.defaultPassword());
        String domain = seed.defaults().getOrDefault("email_domain", properties.emailDomain());
        String loginName = userId + "@" + domain;
        KeycloakLoginClient.LoginResponse response = loginClient.login(loginName, password);
        return new SessionCredentials(
                response.sessionId() == null ? "" : response.sessionId(),
                response.sessionToken());
    }

    public List<SeedPlanRow> buildSeedPlan(int count, SeedFile seed) {
        List<String[]> pairs = validInstructionSeedPairs(seed);
        if (pairs.isEmpty()) {
            pairs = List.of(
                    new String[] {"mo-100", "FICC"},
                    new String[] {"mo-101", "FICC"},
                    new String[] {"mo-100", "FX"},
                    new String[] {"mo-101", "FX"});
        }
        List<SeedPlanRow> plan = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            String[] pair = pairs.get(random.nextInt(pairs.size()));
            String instructionType = INSTRUCTION_TYPES.get(random.nextInt(INSTRUCTION_TYPES.size()));
            String currency = LOB_CURRENCIES.get(pair[1]);
            plan.add(new SeedPlanRow(pair[0], pair[1], instructionType, currency));
        }
        return plan;
    }

    public List<Map.Entry<String, Double>> buildPaymentSeedPlan(int count, SeedFile seed) {
        List<SeedUser> creators = paymentCreators(seed);
        if (creators.isEmpty()) {
            return List.of(
                    Map.entry("pay-101", 1_000_000.0),
                    Map.entry("pay-102", 5_000_000.0),
                    Map.entry("pay-103", 50_000_000.0))
                    .subList(0, Math.min(count, 3));
        }
        List<Map.Entry<String, Double>> plan = new ArrayList<>();
        for (int index = 0; index < count; index++) {
            SeedUser creator = creators.get(random.nextInt(creators.size()));
            double limit = userAmountLimit(creator).orElse(1_000_000.0);
            List<Double> validAmounts = PAYMENT_AMOUNT_TIERS.stream().filter(amount -> amount <= limit).toList();
            double amount = validAmounts.isEmpty()
                    ? Math.min(limit, 1_000_000.0)
                    : validAmounts.get(random.nextInt(validAmounts.size()));
            plan.add(Map.entry(creator.userId(), amount));
        }
        return plan;
    }

    public String instructionSubmitter(SeedFile seed) {
        List<SeedUser> creators = middleOfficeCreators(seed);
        if (creators.isEmpty()) {
            return "mo-100";
        }
        return creators.get(random.nextInt(creators.size())).userId();
    }

    public String approverForInstruction(
            SeedFile seed,
            String owningLob,
            String creatorUserId,
            String creatorTitle,
            String creatorSupervisorId) {
        List<String> eligible = eligibleInstructionApprovers(
                seed, owningLob, creatorUserId, creatorTitle, creatorSupervisorId);
        if (eligible.isEmpty()) {
            return null;
        }
        return eligible.get(random.nextInt(eligible.size()));
    }

    public String approverForPayment(SeedFile seed, JsonNode payment) {
        JsonNode createdBy = payment.path("created_by");
        List<String> eligible = eligiblePaymentApprovers(
                seed,
                payment.path("owning_lob").asText(""),
                payment.path("amount").asDouble(0),
                createdBy.path("user_id").asText(""),
                textOrNull(createdBy, "supervisor_id"));
        if (eligible.isEmpty()) {
            return null;
        }
        return eligible.get(random.nextInt(eligible.size()));
    }

    public String rejectorForPayment(SeedFile seed, JsonNode payment) {
        List<String> eligible = eligiblePaymentRejectors(seed, payment.path("owning_lob").asText(""));
        if (eligible.isEmpty()) {
            return null;
        }
        return eligible.get(random.nextInt(eligible.size()));
    }

    public String paymentSubmitterForLob(SeedFile seed, String lob) {
        List<String> candidates = seed.users().stream()
                .filter(user -> user.roles().contains("PAYMENT_CREATOR") && lob.equals(user.lob()))
                .map(SeedUser::userId)
                .toList();
        if (candidates.isEmpty()) {
            throw new IllegalArgumentException("no front-office payment submitter configured for LOB " + lob);
        }
        return candidates.get(random.nextInt(candidates.size()));
    }

    public double resolvePaymentUpdateAmount(
            double currentAmount,
            String creatorUserId,
            SeedFile seed,
            Double override) {
        if (override != null && override > 0) {
            return override;
        }
        SeedUser creator = seed.users().stream()
                .filter(user -> user.userId().equals(creatorUserId))
                .findFirst()
                .orElse(null);
        double cap = creator == null ? 100_000_000.0 : userAmountLimit(creator).orElse(100_000_000.0);
        List<Double> higherTiers = PAYMENT_AMOUNT_TIERS.stream()
                .filter(amount -> amount > currentAmount && amount <= cap)
                .toList();
        if (!higherTiers.isEmpty()) {
            return higherTiers.get(random.nextInt(higherTiers.size()));
        }
        double bumped = Math.round(currentAmount * 1.25 * 100.0) / 100.0;
        if (bumped > currentAmount && bumped <= cap) {
            return bumped;
        }
        return currentAmount;
    }

    public List<JsonNode> fetchInstructions(SessionCredentials session, String status) {
        ServiceResponse response = instructionClient.listInstructions(session, status, 500);
        return parseListResponse(response, "instructions");
    }

    public List<JsonNode> fetchPayments(SessionCredentials session, String status) {
        ServiceResponse response = paymentClient.listPayments(session, status, 500);
        return parseListResponse(response, "payments", "items");
    }

    public List<JsonNode> fetchApprovedInstructions(SessionCredentials session) {
        return fetchInstructions(session, "APPROVED");
    }

    public Map<String, Object> buildInstructionPayload(String owningLob, String instructionType, String currency) {
        return InstructionPayloadFactory.build(owningLob, instructionType, currency);
    }

    public Comparator<JsonNode> instructionApprovalSortKey(SeedFile seed) {
        return Comparator
                .comparingInt((JsonNode instruction) -> {
                    JsonNode createdBy = instruction.path("created_by");
                    String approver = approverForInstruction(
                            seed,
                            instruction.path("owning_lob").asText(""),
                            createdBy.path("user_id").asText(""),
                            createdBy.path("title").asText(""),
                            textOrNull(createdBy, "supervisor_id"));
                    return approver == null ? 1 : 0;
                })
                .thenComparing(node -> node.path("instruction_id").asText(""));
    }

    public Comparator<JsonNode> paymentApprovalSortKey(SeedFile seed) {
        return Comparator
                .comparingInt((JsonNode payment) -> approverForPayment(seed, payment) == null ? 1 : 0)
                .thenComparing(node -> node.path("payment_id").asText(""));
    }

    private List<String[]> validInstructionSeedPairs(SeedFile seed) {
        List<String[]> pairs = new ArrayList<>();
        for (SeedUser creator : middleOfficeCreators(seed)) {
            for (String owningLob : INSTRUCTION_LOBS) {
                if (!eligibleInstructionApprovers(
                        seed,
                        owningLob,
                        creator.userId(),
                        creator.title(),
                        creator.supervisorId()).isEmpty()) {
                    pairs.add(new String[] {creator.userId(), owningLob});
                }
            }
        }
        return pairs;
    }

    private List<SeedUser> middleOfficeCreators(SeedFile seed) {
        return seed.users().stream()
                .filter(user -> user.roles().contains("INSTRUCTION_CREATOR"))
                .toList();
    }

    private List<SeedUser> paymentCreators(SeedFile seed) {
        return seed.users().stream()
                .filter(user -> user.roles().contains("PAYMENT_CREATOR")
                        && user.groups().contains("MIDDLE_OFFICE"))
                .toList();
    }

    private List<SeedUser> fundingApprovers(SeedFile seed) {
        return seed.users().stream()
                .filter(user -> user.roles().contains("FUNDING_APPROVER")
                        && user.groups().contains("MIDDLE_OFFICE"))
                .toList();
    }

    private List<String> eligibleInstructionApprovers(
            SeedFile seed,
            String owningLob,
            String creatorUserId,
            String creatorTitle,
            String creatorSupervisorId) {
        Set<String> allowedTitles = APPROVAL_MATRIX.getOrDefault(creatorTitle, Set.of());
        List<String> eligible = new ArrayList<>();
        for (SeedUser approver : seed.users()) {
            if (!approver.roles().contains("INSTRUCTION_APPROVER") || !owningLob.equals(approver.lob())) {
                continue;
            }
            if (!allowedTitles.contains(approver.title())) {
                continue;
            }
            if (approver.userId().equals(creatorUserId)) {
                continue;
            }
            if (creatorSupervisorId != null && creatorSupervisorId.equals(approver.userId())) {
                continue;
            }
            if (approver.supervisorId() != null && approver.supervisorId().equals(creatorUserId)) {
                continue;
            }
            eligible.add(approver.userId());
        }
        return eligible;
    }

    private List<String> eligiblePaymentApprovers(
            SeedFile seed,
            String owningLob,
            double amount,
            String creatorUserId,
            String creatorSupervisorId) {
        List<String> eligible = new ArrayList<>();
        for (SeedUser approver : fundingApprovers(seed)) {
            if (!approver.coveringLobs().contains(owningLob)) {
                continue;
            }
            Double limit = userAmountLimit(approver).orElse(null);
            if (limit == null || amount > limit) {
                continue;
            }
            if (approver.userId().equals(creatorUserId)) {
                continue;
            }
            if (approver.supervisorId() != null && approver.supervisorId().equals(creatorUserId)) {
                continue;
            }
            eligible.add(approver.userId());
        }
        return eligible;
    }

    private List<String> eligiblePaymentRejectors(SeedFile seed, String owningLob) {
        return fundingApprovers(seed).stream()
                .filter(approver -> approver.coveringLobs().contains(owningLob))
                .map(SeedUser::userId)
                .toList();
    }

    private java.util.Optional<Double> userAmountLimit(SeedUser user) {
        return user.groups().stream()
                .filter(AMOUNT_CLUB_LIMITS::containsKey)
                .map(AMOUNT_CLUB_LIMITS::get)
                .max(Double::compareTo);
    }

    private static List<JsonNode> parseListResponse(ServiceResponse response, String... wrappedKeys) {
        if (response == null || !response.isSuccess() || response.json() == null) {
            return List.of();
        }
        JsonNode json = response.json();
        if (json.isArray()) {
            return toNodeList(json);
        }
        for (String key : wrappedKeys) {
            JsonNode wrapped = json.path(key);
            if (wrapped.isArray()) {
                return toNodeList(wrapped);
            }
        }
        return List.of();
    }

    private static List<JsonNode> toNodeList(JsonNode arrayNode) {
        if (!arrayNode.isArray()) {
            return List.of();
        }
        List<JsonNode> nodes = new ArrayList<>();
        arrayNode.forEach(nodes::add);
        return nodes;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        if (value.isMissingNode() || value.isNull()) {
            return null;
        }
        String text = value.asText();
        return text.isBlank() ? null : text;
    }
}
