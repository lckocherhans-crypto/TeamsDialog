package com.example.teams;

import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.java.JavaPlugin;

public final class TeamsPlugin extends JavaPlugin {

    private TeamManager manager;

    @Override
    public void onEnable() {
        saveDefaultConfig();

        manager = new TeamManager(this);
        manager.load();
        manager.refreshAllVisuals();

        TeamDialogs dialogs = new TeamDialogs(this, manager);
        getServer().getPluginManager().registerEvents(new TeamListener(this, manager), this);

        TeamCommand executor = new TeamCommand(manager, dialogs);
        for (String name : new String[]{"team", "tc"}) {
            PluginCommand cmd = getCommand(name);
            if (cmd != null) {
                cmd.setExecutor(executor);
                cmd.setTabCompleter(executor);
            }
        }
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new TeamsExpansion(manager).register();
            getLogger().info("Registered PlaceholderAPI placeholders (%teams_tag%, %teams_prefix%, ...).");
        }
        getLogger().info("TeamsDialog enabled.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
    }
}
