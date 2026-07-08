package com.srecatalog.harness.seed;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashMap;
import java.util.Map;

public final class InstructionPayloadFactory {

    private InstructionPayloadFactory() {
    }

    public static Map<String, Object> build(
            String owningLob,
            String instructionType,
            String currency) {
        Instant effective = Instant.now().atZone(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS).toInstant();
        Instant end = effective.plus(365, ChronoUnit.DAYS);

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("instruction_type", instructionType);
        payload.put("owning_lob", owningLob);
        payload.put("wire_scope", "DOMESTIC");
        payload.put("currency", currency);
        payload.put("funding_account", Map.of(
                "account_id", "DDA-" + owningLob + "-01",
                "account_name", owningLob + " Client Payments",
                "owning_lob", owningLob));
        payload.put("debtor", Map.of("name", "Client Fund A", "postal_address", Map.of("country", "US")));
        payload.put("debtor_account", Map.of(
                "identification_scheme", "PROPRIETARY",
                "identification", "DDA-" + owningLob + "-01",
                "currency", "USD"));
        payload.put("debtor_agent", Map.of(
                "financial_institution", Map.of(
                        "scheme", "CLEARING_SYSTEM",
                        "identification", "021000021",
                        "clearing_system_id", "USABA")));
        payload.put("creditor", Map.of("name", "Counterparty LLC", "postal_address", Map.of("country", "US")));
        payload.put("creditor_account", Map.of(
                "identification_scheme", "PROPRIETARY",
                "identification", "9988776655",
                "currency", "USD"));
        payload.put("creditor_agent", Map.of(
                "financial_institution", Map.of(
                        "scheme", "CLEARING_SYSTEM",
                        "identification", "011401533",
                        "clearing_system_id", "USABA")));
        payload.put("charge_bearer", "SHAR");
        payload.put("effective_date", effective.toString());
        payload.put("end_date", end.toString());
        return payload;
    }
}
