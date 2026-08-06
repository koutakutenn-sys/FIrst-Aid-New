package ichttt.mods.firstaid.mixin.client;

import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.Identifier;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(GameRenderer.class)
public interface GameRendererEffectAccessor {
    @Invoker("setPostEffect")
    void firstaid$setPostEffect(Identifier id);

    @Invoker("clearPostEffect")
    void firstaid$clearPostEffect();
}
