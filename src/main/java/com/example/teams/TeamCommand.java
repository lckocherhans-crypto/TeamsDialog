package com.example.teams;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class TeamCommand implements CommandExecutor, TabCompleter {

    private static final List<String> SUB = List.of("create", "invite", "members", "invites", "leave", "disband", "top", "home", "chat");

    private final TeamManager mgr;
    private final TeamDialogs dialogs;

    public TeamCommand(TeamManager mgr, TeamDialogs dialogs) {
        this.mgr = mgr;
        this.dialogs = dialogs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("team") && args.length >= 1 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("teams.admin")) {
                sender.sendMessage(Msg.err("You don't have permission to do that."));
                return true;
            }
            int n = mgr.reload();
            sender.sendMessage(Msg.ok("Reloaded config.yml and teams.yml (" + n + " teams)."));
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Only players can use team commands.");
            return true;
        }

        if (command.getName().equalsIgnoreCase("tc")) {
            if (args.length == 0) {
                if (mgr.teamOf(p.getUniqueId()) == null) {
                    p.sendMessage(Msg.err("You are not in a team."));
                    return true;
                }
                boolean on = mgr.toggleChat(p.getUniqueId());
                p.sendMessage(Msg.info("Team chat " + (on ? "enabled – your messages go to the team." : "disabled.")));
            } else {
                mgr.teamChat(p, Component.text(String.join(" ", args)));
            }
            return true;
        }

        if (args.length == 0) {
            dialogs.openMain(p);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "create" -> dialogs.openCreate(p);
            case "invite" -> dialogs.openInviteList(p);
            case "members", "view" -> dialogs.openMembers(p);
            case "leave" -> dialogs.openLeave(p);
            case "disband" -> dialogs.openDisband(p);
            case "invites" -> dialogs.openInvites(p);
            case "top" -> dialogs.openTop(p);
            case "home" -> mgr.teleportHome(p);
            case "chat" -> {
                if (args.length < 2) p.sendMessage(Msg.err("Usage: /team chat <message>"));
                else mgr.teamChat(p, Component.text(String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length))));
            }
            default -> dialogs.openMain(p);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("team") && args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> options = new java.util.ArrayList<>(SUB);
            if (sender.hasPermission("teams.admin")) options.add("reload");
            return options.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
