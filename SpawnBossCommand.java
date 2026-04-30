package com.darkzone.boss;

import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.command.*;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.Warden;

import java.util.List;
import java.util.Objects;

public class SpawnBossCommand implements CommandExecutor, TabCompleter {

    private final DarkzoneBossPlugin plugin;

    public SpawnBossCommand(DarkzoneBossPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("darkzoneboss.spawn")) {
            sender.sendMessage(ChatColor.RED + "You don't have permission to use this command!");
            return true;
        }

        Player target;
        if (args.length > 0) {
            target = Bukkit.getPlayer(args[0]);
            if (target == null) {
                sender.sendMessage(ChatColor.RED + "Player not found: " + args[0]);
                return true;
            }
        } else if (sender instanceof Player) {
            target = (Player) sender;
        } else {
            sender.sendMessage(ChatColor.RED + "Usage: /spawnboss <player>");
            return true;
        }

        spawnBoss(target.getLocation(), sender);
        return true;
    }

    private void spawnBoss(Location loc, CommandSender sender) {
        World world = loc.getWorld();
        if (world == null) return;

        Warden warden = (Warden) world.spawnEntity(loc, EntityType.WARDEN);

        // Set custom name
        warden.setCustomName(ChatColor.DARK_RED + "✦ " + ChatColor.RED + "Darkzone Warden" + ChatColor.DARK_RED + " ✦");
        warden.setCustomNameVisible(true);
        warden.setRemoveWhenFarAway(false);

        // Set max health (boosted)
        double maxHealth = plugin.getConfig().getDouble("boss-health", 750.0);
        AttributeInstance hpAttr = warden.getAttribute(Attribute.GENERIC_MAX_HEALTH);
        if (hpAttr != null) {
            hpAttr.setBaseValue(maxHealth);
            warden.setHealth(maxHealth);
        }

        // Reduce attack damage
        double attackDamage = plugin.getConfig().getDouble("boss-attack-damage", 20.0);
        AttributeInstance dmgAttr = warden.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE);
        if (dmgAttr != null) {
            dmgAttr.setBaseValue(attackDamage);
        }

        // Register the boss
        plugin.getBossListener().registerBoss(warden);

        // Announce spawn
        String spawnMsg = ChatColor.DARK_RED + "⚠ " + ChatColor.RED + "A powerful Darkzone Warden has appeared!" + ChatColor.DARK_RED + " ⚠";
        for (Player p : world.getPlayers()) {
            p.sendMessage(spawnMsg);
            p.playSound(p.getLocation(), Sound.ENTITY_WARDEN_EMERGE, 1.0f, 0.8f);
        }

        sender.sendMessage(ChatColor.GREEN + "Darkzone Warden spawned successfully!");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream()
                    .map(Player::getName)
                    .filter(name -> name.toLowerCase().startsWith(args[0].toLowerCase()))
                    .toList();
        }
        return List.of();
    }
}
