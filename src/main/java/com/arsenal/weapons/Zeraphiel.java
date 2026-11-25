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
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/** TROVÃO: raios guiados, corrente elétrica e teleporte relâmpago. */
public final class Zeraphiel extends Weapon {

    private final List<Ability> abilities;

    public Zeraphiel(ArsenalPlugin plugin) {
        super(plugin);
        abilities = List.of(
                new Ability(Trigger.RIGHT, "Julgamento do Trovão",
                        "Marca uma área; após 0,7s um raio devastador a atinge (9 de dano + lentidão).",
                        12, 0, this::judgment),
                new Ability(Trigger.SNEAK_RIGHT, "Corrente Fulminante",
                        "Forma uma esfera elétrica na mão e a descarrega em raios que saltam entre até 5 inimigos (7 → 3 de dano).",
                        16, 34, this::chain),
                new Ability(Trigger.SWAP, "Passo do Relâmpago",
                        "Teleporte de 9 blocos que eletrocuta tudo no caminho (5 de dano).",
                        10, 0, this::dash));
    }

    @Override public String id() { return "zeraphiel"; }
    @Override public String name() { return "&e&lEsfera do Trovão &8| &6Zeraphiel"; }
    @Override public Material material() { return Material.NETHERITE_SWORD; }
    @Override public String tagline() { return "Uma esfera que ainda guarda a fúria da tempestade."; }
    @Override public String passiveText() { return "20% de chance de cada golpe soltar um raio que salta para 2 inimigos próximos."; }
    @Override public List<Ability> abilities() { return abilities; }

    @Override
    public void passiveTick(Player p, int tick) {
        if (tick % 20 == 0) {
            Fx.particle(p.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 6, 0.5, 0.8, 0.5, 0.1);
        }
    }

    @Override
    public void onHit(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e) {
        if (ThreadLocalRandom.current().nextDouble() > 0.20) {
            return;
        }
        Location from = victim.getLocation().add(0, 1, 0);
        int jumps = 0;
        for (LivingEntity t : Combat.near(attacker, victim.getLocation(), 6)) {
            if (t.equals(victim)) {
                continue;
            }
            if (jumps++ >= 2) {
                break;
            }
            Fx.lightning(from, t.getLocation().add(0, 1, 0));
            Combat.damage(plugin, attacker, t, 3);
        }
        Fx.particle(from, Particle.ELECTRIC_SPARK, 20, 0.4, 0.6, 0.4, 0.3);
        Fx.sound(from, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.4f, 2f);
    }

    // ---------- Julgamento do Trovão ----------

    private boolean judgment(Player p) {
        Location target = Combat.targetPoint(p, 35);
        Fx.sound(p.getLocation(), Sound.ITEM_TRIDENT_THUNDER, 1f, 1.6f);
        plugin.tasks().repeat(p, 0, 2, 8, (i, job) -> {
            Fx.magicCircle(target.clone().add(0, 0.1, 0), 3.5, Color.YELLOW, i * 0.3);
            double r = 3.5 * (1 - i / 8.0) + 0.4;
            Fx.ring(target, r, 24, Particle.DUST, Fx.dust(255, 255, 120, 1.2f));
            Fx.particle(target.clone().add(0, 0.2, 0), Particle.ELECTRIC_SPARK, 6, 1.5, 0.1, 1.5, 0.05);
        }, () -> strike(p, target));
        return true;
    }

    private void strike(Player p, Location t) {
        if (!p.isValid()) {
            return;
        }
        t.getWorld().strikeLightningEffect(t);
        Fx.sound(t, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 2f, 1f);
        Fx.explosion(t);
        Fx.particle(t, Particle.ELECTRIC_SPARK, 80, 1.5, 1, 1.5, 0.4);
        Fx.ring(t, 3.5, 40, Particle.ELECTRIC_SPARK, null);
        for (LivingEntity e : Combat.near(p, t, 3.5)) {
            Combat.damage(plugin, p, e, 9);
            Combat.addEffect(e, PotionEffectType.SLOWNESS, 40, 1);
            e.setVelocity(new Vector(0, 0.5, 0));
        }
    }

