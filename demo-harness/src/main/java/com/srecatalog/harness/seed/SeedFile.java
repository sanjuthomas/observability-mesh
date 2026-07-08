package com.srecatalog.harness.seed;

import java.util.List;
import java.util.Map;

public record SeedFile(
        Map<String, String> defaults,
        List<SeedUser> users
) {
    public SeedFile {
        defaults = defaults == null ? Map.of() : Map.copyOf(defaults);
        users = users == null ? List.of() : List.copyOf(users);
    }

    public SeedUser userById(String userId) {
        return users.stream()
                .filter(user -> user.userId().equals(userId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("unknown user_id in seed file: " + userId));
    }
}
