package gg.nova.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Drag HUD modules around with the mouse. Click a module to select it, then use Scale -/+. */
public class HudEditor extends Screen {
    final Screen parent;
    String sel, drag;
    int offX, offY;
    boolean wasDown = true;

    public HudEditor(Screen parent) { super(Text.literal("HUD layout")); this.parent = parent; }

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float d) {
        c.fill(0, 0, width, height, 0x80000000);
    }

    boolean button(DrawContext c, Config.Settings s, int mx, int my, boolean press, int x, int y, int w, String t, boolean primary) {
        boolean hov = mx >= x && mx < x + w && my >= y && my < y + 20;
        int bg = primary ? (hov ? 0xFF000000 | NovaClient.lerp(s.accent, 0xFFFFFF, 0.2f) : 0xFF000000 | s.accent)
                : (hov ? 0xFF000000 | NovaClient.lerp(0x262C45, s.accent, 0.25f) : 0xFF222842);
        NovaClient.rbox(c, x, y, w, 20, 5, bg);
        c.drawText(textRenderer, t, x + (w - textRenderer.getWidth(t)) / 2, y + 6, primary ? 0xFF0E1018 : 0xFFE8EBF7, false);
        return hov && press;
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        renderBackground(c, mx, my, d);
        Config.Settings s = Config.cur();
        List<NovaClient.Box> bs = NovaClient.boxes(client, s, width, height);

        // mouse state is polled directly, so no Minecraft input API is needed
        long win = GLFW.glfwGetCurrentContext();
        boolean down = GLFW.glfwGetMouseButton(win, GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        boolean press = down && !wasDown;

        // bottom bar
        int bx = width / 2 - 190, by = height - 40;
        for (int k = 6; k >= 1; k--) NovaClient.rbox(c, bx - k, by - k, 380 + 2 * k, 32 + 2 * k, 9 + k, 0x08000000 | s.accent);
        NovaClient.rbox(c, bx, by, 380, 32, 9, 0xF00D1019);
        boolean barHit = false;
        if (button(c, s, mx, my, press, bx + 8, by + 6, 80, "Done", true)) { barHit = true; later = true; }
        if (button(c, s, mx, my, press, bx + 94, by + 6, 104, "Reset layout", false)) {
            barHit = true;
            for (String n : NovaClient.HUDMODS) { Config.Mod m = s.mod(n); m.fx = -1f; m.fy = -1f; m.scale = 100; }
        }
        if (button(c, s, mx, my, press, bx + 204, by + 6, 80, "Smaller", false)) {
            barHit = true;
            if (sel != null) { Config.Mod m = s.mod(sel); m.scale = Math.max(40, m.scale - 10); }
        }
        if (button(c, s, mx, my, press, bx + 290, by + 6, 80, "Bigger", false)) {
            barHit = true;
            if (sel != null) { Config.Mod m = s.mod(sel); m.scale = Math.min(250, m.scale + 10); }
        }

        if (press && !barHit && my < height - 46) {
            drag = null;
            for (int i = bs.size() - 1; i >= 0; i--) {
                NovaClient.Box b = bs.get(i);
                if (mx >= b.x && mx <= b.x + b.w && my >= b.y && my <= b.y + b.h) {
                    drag = b.name; sel = b.name; offX = mx - b.x; offY = my - b.y;
                    break;
                }
            }
            if (drag == null) sel = null;
        }
        if (!down) drag = null;
        wasDown = down;

        if (drag != null) {
            for (NovaClient.Box b : bs) {
                if (!b.name.equals(drag)) continue;
                int nx = Math.max(0, Math.min(width - b.w, mx - offX));
                int ny = Math.max(0, Math.min(height - b.h, my - offY));
                if (nx < 6) nx = 4;
                if (nx + b.w > width - 6) nx = width - b.w - 4;
                if (ny < 6) ny = 4;
                if (ny + b.h > height - 50) ny = Math.max(4, height - 50 - b.h);
                boolean cx = Math.abs(nx + b.w / 2 - width / 2) < 4, cyy = Math.abs(ny + b.h / 2 - height / 2) < 4;
                if (cx) nx = width / 2 - b.w / 2;
                if (cyy) ny = height / 2 - b.h / 2;
                Config.Mod m = s.mod(drag);
                m.fx = nx / (float) width;
                m.fy = ny / (float) height;
                b.x = nx; b.y = ny;
            }
        }

        // center guides while dragging
        if (drag != null) {
            c.fill(width / 2, 0, width / 2 + 1, height - 50, 0x20FFFFFF);
            c.fill(0, height / 2, width, height / 2 + 1, 0x20FFFFFF);
        }

        for (int i = 0; i < bs.size(); i++) {
            NovaClient.Box b = bs.get(i);
            NovaClient.drawBox(c, client, s, b, i, bs.size(), true);
            boolean on = b.name.equals(sel);
            NovaClient.outline(c, b.x - 1, b.y - 1, b.w + 2, b.h + 2, on ? (0xFF000000 | s.accent) : 0x55FFFFFF);
            if (on) {
                String tag = b.name + "  " + s.mod(b.name).scale + "%";
                int tw = textRenderer.getWidth(tag) + 12, ty = Math.max(2, b.y - 17);
                NovaClient.rbox(c, b.x, ty, tw, 14, 5, 0xFF000000 | s.accent);
                c.drawText(textRenderer, tag, b.x + 6, ty + 3, 0xFF0E1018, false);
            }
        }

        String hint = "Drag a module to move it. Click one to select it, then use Smaller / Bigger.";
        c.drawText(textRenderer, hint, width / 2 - textRenderer.getWidth(hint) / 2, 10, 0xFFE8EBF7, true);
        if (bs.isEmpty()) {
            String none = "No HUD modules are on. Turn some on in the Modules tab.";
            c.drawText(textRenderer, none, width / 2 - textRenderer.getWidth(none) / 2, height / 2, 0xFF8B91AB, true);
        }
        if (later) { later = false; close(); }
    }

    boolean later;

    @Override
    public void close() {
        Config.save();
        client.setScreen(parent);
    }
}
