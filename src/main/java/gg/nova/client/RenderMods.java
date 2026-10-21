package gg.nova.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;

/**
 * Render modules: each one forces a single vanilla video option while ON and
 * restores your original value when turned OFF (originals are saved in the config).
 */
public class RenderMods {
    public static final String[] NAMES = {"Fullbright", "No Clouds", "No Shadows", "No Bob", "Min Particles", "Flat Light",
            "No Blend", "No FOV Fx", "No Distort", "Uncapped", "Low Entities", "FOV", "Render Dist"};

    static String target(String n, Config.Settings s) {
        return switch (n) {
            case "Fullbright" -> "1.0";
            case "No Clouds" -> "OFF";
            case "No Shadows" -> "false";
            case "No Bob" -> "false";
            case "Min Particles" -> "MINIMAL";
            case "Flat Light" -> "false";
            case "No Blend" -> "0";
            case "No FOV Fx" -> "0.0";
            case "No Distort" -> "0.0";
            case "Uncapped" -> "false,260";
            case "Low Entities" -> "0.5";
            case "FOV" -> "" + s.fov;
            case "Render Dist" -> "" + s.viewDist;
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
