/*
 * FirstAid
 * Copyright (C) 2017-2024
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package ichttt.mods.firstaid.mixin.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Previously forced a flat corpse orientation while downed.
 * Downed players now rely on forced {@code Pose.SWIMMING} so vanilla crawl/swim
 * body orientation and movement animation are preserved.
 */
@Mixin(LivingEntityRenderer.class)
public abstract class LivingEntityRendererMixin<T extends LivingEntity, M extends net.minecraft.client.model.EntityModel<T>> {
    @Inject(method = "setupRotations(Lnet/minecraft/world/entity/LivingEntity;Lcom/mojang/blaze3d/vertex/PoseStack;FFF)V", at = @At("HEAD"), cancellable = true)
    private void firstaid$setupRotations(T entity, PoseStack poseStack, float bob, float bodyRot, float partialTick, CallbackInfo ci) {
        // Intentionally empty: do not cancel vanilla swimming/crawl rotations.
    }
}
