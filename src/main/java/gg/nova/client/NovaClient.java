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

    /** Module names, in menu order. */
    public static final String[] MODS = {"FPS", "Coords", "CPS", "Ping", "Direction", "Speed", "Memory", "Clock", "Armor", "Keys", "Sprint", "Zoom"};

    /** Color choices for modules: -1 = theme gradient, -2 = rainbow. */
    static final String[] PN = {"Theme", "Rainbow", "White", "Red", "Orange", "Yellow", "Lime", "Green", "Cyan", "Blue", "Purple", "Pink", "Gray"};
    static final int[] PC = {-1, -2, 0xFFFFFF, 0xFF4D4D, 0xFF9F43, 0xFFE066, 0xA6E22E, 0x3DDC97, 0x00E5FF, 0x2F8CFF, 0x7C5CFF, 0xFF4D8D, 0xAAAAAA};

    static int idxOf(int code) { for (int i = 0; i < PC.length; i++) if (PC[i] == code) return i; return 0; }
    public static String colorName(int code) { return PN[idxOf(code)]; }
    public static int nextColor(int code) { return PC[(idxOf(code) + 1) % PC.length]; }

    static final ArrayDeque<Long> clicksL = new ArrayDeque<>(), clicksR = new ArrayDeque<>();
    static boolean wasL, wasR;
    static double speed, lx, lz;
    static int ping, savedFov = -1;

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
        if (code == -2) return 0xFF000000 | hsv(((System.currentTimeMillis() / 1000.0) * s.chromaSpeed / 100.0 + i * 0.08) % 1.0);
        if (code == -1) return 0xFF000000 | lerp(s.accent, s.accent2, n <= 1 ? 0f : i / (float) (n - 1));
        return 0xFF000000 | code;
    }

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

    /* ---------------- HUD ---------------- */

    static void put(List<String> lines, List<Integer> codes, Config.Settings s, String mod, String text) {
        Config.Mod m = s.mod(mod);
        if (m.on) { lines.add(text); codes.add(m.color); }
    }

    static void hud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Config.Settings s = Config.cur();
        if (!s.hud || mc.options.hudHidden || mc.player == null) return;
        var tr = mc.textRenderer;
        int vw0 = ctx.getScaledWindowWidth(), vh0 = ctx.getScaledWindowHeight();

        // ---- text modules, stacked in the chosen corner ----
        List<String> lines = new ArrayList<>();
        List<Integer> codes = new ArrayList<>();
        put(lines, codes, s, "FPS", "FPS " + mc.getCurrentFps());
        put(lines, codes, s, "Coords", "XYZ " + (int) Math.floor(mc.player.getX()) + " " + (int) Math.floor(mc.player.getY()) + " " + (int) Math.floor(mc.player.getZ()));
        put(lines, codes, s, "CPS", "CPS " + clicksL.size() + " | " + clicksR.size());
        put(lines, codes, s, "Ping", "Ping " + ping + " ms");
        put(lines, codes, s, "Direction", "Facing " + mc.player.getHorizontalFacing().name());
        put(lines, codes, s, "Speed", String.format(Locale.ROOT, "%.1f b/s", speed));
        Runtime rt = Runtime.getRuntime();
        put(lines, codes, s, "Memory", "RAM " + ((rt.totalMemory() - rt.freeMemory()) >> 20) + "/" + (rt.maxMemory() >> 20) + " MB");
        put(lines, codes, s, "Clock", LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm")));

        if (!lines.isEmpty()) {
            int w = 0;
            for (String l : lines) w = Math.max(w, tr.getWidth(l));
            w += 10;
            int h = lines.size() * 10 + 4;
            float sc = s.hudScale / 100f;
            int vw = (int) (vw0 / sc), vh = (int) (vh0 / sc);
            int x = (s.corner % 2 == 0) ? 4 : vw - w - 4;
            int y = (s.corner < 2) ? 4 : vh - h - 4;
            int bg = (((int) (s.bgAlpha * 2.55f)) << 24) | s.bgColor;
            ctx.getMatrices().pushMatrix();
            ctx.getMatrices().scale(sc, sc);
            if (s.hudBg) ctx.fill(x, y, x + w, y + h, bg);
            ctx.fill(x, y, x + 2, y + h, resolve(s, -1, 0, 1));
            for (int i = 0; i < lines.size(); i++)
                ctx.drawTextWithShadow(tr, lines.get(i), x + 6, y + 3 + i * 10, resolve(s, codes.get(i), i, lines.size()));
            ctx.getMatrices().popMatrix();
        }

        // ---- keystrokes (left side) ----
        Config.Mod km = s.mod("Keys");
        if (km.on) {
            var o = mc.options;
            int x = 6, y = vh0 / 2 - 31;
            key(ctx, s, km, "W", o.forwardKey.isPressed(), x + 20, y, 18, 18);
            key(ctx, s, km, "A", o.leftKey.isPressed(), x, y + 20, 18, 18);
            key(ctx, s, km, "S", o.backKey.isPressed(), x + 20, y + 20, 18, 18);
            key(ctx, s, km, "D", o.rightKey.isPressed(), x + 40, y + 20, 18, 18);
            key(ctx, s, km, "LMB", o.attackKey.isPressed(), x, y + 40, 29, 18);
            key(ctx, s, km, "RMB", o.useKey.isPressed(), x + 31, y + 40, 29, 18);
            key(ctx, s, km, "SPACE", o.jumpKey.isPressed(), x, y + 60, 60, 12);
        }

        // ---- armor (right side) ----
        Config.Mod am = s.mod("Armor");
        if (am.on) {
            EquipmentSlot[] slots = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};
            int ax = vw0 - 56, ay = vh0 / 2 - 36;
            for (int i = 0; i < 4; i++) {
                ItemStack st = mc.player.getEquippedStack(slots[i]);
                if (st.isEmpty()) continue;
                int yy = ay + i * 18;
                ctx.drawItem(st, ax, yy);
                if (st.isDamageable())
                    ctx.drawTextWithShadow(tr, "" + (st.getMaxDamage() - st.getDamage()), ax + 19, yy + 5, resolve(s, am.color, i, 4));
            }
        }
    }

    static void key(DrawContext ctx, Config.Settings s, Config.Mod m, String label, boolean down, int x, int y, int w, int h) {
        var tr = MinecraftClient.getInstance().textRenderer;
        int col = resolve(s, m.color, 0, 1) & 0xFFFFFF;
        ctx.fill(x, y, x + w, y + h, down ? (0xD0000000 | col) : 0x80000000);
        ctx.drawTextWithShadow(tr, label, x + (w - tr.getWidth(label)) / 2, y + (h - 8) / 2 + 1, down ? 0xFF000000 : 0xFFFFFFFF);
    }
}
