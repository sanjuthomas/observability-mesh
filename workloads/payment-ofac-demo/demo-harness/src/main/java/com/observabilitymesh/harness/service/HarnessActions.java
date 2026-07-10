package com.observabilitymesh.harness.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.observabilitymesh.harness.client.InstructionServiceClient;
import com.observabilitymesh.harness.client.PaymentServiceClient;
import com.observabilitymesh.harness.client.ServiceResponse;
import com.observabilitymesh.harness.config.HarnessProperties;
import com.observabilitymesh.harness.model.HarnessActionResult;
import com.observabilitymesh.harness.model.SessionCredentials;
import com.observabilitymesh.harness.seed.SeedFile;
import com.observabilitymesh.harness.seed.SeedFileLoader;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

@Service
public class HarnessActions {

    private final HarnessProperties properties;
    private final SeedFileLoader seedFileLoader;
    private final HarnessHelpers helpers;
    private final InstructionServiceClient instructionClient;
    private final PaymentServiceClient paymentClient;
    private final SecurityEventCounter securityEventCounter;

    public HarnessActions(
            HarnessProperties properties,
            SeedFileLoader seedFileLoader,
            HarnessHelpers helpers,
            InstructionServiceClient instructionClient,
            PaymentServiceClient paymentClient,
            SecurityEventCounter securityEventCounter) {
        this.properties = properties;
        this.seedFileLoader = seedFileLoader;
        this.helpers = helpers;
        this.instructionClient = instructionClient;
        this.paymentClient = paymentClient;
        this.securityEventCounter = securityEventCounter;
    }

    public HarnessActionResult createInstructions(int count, SessionCredentials adminSession) {
        HarnessActionResult result = new HarnessActionResult("create_instructions", count);
        SeedFile seed = seedFileLoader.load();
        result.log("Creating " + count + " instruction(s)");

        int index = 1;
        for (HarnessHelpers.SeedPlanRow row : helpers.buildSeedPlan(count, seed)) {
            result.log("[" + index + "] create " + row.instructionType() + " " + row.owningLob()
                    + " currency=" + row.currency() + " user=" + row.userId());
            SessionCredentials session = helpers.sessionForUser(seed, row.userId());
            Map<String, Object> payload = helpers.buildInstructionPayload(
                    row.owningLob(), row.instructionType(), row.currency());
            ServiceResponse response = instructionClient.createInstruction(session, payload);
            if (response.statusCode() == 201) {
                result.recordSuccess();
                String instructionId = response.json() == null
                        ? "?"
                        : response.json().path("instruction_id").asText("?");
                result.log("  -> HTTP 201 created " + instructionId);
            } else {
                result.recordFailure();
                result.log("  -> HTTP " + response.statusCode() + " FAIL");
                appendDetail(result, response);
            }
            index++;
        }

        finalizeResult(result, "Created");
        return result;
    }

    public HarnessActionResult submitInstructions(int count, SessionCredentials adminSession) {
        HarnessActionResult result = new HarnessActionResult("submit_instructions", count);
        SeedFile seed = seedFileLoader.load();
        List<JsonNode> drafts = helpers.fetchInstructions(adminSession, "DRAFT");
        List<JsonNode> toProcess = drafts.stream().limit(count).toList();
        if (toProcess.isEmpty()) {
            result.log("No DRAFT instructions available to submit.");
            return result;
        }

        String submitterId = helpers.instructionSubmitter(seed);
        result.log("Submitting up to " + toProcess.size() + " instruction(s) as " + submitterId);
        SessionCredentials submitSession = helpers.sessionForUser(seed, submitterId);

        int index = 1;
        for (JsonNode instruction : toProcess) {
            String instructionId = instruction.path("instruction_id").asText();
            result.log("[" + index + "] " + instructionId
                    + " lob=" + instruction.path("owning_lob").asText()
                    + " status=DRAFT");
            ServiceResponse response = instructionClient.submitInstruction(submitSession, instructionId);
            recordLifecycleResponse(result, response, "submit");
            index++;
        }

        finalizeResult(result, "Submitted");
        return result;
    }

