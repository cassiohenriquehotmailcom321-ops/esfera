package com.arsenal.weapons;

import com.arsenal.ArsenalPlugin;
import com.arsenal.core.Ability;
import com.arsenal.core.Combat;
import com.arsenal.core.Fx;
import com.arsenal.core.TaskTracker;
import com.arsenal.core.Trigger;
import com.arsenal.core.Weapon;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.AxisAngle4f;
import org.joml.Vector3f;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** GRAVIDADE: poço gravitacional, colapso sísmico e pulso antigravidade. */
public final class Gravitas extends Weapon {

    /** Quanto tempo a esfera fica exibida no chão (ticks). */
    private static final int PLACE_TICKS = 160;

    private final Map<UUID, TaskTracker.Job> placed = new HashMap<>();
    private final Map<UUID, Long> lastPlace = new HashMap<>();
    private final List<Ability> abilities;

    public Gravitas(ArsenalPlugin plugin) {
        super(plugin);
        abilities = List.of(
                new Ability(Trigger.RIGHT, "Poço Gravitacional",
                        "Cria um buraco negro por 5s que suga inimigos e implode (8 de dano final).",
                        22, 0, this::well),
                new Ability(Trigger.SNEAK_RIGHT, "Colapso Sísmico",
                        "Salta e despenca como um meteoro: onda de choque de 6 blocos (9 de dano).",
                        16, 30, this::slam),
                new Ability(Trigger.SWAP, "Pulso Antigravidade",
                        "Ergue e prende inimigos no ar por 1,2s (3 de dano).",
                        18, 0, this::antigravity));
    }

    @Override public String id() { return "gravitas"; }
    @Override public String name() { return "&d&lEsfera do Colapso &8| &5Gravitas"; }
    @Override public Material material() { return Material.MACE; }
    @Override public String tagline() { return "Cada golpe pesa como um planeta."; }
    @Override public String passiveText() { return "Imune a dano de queda. Golpes esmagam o alvo contra o chão. Botão direito olhando para o chão posiciona a esfera no local (ela nunca sai do seu inventário)."; }
    @Override public List<Ability> abilities() { return abilities; }

    @Override
    public void passiveTick(Player p, int tick) {
        if (tick % 20 == 0) {
            Fx.particle(p.getLocation().add(0, 0.1, 0), Particle.PORTAL, 8, 0.5, 0.1, 0.5, 0.3);
        }
    }

    @Override
    public void onHit(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e) {
        victim.setVelocity(victim.getVelocity().add(new Vector(0, -0.35, 0)));
        Fx.particle(victim.getLocation().add(0, 0.2, 0), Particle.PORTAL, 15, 0.3, 0.1, 0.3, 0.3);
    }

