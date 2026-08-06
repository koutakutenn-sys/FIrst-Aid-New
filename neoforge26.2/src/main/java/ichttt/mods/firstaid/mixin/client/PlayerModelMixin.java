package ichttt.mods.firstaid.mixin.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Previously froze limbs into a static corpse pose while downed.
 * Downed players now use forced {@code Pose.SWIMMING}, so vanilla swimming/crawl
 * limb animation must remain intact for crawl movement feedback.
 */
@Mixin(PlayerModel.class)
public abstract class PlayerModelMixin extends HumanoidModel<AvatarRenderState> {

    protected PlayerModelMixin(ModelPart root) {
        super(root);
    }

    @Inject(
            method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V",
            at = @At("TAIL")
    )
    private void firstaid$setupAnim(AvatarRenderState renderState, CallbackInfo ci) {
        // Intentionally empty: keep vanilla swimming/crawl animation while downed.
    }
}
