package dev.russell.yagi.core.sync;

public class GuidValidator {

    public static boolean isValidGuid(String guid) {
        return guid != null
                && !guid.trim().isEmpty()
                && !guid.equals("null");
    }
}