    public HarnessActionResult approveInstructions(int count, SessionCredentials adminSession) {
        HarnessActionResult result = new HarnessActionResult("approve_instructions", count);
        SeedFile seed = seedFileLoader.load();
        String submitterId = helpers.instructionSubmitter(seed);

        List<JsonNode> candidates = new java.util.ArrayList<>();
        candidates.addAll(helpers.fetchInstructions(adminSession, "DRAFT"));
        candidates.addAll(helpers.fetchInstructions(adminSession, "SUBMITTED"));
        if (candidates.isEmpty()) {
            result.log("No DRAFT or SUBMITTED instructions available to approve.");
            return result;
        }

        candidates.sort(helpers.instructionApprovalSortKey(seed));
        List<JsonNode> toProcess = candidates.stream().limit(count).toList();
        result.log("Approving up to " + toProcess.size() + " instruction(s)");

        int index = 1;
        for (JsonNode instruction : toProcess) {
            String instructionId = instruction.path("instruction_id").asText();
            String owningLob = instruction.path("owning_lob").asText();
            String status = instruction.path("status").asText();
            JsonNode createdBy = instruction.path("created_by");
            String creatorId = createdBy.path("user_id").asText();
            String creatorTitle = createdBy.path("title").asText();
            String approverId = helpers.approverForInstruction(
                    seed, owningLob, creatorId, creatorTitle, textOrNull(createdBy, "supervisor_id"));
            if (approverId == null) {
                result.recordSkip();
                result.recordFailure();
                result.log("[" + index + "] " + instructionId + " skip: no eligible approver for lob="
                        + owningLob + " creator=" + creatorId + " title=" + creatorTitle);
                index++;
                continue;
            }

            result.log("[" + index + "] " + instructionId + " lob=" + owningLob + " status=" + status
                    + " creator=" + creatorId + " title=" + creatorTitle
                    + " submit=" + submitterId + " approve=" + approverId);

            if ("DRAFT".equals(status)) {
                SessionCredentials submitSession = helpers.sessionForUser(seed, submitterId);
                ServiceResponse submitResponse = instructionClient.submitInstruction(submitSession, instructionId);
                if (!submitResponse.isSuccess()) {
                    result.recordFailure();
                    result.log("  -> submit HTTP " + submitResponse.statusCode() + " FAIL");
                    appendDetail(result, submitResponse);
                    index++;
                    continue;
                }
                result.log("  -> submit HTTP " + submitResponse.statusCode() + " OK");
            }

            SessionCredentials approveSession = helpers.sessionForUser(seed, approverId);
            ServiceResponse approveResponse = instructionClient.approveInstruction(approveSession, instructionId);
            if (approveResponse.isSuccess()) {
                result.recordSuccess();
                String finalStatus = approveResponse.json() == null
                        ? "APPROVED"
                        : approveResponse.json().path("status").asText("APPROVED");
                result.log("  -> approve HTTP " + approveResponse.statusCode() + " OK (" + finalStatus + ")");
            } else {
                result.recordFailure();
                result.log("  -> approve HTTP " + approveResponse.statusCode() + " FAIL");
                appendDetail(result, approveResponse);
            }
            index++;
        }

        finalizeResult(result, "Approved");
        return result;
    }

    public HarnessActionResult rejectInstructions(int count, SessionCredentials adminSession) {
        HarnessActionResult result = new HarnessActionResult("reject_instructions", count);
        SeedFile seed = seedFileLoader.load();
        List<JsonNode> toProcess = helpers.fetchInstructions(adminSession, "SUBMITTED").stream().limit(count).toList();
        if (toProcess.isEmpty()) {
            result.log("No SUBMITTED instructions available to reject.");
            return result;
        }

        result.log("Rejecting up to " + toProcess.size() + " instruction(s)");
        int index = 1;
        for (JsonNode instruction : toProcess) {
            String instructionId = instruction.path("instruction_id").asText();
            String owningLob = instruction.path("owning_lob").asText();
            JsonNode createdBy = instruction.path("created_by");
            String approverId = helpers.approverForInstruction(
                    seed,
                    owningLob,
                    createdBy.path("user_id").asText(),
                    createdBy.path("title").asText(),
                    textOrNull(createdBy, "supervisor_id"));
            if (approverId == null) {
                result.recordSkip();
                result.recordFailure();
                result.log("[" + index + "] " + instructionId + " skip: no eligible approver for lob="
                        + owningLob + " creator=" + createdBy.path("user_id").asText());
                index++;
                continue;
            }

            result.log("[" + index + "] " + instructionId + " lob=" + owningLob + " reject as " + approverId);
            SessionCredentials session = helpers.sessionForUser(seed, approverId);
            ServiceResponse response = instructionClient.rejectInstruction(
                    session, instructionId, "Rejected via test harness UI");
            recordLifecycleResponse(result, response, "reject");
            index++;
        }

        finalizeResult(result, "Rejected");
        return result;
    }

