package com.observabilitymesh.instruction.model.iso;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class InstructionIsoTypesTest {

    @Test
    void postalAddressDefaultsNullAddressLinesToEmptyList() {
        var address = new InstructionIsoTypes.PostalAddress(
                "Main St", "1", "10001", "NYC", "NY", "US", null);
        assertThat(address.addressLines()).isEmpty();
    }

    @Test
    void postalAddressCopiesProvidedAddressLines() {
        var address = new InstructionIsoTypes.PostalAddress(
                "Main St", "1", "10001", "NYC", "NY", "US", List.of("Suite 100"));
        assertThat(address.addressLines()).containsExactly("Suite 100");
    }

    @Test
    void instructionForAgentRecordHoldsValues() {
        var agentInstruction = new InstructionIsoTypes.InstructionForAgent("PHOB", "Phone beneficiary");
        assertThat(agentInstruction.code()).isEqualTo("PHOB");
        assertThat(agentInstruction.instructionInformation()).isEqualTo("Phone beneficiary");
    }
}
