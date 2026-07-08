package com.srecatalog.authz.web;

import com.srecatalog.auth.RequestSubjectResolver;
import com.srecatalog.authz.config.AuthzProperties;
import com.srecatalog.authz.directory.DirectoryMapper;
import com.srecatalog.authz.directory.UserDirectory;
import com.srecatalog.authz.opa.OpaClient;
import com.srecatalog.authz.service.EligibilityService;
import com.srecatalog.authz.service.EvaluateSubjectResolver;
import com.srecatalog.authz.service.ServiceCallerGuard;
import com.srecatalog.authz.service.SubjectAccess;
import com.srecatalog.authz.web.dto.GroupMembersResponse;
import com.srecatalog.authz.web.dto.InstructionEligibleApproversRequest;
import com.srecatalog.authz.web.dto.InstructionEligibleApproversResponse;
import com.srecatalog.authz.web.dto.InstructionEvaluateRequest;
import com.srecatalog.authz.web.dto.PaymentEligibleApproversRequest;
import com.srecatalog.authz.web.dto.PaymentEligibleApproversResponse;
import com.srecatalog.authz.web.dto.PaymentEvaluateRequest;
import com.srecatalog.authz.web.dto.PolicyDecisionResponse;
import com.srecatalog.common.model.PolicyDecision;
import com.srecatalog.common.model.Subject;
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
