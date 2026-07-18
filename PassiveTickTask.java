package com.exotic.plugin;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.UUID;

public class PassiveTickTask extends BukkitRunnable {

    private final ExoticPlugin plugin;
    private static final UUID KITTY_HEALTH_MOD = UUID.fromString("a1b2c3d4-0000-4000-8000-000000000001");

    public PassiveTickTask(ExoticPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public void run() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean hasSword3 = SwordUtil.hasSwordInInventory(player, SwordType.SWORD3);
            boolean hasSword2 = SwordUtil.hasSwordInInventory(player, SwordType.SWORD2);
            boolean hasSword5 = SwordUtil.hasSwordInInventory(player, SwordType.SWORD5);

            // Sword3 - Swift: infinite Speed 2 while carried
            if (hasSword3) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 60, 1, true, false, false));
            }

            // Sword5 - Emperor's Charisma: permanent Hero of the Village 255
            if (hasSword5) {
                player.addPotionEffect(new PotionEffect(PotionEffectType.HERO_OF_THE_VILLAGE, 60, 254, true, false, false));
            }

            // Sword2 - Kitty Love: permanent +2 hearts while carried
            AttributeInstance maxHealth = player.getAttribute(Attribute.GENERIC_MAX_HEALTH);
            if (maxHealth != null) {
                boolean hasModifier = maxHealth.getModifiers().stream().anyMatch(m -> m.getUniqueId().equals(KITTY_HEALTH_MOD));
                if (hasSword2 && !hasModifier) {
                    maxHealth.addModifier(new AttributeModifier(KITTY_HEALTH_MOD, "exotic.kitty_love", 4.0,
                            AttributeModifier.Operation.ADD_NUMBER, EquipmentSlot.HAND));
                } else if (!hasSword2 && hasModifier) {
                    maxHealth.getModifiers().stream()
                            .filter(m -> m.getUniqueId().equals(KITTY_HEALTH_MOD))
                            .findFirst().ifPresent(maxHealth::removeModifier);
                }
            }

            // Sword2 - Kitty Love: cobweb slowdown negation (approximation: boost velocity out of the web)
            if (hasSword2 && player.getLocation().getBlock().getType() == Material.COBWEB) {
                player.setVelocity(player.getVelocity().multiply(1.0).setY(Math.max(player.getVelocity().getY(), 0.1)));
                player.setVelocity(player.getVelocity().multiply(2.2));
            }

            updateActionBar(player);
            plugin.scoreboards().update(player);
        }
    }

    private void updateActionBar(Player player) {
        SwordType held = SwordUtil.heldSword(player);
        if (held == null) return;

        long remaining = plugin.cooldowns().remainingMs(player.getUniqueId(), held.id());
        Component status = remaining > 0
                ? Component.text(held.displayName() + " - Cooldown: " + (remaining / 1000 + 1) + "s", NamedTextColor.RED)
                : Component.text(held.displayName() + " - Ready (Shift + Right-Click)", NamedTextColor.GREEN);

        player.sendActionBar(status);
    }
}