    public HarnessActionResult suspendInstructions(int count, SessionCredentials adminSession) {
        HarnessActionResult result = new HarnessActionResult("suspend_instructions", count);
        List<JsonNode> toProcess = helpers.fetchInstructions(adminSession, "APPROVED").stream().limit(count).toList();
        if (toProcess.isEmpty()) {
            result.log("No APPROVED instructions available to suspend.");
            return result;
        }

        result.log("Suspending up to " + toProcess.size() + " instruction(s)");
        int index = 1;
        for (JsonNode instruction : toProcess) {
            String instructionId = instruction.path("instruction_id").asText();
            result.log("[" + index + "] " + instructionId
                    + " lob=" + instruction.path("owning_lob").asText()
                    + " status=" + instruction.path("status").asText());
            ServiceResponse response = instructionClient.suspendInstruction(adminSession, instructionId);
            recordLifecycleResponse(result, response, "suspend");
            index++;
        }

        finalizeResult(result, "Suspended");
        return result;
    }

    public HarnessActionResult reactivateInstructions(int count, SessionCredentials adminSession) {
        HarnessActionResult result = new HarnessActionResult("reactivate_instructions", count);
        List<JsonNode> toProcess = helpers.fetchInstructions(adminSession, "SUSPENDED").stream().limit(count).toList();
        if (toProcess.isEmpty()) {
            result.log("No SUSPENDED instructions available to reactivate.");
            return result;
        }

        result.log("Reactivating up to " + toProcess.size() + " instruction(s)");
        int index = 1;
        for (JsonNode instruction : toProcess) {
            String instructionId = instruction.path("instruction_id").asText();
            result.log("[" + index + "] " + instructionId
                    + " lob=" + instruction.path("owning_lob").asText()
                    + " status=SUSPENDED");
            ServiceResponse response = instructionClient.reactivateInstruction(adminSession, instructionId);
            if (response.isSuccess()) {
                result.recordSuccess();
                String finalStatus = response.json() == null
                        ? "REACTIVATED"
                        : response.json().path("status").asText("REACTIVATED");
                result.log("  -> reactivate HTTP " + response.statusCode() + " OK (" + finalStatus + ")");
            } else {
                result.recordFailure();
                result.log("  -> reactivate HTTP " + response.statusCode() + " FAIL");
                appendDetail(result, response);
            }
            index++;
        }

        finalizeResult(result, "Reactivated");
        return result;
    }

