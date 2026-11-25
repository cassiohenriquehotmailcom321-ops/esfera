package com.arsenal.weapons;

import com.arsenal.ArsenalPlugin;
import com.arsenal.core.Ability;
import com.arsenal.core.Combat;
import com.arsenal.core.Fx;
import com.arsenal.core.Trigger;
import com.arsenal.core.Weapon;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** SOMBRAS: emboscada, véu da noite e ceifa de almas. */
public final class Umbra extends Weapon {

    /** Duração da invisibilidade do Véu da Noite: exatamente 60s (1200 ticks). */
    private static final int INVIS_TICKS = 1200;

    private final Map<UUID, Long> invisUntil = new HashMap<>();
    private final List<Ability> abilities;

    public Umbra(ArsenalPlugin plugin) {
        super(plugin);
        abilities = List.of(
                new Ability(Trigger.RIGHT, "Golpe Sombrio",
                        "Teleporta para as costas do alvo na mira e o fere (7 de dano + cegueira).",
                        11, 0, this::shadowStrike),
                new Ability(Trigger.SNEAK_RIGHT, "Véu da Noite",
                        "Aura de trevas (raio 6, 5s): inimigos ficam lentos e no escuro, você ganha velocidade e fica invisível por 60s.",
                        24, 0, this::veil),
                new Ability(Trigger.SWAP, "Ceifa de Almas",
                        "Puxa inimigos em 9 blocos até você (4 de dano + wither).",
                        15, 0, this::reap));
    }

    @Override public String id() { return "umbra"; }
    @Override public String name() { return "&5&lEsfera das Sombras &8| &8Umbra"; }
    @Override public Material material() { return Material.NETHERITE_HOE; }
    @Override public String tagline() { return "Engole o que a luz não alcança."; }
    @Override public String passiveText() { return "Golpes pelas costas causam +35% de dano. No escuro (luz ≤ 5) você ganha velocidade."; }
    @Override public List<Ability> abilities() { return abilities; }

    @Override
    public void passiveTick(Player p, int tick) {
        if (p.getLocation().getBlock().getLightLevel() <= 5) {
            Combat.passiveEffect(p, PotionEffectType.SPEED, 30, 0);
            if (tick % 20 == 0) {
                Fx.particle(p.getLocation().add(0, 1, 0), Particle.SMOKE, 6, 0.4, 0.8, 0.4, 0.01);
            }
        }
    }

    @Override
    public void onHit(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e) {
        Vector facing = victim.getLocation().getDirection().setY(0);
        Vector toAttacker = attacker.getLocation().toVector().subtract(victim.getLocation().toVector()).setY(0);
        if (facing.lengthSquared() < 0.01 || toAttacker.lengthSquared() < 0.01) {
            return;
        }
        if (facing.normalize().dot(toAttacker.normalize()) < -0.3) {
            e.setDamage(e.getDamage() * 1.35);
            Fx.particle(victim.getLocation().add(0, 1, 0), Particle.SCULK_SOUL, 12, 0.3, 0.6, 0.3, 0.05);
            Fx.sound(victim.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 1f, 0.6f);
        }
    }

    // ---------- Golpe Sombrio ----------

    private boolean shadowStrike(Player p) {
        LivingEntity t = Combat.aimTarget(p, 16, 0.8);
        if (t == null) {
            Fx.bar(p, "&cNenhum alvo à vista");
            return false;
        }
        Location from = p.getLocation().add(0, 1, 0);
        Vector tdir = t.getLocation().getDirection().setY(0);
        if (tdir.lengthSquared() < 1.0E-4) {
            tdir = Combat.flatDir(p);
        }
        tdir.normalize();
        Location dest = t.getLocation().subtract(tdir.clone().multiply(1.6));
        if (!dest.getBlock().isPassable() || !dest.clone().add(0, 1, 0).getBlock().isPassable()) {
            dest = t.getLocation();
        }
        dest.setDirection(tdir);

        Fx.particle(from, Particle.SMOKE, 40, 0.3, 0.7, 0.3, 0.05);
        Fx.particle(from, Particle.SCULK_SOUL, 10, 0.3, 0.7, 0.3, 0.05);
        Fx.line(from, dest.clone().add(0, 1, 0), 0.4, Particle.DUST, Fx.dust(60, 0, 90, 1.4f));
        Fx.sound(from, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 0.6f);

        p.teleport(dest);
        Fx.sound(dest, Sound.ENTITY_PLAYER_ATTACK_SWEEP, 1.2f, 0.7f);
        Combat.damage(plugin, p, t, 7);
        Combat.addEffect(t, PotionEffectType.BLINDNESS, 40, 0);
        Location hit = t.getLocation().add(0, 1, 0);
        Fx.particle(hit, Particle.SWEEP_ATTACK, 3, 0.5, 0.5, 0.5, 0);
        Fx.particle(hit, Particle.SCULK_SOUL, 25, 0.4, 0.6, 0.4, 0.1);
        Fx.particle(hit, Particle.SMOKE, 30, 0.4, 0.6, 0.4, 0.05);
        return true;
    }