    @Override
    public void onDamaged(Player victim, EntityDamageEvent e) {
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL) {
            e.setCancelled(true);
            Fx.particle(victim.getLocation(), Particle.PORTAL, 20, 0.5, 0.1, 0.5, 0.3);
        }
    }

    // ---------- Posicionar no chão ----------

    @Override
    public boolean onRightClickBlock(Player p, Block clicked, BlockFace face, Location point) {
        // só olhando para o chão (face de cima de um bloco, olhar bem inclinado, sem agachar)
        if (face != BlockFace.UP || p.isSneaking() || p.getLocation().getPitch() < 40) {
            return false;
        }
        if (clicked.isPassable() || !clicked.getRelative(BlockFace.UP).isPassable()) {
            return false;
        }
        Location floor = point != null ? point.clone()
                : clicked.getLocation().add(0.5, clicked.getBoundingBox().getHeight(), 0.5);
        if (floor.getWorld() == null || !floor.getWorld().equals(p.getWorld())
                || floor.distanceSquared(p.getEyeLocation()) > 7 * 7) {
            return false;
        }
        long now = System.currentTimeMillis();
        Long last = lastPlace.get(p.getUniqueId());
        if (last != null && now - last < 1000) {
            return true; // evita spam; o clique é consumido
        }
        lastPlace.put(p.getUniqueId(), now);
        place(p, floor);
        return true;
    }

    private static Transformation pose(float lift, float scale) {
        return new Transformation(new Vector3f(0, lift, 0), new AxisAngle4f(0, 0, 1, 0),
                new Vector3f(scale, scale, scale), new AxisAngle4f(0, 0, 1, 0));
    }

    /**
     * Exibe uma CÓPIA visual da esfera no chão. O item real nunca sai do inventário do jogador,
     * então não há como perder nem duplicar. Ao fim, a cópia "voa" de volta e some.
     */
    private void place(Player p, Location floor) {
        UUID id = p.getUniqueId();
        TaskTracker.Job old = placed.remove(id);
        if (old != null) {
            old.end();
        }

        ItemStack shown = p.getInventory().getItemInMainHand().clone();
        shown.setAmount(1);
        Location at = floor.clone().add(0, 0.42, 0);
        ItemDisplay display = floor.getWorld().spawn(at, ItemDisplay.class, d -> {
            d.setItemStack(shown);
            d.setItemDisplayTransform(ItemDisplay.ItemDisplayTransform.FIXED);
            d.setBillboard(Display.Billboard.CENTER);
            d.setBrightness(new Display.Brightness(15, 15));
            d.setPersistent(false);
            d.setInterpolationDelay(0);
            d.setInterpolationDuration(10);
            d.setTransformation(pose(0f, 0.7f));
        });

        Fx.sound(floor, Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 1.2f, 0.6f);
        Fx.sound(floor, Sound.ENTITY_ENDERMAN_TELEPORT, 0.8f, 0.5f);
        Fx.particle(at, Particle.REVERSE_PORTAL, 50, 0.3, 0.3, 0.3, 0.3);
        Fx.bar(p, "&d✦ Esfera da Gravidade posicionada");

        final int runs = PLACE_TICKS / 10;
        TaskTracker.Job[] self = new TaskTracker.Job[1];
        self[0] = plugin.tasks().repeat(p, 0, 10, runs, (i, job) -> {
            if (!display.isValid()) {
                job.end();
                return;
            }
            if (i < runs - 2) {
                boolean up = i % 2 == 0;
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(10);
                display.setTransformation(pose(up ? 0.14f : 0f, up ? 0.85f : 0.7f));
            } else if (i == runs - 2 && p.isValid() && p.getWorld().equals(at.getWorld())) {
                Location chest = p.getLocation().add(0, 1.0, 0);
                Vector to = chest.toVector().subtract(at.toVector());
                display.setInterpolationDelay(0);
                display.setInterpolationDuration(10);
                display.setTransformation(new Transformation(
                        new Vector3f((float) to.getX(), (float) to.getY(), (float) to.getZ()),
                        new AxisAngle4f(0, 0, 1, 0), new Vector3f(0.35f, 0.35f, 0.35f), new AxisAngle4f(0, 0, 1, 0)));
                Fx.line(at, chest, 0.5, Particle.PORTAL, null);
            }
            Fx.magicCircle(floor.clone().add(0, 0.05, 0), 1.3, Color.PURPLE, i * 0.3);
            Fx.particle(at, Particle.PORTAL, 12, 0.35, 0.35, 0.35, 0.4);
            Fx.sphere(at, 0.5, 14, Particle.DUST, Fx.dust(150, 60, 220, 1.2f));
        }, () -> {
            boolean current = placed.get(id) == self[0];
            display.remove();
            if (!current) {
                return;
            }
            placed.remove(id);
            if (p.isValid()) {
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.4f);
                Fx.particle(p.getLocation().add(0, 1, 0), Particle.REVERSE_PORTAL, 30, 0.3, 0.5, 0.3, 0.2);
                Fx.bar(p, "&d✦ A esfera voltou ao seu inventário");
            }
        });
        placed.put(id, self[0]);
    }

    @Override
    public void onQuit(UUID id) {
        placed.remove(id);
        lastPlace.remove(id);
    }

    @Override
    public void onDeath(UUID id) {
        lastPlace.remove(id);
    }

    // ---------- Poço Gravitacional ----------

    private boolean well(Player p) {
        Location c = Combat.targetPoint(p, 25).add(0, 1.8, 0);
        Fx.sound(c, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 2f, 0.5f);

        plugin.tasks().repeat(p, 0, 2, 50, (i, job) -> {
            Fx.sphere(c, 0.7, 30, Particle.DUST, Fx.dust(10, 0, 20, 2.5f));
            for (int k = 0; k < 3; k++) {
                double ang = i * 0.5 + k * 2.094;
                double r = 5 - (i % 10) * 0.5;
                Location l = c.clone().add(Math.cos(ang) * r, Math.sin(ang * 0.7) * r * 0.4, Math.sin(ang) * r);
                Fx.particle(l, Particle.PORTAL, 6, 0.1, 0.1, 0.1, 0.3);
                Fx.particle(l, Particle.DUST, 1, 0, 0, 0, 0, Fx.dust(150, 60, 220, 1.5f));
            }
            Fx.ring(c, 2.5, 30, Particle.DUST_COLOR_TRANSITION,
                    Fx.trans(Color.PURPLE, Color.BLACK, 1.4f));
            for (LivingEntity e : Combat.near(p, c, 8)) {
                Vector v = c.toVector().subtract(e.getBoundingBox().getCenter());
                double d = v.length();
                if (d < 0.3) {
                    continue;
                }
                v.normalize().multiply(Math.min(0.55, 0.15 + 0.05 * (8 - d)));
                e.setVelocity(e.getVelocity().multiply(0.6).add(v));
                if (i % 10 == 5) {
                    Combat.damage(plugin, p, e, 2);
                }
            }
        }, () -> implode(p, c));
        return true;
    }

    private void implode(Player p, Location c) {
        if (!p.isValid()) {
            return;
        }
        Fx.explosion(c);
        Fx.sound(c, Sound.ENTITY_GENERIC_EXPLODE, 2f, 0.5f);
        Fx.sphere(c, 4, 120, Particle.DUST_COLOR_TRANSITION, Fx.trans(Color.PURPLE, Color.BLACK, 2f));
        Fx.particle(c, Particle.REVERSE_PORTAL, 150, 0.2, 0.2, 0.2, 2);
        for (LivingEntity e : Combat.near(p, c, 5)) {
            Combat.damage(plugin, p, e, 8);
            Combat.push(e, c, 1.4, 0.5);
        }
    }

    // ---------- Colapso Sísmico ----------

    private boolean slam(Player p) {
        p.setVelocity(new Vector(0, 1.5, 0));
        p.setFallDistance(0);
        Fx.sound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_FLAP, 1.5f, 0.6f);
        Fx.ring(p.getLocation().add(0, 0.2, 0), 2, 24, Particle.PORTAL, null);

        plugin.tasks().repeat(p, 0, 1, 60, (i, job) -> {
            Location l = p.getLocation();
            if (i < 12) {
                Fx.particle(l, Particle.PORTAL, 6, 0.4, 0.2, 0.4, 0.3);
                return;
            }
            p.setVelocity(new Vector(0, -2.2, 0));
            p.setFallDistance(0);
            Fx.particle(l.clone().add(0, 1, 0), Particle.DUST, 8, 0.3, 0.8, 0.3, 0, Fx.dust(140, 60, 220, 1.8f));
            if (i > 14 && Combat.grounded(p)) {
                impact(p, l);
                job.end();
            }
        }, null);
        return true;
    }

    private void impact(Player p, Location l) {
        Fx.explosion(l);
        Fx.sound(l, Sound.ENTITY_WARDEN_SONIC_BOOM, 1.5f, 0.6f);
        Fx.debris(l, 120, 3);
        Fx.particle(l, Particle.POOF, 40, 2, 0.2, 2, 0.2);
        for (LivingEntity e : Combat.near(p, l, 6)) {
            Combat.damage(plugin, p, e, 9);
            Combat.push(e, l, 0.8, 0.9);
        }
        plugin.tasks().repeat(p, 0, 1, 6, (i, job) ->
                Fx.ring(l.clone().add(0, 0.2, 0), 1 + i, 20 + i * 8, Particle.DUST,
                        Fx.dust(160, 90, 230, 1.6f)), null);
    }

    // ---------- Pulso Antigravidade ----------

    private boolean antigravity(Player p) {
        List<LivingEntity> targets = Combat.near(p, p.getLocation(), 7);
        if (targets.isEmpty()) {
            Fx.bar(p, "&cNenhum inimigo por perto");
            return false;
        }
        Fx.sound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1.5f, 0.5f);
        for (LivingEntity e : targets) {
            Combat.damage(plugin, p, e, 3);
            e.setVelocity(new Vector(0, 0.9, 0));
        }
        plugin.tasks().repeat(p, 3, 2, 12, (i, job) -> {
            for (LivingEntity e : targets) {
                if (!e.isValid()) {
                    continue;
                }
                Vector v = e.getVelocity();
                e.setVelocity(new Vector(v.getX() * 0.5, i < 8 ? 0.08 : v.getY(), v.getZ() * 0.5));
                Fx.particle(e.getLocation(), Particle.END_ROD, 3, 0.3, 0.1, 0.3, 0.02);
            }
            Fx.ring(p.getLocation().add(0, 0.2 + i * 0.3, 0), 7, 60, Particle.DUST, Fx.dust(200, 150, 255, 1.3f));
        }, null);
        return true;
    }
}
