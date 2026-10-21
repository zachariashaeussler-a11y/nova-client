package gg.nova.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/** Settings menu: sidebar tabs + a scrollable settings list (mouse wheel). */
public class NovaScreen extends Screen {
    static final String[] TABS = {"Visuals", "Modules", "Themes", "Render", "Profiles", "Custom"};
    static final String[] CORNERS = {"Top left", "Top right", "Bottom left", "Bottom right"};
    static final String[] BOOST = {"Off", "Balanced", "Max"};
    static final String[] MODES = {"Inherit", "On", "Off"};
    static final Object[][] THEMES = {
            {"Neon", 0x7C5CFF, 0x00E5FF}, {"Ember", 0xFF6A3D, 0xFFC857}, {"Forest", 0x3DDC97, 0xB6F09C}, {"Ocean", 0x2F8CFF, 0x5EF2D6},
            {"Rose", 0xFF4D8D, 0xFFB3D1}, {"Mono", 0xFFFFFF, 0x8A8A8A}, {"Sunset", 0xFF5F6D, 0xFFC371}, {"Aurora", 0x00F5A0, 0x00D9F5},
            {"Grape", 0xB721FF, 0x21D4FD}, {"Mint", 0x5EFCE8, 0x736EFE}, {"Gold", 0xF9D423, 0xFF4E50}, {"Ice", 0x74EBD5, 0xACB6E5},
            {"Crimson", 0xE53935, 0xFF8A80}, {"Midnight", 0x6A11CB, 0x2575FC}, {"Lava", 0xFF512F, 0xF09819}, {"Candy", 0xFF9A9E, 0xFAD0C4},
            {"Cyber", 0xFF00C8, 0x00F0FF}, {"Toxic", 0x39FF14, 0xCCFF00}, {"Royal", 0x4776E6, 0x8E54E9}, {"Steel", 0x7F8C8D, 0xBDC3C7}};

    static int tab = 0, scroll = 0;
    static String page = null;   // module settings page (null = list)

    final Screen parent;
    int W, H, px, py, cx, cw, top, bottom, cy, maxScroll;
    boolean reinit;
    TextFieldWidget field;
    final List<Object[]> labels = new ArrayList<>();  // {x, y, text, color(-1 = accent)}
    final List<int[]> sw = new ArrayList<>();         // {x, y, color1, color2}

    public NovaScreen(Screen parent) { super(Text.literal("NOVA Client")); this.parent = parent; }

    /* ---------- layout ---------- */

    @Override
    protected void init() {
        Config.Settings s = Config.cur();
        W = Math.min(width - 16, 460);
        H = Math.min(height - 16, 280);
        px = (width - W) / 2;
        py = (height - H) / 2;
        cx = px + 104;
        cw = W - 104 - 14;
        top = py + 28;
        bottom = py + H - 32;
        cy = top - scroll;
        labels.clear();
        sw.clear();

        for (int i = 0; i < TABS.length; i++) {
            int n = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(TABS[i]), b -> { tab = n; scroll = 0; page = null; clearAndInit(); })
                    .dimensions(px + 8, py + 30 + i * 24, 88, 20).build());
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(px + W - 88, py + H - 26, 80, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Edit HUD layout"), b -> client.setScreen(new HudEditor(this)))
                .dimensions(px + 104, py + H - 26, 120, 20).build());

        switch (tab) {
            case 0 -> visuals(s);
            case 1 -> modules(s);
            case 2 -> themes(s);
            case 3 -> renderTab(s);
            case 4 -> profiles(s);
            default -> custom(s);
        }