    // ---------- Véu da Noite ----------

    private boolean veil(Player p) {
        double radius = 6;
        ThreadLocalRandom rnd = ThreadLocalRandom.current();
        Fx.sound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, 1f, 0.5f);
        Fx.sound(p.getLocation(), Sound.ENTITY_ILLUSIONER_CAST_SPELL, 1f, 0.6f);
        grantInvisibility(p);

        plugin.tasks().repeat(p, 0, 4, 25, (i, job) -> {
            Location c = p.getLocation();
            Fx.magicCircle(c.clone().add(0, 0.1, 0), radius, Color.fromRGB(110, 30, 170), -i * 0.2);
            for (int k = 0; k < 25; k++) {
                double ang = rnd.nextDouble() * Math.PI * 2;
                double r = Math.sqrt(rnd.nextDouble()) * radius;
                Location l = c.clone().add(Math.cos(ang) * r, rnd.nextDouble() * 3, Math.sin(ang) * r);
                Fx.particle(l, Particle.DUST, 1, 0, 0, 0, 0, Fx.dust(15, 0, 25, 2.5f));
            }
            Fx.particle(c.clone().add(0, 1, 0), Particle.SMOKE, 15, radius / 2, 1, radius / 2, 0.01);
            Combat.passiveEffect(p, PotionEffectType.SPEED, 30, 1);
            for (LivingEntity e : Combat.near(p, c, radius)) {
                Combat.addEffect(e, PotionEffectType.DARKNESS, 60, 0);
                Combat.addEffect(e, PotionEffectType.SLOWNESS, 40, 0);
            }
        }, () -> Fx.sound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f));
        return true;
    }

    private void applyInvisibility(Player p, int ticks) {
        p.addPotionEffect(new PotionEffect(PotionEffectType.INVISIBILITY, ticks, 0, false, false, true));
    }

    /**
     * Invisibilidade de exatamente 60s, independente da aura (que dura 5s).
     * Se algo remover o efeito antes do fim (leite, etc.), ele é reaplicado com o tempo restante.
     * Uma nova ativação substitui a anterior (não acumula).
     */
    private void grantInvisibility(Player p) {
        UUID id = p.getUniqueId();
        long until = System.currentTimeMillis() + INVIS_TICKS * 50L;
        invisUntil.put(id, until);
        applyInvisibility(p, INVIS_TICKS);
        plugin.tasks().repeat(p, 10, 10, INVIS_TICKS / 10, (i, job) -> {
            Long current = invisUntil.get(id);
            if (current == null || current != until) {
                job.end();
                return;
            }
            long left = until - System.currentTimeMillis();
            if (left <= 0) {
                job.end();
                return;
            }
            if (!p.hasPotionEffect(PotionEffectType.INVISIBILITY)) {
                applyInvisibility(p, (int) Math.max(1, left / 50));
            }
        }, null);
    }

    @Override
    public void onQuit(UUID id) {
        invisUntil.remove(id);
    }

    @Override
    public void onDeath(UUID id) {
        invisUntil.remove(id);
    }

    // ---------- Ceifa de Almas ----------

    private boolean reap(Player p) {
        Location c = p.getLocation().add(0, 1, 0);
        List<LivingEntity> targets = Combat.near(p, c, 9);
        if (targets.isEmpty()) {
            Fx.bar(p, "&cNenhum inimigo por perto");
            return false;
        }
        Fx.sound(c, Sound.ENTITY_ELDER_GUARDIAN_CURSE, 1f, 0.7f);
        Fx.sound(c, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1f, 1.4f);
        for (LivingEntity e : targets) {
            Vector v = c.toVector().subtract(e.getLocation().toVector());
            if (v.lengthSquared() > 0.01) {
                e.setVelocity(v.normalize().multiply(1.1).setY(0.25));
            }
            Combat.damage(plugin, p, e, 4);
            Combat.addEffect(e, PotionEffectType.WITHER, 60, 0);
            Fx.line(e.getLocation().add(0, 1, 0), c, 0.4, Particle.SCULK_SOUL, null);
            Fx.particle(e.getLocation().add(0, 1, 0), Particle.SOUL, 15, 0.3, 0.6, 0.3, 0.05);
        }
        plugin.tasks().repeat(p, 0, 1, 6, (i, job) ->
                Fx.ring(p.getLocation().add(0, 0.2, 0), (i + 1) * 1.5, 30 + i * 6, Particle.SOUL, null), null);
        return true;
    }
}
