package com.exotic.plugin;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.Sound;
import org.bukkit.Particle;

import java.util.*;

public class CombatListener implements Listener {

    private final ExoticPlugin plugin;

    // sword1 Karma: victim -> attacker -> consecutive-hits-not-returned
    private final Map<UUID, Map<UUID, Integer>> karmaCounters = new HashMap<>();
    // sword1 ability: player -> invincible-until epoch millis
    public final Map<UUID, Long> retributionActive = new HashMap<>();
    // guard against infinite reflect loops between two retribution-active players
    private final Set<UUID> reflecting = new HashSet<>();
    // sword3 ability: player -> auto-crit-until epoch millis
    public final Map<UUID, Long> hypersonicActive = new HashMap<>();
    // sword4 passive: wielder -> total landed hits with sword4 (resets each 7th)
    private final Map<UUID, Integer> shadowsHitCount = new HashMap<>();

    private static final List<PotionEffectType> POSITIVE_POOL = List.of(
            PotionEffectType.SPEED, PotionEffectType.HASTE, PotionEffectType.STRENGTH,
            PotionEffectType.JUMP_BOOST, PotionEffectType.REGENERATION, PotionEffectType.RESISTANCE,
            PotionEffectType.FIRE_RESISTANCE, PotionEffectType.WATER_BREATHING, PotionEffectType.INVISIBILITY,
            PotionEffectType.NIGHT_VISION, PotionEffectType.ABSORPTION, PotionEffectType.LUCK,
            PotionEffectType.SLOW_FALLING
    );

    private static final List<PotionEffectType> NEGATIVE_POOL = List.of(
            PotionEffectType.SLOWNESS, PotionEffectType.MINING_FATIGUE, PotionEffectType.NAUSEA,
            PotionEffectType.BLINDNESS, PotionEffectType.HUNGER, PotionEffectType.WEAKNESS,
            PotionEffectType.POISON, PotionEffectType.GLOWING, PotionEffectType.LEVITATION,
            PotionEffectType.UNLUCK, PotionEffectType.BAD_OMEN, PotionEffectType.DARKNESS
    );

    private final Random random = new Random();

    public CombatListener(ExoticPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;

        // --- Karmic Retribution: invincibility + reflect ---
        Long until = retributionActive.get(victim.getUniqueId());
        if (until != null && until > System.currentTimeMillis()) {
            double dealt = event.getFinalDamage();
            event.setCancelled(true);

            if (event.getDamager() instanceof org.bukkit.entity.LivingEntity attacker) {
                if (!reflecting.contains(attacker.getUniqueId())) {
                    reflecting.add(victim.getUniqueId());
                    attacker.damage(dealt, victim);
                    reflecting.remove(victim.getUniqueId());
                }
            }
            return;
        }

        if (!(event.getDamager() instanceof Player attacker)) return;

        // --- Sword1 Karma passive: only applies if victim carries Judgement ---
        if (SwordUtil.hasSwordInInventory(victim, SwordType.SWORD1)) {
            Map<UUID, Integer> perAttacker = karmaCounters.computeIfAbsent(victim.getUniqueId(), k -> new HashMap<>());
            int count = perAttacker.getOrDefault(attacker.getUniqueId(), 0) + 1;
            if (count > 5) {
                applyRandom(attacker, NEGATIVE_POOL);
                applyRandom(victim, POSITIVE_POOL);
                count = 0; // reset after triggering
            }
            perAttacker.put(attacker.getUniqueId(), count);
        }

        // --- Sword4 Shadows passive: every 7th hit landed WITH sword4 ---
        if (SwordUtil.isSword(attacker.getInventory().getItemInMainHand(), SwordType.SWORD4)) {
            int hits = shadowsHitCount.getOrDefault(attacker.getUniqueId(), 0) + 1;
            if (hits >= 7) {
                hits = 0;
                victim.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, 40, 4, false, true));
                victim.addPotionEffect(new PotionEffect(PotionEffectType.DARKNESS, 40, 4, false, true));
            }
            shadowsHitCount.put(attacker.getUniqueId(), hits);
        }

        // --- Sword3 Hypersonic ability: auto-crit visuals/damage while active ---
        Long critUntil = hypersonicActive.get(attacker.getUniqueId());
        if (critUntil != null && critUntil > System.currentTimeMillis()
                && event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            event.setDamage(event.getDamage() * 1.5);
            victim.getWorld().spawnParticle(Particle.CRIT, victim.getLocation().add(0, 1, 0), 20, 0.3, 0.5, 0.3, 0.1);
            victim.getWorld().playSound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 1f);
        }
    }

    /** Reset karma counter for victim->attacker when victim hits back. */
    @EventHandler(priority = EventPriority.MONITOR)
    public void onRetaliate(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player retaliator)) return;
        if (!(event.getEntity() instanceof Player originalAttacker)) return;
        Map<UUID, Integer> perAttacker = karmaCounters.get(originalAttacker.getUniqueId());
        if (perAttacker != null) perAttacker.remove(retaliator.getUniqueId());
    }

    private void applyRandom(Player target, List<PotionEffectType> pool) {
        PotionEffectType type = pool.get(random.nextInt(pool.size()));
        // amplifier 0 = potency level 1; 30 seconds = 600 ticks
        target.addPotionEffect(new PotionEffect(type, 600, 0, false, true, true));
    }
}
