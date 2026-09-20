package com.example.teams;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Owns all team state: storage, scoreboard visuals, invites and every mutating action.
 * Action methods return an error string, or null on success.
 */
public final class TeamManager {

    public record Invite(UUID teamId, UUID inviter, long expiresAt) {}

    private static final Pattern NAME_PATTERN = Pattern.compile("[A-Za-z0-9_]{3,16}");
    private static final Pattern TAG_PATTERN = Pattern.compile("[A-Za-z0-9]{2,4}");
    private static final String SB_PREFIX = "tm_";

    private final TeamsPlugin plugin;
    private final File file;
    private final Map<UUID, Team> teams = new LinkedHashMap<>();
    private final Map<UUID, UUID> playerTeam = new HashMap<>();
    private final Map<UUID, List<Invite>> invites = new HashMap<>();
    private final Set<UUID> chatToggled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, BukkitTask> warmups = new HashMap<>();

    public TeamManager(TeamsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "teams.yml");
    }

    // ------------------------------------------------------------------ storage

    public void load() {
        teams.clear();
        playerTeam.clear();
        if (!file.exists()) return;

        YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
        ConfigurationSection root = yml.getConfigurationSection("teams");
        if (root == null) return;

        for (String key : root.getKeys(false)) {
            ConfigurationSection s = root.getConfigurationSection(key);
            if (s == null) continue;
            try {
                UUID id = UUID.fromString(key);
                Team t = new Team(id,
                        s.getString("name", "Team"),
                        s.getString("tag", "TM"),
                        TeamColor.fromId(s.getString("color", "white")));
                t.friendlyFire(s.getBoolean("friendly-fire", false));
                t.custom(parseHex(s.getString("custom-color")));
                t.gradientEnd(parseHex(s.getString("gradient-end")));
                t.kills(s.getInt("kills", 0));
                t.createdAt(s.getLong("created", System.currentTimeMillis()));

                ConfigurationSection h = s.getConfigurationSection("home");
                if (h != null) {
                    t.home(new Team.Home(h.getString("world", "world"),
                            h.getDouble("x"), h.getDouble("y"), h.getDouble("z"),
                            (float) h.getDouble("yaw"), (float) h.getDouble("pitch")));
                }

                ConfigurationSection m = s.getConfigurationSection("members");
                if (m != null) {
                    for (String u : m.getKeys(false)) {
                        UUID uid = UUID.fromString(u);
                        Role r = Role.valueOf(m.getString(u, "MEMBER"));
                        t.members().put(uid, r);
                        playerTeam.put(uid, id);
                    }
                }
                if (!t.members().isEmpty()) teams.put(id, t);
            } catch (IllegalArgumentException ex) {
                plugin.getLogger().warning("Skipping corrupt team entry '" + key + "': " + ex.getMessage());
            }
        }
        plugin.getLogger().info("Loaded " + teams.size() + " team(s).");
    }

    public void save() {
        YamlConfiguration yml = new YamlConfiguration();
        for (Team t : teams.values()) {
            String base = "teams." + t.id();
            yml.set(base + ".name", t.name());
            yml.set(base + ".tag", t.tag());
            yml.set(base + ".color", t.color().id());
            if (t.custom() != null) yml.set(base + ".custom-color", t.custom().asHexString());
            if (t.gradientEnd() != null) yml.set(base + ".gradient-end", t.gradientEnd().asHexString());
            yml.set(base + ".friendly-fire", t.friendlyFire());
            yml.set(base + ".kills", t.kills());
            yml.set(base + ".created", t.createdAt());
            Team.Home h = t.home();
            if (h != null) {
                yml.set(base + ".home.world", h.world());
                yml.set(base + ".home.x", h.x());
                yml.set(base + ".home.y", h.y());
                yml.set(base + ".home.z", h.z());
                yml.set(base + ".home.yaw", h.yaw());
                yml.set(base + ".home.pitch", h.pitch());
            }
            for (Map.Entry<UUID, Role> e : t.members().entrySet()) {
                yml.set(base + ".members." + e.getKey(), e.getValue().name());
            }
        }
        try {
            //noinspection ResultOfMethodCallIgnored
            plugin.getDataFolder().mkdirs();
            yml.save(file);
        } catch (IOException e) {
            plugin.getLogger().severe("Could not save teams.yml: " + e.getMessage());
        }
    }

    public void shutdown() {
        warmups.values().forEach(BukkitTask::cancel);
        warmups.clear();
        save();
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (org.bukkit.scoreboard.Team st : new ArrayList<>(sb.getTeams())) {
            if (st.getName().startsWith(SB_PREFIX)) st.unregister();
        }
    }

    // ------------------------------------------------------------------ lookups

    public Team teamOf(UUID player) {
        UUID id = playerTeam.get(player);
        return id == null ? null : teams.get(id);
    }

    public Team byId(UUID id) { return teams.get(id); }

    public Collection<Team> all() { return teams.values(); }

    public String nameOf(UUID uuid) {
        Player p = Bukkit.getPlayer(uuid);
        if (p != null) return p.getName();
        String n = Bukkit.getOfflinePlayer(uuid).getName();
        return n != null ? n : "Unknown";
    }

    public int maxMembers() { return plugin.getConfig().getInt("max-members", 10); }

    public List<Team> top(int limit) {
        return teams.values().stream()
                .sorted(Comparator.comparingInt((Team t) -> -t.kills())
                        .thenComparingInt((Team t) -> -t.members().size()))
                .limit(limit)
                .toList();
    }

    // ------------------------------------------------------------------ visuals (scoreboard)

    private org.bukkit.scoreboard.Team sbTeam(Team t) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String key = SB_PREFIX + t.id().toString().replace("-", "").substring(0, 12);
        org.bukkit.scoreboard.Team st = sb.getTeam(key);
        return st != null ? st : sb.registerNewTeam(key);
    }

    /** Colours names in chat/tab/nametags and adds a [TAG] prefix. Friendly fire is handled by our listener. */
    public void refreshVisuals(Team t) {
        org.bukkit.scoreboard.Team st = sbTeam(t);
        st.color(NamedTextColor.nearestTo(t.primary()));
        st.prefix(t.colorize("[" + t.tag() + "]").append(Component.text(" ")));
        st.setAllowFriendlyFire(true);
        st.setCanSeeFriendlyInvisibles(true);

        Set<String> wanted = new HashSet<>();
        for (UUID u : t.members().keySet()) {
            String n = nameOf(u);
            if (!n.equals("Unknown")) wanted.add(n);
        }
        for (String entry : new HashSet<>(st.getEntries())) {
            if (!wanted.contains(entry)) st.removeEntry(entry);
        }
        for (String n : wanted) {
            if (!st.hasEntry(n)) st.addEntry(n);
        }
    }

    public void refreshAllVisuals() {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        for (org.bukkit.scoreboard.Team st : new ArrayList<>(sb.getTeams())) {
            if (st.getName().startsWith(SB_PREFIX)) st.unregister();
        }
        teams.values().forEach(this::refreshVisuals);
    }

    private void removeVisuals(Team t) {
        Scoreboard sb = Bukkit.getScoreboardManager().getMainScoreboard();
        String key = SB_PREFIX + t.id().toString().replace("-", "").substring(0, 12);
        org.bukkit.scoreboard.Team st = sb.getTeam(key);
        if (st != null) st.unregister();
    }

    // ------------------------------------------------------------------ messaging

    public void broadcast(Team t, Component message) {
        for (UUID u : t.members().keySet()) {
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendMessage(message);
        }
    }

    public void broadcastExcept(Team t, UUID except, Component message) {
        for (UUID u : t.members().keySet()) {
            if (u.equals(except)) continue;
            Player p = Bukkit.getPlayer(u);
            if (p != null) p.sendMessage(message);
        }
    }

    public boolean toggleChat(UUID player) {
        if (!chatToggled.remove(player)) {
            chatToggled.add(player);
            return true;
        }
        return false;
    }

    public boolean isChatToggled(UUID player) { return chatToggled.contains(player); }

    public void teamChat(Player sender, Component message) {
        Team t = teamOf(sender.getUniqueId());
        if (t == null) {
            sender.sendMessage(Msg.err("You are not in a team."));
            return;
        }
        Role role = t.role(sender.getUniqueId());
        Component line = Component.textOfChildren(
                t.colorize("[Team] ").decorate(TextDecoration.BOLD),
                Component.text(role.symbol() + " ", role.color()),
                t.colorize(sender.getName()),
                Component.text(" » ", NamedTextColor.DARK_GRAY),
                message.colorIfAbsent(NamedTextColor.WHITE));
        broadcast(t, line);
        Bukkit.getConsoleSender().sendMessage(line);
    }

    // ------------------------------------------------------------------ create / disband

    public String validateNew(Player p, String name, String tag) {
        if (teamOf(p.getUniqueId()) != null) return "You are already in a team.";
        if (!NAME_PATTERN.matcher(name).matches()) return "Name must be 3–16 letters, numbers or underscores.";
        if (!TAG_PATTERN.matcher(tag).matches()) return "Tag must be 2–4 letters or numbers.";
        if (nameTaken(name, null)) return "That team name is already taken.";
        if (tagTaken(tag, null)) return "That tag is already taken.";
        return null;
    }

    public String validateTag(Team self, String tag) {
        if (!TAG_PATTERN.matcher(tag).matches()) return "Tag must be 2–4 letters or numbers.";
        if (tagTaken(tag, self)) return "That tag is already taken.";
        return null;
    }

    private boolean nameTaken(String name, Team ignore) {
        return teams.values().stream().anyMatch(t -> t != ignore && t.name().equalsIgnoreCase(name));
    }

    private boolean tagTaken(String tag, Team ignore) {
        return teams.values().stream().anyMatch(t -> t != ignore && t.tag().equalsIgnoreCase(tag));
    }

    public Team create(Player owner, String name, String tag, TeamColor color, boolean friendlyFire) {
        Team t = new Team(UUID.randomUUID(), name, tag.toUpperCase(Locale.ROOT), color);
        t.friendlyFire(friendlyFire);
        t.createdAt(System.currentTimeMillis());
        t.members().put(owner.getUniqueId(), Role.OWNER);
        teams.put(t.id(), t);
        playerTeam.put(owner.getUniqueId(), t.id());
        invites.remove(owner.getUniqueId());
        refreshVisuals(t);
        save();
        return t;
    }

    public String disband(Player actor) {
        Team t = teamOf(actor.getUniqueId());
        if (t == null) return "You are not in a team.";
        if (t.role(actor.getUniqueId()) != Role.OWNER) return "Only the owner can disband the team.";

        broadcast(t, Msg.err("Team " + t.name() + " has been disbanded by " + actor.getName() + "."));
        for (UUID u : t.members().keySet()) {
            playerTeam.remove(u);
            chatToggled.remove(u);
        }
        removeVisuals(t);
        teams.remove(t.id());
        invites.values().forEach(list -> list.removeIf(i -> i.teamId().equals(t.id())));
        save();
        return null;
    }

    // ------------------------------------------------------------------ membership

    private void removeMember(Team t, UUID uuid) {
        t.members().remove(uuid);
        playerTeam.remove(uuid);
        chatToggled.remove(uuid);
        cancelWarmup(uuid);
        refreshVisuals(t);
        save();
    }

    public String leave(Player p) {
        Team t = teamOf(p.getUniqueId());
        if (t == null) return "You are not in a team.";
        if (t.role(p.getUniqueId()) == Role.OWNER) {
            return "Owners can't leave. Transfer ownership first, or disband the team.";
        }
        removeMember(t, p.getUniqueId());
        broadcast(t, Msg.info(Component.text(p.getName() + " left the team.", NamedTextColor.YELLOW)));
        return null;
    }

    public String kick(Player actor, UUID target) {
        Team t = teamOf(actor.getUniqueId());
        if (t == null) return "You are not in a team.";
        Role mine = t.role(actor.getUniqueId());
        Role theirs = t.role(target);
        if (theirs == null) return "That player is not in your team.";
        if (target.equals(actor.getUniqueId())) return "Use Leave Team instead.";
        if (!mine.outranks(theirs)) return "You can't kick someone of equal or higher rank.";

        String name = nameOf(target);
        removeMember(t, target);
        Player kicked = Bukkit.getPlayer(target);
        if (kicked != null) kicked.sendMessage(Msg.err("You were kicked from " + t.name() + " by " + actor.getName() + "."));
        broadcast(t, Msg.info(Component.text(name + " was kicked by " + actor.getName() + ".", NamedTextColor.YELLOW)));
        return null;
    }

    public String setRole(Player actor, UUID target, Role newRole) {
        Team t = teamOf(actor.getUniqueId());
        if (t == null) return "You are not in a team.";
        if (t.role(actor.getUniqueId()) != Role.OWNER) return "Only the owner can change ranks.";
        Role current = t.role(target);
        if (current == null) return "That player is not in your team.";
        if (current == Role.OWNER || newRole == Role.OWNER) return "Use Transfer Ownership for that.";
        t.members().put(target, newRole);
        save();
        broadcast(t, Msg.info(Component.text(nameOf(target) + " is now " + newRole.display() + ".", newRole.color())));
        return null;
    }

    public String transfer(Player actor, UUID target) {
        Team t = teamOf(actor.getUniqueId());
        if (t == null) return "You are not in a team.";
        if (t.role(actor.getUniqueId()) != Role.OWNER) return "Only the owner can transfer ownership.";
        if (!t.isMember(target) || target.equals(actor.getUniqueId())) return "Pick another team member.";
        t.members().put(target, Role.OWNER);
        t.members().put(actor.getUniqueId(), Role.OFFICER);
        save();
        broadcast(t, Msg.info(Component.text(nameOf(target) + " is now the team owner!", NamedTextColor.GOLD)));
        return null;
    }

    // ------------------------------------------------------------------ colours

    /** Parses "#RRGGBB" or "RRGGBB". Returns null if blank or invalid. */
    public static TextColor parseHex(String input) {
        if (input == null) return null;
        String h = input.trim();
        if (h.startsWith("#")) h = h.substring(1);
        if (!h.matches("[0-9a-fA-F]{6}")) return null;
        return TextColor.color(Integer.parseInt(h, 16));
    }

    public String validateColors(Player p, String hex, String grad) {
        if (hex != null && !hex.isBlank()) {
            if (!p.hasPermission("teams.color.hex")) return "You don't have permission to use custom hex colours.";
            if (parseHex(hex) == null) return "Invalid hex colour. Use 6 digits like #FF8800.";
        }
        if (grad != null && !grad.isBlank()) {
            if (!p.hasPermission("teams.color.gradient")) return "You don't have permission to use gradients.";
            if (parseHex(grad) == null) return "Invalid gradient colour. Use 6 digits like #00CCFF.";
        }
        return null;
    }

    public void setColors(Team t, TeamColor preset, TextColor custom, TextColor gradientEnd) {
        t.color(preset);
        t.custom(custom);
        t.gradientEnd(gradientEnd);
        refreshVisuals(t);
        save();
    }

    // ------------------------------------------------------------------ reload

    /** Re-reads config.yml and teams.yml from disk. Returns the number of teams loaded. */
    public int reload() {
        plugin.reloadConfig();
        load();
        chatToggled.removeIf(u -> !playerTeam.containsKey(u));
        refreshAllVisuals();
        return teams.size();
    }

    // ------------------------------------------------------------------ settings & home

    public String applySettings(Player actor, String tag, TeamColor color, String hex, String grad, boolean friendlyFire) {
        Team t = teamOf(actor.getUniqueId());
        if (t == null) return "You are not in a team.";
        if (t.role(actor.getUniqueId()) != Role.OWNER) return "Only the owner can change settings.";
        String err = validateTag(t, tag);
        if (err != null) return err;
        err = validateColors(actor, hex, grad);
        if (err != null) return err;
        t.tag(tag.toUpperCase(Locale.ROOT));
        t.color(color);
        t.custom(parseHex(hex));
        t.gradientEnd(parseHex(grad));
        t.friendlyFire(friendlyFire);
        refreshVisuals(t);
        save();
        broadcast(t, Msg.info(Component.text("Team settings updated (friendly fire "
                + (friendlyFire ? "ON" : "OFF") + ").", NamedTextColor.YELLOW)));
        return null;
    }

    public String setHome(Player p) {
        Team t = teamOf(p.getUniqueId());
        if (t == null) return "You are not in a team.";
        if (!t.role(p.getUniqueId()).canSetHome()) return "Only officers and the owner can set the team home.";
        t.home(Team.Home.of(p.getLocation()));
        save();
        return null;
    }

    public void teleportHome(Player p) {
        Team t = teamOf(p.getUniqueId());
        if (t == null) {
            p.sendMessage(Msg.err("You are not in a team."));
            return;
        }
        Team.Home h = t.home();
        Location dest = h == null ? null : h.toLocation();
        if (dest == null) {
            p.sendMessage(Msg.err("Your team has no home set (or its world isn't loaded)."));
            return;
        }

        cancelWarmup(p.getUniqueId());
        int secs = plugin.getConfig().getInt("home-warmup-seconds", 3);
        if (secs <= 0 || p.hasPermission("teams.bypass.warmup")) {
            arrive(p, dest);
            return;
        }

        Location start = p.getLocation();
        int[] left = {secs};
        UUID id = p.getUniqueId();
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (!p.isOnline()) {
                cancelWarmup(id);
                return;
            }
            Location now = p.getLocation();
            if (now.getWorld() != start.getWorld() || now.distanceSquared(start) > 1.0) {
                cancelWarmup(id);
                p.sendActionBar(Component.text("Teleport cancelled – you moved!", NamedTextColor.RED));
                return;
            }
            if (left[0] <= 0) {
                cancelWarmup(id);
                arrive(p, dest);
                return;
            }
            p.sendActionBar(Component.text("Teleporting to team home in " + left[0] + "s… stand still!",
                    NamedTextColor.YELLOW));
            p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 20, 0.4, 0.6, 0.4, 0.2);
            left[0]--;
        }, 0L, 20L);
        warmups.put(id, task);
    }

    private void arrive(Player p, Location dest) {
        p.teleportAsync(dest);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        p.sendActionBar(Component.text("Welcome home!", NamedTextColor.GREEN));
    }

    public void cancelWarmup(UUID id) {
        BukkitTask t = warmups.remove(id);
        if (t != null) t.cancel();
    }

    // ------------------------------------------------------------------ invites

    public List<Invite> pendingInvites(UUID player) {
        List<Invite> list = invites.get(player);
        if (list == null) return List.of();
        long now = System.currentTimeMillis();
        list.removeIf(i -> i.expiresAt() < now || !teams.containsKey(i.teamId()));
        return List.copyOf(list);
    }

    public String invite(Player actor, Player target) {
        Team t = teamOf(actor.getUniqueId());
        if (t == null) return "You are not in a team.";
        if (!t.role(actor.getUniqueId()).canInvite()) return "Only officers and the owner can invite players.";
        if (teamOf(target.getUniqueId()) != null) return target.getName() + " is already in a team.";
        if (t.members().size() >= maxMembers()) return "Your team is full (" + maxMembers() + " members).";

        long expiry = System.currentTimeMillis()
                + plugin.getConfig().getLong("invite-expire-seconds", 60) * 1000L;
        List<Invite> list = invites.computeIfAbsent(target.getUniqueId(), k -> new ArrayList<>());
        list.removeIf(i -> i.teamId().equals(t.id()));
        list.add(new Invite(t.id(), actor.getUniqueId(), expiry));

        Component open = Component.text("[Click to view]", NamedTextColor.GREEN, TextDecoration.BOLD)
                .clickEvent(ClickEvent.runCommand("/team invites"));
        target.sendMessage(Msg.info(Component.textOfChildren(
                Component.text(actor.getName(), NamedTextColor.WHITE),
                Component.text(" invited you to join ", NamedTextColor.GRAY),
                t.colorize(t.name()),
                Component.text("! ", NamedTextColor.GRAY),
                open)));
        target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.4f);
        return null;
    }

    public String accept(Player p, UUID teamId) {
        if (teamOf(p.getUniqueId()) != null) return "You are already in a team.";
        boolean valid = pendingInvites(p.getUniqueId()).stream().anyMatch(i -> i.teamId().equals(teamId));
        Team t = teams.get(teamId);
        if (!valid || t == null) return "That invite has expired.";
        if (t.members().size() >= maxMembers()) return "That team is full.";

        t.members().put(p.getUniqueId(), Role.MEMBER);
        playerTeam.put(p.getUniqueId(), t.id());
        invites.remove(p.getUniqueId());
        refreshVisuals(t);
        save();
        broadcast(t, Msg.info(Component.text(p.getName() + " joined the team! Welcome!", NamedTextColor.GREEN)));
        return null;
    }

    public void decline(Player p, UUID teamId) {
        List<Invite> list = invites.get(p.getUniqueId());
        if (list != null) list.removeIf(i -> i.teamId().equals(teamId));
    }

    // ------------------------------------------------------------------ misc

    public void addKill(Team t) {
        t.kills(t.kills() + 1);
        save();
    }
}
