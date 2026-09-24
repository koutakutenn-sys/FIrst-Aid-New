package ichttt.mods.firstaid.mixin.client;

import ichttt.mods.firstaid.client.ClientEventHandler;
import ichttt.mods.firstaid.client.HealingSoundController;
import ichttt.mods.firstaid.client.SuppressionFeedbackController;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.client.sounds.SoundManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(SoundManager.class)
public abstract class SoundManagerMixin {
   @Inject(method = {"stop()V", "destroy()V", "emergencyShutdown()V"}, at = @At("HEAD"))
   private void firstaid$clearHealingSounds(CallbackInfo ci) {
      HealingSoundController.clear((SoundManager)(Object)this);
   }

   @ModifyVariable(method = "play", at = @At("HEAD"), argsOnly = true)
   private SoundInstance firstaid$modifySound(SoundInstance sound) {
      SuppressionFeedbackController controller = ClientEventHandler.getSuppressionFeedbackController();
      SoundInstance modified = controller.maybeMuffle(sound);
      return modified == null ? sound : modified;
   }
}
