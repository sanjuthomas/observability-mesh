package com.observabilitymesh.authz.directory;

import com.observabilitymesh.common.model.Subject;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class UserDirectoryTest {

    private static UserDirectory directory;

    @BeforeAll
    static void loadDirectory() throws Exception {
        Path path = Path.of(new ClassPathResource("users.yaml").getURI());
        directory = UserDirectory.load(path);
    }

    @Test
    void loadsUsersAndEmailDomain() {
        assertThat(directory.emailDomain()).isEqualTo("ssi.local");
        assertThat(directory.allUsers()).isNotEmpty();
    }

    @Test
    void findsInstructionApproversForLob() {
        assertThat(directory.instructionApproverCandidates("FICC"))
                .extracting(Subject::userId)
                .contains("ficc-201");
    }

    @Test
    void findsFundingApproversForLob() {
        assertThat(directory.fundingApproverCandidates("FICC"))
                .isNotEmpty()
                .allMatch(subject -> subject.roles().contains("FUNDING_APPROVER"));
    }

    @Test
    void membersOfGroupFiltersRoleAndCoveringLob() {
        var members = directory.membersOfGroup("MIDDLE_OFFICE", "FUNDING_APPROVER", "FICC");
        assertThat(members).isNotEmpty();
        assertThat(members).allMatch(user -> user.groups().contains("MIDDLE_OFFICE"));
    }

    @Test
    void displayNameForKnownUser() {
        assertThat(directory.displayNameFor("mo-100")).isEqualTo("Chen, Sarah");
        assertThat(directory.displayNameFor("missing")).isEqualTo("missing");
    }

    @Test
    void displayNameForBlankUserIdReturnsNull() {
        assertThat(directory.displayNameFor("")).isNull();
        assertThat(directory.displayNameFor(null)).isNull();
    }

    @Test
    void membersOfGroupReturnsEmptyForBlankGroup() {
        assertThat(directory.membersOfGroup("", null, null)).isEmpty();
    }

    @Test
    void loadsFromInputStream() throws Exception {
        try (var input = new ClassPathResource("users.yaml").getInputStream()) {
            UserDirectory loaded = UserDirectory.load(input);
            assertThat(loaded.allUsers()).isNotEmpty();
        }
    }

    @Test
    void amountClubsContainsExpectedValues() {
        assertThat(UserDirectory.amountClubs())
                .contains("UP_TO_100_MILLION_CLUB", "UP_TO_1_BILLION_CLUB", "UP_TO_100_BILLION_CLUB");
    }
}
