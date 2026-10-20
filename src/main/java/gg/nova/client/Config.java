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

    public static class Settings {
        public String theme = "Neon";
        public int accent = 0x7C5CFF, accent2 = 0x00E5FF;
        public boolean hud = true, showFps = true, hudBg = true;
        public int hudScale = 100, corner = 0;
        public String boost = "Off";
        public boolean entityCull = true;
        public int cullDist = 64;       // blocks
        public int unfocusedFps = 15;   // 0 = off
        public List<Custom> custom = new ArrayList<>();
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
