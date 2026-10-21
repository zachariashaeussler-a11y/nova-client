package gg.nova.client;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.text.Text;
import org.lwjgl.glfw.GLFW;

import java.util.List;

/** Drag HUD modules around with the mouse. Click a module to select it, then use Scale -/+. */
public class HudEditor extends Screen {
    final Screen parent;
    String sel, drag;
    int offX, offY;
    boolean wasDown;

    public HudEditor(Screen parent) { super(Text.literal("HUD layout")); this.parent = parent; }

    @Override
    protected void init() {
        int y = height - 26, x = width / 2 - 159;
        addDrawableChild(ButtonWidget.builder(Text.literal("Done"), b -> close()).dimensions(x, y, 80, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Reset layout"), b -> {
            Config.Settings s = Config.cur();
            for (String n : NovaClient.HUDMODS) { Config.Mod m = s.mod(n); m.fx = -1f; m.fy = -1f; m.scale = 100; }
        }).dimensions(x + 86, y, 100, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Scale -"), b -> {
            if (sel != null) { Config.Mod m = Config.cur().mod(sel); m.scale = Math.max(40, m.scale - 10); }
        }).dimensions(x + 192, y, 60, 20).build());
        addDrawableChild(ButtonWidget.builder(Text.literal("Scale +"), b -> {
            if (sel != null) { Config.Mod m = Config.cur().mod(sel); m.scale = Math.min(250, m.scale + 10); }
        }).dimensions(x + 258, y, 60, 20).build());
    }

    @Override
    public void renderBackground(DrawContext c, int mx, int my, float d) {
        c.fill(0, 0, width, height, 0x80000000);
    }

    @Override
    public void render(DrawContext c, int mx, int my, float d) {
        super.render(c, mx, my, d);
        Config.Settings s = Config.cur();
        List<NovaClient.Box> bs = NovaClient.boxes(client, s, width, height);

        // mouse state is polled directly, so no Minecraft input API is needed
        boolean down = GLFW.glfwGetMouseButton(GLFW.glfwGetCurrentContext(), GLFW.GLFW_MOUSE_BUTTON_LEFT) == GLFW.GLFW_PRESS;
        if (down && !wasDown && my < height - 34) {
            drag = null;
            for (int i = bs.size() - 1; i >= 0; i--) {
                NovaClient.Box b = bs.get(i);
                if (mx >= b.x && mx <= b.x + b.w && my >= b.y && my <= b.y + b.h) {
                    drag = b.name; sel = b.name; offX = mx - b.x; offY = my - b.y;
                    break;
                }
            }
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
                if (ny + b.h > height - 40) ny = Math.max(4, height - 40 - b.h);
                if (Math.abs(nx + b.w / 2 - width / 2) < 4) nx = width / 2 - b.w / 2;
                if (Math.abs(ny + b.h / 2 - height / 2) < 4) ny = height / 2 - b.h / 2;
                Config.Mod m = s.mod(drag);
                m.fx = nx / (float) width;
                m.fy = ny / (float) height;
                b.x = nx; b.y = ny;
            }
        }

        for (int i = 0; i < bs.size(); i++) {
            NovaClient.Box b = bs.get(i);
            NovaClient.drawBox(c, client, s, b, i, bs.size(), true);
            boolean on = b.name.equals(sel);
            NovaClient.outline(c, b.x - 1, b.y - 1, b.w + 2, b.h + 2, on ? (0xFF000000 | s.accent) : 0x80FFFFFF);
            if (on) c.drawTextWithShadow(textRenderer, b.name + "  " + s.mod(b.name).scale + "%", b.x, Math.max(2, b.y - 11), 0xFFFFFFFF);
        }
        String hint = "Drag modules to move them. Click one, then use Scale -/+ to resize.";
        c.drawTextWithShadow(textRenderer, hint, width / 2 - textRenderer.getWidth(hint) / 2, 8, 0xFFFFFFFF);
        if (bs.isEmpty()) {
            String none = "No HUD modules are on. Turn some on in the Modules tab.";
            c.drawTextWithShadow(textRenderer, none, width / 2 - textRenderer.getWidth(none) / 2, height / 2, 0xFFAAAAAA);
        }
    }

    @Override
    public void close() {
        Config.save();
        client.setScreen(parent);
    }
}
