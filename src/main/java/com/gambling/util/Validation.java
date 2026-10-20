package com.gambling.util;

public final class Validation {
    public static final int MAX_GAME_TYPE_LENGTH = 32;
    public static final int MAX_RESULT_LENGTH = 64;
    public static final long MAX_BET = 1_000_000_000L;
    public static final long MAX_ADMIN_AMOUNT = 1_000_000_000_000L;

    private Validation() {
    }

    public static String sanitizeGameType(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty() || trimmed.length() > MAX_GAME_TYPE_LENGTH) {
            return null;
        }
        for (int i = 0; i < trimmed.length(); i++) {
            char c = trimmed.charAt(i);
            boolean ok = (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z') || (c >= '0' && c <= '9') || c == '_' || c == '-';
            if (!ok) {
                return null;
            }
        }
        return trimmed;
    }

    public static String sanitizeResult(String raw) {
        if (raw == null) {
            return "";
        }
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder(Math.min(trimmed.length(), MAX_RESULT_LENGTH));
        for (int i = 0; i < trimmed.length() && sb.length() < MAX_RESULT_LENGTH; i++) {
            char c = trimmed.charAt(i);
            if (c >= 32 && c != 127) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    public static boolean isValidBet(long amount) {
        return amount > 0 && amount <= MAX_BET;
    }

    public static boolean isValidAdminAmount(long amount, boolean allowZero) {
        if (allowZero) {
            return amount >= 0 && amount <= MAX_ADMIN_AMOUNT;
        }
        return amount > 0 && amount <= MAX_ADMIN_AMOUNT;
    }

    public static long clampNonNegative(long value) {
        return Math.max(0L, value);
    }

    public static long safeAdd(long a, long b) {
        if (b > 0 && a > Long.MAX_VALUE - b) {
            return Long.MAX_VALUE;
        }
        if (b < 0 && a < Long.MIN_VALUE - b) {
            return Long.MIN_VALUE;
        }
        return a + b;
    }
}
