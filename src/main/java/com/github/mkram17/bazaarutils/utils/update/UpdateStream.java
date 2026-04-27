package com.github.mkram17.bazaarutils.utils.update;

public enum UpdateStream {
    ALPHA, BETA, STABLE;

    public static UpdateStream fromVersion(String version) {
        if (version == null) return STABLE;

        String lower = version.toLowerCase();
        if (lower.contains("alpha")) return ALPHA;
        if (lower.contains("beta")) return BETA;

        return STABLE;
    }

    public String toAutoUpdateKey() {
        return switch (this) {
            case ALPHA -> "alpha";
            case BETA -> "beta";
            case STABLE -> "full";
        };
    }
}