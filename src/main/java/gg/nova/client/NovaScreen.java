package gg.nova.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

public class NovaScreen extends Screen {
    static final String[] TABS = {"Visuals", "Modules", "Themes", "Render", "Profiles", "Custom"};
    static final String[] SUBS = {"Presets", "Accent", "Accent 2", "Background"};
    static final String[] CORNERS = {"Top left", "Top right", "Bottom left", "Bottom right"};
    static final String[] BOOST = {"Off", "Balanced", "Max"};
    static final Object[][] THEMES = {
            {"Neon", 0x7C5CFF, 0x00E5FF}, {"Ember", 0xFF6A3D, 0xFFC857}, {"Forest", 0x3DDC97, 0xB6F09C},
            {"Ocean", 0x2F8CFF, 0x5EF2D6}, {"Rose", 0xFF4D8D, 0xFFB3D1}, {"Mono", 0xFFFFFF, 0x8A8A8A},
            {"Sunset", 0xFF5F6D, 0xFFC371}, {"Aurora", 0x00F5A0, 0x00D9F5}, {"Grape", 0xB721FF, 0x21D4FD},
            {"Mint", 0x5EFCE8, 0x736EFE}, {"Gold", 0xF9D423, 0xFF4E50}, {"Ice", 0x74EBD5, 0xACB6E5},
            {"Crimson", 0xE53935, 0xFF8A80}, {"Midnight", 0x6A11CB, 0x2575FC}};
    static int tab = 0, sub = 0;

    final Screen parent;
    TextFieldWidget field;
    String note = "";
    int noteY;
    final List<int[]> sw = new ArrayList<>();

    public NovaScreen(Screen parent) { super(Text.literal("NOVA Client")); this.parent = parent; }

    int px() { return width / 2 - 180; }
    int py() { return height / 2 - 125; }

