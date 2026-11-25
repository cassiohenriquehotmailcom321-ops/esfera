package com.arsenal.weapons;

import com.arsenal.ArsenalPlugin;
import com.arsenal.core.Ability;
import com.arsenal.core.Combat;
import com.arsenal.core.Fx;
import com.arsenal.core.Trigger;
import com.arsenal.core.Weapon;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** TEMPO: parada temporal, retrocesso e surto temporal. */
public final class Chronos extends Weapon {

    private static final Color GOLD = Color.fromRGB(255, 215, 80);

    private record Snap(Location loc, double health, long time) {
    }

    /** Quanto o Retroceder volta no tempo. */
    private static final long WINDOW_MS = 30_000L;
    /** Histórico mínimo para o Retroceder funcionar. */
    private static final long MIN_MS = 3_000L;

    private final Map<UUID, ArrayDeque<Snap>> history = new HashMap<>();
    private final List<Ability> abilities;

    public Chronos(ArsenalPlugin plugin) {
        super(plugin);
        abilities = List.of(
                new Ability(Trigger.RIGHT, "Parada Temporal",
                        "Congela inimigos em 8 blocos por 3s (podem ser atacados, mas não se movem).",
                        40, 0, this::timeStop),
                new Ability(Trigger.SNEAK_RIGHT, "Retroceder",
                        "Volta à posição e à vida de 30s atrás (mín. 3s de histórico). Não altera itens.",
                        25, 0, this::rewind),
                new Ability(Trigger.SWAP, "Surto Temporal",
                        "4s de Velocidade III e Pressa II com rastro de imagens residuais.",
                        18, 0, this::surge));
        // grava o histórico de quem carrega a esfera (hotbar/mão secundária), 4x por segundo
        Bukkit.getScheduler().runTaskTimer(plugin, this::recordAll, 20L, 5L);
    }

    @Override public String id() { return "chronos"; }
    @Override public String name() { return "&6&lEsfera do Tempo &8| &eChronos"; }
    @Override public Material material() { return Material.NETHERITE_AXE; }
    @Override public String tagline() { return "Cada golpe rouba um segundo do futuro."; }
    @Override public String passiveText() { return "Cada golpe reduz em 0,7s a recarga das habilidades do Chronos."; }
    @Override public List<Ability> abilities() { return abilities; }

    @Override
    public void passiveTick(Player p, int tick) {
        if (tick % 10 == 0) {
            Fx.ring(p.getLocation().add(0, 0.1, 0), 0.9, 12, Particle.DUST, Fx.dust(255, 215, 80, 0.9f));
        }
    }

    @Override
    public void onHit(Player attacker, LivingEntity victim, EntityDamageByEntityEvent e) {
        plugin.cooldowns().reduce(attacker.getUniqueId(), id() + ":", 700);
        Fx.particle(victim.getLocation().add(0, 1, 0), Particle.ENCHANT, 12, 0.4, 0.6, 0.4, 0.5);
    }

    @Override
    public void onQuit(UUID id) {
        history.remove(id);
    }

    @Override
    public void onDeath(UUID id) {
        history.remove(id);
    }

    // ---------- Registro do histórico (30s) ----------

