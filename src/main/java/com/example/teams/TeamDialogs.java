package com.example.teams;

import io.papermc.paper.dialog.Dialog;
import io.papermc.paper.dialog.DialogResponseView;
import io.papermc.paper.registry.data.dialog.ActionButton;
import io.papermc.paper.registry.data.dialog.DialogBase;
import io.papermc.paper.registry.data.dialog.action.DialogAction;
import io.papermc.paper.registry.data.dialog.body.DialogBody;
import io.papermc.paper.registry.data.dialog.input.DialogInput;
import io.papermc.paper.registry.data.dialog.input.SingleOptionDialogInput;
import io.papermc.paper.registry.data.dialog.type.DialogType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickCallback;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

/** Every screen of the plugin is a native Minecraft dialog. */
public final class TeamDialogs {

    private static final ClickCallback.Options OPTS = ClickCallback.Options.builder()
            .uses(ClickCallback.UNLIMITED_USES)
            .lifetime(Duration.ofMinutes(15))
            .build();

    private static final String[] ARROWS = {"↑", "↗", "→", "↘", "↓", "↙", "←", "↖"};

    private final TeamsPlugin plugin;
    private final TeamManager mgr;

    public TeamDialogs(TeamsPlugin plugin, TeamManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    // =================================================================== helpers

    private static Component text(String s, TextColor c) { return Component.text(s, c); }

    private static DialogBody line(Component c) { return DialogBody.plainMessage(c); }

    /** Removes all colour from a button label (recursively) so buttons use the default Minecraft look. */
    private static Component plain(Component c) {
        return c.color(null).children(c.children().stream().map(TeamDialogs::plain).toList());
    }

    private ActionButton formButton(Component label, Component tooltip, int width,
                                    BiConsumer<Player, DialogResponseView> action) {
        return ActionButton.create(plain(label), tooltip, width,
                DialogAction.customClick((view, audience) -> {
                    if (audience instanceof Player p) action.accept(p, view);
                }, OPTS));
    }

    private ActionButton button(Component label, Component tooltip, int width, Consumer<Player> action) {
        return formButton(label, tooltip, width, (p, v) -> action.accept(p));
    }

    private ActionButton button(Component label, Component tooltip, Consumer<Player> action) {
        return button(label, tooltip, 200, action);
    }

    private ActionButton backButton(Consumer<Player> target) {
        return button(text("« Back", NamedTextColor.GRAY), null, target);
    }

    private ActionButton closeButton() {
        return ActionButton.create(Component.text("Close"), null, 200, null);
    }

    private Dialog dialog(Component title, List<DialogBody> body, List<DialogInput> inputs, DialogType type) {
        return Dialog.create(f -> f.empty()
                .base(DialogBase.builder(title)
                        .canCloseWithEscape(true)
                        .body(body)
                        .inputs(inputs)
                        .build())
                .type(type));
    }

    private Dialog menu(Component title, List<DialogBody> body, List<ActionButton> buttons,
                        ActionButton exit, int columns) {
        // Paper rejects a multi-action dialog with zero actions, so fall back to a notice.
        if (buttons.isEmpty()) {
            return dialog(title, body, List.of(), DialogType.notice(exit));
        }
        return dialog(title, body, List.of(), DialogType.multiAction(buttons, exit, columns));
    }

    private void confirm(Player p, Component title, Component message, Component yesLabel,
                         Consumer<Player> onYes, Consumer<Player> onNo) {
        p.showDialog(dialog(title, List.of(line(message)), List.of(),
                DialogType.confirmation(
                        button(yesLabel, null, 150, onYes),
                        button(text("Cancel", NamedTextColor.GRAY), null, 150, onNo))));
    }

    private void result(Player p, String error, String success) {
        if (error != null) {
            p.sendMessage(Msg.err(error));
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
        } else {
            p.sendMessage(Msg.ok(success));
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
        }
    }

    private static String clean(String s) { return s == null ? "" : s.trim(); }

    private static Component title(String s, TextColor c) {
        return Component.text(s, c, TextDecoration.BOLD);
    }

    // =================================================================== main menu

    public void openMain(Player p) {
        Team t = mgr.teamOf(p.getUniqueId());
        if (t == null) openNoTeam(p);
        else openTeamMenu(p, t);
    }

    /**
     * Back/Cancel target. Opens the dialog set in config.yml ("back-dialog", default asgard:teams),
     * which can be a datapack dialog. If it is blank or invalid, falls back to the plugin's own menu.
     */
    public void goBack(Player p) {
        String id = plugin.getConfig().getString("back-dialog", "asgard:teams");
        if (id == null || id.isBlank() || !id.matches("[a-z0-9_.\\-]+:[a-z0-9_./\\-]+")) {
            openMain(p);
            return;
        }
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "dialog show " + p.getName() + " " + id);
    }

