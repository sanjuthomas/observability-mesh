package com.observabilitymesh.authz.directory;

import com.observabilitymesh.authz.web.dto.GroupMemberRow;
import com.observabilitymesh.authz.web.dto.UserDirectoryRow;
import com.observabilitymesh.common.model.SeedUser;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class DirectoryMapper {

    private DirectoryMapper() {
    }

    public static List<UserDirectoryRow> buildUserDirectoryRows(UserDirectory directory) {
        String emailDomain = directory.emailDomain();
        List<UserDirectoryRow> rows = new ArrayList<>();
        for (SeedUser user : directory.allUsers()) {
            GroupSplit split = splitGroups(user.groups());
            rows.add(new UserDirectoryRow(
                    user.userId(),
                    user.userId() + "@" + emailDomain,
                    user.givenName(),
                    user.familyName(),
                    user.familyName() + ", " + user.givenName(),
                    user.title(),
                    user.lob(),
                    List.copyOf(user.roles()),
                    split.orgGroups(),
                    split.amountClubs(),
                    List.copyOf(user.coveringLobs()),
                    user.supervisorId(),
                    directory.displayNameFor(user.supervisorId())));
        }
        return rows;
    }

    public static List<GroupMemberRow> buildGroupMemberRows(List<SeedUser> users) {
        return users.stream()
                .map(user -> new GroupMemberRow(
                        user.userId(),
                        user.familyName() + ", " + user.givenName(),
                        user.title(),
                        List.copyOf(user.roles()),
                        List.copyOf(user.groups()),
                        List.copyOf(user.coveringLobs())))
                .toList();
    }

    private static GroupSplit splitGroups(List<String> groups) {
        List<String> orgGroups = new ArrayList<>();
        List<String> amountClubs = new ArrayList<>();
        Set<String> clubSet = UserDirectory.amountClubs();
        for (String group : groups) {
            if (clubSet.contains(group)) {
                amountClubs.add(group);
            } else {
                orgGroups.add(group);
            }
        }
        return new GroupSplit(orgGroups, amountClubs);
    }

    private record GroupSplit(List<String> orgGroups, List<String> amountClubs) {
    }
}
