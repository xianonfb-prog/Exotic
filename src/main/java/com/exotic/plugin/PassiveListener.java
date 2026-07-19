package com.exotic.plugin;

import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.raid.RaidStopEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.Material;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.List;

public class PassiveListener implements Listener {

    private final ExoticPlugin plugin;
    private final CombatListener combat;

    public PassiveListener(ExoticPlugin plugin, CombatListener combat) {
        this.plugin = plugin;
        this.combat = combat;
    }

    // ---------------------------------------------------------------
    // Shift + Right-Click ability activation
    // ---------------------------------------------------------------
    @EventHandler
    public void onInteract(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        Player player = event.getPlayer();

        // Frozen players (Ice Age target) can't use items at all.
        if (combat.frozen.contains(player.getUniqueId())) {
            event.setCancelled(true);
            return;
        }

        if (!player.isSneaking()) return;

        String itemId = SwordUtil.getSwordId(player.getInventory().getItemInMainHand());
        if (itemId == null) return;

        ExoticItem item = ExoticItem.byId(itemId);
        if (item == null) return;

        if (plugin.cooldowns().isOnCooldown(player.getUniqueId(), itemId)) {
            long secs = plugin.cooldowns().remainingMs(player.getUniqueId(), itemId) / 1000;
            player.sendMessage(Component.text(item.displayName() + " is on cooldown: " + secs + "s", NamedTextColor.RED));
            return;
        }

        switch (itemId) {
            case "sword1" -> activateKarmicRetribution(player);
            case "sword2" -> activateKittySwarm(player);
            case "sword3" -> activateSpeedIsMySpecialty(player);
            case "sword4" -> activateLurker(player);
            case "sword5" -> activateImperialDecree(player);
            case "tome1" -> activateIceAge(player);
        }
    }

