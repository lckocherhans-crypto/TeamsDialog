package com.example.teams;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.LinkedHashMap;
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
