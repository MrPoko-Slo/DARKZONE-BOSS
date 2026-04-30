package com.darkzone.boss;

import org.bukkit.plugin.java.JavaPlugin;

public class DarkzoneBossPlugin extends JavaPlugin {

    private static DarkzoneBossPlugin instance;
    private BossListener bossListener;

    @Override
    public void onEnable() {
        instance = this;
        saveDefaultConfig();

        bossListener = new BossListener(this);
        getServer().getPluginManager().registerEvents(bossListener, this);

        SpawnBossCommand spawnCmd = new SpawnBossCommand(this);
        getCommand("spawnboss").setExecutor(spawnCmd);
        getCommand("spawnboss").setTabCompleter(spawnCmd);

        getLogger().info("DarkzoneBoss plugin enabled!");
    }

    @Override
    public void onDisable() {
        getLogger().info("DarkzoneBoss plugin disabled!");
    }

    public static DarkzoneBossPlugin getInstance() {
        return instance;
    }

    public BossListener getBossListener() {
        return bossListener;
    }
}
