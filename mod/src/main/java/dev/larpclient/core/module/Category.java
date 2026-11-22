package dev.larpclient.core.module;

public enum Category {
    HUD("HUD"),
    UTILITY("Utility"),
    PERFORMANCE("Performance");

    private final String displayName;

    Category(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }
}
