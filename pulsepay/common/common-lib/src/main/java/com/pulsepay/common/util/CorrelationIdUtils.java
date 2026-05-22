package com.pulsepay.common.util;

import java.util.UUID;

public final class CorrelationIdUtils {

    private static final String UUID_REGEX =
            "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";

    private CorrelationIdUtils() {}

    public static String generate() {
        return UUID.randomUUID().toString();
    }

    public static boolean isValid(String id) {
        return id != null && id.matches(UUID_REGEX);
    }
}