    public HarnessActionResult runPolicyScenario(SessionCredentials adminSession) {
        HarnessActionResult result = new HarnessActionResult("run_policy_scenario", 1);
        SeedFile seed = seedFileLoader.load();
        String instructionId = null;
        int failures = 0;
        result.log("Running instruction lifecycle policy scenario");

        int index = 1;
        for (HarnessHelpers.ScenarioStep step : helpers.buildScenario()) {
            SessionCredentials session = helpers.sessionForUser(seed, step.userId());
            result.log("[" + index + "] " + step.description()
                    + " (user=" + step.userId() + ", op=" + step.operation()
                    + ", expect=" + (step.expectSuccess() ? "OK" : "DENY") + ")");

            ServiceResponse response = switch (step.operation()) {
                case CREATE -> {
                    Map<String, Object> payload = helpers.buildInstructionPayload("FICC", "SINGLE_USE", "USD");
                    ServiceResponse created = instructionClient.createInstruction(session, payload);
                    if (step.expectSuccess() && created.statusCode() == 201 && created.json() != null) {
                        instructionId = created.json().path("instruction_id").asText(null);
                    }
                    yield created;
                }
                case GET -> {
                    if (instructionId == null) {
                        result.log("  skip: no instruction_id");
                        yield null;
                    }
                    yield instructionClient.getInstruction(session, instructionId);
                }
                case LIST -> instructionClient.listInstructions(session, null, 500);
                case SUBMIT -> {
                    if (instructionId == null) {
                        result.log("  skip: no instruction_id");
                        yield null;
                    }
                    yield instructionClient.submitInstruction(session, instructionId);
                }
                case APPROVE -> {
                    if (instructionId == null) {
                        result.log("  skip: no instruction_id");
                        yield null;
                    }
                    yield instructionClient.approveInstruction(session, instructionId);
                }
                case LIST_VERSIONS -> {
                    if (instructionId == null) {
                        result.log("  skip: no instruction_id");
                        yield null;
                    }
                    yield instructionClient.listVersions(session, instructionId);
                }
            };

            if (response != null) {
                boolean ok = response.isSuccess() == step.expectSuccess();
                result.log("  -> HTTP " + response.statusCode() + " " + (ok ? "PASS" : "FAIL"));
                if (!ok) {
                    failures++;
                    appendDetail(result, response);
                }
            }
            index++;
        }

        result.setSucceeded(failures == 0 ? 1 : 0);
        result.setFailed(failures);
        result.setOk(failures == 0);
        result.log("Scenario finished with " + failures + " failure(s).");
        return result;
    }

    public HarnessActionResult createPayments(int count, SessionCredentials adminSession) {
        HarnessActionResult result = new HarnessActionResult("create_payments", count);
        SeedFile seed = seedFileLoader.load();
        List<JsonNode> approved = helpers.fetchApprovedInstructions(adminSession);
        List<JsonNode> standing = approved.stream()
                .filter(node -> "STANDING".equals(node.path("instruction_type").asText()))
                .toList();
        List<JsonNode> pool = standing.isEmpty() ? approved : standing;
        if (pool.isEmpty()) {
            result.log("No APPROVED instructions found. Run approve-instructions first.");
            result.setOk(false);
            return result;
        }

        String valueDate = LocalDate.now().plusDays(1).toString();
        result.log("Creating " + count + " payment(s) against " + pool.size() + " approved instruction(s)");

        int index = 1;
        for (Map.Entry<String, Double> row : helpers.buildPaymentSeedPlan(count, seed)) {
            String userId = row.getKey();
            double amount = row.getValue();
            try {
                seed.userById(userId);
            } catch (IllegalArgumentException ex) {
                result.recordFailure();
                result.log("[" + index + "] skip: unknown payment creator " + userId);
                index++;
                continue;
            }

            var creator = seed.userById(userId);
            List<JsonNode> matching = pool.stream()
                    .filter(node -> creator.coveringLobs().contains(node.path("owning_lob").asText()))
                    .toList();
            if (matching.isEmpty()) {
                result.recordFailure();
                result.log("[" + index + "] skip: no approved instruction for creator " + userId
                        + " covering " + creator.coveringLobs());
                index++;
                continue;
            }

            JsonNode instruction = matching.get(index % matching.size());
            String instructionId = instruction.path("instruction_id").asText();
            String owningLob = instruction.path("owning_lob").asText("?");
            result.log("[" + index + "] create payment  user=" + userId + "  amount="
                    + String.format("%,.0f", amount) + "  lob=" + owningLob + "  instruction="
                    + instructionId.substring(0, Math.min(8, instructionId.length())) + "…");

            SessionCredentials session = helpers.sessionForUser(seed, userId);
            ServiceResponse response = paymentClient.createPayment(session, instructionId, amount, valueDate);
            if (response.statusCode() == 201) {
                result.recordSuccess();
                String paymentId = response.json() == null
                        ? "?"
                        : response.json().path("payment_id").asText("?");
                result.log("  -> HTTP 201 created " + paymentId);
            } else {
                result.recordFailure();
                result.log("  -> HTTP " + response.statusCode() + " FAIL");
                appendDetail(result, response);
            }
            index++;
        }

        finalizeResult(result, "Created");
        return result;
    }

