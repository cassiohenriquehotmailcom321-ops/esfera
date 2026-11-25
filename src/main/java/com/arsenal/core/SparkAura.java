package com.arsenal.core;

import com.arsenal.ArsenalPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;

/**
 * Faíscas coloridas que giram ao redor do corpo de quem segura uma esfera.
 * Um único loop (a cada 2 ticks) para todos os jogadores.
 */
public final class SparkAura {

    private static final Map<String, int[]> COLORS = new HashMap<>();

    static {
        COLORS.put("zeraphiel", new int[]{255, 235, 60});
        COLORS.put("ignaris", new int[]{255, 110, 20});
        COLORS.put("glacialis", new int[]{120, 220, 255});
        COLORS.put("umbra", new int[]{150, 60, 220});
        COLORS.put("gravitas", new int[]{190, 90, 255});
        COLORS.put("aegis", new int[]{80, 160, 255});
        COLORS.put("nullis", new int[]{235, 235, 255});
        COLORS.put("zephyria", new int[]{110, 255, 190});
        COLORS.put("venenum", new int[]{110, 255, 60});
        COLORS.put("chronos", new int[]{255, 200, 90});
    }

    private static final Map<java.util.UUID, Integer> BOOST = new HashMap<>();
    private final ArsenalPlugin plugin;
    private int t = 0;

    public static int[] color(String id) {
        return COLORS.getOrDefault(id, new int[]{255, 255, 255});
    }

    /** Intensifica a aura por alguns ticks (chamado ao conjurar uma habilidade). */
    public static void pulse(java.util.UUID id, int ticks) {
        BOOST.put(id, ticks);
    }


    public SparkAura(ArsenalPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getScheduler().runTaskTimer(plugin, this::loop, 20L, 2L);
    }

    private void loop() {
        t += 2;
        if (!plugin.getConfig().getBoolean("spark-aura", true)) {
            return;
        }
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.isDead() || p.getGameMode() == org.bukkit.GameMode.SPECTATOR) {
                continue;
            }
            Weapon w = plugin.registry().fromItem(p.getInventory().getItemInMainHand());
            if (w == null) {
                continue;
            }
            int[] c = color(w.id());
            int boost = BOOST.getOrDefault(p.getUniqueId(), 0);
            if (boost > 0) {
                BOOST.put(p.getUniqueId(), boost - 2);
            } else {
                BOOST.remove(p.getUniqueId());
            }
            int orbs = boost > 0 ? 7 : 3;
            Location base = p.getLocation();
            double ph = t * 0.22;
            // 3 faíscas orbitando em alturas diferentes (espiral ao redor do corpo)
            for (int i = 0; i < orbs; i++) {
                double ang = ph * (boost > 0 ? 1.8 : 1) + i * (Math.PI * 2 / orbs);
                double h = 0.15 + 0.85 * (0.5 + 0.5 * Math.sin(t * 0.09 + i * 2.1)) * 1.9;
                Location at = base.clone().add(Math.cos(ang) * 0.85, h, Math.sin(ang) * 0.85);
                Fx.particle(at, Particle.DUST, 1, 0.02, 0.02, 0.02, 0, Fx.dust(c[0], c[1], c[2], 0.8f));
            }
            // faíscas soltas que saem do corpo e sobem
            if (boost > 0 || t % 4 == 0) {
                Location at = base.clone().add((Math.random() - 0.5) * 1.1, 0.2 + Math.random() * 1.6, (Math.random() - 0.5) * 1.1);
                Fx.particle(at, Particle.DUST, 1, 0.05, 0.1, 0.05, 0, Fx.dust(c[0], c[1], c[2], 0.9f));
                Fx.particle(at, Particle.DUST, 1, 0.02, 0.05, 0.02, 0, Fx.dust(c[0], c[1], c[2], 0.7f));
            }
        }
    }
}
