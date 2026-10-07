package com.snailtools.shoplogger.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import com.snailtools.shoplogger.WorldHighlights;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.gizmos.Gizmos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Draws WorldHighlights into the world. Plain vanilla hooks, so the same mixin
 * works on Fabric and NeoForge:
 *  - right before the frame's gizmos are finalized, add ours to the per-frame
 *    collector (outlines that show through walls);
 *  - after the block entities are submitted, add beacon beams with the same
 *    camera-relative pose vanilla beacons get.
 */
@Mixin(LevelRenderer.class)
public abstract class LevelRendererMixin {

    @Shadow
    public abstract Gizmos.TemporaryCollection collectPerFrameRenderThreadGizmos();

    @Inject(method = "finalizeGizmoCollection", at = @At("HEAD"))
    private void shoplogger$addHighlights(CallbackInfo ci) {
        try (Gizmos.TemporaryCollection ignored = collectPerFrameRenderThreadGizmos()) {
            WorldHighlights.emitGizmos();
        } catch (RuntimeException e) {
            // never let a highlight break the frame
        }
    }

    @Inject(method = "submitBlockEntities", at = @At("TAIL"))
    private void shoplogger$addBeams(PoseStack poseStack, LevelRenderState state, SubmitNodeCollector collector, CallbackInfo ci) {
        try {
            WorldHighlights.submitBeams(poseStack, collector, state.cameraRenderState.pos);
            com.snailtools.shoplogger.StockHolograms.submit(poseStack, collector, state.cameraRenderState.pos);
        } catch (RuntimeException e) {
            // same: a beam is never worth a crash
        }
    }
}
