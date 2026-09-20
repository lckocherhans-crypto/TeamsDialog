package com.example.teams;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

import java.util.Locale;

/**
 * Selectable team colours (in rainbow order, since the picker cycles through them).
 * Old constant names are kept so existing saved teams still load.
 */
public enum TeamColor {
    RED("Red", 0xFF5555),
    DARK_RED("Dark Red", 0xAA0000),
    CORAL("Coral", 0xFF6F61),
    ORANGE("Orange", 0xFF8C00),
    GOLD("Gold", 0xFFAA00),
    YELLOW("Yellow", 0xFFFF55),
    LIME("Lime", 0xAAFF00),
    GREEN("Green", 0x55FF55),
    DARK_GREEN("Forest", 0x00AA00),
    MINT("Mint", 0x98FF98),
    TEAL("Teal", 0x20B2AA),
    AQUA("Aqua", 0x55FFFF),
    DARK_AQUA("Cyan", 0x00AAAA),
    SKY_BLUE("Sky Blue", 0x55DDFF),
    BLUE("Blue", 0x5555FF),
    DARK_BLUE("Navy", 0x0000AA),
    INDIGO("Indigo", 0x6A5ACD),
    DARK_PURPLE("Purple", 0xAA00AA),
    VIOLET("Violet", 0xB57EDC),
    MAGENTA("Magenta", 0xFF00FF),
    LIGHT_PURPLE("Pink", 0xFF55FF),
    HOT_PINK("Hot Pink", 0xFF69B4),
    BROWN("Brown", 0xB5651D),
    WHITE("White", 0xFFFFFF),
    SILVER("Silver", 0xC0C0C0),
    GRAY("Gray", 0xAAAAAA),
    DARK_GRAY("Charcoal", 0x555555);

    private final String display;
    private final TextColor color;

    TeamColor(String display, int rgb) {
        this.display = display;
        this.color = TextColor.color(rgb);
    }

    public String id() { return name().toLowerCase(Locale.ROOT); }
    public String display() { return display; }
    public TextColor color() { return color; }

    /** Scoreboard teams only support the 16 vanilla colours, so pick the closest one. */
    public NamedTextColor nearestNamed() { return NamedTextColor.nearestTo(color); }

    /** Hex code for TAB / PlaceholderAPI, e.g. "&#FF5555". */
    public String hexCode() { return "&" + color.asHexString().toUpperCase(Locale.ROOT); }

    public static TeamColor fromId(String id) {
        if (id != null) {
            for (TeamColor c : values()) {
                if (c.id().equalsIgnoreCase(id)) return c;
            }
        }
        return WHITE;
    }
}
