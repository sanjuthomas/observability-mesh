package com.srecatalog.authz.web;

import com.srecatalog.auth.RequestSubjectResolver;
import com.srecatalog.authz.AuthzTestFixtures;
import com.srecatalog.authz.config.AuthzProperties;
import com.srecatalog.authz.directory.UserDirectory;
import com.srecatalog.authz.opa.OpaClient;
import com.srecatalog.authz.service.EligibilityService;
import com.srecatalog.authz.service.EvaluateSubjectResolver;
import com.srecatalog.authz.service.ServiceCallerGuard;
import com.srecatalog.common.model.PolicyDecision;
import com.srecatalog.common.model.Subject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class AuthorizationControllerTest {

    @Mock OpaClient opaClient;
    @Mock EligibilityService eligibilityService;
    @Mock UserDirectory userDirectory;
    @Mock RequestSubjectResolver subjectResolver;
    @Mock EvaluateSubjectResolver evaluateSubjectResolver;

    private MockMvc mockMvc;
    private final Subject serviceCaller = new Subject(
            "svc-instruction", null, null, "Service", null,
            List.of("SERVICE"), List.of(), null, List.of(), null, List.of());
    private final Subject complianceUser = new Subject(
            "comp-001", "C", "O", "Analyst", "FICC",
            List.of("COMPLIANCE_ANALYST"), List.of(), null, List.of("FICC"), null, List.of());

    @BeforeEach
    void setUp() {
        AuthzProperties properties = new AuthzProperties(
                "http://opa:9181", "classpath:users.yaml", "COMPLIANCE_ANALYST,PLATFORM_ADMIN");
        AuthorizationController controller = new AuthorizationController(
                opaClient,
                eligibilityService,
                userDirectory,
                subjectResolver,
                evaluateSubjectResolver,
                new ServiceCallerGuard("svc-instruction,svc-payment"),
                properties);
        mockMvc = AuthzTestFixtures.standaloneMockMvc(controller);
    }

    @Test
    void evaluateInstructionRequiresAuthorizedServiceCaller() throws Exception {
        when(subjectResolver.resolveCaller(any())).thenReturn(serviceCaller);
        when(evaluateSubjectResolver.resolve(eq(serviceCaller), eq(null), any()))
                .thenReturn(serviceCaller);
        when(opaClient.evaluateInstruction(any(), any(), any(), any()))
                .thenReturn(PolicyDecision.allow(List.of("ROLE_MATCH")));

        mockMvc.perform(post("/api/v1/authorization/instructions/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "CREATE",
                                  "instruction": {"status": "DRAFT"},
                                  "account": {"owning_lob": "FICC"},
                                  "subject": {
                                    "user_id": "mo-100",
                                    "title": "Analyst",
                                    "roles": ["INSTRUCTION_CREATOR"]
                                  }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.allow_basis[0]").value("ROLE_MATCH"));
    }

    @Test
    void evaluateInstructionRejectsUnauthorizedServiceCaller() throws Exception {
        Subject other = new Subject(
                "svc-other", null, null, "Service", null,
                List.of("SERVICE"), List.of(), null, List.of(), null, List.of());
        when(subjectResolver.resolveCaller(any())).thenReturn(other);

        mockMvc.perform(post("/api/v1/authorization/instructions/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "CREATE",
                                  "instruction": {"status": "DRAFT"},
                                  "account": {"owning_lob": "FICC"},
                                  "subject": {
                                    "user_id": "mo-100",
                                    "title": "Analyst",
                                    "roles": ["INSTRUCTION_CREATOR"]
                                  }
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    void listGroupMembersRequiresComplianceRole() throws Exception {
        when(subjectResolver.resolveActor(any())).thenReturn(complianceUser);
        when(userDirectory.membersOfGroup("MIDDLE_OFFICE", null, null)).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/authorization/groups/MIDDLE_OFFICE/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group").value("MIDDLE_OFFICE"));
    }

    @Test
    void eligibleApproversDelegatesToService() throws Exception {
        when(subjectResolver.resolveCaller(any())).thenReturn(serviceCaller);
        when(eligibilityService.eligibleApproversForInstruction(any()))
                .thenReturn(new com.srecatalog.authz.web.dto.InstructionEligibleApproversResponse(
                        "I-1", "DRAFT", "SINGLE_USE", "FICC", "mo-100", "Analyst",
                        "2026-01-01T00:00:00Z", List.of(), List.of(), 3, "blocked"));

        mockMvc.perform(post("/api/v1/authorization/instructions/eligible-approvers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction\":{\"instruction_id\":\"I-1\",\"status\":\"DRAFT\",\"owning_lob\":\"FICC\"}}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instruction_id").value("I-1"));

        verify(eligibilityService).eligibleApproversForInstruction(any());
    }

    @Test
    void evaluatePaymentReturnsPolicyDecision() throws Exception {
        when(subjectResolver.resolveCaller(any())).thenReturn(serviceCaller);
        when(evaluateSubjectResolver.resolve(eq(serviceCaller), eq(null), any()))
                .thenReturn(serviceCaller);
        when(opaClient.evaluatePayment(any(), any(), any(), any(), any()))
                .thenReturn(PolicyDecision.deny(List.of("SEGREGATION_OF_DUTIES"), true));

        mockMvc.perform(post("/api/v1/authorization/payments/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "APPROVE",
                                  "payment": {"status": "SUBMITTED", "payment_id": "P-1"},
                                  "instruction_status": "APPROVED",
                                  "instruction_end_date": "2027-01-01",
                                  "subject": {"user_id": "pay-201", "title": "VP", "roles": ["FUNDING_APPROVER"]}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.violations[0]").value("SEGREGATION_OF_DUTIES"))
                .andExpect(jsonPath("$.is_alert").value(true));
    }

    @Test
    void evaluateInstructionMapsOpaFailuresTo503() throws Exception {
        when(subjectResolver.resolveCaller(any())).thenReturn(serviceCaller);
        when(evaluateSubjectResolver.resolve(eq(serviceCaller), eq(null), any()))
                .thenReturn(serviceCaller);
        when(opaClient.evaluateInstruction(any(), any(), any(), any()))
                .thenThrow(new RuntimeException("connection refused"));

        mockMvc.perform(post("/api/v1/authorization/instructions/evaluate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "action": "CREATE",
                                  "instruction": {"status": "DRAFT"},
                                  "account": {"owning_lob": "FICC"},
                                  "subject": {"user_id": "mo-100", "title": "Analyst", "roles": ["INSTRUCTION_CREATOR"]}
                                }
                                """))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void listGroupMembersSupportsRoleAndCoveringLobFilters() throws Exception {
        when(subjectResolver.resolveActor(any())).thenReturn(complianceUser);
        when(userDirectory.membersOfGroup("MIDDLE_OFFICE", "FUNDING_APPROVER", "FICC")).thenReturn(List.of());

        mockMvc.perform(get("/api/v1/authorization/groups/MIDDLE_OFFICE/members")
                        .param("role", "FUNDING_APPROVER")
                        .param("covering_lob", "FICC"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.group").value("MIDDLE_OFFICE"));
    }

    @Test
    void paymentEligibleApproversDelegatesToService() throws Exception {
        when(subjectResolver.resolveCaller(any())).thenReturn(serviceCaller);
        when(eligibilityService.eligibleApproversForPayment(any(), any(), any()))
                .thenReturn(new com.srecatalog.authz.web.dto.PaymentEligibleApproversResponse(
                        "P-1", "I-1", "SUBMITTED", 100.0, "USD", "FICC", "APPROVED",
                        "2026-01-01T00:00:00Z", List.of(), List.of(), 2, null));

        mockMvc.perform(post("/api/v1/authorization/payments/eligible-approvers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "payment": {
                                    "payment_id": "P-1",
                                    "instruction_id": "I-1",
                                    "instruction_version": 1,
                                    "status": "SUBMITTED",
                                    "amount": 100.0,
                                    "currency": "USD",
                                    "owning_lob": "FICC",
                                    "instruction_type": "STANDING",
                                    "created_by_user_id": "mo-100"
                                  },
                                  "instruction_status": "APPROVED",
                                  "instruction_end_date": "2027-01-01"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.payment_id").value("P-1"));
    }

    @Test
    void eligibleApproversMapsServiceFailuresTo503() throws Exception {
        when(subjectResolver.resolveCaller(any())).thenReturn(serviceCaller);
        when(eligibilityService.eligibleApproversForInstruction(any()))
                .thenThrow(new RuntimeException("opa down"));

        mockMvc.perform(post("/api/v1/authorization/instructions/eligible-approvers")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"instruction\":{\"instruction_id\":\"I-1\",\"status\":\"DRAFT\",\"owning_lob\":\"FICC\"}}"))
                .andExpect(status().isServiceUnavailable());
    }
}
