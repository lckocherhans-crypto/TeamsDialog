package com.example.teams;

import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;

/**
 * PlaceholderAPI expansion so other plugins (TAB, scoreboards, chat formatters) can show team info.
 *
 *   %teams_tag%     ABC            (empty if no team)
 *   %teams_prefix%  &#FF5555[ABC]&r      (coloured tag + trailing space, empty if no team)
 *   %teams_color%   &#FF5555            (team colour code, empty if no team)
 *   %teams_name%    TeamName
 *   %teams_role%    Owner / Officer / Member
 *   %teams_kills%   12
 *   %teams_members% 3
 */
public final class TeamsExpansion extends PlaceholderExpansion {

    private final TeamManager mgr;

    public TeamsExpansion(TeamManager mgr) {
        this.mgr = mgr;
    }

    @Override public @NotNull String getIdentifier() { return "teams"; }
    @Override public @NotNull String getAuthor() { return "TeamsDialog"; }
    @Override public @NotNull String getVersion() { return "1.0.0"; }
    @Override public boolean persist() { return true; }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";
        Team t = mgr.teamOf(player.getUniqueId());
        if (t == null) return "";

        return switch (params.toLowerCase()) {
            case "tag" -> t.tag();
            case "color" -> t.primaryCode();
            case "prefix" -> {
    String start = t.primary().asHexString();
    if (t.gradientEnd() != null) {
        String end = t.gradientEnd().asHexString();
        yield "<gradient:" + start + ":" + end + ">[" + t.tag() + "]</gradient> ";
    }
    yield "<color:" + start + ">[" + t.tag() + "]</color> ";
}
            case "name" -> t.name();
            case "role" -> t.role(player.getUniqueId()).display();
            case "kills" -> String.valueOf(t.kills());
            case "members" -> String.valueOf(t.members().size());
            default -> null;
        };
    }
}