    // ---------- Corrente Fulminante ----------

    /** Tempo de carga da esfera elétrica antes da descarga (ticks). */
    private static final int CHARGE_TICKS = 12;

    private static Location hand(Player p) {
        Location eye = p.getEyeLocation();
        return eye.clone().add(eye.getDirection().multiply(1.7)).add(0, -0.35, 0);
    }

    private static Vector randomDir() {
        ThreadLocalRandom r = ThreadLocalRandom.current();
        Vector v = new Vector(r.nextDouble(-1, 1), r.nextDouble(-1, 1), r.nextDouble(-1, 1));
        if (v.lengthSquared() < 1.0E-4) {
            return new Vector(0, 1, 0);
        }
        return v.normalize();
    }

    private boolean chain(Player p) {
        LivingEntity first = Combat.aimTarget(p, 16, 0.6);
        if (first == null) {
            Fx.bar(p, "&cNenhum alvo à vista");
            return false;
        }
        Location start = p.getLocation();
        Fx.sound(start, Sound.ITEM_TRIDENT_THUNDER, 1.2f, 1.2f);
        Fx.sound(start, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 1.4f, 1.6f);

        // fase 1: a esfera elétrica cresce na mão; ao fim, a descarga (release)
        plugin.tasks().repeat(p, 0, 1, CHARGE_TICKS, (i, job) ->
                chargeFx(p, (i + 1) / (double) CHARGE_TICKS, i), () -> release(p, first));
        return true;
    }

    /** Três anéis orbitais de faíscas girando em torno da esfera. */
    private void orbits(Location c, Vector dir, double radius, double angle) {
        Vector fwd = dir.clone().normalize();
        Vector right = fwd.clone().crossProduct(new Vector(0, 1, 0));
        if (right.lengthSquared() < 1.0E-4) {
            right = new Vector(1, 0, 0);
        }
        right.normalize();
        Vector up = right.clone().crossProduct(fwd).normalize();
        for (int k = 0; k < 3; k++) {
            double tilt = k * Math.PI / 3;
            Vector e2 = up.clone().multiply(Math.cos(tilt)).add(fwd.clone().multiply(Math.sin(tilt)));
            for (int s = 0; s < 2; s++) {
                double th = angle * (1.0 + k * 0.35) + s * Math.PI;
                Location l = c.clone()
                        .add(right.clone().multiply(Math.cos(th) * radius))
                        .add(e2.clone().multiply(Math.sin(th) * radius));
                Fx.particle(l, Particle.ELECTRIC_SPARK, 2, 0.02, 0.02, 0.02, 0.01);
                Fx.particle(l, Particle.DUST, 1, 0, 0, 0, 0, Fx.dust(255, 235, 90, 1.1f));
            }
        }
    }

    private void chargeFx(Player p, double prog, int i) {
        Location core = hand(p);
        Vector dir = p.getEyeLocation().getDirection();
        double r = 0.25 + 0.75 * prog;
        Fx.sphere(core, r, 14 + (int) (prog * 24), Particle.DUST, Fx.dust(255, 236, 100, 1.3f));
        Fx.particle(core, Particle.DUST, 3, 0.08, 0.08, 0.08, 0, Fx.dust(255, 255, 255, 1.6f));
        Fx.particle(core, Particle.END_ROD, 2, r * 0.4, r * 0.4, r * 0.4, 0.02);
        Fx.particle(core, Particle.ELECTRIC_SPARK, 4 + (int) (prog * 10), r, r, r, 0.15);
        orbits(core, dir, 1.0 + prog * 0.5, i * 0.8);
        int arcs = 1 + (int) (prog * 3);
        for (int a = 0; a < arcs; a++) {
            double reach = r + 0.8 + ThreadLocalRandom.current().nextDouble(0.8);
            Fx.lightning(core, core.clone().add(randomDir().multiply(reach)));
        }
        // energia convergindo para a esfera
        for (int k = 0; k < 6; k++) {
            Fx.particle(core.clone().add(randomDir().multiply(3.2 * (1 - prog) + 0.6)), Particle.END_ROD, 1, 0, 0, 0, 0);
        }
        Location feet = p.getLocation().add(0, 0.1, 0);
        if (i % 2 == 0) {
            Fx.magicCircle(feet, 2.0 + prog * 0.8, Color.YELLOW, i * 0.35);
            Fx.sound(core, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.25f, Math.min(2f, 1.2f + (float) prog));
        }
        Fx.ring(feet, 3.0 * (1 - prog) + 0.7, 28, Particle.ELECTRIC_SPARK, null);
    }

