package gg.nova.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.item.ItemStack;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class NovaClient implements ClientModInitializer {
    public static KeyBinding menuKey, zoomKey, lyricsKey;
    public static boolean perfOpen;                    // Performance tab is open: keep sampling frame times

    /** HUD modules (movable boxes). */
    public static final String[] HUDMODS = {"FPS", "Frametime", "Graph", "Coords", "Chunk", "Nether", "Direction", "Angles", "Speed", "CPS", "Ping",
            "Memory", "Clock", "Playtime", "World Time", "Health", "Hunger", "Held Item", "Server", "Lyrics", "Watermark", "Armor", "Keys"};
    /** All modules shown in the Modules tab. */
    public static final String[] MODS = {"FPS", "Frametime", "Graph", "Coords", "Chunk", "Nether", "Direction", "Angles", "Speed", "CPS", "Ping",
            "Memory", "Clock", "Playtime", "World Time", "Health", "Hunger", "Held Item", "Server", "Lyrics", "Watermark", "Armor", "Keys",
            "Sprint", "Zoom", "AutoRespawn", "IdleFPS"};

    /** Color choices: negative codes are special modes. */
    static final String[] PN = {"Theme", "Rainbow", "Pulse", "Wave", "Custom", "Own", "White", "Red", "Orange", "Gold", "Yellow", "Lime", "Green", "Mint",
            "Teal", "Cyan", "Sky", "Blue", "Indigo", "Purple", "Magenta", "Pink", "Coral", "Peach", "Silver", "Gray", "Black"};
    static final int[] PC = {-1, -2, -3, -4, -5, -6, 0xFFFFFF, 0xFF4D4D, 0xFF9F43, 0xFFD700, 0xFFE066, 0xA6E22E, 0x3DDC97, 0x98FFC8,
            0x14B8A6, 0x00E5FF, 0x87CEEB, 0x2F8CFF, 0x4B5BD8, 0x7C5CFF, 0xFF00FF, 0xFF4D8D, 0xFF7F50, 0xFFDAB9, 0xC0C0C0, 0x808080, 0x000000};

    static int idxOf(int code) { for (int i = 0; i < PC.length; i++) if (PC[i] == code) return i; return 0; }
    public static String colorName(int code) { return PN[idxOf(code)]; }
    public static int nextColor(int code) { return PC[(idxOf(code) + 1) % PC.length]; }
    public static int prevColor(int code) { return PC[(idxOf(code) + PC.length - 1) % PC.length]; }

    static final java.util.HashMap<String, String> DISP = new java.util.HashMap<>();
    static final String[] SAMPLE_LY = {"Your current lyric line", "The next line appears here"};
    static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    static final ArrayDeque<Long> clicksL = new ArrayDeque<>(), clicksR = new ArrayDeque<>();
    static boolean wasL, wasR;
    static double speed, lx, lz;
    static int ping, tickN;
    public static int savedFov = -1;
    public static boolean idle;                       // window unfocused and IdleFPS active
    static int idleSaved;
    static final long START = System.currentTimeMillis();
    static final float[] FT = new float[60];          // last 60 frame times (ms)
    static int fi;
    static long lastNs;
    static java.util.List<Box> CACHE;
    static int cacheW, cacheH;
    static boolean dirty = true;
    public static boolean cullOn;
    public static double cullSq;

    @Override
    @SuppressWarnings("deprecation")
    public void onInitializeClient() {
        Config.load();
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("novaclient", "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.novaclient.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.novaclient.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, category));
        lyricsKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.novaclient.lyrics", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_K, category));
        ClientTickEvents.END_CLIENT_TICK.register(NovaClient::tick);
        HudRenderCallback.EVENT.register((ctx, tick) -> hud(ctx));
    }

    /* ---------------- per-tick logic ---------------- */

    static void tick(MinecraftClient mc) {
        while (menuKey.wasPressed()) mc.setScreen(new NovaScreen(mc.currentScreen));
        Config.Settings s = Config.cur();
        while (lyricsKey.wasPressed()) { if (s.mod("Lyrics").on) Lyrics.toggle(); }
        cullOn = s.entityCull;
        cullSq = (double) s.cullDist * s.cullDist;
        if (mc.options != null && ++tickN % 10 == 0) RenderMods.sync(s);
        if (tickN % 5 == 0) { DISP.clear(); dirty = true; }   // HUD text and layout are rebuilt 4x per second, not every frame
        Adaptive.tick(mc, s);

        // IdleFPS: 20 FPS while the window is in the background, your own cap back when you return
        var mf = mc.options.getMaxFps();
        boolean wantIdle = false;
        if (s.mod("IdleFPS").on) {
            long win = GLFW.glfwGetCurrentContext();
            wantIdle = win != 0 && GLFW.glfwGetWindowAttrib(win, GLFW.GLFW_FOCUSED) == 0;
        }
        if (wantIdle) {
            if (!idle) { idleSaved = mf.getValue(); idle = true; }
            if (mf.getValue() != 20) mf.setValue(20);
        } else if (idle) {
            mf.setValue(idleSaved);
            idle = false;
        }

        var fov = mc.options.getFov();
        if (mc.player == null) {
            if (savedFov >= 0) { fov.setValue(savedFov); savedFov = -1; }
            return;
        }
        long now = System.currentTimeMillis();
        boolean l = mc.options.attackKey.isPressed(), r = mc.options.useKey.isPressed();
        if (l && !wasL) clicksL.addLast(now);
        if (r && !wasR) clicksR.addLast(now);
        wasL = l; wasR = r;
        while (!clicksL.isEmpty() && now - clicksL.peekFirst() > 1000) clicksL.pollFirst();
        while (!clicksR.isEmpty() && now - clicksR.peekFirst() > 1000) clicksR.pollFirst();

        double dx = mc.player.getX() - lx, dz = mc.player.getZ() - lz;
        lx = mc.player.getX(); lz = mc.player.getZ();
        speed = speed * 0.8 + Math.sqrt(dx * dx + dz * dz) * 20 * 0.2;

        var nh = mc.getNetworkHandler();
        if (nh != null) {
            var e = nh.getPlayerListEntry(mc.player.getUuid());
            if (e != null) ping = e.getLatency();
        }

        if (s.mod("Sprint").on && mc.currentScreen == null && mc.options.forwardKey.isPressed()
                && !mc.player.isSneaking() && !mc.player.isUsingItem()
                && mc.player.getHungerManager().getFoodLevel() > 6) {
            mc.player.setSprinting(true);
        }

        if (s.mod("AutoRespawn").on && mc.player.isDead()) mc.player.requestRespawn();

        boolean zoom = s.mod("Zoom").on && zoomKey.isPressed() && mc.currentScreen == null;
        if (zoom) {
            if (savedFov < 0) savedFov = fov.getValue();
            if (fov.getValue() != s.zoomFov) fov.setValue(s.zoomFov);
        } else if (savedFov >= 0) {
            fov.setValue(savedFov);
            savedFov = -1;
        }
    }

    /* ---------------- colors ---------------- */

    static int resolve(Config.Settings s, int code, int i, int n) {
        long t = System.currentTimeMillis();
        if (code == -2) return 0xFF000000 | hsv(((t / 1000.0) * s.chromaSpeed / 100.0 + i * 0.08) % 1.0);
        if (code == -3) return 0xFF000000 | lerp(s.accent, s.accent2, (float) (0.5 + 0.5 * Math.sin(t / 400.0 * s.chromaSpeed / 30.0)));
        if (code == -4) return 0xFF000000 | lerp(s.accent, s.accent2, (float) (0.5 + 0.5 * Math.sin(t / 600.0 * s.chromaSpeed / 30.0 + i * 0.9)));
        if (code == -5) return 0xFF000000 | s.textColor;
        if (code == -6) return 0xFFFFFFFF;
        if (code == -1) return 0xFF000000 | lerp(s.accent, s.accent2, n <= 1 ? 0f : i / (float) (n - 1));
        return 0xFF000000 | code;
    }

    static int resolveM(Config.Settings s, Config.Mod m, int i, int n) {
        return m.color == -6 ? (0xFF000000 | m.rgb) : resolve(s, m.color, i, n);
    }

    static boolean flag(int mode, boolean global) { return mode == 1 || (mode == 0 && global); }

    static int lerp(int a, int b, float t) {
        int r = (int) (((a >> 16) & 255) * (1 - t) + ((b >> 16) & 255) * t);
        int g = (int) (((a >> 8) & 255) * (1 - t) + ((b >> 8) & 255) * t);
        int bl = (int) ((a & 255) * (1 - t) + (b & 255) * t);
        return (r << 16) | (g << 8) | bl;
    }

    static int hsv(double h) {
        float hh = (float) (h * 6), f = hh - (int) hh;
        float p = .3f, q = 1 - .7f * f, t = 1 - .7f * (1 - f);
        float r, g, b;
        switch (((int) hh) % 6) {
            case 0 -> { r = 1; g = t; b = p; }
            case 1 -> { r = q; g = 1; b = p; }
            case 2 -> { r = p; g = 1; b = t; }
            case 3 -> { r = p; g = q; b = 1; }
            case 4 -> { r = t; g = p; b = 1; }
            default -> { r = 1; g = p; b = q; }
        }
        return ((int) (r * 255) << 16) | ((int) (g * 255) << 8) | (int) (b * 255);
    }

    /* ---------------- module boxes ---------------- */

    public static class Box { public String name, text = ""; public int x, y, w, h; public float sc; }

    public static String defaultLabel(String n) {
        return switch (n) {
            case "FPS" -> "FPS"; case "Coords" -> "XYZ"; case "CPS" -> "CPS"; case "Ping" -> "Ping";
            case "Frametime" -> "Frame"; case "Chunk" -> "Chunk"; case "Nether" -> "Nether"; case "Angles" -> "Look";
            case "Playtime" -> "Played"; case "Health" -> "HP"; case "Hunger" -> "Food";
            case "Direction" -> "Facing"; case "Memory" -> "RAM"; case "Watermark" -> "ORBITAL";
            default -> "";
        };
    }

    static String value(MinecraftClient mc, String n) {
        var p = mc.player;
        return switch (n) {
            case "FPS" -> "" + mc.getCurrentFps();
            case "Coords" -> p == null ? "0 0 0" : (int) Math.floor(p.getX()) + " " + (int) Math.floor(p.getY()) + " " + (int) Math.floor(p.getZ());
            case "CPS" -> clicksL.size() + " | " + clicksR.size();
            case "Ping" -> ping + " ms";
            case "Direction" -> p == null ? "NORTH" : p.getHorizontalFacing().name();
            case "Speed" -> String.format(Locale.ROOT, "%.1f b/s", speed);
            case "Memory" -> {
                Runtime rt = Runtime.getRuntime();
                yield ((rt.totalMemory() - rt.freeMemory()) >> 20) + "/" + (rt.maxMemory() >> 20) + " MB";
            }
            case "Clock" -> LocalTime.now().format(CLOCK);
            case "Frametime" -> {
                float sum = 0, mx = 0;
                int c = 0;
                for (float f : FT) if (f > 0) { sum += f; c++; mx = Math.max(mx, f); }
                yield c == 0 ? "-" : String.format(Locale.ROOT, "%.1f ms (max %.0f)", sum / c, mx);
            }
            case "Chunk" -> p == null ? "0 0" : ((int) Math.floor(p.getX()) >> 4) + " " + ((int) Math.floor(p.getZ()) >> 4);
            case "Nether" -> p == null ? "0 0 0" : (int) Math.floor(p.getX() / 8) + " " + (int) Math.floor(p.getY()) + " " + (int) Math.floor(p.getZ() / 8);
            case "Angles" -> p == null ? "0 / 0" : Math.round(((p.getYaw() % 360) + 360) % 360) + " / " + Math.round(p.getPitch());
            case "Playtime" -> {
                long sec = (System.currentTimeMillis() - START) / 1000;
                yield sec >= 3600 ? String.format(Locale.ROOT, "%d:%02d:%02d", sec / 3600, sec % 3600 / 60, sec % 60)
                        : String.format(Locale.ROOT, "%d:%02d", sec / 60, sec % 60);
            }
            case "World Time" -> {
                if (mc.world == null) yield "--:--";
                long t = mc.world.getTimeOfDay();
                yield String.format(Locale.ROOT, "%02d:%02d  Day %d", (int) ((t / 1000 + 6) % 24), (int) ((t % 1000) * 60 / 1000), t / 24000 + 1);
            }
            case "Health" -> {
                if (p == null) yield "20.0";
                float ab = p.getAbsorptionAmount();
                yield String.format(Locale.ROOT, "%.1f", p.getHealth()) + (ab > 0 ? String.format(Locale.ROOT, " +%.1f", ab) : "");
            }
            case "Hunger" -> p == null ? "20/20" : p.getHungerManager().getFoodLevel() + "/20";
            case "Held Item" -> {
                if (p == null) yield "Hand";
                ItemStack st = p.getMainHandStack();
                if (st.isEmpty()) yield "Empty hand";
                String nm = st.getName().getString();
                if (st.getCount() > 1) nm += " x" + st.getCount();
                if (st.isDamageable()) nm += "  " + (st.getMaxDamage() - st.getDamage()) + "/" + st.getMaxDamage();
                yield nm;
            }
            case "Server" -> {
                var si = mc.getCurrentServerEntry();
                yield si != null ? si.address : (mc.isInSingleplayer() ? "Singleplayer" : "Not connected");
            }
            case "Watermark" -> Config.I.active;
            default -> "";
        };
    }

    static String display(MinecraftClient mc, Config.Settings s, String n) {
        Config.Mod m = s.mod(n);
        String lab = (m.label == null || m.label.isEmpty()) ? defaultLabel(n) : m.label;
        String v = value(mc, n);
        return lab.isEmpty() ? v : lab + " " + v;
    }

    /** Enabled HUD modules with their on-screen position and size. Unmoved modules are auto-placed. */
    public static List<Box> boxes(MinecraftClient mc, Config.Settings s, int sw, int sh) {
        var tr = mc.textRenderer;
        List<Box> out = new ArrayList<>();
        float base = s.hudScale / 100f;
        for (String n : HUDMODS) {
            Config.Mod m = s.mod(n);
            if (!m.on) continue;
            Box b = new Box();
            b.name = n;
            b.sc = base * m.scale / 100f;
            if (n.equals("Armor")) { b.w = Math.round(56 * b.sc); b.h = Math.round(72 * b.sc); }
            else if (n.equals("Keys")) { b.w = Math.round(60 * b.sc); b.h = Math.round(72 * b.sc); }
            else if (n.equals("Graph")) { b.w = Math.round(64 * b.sc); b.h = Math.round(28 * b.sc); }
            else if (n.equals("Lyrics")) { b.w = Math.round(240 * b.sc); b.h = Math.round(30 * b.sc); }
            else { String dd = DISP.get(n); if (dd == null) { dd = display(mc, s, n); DISP.put(n, dd); } b.text = dd; b.w = Math.round((tr.getWidth(b.text) + 10) * b.sc); b.h = Math.round(14 * b.sc); }
            out.add(b);
        }
        int top = 4, bottom = 4;
        for (Box b : out) {
            Config.Mod m = s.mod(b.name);
            if (m.fx >= 0) { b.x = (int) (m.fx * sw); b.y = (int) (m.fy * sh); }
            else if (b.name.equals("Keys")) { b.x = 6; b.y = sh / 2 - b.h / 2; }
            else if (b.name.equals("Armor")) { b.x = sw - b.w - 4; b.y = sh / 2 - b.h / 2; }
            else {
                b.x = (s.corner % 2 == 0) ? 4 : sw - b.w - 4;
                if (s.corner < 2) { b.y = top; top += b.h + 2; }
                else { b.y = sh - bottom - b.h; bottom += b.h + 2; }
            }
            b.x = Math.max(0, Math.min(sw - b.w, b.x));
            b.y = Math.max(0, Math.min(sh - b.h, b.y));
        }
        return out;
    }

    /** Filled rounded rectangle (no overlapping fills, so translucent colors stay even). */
    static void rbox(DrawContext c, int x, int y, int w, int h, int r, int col) {
        if (Config.cur().guiClassic) {            // classic: square, bevelled like old Minecraft buttons
            c.fill(x, y, x + w, y + h, col);
            if (((col >>> 24) & 255) >= 0xF0 && h >= 8) {
                int lt = 0xFF000000 | lerp(col, 0xFFFFFF, 0.2f), dk = 0xFF000000 | lerp(col, 0x000000, 0.4f);
                c.fill(x, y, x + w, y + 1, lt);
                c.fill(x, y, x + 1, y + h, lt);
                c.fill(x, y + h - 1, x + w, y + h, dk);
                c.fill(x + w - 1, y, x + w, y + h, dk);
            }
            return;
        }
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        for (int i = 0; i < r; i++) {
            double dy = r - i - 0.5;
            int inset = r - (int) Math.round(Math.sqrt(r * r - dy * dy));
            c.fill(x + inset, y + i, x + w - inset, y + i + 1, col);
            c.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, col);
        }
        c.fill(x, y + r, x + w, y + h - r, col);
    }

    static void outline(DrawContext c, int x, int y, int w, int h, int col) {
        c.fill(x, y, x + w, y + 1, col);
        c.fill(x, y + h - 1, x + w, y + h, col);
        c.fill(x, y, x + 1, y + h, col);
        c.fill(x + w - 1, y, x + w, y + h, col);
    }

    public static void drawBox(DrawContext ctx, MinecraftClient mc, Config.Settings s, Box b, int idx, int n, boolean preview) {
        var tr = mc.textRenderer;
        Config.Mod m = s.mod(b.name);
        int bg = (((int) (s.bgAlpha * 2.55f)) << 24) | s.bgColor;
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) b.x, (float) b.y);
        ctx.getMatrices().scale(b.sc, b.sc);
        boolean sh = flag(m.shadowMode, s.hudShadow);
        if (b.name.equals("Keys")) {
            var o = mc.options;
            key(ctx, s, m, "W", o.forwardKey.isPressed(), 20, 0, 18, 18);
            key(ctx, s, m, "A", o.leftKey.isPressed(), 0, 20, 18, 18);
            key(ctx, s, m, "S", o.backKey.isPressed(), 20, 20, 18, 18);
            key(ctx, s, m, "D", o.rightKey.isPressed(), 40, 20, 18, 18);
            key(ctx, s, m, "LMB", o.attackKey.isPressed(), 0, 40, 29, 18);
            key(ctx, s, m, "RMB", o.useKey.isPressed(), 31, 40, 29, 18);
            key(ctx, s, m, "SPACE", o.jumpKey.isPressed(), 0, 60, 60, 12);
        } else if (b.name.equals("Lyrics")) {
            String[] ly = preview && !Lyrics.running ? SAMPLE_LY : Lyrics.current(s);
            if (flag(m.bgMode, s.hudBg)) ctx.fill(0, 0, 240, 30, bg);
            String cur = tr.trimToWidth(ly[0], 228), nxt = tr.trimToWidth(ly[1], 228);
            ctx.drawText(tr, cur, (240 - tr.getWidth(cur)) / 2, 4, resolveM(s, m, 0, 1), sh);
            ctx.drawText(tr, nxt, (240 - tr.getWidth(nxt)) / 2, 17, 0xFF9AA0B8, false);
        } else if (b.name.equals("Graph")) {
            if (flag(m.bgMode, s.hudBg)) ctx.fill(0, 0, 64, 28, bg);
            float top = 16.7f;
            for (float f : FT) top = Math.max(top, f);
            for (int i = 0; i < 60; i++) {
                int hh = Math.min(24, (int) (FT[(fi + i) % 60] / top * 24));
                if (hh > 0) ctx.fill(2 + i, 26 - hh, 3 + i, 26, resolveM(s, m, i, 60));
            }
            ctx.fill(2, 26, 62, 27, 0x40FFFFFF);
        } else if (b.name.equals("Armor")) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            for (int i = 0; i < 4; i++) {
                ItemStack st = mc.player == null ? ItemStack.EMPTY : mc.player.getEquippedStack(slots[i]);
                int yy = i * 18;
                if (st.isEmpty()) {
                    if (preview) ctx.fill(1, yy + 1, 15, yy + 15, 0x40FFFFFF);
                    continue;
                }
                ctx.drawItem(st, 0, yy);
                if (st.isDamageable())
                    ctx.drawText(tr, "" + (st.getMaxDamage() - st.getDamage()), 19, yy + 5, resolveM(s, m, i, 4), sh);
            }
        } else {
            int w0 = tr.getWidth(b.text) + 10;
            if (flag(m.bgMode, s.hudBg)) ctx.fill(0, 0, w0, 14, bg);
            if (flag(m.barMode, s.hudBar)) ctx.fill(0, 0, 2, 14, resolve(s, -1, 0, 1));
            if (flag(m.outlineMode, s.hudOutline)) outline(ctx, 0, 0, w0, 14, resolve(s, -1, 0, 1));
            ctx.drawText(tr, b.text, 6, 3, resolveM(s, m, idx, n), sh);
        }
        ctx.getMatrices().popMatrix();
    }

    static void key(DrawContext ctx, Config.Settings s, Config.Mod m, String label, boolean down, int x, int y, int w, int h) {
        var tr = MinecraftClient.getInstance().textRenderer;
        int col = resolveM(s, m, 0, 1) & 0xFFFFFF;
        ctx.fill(x, y, x + w, y + h, down ? (0xD0000000 | col) : 0x80000000);
        ctx.drawText(tr, label, x + (w - tr.getWidth(label)) / 2, y + (h - 8) / 2 + 1, down ? 0xFF000000 : 0xFFFFFFFF, flag(m.shadowMode, s.hudShadow) && !down);
    }

    /* ---------------- HUD ---------------- */

    static void effects(DrawContext ctx, Config.Settings s, int sw, int sh) {
        if (s.tint) {
            int col = resolve(s, s.tintColor, 0, 1) & 0xFFFFFF;
            ctx.fill(0, 0, sw, sh, (((int) (s.tintAlpha * 2.55f)) << 24) | col);
        }
        if (s.glow) {
            int g = Math.max(2, (int) (sh * s.glowSize / 100f));
            int c1 = resolve(s, -1, 0, 2) & 0xFFFFFF, c2 = resolve(s, -1, 1, 2) & 0xFFFFFF;
            ctx.fillGradient(0, 0, sw, g, 0x88000000 | c1, c1);
            ctx.fillGradient(0, sh - g, sw, sh, c2, 0x88000000 | c2);
        }
        ambience(ctx, s, sw, sh);
        vignette(ctx, s, sw, sh);
    }

    /* ---------------- ambience particles + vignette ---------------- */

    static final float[] AX = new float[200], AY = new float[200], AP = new float[200];
    static int aStyle = -1, aCount = -1, aW, aH;
    static long aLast;

    static void ambience(DrawContext ctx, Config.Settings s, int sw, int sh) {
        if (s.ambStyle == 0) return;
        int n = Math.max(1, Math.min(200, s.ambCount));
        if (aStyle != s.ambStyle || aCount != n || aW != sw || aH != sh) {
            for (int i = 0; i < 200; i++) {
                AX[i] = (float) Math.random() * sw;
                AY[i] = (float) Math.random() * sh;
                AP[i] = (float) (Math.random() * 6.283);
            }
            aStyle = s.ambStyle; aCount = n; aW = sw; aH = sh;
        }
        long now = System.nanoTime();
        float dt = aLast == 0 ? 0.016f : Math.min(0.05f, (now - aLast) / 1e9f);
        aLast = now;
        float sp = s.ambSpeed / 100f, t = (now / 1_000_000L % 1_000_000L) / 1000f;
        for (int i = 0; i < n; i++) {
            int col = resolve(s, s.ambColor, i, n) & 0xFFFFFF;
            float vary = 0.6f + 0.8f * (i % 5) / 5f;
            switch (s.ambStyle) {
                case 1 -> {                                           // snow
                    AY[i] += 28 * sp * vary * dt;
                    AX[i] += (float) Math.sin(t * 0.8 + AP[i]) * 10 * dt;
                    if (AY[i] > sh) { AY[i] = -3; AX[i] = (float) Math.random() * sw; }
                    int sz = i % 3 == 0 ? 2 : 1;
                    ctx.fill((int) AX[i], (int) AY[i], (int) AX[i] + sz, (int) AY[i] + sz, 0xC0000000 | col);
                }
                case 2 -> {                                           // embers
                    AY[i] -= 36 * sp * vary * dt;
                    AX[i] += (float) Math.sin(t * 1.3 + AP[i]) * 14 * dt;
                    if (AY[i] < -3) { AY[i] = sh + 3; AX[i] = (float) Math.random() * sw; }
                    int a = (int) (0x60 + 0x90 * (0.5 + 0.5 * Math.sin(t * 6 + AP[i])));
                    ctx.fill((int) AX[i], (int) AY[i], (int) AX[i] + 2, (int) AY[i] + 2, (a << 24) | col);
                }
                case 3 -> {                                           // twinkling stars
                    int a = (int) (0x30 + 0x90 * (0.5 + 0.5 * Math.sin(t * 2 + AP[i] * 3)));
                    ctx.fill((int) AX[i], (int) AY[i], (int) AX[i] + 1, (int) AY[i] + 1, (a << 24) | col);
                }
                case 4 -> {                                           // rain
                    AY[i] += 360 * sp * vary * dt;
                    AX[i] -= 50 * sp * dt;
                    if (AY[i] > sh) { AY[i] = -8; AX[i] = (float) Math.random() * (sw + 60); }
                    ctx.fill((int) AX[i], (int) AY[i], (int) AX[i] + 1, (int) AY[i] + 6, 0x90000000 | col);
                }
                default -> {                                          // fireflies
                    AX[i] += (float) Math.cos(t * 0.7 + AP[i] * 2) * 18 * sp * dt;
                    AY[i] += (float) Math.sin(t * 0.5 + AP[i]) * 18 * sp * dt;
                    if (AX[i] < 0) AX[i] += sw;
                    if (AX[i] > sw) AX[i] -= sw;
                    if (AY[i] < 0) AY[i] += sh;
                    if (AY[i] > sh) AY[i] -= sh;
                    int a = (int) (0x40 + 0xB0 * (0.5 + 0.5 * Math.sin(t * 2.2 + AP[i] * 5)));
                    int x = (int) AX[i], y = (int) AY[i];
                    ctx.fill(x - 1, y - 1, x + 3, y + 3, ((a / 4) << 24) | col);
                    ctx.fill(x, y, x + 2, y + 2, (a << 24) | col);
                }
            }
        }
    }

    static void vignette(DrawContext ctx, Config.Settings s, int sw, int sh) {
        if (!s.vignette) return;
        int a0 = (int) (s.vigStrength * 2.55f);
        int vh = (int) (sh * 0.22f);
        ctx.fillGradient(0, 0, sw, vh, a0 << 24, 0);
        ctx.fillGradient(0, sh - vh, sw, sh, 0, a0 << 24);
        int vw = (int) (sw * 0.16f);
        for (int i = 0; i < 16; i++) {
            float k = 1 - i / 16f;
            int a = (int) (a0 * k * k);
            int x0 = i * vw / 16, x1 = (i + 1) * vw / 16;
            ctx.fill(x0, 0, x1, sh, a << 24);
            ctx.fill(sw - x1, 0, sw - x0, sh, a << 24);
        }
    }

    /** {average ms, worst ms, average of the worst 5% ms} over the last 60 frames. */
    public static float[] frameStats() {
        float[] tmp = new float[60];
        int c = 0;
        float sum = 0;
        for (float f : FT) if (f > 0) { tmp[c++] = f; sum += f; }
        if (c == 0) return new float[]{0, 0, 0};
        java.util.Arrays.sort(tmp, 0, c);
        int k = Math.max(1, c / 20);
        float low = 0;
        for (int i = c - k; i < c; i++) low += tmp[i];
        return new float[]{sum / c, tmp[c - 1], low / k};
    }

    static void hud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Config.Settings s = Config.cur();
        if (mc.options.hudHidden || mc.player == null) return;
        if (mc.currentScreen instanceof HudEditor) return;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        effects(ctx, s, sw, sh);
        if (s.mod("Frametime").on || s.mod("Graph").on || perfOpen) {   // only measured while needed
            long now = System.nanoTime();
            if (lastNs > 0 && now - lastNs < 1_000_000_000L) { FT[fi] = (now - lastNs) / 1_000_000f; fi = (fi + 1) % 60; }
            lastNs = now;
        } else {
            lastNs = 0;
        }
        if (!s.hud) return;
        if (dirty || CACHE == null || sw != cacheW || sh != cacheH || mc.currentScreen instanceof NovaScreen) {
            CACHE = boxes(mc, s, sw, sh);
            cacheW = sw; cacheH = sh; dirty = false;
        }
        List<Box> bs = CACHE;
        for (int i = 0; i < bs.size(); i++) drawBox(ctx, mc, s, bs.get(i), i, bs.size(), false);
    }
}