    // ---- entry points used by /team <subcommand> (and datapack dialogs) ----

    public void openCreate(Player p) {
        if (mgr.teamOf(p.getUniqueId()) != null) {
            p.sendMessage(Msg.err("You are already in a team."));
            openMain(p);
            return;
        }
        openCreate(p, null, "", "", TeamColor.AQUA, "", "", plugin.getConfig().getBoolean("default-friendly-fire", false));
    }

    public void openInviteList(Player p) {
        Team t = mgr.teamOf(p.getUniqueId());
        if (t == null) {
            p.sendMessage(Msg.err("You are not in a team."));
            return;
        }
        if (!t.role(p.getUniqueId()).canInvite()) {
            p.sendMessage(Msg.err("Only officers and the owner can invite players."));
            return;
        }
        openInviteList(p, null);
    }

    public void openLeave(Player p) {
        Team t = mgr.teamOf(p.getUniqueId());
        if (t == null) {
            p.sendMessage(Msg.err("You are not in a team."));
            return;
        }
        if (t.role(p.getUniqueId()) == Role.OWNER) {
            p.sendMessage(Msg.err("Owners can't leave. Transfer ownership first, or disband the team."));
            return;
        }
        confirm(p, title("Leave team?", NamedTextColor.RED),
                text("You will leave " + t.name() + ".", NamedTextColor.GRAY),
                text("Leave", NamedTextColor.RED),
                pp -> {
                    result(pp, mgr.leave(pp), "You left the team.");
                    openMain(pp);
                },
                this::goBack);
    }

    public void openDisband(Player p) {
        Team t = mgr.teamOf(p.getUniqueId());
        if (t == null) {
            p.sendMessage(Msg.err("You are not in a team."));
            return;
        }
        if (t.role(p.getUniqueId()) != Role.OWNER) {
            p.sendMessage(Msg.err("Only the owner can disband the team."));
            return;
        }
        confirm(p, title("Disband team?", NamedTextColor.RED),
                text("This deletes " + t.name() + " for everyone. It cannot be undone.", NamedTextColor.GRAY),
                text("Disband", NamedTextColor.RED),
                pp -> {
                    String err = mgr.disband(pp);
                    if (err != null) pp.sendMessage(Msg.err(err));
                    openMain(pp);
                },
                this::goBack);
    }

    private void openNoTeam(Player p) {
        int invites = mgr.pendingInvites(p.getUniqueId()).size();
        List<DialogBody> body = List.of(
                line(text("You're not in a team yet.", NamedTextColor.GRAY)),
                line(text("Create your own, or accept an invite from a friend!", NamedTextColor.DARK_GRAY)));

        List<ActionButton> buttons = new ArrayList<>();
        buttons.add(button(text("Create Team", NamedTextColor.GREEN),
                text("Pick a name, tag and colour", NamedTextColor.GRAY),
                pl -> openCreate(pl, null, "", "", TeamColor.AQUA, "", "",
                        plugin.getConfig().getBoolean("default-friendly-fire", false))));
        buttons.add(button(text("Invites (" + invites + ")", invites > 0 ? NamedTextColor.YELLOW : NamedTextColor.GRAY),
                text("View pending team invites", NamedTextColor.GRAY), this::openInvites));
        buttons.add(button(text("Top Teams", NamedTextColor.GOLD),
                text("The kill leaderboard", NamedTextColor.GRAY), this::openTop));

        p.showDialog(menu(title("Teams", NamedTextColor.AQUA), body, buttons, backButton(this::goBack), 1));
    }

