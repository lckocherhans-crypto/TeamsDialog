package com.example.teams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class Team {

    /** World is stored by name so homes in not-yet-loaded worlds survive restarts. */
    public record Home(String world, double x, double y, double z, float yaw, float pitch) {
        public static Home of(Location l) {
            return new Home(l.getWorld().getName(), l.getX(), l.getY(), l.getZ(), l.getYaw(), l.getPitch());
        }

        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            return w == null ? null : new Location(w, x, y, z, yaw, pitch);
        }
    }

    private final UUID id;
    private String name;
    private String tag;
    private TeamColor color;
    private TextColor custom;      // optional custom hex colour (overrides preset)
    private TextColor gradientEnd; // optional gradient end colour
    private boolean friendlyFire;
    private int kills;
    private long createdAt;
    private Home home;
    private final Map<UUID, Role> members = new LinkedHashMap<>();

    public Team(UUID id, String name, String tag, TeamColor color) {
        this.id = id;
        this.name = name;
        this.tag = tag;
        this.color = color;
    }

    public UUID id() { return id; }
    public String name() { return name; }
    public void name(String name) { this.name = name; }
    public String tag() { return tag; }
    public void tag(String tag) { this.tag = tag; }
    public TeamColor color() { return color; }
    public void color(TeamColor color) { this.color = color; }
    public TextColor custom() { return custom; }
    public void custom(TextColor custom) { this.custom = custom; }
    public TextColor gradientEnd() { return gradientEnd; }
    public void gradientEnd(TextColor gradientEnd) { this.gradientEnd = gradientEnd; }

    /** The team's main colour: custom hex if set, otherwise the preset. */
    public TextColor primary() { return custom != null ? custom : color.color(); }

    /** Text coloured with the team's colour or gradient (one colour per character). */
    public Component colorize(String text) {
        TextColor start = primary();
        if (gradientEnd == null || text.length() < 2) return Component.text(text, start);
        Component out = Component.empty();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            out = out.append(Component.text(String.valueOf(text.charAt(i)), lerp(start, gradientEnd, i / (float) (n - 1))));
        }
        return out;
    }

    /** Same as colorize, but as a string with &#RRGGBB codes (for TAB / PlaceholderAPI). */
    public String tabColorize(String text) {
        TextColor start = primary();
        if (gradientEnd == null || text.length() < 2) return amp(start) + text;
        StringBuilder sb = new StringBuilder();
        int n = text.length();
        for (int i = 0; i < n; i++) {
            sb.append(amp(lerp(start, gradientEnd, i / (float) (n - 1)))).append(text.charAt(i));
        }
        return sb.toString();
    }

    public String primaryCode() { return amp(primary()); }

    private static String amp(TextColor c) { return "&" + c.asHexString().toUpperCase(Locale.ROOT); }

    private static TextColor lerp(TextColor a, TextColor b, float t) {
        int r = Math.round(a.red() + (b.red() - a.red()) * t);
        int g = Math.round(a.green() + (b.green() - a.green()) * t);
        int bl = Math.round(a.blue() + (b.blue() - a.blue()) * t);
        return TextColor.color(r, g, bl);
    }

    public boolean friendlyFire() { return friendlyFire; }
    public void friendlyFire(boolean friendlyFire) { this.friendlyFire = friendlyFire; }
    public int kills() { return kills; }
    public void kills(int kills) { this.kills = kills; }
    public long createdAt() { return createdAt; }
    public void createdAt(long createdAt) { this.createdAt = createdAt; }
    public Home home() { return home; }
    public void home(Home home) { this.home = home; }
    public Map<UUID, Role> members() { return members; }
    public Role role(UUID uuid) { return members.get(uuid); }
    public boolean isMember(UUID uuid) { return members.containsKey(uuid); }
}
