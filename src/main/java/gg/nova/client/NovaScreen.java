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
 * NOVA settings menu: glass panel with a glowing edge, icon sidebar, module cards,
 * pill rows, animated switches and gradient sliders. Everything is custom drawn and the
 * mouse is read directly. Left-click uses a control, right-click on a chip/card goes
 * back or opens settings, the wheel scrolls.
 */
public class NovaScreen extends Screen {
    static final String[] TABS = {"Visuals", "Modules", "Themes", "Render", "Profiles", "Custom"};
    static final String[] SUBS = {"HUD, style and screen effects", "Switch modules on and tune them", "Colors and gradients",
            "FPS boost and video options", "Save and switch setups", "Your own settings"};
    static final String[] CORNERS = {"Top left", "Top right", "Bottom left", "Bottom right"};
    static final String[] BOOST = {"Off", "Balanced", "Max"};
    static final String[] MODES = {"Inherit", "On", "Off"};
    static final Object[][] THEMES = {
            {"Neon", 0x7C5CFF, 0x00E5FF}, {"Ember", 0xFF6A3D, 0xFFC857}, {"Forest", 0x3DDC97, 0xB6F09C}, {"Ocean", 0x2F8CFF, 0x5EF2D6},
            {"Rose", 0xFF4D8D, 0xFFB3D1}, {"Mono", 0xFFFFFF, 0x8A8A8A}, {"Sunset", 0xFF5F6D, 0xFFC371}, {"Aurora", 0x00F5A0, 0x00D9F5},
            {"Grape", 0xB721FF, 0x21D4FD}, {"Mint", 0x5EFCE8, 0x736EFE}, {"Gold", 0xF9D423, 0xFF4E50}, {"Ice", 0x74EBD5, 0xACB6E5},
            {"Crimson", 0xE53935, 0xFF8A80}, {"Midnight", 0x6A11CB, 0x2575FC}, {"Lava", 0xFF512F, 0xF09819}, {"Candy", 0xFF9A9E, 0xFAD0C4},
            {"Cyber", 0xFF00C8, 0x00F0FF}, {"Toxic", 0x39FF14, 0xCCFF00}, {"Royal", 0x4776E6, 0x8E54E9}, {"Steel", 0x7F8C8D, 0xBDC3C7}};
    static final String[][] ICONS = {
            {"00000000", "00111100", "01000010", "10011001", "10011001", "01000010", "00111100", "00000000"},
            {"11101110", "11101110", "11101110", "00000000", "11101110", "11101110", "11101110", "00000000"},
            {"00011000", "00011000", "00111100", "01111110", "11111111", "11111111", "01111110", "00111100"},
            {"11111111", "10000001", "10000001", "10000001", "11111111", "00011000", "00111100", "00000000"},
            {"00111100", "01111110", "01111110", "00111100", "00000000", "01111110", "11111111", "11111111"},
            {"11111111", "00011000", "00000000", "11111111", "11000000", "00000000", "11111111", "00001100"}};

    static final int ROW = 26;
    static final int TXT = 0xFFE8EBF7, MUT = 0xFF8B91AB, DIM = 0xFF5E647D;
    static final int ROWBG = 0xFF131726, ROWHOV = 0xFF1A2034, CARD = 0xFF161A2A, CARDHOV = 0xFF1D2338, TRACK = 0xFF2A3047;

    static int tab = 0, scroll = 0;
    static String page = null;

    interface Ctl { void draw(int y, int x1, int x2); }

    final Screen parent;
    DrawContext ctx;
    Config.Settings s;
    int W, H, px, py, cx, cw, top, bottom;
    int cy, maxScroll, dragId = -1, mx, my;
    boolean pressL, pressR, down, wasL = true, wasR = true;
    float tabY = -1;
    final float[] anim = new float[1024];
    final boolean[] seen = new boolean[1024];
    Runnable pending;
    TextFieldWidget fProfile, fCustom, fLabel;
    Config.Mod labelMod;
    String labelFor = "", labelDef = "";

    public NovaScreen(Screen parent) { super(Text.literal("NOVA Client")); this.parent = parent; }

    @Override
    protected void init() {
        W = Math.min(width - 12, 540);
        H = Math.min(height - 12, 330);
        px = (width - W) / 2;
        py = (height - H) / 2;
        cx = px + 128;
        cw = W - 128 - 20;
        top = py + 44;
        bottom = py + H - 38;
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
        f.setDrawsBackground(false);
        f.setSuggestion(hint);
        f.setChangedListener(t -> f.setSuggestion(t.isEmpty() ? hint : ""));
        f.visible = false;
        addDrawableChild(f);
        return f;
    }

