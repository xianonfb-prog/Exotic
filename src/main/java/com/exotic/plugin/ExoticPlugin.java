package com.exotic.plugin;

import org.bukkit.plugin.java.JavaPlugin;

public class ExoticPlugin extends JavaPlugin {

    private CooldownManager cooldownManager;
    private TrialSystem trialSystem;
    private ScoreboardManager scoreboardManager;

    @Override
    public void onEnable() {
        cooldownManager = new CooldownManager();
        trialSystem = new TrialSystem(this);
        scoreboardManager = new ScoreboardManager(this);

        CombatListener combat = new CombatListener(this);
        getServer().getPluginManager().registerEvents(combat, this);
        getServer().getPluginManager().registerEvents(new PassiveListener(this, combat), this);

        CommandHandler handler = new CommandHandler(this);
        getCommand("exotic").setExecutor(handler);
        getCommand("exotic").setTabCompleter(handler);

        new PassiveTickTask(this).runTaskTimer(this, 20L, 20L);

        getLogger().info("Exotic enabled - 5 swords loaded.");
    }

    @Override
    public void onDisable() {
        getLogger().info("Exotic disabled.");
    }

    public CooldownManager cooldowns() { return cooldownManager; }
    public TrialSystem trials() { return trialSystem; }
    public ScoreboardManager scoreboards() { return scoreboardManager; }
}
