package com.darkzone.boss;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.*;

public class BossListener implements Listener {

    private final DarkzoneBossPlugin plugin;
    private final Set<UUID> bosses = new HashSet<>();
    private final Set<UUID> phaseTwoTriggered = new HashSet<>();
    private final Map<UUID, BossBar> bossBars = new HashMap<>();

    public BossListener(DarkzoneBossPlugin plugin) {
        this.plugin = plugin;
    }

    public void registerBoss(Warden warden) {
        UUID id = warden.getUniqueId();
        bosses.add(id);

        // Create boss bar
        BossBar bar = Bukkit.createBossBar(
                ChatColor.DARK_RED + "✦ " + ChatColor.RED + "Darkzone Warden" + ChatColor.DARK_RED + " ✦",
                BarColor.RED,
                BarStyle.SEGMENTED_10
        );

        // Add all nearby players to boss bar
        for (Player p : warden.getWorld().getPlayers()) {
            bar.addPlayer(p);
        }
        bar.setVisible(true);
        bossBars.put(id, bar);

        // Start boss bar update task
        new BukkitRunnable() {
            @Override
            public void run() {
                if (!bosses.contains(id)) {
                    bar.removeAll();
                    bar.setVisible(false);
                    cancel();
                    return;
                }
                Entity e = Bukkit.getEntity(id);
                if (e == null || e.isDead()) {
                    bar.removeAll();
                    bar.setVisible(false);
                    cancel();
                    return;
                }
                Warden w = (Warden) e;
                AttributeInstance maxHpAttr = w.getAttribute(Attribute.GENERIC_MAX_HEALTH);
                if (maxHpAttr != null) {
                    double progress = w.getHealth() / maxHpAttr.getValue();
                    bar.setProgress(Math.max(0, Math.min(1, progress)));

                    // Change bar color based on health
                    if (progress <= 0.25) {
                        bar.setColor(BarColor.RED);
                    } else if (progress <= 0.5) {
                        bar.setColor(BarColor.YELLOW);
                    } else {
                        bar.setColor(BarColor.GREEN);
                    }
                }

                // Update players in range
                for (Player p : w.getWorld().getPlayers()) {
                    if (!bar.getPlayers().contains(p)) {
                        bar.addPlayer(p);
                    }
                }
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    @EventHandler
    public void onEntityDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Warden)) return;
        if (!bosses.contains(event.getEntity().getUniqueId())) return;

        Warden warden = (Warden) event.getEntity();
        AttributeInstance maxHpAttr = warden.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (maxHpAttr == null) return;

        double maxHealth = maxHpAttr.getValue();
        double healthAfter = warden.getHealth() - event.getFinalDamage();

        // Phase 2: spawn minions at 50% health
        if (healthAfter <= (maxHealth / 2.0) && !phaseTwoTriggered.contains(warden.getUniqueId())) {
            phaseTwoTriggered.add(warden.getUniqueId());

            // Delay spawn slightly so damage is applied first
            Location loc = warden.getLocation();
            new BukkitRunnable() {
                @Override
                public void run() {
                    spawnMinions(loc);
                    String msg = ChatColor.DARK_RED + "⚠ " + ChatColor.RED + "The Warden unleashes its minions!" + ChatColor.DARK_RED + " ⚠";
                    for (Player p : loc.getWorld().getPlayers()) {
                        p.sendMessage(msg);
                        p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_ROAR, 1.0f, 0.8f);
                    }
                }
            }.runTaskLater(plugin, 1L);
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        if (!(event.getEntity() instanceof Warden)) return;
        if (!bosses.contains(event.getEntity().getUniqueId())) return;

        UUID id = event.getEntity().getUniqueId();
        bosses.remove(id);
        phaseTwoTriggered.remove(id);

        // Remove boss bar
        BossBar bar = bossBars.remove(id);
        if (bar != null) {
            bar.removeAll();
            bar.setVisible(false);
        }

        // Clear default drops
        event.getDrops().clear();
        event.setDroppedExp(0);

        Location loc = event.getEntity().getLocation();
        String crateName = plugin.getConfig().getString("crate-name", "mythic");

        // Give key to killer, or nearest player
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            // Find nearest player
            killer = loc.getWorld().getPlayers().stream()
                    .min(Comparator.comparingDouble(p -> p.getLocation().distanceSquared(loc)))
                    .orElse(null);
        }

        if (killer != null) {
            final Player winner = killer;
            // Execute LootCrate key give command
            String cmd = "lootcrate key " + winner.getName() + " " + crateName;
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);

            winner.sendMessage(ChatColor.GOLD + "✦ " + ChatColor.LIGHT_PURPLE + "You received a Mythic Key!" + ChatColor.GOLD + " ✦");
            winner.playSound(winner.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.0f);
        }

        // Broadcast to all players
        String deathMsg = ChatColor.GOLD + "★ " + ChatColor.YELLOW + "The Darkzone Warden has been defeated!" + ChatColor.GOLD + " ★";
        for (Player p : loc.getWorld().getPlayers()) {
            p.sendMessage(deathMsg);
        }

        // Death particles and sound
        loc.getWorld().spawnParticle(Particle.EXPLOSION_HUGE, loc, 5, 1, 1, 1, 0);
        loc.getWorld().playSound(loc, Sound.ENTITY_WARDEN_DEATH, 2.0f, 1.0f);
    }

    private void spawnMinions(Location location) {
        int count = plugin.getConfig().getInt("minion-count", 10);
        World world = location.getWorld();
        if (world == null) return;

        for (int i = 0; i < count; i++) {
            Zombie zombie = (Zombie) world.spawnEntity(location, EntityType.ZOMBIE);
            zombie.setBaby(true);
            zombie.setCustomName(ChatColor.RED + "✦ Warden Minion");
            zombie.setCustomNameVisible(true);

            // Give diamond sword
            ItemStack sword = new ItemStack(Material.DIAMOND_SWORD);
            Objects.requireNonNull(zombie.getEquipment()).setItemInMainHand(sword);
            zombie.getEquipment().setItemInMainHandDropChance(0.0f);

            // Set minion health
            AttributeInstance hp = zombie.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (hp != null) {
                hp.setBaseValue(plugin.getConfig().getDouble("minion-health", 20.0));
                zombie.setHealth(hp.getValue());
            }

            // Set minion speed
            AttributeInstance speed = zombie.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED);
            if (speed != null) {
                speed.setBaseValue(0.35);
            }
        }
    }
}
