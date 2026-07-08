package com.srecatalog.authz.directory;

import com.srecatalog.authz.web.dto.GroupMemberRow;
import com.srecatalog.authz.web.dto.UserDirectoryRow;
import com.srecatalog.common.model.SeedUser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DirectoryMapperTest {

    private static UserDirectory directory;

    @BeforeAll
    static void loadDirectory() throws Exception {
        Path path = Path.of(new ClassPathResource("users.yaml").getURI());
        directory = UserDirectory.load(path);
    }

    @Test
    void buildsUserDirectoryRowsFromSeedFile() {
        List<UserDirectoryRow> rows = DirectoryMapper.buildUserDirectoryRows(directory);
        assertThat(rows).isNotEmpty();
        assertThat(rows.getFirst().loginName()).endsWith("@" + directory.emailDomain());
    }

    @Test
    void splitsAmountClubsFromGroups() {
        List<UserDirectoryRow> rows = DirectoryMapper.buildUserDirectoryRows(directory);
        UserDirectoryRow fundingApprover = rows.stream()
                .filter(row -> row.amountClubs().contains("UP_TO_100_MILLION_CLUB"))
                .findFirst()
                .orElseThrow();
        assertThat(fundingApprover.groups()).contains("MIDDLE_OFFICE");
        assertThat(fundingApprover.amountClubs()).isNotEmpty();
    }

    @Test
    void buildsGroupMemberRows() {
        SeedUser user = directory.allUsers().getFirst();
        List<GroupMemberRow> rows = DirectoryMapper.buildGroupMemberRows(List.of(user));
        assertThat(rows.getFirst().userId()).isEqualTo(user.userId());
    }
}