    private void openTeamMenu(Player p, Team t) {
        Role role = t.role(p.getUniqueId());
        TextColor c = t.primary();
        long online = t.members().keySet().stream().filter(u -> Bukkit.getPlayer(u) != null).count();
        boolean chatOn = mgr.isChatToggled(p.getUniqueId());

        List<DialogBody> body = List.of(
                line(Component.textOfChildren(
                        t.colorize("[" + t.tag() + "] "),
                        t.colorize(t.name()).decorate(TextDecoration.BOLD))),
                line(Component.textOfChildren(
                        text("Rank ", NamedTextColor.GRAY), text(role.display(), role.color()),
                        text("  ·  Members ", NamedTextColor.GRAY),
                        text(t.members().size() + "/" + mgr.maxMembers(), NamedTextColor.WHITE),
                        text(" (" + online + " online)", NamedTextColor.DARK_GRAY))),
                line(Component.textOfChildren(
                        text("Kills ", NamedTextColor.GRAY), text(String.valueOf(t.kills()), NamedTextColor.GOLD),
                        text("  ·  Home ", NamedTextColor.GRAY),
                        t.home() != null ? text("set", NamedTextColor.GREEN) : text("not set", NamedTextColor.RED),
                        text("  ·  PvP ", NamedTextColor.GRAY),
                        t.friendlyFire() ? text("friendly", NamedTextColor.RED) : text("protected", NamedTextColor.GREEN))),
                line(text("Team chat: " + (chatOn ? "ON – all your messages go to the team" : "OFF"),
                        chatOn ? NamedTextColor.GREEN : NamedTextColor.DARK_GRAY)));

        List<ActionButton> b = new ArrayList<>();
        b.add(button(text("Members & Radar", NamedTextColor.AQUA),
                text("See teammates, distance and direction", NamedTextColor.GRAY), this::openMembers));
        b.add(button(text("Team Home", NamedTextColor.GREEN),
                text("Teleport home (stand still)", NamedTextColor.GRAY), pl -> mgr.teleportHome(pl)));
        if (role.canSetHome()) {
            b.add(button(text("Set Home", NamedTextColor.DARK_GREEN),
                    text("Use your current location", NamedTextColor.GRAY), pl -> {
                        result(pl, mgr.setHome(pl), "Team home set to your current location.");
                        openMain(pl);
                    }));
        }
        if (role.canInvite()) {
            b.add(button(text("Invite Player", NamedTextColor.YELLOW),
                    text("Pick from players without a team", NamedTextColor.GRAY), pl -> openInviteList(pl, null)));
        }
        b.add(button(text("Team Chat: " + (chatOn ? "ON" : "OFF"), chatOn ? NamedTextColor.GREEN : NamedTextColor.GRAY),
                text("Toggle private team chat (or use /tc)", NamedTextColor.GRAY), pl -> {
                    boolean on = mgr.toggleChat(pl.getUniqueId());
                    pl.sendMessage(Msg.info("Team chat " + (on ? "enabled – your messages go to the team." : "disabled.")));
                    openMain(pl);
                }));
        b.add(button(text("Top Teams", NamedTextColor.GOLD),
                text("The kill leaderboard", NamedTextColor.GRAY), this::openTop));

        if (role == Role.OWNER) {
            b.add(button(text("Settings", NamedTextColor.LIGHT_PURPLE),
                    text("Colour, tag and friendly fire", NamedTextColor.GRAY), this::openSettings));
            b.add(button(text("Disband Team", NamedTextColor.RED),
                    text("Permanently delete the team", NamedTextColor.GRAY), pl -> confirm(pl,
                            title("Disband team?", NamedTextColor.RED),
                            text("This deletes " + t.name() + " for everyone. It cannot be undone.", NamedTextColor.GRAY),
                            text("Disband", NamedTextColor.RED),
                            pp -> {
                                String err = mgr.disband(pp);
                                if (err != null) pp.sendMessage(Msg.err(err));
                                openMain(pp);
                            },
                            this::goBack)));
        } else {
            b.add(button(text("Leave Team", NamedTextColor.RED),
                    text("Leave " + t.name(), NamedTextColor.GRAY), pl -> confirm(pl,
                            title("Leave team?", NamedTextColor.RED),
                            text("You will leave " + t.name() + ".", NamedTextColor.GRAY),
                            text("Leave", NamedTextColor.RED),
                            pp -> {
                                result(pp, mgr.leave(pp), "You left the team.");
                                openMain(pp);
                            },
                            this::goBack)));
        }

        p.showDialog(menu(title(t.name(), c), body, b, backButton(this::goBack), 2));
    }