    public HarnessActionResult submitPayments(int count, SessionCredentials adminSession) {
        HarnessActionResult result = new HarnessActionResult("submit_payments", count);
        SeedFile seed = seedFileLoader.load();
        List<JsonNode> toProcess = helpers.fetchPayments(adminSession, "DRAFT").stream().limit(count).toList();
        if (toProcess.isEmpty()) {
            result.log("No DRAFT payments available to submit.");
            return result;
        }

        result.log("Submitting up to " + toProcess.size() + " payment(s)");
        int index = 1;
        for (JsonNode payment : toProcess) {
            String paymentId = payment.path("payment_id").asText();
            String owningLob = payment.path("owning_lob").asText("?");
            try {
                String submitterId = helpers.paymentSubmitterForLob(seed, owningLob);
                result.log("[" + index + "] " + paymentId + "  lob=" + owningLob + "  submitting as " + submitterId);
                SessionCredentials session = helpers.sessionForUser(seed, submitterId);
                ServiceResponse response = paymentClient.submitPayment(session, paymentId);
                recordLifecycleResponse(result, response, "submit");
            } catch (IllegalArgumentException ex) {
                result.recordFailure();
                result.log("[" + index + "] " + paymentId + "  lob=" + owningLob + "  skip: " + ex.getMessage());
            }
            index++;
        }

        finalizeResult(result, "Submitted");
        return result;
    }

    public HarnessActionResult updatePayments(int count, SessionCredentials adminSession, Double amount) {
        HarnessActionResult result = new HarnessActionResult("update_payments", count);
        SeedFile seed = seedFileLoader.load();
        List<JsonNode> toProcess = helpers.fetchPayments(adminSession, "DRAFT").stream().limit(count).toList();
        if (toProcess.isEmpty()) {
            result.log("No DRAFT payments available to update.");
            return result;
        }

        String amountNote = amount != null ? "amount=" + String.format("%,.0f", amount) : "auto amount bump";
        result.log("Updating up to " + toProcess.size() + " DRAFT payment(s) (" + amountNote + ")");

        int index = 1;
        for (JsonNode payment : toProcess) {
            String paymentId = payment.path("payment_id").asText();
            String instructionId = payment.path("instruction_id").asText();
            String valueDate = payment.path("value_date").asText("2026-07-01");
            double currentAmount = payment.path("amount").asDouble(0);
            JsonNode createdBy = payment.path("created_by");
            String creatorId = createdBy.path("user_id").asText(null);
            if (creatorId == null || creatorId.isBlank()) {
                result.recordFailure();
                result.log("[" + index + "] " + paymentId + " skip: missing created_by");
                index++;
                continue;
            }

            double newAmount = helpers.resolvePaymentUpdateAmount(currentAmount, creatorId, seed, amount);
            if (newAmount == currentAmount) {
                result.recordSkip();
                result.log("[" + index + "] " + paymentId + " skip: amount unchanged at "
                        + String.format("%,.0f", currentAmount));
                index++;
                continue;
            }

            result.log("[" + index + "] " + paymentId + "  user=" + creatorId + "  "
                    + String.format("%,.0f", currentAmount) + " -> " + String.format("%,.0f", newAmount));
            SessionCredentials session = helpers.sessionForUser(seed, creatorId);
            ServiceResponse response = paymentClient.updatePayment(
                    session, paymentId, instructionId, newAmount, valueDate);
            if (response.isSuccess()) {
                result.recordSuccess();
                String version = response.json() == null
                        ? "?"
                        : response.json().path("version_number").asText("?");
                double savedAmount = response.json() == null
                        ? newAmount
                        : response.json().path("amount").asDouble(newAmount);
                result.log("  -> HTTP " + response.statusCode() + " v" + version
                        + " amount=" + String.format("%,.0f", savedAmount));
            } else {
                result.recordFailure();
                result.log("  -> update HTTP " + response.statusCode() + " FAIL");
                appendDetail(result, response);
            }
            index++;
        }

        result.setOk(result.failed() == 0);
        result.log("Updated " + result.succeeded() + " payment(s) with " + result.failed()
                + " failure(s), " + result.skipped() + " skipped.");
        return result;
    }

