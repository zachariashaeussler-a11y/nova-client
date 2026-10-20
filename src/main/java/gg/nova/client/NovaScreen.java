package gg.nova.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;

public class NovaScreen extends Screen {
    static final String[] TABS = {"Visuals", "Themes", "Render", "Profiles", "Custom"};
    static final String[] CORNERS = {"Top left", "Top right", "Bottom left", "Bottom right"};
    static final String[] BOOST = {"Off", "Balanced", "Max"};
    static final Object[][] THEMES = {
            {"Neon", 0x7C5CFF, 0x00E5FF}, {"Ember", 0xFF6A3D, 0xFFC857}, {"Forest", 0x3DDC97, 0xB6F09C},
            {"Ocean", 0x2F8CFF, 0x5EF2D6}, {"Rose", 0xFF4D8D, 0xFFB3D1}, {"Mono", 0xFFFFFF, 0x8A8A8A}};
    static int tab = 0;

    final Screen parent;
    TextFieldWidget field;
    String note = "";
    int noteY;

    public NovaScreen(Screen parent) { super(Text.literal("NOVA Client")); this.parent = parent; }

    int px() { return width / 2 - 180; }
    int py() { return height / 2 - 125; }

    @Override
    protected void init() {
        Config.Settings s = Config.cur();
        int x = px() + 10, y = py() + 52, w = 340;
        note = "";
        for (int i = 0; i < TABS.length; i++) {
            int n = i;
            addDrawableChild(ButtonWidget.builder(Text.literal(TABS[i]), b -> { tab = n; clearAndInit(); })
                    .dimensions(px() + 10 + i * 68, py() + 24, 66, 20).build());
        }
        switch (tab) {
            case 0 -> visuals(s, x, y, w);
            case 1 -> themes(s, x, y, w);
            case 2 -> renderTab(s, x, y, w);
            case 3 -> profiles(s, x, y, w);
            default -> custom(s, x, y, w);
        }
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close())
                .dimensions(width / 2 - 40, py() + 226, 80, 20).build());
    }

    /* ---------- tabs ---------- */

    void visuals(Config.Settings s, int x, int y, int w) {
        toggle("HUD overlay", x, y, w, () -> s.hud, v -> s.hud = v);
        toggle("Show FPS", x, y + 24, w, () -> s.showFps, v -> s.showFps = v);
        toggle("HUD background", x, y + 48, w, () -> s.hudBg, v -> s.hudBg = v);
        slider("HUD scale %", x, y + 72, w, 50, 200, s.hudScale, v -> s.hudScale = v.intValue());
        addDrawableChild(ButtonWidget.builder(Text.literal("HUD corner: " + CORNERS[s.corner]), b -> {
            s.corner = (s.corner + 1) % 4;
            b.setMessage(Text.literal("HUD corner: " + CORNERS[s.corner]));
        }).dimensions(x, y + 96, w, 20).build());
    }

    void themes(Config.Settings s, int x, int y, int w) {
        for (int i = 0; i < THEMES.length; i++) {
            Object[] t = THEMES[i];
            addDrawableChild(ButtonWidget.builder(Text.literal((String) t[0]), b -> {
                s.theme = (String) t[0]; s.accent = (int) t[1]; s.accent2 = (int) t[2]; clearAndInit();
            }).dimensions(x + (i % 3) * 115, y + (i / 3) * 24, 110, 20).build());
        }
        slider("Accent red", x, y + 60, w, 0, 255, (s.accent >> 16) & 255, v -> setAccent(s, 16, v.intValue()));
        slider("Accent green", x, y + 84, w, 0, 255, (s.accent >> 8) & 255, v -> setAccent(s, 8, v.intValue()));
        slider("Accent blue", x, y + 108, w, 0, 255, s.accent & 255, v -> setAccent(s, 0, v.intValue()));
        note = "Current theme: " + s.theme;
        noteY = y + 136;
    }

    void setAccent(Config.Settings s, int shift, int v) {
        s.accent = (s.accent & ~(255 << shift)) | (v << shift);
        s.theme = "Custom";
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
        slider("FPS when unfocused (0 = off)", x, y + 72, w, 0, 60, s.unfocusedFps, v -> s.unfocusedFps = v.intValue());
        note = "Boost edits your video settings; Off restores them.";
        noteY = y + 100;
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
        if (!note.isEmpty()) c.drawTextWithShadow(textRenderer, note, px() + 10, noteY, 0xFFAAAAAA);
    }

    @Override
    public void close() {
        Config.save();
        client.setScreen(parent);
    }
}
