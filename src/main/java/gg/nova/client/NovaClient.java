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
    public static KeyBinding menuKey, zoomKey;

    /** HUD modules (movable boxes). */
    public static final String[] HUDMODS = {"FPS", "Coords", "CPS", "Ping", "Direction", "Speed", "Memory", "Clock", "Watermark", "Armor", "Keys"};
    /** All modules shown in the Modules tab. */
    public static final String[] MODS = {"FPS", "Coords", "CPS", "Ping", "Direction", "Speed", "Memory", "Clock", "Watermark", "Armor", "Keys", "Sprint", "Zoom"};

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
    static final DateTimeFormatter CLOCK = DateTimeFormatter.ofPattern("HH:mm");
    static final ArrayDeque<Long> clicksL = new ArrayDeque<>(), clicksR = new ArrayDeque<>();
    static boolean wasL, wasR;
    static double speed, lx, lz;
    static int ping, tickN;
    public static int savedFov = -1;

    @Override
    @SuppressWarnings("deprecation")
    public void onInitializeClient() {
        Config.load();
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("novaclient", "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.novaclient.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.novaclient.zoom", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_Z, category));
        ClientTickEvents.END_CLIENT_TICK.register(NovaClient::tick);
        HudRenderCallback.EVENT.register((ctx, tick) -> hud(ctx));
    }

    /* ---------------- per-tick logic ---------------- */

    static void tick(MinecraftClient mc) {
        while (menuKey.wasPressed()) mc.setScreen(new NovaScreen(mc.currentScreen));
        Config.Settings s = Config.cur();
        if (mc.options != null && ++tickN % 10 == 0) RenderMods.sync(s);
        if (tickN % 5 == 0) DISP.clear();          // HUD text is rebuilt 4x per second, not every frame
        Adaptive.tick(mc, s);
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
            case "Direction" -> "Facing"; case "Memory" -> "RAM"; case "Watermark" -> "NOVA";
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
    }

    static void hud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Config.Settings s = Config.cur();
        if (mc.options.hudHidden || mc.player == null) return;
        if (mc.currentScreen instanceof HudEditor) return;
        int sw = ctx.getScaledWindowWidth(), sh = ctx.getScaledWindowHeight();
        effects(ctx, s, sw, sh);
        if (!s.hud) return;
        List<Box> bs = boxes(mc, s, sw, sh);
        for (int i = 0; i < bs.size(); i++) drawBox(ctx, mc, s, bs.get(i), i, bs.size(), false);
    }
}
