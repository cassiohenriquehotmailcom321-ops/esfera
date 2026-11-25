package com.arsenal.core;

import com.arsenal.ArsenalPlugin;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Efeito visual/sonoro de conjuração aplicado a TODA habilidade que dispara com sucesso.
 * Não altera nenhuma mecânica: só desenha partículas e toca sons.
 * Intensidade: botão direito (leve) < tecla F (média) < shift + direito (grande).
 */
public final class CastFx {

    private record Style(Particle accent, Sound main, Sound layer, float pitch) {
    }

    private static final Map<String, Style> STYLES = new HashMap<>();

    static {
        STYLES.put("zeraphiel", new Style(Particle.ELECTRIC_SPARK, Sound.ENTITY_LIGHTNING_BOLT_IMPACT, Sound.ITEM_TRIDENT_THUNDER, 1.4f));
        STYLES.put("ignaris", new Style(Particle.FLAME, Sound.ITEM_FIRECHARGE_USE, Sound.ENTITY_BLAZE_SHOOT, 0.8f));
        STYLES.put("glacialis", new Style(Particle.SNOWFLAKE, Sound.BLOCK_GLASS_BREAK, Sound.ENTITY_PLAYER_HURT_FREEZE, 1.2f));
        STYLES.put("umbra", new Style(Particle.SMOKE, Sound.ENTITY_ENDERMAN_TELEPORT, Sound.ENTITY_ILLUSIONER_CAST_SPELL, 0.7f));
        STYLES.put("gravitas", new Style(Particle.PORTAL, Sound.BLOCK_BEACON_ACTIVATE, Sound.BLOCK_RESPAWN_ANCHOR_CHARGE, 0.6f));
        STYLES.put("aegis", new Style(Particle.WAX_ON, Sound.BLOCK_BEACON_POWER_SELECT, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1.1f));
        STYLES.put("nullis", new Style(Particle.ENCHANT, Sound.ENTITY_EVOKER_CAST_SPELL, Sound.ENTITY_WARDEN_SONIC_CHARGE, 1.3f));
        STYLES.put("zephyria", new Style(Particle.CLOUD, Sound.ENTITY_WIND_CHARGE_WIND_BURST, Sound.ENTITY_PHANTOM_FLAP, 1.2f));
        STYLES.put("venenum", new Style(Particle.HAPPY_VILLAGER, Sound.ENTITY_SLIME_SQUISH, Sound.BLOCK_SWEET_BERRY_BUSH_BREAK, 0.8f));
        STYLES.put("chronos", new Style(Particle.GLOW, Sound.BLOCK_BELL_USE, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.9f));
    }

    private CastFx() {
    }

    public static void play(ArsenalPlugin plugin, Player p, Weapon w, Trigger trigger) {
        Style st = STYLES.get(w.id());
        if (st == null || !p.isOnline()) {
            return;
        }
        int[] c = SparkAura.color(w.id());
        Color color = Color.fromRGB(c[0], c[1], c[2]);
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.4f);
        int tier = trigger == Trigger.SNEAK_RIGHT ? 3 : trigger == Trigger.SWAP ? 2 : 1;

        Location o = p.getLocation();
        // clarão + sons em camadas
        Fx.particle(o.clone().add(0, 1, 0), Particle.END_ROD, 12 + tier * 6, 0.3, 0.5, 0.3, 0.12);
        Fx.sound(o, st.main(), 0.6f + 0.2f * tier, st.pitch());
        Fx.sound(o, st.layer(), 0.4f + 0.15f * tier, st.pitch() + 0.2f);
        if (tier >= 2) {
            Fx.sound(o, Sound.BLOCK_BEACON_POWER_SELECT, 0.5f, 1.6f);
        }
        if (tier == 3) {
            Fx.sound(o, Sound.ENTITY_GENERIC_EXPLODE, 0.35f, 1.5f);
        }

        int runs = 8 + tier * 4;
        plugin.tasks().repeat(p, 0L, 1L, runs, (i, job) -> {
            if (!p.isOnline() || p.isDead()) {
                job.end();
                return;
            }
            Location b = p.getLocation();
            double prog = (double) i / runs;

            // anéis de energia que sobem e se expandem
            double rr = 0.6 + prog * (1.2 + tier * 0.9);
            Location ringAt = b.clone().add(0, 0.1 + prog * 1.8, 0);
            Fx.ring(ringAt, rr, 22 + tier * 8, Particle.DUST, dust);
            if (i % 2 == 0) {
                Fx.ring(b.clone().add(0, 0.05, 0), rr * 1.3, 18 + tier * 8, st.accent(), null);
            }

            // espiral dupla subindo ao redor do corpo
            for (int s = 0; s < 2 + tier; s++) {
                double ang = prog * Math.PI * 6 + s * (2 * Math.PI / (2 + tier));
                double h = prog * 2.2;
                Location at = b.clone().add(Math.cos(ang) * 0.9, h, Math.sin(ang) * 0.9);
                Fx.particle(at, Particle.DUST, 2, 0.04, 0.04, 0.04, 0, dust);
                Fx.particle(at, st.accent(), 1, 0.05, 0.05, 0.05, 0.01);
            }

            if (tier >= 2 && i < 3) {
                // explosão de faíscas esférica
                Fx.sphere(b.clone().add(0, 1, 0), 0.8 + i * 0.9, 26 + tier * 8, Particle.DUST, dust);
            }
            if (tier == 3) {
                // coluna de luz + círculo mágico no chão
                if (i % 2 == 0) {
                    Fx.magicCircle(b.clone().add(0, 0.05, 0), 2.6, color, prog * Math.PI);
                }
                for (double y = 0; y < 3.5; y += 0.35) {
                    Fx.particle(b.clone().add(0, y, 0), Particle.DUST, 1, 0.12, 0, 0.12, 0, dust);
                }
                if (i == 0) {
                    Fx.particle(b.clone().add(0, 1, 0), Particle.DUST, 40, 0.8, 0.8, 0.8, 0, dust);
                }
            }
        }, null);

        SparkAura.pulse(p.getUniqueId(), 40 + tier * 20);
    }
}
