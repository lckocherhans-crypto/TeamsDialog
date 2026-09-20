package com.example.teams;

import net.kyori.adventure.text.format.NamedTextColor;

import java.util.Locale;

public enum TeamColor {

    RED("Red", TextColor.color(0xFF0000)),
    DARK_RED("Dark Red", TextColor.color(0x8B0000)),
    CORAL("Coral", TextColor.color(0xFF6F61)),
    ORANGE("Orange", TextColor.color(0xFF8C00)),
    GOLD("Gold", TextColor.color(0xFFAA00)),
    YELLOW("Yellow", TextColor.color(0xFFFF00)),
    LIME("Lime", TextColor.color(0xAAFF00)),
    GREEN("Green", TextColor.color(0x00FF00)),
    DARK_GREEN("Forest", TextColor.color(0x006400)),
    MINT("Mint", TextColor.color(0x98FF98)),
    TEAL("Teal", TextColor.color(0x00AFAF)),
    AQUA("Aqua", TextColor.color(0x00FFFF)),
    DARK_AQUA("Cyan", TextColor.color(0x00AAAA)),
    SKY_BLUE("Sky Blue", TextColor.color(0x55DDFF)),
    BLUE("Blue", TextColor.color(0x5555FF)),
    DARK_BLUE("Navy", TextColor.color(0x00008B)),
    PERIWINKLE("Periwinkle", TextColor.color(0x8FA8FF)),
    LIGHT_PURPLE("Pink", TextColor.color(0xFF55FF)),
    PINK("Pink Rose", TextColor.color(0xFF69B4)),
    HOT_PINK("Hot Pink", TextColor.color(0xFF1493)),
    MAGENTA("Magenta", TextColor.color(0xFF00FF)),
    PURPLE("Purple", TextColor.color(0xAA00AA)),
    VIOLET("Violet", TextColor.color(0x8A2BE2)),
    LAVENDER("Lavender", TextColor.color(0xB57EDC)),
    WHITE("White", TextColor.color(0xFFFFFF)),
    SILVER("Silver", TextColor.color(0xC0C0C0)),
    LIGHT_GRAY("Light Gray", TextColor.color(0xD3D3D3)),
    GRAY("Gray", TextColor.color(0x808080)),
    DARK_GRAY("Charcoal", TextColor.color(0x404040)),
    BLACK("Black", TextColor.color(0x000000));

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