    private boolean usable(Player p, LivingEntity t) {
        return t != null && t.isValid() && !t.isDead() && t.getWorld().equals(p.getWorld())
                && t.getLocation().distanceSquared(p.getLocation()) <= 20 * 20 && Combat.canHarm(p, t);
    }

    /** Fase 2: a esfera explode em luz e dispara a corrente no alvo. */
    private void release(Player p, LivingEntity first) {
        if (!p.isValid() || p.isDead()) {
            return;
        }
        Location core = hand(p);
        Fx.sphere(core, 1.6, 50, Particle.ELECTRIC_SPARK, null);
        Fx.particle(core, Particle.END_ROD, 40, 0.5, 0.5, 0.5, 0.25);
        Fx.particle(core, Particle.FIREWORK, 30, 0.4, 0.4, 0.4, 0.2);
        Fx.sound(core, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1.2f, 1.4f);
        Fx.sound(core, Sound.ENTITY_WARDEN_SONIC_BOOM, 0.7f, 1.8f);
        plugin.tasks().repeat(p, 0, 1, 6, (i, j) ->
                Fx.ring(core, 1 + i * 0.8, 24 + i * 6, Particle.ELECTRIC_SPARK, null), null);

        LivingEntity target = first;
        if (!usable(p, target)) {
            target = Combat.aimTarget(p, 18, 0.5);
        }
        if (target == null) {
            // sem alvo: a esfera se descarrega no ar
            Location end = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(14));
            Fx.lightning(core, end);
            Fx.lightning(core, end);
            Fx.sound(end, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 1f, 1.4f);
            Fx.particle(end, Particle.ELECTRIC_SPARK, 40, 0.5, 0.5, 0.5, 0.3);
            return;
        }
        arc(p, target, core);
    }

    /** A corrente em si: mesmo funcionamento de sempre (até 5 alvos, dano 7 → 3, saltos de até 7 blocos). */
    private void arc(Player p, LivingEntity target, Location origin) {
        Set<UUID> hit = new HashSet<>();
        LivingEntity[] current = {target};
        Location[] from = {origin.clone()};
        double[] damage = {7, 6, 5, 4, 3};
        List<LivingEntity> victims = new ArrayList<>();

        plugin.tasks().repeat(p, 0, 4, 5, (i, job) -> {
            LivingEntity t = current[0];
            if (t == null || !t.isValid()) {
                job.end();
                return;
            }
            Location to = t.getBoundingBox().getCenter().toLocation(t.getWorld());
            Fx.lightning(from[0], to);
            Fx.lightning(from[0], to);
            Fx.lightning(from[0].clone().add(0, 0.2, 0), to.clone().add(0, 0.2, 0));
            Fx.line(from[0], to, 0.5, Particle.DUST, Fx.dust(255, 240, 120, 1.2f));
            impactFx(to, i);
            Combat.damage(plugin, p, t, damage[i]);
            hit.add(t.getUniqueId());
            victims.add(t);
            shock(p, t);
            from[0] = to;

            LivingEntity next = null;
            double best = 49;
            for (LivingEntity c : Combat.near(p, to, 7)) {
                if (hit.contains(c.getUniqueId())) {
                    continue;
                }
                double d = c.getLocation().distanceSquared(to);
                if (d < best) {
                    best = d;
                    next = c;
                }
            }
            current[0] = next;
            if (i == 0 || next == null || i == 4) {
                to.getWorld().strikeLightningEffect(to);
            }
        }, () -> finale(p, victims, from[0]));
    }

    private void impactFx(Location to, int i) {
        Location ground = to.clone().subtract(0, 0.9, 0);
        Fx.sphere(to, 0.9, 30, Particle.DUST, Fx.dust(255, 240, 120, 1.5f));
        Fx.particle(to, Particle.ELECTRIC_SPARK, 40, 0.5, 0.7, 0.5, 0.35);
        Fx.particle(to, Particle.END_ROD, 12, 0.3, 0.5, 0.3, 0.15);
        Fx.ring(ground, 1.3, 24, Particle.ELECTRIC_SPARK, null);
        Fx.ring(ground, 2.1, 36, Particle.DUST, Fx.dust(255, 235, 90, 1.2f));
        Fx.sound(to, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, 0.9f, Math.min(2f, 1.6f + i * 0.1f));
        Fx.sound(to, Sound.ITEM_TRIDENT_THUNDER, 0.6f, Math.min(2f, 1.8f + i * 0.1f));
    }

    /** Eletricidade residual no alvo atingido (só visual). */
    private void shock(Player p, LivingEntity t) {
        plugin.tasks().repeat(p, 0, 2, 10, (k, j) -> {
            if (!t.isValid()) {
                j.end();
                return;
            }
            Location b = t.getLocation().add(0, 1, 0);
            Fx.particle(b, Particle.ELECTRIC_SPARK, 8, 0.4, 0.7, 0.4, 0.2);
            if (k % 2 == 0) {
                Fx.lightning(b.clone().add(randomDir().multiply(0.4)), b.clone().add(randomDir().multiply(1.6)));
            }
        }, null);
    }

    /** Fecho: explosão no último alvo e a corrente ligando todos os atingidos por um instante. */
    private void finale(Player p, List<LivingEntity> victims, Location last) {
        if (!p.isValid() || victims.isEmpty()) {
            return;
        }
        Fx.particle(last, Particle.FIREWORK, 40, 0.5, 0.7, 0.5, 0.25);
        Fx.sound(last, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 1f, 1.2f);
        plugin.tasks().repeat(p, 0, 2, 8, (k, j) -> {
            for (int a = 0; a + 1 < victims.size(); a++) {
                LivingEntity x = victims.get(a);
                LivingEntity y = victims.get(a + 1);
                if (x.isValid() && y.isValid() && x.getWorld().equals(y.getWorld())) {
                    Fx.lightning(x.getBoundingBox().getCenter().toLocation(x.getWorld()),
                            y.getBoundingBox().getCenter().toLocation(y.getWorld()));
                }
            }
        }, null);
    }

    // ---------- Passo do Relâmpago ----------

    private boolean dash(Player p) {
        Location dest = Combat.blink(p, 9);
        if (dest.distanceSquared(p.getLocation()) < 1) {
            Fx.bar(p, "&cCaminho bloqueado");
            return false;
        }
        Location start = p.getLocation().add(0, 1, 0);
        Location end = dest.clone().add(0, 1, 0);
        Fx.lightning(start, end);
        Fx.lightning(start.clone().add(0, 0.3, 0), end.clone().add(0, 0.3, 0));
        for (LivingEntity e : Combat.inSegment(p, start, end, 1.8)) {
            Combat.damage(plugin, p, e, 5);
            Combat.addEffect(e, PotionEffectType.SLOWNESS, 30, 0);
            Fx.particle(e.getLocation().add(0, 1, 0), Particle.ELECTRIC_SPARK, 20, 0.3, 0.5, 0.3, 0.3);
        }
        Fx.sound(start, Sound.ENTITY_LIGHTNING_BOLT_THUNDER, 0.8f, 2f);
        p.teleport(dest);
        p.setFallDistance(0);
        Fx.particle(end, Particle.ELECTRIC_SPARK, 40, 0.5, 0.8, 0.5, 0.4);
        Fx.sound(dest, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 1.8f);
        return true;
    }
}
