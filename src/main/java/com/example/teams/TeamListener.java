package com.example.teams;

import io.papermc.paper.event.player.AsyncChatEvent;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public final class TeamListener implements Listener {

    private final TeamsPlugin plugin;
    private final TeamManager mgr;

    public TeamListener(TeamsPlugin plugin, TeamManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        Team t = mgr.teamOf(p.getUniqueId());
        if (t == null) return;
        mgr.refreshVisuals(t);
        mgr.broadcastExcept(t, p.getUniqueId(),
                Msg.info(Component.text(p.getName() + " is now online.", NamedTextColor.GREEN)));
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        mgr.cancelWarmup(p.getUniqueId());
        Team t = mgr.teamOf(p.getUniqueId());
        if (t != null) {
            mgr.broadcastExcept(t, p.getUniqueId(),
                    Msg.info(Component.text(p.getName() + " went offline.", NamedTextColor.GRAY)));
        }
    }

    /** Friendly fire protection (melee and projectiles). */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;

        Player attacker = null;
        Entity damager = e.getDamager();
        if (damager instanceof Player p) {
            attacker = p;
        } else if (damager instanceof Projectile proj && proj.getShooter() instanceof Player shooter) {
            attacker = shooter;
        }
        if (attacker == null || attacker.equals(victim)) return;

        Team a = mgr.teamOf(attacker.getUniqueId());
        if (a == null || a.friendlyFire()) return;
        Team v = mgr.teamOf(victim.getUniqueId());
        if (a == v) {
            e.setCancelled(true);
            attacker.sendActionBar(Component.text("Friendly fire is disabled for your team.", NamedTextColor.RED));
        }
    }

    /** Kills against other teams count towards the leaderboard. */
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return;
        Team kt = mgr.teamOf(killer.getUniqueId());
        Team vt = mgr.teamOf(victim.getUniqueId());
        if (kt != null && kt != vt) {
            mgr.addKill(kt);
        }
    }

    /** When team-chat mode is toggled on, redirect normal chat to the team. */
    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = true)
    public void onChat(AsyncChatEvent e) {
        Player p = e.getPlayer();
        if (!mgr.isChatToggled(p.getUniqueId())) return;
        e.setCancelled(true);
        Component message = e.message();
        Bukkit.getScheduler().runTask(plugin, () -> mgr.teamChat(p, message));
    }
}