    // =================================================================== create

    private void openCreate(Player p, String error, String name, String tag, TeamColor color,
                            String hex, String grad, boolean ff) {
        List<DialogBody> body = new ArrayList<>();
        if (error != null) body.add(line(text("✖ " + error, NamedTextColor.RED)));
        body.add(line(text("Name: 3–16 letters/numbers/_   ·   Tag: 2–4 letters/numbers", NamedTextColor.GRAY)));
        body.add(line(text("Pick a colour, or type your own hex code. Add a gradient end colour for a gradient tag.",
                NamedTextColor.DARK_GRAY)));

        List<SingleOptionDialogInput.OptionEntry> colors = new ArrayList<>();
        for (TeamColor tc : TeamColor.values()) {
            colors.add(SingleOptionDialogInput.OptionEntry.create(
                    tc.id(), Component.text(tc.display(), tc.color()), tc == color));
        }

        List<DialogInput> inputs = List.of(
                DialogInput.text("name", Component.text("Team name")).width(250).initial(name).maxLength(16).build(),
                DialogInput.text("tag", Component.text("Tag")).width(120).initial(tag).maxLength(4).build(),
                DialogInput.singleOption("color", Component.text("Team colour"), colors).width(250).build(),
                DialogInput.text("hex", Component.text("Custom hex (optional, e.g. #FF8800)")).width(250).initial(hex).maxLength(7).build(),
                DialogInput.text("grad", Component.text("Gradient end (optional, e.g. #00CCFF)")).width(250).initial(grad).maxLength(7).build(),
                DialogInput.bool("ff", Component.text("Allow friendly fire")).initial(ff).build());

        ActionButton create = formButton(text("Create", NamedTextColor.GREEN), null, 150, (pl, view) -> {
            String n = clean(view.getText("name"));
            String tg = clean(view.getText("tag"));
            TeamColor c = TeamColor.fromId(view.getText("color"));
            String hx = clean(view.getText("hex"));
            String gr = clean(view.getText("grad"));
            boolean f = Boolean.TRUE.equals(view.getBoolean("ff"));

            String err = mgr.validateNew(pl, n, tg);
            if (err == null) err = mgr.validateColors(pl, hx, gr);
            if (err != null) {
                openCreate(pl, err, n, tg, c, hx, gr, f);
                return;
            }
            Team t = mgr.create(pl, n, tg, c, f);
            mgr.setColors(t, c, TeamManager.parseHex(hx), TeamManager.parseHex(gr));
            pl.sendMessage(Msg.ok("Team " + t.name() + " created! Invite some friends."));
            pl.playSound(pl.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
            openMain(pl);
        });

        p.showDialog(dialog(title("Create a Team", NamedTextColor.GREEN), body, inputs,
                DialogType.confirmation(create,
                        button(text("Cancel", NamedTextColor.GRAY), null, 150, this::goBack))));
    }

    // =================================================================== settings

    private void openSettings(Player p) {
        Team t = mgr.teamOf(p.getUniqueId());
        if (t == null || t.role(p.getUniqueId()) != Role.OWNER) {
            openMain(p);
            return;
        }
        openSettings(p, t, null);
    }

    private void openSettings(Player p, Team t, String error) {
        List<DialogBody> body = new ArrayList<>();
        if (error != null) body.add(line(text("✖ " + error, NamedTextColor.RED)));
        body.add(line(Component.textOfChildren(text("Current: ", NamedTextColor.GRAY),
                t.colorize("[" + t.tag() + "] " + t.name()))));
        body.add(line(text("Changes apply instantly to nametags, tab list and chat. "
                + "Leave hex blank to use the picked colour.", NamedTextColor.GRAY)));

        List<SingleOptionDialogInput.OptionEntry> colors = new ArrayList<>();
        for (TeamColor tc : TeamColor.values()) {
            colors.add(SingleOptionDialogInput.OptionEntry.create(
                    tc.id(), Component.text(tc.display(), tc.color()), tc == t.color()));
        }
        String hexNow = t.custom() != null ? t.custom().asHexString() : "";
        String gradNow = t.gradientEnd() != null ? t.gradientEnd().asHexString() : "";

        List<DialogInput> inputs = List.of(
                DialogInput.text("tag", Component.text("Tag")).width(120).initial(t.tag()).maxLength(4).build(),
                DialogInput.singleOption("color", Component.text("Team colour"), colors).width(250).build(),
                DialogInput.text("hex", Component.text("Custom hex (optional, e.g. #FF8800)")).width(250).initial(hexNow).maxLength(7).build(),
                DialogInput.text("grad", Component.text("Gradient end (optional, e.g. #00CCFF)")).width(250).initial(gradNow).maxLength(7).build(),
                DialogInput.bool("ff", Component.text("Allow friendly fire")).initial(t.friendlyFire()).build());

        ActionButton save = formButton(text("Save", NamedTextColor.GREEN), null, 150, (pl, view) -> {
            String err = mgr.applySettings(pl,
                    clean(view.getText("tag")),
                    TeamColor.fromId(view.getText("color")),
                    clean(view.getText("hex")),
                    clean(view.getText("grad")),
                    Boolean.TRUE.equals(view.getBoolean("ff")));
            if (err != null) {
                Team cur = mgr.teamOf(pl.getUniqueId());
                if (cur == null) openMain(pl);
                else openSettings(pl, cur, err);
                return;
            }
            pl.playSound(pl.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            openMain(pl);
        });

        p.showDialog(dialog(title("Team Settings", NamedTextColor.LIGHT_PURPLE), body, inputs,
                DialogType.confirmation(save,
                        button(text("Cancel", NamedTextColor.GRAY), null, 150, this::goBack))));
    }

    // =================================================================== members & radar

    public void openMembers(Player p) {
        Team t = mgr.teamOf(p.getUniqueId());
        if (t == null) {
            openMain(p);
            return;
        }
        Role mine = t.role(p.getUniqueId());

        List<UUID> sorted = new ArrayList<>(t.members().keySet());
        sorted.sort(Comparator.comparingInt((UUID u) -> t.role(u).ordinal())
                .thenComparing(mgr::nameOf, String.CASE_INSENSITIVE_ORDER));

        List<DialogBody> body = new ArrayList<>();
        List<ActionButton> buttons = new ArrayList<>();
        for (UUID u : sorted) {
            Role r = t.role(u);
            String name = mgr.nameOf(u);
            Player online = Bukkit.getPlayer(u);

            Component status;
            if (online == null) {
                status = text("○ offline", NamedTextColor.DARK_GRAY);
            } else if (u.equals(p.getUniqueId())) {
                status = text("● you", NamedTextColor.GREEN);
            } else if (online.getWorld().equals(p.getWorld())) {
                int dist = (int) online.getLocation().distance(p.getLocation());
                status = text("● " + dist + "m " + arrow(p.getLocation(), online.getLocation()), NamedTextColor.GREEN);
            } else {
                status = text("● " + online.getWorld().getName(), NamedTextColor.YELLOW);
            }

            body.add(line(Component.textOfChildren(
                    text(r.symbol() + " ", r.color()),
                    t.colorize(name),
                    text("   ", NamedTextColor.GRAY),
                    status)));

            if (!u.equals(p.getUniqueId()) && mine.outranks(r)) {
                buttons.add(button(text("Manage " + name, NamedTextColor.YELLOW),
                        text(r.display(), r.color()), pl -> openManage(pl, u)));
            }
        }
        if (buttons.isEmpty()) {
            body.add(line(text("Radar updates each time you open this page.", NamedTextColor.DARK_GRAY)));
        }

        p.showDialog(menu(title("Members & Radar", t.primary()), body, buttons, backButton(this::goBack), 1));
    }

    /** Compass arrow relative to where the viewer is currently facing. */
    private static String arrow(Location from, Location to) {
        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double targetYaw = Math.toDegrees(Math.atan2(-dx, dz));
        double rel = ((targetYaw - from.getYaw()) % 360 + 360) % 360;
        return ARROWS[(int) Math.round(rel / 45.0) % 8];
    }

    private void openManage(Player p, UUID target) {
        Team t = mgr.teamOf(p.getUniqueId());
        if (t == null) {
            openMain(p);
            return;
        }
        Role mine = t.role(p.getUniqueId());
        Role theirs = t.role(target);
        if (theirs == null || !mine.outranks(theirs)) {
            openMembers(p);
            return;
        }
        String name = mgr.nameOf(target);

        List<DialogBody> body = List.of(line(Component.textOfChildren(
                t.colorize(name), text("  ·  ", NamedTextColor.DARK_GRAY),
                text(theirs.display(), theirs.color()))));

        List<ActionButton> b = new ArrayList<>();
        if (mine == Role.OWNER) {
            if (theirs == Role.MEMBER) {
                b.add(button(text("Promote to Officer", NamedTextColor.GREEN),
                        text("Officers can invite and set the home", NamedTextColor.GRAY), pl -> {
                            result(pl, mgr.setRole(pl, target, Role.OFFICER), name + " promoted to Officer.");
                            openMembers(pl);
                        }));
            } else if (theirs == Role.OFFICER) {
                b.add(button(text("Demote to Member", NamedTextColor.YELLOW), null, pl -> {
                    result(pl, mgr.setRole(pl, target, Role.MEMBER), name + " demoted to Member.");
                    openMembers(pl);
                }));
            }
            b.add(button(text("Transfer Ownership", NamedTextColor.GOLD),
                    text("You become an Officer", NamedTextColor.GRAY), pl -> confirm(pl,
                            title("Transfer ownership?", NamedTextColor.GOLD),
                            text("Make " + name + " the owner of " + t.name() + "? You'll become an Officer.",
                                    NamedTextColor.GRAY),
                            text("Transfer", NamedTextColor.GOLD),
                            pp -> {
                                result(pp, mgr.transfer(pp, target), "Ownership transferred to " + name + ".");
                                openMain(pp);
                            },
                            this::openMembers)));
        }
        b.add(button(text("Kick", NamedTextColor.RED), null, pl -> {
            result(pl, mgr.kick(pl, target), name + " was kicked.");
            openMembers(pl);
        }));

        p.showDialog(menu(title("Manage " + name, NamedTextColor.YELLOW), body, b,
                backButton(this::openMembers), 1));
    }

    // =================================================================== inviting

    private void openInviteList(Player p, String notice) {
        Team t = mgr.teamOf(p.getUniqueId());
        if (t == null || !t.role(p.getUniqueId()).canInvite()) {
            openMain(p);
            return;
        }

        List<DialogBody> body = new ArrayList<>();
        if (notice != null) body.add(line(text(notice, NamedTextColor.GREEN)));
        body.add(line(text("Click a player to send an invite. Invites expire after "
                + plugin.getConfig().getLong("invite-expire-seconds", 60) + "s.", NamedTextColor.GRAY)));

        List<ActionButton> buttons = new ArrayList<>();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.equals(p) || mgr.teamOf(other.getUniqueId()) != null) continue;
            if (buttons.size() >= 30) break;
            String name = other.getName();
            UUID id = other.getUniqueId();
            buttons.add(button(text(name, NamedTextColor.WHITE), null, 150, pl -> {
                Player target = Bukkit.getPlayer(id);
                if (target == null) {
                    pl.sendMessage(Msg.err(name + " went offline."));
                    openInviteList(pl, null);
                    return;
                }
                String err = mgr.invite(pl, target);
                if (err != null) {
                    pl.sendMessage(Msg.err(err));
                    openInviteList(pl, null);
                } else {
                    openInviteList(pl, "✔ Invite sent to " + name + ".");
                }
            }));
        }
        if (buttons.isEmpty()) {
            body.add(line(text("No one without a team is online right now.", NamedTextColor.DARK_GRAY)));
        }

        p.showDialog(menu(title("Invite Player", NamedTextColor.YELLOW), body, buttons,
                backButton(this::goBack), 2));
    }

