package gg.nova.client.mixin;

import gg.nova.client.Config;
import net.minecraft.client.MinecraftClient;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Caps FPS while the game window is in the background. */
@Mixin(MinecraftClient.class)
public abstract class MinecraftClientMixin {
    @Shadow public abstract boolean isWindowFocused();

    @Inject(method = "getFramerateLimit", at = @At("HEAD"), cancellable = true)
    private void nova$unfocused(CallbackInfoReturnable<Integer> cir) {
        int f = Config.cur().unfocusedFps;
        if (f > 0 && !isWindowFocused()) cir.setReturnValue(f);
    }
}
