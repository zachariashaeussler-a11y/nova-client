package gg.nova.client;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class NovaClient implements ClientModInitializer {
    public static KeyBinding menuKey;

    @Override
    @SuppressWarnings("deprecation")
    public void onInitializeClient() {
        Config.load();
        KeyBinding.Category category = KeyBinding.Category.create(Identifier.of("novaclient", "main"));
        menuKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.novaclient.menu", InputUtil.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, category));
        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (menuKey.wasPressed()) mc.setScreen(new NovaScreen(mc.currentScreen));
        });
        HudRenderCallback.EVENT.register((ctx, tick) -> hud(ctx));
    }

    static void hud(DrawContext ctx) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Config.Settings s = Config.cur();
        if (!s.hud || mc.options.hudHidden) return;
        var tr = mc.textRenderer;
        List<String> lines = new ArrayList<>();
        lines.add("NOVA | " + Config.I.active);
        if (s.showFps) lines.add("FPS " + mc.getCurrentFps());
        int w = 0;
        for (String l : lines) w = Math.max(w, tr.getWidth(l));
        w += 10;
        int h = lines.size() * 10 + 4;
        float sc = s.hudScale / 100f;
        int vw = (int) (ctx.getScaledWindowWidth() / sc), vh = (int) (ctx.getScaledWindowHeight() / sc);
        int x = (s.corner % 2 == 0) ? 4 : vw - w - 4;
        int y = (s.corner < 2) ? 4 : vh - h - 4;
        // 1.21.6+ uses a 2D matrix stack for GUI drawing
        ctx.getMatrices().pushMatrix();
        ctx.getMatrices().scale(sc, sc);
        if (s.hudBg) ctx.fill(x, y, x + w, y + h, 0x90000000);
        ctx.fill(x, y, x + 2, y + h, 0xFF000000 | s.accent);
        for (int i = 0; i < lines.size(); i++)
            ctx.drawTextWithShadow(tr, lines.get(i), x + 6, y + 3 + i * 10, 0xFF000000 | (i == 0 ? s.accent : s.accent2));
        ctx.getMatrices().popMatrix();
    }
}
