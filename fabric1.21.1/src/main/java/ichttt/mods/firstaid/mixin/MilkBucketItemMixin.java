/*
 * FirstAid
 * Copyright (C) 2017-2024
 */

package ichttt.mods.firstaid.mixin;

import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.MilkBucketItem;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Milk clears vanilla potion effects; also clear FirstAid morphine model state / pending activation.
 */
@Mixin(MilkBucketItem.class)
public class MilkBucketItemMixin {
    @Inject(method = "finishUsingItem", at = @At("RETURN"))
    private void firstaid$clearPainSuppressants(ItemStack stack, Level level, LivingEntity entity, CallbackInfoReturnable<ItemStack> cir) {
        if (level.isClientSide() || !(entity instanceof Player player)) {
            return;
        }
        // Always clear after milk finishes — stack may already be the empty bucket at RETURN.
        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        if (damageModel instanceof PlayerDamageModel playerDamageModel) {
            playerDamageModel.clearPainSuppressants(player);
        }
    }
}
