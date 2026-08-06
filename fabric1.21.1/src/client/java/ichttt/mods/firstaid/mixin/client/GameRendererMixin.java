package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.ClientEventHandler;
import ichttt.mods.firstaid.client.SuppressionFeedbackController;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(GameRenderer.class)
public abstract class GameRendererMixin {
    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void firstaid$getFov(Camera camera, float partialTick, boolean useFovSetting, CallbackInfoReturnable<Double> cir) {
        SuppressionFeedbackController controller = ClientEventHandler.getSuppressionFeedbackController();
        double baseFov = cir.getReturnValue();
        float adjustedFov = controller.applyFov((float) baseFov);
        cir.setReturnValue((double) adjustedFov);
    }

    @Inject(method = "renderLevel", at = @At("RETURN"))
    private void firstaid$afterRenderLevel(DeltaTracker deltaTracker, CallbackInfo ci) {
        float partialTick = deltaTracker.getGameTimeDeltaPartialTick(false);
        ClientEventHandler.getPainVisualEffectsController().processFrame(Minecraft.getInstance(), partialTick);
    }
}
