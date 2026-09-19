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

    private static final List<String> SUB = List.of("invites", "top", "home", "chat");

    private final TeamManager mgr;
    private final TeamDialogs dialogs;

    public TeamCommand(TeamManager mgr, TeamDialogs dialogs) {
        this.mgr = mgr;
        this.dialogs = dialogs;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
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
            return SUB.stream().filter(s -> s.startsWith(prefix)).toList();
        }
        return List.of();
    }
}