    private void recordAll() {
        long now = System.currentTimeMillis();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!p.isDead() && carries(p)) {
                record(p, now);
            }
        }
        // descarta históricos de quem parou de carregar a esfera
        history.values().removeIf(d -> d.isEmpty() || now - d.peekLast().time() > 60_000L);
    }

    private boolean carries(Player p) {
        PlayerInventory inv = p.getInventory();
        if (plugin.registry().fromItem(inv.getItemInOffHand()) == this) {
            return true;
        }
        for (int i = 0; i < 9; i++) {
            if (plugin.registry().fromItem(inv.getItem(i)) == this) {
                return true;
            }
        }
        return false;
    }

    private void record(Player p, long now) {
        ArrayDeque<Snap> d = history.computeIfAbsent(p.getUniqueId(), k -> new ArrayDeque<>());
        Snap last = d.peekLast();
        // lacuna no registro (largou a esfera) ou troca de mundo: recomeça, para não voltar a dados velhos
        if (last != null && (now - last.time() > 5_000L || !p.getWorld().equals(last.loc().getWorld()))) {
            d.clear();
        }
        d.addLast(new Snap(p.getLocation().clone(), p.getHealth(), now));
        // mantém como mais antigo o registro mais recente com 30s ou mais (idade entre 30,0s e 30,25s)
        while (d.size() > 1) {
            Snap first = d.pollFirst();
            Snap second = d.peekFirst();
            if (now - second.time() >= WINDOW_MS) {
                continue;
            }
            d.addFirst(first);
            break;
        }
        while (d.size() > 400) {
            d.pollFirst();
        }
    }

    /** Primeiro local seguro (2 blocos livres, sem lava) a partir do ponto, subindo até 8 blocos. */
    private static Location safeSpot(Location loc) {
        Location l = loc.clone();
        for (int i = 0; i <= 8; i++) {
            Material feet = l.getBlock().getType();
            Material head = l.clone().add(0, 1, 0).getBlock().getType();
            if (l.getBlock().isPassable() && l.clone().add(0, 1, 0).getBlock().isPassable()
                    && feet != Material.LAVA && head != Material.LAVA) {
                return l;
            }
            l.add(0, 1, 0);
        }
        return null;
    }

    // ---------- Parada Temporal ----------

    private boolean timeStop(Player p) {
        Location c = p.getLocation().clone();
        List<LivingEntity> frozen = Combat.near(p, c, 8);
        Map<LivingEntity, Location> anchors = new LinkedHashMap<>();
        Map<LivingEntity, Boolean> hadAi = new HashMap<>();
        for (LivingEntity e : frozen) {
            anchors.put(e, e.getLocation().clone());
            hadAi.put(e, e.hasAI());
            if (!(e instanceof Player)) {
                e.setAI(false);
            }
            Combat.addEffect(e, PotionEffectType.SLOWNESS, 70, 9);
        }
        Fx.sound(c, Sound.BLOCK_BEACON_ACTIVATE, 2f, 0.5f);
        Fx.sound(c, Sound.BLOCK_BELL_USE, 2f, 0.5f);

        plugin.tasks().repeat(p, 0, 2, 30, (i, job) -> {
            Location base = c.clone().add(0, 0.2, 0);
            Fx.magicCircle(base, 8, GOLD, -i * 0.12);
            double minute = -i * 0.2;
            double hour = -i * 0.04;
            Fx.line(base, base.clone().add(Math.cos(minute) * 7, 0, Math.sin(minute) * 7), 0.4,
                    Particle.DUST, Fx.dust(255, 230, 120, 1.3f));
            Fx.line(base, base.clone().add(Math.cos(hour) * 4.5, 0, Math.sin(hour) * 4.5), 0.4,
                    Particle.DUST, Fx.dust(255, 190, 60, 1.6f));
            for (Map.Entry<LivingEntity, Location> en : anchors.entrySet()) {
                LivingEntity e = en.getKey();
                if (!e.isValid()) {
                    continue;
                }
                if (e instanceof Player) {
                    Location now = e.getLocation();
                    if (now.getWorld().equals(en.getValue().getWorld())
                            && now.distanceSquared(en.getValue()) > 0.04) {
                        Location back = en.getValue().clone();
                        back.setYaw(now.getYaw());
                        back.setPitch(now.getPitch());
                        e.teleport(back);
                    }
                }
                e.setVelocity(new Vector(0, 0, 0));
                Fx.particle(e.getLocation().add(0, 1, 0), Particle.END_ROD, 2, 0.3, 0.6, 0.3, 0.01);
            }
        }, () -> {
            for (LivingEntity e : frozen) {
                if (e.isValid() && !(e instanceof Player) && hadAi.getOrDefault(e, true)) {
                    e.setAI(true);
                }
            }
            Fx.ring(c.clone().add(0, 0.3, 0), 8, 60, Particle.END_ROD, null);
            Fx.sound(c, Sound.BLOCK_BEACON_DEACTIVATE, 2f, 0.6f);
        });
        return true;
    }

    // ---------- Retroceder ----------

    private boolean rewind(Player p) {
        ArrayDeque<Snap> d = history.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        // o registro mais antigo é o de ~30s atrás (ou o mais antigo disponível, com no mínimo 3s)
        Snap target = d == null ? null : d.peekFirst();
        if (target == null || now - target.time() < MIN_MS || !p.getWorld().equals(target.loc().getWorld())) {
            Fx.bar(p, "&cHistórico temporal insuficiente");
            return false;
        }
        Location to = safeSpot(target.loc());
        if (to == null) {
            Fx.bar(p, "&cO local de destino está bloqueado");
            return false;
        }
        Location from = p.getLocation().add(0, 1, 0);
        if (from.distanceSquared(to) < 64 * 64) {
            Fx.line(from, to.clone().add(0, 1, 0), 0.4, Particle.DUST, Fx.dust(255, 215, 80, 1.4f));
        }
        Fx.magicCircle(from.clone().subtract(0, 0.9, 0), 1.8, GOLD, 0);
        Fx.sound(from, Sound.ITEM_TRIDENT_RETURN, 1.5f, 0.5f);
        Fx.sound(from, Sound.BLOCK_BELL_USE, 1.5f, 1.2f);

        p.teleport(to);
        p.setFallDistance(0);
        p.setFireTicks(0);
        p.setVelocity(new Vector(0, 0, 0));
        // só recupera vida (nunca tira): evita matar o jogador e não toca em itens (sem duplicação)
        AttributeInstance max = p.getAttribute(Attribute.MAX_HEALTH);
        double cap = max == null ? 20 : max.getValue();
        p.setHealth(Math.min(cap, Math.max(p.getHealth(), target.health())));
        d.clear();

        Fx.magicCircle(to.clone().add(0, 0.1, 0), 1.8, GOLD, 0.5);
        Fx.particle(to.clone().add(0, 1, 0), Particle.ENCHANT, 60, 0.5, 0.9, 0.5, 0.8);
        Fx.sound(to, Sound.BLOCK_BELL_USE, 1.5f, 0.8f);
        return true;
    }

    // ---------- Surto Temporal ----------

    private boolean surge(Player p) {
        Combat.passiveEffect(p, PotionEffectType.SPEED, 80, 2);
        Combat.passiveEffect(p, PotionEffectType.HASTE, 80, 1);
        Location l = p.getLocation();
        Fx.magicCircle(l.clone().add(0, 0.1, 0), 2, GOLD, 0);
        Fx.sound(l, Sound.ENTITY_ZOMBIE_VILLAGER_CURE, 1f, 1.6f);
        Fx.sound(l, Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.8f);

        plugin.tasks().repeat(p, 0, 2, 40, (i, job) -> {
            Location cur = p.getLocation();
            for (double y = 0; y < 1.8; y += 0.3) {
                Fx.particle(cur.clone().add(0, y, 0), Particle.DUST_COLOR_TRANSITION, 2, 0.25, 0, 0.25, 0,
                        Fx.trans(GOLD, Color.WHITE, 1.4f));
            }
            Fx.particle(cur.clone().add(0, 1, 0), Particle.ENCHANT, 6, 0.4, 0.6, 0.4, 0.6);
        }, null);
        return true;
    }
}
