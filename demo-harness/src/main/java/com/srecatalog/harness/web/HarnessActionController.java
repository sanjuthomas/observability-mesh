package com.srecatalog.harness.web;

import com.srecatalog.harness.model.HarnessActionResult;
import com.srecatalog.harness.model.SessionCredentials;
import com.srecatalog.harness.service.HarnessActions;
import com.srecatalog.harness.service.HarnessAdminAccess;
import com.srecatalog.harness.web.dto.CountRequest;
import com.srecatalog.harness.web.dto.UpdatePaymentsRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HarnessActionController {

    private final HarnessActions harnessActions;
    private final HarnessAdminAccess adminAccess;

    public HarnessActionController(HarnessActions harnessActions, HarnessAdminAccess adminAccess) {
        this.harnessActions = harnessActions;
        this.adminAccess = adminAccess;
    }

    @PostMapping("/api/actions/create-instructions")
    public Map<String, Object> createInstructions(
            @Valid @RequestBody CountRequest request, HttpServletRequest httpRequest) {
        return runCountAction("create-instructions", request.count(), httpRequest, null);
    }

    @PostMapping("/api/actions/submit-instructions")
    public Map<String, Object> submitInstructions(
            @Valid @RequestBody CountRequest request, HttpServletRequest httpRequest) {
        return runCountAction("submit-instructions", request.count(), httpRequest, null);
    }

    @PostMapping("/api/actions/approve-instructions")
    public Map<String, Object> approveInstructions(
            @Valid @RequestBody CountRequest request, HttpServletRequest httpRequest) {
        return runCountAction("approve-instructions", request.count(), httpRequest, null);
    }

    @PostMapping("/api/actions/reject-instructions")
    public Map<String, Object> rejectInstructions(
            @Valid @RequestBody CountRequest request, HttpServletRequest httpRequest) {
        return runCountAction("reject-instructions", request.count(), httpRequest, null);
    }

    @PostMapping("/api/actions/suspend-instructions")
    public Map<String, Object> suspendInstructions(
            @Valid @RequestBody CountRequest request, HttpServletRequest httpRequest) {
        return runCountAction("suspend-instructions", request.count(), httpRequest, null);
    }

    @PostMapping("/api/actions/reactivate-instructions")
    public Map<String, Object> reactivateInstructions(
            @Valid @RequestBody CountRequest request, HttpServletRequest httpRequest) {
        return runCountAction("reactivate-instructions", request.count(), httpRequest, null);
    }

    @PostMapping("/api/actions/create-payments")
    public Map<String, Object> createPayments(
            @Valid @RequestBody CountRequest request, HttpServletRequest httpRequest) {
        return runCountAction("create-payments", request.count(), httpRequest, null);
    }

    @PostMapping("/api/actions/update-payments")
    public Map<String, Object> updatePayments(
            @Valid @RequestBody UpdatePaymentsRequest request, HttpServletRequest httpRequest) {
        return runCountAction("update-payments", request.count(), httpRequest, request.amount());
    }

    @PostMapping("/api/actions/submit-payments")
    public Map<String, Object> submitPayments(
            @Valid @RequestBody CountRequest request, HttpServletRequest httpRequest) {
        return runCountAction("submit-payments", request.count(), httpRequest, null);
    }

    @PostMapping("/api/actions/approve-payments")
    public Map<String, Object> approvePayments(
            @Valid @RequestBody CountRequest request, HttpServletRequest httpRequest) {
        return runCountAction("approve-payments", request.count(), httpRequest, null);
    }

    @PostMapping("/api/actions/reject-payments")
    public Map<String, Object> rejectPayments(
            @Valid @RequestBody CountRequest request, HttpServletRequest httpRequest) {
        return runCountAction("reject-payments", request.count(), httpRequest, null);
    }

    @PostMapping("/api/actions/run-policy-scenario")
    public Map<String, Object> runPolicyScenario(HttpServletRequest httpRequest) {
        SessionCredentials session = adminAccess.requireAdminSession(httpRequest);
        return harnessActions.runPolicyScenario(session).toMap();
    }

    @PostMapping("/api/actions/run-payment-policy-scenario")
    public Map<String, Object> runPaymentPolicyScenario(HttpServletRequest httpRequest) {
        SessionCredentials session = adminAccess.requireAdminSession(httpRequest);
        return harnessActions.runPaymentPolicyScenario(session).toMap();
    }

    private Map<String, Object> runCountAction(
            String action,
            int count,
            HttpServletRequest httpRequest,
            Double amount) {
        SessionCredentials session = adminAccess.requireAdminSession(httpRequest);
        HarnessActionResult result = switch (action) {
            case "create-instructions" -> harnessActions.createInstructions(count, session);
            case "submit-instructions" -> harnessActions.submitInstructions(count, session);
            case "approve-instructions" -> harnessActions.approveInstructions(count, session);
            case "reject-instructions" -> harnessActions.rejectInstructions(count, session);
            case "suspend-instructions" -> harnessActions.suspendInstructions(count, session);
            case "reactivate-instructions" -> harnessActions.reactivateInstructions(count, session);
            case "create-payments" -> harnessActions.createPayments(count, session);
            case "update-payments" -> harnessActions.updatePayments(count, session, amount);
            case "submit-payments" -> harnessActions.submitPayments(count, session);
            case "approve-payments" -> harnessActions.approvePayments(count, session);
            case "reject-payments" -> harnessActions.rejectPayments(count, session);
            default -> throw new IllegalArgumentException("unknown action: " + action);
        };
        return result.toMap();
    }
}
