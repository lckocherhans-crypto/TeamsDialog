package com.example.teams;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;

public enum TeamColor {
    RED("Red", NamedTextColor.RED),
    GOLD("Gold", NamedTextColor.GOLD),
    YELLOW("Yellow", NamedTextColor.YELLOW),
    GREEN("Lime", NamedTextColor.GREEN),
    DARK_GREEN("Forest", NamedTextColor.DARK_GREEN),
    AQUA("Aqua", NamedTextColor.AQUA),
    DARK_AQUA("Cyan", NamedTextColor.DARK_AQUA),
    BLUE("Blue", NamedTextColor.BLUE),
    DARK_BLUE("Navy", NamedTextColor.DARK_BLUE),
    LIGHT_PURPLE("Pink", NamedTextColor.LIGHT_PURPLE),
    DARK_PURPLE("Purple", NamedTextColor.DARK_PURPLE),
    WHITE("White", NamedTextColor.WHITE),
    GRAY("Gray", NamedTextColor.GRAY),
    DARK_GRAY("Charcoal", NamedTextColor.DARK_GRAY);

    private final String display;
    private final NamedTextColor color;

    TeamColor(String display, NamedTextColor color) {
        this.display = display;
        this.color = color;
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String display() { return display; }
    public NamedTextColor color() { return color; }

    public static TeamColor fromId(String id) {
        if (id != null) {
            for (TeamColor c : values()) {
                if (c.id().equalsIgnoreCase(id)) return c;
            }
        }
        return WHITE;
    }
}
