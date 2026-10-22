package com.example.pearltrail;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * Client-side mod: every ender pearl you can see (yours or other players') leaves a
 * purple particle trail along its flight path. Each particle lives exactly 5 seconds.
 */
public class PearlTrailClient implements ClientModInitializer {
    private static final int TRAIL_LIFETIME_TICKS = 100;  // 5 seconds
    private static final double SPACING = 0.3;             // blocks between particles
    private static final int PURPLE = 0xAA00FF;
    private static final DustParticleOptions PURPLE_DUST = new DustParticleOptions(PURPLE, 1.0f);

    /** Last known position per pearl entity id. */
    private final Map<Integer, double[]> lastPos = new HashMap<>();
    private ClientLevel lastLevel;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            lastPos.clear();
            lastLevel = null;
            return;
        }
        if (level != lastLevel) {
            lastPos.clear();
            lastLevel = level;
        }

        Set<Integer> seen = new HashSet<>();
        for (Entity e : level.entitiesForRendering()) {
            if (!"ender_pearl".equals(EntityType.getKey(e.getType()).getPath())) continue;

            int id = e.getId();
            seen.add(id);
            double x = e.getX(), y = e.getY(), z = e.getZ();

            double[] prev = lastPos.get(id);
            if (prev == null) {
                spawn(mc, x, y, z);
            } else {
                double dx = x - prev[0], dy = y - prev[1], dz = z - prev[2];
                double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (dist > 1.0E-4 && dist < 128.0) {
                    int steps = Math.max(1, (int) Math.ceil(dist / SPACING));
                    for (int i = 1; i <= steps; i++) {
                        double f = (double) i / steps;
                        spawn(mc, prev[0] + dx * f, prev[1] + dy * f, prev[2] + dz * f);
                    }
                }
            }
            lastPos.put(id, new double[] {x, y, z});
        }

        // forget pearls that have landed / despawned
        Iterator<Integer> it = lastPos.keySet().iterator();
        while (it.hasNext()) {
            if (!seen.contains(it.next())) it.remove();
        }
    }

    private static void spawn(Minecraft mc, double x, double y, double z) {
        Particle p = mc.particleEngine.createParticle(PURPLE_DUST, x, y, z, 0.0, 0.0, 0.0);
        if (p != null) {
            p.setLifetime(TRAIL_LIFETIME_TICKS);
        }
    }
}
