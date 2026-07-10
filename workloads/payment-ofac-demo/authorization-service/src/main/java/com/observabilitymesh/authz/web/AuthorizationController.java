package com.observabilitymesh.authz.web;

import com.observabilitymesh.auth.RequestSubjectResolver;
import com.observabilitymesh.authz.config.AuthzProperties;
import com.observabilitymesh.authz.directory.DirectoryMapper;
import com.observabilitymesh.authz.directory.UserDirectory;
import com.observabilitymesh.authz.opa.OpaClient;
import com.observabilitymesh.authz.service.EligibilityService;
import com.observabilitymesh.authz.service.EvaluateSubjectResolver;
import com.observabilitymesh.authz.service.ServiceCallerGuard;
import com.observabilitymesh.authz.service.SubjectAccess;
import com.observabilitymesh.authz.web.dto.GroupMembersResponse;
import com.observabilitymesh.authz.web.dto.InstructionEligibleApproversRequest;
import com.observabilitymesh.authz.web.dto.InstructionEligibleApproversResponse;
import com.observabilitymesh.authz.web.dto.InstructionEvaluateRequest;
import com.observabilitymesh.authz.web.dto.PaymentEligibleApproversRequest;
import com.observabilitymesh.authz.web.dto.PaymentEligibleApproversResponse;
import com.observabilitymesh.authz.web.dto.PaymentEvaluateRequest;
import com.observabilitymesh.authz.web.dto.PolicyDecisionResponse;
import com.observabilitymesh.common.model.PolicyDecision;
import com.observabilitymesh.common.model.Subject;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/v1/authorization")
public class AuthorizationController {

    private final OpaClient opaClient;
    private final EligibilityService eligibilityService;
    private final UserDirectory userDirectory;
    private final RequestSubjectResolver subjectResolver;
    private final EvaluateSubjectResolver evaluateSubjectResolver;
    private final ServiceCallerGuard serviceCallerGuard;
    private final AuthzProperties authzProperties;

    public AuthorizationController(
            OpaClient opaClient,
            EligibilityService eligibilityService,
            UserDirectory userDirectory,
            RequestSubjectResolver subjectResolver,
            EvaluateSubjectResolver evaluateSubjectResolver,
            ServiceCallerGuard serviceCallerGuard,
            AuthzProperties authzProperties) {
        this.opaClient = opaClient;
        this.eligibilityService = eligibilityService;
        this.userDirectory = userDirectory;
        this.subjectResolver = subjectResolver;
        this.evaluateSubjectResolver = evaluateSubjectResolver;
        this.serviceCallerGuard = serviceCallerGuard;
        this.authzProperties = authzProperties;
    }

    @GetMapping("/groups/{group}/members")
    public GroupMembersResponse listGroupMembers(
            @PathVariable String group,
            @RequestParam(required = false) String role,
            @RequestParam(name = "covering_lob", required = false) String coveringLob,
            HttpServletRequest request) {
        Subject subject = subjectResolver.resolveActor(request);
        SubjectAccess.requireCompliance(subject, authzProperties);
        var members = DirectoryMapper.buildGroupMemberRows(
                userDirectory.membersOfGroup(group, role, coveringLob));
        return new GroupMembersResponse(group.strip(), members.size(), members);
    }

    @PostMapping("/instructions/evaluate")
    public PolicyDecisionResponse evaluateInstruction(
            @Valid @RequestBody InstructionEvaluateRequest body,
            HttpServletRequest request) {
        Subject serviceCaller = requireServiceCaller(request);
        Subject subject = evaluateSubjectResolver.resolve(
                serviceCaller,
                request.getHeader(RequestSubjectResolver.OBO_HEADER),
                body.subject());
        try {
            PolicyDecision decision = opaClient.evaluateInstruction(
                    body.action(), subject, body.instruction(), body.account());
            return PolicyDecisionResponse.from(decision);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "opa evaluation failed: " + ex.getMessage(), ex);
        }
    }

    @PostMapping("/payments/evaluate")
    public PolicyDecisionResponse evaluatePayment(
            @Valid @RequestBody PaymentEvaluateRequest body,
            HttpServletRequest request) {
        Subject serviceCaller = requireServiceCaller(request);
        Subject subject = evaluateSubjectResolver.resolve(
                serviceCaller,
                request.getHeader(RequestSubjectResolver.OBO_HEADER),
                body.subject());
        try {
            PolicyDecision decision = opaClient.evaluatePayment(
                    body.action(),
                    subject,
                    body.payment(),
                    body.instructionEndDate(),
                    body.instructionStatus());
            return PolicyDecisionResponse.from(decision);
        } catch (ResponseStatusException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "opa evaluation failed: " + ex.getMessage(), ex);
        }
    }

    @PostMapping("/instructions/eligible-approvers")
    public InstructionEligibleApproversResponse evaluateInstructionEligibleApprovers(
            @Valid @RequestBody InstructionEligibleApproversRequest body,
            HttpServletRequest request) {
        requireServiceCaller(request);
        try {
            return eligibilityService.eligibleApproversForInstruction(body.instruction());
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "eligibility evaluation failed: " + ex.getMessage(), ex);
        }
    }

    @PostMapping("/payments/eligible-approvers")
    public PaymentEligibleApproversResponse evaluatePaymentEligibleApprovers(
            @Valid @RequestBody PaymentEligibleApproversRequest body,
            HttpServletRequest request) {
        requireServiceCaller(request);
        try {
            return eligibilityService.eligibleApproversForPayment(
                    body.payment(), body.instructionStatus(), body.instructionEndDate());
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE, "eligibility evaluation failed: " + ex.getMessage(), ex);
        }
    }

    private Subject requireServiceCaller(HttpServletRequest request) {
        Subject serviceCaller = subjectResolver.resolveCaller(request);
        serviceCallerGuard.requireAuthorizedService(serviceCaller);
        return serviceCaller;
    }
}
