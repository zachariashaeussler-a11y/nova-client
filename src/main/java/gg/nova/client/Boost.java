package gg.nova.client;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.SimpleOption;

import java.util.Map;

/** FPS boost presets. They change vanilla video options; "Off" restores what you had. */
public class Boost {
    public static void apply(String mode) {
        GameOptions o = MinecraftClient.getInstance().options;
        if (mode.equals("Off")) { restore(o); return; }
        if (Config.I.backup.isEmpty()) snapshot(o);
        boolean max = mode.equals("Max");
        o.getViewDistance().setValue(max ? 6 : 10);
        o.getSimulationDistance().setValue(max ? 5 : 8);
        o.getEntityDistanceScaling().setValue(max ? 0.5 : 0.75);
        setEnum(o.getParticles(), max ? "MINIMAL" : "DECREASED");
        setEnum(o.getCloudRenderMode(), max ? "OFF" : "FAST");
        o.getBiomeBlendRadius().setValue(max ? 0 : 1);
        o.getMipmapLevels().setValue(max ? 0 : 2);
        o.getEntityShadows().setValue(false);
        o.getAo().setValue(!max);
        o.getEnableVsync().setValue(false);
        o.getMaxFps().setValue(max ? 260 : 144);
        o.write();
    }

    /** Sets an enum-valued option by constant name, without naming its type. */
    @SuppressWarnings({"unchecked", "rawtypes"})
    static void setEnum(SimpleOption opt, String name) {
        Object cur = opt.getValue();
        if (!(cur instanceof Enum<?> e)) return;
        for (Object c : e.getDeclaringClass().getEnumConstants()) {
            if (((Enum<?>) c).name().equals(name)) { opt.setValue(c); return; }
        }
    }

    static String nameOf(Object v) { return v instanceof Enum<?> e ? e.name() : String.valueOf(v); }

    static void snapshot(GameOptions o) {
        Map<String, String> b = Config.I.backup;
        b.put("view", "" + o.getViewDistance().getValue());
        b.put("sim", "" + o.getSimulationDistance().getValue());
        b.put("ent", "" + o.getEntityDistanceScaling().getValue());
        b.put("part", nameOf(o.getParticles().getValue()));
        b.put("cloud", nameOf(o.getCloudRenderMode().getValue()));
        b.put("blend", "" + o.getBiomeBlendRadius().getValue());
        b.put("mip", "" + o.getMipmapLevels().getValue());
        b.put("shadow", "" + o.getEntityShadows().getValue());
        b.put("ao", "" + o.getAo().getValue());
        b.put("vsync", "" + o.getEnableVsync().getValue());
        b.put("fps", "" + o.getMaxFps().getValue());
        Config.save();
    }

    static void restore(GameOptions o) {
        Map<String, String> b = Config.I.backup;
        if (b.isEmpty()) return;
        try {
            o.getViewDistance().setValue(Integer.parseInt(b.get("view")));
            o.getSimulationDistance().setValue(Integer.parseInt(b.get("sim")));
            o.getEntityDistanceScaling().setValue(Double.parseDouble(b.get("ent")));
            setEnum(o.getParticles(), b.get("part"));
            setEnum(o.getCloudRenderMode(), b.get("cloud"));
            o.getBiomeBlendRadius().setValue(Integer.parseInt(b.get("blend")));
            o.getMipmapLevels().setValue(Integer.parseInt(b.get("mip")));
            o.getEntityShadows().setValue(Boolean.parseBoolean(b.get("shadow")));
            o.getAo().setValue(Boolean.parseBoolean(b.get("ao")));
            o.getEnableVsync().setValue(Boolean.parseBoolean(b.get("vsync")));
            o.getMaxFps().setValue(Integer.parseInt(b.get("fps")));
            o.write();
        } catch (Exception ignored) {}
        b.clear();
        Config.save();
    }
}