    // Sword1 - Judgement: 25s invincibility + reflect (no Glowing tell anymore)
    private void activateKarmicRetribution(Player player) {
        plugin.cooldowns().trigger(player.getUniqueId(), "sword1");
        long until = System.currentTimeMillis() + 25_000L;
        combat.retributionActive.put(player.getUniqueId(), until);
        player.getWorld().playSound(player.getLocation(), Sound.ITEM_TOTEM_USE, 1f, 1f);
        player.sendMessage(Component.text("Karmic Retribution activated! Invincible for 25 seconds.", NamedTextColor.GOLD));
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> combat.retributionActive.remove(player.getUniqueId()), 500L);
    }

    // Sword2 - Pretty Kitty Princess Blade: 9 cats, 16 dmg, Speed 1 (nerfed from Speed 3), 4 min cooldown
    private void activateKittySwarm(Player player) {
        plugin.cooldowns().trigger(player.getUniqueId(), "sword2");
        combat.swarmActive.put(player.getUniqueId(), System.currentTimeMillis() + 30_000L);
        player.sendMessage(Component.text("Kitty Swarm summoned!", NamedTextColor.LIGHT_PURPLE));
        Location base = player.getLocation();
        for (int i = 0; i < 9; i++) {
            Cat cat = (Cat) base.getWorld().spawnEntity(base.clone().add(
                    (Math.random() - 0.5) * 3, 0, (Math.random() - 0.5) * 3), EntityType.CAT);
            cat.setOwner(player);
            cat.getAttribute(Attribute.GENERIC_ATTACK_DAMAGE).setBaseValue(16.0);
            cat.getAttribute(Attribute.GENERIC_MOVEMENT_SPEED).setBaseValue(0.35);
            cat.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 600, 0, false, false)); // Speed 1
            cat.setCustomName(player.getName() + "'s Kitty");
            cat.setCustomNameVisible(false);
            new SwarmCatAI(plugin, cat, player, 12.0, 600L).runTaskTimer(plugin, 0L, 5L);
        }
    }

    // Sword3 - Hypersonic Devastator: Speed 7, 45s, auto-crit, 5 min cooldown
    private void activateSpeedIsMySpecialty(Player player) {
        plugin.cooldowns().trigger(player.getUniqueId(), "sword3");
        player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 900, 6, false, true)); // Speed 7
        combat.hypersonicActive.put(player.getUniqueId(), System.currentTimeMillis() + 45_000L);
        player.sendMessage(Component.text("\"Speed is my specialty.\"", NamedTextColor.AQUA));
        plugin.getServer().getScheduler().runTaskLater(plugin,
                () -> combat.hypersonicActive.remove(player.getUniqueId()), 900L);
    }

    // Sword4 - Deception: true invis (hides armor natively), 25 seconds
    private void activateLurker(Player player) {
        plugin.cooldowns().trigger(player.getUniqueId(), "sword4");
        player.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, 500, 0, true, false, false));
        combat.lurkerActive.put(player.getUniqueId(), System.currentTimeMillis() + 25_000L);
        plugin.scoreboards().hideNameTag(player, 500L);
        player.sendMessage(Component.text("You slip into the shadows.", NamedTextColor.DARK_RED));
    }

    // Sword5 - Bane Of The Emperor: Imperial Decree, 30s self-buff (Absorption III), 12s enemy debuff
    private void activateImperialDecree(Player player) {
        plugin.cooldowns().trigger(player.getUniqueId(), "sword5");
        combat.decreeActive.put(player.getUniqueId(), System.currentTimeMillis() + 30_000L);
        player.sendMessage(Component.text("Imperial Decree issued. None shall stand against the throne.", NamedTextColor.RED));
        player.getWorld().playSound(player.getLocation(), Sound.EVENT_RAID_HORN, 1f, 0.6f);

        // Self buffs - 30 seconds of near-untouchable dominance
        player.addPotionEffect(new PotionEffect(PotionEffectType.STRENGTH, 600, 1, false, true));   // Strength II
        player.addPotionEffect(new PotionEffect(PotionEffectType.RESISTANCE, 600, 1, false, true)); // Resistance II
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 600, 2, false, true)); // Absorption III

        // Enemies nearby are crippled for 12 seconds - can barely fight back or flee
        for (Entity e : player.getNearbyEntities(12, 12, 12)) {
            boolean isEnemyPlayer = e instanceof Player p && !p.equals(player);
            boolean isHostile = e instanceof Monster;
            if (!isEnemyPlayer && !isHostile) continue;
            if (e instanceof LivingEntity le) {
                le.addPotionEffect(new PotionEffect(PotionEffectType.WEAKNESS, 240, 2, false, true));
                le.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 240, 2, false, true));
                le.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, 240, 2, false, true));
            }
        }
    }

    // Tome1 - Tome Of Subzero: Ice Age, freeze everything in 15 blocks for 4 seconds
    private void activateIceAge(Player player) {
        plugin.cooldowns().trigger(player.getUniqueId(), "tome1");
        player.sendMessage(Component.text("Ice Age unleashed!", NamedTextColor.AQUA));
        player.getWorld().playSound(player.getLocation(), Sound.BLOCK_GLASS_BREAK, 1f, 0.6f);
        for (Entity e : player.getNearbyEntities(15, 15, 15)) {
            if (e.equals(player)) continue;
            if (!(e instanceof LivingEntity le)) continue;
            new IceCageTask(plugin, combat, le, 80L).start(); // 4 seconds
        }
    }

    // ---------------------------------------------------------------
    // Sword2 - Kitty Love: no fall damage
    // ---------------------------------------------------------------
    @EventHandler
    public void onFallDamage(EntityDamageEvent event) {
        if (event.getCause() != EntityDamageEvent.DamageCause.FALL) return;
        if (!(event.getEntity() instanceof Player player)) return;
        if (SwordUtil.hasSwordInInventory(player, SwordType.SWORD2)) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------
    // Sword5 - Emperor's Charisma: hostile/neutral mobs ignore wielder
    // ---------------------------------------------------------------
    private static final List<EntityType> BOSS_TYPES = List.of(
            EntityType.ENDER_DRAGON, EntityType.WITHER, EntityType.WARDEN, EntityType.ELDER_GUARDIAN
    );

    @EventHandler
    public void onTarget(EntityTargetLivingEntityEvent event) {
        if (!(event.getTarget() instanceof Player player)) return;
        if (BOSS_TYPES.contains(event.getEntityType())) return;
        if (event.getEntity().getScoreboardTags().contains("exotic_summoned")) return;
        if (!(event.getEntity() instanceof Monster) && !(event.getEntity() instanceof Animals)) return;
        if (SwordUtil.hasSwordInInventory(player, SwordType.SWORD5)) {
            event.setCancelled(true);
        }
    }

    // ---------------------------------------------------------------
    // Trial progress hooks
    // ---------------------------------------------------------------
    @EventHandler
    public void onEntityTame(EntityTameEvent event) {
        if (event.getEntityType() != EntityType.CAT) return;
        if (!(event.getOwner() instanceof Player player)) return;
        plugin.trials().progress(player, TrialSystem.ObjectiveType.CATS_TAMED, 1);
    }

    @EventHandler
    public void onVillagerDeath(EntityDeathEvent event) {
        if (event.getEntityType() != EntityType.VILLAGER) return;
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        plugin.trials().progress(killer, TrialSystem.ObjectiveType.VILLAGER_KILLS, 1);
    }

    @EventHandler
    public void onPotionConsume(PlayerItemConsumeEvent event) {
        ItemStack item = event.getItem();
        if (!(item.getItemMeta() instanceof PotionMeta meta)) return;
        PotionType baseType = meta.getBasePotionType();
        if (baseType == null) return;

        Player player = event.getPlayer();
        String name = baseType.name();
        if (name.contains("SPEED") || name.contains("SWIFTNESS")) {
            plugin.trials().progress(player, TrialSystem.ObjectiveType.SPEED_POTIONS_DRUNK, 1);
        } else if (name.contains("INVISIBILITY")) {
            plugin.trials().progress(player, TrialSystem.ObjectiveType.INVIS_POTIONS_DRUNK, 1);
        }
    }

    @EventHandler
    public void onRaidStop(RaidStopEvent event) {
        if (event.getRaid().getStatus() != org.bukkit.Raid.RaidStatus.VICTORY) return;
        for (java.util.UUID heroId : event.getRaid().getHeroes()) {
            Player player = org.bukkit.Bukkit.getPlayer(heroId);
            if (player != null) {
                plugin.trials().progress(player, TrialSystem.ObjectiveType.RAID_WON, 1);
            }
        }
    }

    // Tome1 trial: collect (pick up) Ice blocks
    @EventHandler
    public void onIcePickup(EntityPickupItemEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;
        ItemStack picked = event.getItem().getItemStack();
        if (picked.getType() != Material.ICE) return;
        plugin.trials().progress(player, TrialSystem.ObjectiveType.ICE_BLOCKS_COLLECTED, picked.getAmount());
    }

    // ---------------------------------------------------------------
    // Prevent renaming exotic swords (anvil)
    // ---------------------------------------------------------------
    @EventHandler
    public void onAnvilPrepare(PrepareAnvilEvent event) {
        ItemStack left = event.getInventory().getItem(0);
        if (left != null && SwordUtil.getSwordId(left) != null) {
            event.setResult(null);
        }
    }
}
