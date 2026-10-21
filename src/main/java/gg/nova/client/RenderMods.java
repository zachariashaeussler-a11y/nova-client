package gg.nova.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

/**
 * Render modules: each one forces a single vanilla video option while ON and
 * restores your original value when turned OFF (originals are saved in the config).
 * Most have an adjustable value.
 */
public class RenderMods {
    public static final String[] NAMES = {"Fullbright", "No Clouds", "No Shadows", "No Bob", "Min Particles", "Flat Light",
            "No Blend", "No FOV Fx", "No Distort", "Uncapped", "Low Entities", "FOV", "Render Dist"};

    public static boolean hasValue(String n) { return !(n.equals("No Shadows") || n.equals("No Bob") || n.equals("Flat Light")); }
    public static boolean isChoice(String n) { return n.equals("No Clouds") || n.equals("Min Particles"); }

    public static int min(String n) {
        return switch (n) { case "Uncapped" -> 60; case "Low Entities" -> 50; case "FOV" -> 30; case "Render Dist" -> 2; default -> 0; };
    }
    public static int max(String n) {
        return switch (n) {
            case "Fullbright", "No FOV Fx", "No Distort" -> 100;
            case "No Clouds", "Min Particles" -> 1;
            case "No Blend" -> 7;
            case "Uncapped" -> 260;
            case "Low Entities" -> 500;
            case "FOV" -> 110;
            case "Render Dist" -> 32;
            default -> 1;
        };
    }
    public static int def(String n) {
        return switch (n) { case "Fullbright" -> 100; case "Uncapped" -> 260; case "Low Entities" -> 50; case "FOV" -> 90; case "Render Dist" -> 12; default -> 0; };
    }
    public static int value(Config.Settings s, String n) { Config.Mod m = s.mod(n); return m.val < 0 ? def(n) : m.val; }

    public static String describe(String n, int v) {
        return switch (n) {
            case "Fullbright" -> "Brightness " + v + "%";
            case "No Clouds" -> "Clouds: " + (v == 0 ? "Off" : "Fast");
            case "Min Particles" -> "Particles: " + (v == 0 ? "Minimal" : "Decreased");
            case "No Blend" -> "Biome blend " + v;
            case "No FOV Fx" -> "FOV effects " + v + "%";
            case "No Distort" -> "Distortion " + v + "%";
            case "Uncapped" -> "Max FPS " + (v >= 260 ? "Unlimited" : "" + v);
            case "Low Entities" -> "Entity range " + v + "%";
            case "FOV" -> "FOV " + v;
            case "Render Dist" -> "Render dist " + v;
            default -> n;
        };
    }

    static String target(String n, Config.Settings s) {
        int v = value(s, n);
        return switch (n) {
            case "Fullbright" -> "" + (v / 100.0);
            case "No Clouds" -> v == 0 ? "OFF" : "FAST";
            case "No Shadows" -> "false";
            case "No Bob" -> "false";
            case "Min Particles" -> v == 0 ? "MINIMAL" : "DECREASED";
            case "Flat Light" -> "false";
            case "No Blend" -> "" + v;
            case "No FOV Fx" -> "" + (v / 100.0);
            case "No Distort" -> "" + (v / 100.0);
            case "Uncapped" -> "false," + v;
            case "Low Entities" -> "" + (v / 100.0);
            case "FOV" -> "" + v;
            case "Render Dist" -> "" + v;
            default -> "";
        };
    }

    static String get(String n, GameOptions o) {
        return switch (n) {
            case "Fullbright" -> "" + o.getGamma().getValue();
            case "No Clouds" -> Boost.nameOf(o.getCloudRenderMode().getValue());
            case "No Shadows" -> "" + o.getEntityShadows().getValue();
            case "No Bob" -> "" + o.getBobView().getValue();
            case "Min Particles" -> Boost.nameOf(o.getParticles().getValue());
            case "Flat Light" -> "" + o.getAo().getValue();
            case "No Blend" -> "" + o.getBiomeBlendRadius().getValue();
            case "No FOV Fx" -> "" + o.getFovEffectScale().getValue();
            case "No Distort" -> "" + o.getDistortionEffectScale().getValue();
            case "Uncapped" -> o.getEnableVsync().getValue() + "," + o.getMaxFps().getValue();
            case "Low Entities" -> "" + o.getEntityDistanceScaling().getValue();
            case "FOV" -> "" + o.getFov().getValue();
            case "Render Dist" -> "" + o.getViewDistance().getValue();
            default -> "";
        };
    }

    static void set(String n, GameOptions o, String v) {
        try {
            switch (n) {
                case "Fullbright" -> o.getGamma().setValue(Double.parseDouble(v));
                case "No Clouds" -> Boost.setEnum(o.getCloudRenderMode(), v);
                case "No Shadows" -> o.getEntityShadows().setValue(Boolean.parseBoolean(v));
                case "No Bob" -> o.getBobView().setValue(Boolean.parseBoolean(v));
                case "Min Particles" -> Boost.setEnum(o.getParticles(), v);
                case "Flat Light" -> o.getAo().setValue(Boolean.parseBoolean(v));
                case "No Blend" -> o.getBiomeBlendRadius().setValue(Integer.parseInt(v));
                case "No FOV Fx" -> o.getFovEffectScale().setValue(Double.parseDouble(v));
                case "No Distort" -> o.getDistortionEffectScale().setValue(Double.parseDouble(v));
                case "Uncapped" -> {
                    String[] p = v.split(",");
                    o.getEnableVsync().setValue(Boolean.parseBoolean(p[0]));
                    o.getMaxFps().setValue(Integer.parseInt(p[1]));
                }
                case "Low Entities" -> o.getEntityDistanceScaling().setValue(Double.parseDouble(v));
                case "FOV" -> o.getFov().setValue(Integer.parseInt(v));
                case "Render Dist" -> o.getViewDistance().setValue(Integer.parseInt(v));
                default -> { }
            }
        } catch (Exception ignored) {}
    }

    public static void sync(Config.Settings s) {
        GameOptions o = MinecraftClient.getInstance().options;
        if (o == null) return;
        boolean changed = false;
        for (String n : NAMES) {
            Config.Mod m = s.mod(n);
            String key = "rm_" + n;
            boolean has = Config.I.backup.containsKey(key);
            if (m.on) {
                if (n.equals("FOV") && NovaClient.savedFov >= 0) continue; // zooming
                if (!has) { Config.I.backup.put(key, get(n, o)); changed = true; }
                String t = target(n, s);
                if (!t.equals(get(n, o))) set(n, o, t);
            } else if (has) {
                set(n, o, Config.I.backup.remove(key));
                changed = true;
            }
        }
        if (changed) { Config.save(); o.write(); }
    }
}