    public void openInvites(Player p) {
        if (mgr.teamOf(p.getUniqueId()) != null) {
            p.sendMessage(Msg.err("You are already in a team."));
            openMain(p);
            return;
        }
        List<TeamManager.Invite> invites = mgr.pendingInvites(p.getUniqueId());

        List<DialogBody> body = new ArrayList<>();
        List<ActionButton> buttons = new ArrayList<>();
        if (invites.isEmpty()) {
            body.add(line(text("You have no pending invites.", NamedTextColor.GRAY)));
        } else {
            body.add(line(text("Pick an invite to review:", NamedTextColor.GRAY)));
        }
        for (TeamManager.Invite inv : invites) {
            Team t = mgr.byId(inv.teamId());
            if (t == null) continue;
            String from = mgr.nameOf(inv.inviter());
            buttons.add(button(
                    Component.textOfChildren(t.colorize("[" + t.tag() + "] "),
                            text(t.name(), NamedTextColor.WHITE)),
                    text("Invited by " + from, NamedTextColor.GRAY),
                    pl -> openInviteDetail(pl, t.id())));
        }
        p.showDialog(menu(title("Team Invites", NamedTextColor.YELLOW), body, buttons,
                backButton(this::goBack), 1));
    }

    private void openInviteDetail(Player p, UUID teamId) {
        Team t = mgr.byId(teamId);
        if (t == null) {
            openInvites(p);
            return;
        }
        long online = t.members().keySet().stream().filter(u -> Bukkit.getPlayer(u) != null).count();
        Component info = Component.textOfChildren(
                text("Join ", NamedTextColor.GRAY),
                t.colorize("[" + t.tag() + "] " + t.name()),
                text("?\n" + t.members().size() + " members (" + online + " online)  ·  "
                        + t.kills() + " kills", NamedTextColor.GRAY));

        p.showDialog(dialog(title("Team Invite", t.primary()), List.of(line(info)), List.of(),
                DialogType.confirmation(
                        button(text("Accept", NamedTextColor.GREEN), null, 150, pl -> {
                            String err = mgr.accept(pl, teamId);
                            if (err != null) {
                                pl.sendMessage(Msg.err(err));
                                openInvites(pl);
                            } else {
                                pl.playSound(pl.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                                openMain(pl);
                            }
                        }),
                        button(text("Decline", NamedTextColor.RED), null, 150, pl -> {
                            mgr.decline(pl, teamId);
                            openInvites(pl);
                        }))));
    }

    // =================================================================== leaderboard

    public void openTop(Player p) {
        Team mine = mgr.teamOf(p.getUniqueId());
        List<Team> top = mgr.top(10);

        List<DialogBody> body = new ArrayList<>();
        if (top.isEmpty()) {
            body.add(line(text("No teams yet – be the first!", NamedTextColor.GRAY)));
        }
        String[] medals = {"#1", "#2", "#3"};
        NamedTextColor[] medalColors = {NamedTextColor.GOLD, NamedTextColor.GRAY, NamedTextColor.RED};
        for (int i = 0; i < top.size(); i++) {
            Team t = top.get(i);
            String rank = i < 3 ? medals[i] : "#" + (i + 1);
            TextColor rankColor = i < 3 ? medalColors[i] : NamedTextColor.DARK_GRAY;
            Component row = Component.textOfChildren(
                    text(rank + " ", rankColor),
                    t.colorize("[" + t.tag() + "] "),
                    text(t.name(), NamedTextColor.WHITE),
                    text("  " + t.kills() + " kills · " + t.members().size() + " members", NamedTextColor.GRAY),
                    t == mine ? text("  ◄ you", NamedTextColor.GREEN) : Component.empty());
            body.add(line(row));
        }

        p.showDialog(dialog(title("Top Teams", NamedTextColor.GOLD), body, List.of(),
                DialogType.notice(button(text("« Back", NamedTextColor.GRAY), null, 150, this::goBack))));
    }
}