    public HarnessActionResult approvePayments(int count, SessionCredentials adminSession) {
        HarnessActionResult result = new HarnessActionResult("approve_payments", count);
        SeedFile seed = seedFileLoader.load();
        List<JsonNode> submitted = helpers.fetchPayments(adminSession, "SUBMITTED");
        if (submitted.isEmpty()) {
            result.log("No SUBMITTED payments available to approve.");
            return result;
        }

        List<JsonNode> toProcess = submitted.stream()
                .sorted(helpers.paymentApprovalSortKey(seed))
                .limit(count)
                .toList();
        result.log("Approving up to " + toProcess.size() + " payment(s)");

        int index = 1;
        for (JsonNode payment : toProcess) {
            String paymentId = payment.path("payment_id").asText();
            double amount = payment.path("amount").asDouble(0);
            String owningLob = payment.path("owning_lob").asText("?");
            String creatorId = payment.path("created_by").path("user_id").asText("?");
            String approverId = helpers.approverForPayment(seed, payment);
            if (approverId == null) {
                result.recordSkip();
                result.recordFailure();
                result.log("[" + index + "] " + paymentId + " skip: no eligible approver for lob="
                        + owningLob + " creator=" + creatorId + " amount=" + String.format("%,.0f", amount));
                index++;
                continue;
            }

            result.log("[" + index + "] " + paymentId + " lob=" + owningLob + " creator=" + creatorId
                    + " amount=" + String.format("%,.0f", amount) + " approve=" + approverId);
            SessionCredentials approveSession = helpers.sessionForUser(seed, approverId);
            ServiceResponse response = paymentClient.approvePayment(approveSession, paymentId);
            recordLifecycleResponse(result, response, "approve");
            index++;
        }

        finalizeResult(result, "Approved");
        return result;
    }

    public HarnessActionResult rejectPayments(int count, SessionCredentials adminSession) {
        HarnessActionResult result = new HarnessActionResult("reject_payments", count);
        SeedFile seed = seedFileLoader.load();
        List<JsonNode> toProcess = helpers.fetchPayments(adminSession, "SUBMITTED").stream().limit(count).toList();
        if (toProcess.isEmpty()) {
            result.log("No SUBMITTED payments available to reject.");
            return result;
        }

        result.log("Rejecting up to " + toProcess.size() + " payment(s)");
        int index = 1;
        for (JsonNode payment : toProcess) {
            String paymentId = payment.path("payment_id").asText();
            String owningLob = payment.path("owning_lob").asText("?");
            String rejectorId = helpers.rejectorForPayment(seed, payment);
            if (rejectorId == null) {
                result.recordSkip();
                result.recordFailure();
                result.log("[" + index + "] " + paymentId + " skip: no eligible rejector for lob=" + owningLob);
                index++;
                continue;
            }

            result.log("[" + index + "] " + paymentId + " lob=" + owningLob + " reject=" + rejectorId);
            SessionCredentials rejectSession = helpers.sessionForUser(seed, rejectorId);
            ServiceResponse response = paymentClient.rejectPayment(
                    rejectSession, paymentId, "Rejected via test harness");
            recordLifecycleResponse(result, response, "reject");
            index++;
        }

        finalizeResult(result, "Rejected");
        return result;
    }