    void reset() { Arrays.fill(seen, false); labelFor = ""; }
    void later(Runnable r) { pending = r; }

    /* ---------- drawing helpers ---------- */

    boolean in(int x, int y, int w, int h) { return mx >= x && mx < x + w && my >= y && my < y + h; }
    int acc() { return 0xFF000000 | s.accent; }
    int acc2() { return 0xFF000000 | s.accent2; }
    int mix(int a, int b, float t) { return 0xFF000000 | NovaClient.lerp(a, b, t); }
    int tw(String t) { return textRenderer.getWidth(t); }
    void text(String t, int x, int y, int col) { ctx.drawText(textRenderer, t, x, y, col, false); }
    void rbox(int x, int y, int w, int h, int r, int col) { NovaClient.rbox(ctx, x, y, w, h, r, col); }
    String fit(String t, int w) { return tw(t) <= w ? t : textRenderer.trimToWidth(t, Math.max(0, w - 8)) + ".."; }

    void big(String t, int x, int y, float sc, int col) {
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().translate((float) x, (float) y);
        ctx.getMatrices().scale(sc, sc);
        text(t, 0, 0, col);
        ctx.getMatrices().popMatrix();
    }

    void icon(int i, int x, int y, int col) {
        for (int r = 0; r < 8; r++)
            for (int c = 0; c < 8; c++)
                if (ICONS[i][r].charAt(c) == '1') ctx.fill(x + c, y + r, x + c + 1, y + r + 1, col);
    }

    void rowBg(int y, boolean hov) { rbox(cx, y + 1, cw, ROW - 2, 5, hov ? ROWHOV : ROWBG); }

    /* ---------- controls ---------- */