    @Override
    protected void init() {
        Config.Settings s = Config.cur();
        int x = px() + 10, y = py() + 52, w = 340;
        note = "";
        sw.clear();
        for (int i = 0; i < TABS.length; i++) {
            int n = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(TABS[i]), b -> { tab = n; clearAndInit(); })
                    .dimensions(px() + 10 + i * 56, py() + 24, 54, 20).build());
        }
        switch (tab) {
            case 0 -> visuals(s, x, y, w);
            case 1 -> modules(s, x, y, w);
            case 2 -> themes(s, x, y, w);
            case 3 -> renderTab(s, x, y, w);
            case 4 -> profiles(s, x, y, w);
            default -> custom(s, x, y, w);
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 - 40, py() + 226, 80, 20).build());
    }

    /* ---------- tabs ---------- */

    void visuals(Config.Settings s, int x, int y, int w) {
        toggle("HUD overlay", x, y, w, () -> s.hud, v -> s.hud = v);
        toggle("HUD background", x, y + 24, w, () -> s.hudBg, v -> s.hudBg = v);
        slider("HUD scale %", x, y + 48, w, 50, 200, s.hudScale, v -> s.hudScale = v.intValue());
        addDrawableChild(ButtonWidget.builder(Text.literal("HUD corner: " + CORNERS[s.corner]), b -> {
            s.corner = (s.corner + 1) % 4;
            b.setMessage(Text.literal("HUD corner: " + CORNERS[s.corner]));
        }).dimensions(x, y + 72, w, 20).build());
        note = "Modules and colors are in the next tabs.";
        noteY = y + 100;
    }

    void modules(Config.Settings s, int x, int y, int w) {
        for (int i = 0; i < NovaClient.MODS.length; i++) {
            String n = NovaClient.MODS[i];
            Config.Mod m = s.mod(n);
            int cx = x + (i % 2) * 175, cy = y + (i / 2) * 24;
            addDrawableChild(ButtonWidget.builder(txt(n, m.on), b -> {
                m.on = !m.on;
                b.setMessage(txt(n, m.on));
            }).dimensions(cx, cy, 100, 20).build());
            addDrawableChild(ButtonWidget.builder(Text.literal(NovaClient.colorName(m.color)), b -> {
                m.color = NovaClient.nextColor(m.color);
                b.setMessage(Text.literal(NovaClient.colorName(m.color)));
            }).dimensions(cx + 102, cy, 62, 20).build());
        }
        slider("Zoom FOV (hold Z)", x, y + 144, w, 30, 70, s.zoomFov, v -> s.zoomFov = v.intValue());
    }

    void themes(Config.Settings s, int x, int y, int w) {
        for (int i = 0; i < SUBS.length; i++) {
            int n = i;
            addDrawableChild(ButtonWidget.builder(Text.literal((sub == i ? "> " : "") + SUBS[i]), b -> { sub = n; clearAndInit(); })
                    .dimensions(x + i * 86, y, 82, 20).build());
        }
        int y0 = y + 28;
        if (sub == 0) {
            for (int i = 0; i < THEMES.length; i++) {
                Object[] t = THEMES[i];
                int bx = x + (i % 3) * 115, by = y0 + (i / 3) * 24;
                addDrawableChild(ButtonWidget.builder(Text.literal("" + t[0]), b -> {
                    s.theme = (String) t[0]; s.accent = (int) t[1]; s.accent2 = (int) t[2]; clearAndInit();
                }).dimensions(bx, by, 110, 20).build());
                sw.add(new int[]{bx + 5, by + 4, (int) t[1], (int) t[2]});
            }
            note = "Current theme: " + s.theme;
            noteY = y0 + 126;
        } else if (sub == 1) {
            rgb("Accent", x, y0, w, () -> s.accent, v -> { s.accent = v; s.theme = "Custom"; });
            note = "Accent: HUD bar, menu lines, first gradient color.";
            noteY = y0 + 76;
        } else if (sub == 2) {
            rgb("Accent 2", x, y0, w, () -> s.accent2, v -> { s.accent2 = v; s.theme = "Custom"; });
            note = "Accent 2: second gradient color and menu line.";
            noteY = y0 + 76;
        } else {
            rgb("Background", x, y0, w, () -> s.bgColor, v -> s.bgColor = v);
            slider("Opacity %", x, y0 + 72, w, 0, 100, s.bgAlpha, v -> s.bgAlpha = v.intValue());
            slider("Rainbow speed", x, y0 + 96, w, 1, 100, s.chromaSpeed, v -> s.chromaSpeed = v.intValue());
        }
    }

    void rgb(String name, int x, int y, int w, IntSupplier get, IntConsumer set) {
        slider(name + " red", x, y, w, 0, 255, (get.getAsInt() >> 16) & 255, v -> set.accept((get.getAsInt() & 0x00FFFF) | (v.intValue() << 16)));
        slider(name + " green", x, y + 24, w, 0, 255, (get.getAsInt() >> 8) & 255, v -> set.accept((get.getAsInt() & 0xFF00FF) | (v.intValue() << 8)));
        slider(name + " blue", x, y + 48, w, 0, 255, get.getAsInt() & 255, v -> set.accept((get.getAsInt() & 0xFFFF00) | v.intValue()));
    }

    void renderTab(Config.Settings s, int x, int y, int w) {
        addDrawableChild(ButtonWidget.builder(Text.literal("FPS boost: " + s.boost), b -> {
            s.boost = BOOST[(Arrays.asList(BOOST).indexOf(s.boost) + 1) % 3];
            Boost.apply(s.boost);
            Config.save();
            b.setMessage(Text.literal("FPS boost: " + s.boost));
        }).dimensions(x, y, w, 20).build());
        toggle("Entity culling", x, y + 24, w, () -> s.entityCull, v -> s.entityCull = v);
        slider("Cull distance (blocks)", x, y + 48, w, 16, 128, s.cullDist, v -> s.cullDist = v.intValue());
        note = "Boost edits your video settings; Off restores them.";
        noteY = y + 78;
    }

    void profiles(Config.Settings s, int x, int y, int w) {
        field = new TextFieldWidget(textRenderer, x, y, 230, 20, Text.literal("Name"));
        field.setSuggestion("Profile name");
        field.setChangedListener(t -> field.setSuggestion(t.isEmpty() ? "Profile name" : ""));
        addDrawableChild(field);
        addDrawableChild(ButtonWidget.builder(Text.literal("Save new"), b -> {
            String n = field.getText().trim();
            if (n.isEmpty() || Config.I.profiles.containsKey(n)) return;
            Config.Settings copy = Config.G.fromJson(Config.G.toJson(s), Config.Settings.class);
            Config.I.profiles.put(n, copy);
            Config.I.active = n;
            Config.save();
            clearAndInit();
        }).dimensions(x + 236, y, 104, 20).build());
        int i = 0;
        for (String n : new ArrayList<>(Config.I.profiles.keySet())) {
            if (i >= 6) break;
            int yy = y + 28 + i * 24;
            addDrawableChild(ButtonWidget.builder(Text.literal((n.equals(Config.I.active) ? "> " : "") + n), b -> {
                Config.I.active = n;
                Boost.apply(Config.cur().boost);
                Config.save();
                clearAndInit();
            }).dimensions(x, yy, 304, 20).build());
            ButtonWidget del = ButtonWidget.builder(Text.literal("X"), b -> {
                Config.I.profiles.remove(n);
                if (n.equals(Config.I.active)) Config.I.active = Config.I.profiles.keySet().iterator().next();
                Config.save();
                clearAndInit();
            }).dimensions(x + 310, yy, 30, 20).build();
            del.active = Config.I.profiles.size() > 1;
            addDrawableChild(del);
            i++;
        }
    }

    void custom(Config.Settings s, int x, int y, int w) {
        field = new TextFieldWidget(textRenderer, x, y, 170, 20, Text.literal("Name"));
        field.setSuggestion("Setting name");
        field.setChangedListener(t -> field.setSuggestion(t.isEmpty() ? "Setting name" : ""));
        addDrawableChild(field);
        addCustom("Toggle", "toggle", x + 174, y, s);
        addCustom("Slider", "slider", x + 258, y, s);
        for (int i = 0; i < Math.min(6, s.custom.size()); i++) {
            int n = i, yy = y + 28 + i * 24;
            Config.Custom c = s.custom.get(i);
            if (c.type.equals("toggle")) {
                addDrawableChild(ButtonWidget.builder(Text.literal(c.name + ": " + (c.value > 0 ? "ON" : "OFF")), b -> {
                    c.value = c.value > 0 ? 0 : 1;
                    b.setMessage(Text.literal(c.name + ": " + (c.value > 0 ? "ON" : "OFF")));
                }).dimensions(x, yy, 304, 20).build());
            } else {
                slider(c.name, x, yy, 304, 0, 100, c.value, v -> c.value = v);
            }
            addDrawableChild(ButtonWidget.builder(Text.literal("X"), b -> { s.custom.remove(n); clearAndInit(); })
                    .dimensions(x + 310, yy, 30, 20).build());
        }
    }

    void addCustom(String label, String type, int x, int y, Config.Settings s) {
        addDrawableChild(ButtonWidget.builder(Text.literal(label), b -> {
            String n = field.getText().trim();
            if (n.isEmpty()) return;
            Config.Custom c = new Config.Custom();
            c.name = n; c.type = type; c.value = type.equals("slider") ? 50 : 0;
            s.custom.add(c);
            clearAndInit();
        }).dimensions(x, y, 80, 20).build());
    }

    /* ---------- widgets ---------- */

    void toggle(String label, int x, int y, int w, BooleanSupplier get, Consumer<Boolean> set) {
        addDrawableChild(ButtonWidget.builder(txt(label, get.getAsBoolean()), b -> {
            set.accept(!get.getAsBoolean());
            b.setMessage(txt(label, get.getAsBoolean()));
        }).dimensions(x, y, w, 20).build());
    }

    static Text txt(String l, boolean on) { return Text.literal(l + ": " + (on ? "ON" : "OFF")); }

    void slider(String label, int x, int y, int w, double min, double max, double cur, Consumer<Double> set) {
        addDrawableChild(new Slider(x, y, w, label, min, max, cur, set));
    }

    static class Slider extends SliderWidget {
        final String label; final double min, max; final Consumer<Double> set;
        Slider(int x, int y, int w, String label, double min, double max, double cur, Consumer<Double> set) {
            super(x, y, w, 20, Text.empty(), Math.max(0, Math.min(1, (cur - min) / (max - min))));
            this.label = label; this.min = min; this.max = max; this.set = set;
            updateMessage();
        }
        double val() { return min + (max - min) * value; }
        @Override protected void updateMessage() { setMessage(Text.literal(label + ": " + Math.round(val()))); }
        @Override protected void applyValue() { set.accept(val()); }
    }

    /* ---------- drawing ---------- */

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float d) {
        super.renderBackground(c, mx, my, d);
        Config.Settings s = Config.cur();
        int x = px(), y = py();
        c.fill(x, y, x + 360, y + 250, 0xE60B0D14);
        c.fill(x, y, x + 360, y + 2, 0xFF000000 | s.accent);
        c.fill(x, y + 248, x + 360, y + 250, 0xFF000000 | s.accent2);
        c.drawTextWithShadow(textRenderer, "NOVA CLIENT  |  profile: " + Config.I.active, x + 10, y + 8, 0xFF000000 | s.accent);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        super.render(c, mx, my, d);
        for (int[] a : sw) {
            c.fill(a[0], a[1], a[0] + 6, a[1] + 6, 0xFF000000 | a[2]);
            c.fill(a[0], a[1] + 6, a[0] + 6, a[1] + 12, 0xFF000000 | a[3]);
        }
        if (!note.isEmpty()) c.drawTextWithShadow(textRenderer, note, px() + 10, noteY, 0xFFAAAAAA);
    }

    @Override
    public void close() {
        Config.save();
        client.setScreen(parent);
    }
}
