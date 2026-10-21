package gg.nova.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.DoubleSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;
import java.util.function.IntSupplier;

/**
 * Clean settings menu. Everything is custom drawn (flat cards, switches, slim sliders)
 * and the mouse is read directly, so it does not depend on Minecraft's widget classes.
 * Left-click uses a control, right-click on a color/choice goes backwards, wheel scrolls.
 */
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

    static final int ROW = 26;
    static final int TXT = 0xFFE6E9F5, MUT = 0xFF8A90A8, OFFC = 0x3A3F52, CARD = 0xFF1A1E2E;

    static int tab = 0, scroll = 0;
    static String page = null;

    interface Ctl { void draw(int y, int x1, int x2); }

    final Screen parent;
    DrawContext ctx;
    Config.Settings s;
    int W, H, px, py, cx, cw, top, bottom;
    int cy, idc, maxScroll, dragId = -1, mx, my;
    boolean pressL, pressR, down, wasL = true, wasR = true;
    final float[] anim = new float[1024];
    final boolean[] seen = new boolean[1024];
    Runnable pending;
    TextFieldWidget fProfile, fCustom, fLabel;
    Config.Mod labelMod;
    String labelFor = "", labelDef = "";

    public NovaScreen(Screen parent) { super(Text.literal("NOVA Client")); this.parent = parent; }

    @Override
    protected void init() {
        W = Math.min(width - 16, 480);
        H = Math.min(height - 16, 300);
        px = (width - W) / 2;
        py = (height - H) / 2;
        cx = px + 112;
        cw = W - 112 - 18;
        top = py + 30;
        bottom = py + H - 34;
        fProfile = makeField("Profile name");
        fCustom = makeField("Setting name");
        fLabel = makeField("Label");
        fLabel.setMaxLength(24);
        fLabel.setChangedListener(t -> {
            if (labelMod != null) labelMod.label = t;
            fLabel.setSuggestion(t.isEmpty() ? (labelDef.isEmpty() ? "Label (none)" : "Default: " + labelDef) : "");
        });
        labelFor = "";
    }

    TextFieldWidget makeField(String hint) {
        TextFieldWidget f = new TextFieldWidget(textRenderer, 0, 0, 100, 18, Text.literal(hint));
        f.setSuggestion(hint);
        f.setChangedListener(t -> f.setSuggestion(t.isEmpty() ? hint : ""));
        f.visible = false;
        addDrawableChild(f);
        return f;
    }

    void reset() { Arrays.fill(seen, false); labelFor = ""; }
    void later(Runnable r) { pending = r; }

    /* ---------- tiny drawing helpers ---------- */

    boolean in(int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    int acc() { return 0xFF000000 | s.accent; }
    int tw(String t) { return textRenderer.getWidth(t); }
    void text(String t, int x, int y, int col) { ctx.drawText(textRenderer, t, x, y, col, false); }
    void box(int x, int y, int w, int h, int col) { ctx.fill(x + 1, y, x + w - 1, y + h, col); ctx.fill(x, y + 1, x + w, y + h - 1, col); }
    String fit(String t, int w) { return tw(t) <= w ? t : textRenderer.trimToWidth(t, Math.max(0, w - 8)) + ".."; }

    /* ---------- rows ---------- */

    void header(String t) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        ctx.fill(cx + 2, y + 10, cx + 5, y + 17, acc());
        text(t.toUpperCase(), cx + 10, y + 10, MUT);
        int lx = cx + 16 + tw(t.toUpperCase());
        ctx.fill(lx, y + 14, cx + cw, y + 15, 0x18FFFFFF);
    }

    void note(String t) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        text(fit(t, cw - 12), cx + 8, y + 9, 0xFF666C84);
    }

    /** Draws a slim slider in x1..x2 and handles dragging. */
    void sliderCtl(int y, int x1, int x2, double min, double max, DoubleSupplier get, Consumer<Double> set, IntFunction<String> fmt) {
        int id = 5000 + (y + scroll - top) / ROW;
        double cur = get.getAsDouble();
        int valW = 64, trackW = Math.max(50, Math.min(150, x2 - x1 - valW - 8)), tx = x2 - valW - 6 - trackW;
        if (pressL && in(tx - 6, y + 2, trackW + 12, ROW - 4)) { dragId = id; pressL = false; }
        if (dragId == id && down) {
            double t = Math.max(0, Math.min(1, (mx - tx) / (double) trackW));
            double r = Math.round(min + t * (max - min));
            if (r != cur) { set.accept(r); cur = r; }
        }
        double t = Math.max(0, Math.min(1, (cur - min) / (max - min)));
        int fx = tx + (int) (trackW * t);
        ctx.fill(tx, y + 12, tx + trackW, y + 14, 0xFF30354A);
        ctx.fill(tx, y + 12, fx, y + 14, acc());
        box(fx - 3, y + 7, 6, 12, dragId == id || in(fx - 6, y + 5, 12, 16) ? 0xFFFFFFFF : 0xFFD8DCEC);
        String v = fmt != null ? fmt.apply((int) Math.round(cur)) : "" + (int) Math.round(cur);
        text(v, x2 - tw(v), y + 9, TXT);
    }

    /** Right-aligned chip. Returns its left edge. */
    int chipCtl(int y, int xRight, String t, int swatch, Runnable l, Runnable r) {
        int w = tw(t) + 16 + (swatch != 0 ? 12 : 0), x = xRight - w;
        boolean hov = in(x, y + 3, w, ROW - 6);
        box(x, y + 3, w, ROW - 6, hov ? 0xFF2A3050 : CARD);
        int tx = x + 8;
        if (swatch != 0) { ctx.fill(x + 6, y + 9, x + 14, y + 17, swatch); tx = x + 20; }
        text(t, tx, y + 9, TXT);
        if (hov && pressL && l != null) { l.run(); pressL = false; }
        if (hov && pressR && r != null) { r.run(); pressR = false; }
        return x;
    }

    void drawSwitch(int id, int x, int y, boolean on) {
        float target = on ? 1f : 0f;
        if (!seen[id]) { anim[id] = target; seen[id] = true; }
        anim[id] += (target - anim[id]) * 0.35f;
        float t = anim[id];
        box(x, y, 30, 14, 0xFF000000 | NovaClient.lerp(OFFC, s.accent, t));
        ctx.fill(x + 3 + (int) (16 * t), y + 3, x + 3 + (int) (16 * t) + 8, y + 11, 0xFFFFFFFF);
    }

    /** Switch on the left, label, optional control on the right. Click the left part to toggle. */
    void switchRow(String label, BooleanSupplier g, Consumer<Boolean> st, int leftW, Ctl ctl) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        int id = (y + scroll - top) / ROW;
        boolean on = g.getAsBoolean();
        boolean hov = in(cx, y, cw, ROW);
        if (hov) box(cx, y + 1, cw, ROW - 2, 0x0DFFFFFF);
        if (ctl != null) ctl.draw(y, cx + leftW, cx + cw - 10);
        if (pressL && in(cx, y, Math.min(leftW, cw), ROW)) { st.accept(!on); pressL = false; on = !on; }
        drawSwitch(id, cx + 8, y + 6, on);
        text(fit(label, leftW - 52), cx + 46, y + 9, on ? TXT : MUT);
    }

    /** Switch on the right (simple on/off setting). */
    void toggleRow(String label, BooleanSupplier g, Consumer<Boolean> st) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        int id = (y + scroll - top) / ROW;
        boolean on = g.getAsBoolean();
        boolean hov = in(cx, y, cw, ROW);
        if (hov) box(cx, y + 1, cw, ROW - 2, 0x0DFFFFFF);
        if (hov && pressL) { st.accept(!on); pressL = false; on = !on; }
        drawSwitch(id, cx + cw - 40, y + 6, on);
        text(fit(label, cw - 70), cx + 10, y + 9, TXT);
    }

    void sliderRow(String label, double min, double max, DoubleSupplier get, Consumer<Double> set, IntFunction<String> fmt) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        boolean hov = in(cx, y, cw, ROW);
        if (hov) box(cx, y + 1, cw, ROW - 2, 0x0DFFFFFF);
        text(fit(label, 130), cx + 10, y + 9, TXT);
        sliderCtl(y, cx + 140, cx + cw - 10, min, max, get, set, fmt);
    }

    void chipRow(String label, String val, int swatch, Runnable l, Runnable r) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        boolean hov = in(cx, y, cw, ROW);
        if (hov) box(cx, y + 1, cw, ROW - 2, 0x0DFFFFFF);
        text(fit(label, cw - 140), cx + 10, y + 9, TXT);
        chipCtl(y, cx + cw - 10, val, swatch, l, r);
    }

    void plainRow(String label, int leftW, Ctl ctl) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        if (in(cx, y, cw, ROW)) box(cx, y + 1, cw, ROW - 2, 0x0DFFFFFF);
        text(fit(label, leftW - 14), cx + 10, y + 9, TXT);
        if (ctl != null) ctl.draw(y, cx + leftW, cx + cw - 10);
    }

    void buttonRow(String t, Runnable a) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        boolean hov = in(cx + 2, y + 2, cw - 4, ROW - 4);
        box(cx + 2, y + 2, cw - 4, ROW - 4, hov ? (0x55000000 | s.accent) : CARD);
        if (hov) { ctx.fill(cx + 2, y + 2, cx + 4, y + ROW - 2, acc()); }
        text(t, cx + 2 + (cw - 4 - tw(t)) / 2, y + 9, TXT);
        if (hov && pressL) { a.run(); pressL = false; }
    }

    void fieldRow(TextFieldWidget f, String label, String b1, Runnable a1, String b2, Runnable a2) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        int right = cx + cw - 10;
        if (b1 != null) right = chipCtl(y, right, b1, 0, a1, null) - 6;
        if (b2 != null) right = chipCtl(y, right, b2, 0, a2, null) - 6;
        int x = cx + 10;
        if (label != null) { text(label, x, y + 9, TXT); x += tw(label) + 10; }
        f.setX(x); f.setY(y + 4); f.setWidth(Math.max(40, right - x));
        f.visible = true; f.active = true;
    }

    void rgbRows(String name, IntSupplier get, IntConsumer set) {
        sliderRow(name + " red", 0, 255, () -> (get.getAsInt() >> 16) & 255, v -> set.accept((get.getAsInt() & 0x00FFFF) | (v.intValue() << 16)), null);
        sliderRow(name + " green", 0, 255, () -> (get.getAsInt() >> 8) & 255, v -> set.accept((get.getAsInt() & 0xFF00FF) | (v.intValue() << 8)), null);
        sliderRow(name + " blue", 0, 255, () -> get.getAsInt() & 255, v -> set.accept((get.getAsInt() & 0xFFFF00) | v.intValue()), null);
    }

    /* ---------- pages ---------- */

    void visuals() {
        header("HUD");
        toggleRow("HUD overlay", () -> s.hud, v -> s.hud = v);
        toggleRow("HUD background", () -> s.hudBg, v -> s.hudBg = v);
        sliderRow("HUD scale", 50, 200, () -> s.hudScale, v -> s.hudScale = v.intValue(), i -> i + "%");
        chipRow("Default corner", CORNERS[s.corner], 0, () -> s.corner = (s.corner + 1) % 4, () -> s.corner = (s.corner + 3) % 4);
        buttonRow("Edit HUD layout (drag modules)", () -> later(() -> client.setScreen(new HudEditor(this))));
        header("Style");
        toggleRow("Text shadow", () -> s.hudShadow, v -> s.hudShadow = v);
        toggleRow("Accent bar", () -> s.hudBar, v -> s.hudBar = v);
        toggleRow("Outline", () -> s.hudOutline, v -> s.hudOutline = v);
        sliderRow("Background opacity", 0, 100, () -> s.bgAlpha, v -> s.bgAlpha = v.intValue(), i -> i + "%");
        sliderRow("Rainbow / pulse speed", 1, 100, () -> s.chromaSpeed, v -> s.chromaSpeed = v.intValue(), null);
        note("Each module can override this style in Modules.");
        header("Screen effects");
        toggleRow("Edge glow", () -> s.glow, v -> s.glow = v);
        sliderRow("Glow size", 4, 40, () -> s.glowSize, v -> s.glowSize = v.intValue(), i -> i + "%");
        toggleRow("Screen tint", () -> s.tint, v -> s.tint = v);
        sliderRow("Tint strength", 1, 60, () -> s.tintAlpha, v -> s.tintAlpha = v.intValue(), i -> i + "%");
        chipRow("Tint color", NovaClient.colorName(s.tintColor), NovaClient.resolve(s, s.tintColor, 0, 1),
                () -> s.tintColor = NovaClient.nextColor(s.tintColor), () -> s.tintColor = NovaClient.prevColor(s.tintColor));
    }

    void modules() {
        if (page != null) { modulePage(page); return; }
        header("Modules");
        for (String n : NovaClient.MODS) {
            Config.Mod m = s.mod(n);
            if (n.equals("Sprint")) {
                switchRow(n, () -> m.on, v -> m.on = v, cw, null);
            } else if (n.equals("Zoom")) {
                switchRow(n, () -> m.on, v -> m.on = v, 150,
                        (y, x1, x2) -> sliderCtl(y, x1, x2, 30, 70, () -> s.zoomFov, v -> s.zoomFov = v.intValue(), i -> "FOV " + i));
            } else {
                switchRow(n, () -> m.on, v -> m.on = v, 170, (y, x1, x2) -> {
                    int l = chipCtl(y, x2, "Settings >", 0, () -> { page = n; scroll = 0; reset(); }, null);
                    chipCtl(y, l - 6, NovaClient.colorName(m.color), NovaClient.resolveM(s, m, 0, 1),
                            () -> m.color = NovaClient.nextColor(m.color), () -> m.color = NovaClient.prevColor(m.color));
                });
            }
        }
        note("Click a row to switch it. Right-click a color to go back.");
    }

    void modulePage(String n) {
        Config.Mod m = s.mod(n);
        boolean text = !(n.equals("Keys") || n.equals("Armor"));
        buttonRow("< Back to modules", () -> { page = null; scroll = 0; reset(); });
        header(n);
        toggleRow("Enabled", () -> m.on, v -> m.on = v);
        chipRow("Color", NovaClient.colorName(m.color), NovaClient.resolveM(s, m, 0, 1),
                () -> m.color = NovaClient.nextColor(m.color), () -> m.color = NovaClient.prevColor(m.color));
        header("Own color (choose Own above)");
        rgbRows("Own", () -> m.rgb, v -> m.rgb = v);
        header("Size and position");
        sliderRow("Scale", 40, 250, () -> m.scale, v -> m.scale = v.intValue(), i -> i + "%");
        NovaClient.Box box = null;
        for (NovaClient.Box b : NovaClient.boxes(client, s, width, height)) if (b.name.equals(n)) box = b;
        float curFx = m.fx >= 0 ? m.fx : (box != null ? box.x / (float) width : 0f);
        float curFy = m.fy >= 0 ? m.fy : (box != null ? box.y / (float) height : 0f);
        sliderRow("Position X", 0, 100, () -> Math.round(curFx * 100), v -> { if (m.fy < 0) m.fy = curFy; m.fx = v.floatValue() / 100f; }, i -> i + "%");
        sliderRow("Position Y", 0, 100, () -> Math.round(curFy * 100), v -> { if (m.fx < 0) m.fx = curFx; m.fy = v.floatValue() / 100f; }, i -> i + "%");
        buttonRow("Auto position (reset)", () -> { m.fx = -1f; m.fy = -1f; });
        header("Look");
        if (text) {
            labelMod = m;
            if (!n.equals(labelFor)) {
                labelFor = n;
                labelDef = NovaClient.defaultLabel(n);
                fLabel.setText(m.label == null ? "" : m.label);
            }
            fieldRow(fLabel, "Label", null, null, null, null);
            chipRow("Background", MODES[m.bgMode], 0, () -> m.bgMode = (m.bgMode + 1) % 3, () -> m.bgMode = (m.bgMode + 2) % 3);
            chipRow("Accent bar", MODES[m.barMode], 0, () -> m.barMode = (m.barMode + 1) % 3, () -> m.barMode = (m.barMode + 2) % 3);
            chipRow("Outline", MODES[m.outlineMode], 0, () -> m.outlineMode = (m.outlineMode + 1) % 3, () -> m.outlineMode = (m.outlineMode + 2) % 3);
        }
        chipRow("Text shadow", MODES[m.shadowMode], 0, () -> m.shadowMode = (m.shadowMode + 1) % 3, () -> m.shadowMode = (m.shadowMode + 2) % 3);
        note("Inherit uses the global style from Visuals.");
    }

    void themes() {
        header("Presets");
        int bw = (cw - 18) / 4;
        for (int r = 0; r < (THEMES.length + 3) / 4; r++) {
            int y = cy; cy += ROW;
            if (y < top || y + ROW > bottom) continue;
            for (int k = 0; k < 4; k++) {
                int i = r * 4 + k;
                if (i >= THEMES.length) break;
                Object[] t = THEMES[i];
                int x = cx + k * (bw + 6);
                boolean hov = in(x, y + 1, bw, ROW - 2), sel = s.theme.equals(t[0]);
                box(x, y + 1, bw, ROW - 2, hov ? 0xFF2A3050 : CARD);
                for (int g = 0; g < bw - 4; g += 2)
                    ctx.fill(x + 2 + g, y + ROW - 6, x + 4 + g, y + ROW - 3, 0xFF000000 | NovaClient.lerp((int) t[1], (int) t[2], g / (float) (bw - 4)));
                text(fit("" + t[0], bw - 8), x + 6, y + 6, sel ? acc() : TXT);
                if (sel) NovaClient.outline(ctx, x, y + 1, bw, ROW - 2, acc());
                if (hov && pressL) { s.theme = (String) t[0]; s.accent = (int) t[1]; s.accent2 = (int) t[2]; pressL = false; }
            }
        }
        note("Current theme: " + s.theme);
        header("Accent");
        rgbRows("Accent", () -> s.accent, v -> { s.accent = v; s.theme = "Custom"; });
        header("Accent 2");
        rgbRows("Accent 2", () -> s.accent2, v -> { s.accent2 = v; s.theme = "Custom"; });
        header("HUD background");
        rgbRows("Background", () -> s.bgColor, v -> s.bgColor = v);
        sliderRow("Opacity", 0, 100, () -> s.bgAlpha, v -> s.bgAlpha = v.intValue(), i -> i + "%");
        header("Custom text color");
        rgbRows("Text", () -> s.textColor, v -> s.textColor = v);
        sliderRow("Rainbow / pulse speed", 1, 100, () -> s.chromaSpeed, v -> s.chromaSpeed = v.intValue(), null);
        note("Pick the color \"Custom\" on a module to use it.");
    }

    void renderTab() {
        header("FPS boost");
        chipRow("Preset", s.boost, 0, () -> boost(1), () -> boost(2));
        toggleRow("Entity culling", () -> s.entityCull, v -> s.entityCull = v);
        sliderRow("Cull distance", 16, 128, () -> s.cullDist, v -> s.cullDist = v.intValue(), i -> i + " blocks");
        note("A preset edits your video settings; Off restores them.");
        header("Render modules");
        for (String n : RenderMods.NAMES) {
            Config.Mod m = s.mod(n);
            if (!RenderMods.hasValue(n)) { switchRow(n, () -> m.on, v -> m.on = v, cw, null); continue; }
            if (RenderMods.isChoice(n)) {
                switchRow(n, () -> m.on, v -> m.on = v, 150, (y, x1, x2) ->
                        chipCtl(y, x2, RenderMods.shortText(n, RenderMods.value(s, n)), 0,
                                () -> m.val = RenderMods.value(s, n) == 0 ? 1 : 0, () -> m.val = RenderMods.value(s, n) == 0 ? 1 : 0));
            } else {
                switchRow(n, () -> m.on, v -> m.on = v, 150, (y, x1, x2) ->
                        sliderCtl(y, x1, x2, RenderMods.min(n), RenderMods.max(n), () -> RenderMods.value(s, n),
                                v -> m.val = v.intValue(), i -> RenderMods.shortText(n, i)));
            }
        }
        note("Each one changes a video option. Off restores yours.");
    }

    void boost(int dir) {
        s.boost = BOOST[(Arrays.asList(BOOST).indexOf(s.boost) + dir) % 3];
        Boost.apply(s.boost);
        Config.save();
    }

    void profiles() {
        header("New profile");
        fieldRow(fProfile, null, "Save new", () -> {
            String n = fProfile.getText().trim();
            if (n.isEmpty() || Config.I.profiles.containsKey(n)) return;
            Config.Settings copy = Config.G.fromJson(Config.G.toJson(s), Config.Settings.class);
            Config.I.profiles.put(n, copy);
            Config.I.active = n;
            Config.save();
            fProfile.setText("");
        }, null, null);
        header("Your profiles");
        for (String n : new ArrayList<>(Config.I.profiles.keySet())) {
            int y = cy; cy += ROW;
            if (y < top || y + ROW > bottom) continue;
            boolean active = n.equals(Config.I.active), hov = in(cx, y, cw, ROW);
            if (hov) box(cx, y + 1, cw, ROW - 2, 0x0DFFFFFF);
            if (active) ctx.fill(cx + 8, y + 9, cx + 14, y + 15, acc());
            text(fit(n, cw - 120), cx + 22, y + 9, active ? acc() : TXT);
            boolean onlyOne = Config.I.profiles.size() <= 1;
            int l = cx + cw - 10;
            if (!onlyOne) l = chipCtl(y, cx + cw - 10, "Delete", 0, () -> later(() -> {
                Config.I.profiles.remove(n);
                if (n.equals(Config.I.active)) Config.I.active = Config.I.profiles.keySet().iterator().next();
                Config.save();
            }), null) - 6;
            if (hov && pressL && mx < l) {
                Config.I.active = n;
                Boost.apply(Config.cur().boost);
                Config.save();
                pressL = false;
            }
        }
    }

    void custom() {
        header("Add a setting");
        fieldRow(fCustom, null, "Slider", () -> addCustom("slider"), "Toggle", () -> addCustom("toggle"));
        header("Your settings");
        if (s.custom.isEmpty()) note("Nothing here yet.");
        for (int i = 0; i < s.custom.size(); i++) {
            int idx = i;
            Config.Custom c = s.custom.get(i);
            if (c.type.equals("toggle")) {
                switchRow(c.name, () -> c.value > 0, v -> c.value = v ? 1 : 0, cw - 80,
                        (y, x1, x2) -> chipCtl(y, cx + cw - 10, "Delete", 0, () -> later(() -> s.custom.remove(idx)), null));
            } else {
                plainRow(c.name, 150, (y, x1, x2) -> {
                    int l = chipCtl(y, x2, "Delete", 0, () -> later(() -> s.custom.remove(idx)), null);
                    sliderCtl(y, x1, l - 8, 0, 100, () -> c.value, v -> c.value = v, null);
                });
            }
        }
    }

    void addCustom(String type) {
        String n = fCustom.getText().trim();
        if (n.isEmpty()) return;
        Config.Custom c = new Config.Custom();
        c.name = n; c.type = type; c.value = type.equals("slider") ? 50 : 0;
        s.custom.add(c);
        fCustom.setText("");
    }

    /* ---------- frame ---------- */

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        scroll = Math.max(0, Math.min(maxScroll, scroll + (verticalAmount < 0 ? ROW : -ROW)));
        return true;
    }

    @Override
    public void renderBackground(DrawContext c, int mouseX, int mouseY, float d) {
        super.renderBackground(c, mouseX, mouseY, d);
    }

    boolean footBtn(int x, int y, int w, String t, boolean primary) {
        boolean hov = in(x, y, w, 20);
        int bg = primary ? (hov ? 0xFF000000 | NovaClient.lerp(s.accent, 0xFFFFFF, 0.2f) : acc()) : (hov ? 0xFF2A3050 : CARD);
        box(x, y, w, 20, bg);
        text(t, x + (w - tw(t)) / 2, y + 6, primary ? 0xFF10131A : TXT);
        if (hov && pressL) { pressL = false; return true; }
        return false;
    }

    @Override
    public void render(DrawContext c, int mouseX, int mouseY, float d) {
        ctx = c;
        s = Config.cur();
        mx = mouseX; my = mouseY;
        long win = GLFW.glfwGetCurrentContext();
        boolean l = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean r = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_RIGHT) == GLFW.GLFW_PRESS;
        pressL = l && !wasL; pressR = r && !wasR; down = l; wasL = l; wasR = r;
        if (!l) dragId = -1;

        renderBackground(c, mouseX, mouseY, d);

        // panel
        c.fill(px - 1, py - 1, px + W + 1, py + H + 1, 0x55000000);
        box(px, py, W, H, 0xF2101319);
        c.fill(px + 1, py + 1, px + 110, py + H - 1, 0xFF0C0E15);
        for (int i = 0; i < W - 2; i += 3)
            c.fill(px + 1 + i, py, Math.min(px + W - 1, px + 4 + i), py + 2, 0xFF000000 | NovaClient.lerp(s.accent, s.accent2, i / (float) W));

        // sidebar: logo
        String logo = "NOVA";
        int lx = px + 16;
        for (int i = 0; i < logo.length(); i++) {
            String ch = "" + logo.charAt(i);
            text(ch, lx, py + 12, 0xFF000000 | NovaClient.lerp(s.accent, s.accent2, i / 3f));
            lx += tw(ch) + 1;
        }
        text("Client", px + 16, py + 23, MUT);

        // sidebar: tabs
        int th = Math.max(18, Math.min(26, (H - 96) / TABS.length));
        for (int i = 0; i < TABS.length; i++) {
            int y = py + 40 + i * (th + 2);
            boolean sel = i == tab, hov = in(px + 8, y, 96, th);
            if (sel) { box(px + 8, y, 96, th, 0x30000000 | s.accent); ctx.fill(px + 8, y + 3, px + 10, y + th - 3, acc()); }
            else if (hov) box(px + 8, y, 96, th, 0x14FFFFFF);
            text(TABS[i], px + 18, y + (th - 8) / 2, sel ? acc() : (hov ? TXT : MUT));
            if (hov && pressL && !sel) { tab = i; scroll = 0; page = null; reset(); pressL = false; }
        }
        // sidebar: active profile
        String pr = fit(Config.I.active, 84);
        boolean ph = in(px + 8, py + H - 30, 96, 22);
        box(px + 8, py + H - 30, 96, 22, ph ? 0xFF2A3050 : CARD);
        text("Profile", px + 14, py + H - 26, 0xFF666C84);
        text(pr, px + 14, py + H - 16, TXT);
        if (ph && pressL) { tab = 4; scroll = 0; page = null; reset(); pressL = false; }

        // title
        String title = tab == 1 && page != null ? page : TABS[tab];
        text(title, cx + 2, py + 12, TXT);

        // content
        fProfile.visible = false; fCustom.visible = false; fLabel.visible = false;
        cy = top - scroll;
        idc = 0;
        switch (tab) {
            case 0 -> visuals();
            case 1 -> modules();
            case 2 -> themes();
            case 3 -> renderTab();
            case 4 -> profiles();
            default -> custom();
        }
        int total = (cy + scroll) - top;
        maxScroll = Math.max(0, (int) Math.ceil((total - (bottom - top)) / (double) ROW) * ROW);
        if (scroll > maxScroll) scroll = maxScroll;

        // text fields (real widgets)
        if (fProfile.visible) fProfile.render(c, mouseX, mouseY, d);
        if (fCustom.visible) fCustom.render(c, mouseX, mouseY, d);
        if (fLabel.visible) fLabel.render(c, mouseX, mouseY, d);

        // scrollbar
        if (maxScroll > 0) {
            int track = bottom - top, thumb = Math.max(16, track * track / (track + maxScroll));
            int ty = top + (int) ((track - thumb) * (scroll / (float) maxScroll));
            c.fill(cx + cw + 6, top, cx + cw + 9, bottom, 0x22FFFFFF);
            c.fill(cx + cw + 6, ty, cx + cw + 9, ty + thumb, acc());
        }

        // footer
        c.fill(px + 112, py + H - 32, px + W - 8, py + H - 31, 0x18FFFFFF);
        if (footBtn(cx, py + H - 26, 124, "Edit HUD layout", false)) later(() -> client.setScreen(new HudEditor(this)));
        if (footBtn(px + W - 8 - 76, py + H - 26, 76, "Done", true)) later(this::close);
        if (maxScroll > 0) text("Mouse wheel to scroll", cx + 134, py + H - 20, 0xFF666C84);

        if (pending != null) { Runnable p = pending; pending = null; p.run(); }
    }

    @Override
    public void close() {
        Config.save();
        RenderMods.sync(Config.cur());
        client.setScreen(parent);
    }
}