    public HarnessActionResult runPaymentPolicyScenario(SessionCredentials adminSession) {
        HarnessActionResult result = new HarnessActionResult("run_payment_policy_scenario", 1);
        SeedFile seed = seedFileLoader.load();
        int failures = 0;
        List<HarnessHelpers.PaymentScenarioStep> scenario = helpers.buildPaymentScenario();
        long expectedDenials = scenario.stream().filter(step -> !step.expectSuccess()).count();
        long expectedSuccesses = scenario.stream().filter(HarnessHelpers.PaymentScenarioStep::expectSuccess).count();

        long alertsBefore = properties.verifySecurityEvents()
                ? securityEventCounter.countPaymentEvents("ALERT", "failure")
                : -1;
        long infosBefore = properties.verifySecurityEvents()
                ? securityEventCounter.countPaymentEvents("INFO", "success")
                : -1;

        result.log("Running payment lifecycle policy scenario");

        List<JsonNode> approved = helpers.fetchApprovedInstructions(adminSession);
        List<JsonNode> ficcInstructions = approved.stream()
                .filter(node -> "FICC".equals(node.path("owning_lob").asText())
                        && "STANDING".equals(node.path("instruction_type").asText()))
                .toList();
        if (ficcInstructions.isEmpty()) {
            ficcInstructions = approved.stream()
                    .filter(node -> "FICC".equals(node.path("owning_lob").asText()))
                    .toList();
        }
        if (ficcInstructions.isEmpty()) {
            result.log("No approved FICC instruction found. "
                    + "Run approve-instructions first to seed at least one FICC instruction.");
            result.setOk(false);
            return result;
        }

        String instructionId = ficcInstructions.getFirst().path("instruction_id").asText();
        String valueDate = LocalDate.now().plusDays(1).toString();
        result.log("Using FICC instruction " + instructionId);

        String paymentId = null;
        int index = 1;
        for (HarnessHelpers.PaymentScenarioStep step : scenario) {
            SessionCredentials session = helpers.sessionForUser(seed, step.userId());
            result.log("[" + index + "] " + step.description()
                    + " (user=" + step.userId() + ", op=" + step.operation()
                    + ", expect=" + (step.expectSuccess() ? "OK" : "DENY") + ")");

            ServiceResponse response = switch (step.operation()) {
                case CREATE_PAYMENT -> {
                    ServiceResponse created = paymentClient.createPayment(
                            session, instructionId, 1_000_000.0, valueDate);
                    if (step.expectSuccess() && created.statusCode() == 201 && created.json() != null) {
                        paymentId = created.json().path("payment_id").asText(null);
                    }
                    yield created;
                }
                case SUBMIT_PAYMENT -> {
                    if (paymentId == null) {
                        result.log("  skip: no payment_id (earlier CREATE failed)");
                        yield null;
                    }
                    yield paymentClient.submitPayment(session, paymentId);
                }
                case APPROVE_PAYMENT -> {
                    if (paymentId == null) {
                        result.log("  skip: no payment_id (earlier step failed)");
                        yield null;
                    }
                    yield paymentClient.approvePayment(session, paymentId);
                }
                case REJECT_PAYMENT -> {
                    if (paymentId == null) {
                        result.log("  skip: no payment_id");
                        yield null;
                    }
                    yield paymentClient.rejectPayment(session, paymentId, "test harness rejection");
                }
            };

            if (response != null) {
                boolean ok = response.isSuccess() == step.expectSuccess();
                result.log("  -> HTTP " + response.statusCode() + " " + (ok ? "PASS" : "FAIL"));
                if (!ok) {
                    failures++;
                    appendDetail(result, response);
                }
            }
            index++;
        }

        if (properties.verifySecurityEvents() && alertsBefore >= 0) {
            long alertsAfter = securityEventCounter.countPaymentEvents("ALERT", "failure");
            long infosAfter = securityEventCounter.countPaymentEvents("INFO", "success");
            long newAlerts = alertsAfter - alertsBefore;
            long newInfos = infosAfter - infosBefore;
            result.log("Security events: +" + newAlerts + " ALERT (expected " + expectedDenials + "), "
                    + "+" + newInfos + " INFO (expected " + expectedSuccesses + ")");
            if (newAlerts < expectedDenials) {
                failures++;
                result.log("  FAIL: expected an ALERT security event for each policy denial");
            }
            if (newInfos < expectedSuccesses) {
                failures++;
                result.log("  FAIL: expected an INFO security event for each authorized action");
            }
        }

        result.setSucceeded(failures == 0 ? 1 : 0);
        result.setFailed(failures);
        result.setOk(failures == 0);
        result.log("Scenario finished with " + failures + " failure(s).");
        return result;
    }

    private void recordLifecycleResponse(HarnessActionResult result, ServiceResponse response, String verb) {
        if (response.isSuccess()) {
            result.recordSuccess();
            result.log("  -> " + verb + " HTTP " + response.statusCode() + " OK");
        } else {
            result.recordFailure();
            result.log("  -> " + verb + " HTTP " + response.statusCode() + " FAIL");
            appendDetail(result, response);
        }
    }

    private void finalizeResult(HarnessActionResult result, String verb) {
        result.setOk(result.failed() == 0);
        result.log(verb + " " + result.succeeded() + " instruction(s) with " + result.failed() + " failure(s).");
    }

    private void appendDetail(HarnessActionResult result, ServiceResponse response) {
        String detail = response.text(300);
        if (!detail.isBlank()) {
            result.log("     " + detail);
        }
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
