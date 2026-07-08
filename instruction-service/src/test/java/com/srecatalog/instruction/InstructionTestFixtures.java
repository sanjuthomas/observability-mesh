package com.srecatalog.instruction;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.srecatalog.common.model.Subject;
import com.srecatalog.common.model.UserReference;
import com.srecatalog.instruction.config.InstructionProperties;
import com.srecatalog.instruction.model.AccountIdentificationScheme;
import com.srecatalog.instruction.model.CashSettlementInstruction;
import com.srecatalog.instruction.model.ChargeBearer;
import com.srecatalog.instruction.model.FinancialInstitutionIdScheme;
import com.srecatalog.instruction.model.InstructionStatus;
import com.srecatalog.instruction.model.InstructionType;
import com.srecatalog.instruction.model.WireScope;
import com.srecatalog.instruction.model.iso.InstructionIsoTypes;
import com.srecatalog.instruction.service.InstructionAuthorization;
import com.srecatalog.instruction.web.dto.CreateInstructionRequest;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;

public final class InstructionTestFixtures {

    public static final Subject CREATOR = new Subject(
            "user-001", "Jane", "Doe", "VP", "FICC",
            List.of("INSTRUCTION_CREATOR", "MIDDLE_OFFICE"), List.of("MIDDLE_OFFICE"), null,
            List.of("FICC"), null, List.of());

    public static final Subject APPROVER = new Subject(
            "user-002", "John", "Smith", "MD", "FICC",
            List.of("INSTRUCTION_APPROVER"), List.of("MIDDLE_OFFICE"), "user-001",
            List.of("FICC"), null, List.of());

    public static final Subject ADMIN = new Subject(
            "admin-001", "Admin", "User", "Platform Admin", null,
            List.of("PLATFORM_ADMIN"), List.of(), null, List.of(), null, List.of());

    private InstructionTestFixtures() {
    }

    public static InstructionProperties properties() {
        return new InstructionProperties(
                "instructions", "security_events", "instruction_service",
                "svc-instruction", "Password1!", "COMPLIANCE_ANALYST", "", "admin-001", 200);
    }

    public static CreateInstructionRequest sampleCreateRequest() {
        return new CreateInstructionRequest(
                InstructionType.STANDING,
                "FICC",
                WireScope.DOMESTIC,
                "USD",
                new InstructionIsoTypes.FundingAccount("FA-1", "Funding", "FICC"),
                null,
                null,
                new InstructionIsoTypes.PartyIdentification("Debtor Co", null, null, "US"),
                new InstructionIsoTypes.CashAccount(AccountIdentificationScheme.IBAN, "US123", "USD", null),
                clearingAgent("021000021"),
                null,
                null,
                null,
                List.of(),
                List.of(),
                clearingAgent("026009593"),
                null,
                new InstructionIsoTypes.PartyIdentification("Creditor Co", null, null, "US"),
                new InstructionIsoTypes.CashAccount(AccountIdentificationScheme.IBAN, "US456", "USD", null),
                null,
                ChargeBearer.DEBT,
                List.of(),
                List.of(),
                Instant.parse("2026-01-01T00:00:00Z"),
                Instant.parse("2099-12-31T00:00:00Z"));
    }

    public static CashSettlementInstruction sampleInstruction(String instructionId) {
        CashSettlementInstruction instruction = new CashSettlementInstruction();
        Instant now = Instant.now();
        instruction.setInstructionId(instructionId);
        instruction.setInstructionType(InstructionType.STANDING);
        instruction.setStatus(InstructionStatus.DRAFT);
        instruction.setOwningLob("FICC");
        instruction.setWireScope(WireScope.DOMESTIC);
        instruction.setCurrency("USD");
        instruction.setFundingAccount(new InstructionIsoTypes.FundingAccount("FA-1", "Funding", "FICC"));
        instruction.setDebtor(new InstructionIsoTypes.PartyIdentification("Debtor Co", null, null, "US"));
        instruction.setDebtorAccount(new InstructionIsoTypes.CashAccount(AccountIdentificationScheme.IBAN, "US123", "USD", null));
        instruction.setDebtorAgent(clearingAgent("021000021"));
        instruction.setCreditorAgent(clearingAgent("026009593"));
        instruction.setCreditor(new InstructionIsoTypes.PartyIdentification("Creditor Co", null, null, "US"));
        instruction.setCreditorAccount(new InstructionIsoTypes.CashAccount(AccountIdentificationScheme.IBAN, "US456", "USD", null));
        instruction.setChargeBearer(ChargeBearer.DEBT);
        instruction.setEffectiveDate(Instant.parse("2026-01-01T00:00:00Z"));
        instruction.setEndDate(Instant.parse("2099-12-31T00:00:00Z"));
        instruction.setCreatedBy(InstructionAuthorization.userRef(CREATOR));
        instruction.setCreatedAt(now);
        instruction.setUpdatedAt(now);
        return instruction;
    }

    public static UserReference userRef(Subject subject) {
        return InstructionAuthorization.userRef(subject);
    }

    private static InstructionIsoTypes.BranchAndFinancialInstitutionIdentification clearingAgent(String routing) {
        return new InstructionIsoTypes.BranchAndFinancialInstitutionIdentification(
                new InstructionIsoTypes.FinancialInstitutionIdentification(
                        FinancialInstitutionIdScheme.CLEARING_SYSTEM, routing, "Bank", "USABA"),
                "Bank",
                "US");
    }

    public static ObjectMapper objectMapper() {
        return new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .setPropertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    public static String createRequestJson() {
        try {
            return objectMapper().writeValueAsString(sampleCreateRequest());
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    public static MockMvc standaloneMockMvc(Object... controllers) {
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        return MockMvcBuilders.standaloneSetup(controllers)
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper()))
                .setValidator(validator)
                .build();
    }
}
