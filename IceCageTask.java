package com.exotic.plugin;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.entity.LivingEntity;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.List;

public class IceCageTask extends BukkitRunnable {

    private final ExoticPlugin plugin;
    private final CombatListener combat;
    private final LivingEntity target;
    private final long durationTicks;
    private long elapsed = 0;
    private final List<BlockState> restoreStates = new ArrayList<>();

    public IceCageTask(ExoticPlugin plugin, CombatListener combat, LivingEntity target, long durationTicks) {
        this.plugin = plugin;
        this.combat = combat;
        this.target = target;
        this.durationTicks = durationTicks;
    }

    public void start() {
        combat.frozen.add(target.getUniqueId());
        int effectDuration = (int) durationTicks + 10;
        target.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, effectDuration, 250, false, false));
        target.addPotionEffect(new PotionEffect(PotionEffectType.MINING_FATIGUE, effectDuration, 250, false, false));
        encase();
        runTaskTimer(plugin, 0L, 1L);
    }

    /** Places temporary ice blocks around the target - only into air, so it never overwrites terrain. */
    private void encase() {
        Location base = target.getLocation();
        int[][] offsets = {
                {1, 0, 0}, {-1, 0, 0}, {0, 0, 1}, {0, 0, -1},
                {1, 1, 0}, {-1, 1, 0}, {0, 1, 1}, {0, 1, -1}
        };
        for (int[] off : offsets) {
            Block block = base.clone().add(off[0], off[1], off[2]).getBlock();
            if (block.getType() == Material.AIR || block.getType() == Material.CAVE_AIR) {
                restoreStates.add(block.getState());
                block.setType(Material.ICE, false);
            }
        }
    }

    @Override
    public void run() {
        elapsed++;
        if (!target.isValid() || target.isDead() || elapsed >= durationTicks) {
            finish();
            return;
        }
        // Hard-lock horizontal movement each tick; allow gravity so they don't hover.
        Vector v = target.getVelocity();
        target.setVelocity(new Vector(0, Math.min(v.getY(), 0), 0));
    }

    private void finish() {
        combat.frozen.remove(target.getUniqueId());
        for (BlockState state : restoreStates) {
            state.update(true, false);
        }
        cancel();
    }
}
