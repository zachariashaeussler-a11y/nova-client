package gg.nova.client;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

/** Profiles + settings, stored in config/novaclient.json */
public class Config {
    public static class Custom { public String name, type; public double value; }

    /** One module: on/off, color code, position (fractions of screen, -1 = auto) and scale %. */
    public static class Mod {
        public boolean on;
        public int color = -1;   // -1 theme, -2 rainbow, -3 pulse, -4 wave, -5 custom, else 0xRRGGBB
        public float fx = -1f, fy = -1f;
        public int scale = 100;
        public int rgb = 0xFFFFFF;            // used when color = "Own"
        public String label = "";             // custom label text ("" = default)
        public int bgMode = 0, shadowMode = 0, barMode = 0, outlineMode = 0; // 0 inherit, 1 on, 2 off
        public int val = -1;                  // render-module value (-1 = default)
    }

    public static class Settings {
        public String theme = "Neon";
        public int accent = 0x7C5CFF, accent2 = 0x00E5FF;
        public int bgColor = 0x000000, bgAlpha = 56, chromaSpeed = 30, textColor = 0xFFFFFF;
        public boolean hud = true, showFps = true, hudBg = true, hudShadow = true, hudBar = true, hudOutline = false;
        public int hudScale = 100, corner = 0;
        public boolean glow = false, tint = false;
        public int glowSize = 12, tintAlpha = 12, tintColor = -1;
        public String boost = "Off";
        public boolean entityCull = true;
        public int cullDist = 64;
        public int unfocusedFps = 15;   // unused
        public int zoomFov = 30, fov = 90, viewDist = 12;
        public boolean guiClassic = true;   // menu style: classic (old Minecraft look) or modern
        public boolean adaptOn = false;
        public int adaptTarget = 120, adaptMin = 5, adaptMax = 16;
        public Map<String, Mod> mods = new LinkedHashMap<>();
        public List<Custom> custom = new ArrayList<>();

        public Mod mod(String name) {
            if (mods == null) mods = new LinkedHashMap<>();
            return mods.computeIfAbsent(name, k -> { Mod m = new Mod(); m.on = k.equals("FPS"); return m; });
        }
    }

    public String active = "Default";
    public Map<String, Settings> profiles = new LinkedHashMap<>();
    public Map<String, String> backup = new HashMap<>();

    public static Config I;
    public static final Gson G = new GsonBuilder().setPrettyPrinting().create();

    static Path file() { return FabricLoader.getInstance().getConfigDir().resolve("novaclient.json"); }

    public static void load() {
        try { if (Files.exists(file())) I = G.fromJson(Files.readString(file()), Config.class); }
        catch (Exception ignored) {}
        if (I == null) I = new Config();
        if (I.profiles == null || I.profiles.isEmpty()) { I.profiles = new LinkedHashMap<>(); I.profiles.put("Default", new Settings()); }
        if (I.backup == null) I.backup = new HashMap<>();
        if (!I.profiles.containsKey(I.active)) I.active = I.profiles.keySet().iterator().next();
    }

    public static void save() {
        try { Files.writeString(file(), G.toJson(I)); } catch (IOException ignored) {}
    }

    public static Settings cur() {
        if (I == null) load();
        return I.profiles.get(I.active);
    }
}
