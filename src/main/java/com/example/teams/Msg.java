package com.example.teams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

final class Msg {
    private static final Component PREFIX = Component.text("Teams » ", NamedTextColor.AQUA);

    private Msg() {}

    static Component ok(String s) { return PREFIX.append(Component.text(s, NamedTextColor.GREEN)); }
    static Component err(String s) { return PREFIX.append(Component.text(s, NamedTextColor.RED)); }
    static Component info(String s) { return PREFIX.append(Component.text(s, NamedTextColor.GRAY)); }
    static Component info(Component c) { return PREFIX.append(c); }
}
