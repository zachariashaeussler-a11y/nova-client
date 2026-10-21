package gg.nova.client.mixin;

import gg.nova.client.NovaClient;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.entity.EntityRenderer;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Skips rendering of far-away entities (never players or very large mobs). */
@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity> {
    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true, require = 0)
    private void nova$cull(T entity, Frustum frustum, double x, double y, double z, CallbackInfoReturnable<Boolean> cir) {
        if (!NovaClient.cullOn || entity instanceof PlayerEntity || entity.getWidth() > 3f) return;
        if (entity.squaredDistanceTo(x, y, z) > NovaClient.cullSq) cir.setReturnValue(false);
    }
}
