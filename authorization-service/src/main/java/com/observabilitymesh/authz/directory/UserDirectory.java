package com.observabilitymesh.authz.directory;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import com.observabilitymesh.common.model.SeedUser;
import com.observabilitymesh.common.model.Subject;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
public final class UserDirectory {

    private static final Set<String> AMOUNT_CLUBS = Set.of(
            "UP_TO_100_MILLION_CLUB",
            "UP_TO_1_BILLION_CLUB",
            "UP_TO_100_BILLION_CLUB");

    private final SeedFile seed;

    private UserDirectory(SeedFile seed) {
        this.seed = seed;
    }

    public static UserDirectory load(Path path) throws IOException {
        return load(Files.readString(path));
    }

    public static UserDirectory load(InputStream inputStream) throws IOException {
        return load(new String(inputStream.readAllBytes()));
    }

    public static UserDirectory load(String yamlContent) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        SeedFile seed = mapper.readValue(yamlContent, SeedFile.class);
        return new UserDirectory(seed);
    }

    public String emailDomain() {
        return seed.defaults().getOrDefault("email_domain", "ssi.local");
    }

    public List<SeedUser> allUsers() {
        return seed.users().stream()
                .sorted(Comparator.comparing(SeedUser::userId))
                .toList();
    }

    public String displayNameFor(String userId) {
        if (userId == null || userId.isBlank()) {
            return null;
        }
        for (SeedUser user : seed.users()) {
            if (userId.equals(user.userId())) {
                return user.familyName() + ", " + user.givenName();
            }
        }
        return userId;
    }

    public List<Subject> fundingApproverCandidates(String owningLob) {
        return seed.users().stream()
                .filter(user -> user.roles().contains("FUNDING_APPROVER"))
                .filter(user -> user.groups().contains("MIDDLE_OFFICE"))
                .filter(user -> user.coveringLobs().contains(owningLob))
                .map(SeedUser::toSubject)
                .sorted(Comparator.comparing(Subject::userId))
                .toList();
    }

    public List<Subject> instructionApproverCandidates(String owningLob) {
        return seed.users().stream()
                .filter(user -> user.roles().contains("INSTRUCTION_APPROVER"))
                .filter(user -> owningLob.equals(user.lob()))
                .map(SeedUser::toSubject)
                .sorted(Comparator.comparing(Subject::userId))
                .toList();
    }

    public List<SeedUser> membersOfGroup(String group, String role, String coveringLob) {
        String groupUpper = group == null ? "" : group.strip().toUpperCase(Locale.ROOT);
        if (groupUpper.isEmpty()) {
            return List.of();
        }
        String roleUpper = role == null || role.isBlank() ? null : role.strip().toUpperCase(Locale.ROOT);
        String lobUpper = coveringLob == null || coveringLob.isBlank() ? null : coveringLob.strip().toUpperCase(Locale.ROOT);

        return seed.users().stream()
                .filter(user -> containsIgnoreCase(user.groups(), groupUpper))
                .filter(user -> roleUpper == null || containsIgnoreCase(user.roles(), roleUpper))
                .filter(user -> lobUpper == null || containsIgnoreCase(user.coveringLobs(), lobUpper))
                .sorted(Comparator.comparing(SeedUser::userId))
                .toList();
    }

    public static Set<String> amountClubs() {
        return AMOUNT_CLUBS;
    }

    private static boolean containsIgnoreCase(List<String> values, String needle) {
        return values.stream().anyMatch(value -> needle.equals(value.toUpperCase(Locale.ROOT)));
    }

    public record SeedFile(
            java.util.Map<String, String> defaults,
            List<SeedUser> users
    ) {
        public SeedFile {
            defaults = defaults == null ? java.util.Map.of() : java.util.Map.copyOf(defaults);
            users = users == null ? List.of() : List.copyOf(users);
        }
    }
}
