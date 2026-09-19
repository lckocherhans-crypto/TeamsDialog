package com.example.teams;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

public enum Role {
    OWNER("Owner", "★", NamedTextColor.GOLD),
    OFFICER("Officer", "✦", NamedTextColor.YELLOW),
    MEMBER("Member", "•", NamedTextColor.GRAY);

    private final String display;
    private final String symbol;
    private final TextColor color;

    Role(String display, String symbol, TextColor color) {
        this.display = display;
        this.symbol = symbol;
        this.color = color;
    }

    public String display() { return display; }
    public String symbol() { return symbol; }
    public TextColor color() { return color; }

    public boolean canInvite() { return this != MEMBER; }
    public boolean canSetHome() { return this != MEMBER; }

    /** True if this role is strictly higher than the other one. */
    public boolean outranks(Role other) { return ordinal() < other.ordinal(); }
}