    void header(String t) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        String u = t.toUpperCase();
        rbox(cx + 2, y + 10, 3, 8, 1, acc());
        text(u, cx + 10, y + 10, MUT);
        ctx.fill(cx + 16 + tw(u), y + 14, cx + cw, y + 15, 0x16FFFFFF);
    }

    void note(String t) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        text(fit(t, cw - 12), cx + 8, y + 9, DIM);
    }

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
        boolean hot = dragId == id || in(fx - 8, y + 4, 16, 18);
        rbox(tx, y + 11, trackW, 4, 2, TRACK);
        for (int g = 0; g < fx - tx; g += 2)
            ctx.fill(tx + g, y + 11, Math.min(fx, tx + g + 2), y + 15, mix(s.accent, s.accent2, g / (float) Math.max(1, trackW)));
        if (hot) rbox(fx - 7, y + 6, 14, 14, 7, 0x30000000 | s.accent);
        rbox(fx - 5, y + 8, 10, 10, 5, 0xFFFFFFFF);
        String v = fmt != null ? fmt.apply((int) Math.round(cur)) : "" + (int) Math.round(cur);
        text(v, x2 - tw(v), y + 9, TXT);
    }

    /** Right-aligned chip. Returns its left edge. */
    int chipCtl(int y, int xRight, String t, int swatch, Runnable l, Runnable r) {
        int w = tw(t) + 18 + (swatch != 0 ? 12 : 0), x = xRight - w;
        boolean hov = in(x, y + 3, w, ROW - 6);
        rbox(x, y + 3, w, ROW - 6, 5, hov ? mix(0x262C45, s.accent, 0.25f) : 0xFF222842);
        int tx = x + 9;
        if (swatch != 0) { rbox(x + 7, y + 9, 8, 8, 3, swatch); tx = x + 21; }
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
        if (t > 0.05f) rbox(x - 2, y - 2, 34, 18, 9, ((int) (0x38 * t) << 24) | s.accent);
        rbox(x, y, 30, 14, 7, mix(0x363C56, s.accent, t));
        rbox(x + 2 + (int) (16 * t), y + 2, 10, 10, 5, 0xFFFFFFFF);
    }

    void switchRow(String label, BooleanSupplier g, Consumer<Boolean> st, int leftW, Ctl ctl) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        int id = (y + scroll - top) / ROW;
        boolean on = g.getAsBoolean();
        rowBg(y, in(cx, y, cw, ROW));
        if (ctl != null) ctl.draw(y, cx + leftW, cx + cw - 10);
        if (pressL && in(cx, y, Math.min(leftW, cw), ROW)) { st.accept(!on); pressL = false; on = !on; }
        drawSwitch(id, cx + 10, y + 6, on);
        text(fit(label, leftW - 56), cx + 48, y + 9, on ? TXT : MUT);
    }

    void toggleRow(String label, BooleanSupplier g, Consumer<Boolean> st) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        int id = (y + scroll - top) / ROW;
        boolean on = g.getAsBoolean(), hov = in(cx, y, cw, ROW);
        rowBg(y, hov);
        if (hov && pressL) { st.accept(!on); pressL = false; on = !on; }
        drawSwitch(id, cx + cw - 42, y + 6, on);
        text(fit(label, cw - 70), cx + 12, y + 9, TXT);
    }

    void sliderRow(String label, double min, double max, DoubleSupplier get, Consumer<Double> set, IntFunction<String> fmt) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        rowBg(y, in(cx, y, cw, ROW));
        text(fit(label, 130), cx + 12, y + 9, TXT);
        sliderCtl(y, cx + 140, cx + cw - 10, min, max, get, set, fmt);
    }

    void chipRow(String label, String val, int swatch, Runnable l, Runnable r) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        rowBg(y, in(cx, y, cw, ROW));
        text(fit(label, cw - 150), cx + 12, y + 9, TXT);
        chipCtl(y, cx + cw - 10, val, swatch, l, r);
    }

    void plainRow(String label, int leftW, Ctl ctl) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        rowBg(y, in(cx, y, cw, ROW));
        text(fit(label, leftW - 14), cx + 12, y + 9, TXT);
        if (ctl != null) ctl.draw(y, cx + leftW, cx + cw - 10);
    }

    void buttonRow(String t, Runnable a) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        boolean hov = in(cx, y + 1, cw, ROW - 2);
        rbox(cx, y + 1, cw, ROW - 2, 5, hov ? mix(0x1A2034, s.accent, 0.3f) : CARD);
        if (hov) rbox(cx, y + 6, 3, ROW - 12, 1, acc());
        text(t, cx + (cw - tw(t)) / 2, y + 9, TXT);
        if (hov && pressL) { a.run(); pressL = false; }
    }

    void fieldRow(TextFieldWidget f, String label, String b1, Runnable a1, String b2, Runnable a2) {
        int y = cy; cy += ROW;
        if (y < top || y + ROW > bottom) return;
        rowBg(y, false);
        int right = cx + cw - 10;
        if (b1 != null) right = chipCtl(y, right, b1, 0, a1, null) - 6;
        if (b2 != null) right = chipCtl(y, right, b2, 0, a2, null) - 6;
        int x = cx + 12;
        if (label != null) { text(label, x, y + 9, TXT); x += tw(label) + 10; }
        int w = Math.max(50, right - x);
        rbox(x, y + 3, w, ROW - 6, 5, 0xFF0C0F1A);
        f.setX(x + 7); f.setY(y + 4); f.setWidth(w - 10);
        f.visible = true; f.active = true;
    }

    void rgbRows(String name, IntSupplier get, IntConsumer set) {
        sliderRow(name + " red", 0, 255, () -> (get.getAsInt() >> 16) & 255, v -> set.accept((get.getAsInt() & 0x00FFFF) | (v.intValue() << 16)), null);
        sliderRow(name + " green", 0, 255, () -> (get.getAsInt() >> 8) & 255, v -> set.accept((get.getAsInt() & 0xFF00FF) | (v.intValue() << 8)), null);
        sliderRow(name + " blue", 0, 255, () -> get.getAsInt() & 255, v -> set.accept((get.getAsInt() & 0xFFFF00) | v.intValue()), null);
    }

    /** A module card (two rows tall): title, switch, optional bottom control. Right-click runs alt. */
    void card(int x, int y, int w, int id, String title, int stripe, boolean on, Consumer<Boolean> st, Runnable alt, String sub, Ctl bottomCtl) {
        int ct = y + 2, h = 2 * ROW - 4;
        boolean hov = in(x, ct, w, h);
        if (on) rbox(x - 1, ct - 1, w + 2, h + 2, 7, 0x44000000 | s.accent);
        rbox(x, ct, w, h, 6, on ? mix(0x161A2A, s.accent, 0.14f) : (hov ? CARDHOV : CARD));
        if (stripe != 0) rbox(x + 1, ct + 7, 3, h - 14, 1, stripe);
        if (bottomCtl != null) bottomCtl.draw(ct + 22, x + 12, x + w - 10);
        else if (sub != null) text(fit(sub, w - 26), x + 12, ct + 28, MUT);
        if (hov) {
            if (pressL) { st.accept(!on); pressL = false; on = !on; }
            else if (pressR && alt != null) { alt.run(); pressR = false; }
        }
        text(fit(title, w - 70), x + 12, ct + 9, on ? TXT : 0xFFB9BFD6);
        drawSwitch(id, x + w - 42, ct + 7, on);
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
        int cardW = cw / 2 - 4, rowY = 0;
        for (int i = 0; i < NovaClient.MODS.length; i++) {
            String n = NovaClient.MODS[i];
            Config.Mod m = s.mod(n);
            if (i % 2 == 0) { rowY = cy; cy += 2 * ROW; }
            if (rowY < top || rowY + 2 * ROW > bottom) continue;
            int x = cx + (i % 2) * (cw / 2 + 4), id = (rowY + scroll - top) / ROW * 2 + (i % 2);
            if (n.equals("Sprint")) {
                card(x, rowY, cardW, id, n, 0, m.on, v -> m.on = v, null, "Auto sprint while moving", null);
            } else if (n.equals("Zoom")) {
                card(x, rowY, cardW, id, n, 0, m.on, v -> m.on = v, null, null,
                        (y, x1, x2) -> sliderCtl(y, x1, x2, 30, 70, () -> s.zoomFov, v -> s.zoomFov = v.intValue(), k -> "FOV " + k));
            } else {
                card(x, rowY, cardW, id, n, NovaClient.resolveM(s, m, 0, 1), m.on, v -> m.on = v,
                        () -> { page = n; scroll = 0; reset(); }, null, (y, x1, x2) -> {
                            int l = chipCtl(y, x2, "Settings", 0, () -> { page = n; scroll = 0; reset(); }, null);
                            chipCtl(y, l - 6, NovaClient.colorName(m.color), NovaClient.resolveM(s, m, 0, 1),
                                    () -> m.color = NovaClient.nextColor(m.color), () -> m.color = NovaClient.prevColor(m.color));
                        });
            }
        }
        note("Click a card to switch it. Right-click it to open its settings.");
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
                if (sel) rbox(x - 1, y, bw + 2, ROW, 6, 0x60000000 | s.accent);
                rbox(x, y + 1, bw, ROW - 2, 5, hov ? CARDHOV : CARD);
                for (int g = 0; g < bw - 8; g += 2)
                    ctx.fill(x + 4 + g, y + ROW - 7, x + 6 + g, y + ROW - 4, 0xFF000000 | NovaClient.lerp((int) t[1], (int) t[2], g / (float) (bw - 8)));
                text(fit("" + t[0], bw - 8), x + 6, y + 5, sel ? acc() : TXT);
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
        int cardW = cw / 2 - 4, rowY = 0;
        for (int i = 0; i < RenderMods.NAMES.length; i++) {
            String n = RenderMods.NAMES[i];
            Config.Mod m = s.mod(n);
            if (i % 2 == 0) { rowY = cy; cy += 2 * ROW; }
            if (rowY < top || rowY + 2 * ROW > bottom) continue;
            int x = cx + (i % 2) * (cw / 2 + 4), id = (rowY + scroll - top) / ROW * 2 + (i % 2);
            if (!RenderMods.hasValue(n)) {
                String d = n.equals("No Shadows") ? "Entity shadows off" : n.equals("No Bob") ? "View bobbing off" : "Smooth lighting off";
                card(x, rowY, cardW, id, n, 0, m.on, v -> m.on = v, null, d, null);
            } else if (RenderMods.isChoice(n)) {
                card(x, rowY, cardW, id, n, 0, m.on, v -> m.on = v, null, null, (y, x1, x2) ->
                        chipCtl(y, x2, RenderMods.shortText(n, RenderMods.value(s, n)), 0,
                                () -> m.val = RenderMods.value(s, n) == 0 ? 1 : 0, () -> m.val = RenderMods.value(s, n) == 0 ? 1 : 0));
            } else {
                card(x, rowY, cardW, id, n, 0, m.on, v -> m.on = v, null, null, (y, x1, x2) ->
                        sliderCtl(y, x1, x2, RenderMods.min(n), RenderMods.max(n), () -> RenderMods.value(s, n),
                                v -> m.val = v.intValue(), k -> RenderMods.shortText(n, k)));
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
            rowBg(y, hov);
            if (active) rbox(cx + 10, y + 9, 8, 8, 4, acc());
            text(fit(n, cw - 130), cx + 26, y + 9, active ? acc() : TXT);
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

    boolean footBtn(int x, int y, int w, String t, boolean primary) {
        boolean hov = in(x, y, w, 22);
        if (primary) {
            rbox(x, y, w, 22, 6, hov ? mix(s.accent, 0xFFFFFF, 0.2f) : acc());
        } else {
            rbox(x, y, w, 22, 6, hov ? mix(0x262C45, s.accent, 0.25f) : 0xFF222842);
        }
        text(t, x + (w - tw(t)) / 2, y + 7, primary ? 0xFF0E1018 : TXT);
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

        // glow + glass panel
        for (int k = 9; k >= 1; k--) rbox(px - k, py - k, W + 2 * k, H + 2 * k, 9 + k, 0x08000000 | s.accent);
        rbox(px, py, W, H, 9, 0xF00D1019);
        rbox(px + 6, py + 6, 112, H - 12, 7, 0xFF090B12);
        for (int i = 8; i < W - 8; i += 3)
            ctx.fill(px + i, py + 1, Math.min(px + W - 8, px + i + 3), py + 3, mix(s.accent, s.accent2, i / (float) W));

        // logo
        String logo = "NOVA";
        int lx = px + 20;
        for (int i = 0; i < logo.length(); i++) {
            String ch = "" + logo.charAt(i);
            big(ch, lx, py + 15, 1.6f, mix(s.accent, s.accent2, i / 3f));
            lx += (int) (tw(ch) * 1.6f) + 2;
        }
        text("CLIENT", px + 21, py + 31, MUT);

        // tabs with sliding highlight
        int th = Math.max(20, Math.min(28, (H - 118) / TABS.length));
        int tabTop = py + 50;
        float target = tabTop + tab * (th + 3);
        tabY = tabY < 0 ? target : tabY + (target - tabY) * 0.3f;
        rbox(px + 12, (int) tabY, 104, th, 6, 0x34000000 | s.accent);
        rbox(px + 12, (int) tabY + 5, 3, th - 10, 1, acc());
        for (int i = 0; i < TABS.length; i++) {
            int y = tabTop + i * (th + 3);
            boolean sel = i == tab, hov = in(px + 12, y, 104, th);
            if (hov && !sel) rbox(px + 12, y, 104, th, 6, 0x14FFFFFF);
            int col = sel ? acc() : (hov ? TXT : MUT);
            icon(i, px + 24, y + (th - 8) / 2, col);
            text(TABS[i], px + 38, y + (th - 8) / 2, col);
            if (hov && pressL && !sel) { tab = i; scroll = 0; page = null; reset(); pressL = false; }
        }

        // active profile card
        String pr = fit(Config.I.active, 80);
        boolean ph = in(px + 12, py + H - 42, 104, 30);
        rbox(px + 12, py + H - 42, 104, 30, 6, ph ? CARDHOV : CARD);
        text("PROFILE", px + 20, py + H - 37, DIM);
        text(pr, px + 20, py + H - 26, TXT);
        if (ph && pressL) { tab = 4; scroll = 0; page = null; reset(); pressL = false; }

        // page title
        String title = tab == 1 && page != null ? page + " settings" : TABS[tab];
        String sub = tab == 1 && page != null ? "Modules  >  " + page : SUBS[tab];
        big(title, cx + 2, py + 13, 1.35f, TXT);
        text(sub, cx + 3, py + 28, MUT);
        ctx.fill(cx, py + 39, cx + cw, py + 40, 0x14FFFFFF);

        // content
        fProfile.visible = false; fCustom.visible = false; fLabel.visible = false;
        cy = top - scroll;
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

        // real text fields on top of their drawn backgrounds
        if (fProfile.visible) fProfile.render(c, mouseX, mouseY, d);
        if (fCustom.visible) fCustom.render(c, mouseX, mouseY, d);
        if (fLabel.visible) fLabel.render(c, mouseX, mouseY, d);

        // scrollbar
        if (maxScroll > 0) {
            int track = bottom - top, thumb = Math.max(18, track * track / (track + maxScroll));
            int ty = top + (int) ((track - thumb) * (scroll / (float) maxScroll));
            rbox(cx + cw + 7, top, 3, track, 1, 0x22FFFFFF);
            rbox(cx + cw + 7, ty, 3, thumb, 1, acc());
        }

        // footer
        ctx.fill(cx, py + H - 34, cx + cw, py + H - 33, 0x14FFFFFF);
        if (footBtn(cx, py + H - 30, 128, "Edit HUD layout", false)) later(() -> client.setScreen(new HudEditor(this)));
        if (footBtn(cx + cw - 80, py + H - 30, 80, "Done", true)) later(this::close);
        if (maxScroll > 0) text("Scroll for more", cx + 140, py + H - 24, DIM);

        if (pending != null) { Runnable p = pending; pending = null; p.run(); }
    }

    @Override
    public void close() {
        Config.save();
        RenderMods.sync(Config.cur());
        client.setScreen(parent);
    }
}
