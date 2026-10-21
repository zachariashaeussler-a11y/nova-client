package gg.nova.client;

import net.minecraft.client.MinecraftClient;

/**
 * Adaptive render distance: every few seconds it compares your average FPS to a target
 * and moves the render distance down (FPS low) or up (FPS has headroom) within a range.
 * Your original render distance is restored when this is switched off.
 */
public class Adaptive {
    static int sum, n, ticks;
    static final String KEY = "adapt_view";

    public static void tick(MinecraftClient mc, Config.Settings s) {
        var o = mc.options;
        if (o == null) return;
        if (!s.adaptOn) {
            if (Config.I.backup.containsKey(KEY)) {
                String old = Config.I.backup.remove(KEY);
                try { o.getViewDistance().setValue(Integer.parseInt(old)); o.write(); } catch (Exception ignored) {}
                Config.save();
            }
            sum = 0; n = 0; ticks = 0;
            return;
        }
        if (s.mod("Render Dist").on) return;                        // that module owns render distance
        if (mc.world == null || mc.currentScreen != null) { sum = 0; n = 0; ticks = 0; return; }
        if (!Config.I.backup.containsKey(KEY)) { Config.I.backup.put(KEY, "" + o.getViewDistance().getValue()); Config.save(); }

        sum += mc.getCurrentFps();
        n++;
        ticks++;
        if (ticks < 100) return;                                    // about 5 seconds
        int avg = sum / Math.max(1, n);
        sum = 0; n = 0; ticks = 0;

        int rd = o.getViewDistance().getValue();
        int min = Math.min(s.adaptMin, s.adaptMax), max = Math.max(s.adaptMin, s.adaptMax);
        int next = rd;
        if (rd > max) next = max;
        else if (rd < min) next = min;
        else if (avg < s.adaptTarget * 0.9) next = Math.max(min, rd - 1);
        else if (avg > s.adaptTarget * 1.3) next = Math.min(max, rd + 1);
        if (next != rd) o.getViewDistance().setValue(next);
    }
}
