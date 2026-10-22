package com.example.pearltrail;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Client-side mod: every ender pearl you can see (yours or other players') leaves a
 * purple particle trail along its flight path. The whole trail stays visible while the
 * pearl flies and disappears 3 seconds after the pearl lands.
 */
public class PearlTrailClient implements ClientModInitializer {
    private static final int HOLD_TICKS = 60;            // full trail stays 3 seconds after landing
    private static final int WIPE_TICKS = 30;            // then it wipes away start-to-end over 1.5 seconds
    private static final int LANDED_AFTER_MISSING = 3;   // pearl must be gone 3 ticks in a row to count as landed
    private static final int REEMIT_INTERVAL = 1;        // redraw the trail every tick so it never pulses
    private static final int PARTICLE_LIFETIME = 2;      // very short life so each tick looks identical
    private static final double SPACING = 0.5;           // blocks between trail points
    private static final int MAX_POINTS = 500;           // safety cap per trail
    private static final int PURPLE = 0xAA00FF;
    private static final DustParticleOptions PURPLE_DUST = new DustParticleOptions(PURPLE, 1.0f);

    private static final class Trail {
        final List<double[]> points = new ArrayList<>();
        double lastX, lastY, lastZ;
        boolean hasLast;
        long landedAt = -1;   // client tick when the pearl disappeared, -1 while still flying
        int missingTicks = 0; // consecutive ticks the pearl has not been seen
    }

    private final Map<Integer, Trail> trails = new HashMap<>();
    private ClientLevel lastLevel;
    private long tickCounter;

    @Override
    public void onInitializeClient() {
        ClientTickEvents.END_CLIENT_TICK.register(this::tick);
    }

    private void tick(Minecraft mc) {
        ClientLevel level = mc.level;
        if (level == null) {
            trails.clear();
            lastLevel = null;
            return;
        }
        if (level != lastLevel) {
            trails.clear();
            lastLevel = level;
        }
        tickCounter++;

        // 1) extend trails of pearls that are still flying
        Set<Integer> seen = new HashSet<>();
        for (Entity e : level.entitiesForRendering()) {
            if (!"ender_pearl".equals(EntityType.getKey(e.getType()).getPath())) continue;

            int id = e.getId();
            seen.add(id);
            Trail t = trails.computeIfAbsent(id, k -> new Trail());
            addSegment(t, e.getX(), e.getY(), e.getZ());
        }

        // 2) pearls that stay gone for a few ticks in a row have landed: start the countdown
        for (Map.Entry<Integer, Trail> entry : trails.entrySet()) {
            Trail t = entry.getValue();
            if (seen.contains(entry.getKey())) {
                t.missingTicks = 0;
                t.landedAt = -1;   // pearl is (still) flying
            } else {
                t.missingTicks++;
                if (t.missingTicks >= LANDED_AFTER_MISSING && t.landedAt < 0) {
                    t.landedAt = tickCounter - t.missingTicks;   // moment it actually disappeared
                }
            }
        }

        // 3) drop trails once the hold time and the wipe are both over
        Iterator<Map.Entry<Integer, Trail>> it = trails.entrySet().iterator();
        while (it.hasNext()) {
            Trail t = it.next().getValue();
            if (t.landedAt >= 0 && tickCounter - t.landedAt >= HOLD_TICKS + WIPE_TICKS) {
                it.remove();
            }
        }

        // 4) redraw the surviving trails
        if (tickCounter % REEMIT_INTERVAL == 0) {
            for (Trail t : trails.values()) {
                // The full trail stays for 3 seconds after landing, then wipes away from the
                // start (where it was thrown) to the end (where it landed).
                int skip = 0;
                if (t.landedAt >= 0) {
                    long sinceHoldEnded = tickCounter - t.landedAt - HOLD_TICKS;
                    if (sinceHoldEnded > 0) {
                        double progress = Math.min(1.0, (double) sinceHoldEnded / WIPE_TICKS);
                        skip = (int) Math.floor(progress * t.points.size());
                    }
                }
                for (int i = skip; i < t.points.size(); i++) {
                    double[] p = t.points.get(i);
                    Particle particle = mc.particleEngine.createParticle(PURPLE_DUST, p[0], p[1], p[2], 0.0, 0.0, 0.0);
                    if (particle != null) {
                        particle.setLifetime(PARTICLE_LIFETIME);
                    }
                }
            }
        }
    }

    /** Adds evenly spaced points from the last position to (x,y,z) so fast pearls leave a solid line. */
    private static void addSegment(Trail t, double x, double y, double z) {
        if (!t.hasLast) {
            t.points.add(new double[] {x, y, z});
            t.lastX = x; t.lastY = y; t.lastZ = z;
            t.hasLast = true;
            return;
        }
        double dx = x - t.lastX, dy = y - t.lastY, dz = z - t.lastZ;
        double dist = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (dist < 1.0E-4 || dist > 128.0) {
            t.lastX = x; t.lastY = y; t.lastZ = z;
            return;
        }
        int steps = Math.max(1, (int) Math.ceil(dist / SPACING));
        for (int i = 1; i <= steps && t.points.size() < MAX_POINTS; i++) {
            double f = (double) i / steps;
            t.points.add(new double[] {t.lastX + dx * f, t.lastY + dy * f, t.lastZ + dz * f});
        }
        t.lastX = x; t.lastY = y; t.lastZ = z;
    }
}
