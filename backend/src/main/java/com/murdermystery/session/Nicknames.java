package com.murdermystery.session;

final class Nicknames {
    private Nicknames() {}

    static String validate(String raw) {
        if (raw == null) throw new IllegalArgumentException("nickname must not be null");
        String trimmed = raw.trim();
        if (trimmed.isEmpty()) throw new IllegalArgumentException("nickname must not be blank");
        if (trimmed.length() > 20) throw new IllegalArgumentException("nickname too long (max 20)");
        if (trimmed.chars().anyMatch(Character::isISOControl))
            throw new IllegalArgumentException("nickname contains invalid characters");
        return trimmed;
    }
}
