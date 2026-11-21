package com.hungerbox.wings.domain;

public enum DocumentCategory {
    COMPLIANCE("Compliance"), FOOD_SAFETY("Food Safety"), BUSINESS("Business"), IT_SECURITY("IT Security"), POLICIES("Policies");

    private final String label;
    DocumentCategory(String label) { this.label = label; }
    public String label() { return label; }
}