        int total = (cy + scroll) - top;
        maxScroll = Math.max(0, (int) Math.ceil((total - (bottom - top)) / 24.0) * 24);
        if (scroll > maxScroll && !reinit) {
            scroll = maxScroll;
            reinit = true;
            clearAndInit();
            reinit = false;
        }
    }

    <T extends ClickableWidget> T add(T w) {
        if (w.getY() < top || w.getY() + w.getHeight() > bottom) { w.visible = false; w.active = false; }
        return addDrawableChild(w);
    }

    int row() { int y = cy; cy += 24; return y; }
    void header(String t) { labels.add(new Object[]{cx, cy + 9, t, -1}); cy += 24; }
    void note(String t) { labels.add(new Object[]{cx, cy + 6, t, 0xFFAAAAAA}); cy += 24; }

    ButtonWidget btn(String t, int x, int y, int w, Consumer<ButtonWidget> a) {
        return add(ButtonWidget.builder(Text.literal(t), b -> a.accept(b)).dimensions(x, y, w, 20).build());
    }

    static String onOff(String l, boolean on) { return l + ": " + (on ? "ON" : "OFF"); }

    void toggle(String label, int x, int y, int w, BooleanSupplier g, Consumer<Boolean> st) {
        btn(onOff(label, g.getAsBoolean()), x, y, w, b -> {
            st.accept(!g.getAsBoolean());
            b.setMessage(Text.literal(onOff(label, g.getAsBoolean())));
        });
    }

    void trow(String label, BooleanSupplier g, Consumer<Boolean> st) { toggle(label, cx, row(), cw, g, st); }

    Slider slider(String label, int x, int y, int w, double min, double max, double cur, Consumer<Double> set, IntFunction<String> fmt) {
        return add(new Slider(x, y, w, label, min, max, cur, set, fmt));
    }

    void srow(String label, double min, double max, double cur, Consumer<Double> set) {
        slider(label, cx, row(), cw, min, max, cur, set, null);
    }

    void cycle(String label, int x, int y, int w, IntSupplier get, IntConsumer set, int count, IntFunction<String> name) {
        btn(label + ": " + name.apply(get.getAsInt()), x, y, w, b -> {
            set.accept((get.getAsInt() + 1) % count);
            b.setMessage(Text.literal(label + ": " + name.apply(get.getAsInt())));
        });
    }

    void rgbRows(String name, IntSupplier get, IntConsumer set) {
        srow(name + " red", 0, 255, (get.getAsInt() >> 16) & 255, v -> set.accept((get.getAsInt() & 0x00FFFF) | (v.intValue() << 16)));
        srow(name + " green", 0, 255, (get.getAsInt() >> 8) & 255, v -> set.accept((get.getAsInt() & 0xFF00FF) | (v.intValue() << 8)));
        srow(name + " blue", 0, 255, get.getAsInt() & 255, v -> set.accept((get.getAsInt() & 0xFFFF00) | v.intValue()));
    }

    /* ---------- tabs ---------- */

    void visuals(Config.Settings s) {
        header("HUD");
        trow("HUD overlay", () -> s.hud, v -> s.hud = v);
        trow("HUD background", () -> s.hudBg, v -> s.hudBg = v);
        srow("HUD scale %", 50, 200, s.hudScale, v -> s.hudScale = v.intValue());
        btn("Default corner: " + CORNERS[s.corner], cx, row(), cw, b -> {
            s.corner = (s.corner + 1) % 4;
            b.setMessage(Text.literal("Default corner: " + CORNERS[s.corner]));
        });
        btn("Edit HUD layout (drag modules)", cx, row(), cw, b -> client.setScreen(new HudEditor(this)));
        header("Style (for every module)");
        trow("Text shadow", () -> s.hudShadow, v -> s.hudShadow = v);
        trow("Accent bar", () -> s.hudBar, v -> s.hudBar = v);
        trow("Outline", () -> s.hudOutline, v -> s.hudOutline = v);
        srow("Background opacity %", 0, 100, s.bgAlpha, v -> s.bgAlpha = v.intValue());
        srow("Rainbow / pulse speed", 1, 100, s.chromaSpeed, v -> s.chromaSpeed = v.intValue());
        note("Each module can override these in Modules > Settings.");
        header("Screen effects");
        trow("Edge glow (top and bottom)", () -> s.glow, v -> s.glow = v);
        srow("Glow size %", 4, 40, s.glowSize, v -> s.glowSize = v.intValue());
        trow("Screen tint", () -> s.tint, v -> s.tint = v);
        srow("Tint strength %", 1, 60, s.tintAlpha, v -> s.tintAlpha = v.intValue());
        btn("Tint color: " + NovaClient.colorName(s.tintColor), cx, row(), cw, b -> {
            s.tintColor = hasShiftDown() ? NovaClient.prevColor(s.tintColor) : NovaClient.nextColor(s.tintColor);
            b.setMessage(Text.literal("Tint color: " + NovaClient.colorName(s.tintColor)));
        });
    }

    void modules(Config.Settings s) {
        if (page != null) { modulePage(s, page); return; }
        header("Modules");
        for (String n : NovaClient.MODS) {
            Config.Mod m = s.mod(n);
            int y = row();
            boolean hud = Arrays.asList(NovaClient.HUDMODS).contains(n);
            if (!hud && n.equals("Sprint")) { toggle(n, cx, y, cw, () -> m.on, v -> m.on = v); continue; }
            toggle(n, cx, y, 112, () -> m.on, v -> m.on = v);
            if (n.equals("Zoom")) {
                slider("Zoom FOV (hold Z)", cx + 116, y, cw - 116, 30, 70, s.zoomFov, v -> s.zoomFov = v.intValue(), null);
                continue;
            }
            btn(NovaClient.colorName(m.color), cx + 116, y, 72, b -> {
                m.color = hasShiftDown() ? NovaClient.prevColor(m.color) : NovaClient.nextColor(m.color);
                b.setMessage(Text.literal(NovaClient.colorName(m.color)));
            });
            btn("Settings >", cx + 192, y, cw - 192, b -> { page = n; scroll = 0; clearAndInit(); });
        }
        note("Click a color to cycle it (hold Shift to go back).");
    }

    void modulePage(Config.Settings s, String n) {
        Config.Mod m = s.mod(n);
        boolean text = !(n.equals("Keys") || n.equals("Armor"));
        btn("< Back to modules", cx, row(), cw, b -> { page = null; scroll = 0; clearAndInit(); });
        header(n + " settings");
        trow("Enabled", () -> m.on, v -> m.on = v);
        btn("Color: " + NovaClient.colorName(m.color), cx, row(), cw, b -> {
            m.color = hasShiftDown() ? NovaClient.prevColor(m.color) : NovaClient.nextColor(m.color);
            b.setMessage(Text.literal("Color: " + NovaClient.colorName(m.color)));
        });
        header("Own color (pick \"Own\" above)");
        rgbRows("Own", () -> m.rgb, v -> m.rgb = v);
        header("Size and position");
        srow("Scale %", 40, 250, m.scale, v -> m.scale = v.intValue());
        var boxes = NovaClient.boxes(client, s, width, height);
        NovaClient.Box box = null;
        for (var b : boxes) if (b.name.equals(n)) box = b;
        float curFx = m.fx >= 0 ? m.fx : (box != null ? box.x / (float) width : 0f);
        float curFy = m.fy >= 0 ? m.fy : (box != null ? box.y / (float) height : 0f);
        srow("Position X %", 0, 100, Math.round(curFx * 100), v -> { if (m.fy < 0) m.fy = curFy; m.fx = v.floatValue() / 100f; });
        srow("Position Y %", 0, 100, Math.round(curFy * 100), v -> { if (m.fx < 0) m.fx = curFx; m.fy = v.floatValue() / 100f; });
        btn("Auto position (reset)", cx, row(), cw, b -> { m.fx = -1f; m.fy = -1f; clearAndInit(); });
        header("Look");
        if (text) {
            int y = row();
            field = add(new TextFieldWidget(textRenderer, cx, y, cw, 20, Text.literal("Label")));
            field.setMaxLength(24);
            field.setText(m.label == null ? "" : m.label);
            String def = NovaClient.defaultLabel(n);
            field.setSuggestion(field.getText().isEmpty() ? (def.isEmpty() ? "Label (none)" : "Label: " + def) : "");
            field.setChangedListener(t -> { m.label = t; field.setSuggestion(t.isEmpty() ? (def.isEmpty() ? "Label (none)" : "Label: " + def) : ""); });
            cycle("Background", cx, row(), cw, () -> m.bgMode, v -> m.bgMode = v, 3, i -> MODES[i]);
            cycle("Accent bar", cx, row(), cw, () -> m.barMode, v -> m.barMode = v, 3, i -> MODES[i]);
            cycle("Outline", cx, row(), cw, () -> m.outlineMode, v -> m.outlineMode = v, 3, i -> MODES[i]);
        }
        cycle("Text shadow", cx, row(), cw, () -> m.shadowMode, v -> m.shadowMode = v, 3, i -> MODES[i]);
        note("Inherit = use the global style from Visuals.");
    }

    void themes(Config.Settings s) {
        header("Presets");
        int bw = (cw - 12) / 4, gy = 0;
        for (int i = 0; i < THEMES.length; i++) {
            Object[] t = THEMES[i];
            if (i % 4 == 0) gy = row();
            int bx = cx + (i % 4) * (bw + 4);
            btn("" + t[0], bx, gy, bw, b -> {
                s.theme = (String) t[0]; s.accent = (int) t[1]; s.accent2 = (int) t[2]; clearAndInit();
            });
            sw.add(new int[]{bx + 4, gy + 4, (int) t[1], (int) t[2]});
        }
        note("Current theme: " + s.theme);
        header("Accent");
        rgbRows("Accent", () -> s.accent, v -> { s.accent = v; s.theme = "Custom"; });
        header("Accent 2");
        rgbRows("Accent 2", () -> s.accent2, v -> { s.accent2 = v; s.theme = "Custom"; });
        header("HUD background");
        rgbRows("Background", () -> s.bgColor, v -> s.bgColor = v);
        srow("Opacity %", 0, 100, s.bgAlpha, v -> s.bgAlpha = v.intValue());
        header("Custom text color (module color \"Custom\")");
        rgbRows("Text", () -> s.textColor, v -> s.textColor = v);
        srow("Rainbow / pulse speed", 1, 100, s.chromaSpeed, v -> s.chromaSpeed = v.intValue());
    }

    void renderTab(Config.Settings s) {
        header("FPS boost");
        btn("FPS boost: " + s.boost, cx, row(), cw, b -> {
            s.boost = BOOST[(Arrays.asList(BOOST).indexOf(s.boost) + 1) % 3];
            Boost.apply(s.boost);
            Config.save();
            b.setMessage(Text.literal("FPS boost: " + s.boost));
        });
        trow("Entity culling", () -> s.entityCull, v -> s.entityCull = v);
        srow("Cull distance (blocks)", 16, 128, s.cullDist, v -> s.cullDist = v.intValue());
        note("Boost edits your video settings; Off restores them.");
        header("Render modules");
        for (String n : RenderMods.NAMES) {
            Config.Mod m = s.mod(n);
            int y = row();
            if (!RenderMods.hasValue(n)) { toggle(n, cx, y, cw, () -> m.on, v -> m.on = v); continue; }
            toggle(n, cx, y, 126, () -> m.on, v -> m.on = v);
            int x2 = cx + 130, w2 = cw - 130;
            if (RenderMods.isChoice(n)) {
                btn(RenderMods.describe(n, RenderMods.value(s, n)), x2, y, w2, b -> {
                    m.val = RenderMods.value(s, n) == 0 ? 1 : 0;
                    b.setMessage(Text.literal(RenderMods.describe(n, RenderMods.value(s, n))));
                });
            } else {
                slider(n, x2, y, w2, RenderMods.min(n), RenderMods.max(n), RenderMods.value(s, n),
                        v -> m.val = v.intValue(), i -> RenderMods.describe(n, i));
            }
        }
        note("Each module changes one video option; Off restores yours.");
    }

    void profiles(Config.Settings s) {
        header("New profile");
        int y = row();
        field = add(new TextFieldWidget(textRenderer, cx, y, cw - 110, 20, Text.literal("Name")));
        field.setSuggestion("Profile name");
        field.setChangedListener(t -> field.setSuggestion(t.isEmpty() ? "Profile name" : ""));
        btn("Save new", cx + cw - 106, y, 106, b -> {
            String n = field.getText().trim();
            if (n.isEmpty() || Config.I.profiles.containsKey(n)) return;
            Config.Settings copy = Config.G.fromJson(Config.G.toJson(s), Config.Settings.class);
            Config.I.profiles.put(n, copy);
            Config.I.active = n;
            Config.save();
            clearAndInit();
        });
        header("Your profiles");
        for (String n : new ArrayList<>(Config.I.profiles.keySet())) {
            int yy = row();
            btn((n.equals(Config.I.active) ? "> " : "") + n, cx, yy, cw - 34, b -> {
                Config.I.active = n;
                Boost.apply(Config.cur().boost);
                Config.save();
                clearAndInit();
            });
            ButtonWidget del = btn("X", cx + cw - 30, yy, 30, b -> {
                Config.I.profiles.remove(n);
                if (n.equals(Config.I.active)) Config.I.active = Config.I.profiles.keySet().iterator().next();
                Config.save();
                clearAndInit();
            });
            if (Config.I.profiles.size() <= 1) del.active = false;
        }
    }

    void custom(Config.Settings s) {
        header("Add a setting");
        int y = row();
        field = add(new TextFieldWidget(textRenderer, cx, y, cw - 170, 20, Text.literal("Name")));
        field.setSuggestion("Setting name");
        field.setChangedListener(t -> field.setSuggestion(t.isEmpty() ? "Setting name" : ""));
        addCustom("Toggle", "toggle", cx + cw - 166, y, s);
        addCustom("Slider", "slider", cx + cw - 82, y, s);
        header("Your settings");
        if (s.custom.isEmpty()) note("Nothing here yet.");
        for (int i = 0; i < s.custom.size(); i++) {
            int n = i, yy = row();
            Config.Custom c = s.custom.get(i);
            if (c.type.equals("toggle")) {
                btn(c.name + ": " + (c.value > 0 ? "ON" : "OFF"), cx, yy, cw - 34, b -> {
                    c.value = c.value > 0 ? 0 : 1;
                    b.setMessage(Text.literal(c.name + ": " + (c.value > 0 ? "ON" : "OFF")));
                });
            } else {
                slider(c.name, cx, yy, cw - 34, 0, 100, c.value, v -> c.value = v, null);
            }
            btn("X", cx + cw - 30, yy, 30, b -> { s.custom.remove(n); clearAndInit(); });
        }
    }

    void addCustom(String label, String type, int x, int y, Config.Settings s) {
        btn(label, x, y, 80, b -> {
            String n = field.getText().trim();
            if (n.isEmpty()) return;
            Config.Custom c = new Config.Custom();
            c.name = n; c.type = type; c.value = type.equals("slider") ? 50 : 0;
            s.custom.add(c);
            clearAndInit();
        });
    }

    /* ---------- helpers ---------- */

    static boolean hasShiftDown() {
        long w = org.lwjgl.glfw.GLFW.glfwGetCurrentContext();
        return org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_LEFT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS
                || org.lwjgl.glfw.GLFW.glfwGetKey(w, org.lwjgl.glfw.GLFW.GLFW_KEY_RIGHT_SHIFT) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
    }

    static class Slider extends SliderWidget {
        final String label; final double min, max; final Consumer<Double> set; final IntFunction<String> fmt;
        Slider(int x, int y, int w, String label, double min, double max, double cur, Consumer<Double> set, IntFunction<String> fmt) {
            super(x, y, w, 20, Text.empty(), Math.max(0, Math.min(1, (cur - min) / (max - min))));
            this.label = label; this.min = min; this.max = max; this.set = set; this.fmt = fmt;
            updateMessage();
        }
        double val() { return min + (max - min) * value; }
        @Override protected void updateMessage() {
            int r = (int) Math.round(val());
            setMessage(Text.literal(fmt != null ? fmt.apply(r) : label + ": " + r));
        }
        @Override protected void applyValue() {
            double r = Math.round(val());
            value = (r - min) / (max - min);
            set.accept(r);
        }
    }

    /* ---------- scrolling and drawing ---------- */

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        if (maxScroll > 0) {
            int ns = Math.max(0, Math.min(maxScroll, scroll + (verticalAmount < 0 ? 24 : -24)));
            if (ns != scroll) { scroll = ns; clearAndInit(); }
        }
        return true;
    }

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float d) {
        super.renderBackground(c, mx, my, d);
        Config.Settings s = Config.cur();
        c.fill(px, py, px + W, py + H, 0xEE0B0D14);
        c.fill(px, py + 24, px + 100, py + H, 0xFF0F1220);
        c.fill(cx - 4, top - 2, cx + cw + 10, bottom + 2, 0x16FFFFFF);
        for (int i = 0; i < W; i += 4)
            c.fill(px + i, py, Math.min(px + W, px + i + 4), py + 2, 0xFF000000 | NovaClient.lerp(s.accent, s.accent2, i / (float) W));
        c.fill(px + 4, py + 30 + tab * 24, px + 7, py + 50 + tab * 24, 0xFF000000 | s.accent);
        c.drawTextWithShadow(textRenderer, "NOVA CLIENT", px + 10, py + 9, 0xFF000000 | s.accent);
        String pr = "profile: " + Config.I.active;
        c.drawTextWithShadow(textRenderer, pr, px + W - 8 - textRenderer.getWidth(pr), py + 9, 0xFFAAAAAA);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        super.render(c, mx, my, d);
        Config.Settings s = Config.cur();
        for (int[] a : sw) {
            if (a[1] < top || a[1] + 12 > bottom) continue;
            c.fill(a[0], a[1], a[0] + 6, a[1] + 6, 0xFF000000 | a[2]);
            c.fill(a[0], a[1] + 6, a[0] + 6, a[1] + 12, 0xFF000000 | a[3]);
        }
        for (Object[] l : labels) {
            int y = (int) l[1];
            if (y < top || y + 8 > bottom) continue;
            int col = (int) l[3] == -1 ? (0xFF000000 | s.accent) : (int) l[3];
            c.drawTextWithShadow(textRenderer, (String) l[2], (int) l[0], y, col);
        }
        if (maxScroll > 0) {
            int track = bottom - top;
            int total = track + maxScroll;
            int thumb = Math.max(14, track * track / total);
            int ty = top + (int) ((track - thumb) * (scroll / (float) maxScroll));
            c.fill(cx + cw + 4, top, cx + cw + 7, bottom, 0x30FFFFFF);
            c.fill(cx + cw + 4, ty, cx + cw + 7, ty + thumb, 0xFF000000 | s.accent);
            String hint = "Scroll with the mouse wheel";
            c.drawTextWithShadow(textRenderer, hint, px + W - 8 - textRenderer.getWidth(hint) - 90, py + H - 21, 0xFF777777);
        }
    }

    @Override
    public void close() {
        Config.save();
        RenderMods.sync(Config.cur());
        client.setScreen(parent);
    }
}
