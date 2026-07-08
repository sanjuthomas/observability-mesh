package com.observabilitymesh.sloprovisioner.repo;

import java.util.regex.Pattern;

final class SqlIdentifiers {

    private static final Pattern SAFE = Pattern.compile("^[a-z][a-z0-9_]*$");

    private SqlIdentifiers() {}

    static String requireTableName(String name) {
        if (name == null || !SAFE.matcher(name).matches()) {
            throw new IllegalArgumentException("Unsafe SQL identifier: " + name);
        }
        return name;
    }
}
