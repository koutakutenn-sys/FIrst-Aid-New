package ichttt.mods.firstaid.mixin.client;

import net.minecraft.client.model.PlayerModel;
import net.minecraft.world.entity.LivingEntity;
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
public abstract class PlayerModelMixin {

    @Inject(
            method = "setupAnim(Lnet/minecraft/world/entity/LivingEntity;FFFFF)V",
            at = @At("TAIL")
    )
    private void firstaid$setupAnim(LivingEntity entity, float limbSwing, float limbSwingAmount, float ageInTicks, float netHeadYaw, float headPitch, CallbackInfo ci) {
        // Intentionally empty: keep vanilla swimming/crawl animation while downed.
    }
}
