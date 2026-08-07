/*
 * FirstAid
 * Copyright (C) 2017-2024
 */

package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import ichttt.mods.firstaid.api.damagesystem.AbstractDamageablePart;
import ichttt.mods.firstaid.api.damagesystem.AbstractPlayerDamageModel;
import ichttt.mods.firstaid.common.RegistryObjects;
import ichttt.mods.firstaid.common.damagesystem.PlayerDamageModel;
import ichttt.mods.firstaid.common.util.CommonUtils;
import ichttt.mods.firstaid.mixin.client.GameRendererEffectAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Classic visuals: continuous composite post (color grade + radial blur),
 * hit red pulse, morphine sat vs suppression desat (net Amount),
 * gray-white suppression rim overlay.
 */
public final class PainVisualEffectsController {
    private static final ResourceLocation COMPOSITE_EFFECT = ResourceLocation.fromNamespaceAndPath(FirstAid.MODID, "shaders/post/composite.json");
    private static final float PAIN_SHADER_PEAK = 0.16F;
    private static final float SUPPRESSION_BLUR_PEAK = 0.11F;
    /** Pain level 2 ("moderate") → 2/5 blur strength; adrenaline rush uses this. */
    private static final float ADRENALINE_MODERATE_BLUR = 0.40F;
    private static final float MORPHINE_SAT_PEAK = 0.72F;
    private static final float SUPPRESSION_DESAT_PEAK = 1.05F;
    private static final float HIT_PULSE_MAX = 1.55F;
    private static final float HIT_PULSE_DECAY = 0.014F;
    private static final float HIT_PULSE_SUPPRESSED_DECAY = 0.06F;

    private float painStrength;
    private float morphineStrength;
    private float suppressionStrength;
    private float hitPulse;
    private float lastMissingHealth = -1.0F;
    private int lastHurtTime = -1;
    private boolean compositeActive;
    private int loadFailCooldown;
    private int loadFailStreak;

    public void tick(Minecraft client) {
        Player player = client.player;
        if (player == null || !player.isAlive() || !FirstAid.isSynced) {
            painStrength = approach(painStrength, 0.0F, 0.035F);
            morphineStrength = approach(morphineStrength, 0.0F, 0.03F);
            suppressionStrength = approach(suppressionStrength, 0.0F, 0.025F);
            hitPulse = Math.max(0.0F, hitPulse - HIT_PULSE_DECAY * 2.0F);
            lastMissingHealth = -1.0F;
            updateEffect(client);
            return;
        }

        AbstractPlayerDamageModel damageModel = CommonUtils.getDamageModel(player);
        PlayerDamageModel model = damageModel instanceof PlayerDamageModel m ? m : null;

        boolean hasMorphine = player.hasEffect(RegistryObjects.MORPHINE_EFFECT)
                || (model != null && model.getMorphineTicks() > 0);
        boolean hasPainkiller = player.hasEffect(RegistryObjects.PAINKILLER_EFFECT);
        boolean painSuppressed = hasMorphine || hasPainkiller;

        tickHitPulse(player, model, painSuppressed);

        float targetPain = 0.0F;
        if (!painSuppressed && model != null && FirstAid.enablePainBlur) {
            int painLevel = Math.max(0, model.getPainLevel());
            boolean injured = painLevel > 0 || hasVisibleInjury(model);
            if (injured) {
                float levelStrength = painLevel <= 0 ? 0.35F : Mth.clamp(painLevel / 5.0F, 0.30F, 1.0F);
                targetPain = Math.max(levelStrength * 1.0F, model.getPainVisualStrength() * 0.95F);
            }
            if (model.isUnconscious()) {
                targetPain = Math.max(targetPain, 0.55F);
            }
            if (model.isWithdrawalEpisodeActive()) {
                targetPain = Math.max(targetPain, 0.75F);
            }
            targetPain = Math.max(targetPain, Mth.clamp(hitPulse * 0.85F, 0.0F, 1.0F));
        }
        // Adrenaline injector combat rush: moderate blur even while painkillers suppress injury pain.
        if (FirstAid.enablePainBlur && isAdrenalineRushActive(player)) {
            targetPain = Math.max(targetPain, ADRENALINE_MODERATE_BLUR);
        }

        float targetMorphine = 0.0F;
        if (hasMorphine) {
            float ratio = 0.0F;
            if (model != null) {
                ratio = model.getMorphineRemainingRatio();
                // Model ticks/max can lag the mob effect by a sync; keep sat visible.
                if (ratio <= 0.0F && (model.getMorphineTicks() > 0 || player.hasEffect(RegistryObjects.MORPHINE_EFFECT))) {
                    ratio = 1.0F;
                }
            } else {
                ratio = 1.0F;
            }
            targetMorphine = smoothstep(Mth.clamp(ratio, 0.0F, 1.0F));
        }

        float modelSuppression = model == null ? 0.0F : model.getSuppressionIntensity();
        float feedbackSuppression = ClientEventHandler.getSuppressionFeedbackController().getVisualStrength();
        float suppressionScale = FirstAid.lowSuppressionEnabled ? FirstAid.lowSuppressionMultiplier : 1.0F;
        float targetSuppression = Math.max(modelSuppression, feedbackSuppression) * suppressionScale;

        painStrength = approach(painStrength, targetPain, targetPain > painStrength ? 0.08F : 0.03F);
        morphineStrength = approach(morphineStrength, targetMorphine, targetMorphine > morphineStrength ? 0.06F : 0.025F);
        suppressionStrength = approach(suppressionStrength, targetSuppression, targetSuppression > suppressionStrength ? 0.08F : 0.018F);
        updateEffect(client);
    }

    public void clear(Minecraft client) {
        painStrength = 0.0F;
        morphineStrength = 0.0F;
        suppressionStrength = 0.0F;
        hitPulse = 0.0F;
        lastMissingHealth = -1.0F;
        lastHurtTime = -1;
        shutdown(client);
        compositeActive = false;
        loadFailCooldown = 0;
        loadFailStreak = 0;
    }

    public float getPainStrength() { return painStrength; }
    public float getMorphineStrength() { return morphineStrength; }
    public float getSuppressionStrength() { return suppressionStrength; }
    public float getHitPulse() { return hitPulse; }

    public float getNetColorAmount() {
        return MORPHINE_SAT_PEAK * morphineStrength - SUPPRESSION_DESAT_PEAK * suppressionStrength;
    }

    public void renderOverlay(GuiGraphics guiGraphics, int width, int height, float pulseTime) {
        if (FirstAid.enablePainVignette && hitPulse > 0.02F) {
            float wave = 0.72F + 0.28F * Mth.sin(pulseTime * 0.42F);
            float intensity = Mth.clamp(hitPulse * wave, 0.0F, 1.35F);
            renderRedPulseVignette(guiGraphics, width, height, intensity);
        }
        if (suppressionStrength > 0.02F) {
            renderSuppressionGrayRim(guiGraphics, width, height, suppressionStrength, pulseTime);
        }
    }

    public void processFrame(Minecraft client, float partialTick) {
        // Re-assert ownership each frame: vanilla may clear postEffect between ticks (F5 / camera).
        updateEffect(client);
    }

    private void tickHitPulse(Player player, PlayerDamageModel model, boolean painSuppressed) {
        float missing = model == null ? 0.0F : sumMissingHealth(model);
        boolean tookHit = false;
        if (lastMissingHealth < 0.0F) {
            lastMissingHealth = missing;
        } else if (missing > lastMissingHealth + 0.04F) {
            float delta = missing - lastMissingHealth;
            float add = Mth.clamp(0.10F + delta * 0.14F, 0.12F, 0.70F);
            hitPulse = Math.min(HIT_PULSE_MAX, hitPulse + add);
            tookHit = true;
        }
        lastMissingHealth = missing;
        int hurtTime = player.hurtTime;
        if (!tookHit && hurtTime > 0 && hurtTime != lastHurtTime && hurtTime >= player.hurtDuration - 1) {
            hitPulse = Math.min(HIT_PULSE_MAX, hitPulse + 0.14F);
            tookHit = true;
        }
        lastHurtTime = hurtTime;
        if (!tookHit) {
            hitPulse = Math.max(0.0F, hitPulse - (painSuppressed ? HIT_PULSE_SUPPRESSED_DECAY : HIT_PULSE_DECAY));
        }
        if (painSuppressed) {
            hitPulse = Math.max(0.0F, hitPulse - HIT_PULSE_SUPPRESSED_DECAY * 0.5F);
        }
    }

    private void updateEffect(Minecraft client) {
        if (client.gameRenderer == null) {
            return;
        }
        if (loadFailCooldown > 0) {
            loadFailCooldown--;
        }

        boolean want = Math.abs(getNetColorAmount()) > 0.02F
                || painStrength > 0.03F
                || (suppressionStrength > 0.04F && FirstAid.enablePainBlur);

        PostChain chain = getPostEffect(client);
        boolean ours = isOurComposite(chain);
        // Keep flag honest: vanilla may clear postEffect (camera change / F5) without telling us.
        compositeActive = ours;

        if (!want) {
            if (ours) {
                shutdown(client);
                compositeActive = false;
            }
            return;
        }

        if (!ours) {
            if (loadFailCooldown > 0) {
                return;
            }
            try {
                ((GameRendererEffectAccessor) client.gameRenderer).firstaid$loadEffect(COMPOSITE_EFFECT);
                chain = getPostEffect(client);
                ours = isOurComposite(chain);
                compositeActive = ours;
                if (!ours) {
                    // loadEffect swallows IO/JSON errors; treat as soft failure and retry later.
                    loadFailStreak = Math.min(loadFailStreak + 1, 8);
                    loadFailCooldown = 20 * loadFailStreak;
                    FirstAid.LOGGER.warn(
                            "FirstAid composite post effect not active after load (streak={}); will retry",
                            loadFailStreak
                    );
                    return;
                }
                loadFailStreak = 0;
                loadFailCooldown = 0;
            } catch (Exception e) {
                compositeActive = false;
                loadFailStreak = Math.min(loadFailStreak + 1, 8);
                loadFailCooldown = 20 * loadFailStreak;
                FirstAid.LOGGER.error("Failed to load firstaid composite post effect", e);
                return;
            }
        }

        applyUniforms(client, chain);
    }

    private void applyUniforms(Minecraft client, PostChain chain) {
        if (chain == null) {
            return;
        }
        PostChainUniforms.setFloat(chain, "Amount", getNetColorAmount());

        float blur = 0.0F;
        if (FirstAid.enablePainBlur) {
            if (painStrength > 0.02F) {
                blur = Math.max(blur, PAIN_SHADER_PEAK * Mth.clamp(0.40F + painStrength * 0.60F, 0.40F, 1.0F));
            }
            if (suppressionStrength > 0.03F) {
                float s = Mth.clamp(suppressionStrength, 0.0F, 1.0F);
                blur = Math.max(blur, SUPPRESSION_BLUR_PEAK * (0.35F + s * 0.65F));
            }
        }
        PostChainUniforms.setFloat(chain, "Strength", blur);
    }

    private static PostChain getPostEffect(Minecraft client) {
        if (client.gameRenderer == null) {
            return null;
        }
        return ((GameRendererEffectAccessor) client.gameRenderer).firstaid$getPostEffect();
    }

    private static boolean isOurComposite(PostChain chain) {
        if (chain == null) {
            return false;
        }
        String name = chain.getName();
        if (name == null || name.isEmpty()) {
            return false;
        }
        // PostChain stores ResourceLocation.toString(), e.g. firstaid:shaders/post/composite.json
        return name.contains("firstaid") && name.contains("composite");
    }

    private void shutdown(Minecraft client) {
        if (client.gameRenderer == null) {
            return;
        }
        try {
            PostChain chain = getPostEffect(client);
            // Only tear down our own chain — do not clobber creeper/spider vision etc.
            if (isOurComposite(chain)) {
                ((GameRendererEffectAccessor) client.gameRenderer).firstaid$shutdownEffect();
            }
        } catch (Exception ignored) {
        }
    }

    private static float sumMissingHealth(PlayerDamageModel model) {
        float total = 0.0F;
        for (AbstractDamageablePart part : model) {
            total += CommonUtils.getVisibleMissingHealth(part);
        }
        return total;
    }

    private static boolean hasVisibleInjury(PlayerDamageModel model) {
        return sumMissingHealth(model) > 0.0F;
    }

    private static void renderRedPulseVignette(GuiGraphics guiGraphics, int width, int height, float intensity) {
        int layers = 8;
        int baseThickness = 18 + Math.round(10.0F * Math.min(1.0F, intensity));
        for (int layer = 0; layer < layers; layer++) {
            float progress = (layer + 1) / (float) layers;
            float falloff = 1.0F - progress;
            int thickness = Math.max(3, Math.round(baseThickness * (0.30F + progress * (1.20F + intensity * 0.90F))));
            int alpha = Math.round((10.0F + 92.0F * intensity) * falloff * falloff);
            if (alpha > 0) {
                fillEdge(guiGraphics, width, height, color(alpha, 170, 12, 12), thickness);
            }
        }
        int wash = Math.round(4.0F + 28.0F * Math.min(1.0F, intensity));
        if (wash > 0) {
            guiGraphics.fill(0, 0, width, height, color(wash, 140, 8, 8));
        }
    }

    private static void renderSuppressionGrayRim(GuiGraphics guiGraphics, int width, int height, float strength, float pulseTime) {
        float s = Mth.clamp(strength, 0.0F, 1.0F);
        float pulse = 0.90F + 0.10F * Mth.sin(pulseTime * 0.38F + 0.6F);
        float intensity = s * pulse;
        int layers = 10;
        int baseThickness = 16 + Math.round(34.0F * s);
        for (int layer = 0; layer < layers; layer++) {
            float progress = (layer + 1) / (float) layers;
            float falloff = 1.0F - progress;
            int thickness = Math.max(4, Math.round(baseThickness * (0.28F + progress * (1.15F + intensity * 1.05F))));
            int alpha = Math.round((14.0F + 130.0F * intensity) * falloff * falloff);
            if (alpha > 0) {
                int r = 188 + Math.round(28.0F * s);
                int g = 192 + Math.round(30.0F * s);
                int b = 200 + Math.round(28.0F * s);
                fillEdge(guiGraphics, width, height, color(Math.min(220, alpha), r, g, b), thickness);
            }
        }
        int wash = Math.round(6.0F + 42.0F * s * s);
        if (wash > 0) {
            guiGraphics.fill(0, 0, width, height, color(wash, 200, 204, 210));
        }
    }

    private static void fillEdge(GuiGraphics guiGraphics, int width, int height, int color, int thickness) {
        guiGraphics.fill(0, 0, width, thickness, color);
        guiGraphics.fill(0, height - thickness, width, height, color);
        guiGraphics.fill(0, thickness, thickness, height - thickness, color);
        guiGraphics.fill(width - thickness, thickness, width, height - thickness, color);
    }

    private static int color(int alpha, int red, int green, int blue) {
        return (alpha & 255) << 24 | (red & 255) << 16 | (green & 255) << 8 | blue & 255;
    }

    private static float approach(float current, float target, float delta) {
        return current < target ? Math.min(target, current + delta) : Math.max(target, current - delta);
    }

    private static float smoothstep(float t) {
        return t * t * (3.0F - 2.0F * t);
    }

    private static boolean isAdrenalineRushActive(Player player) {
        var absorption = player.getEffect(net.minecraft.world.effect.MobEffects.ABSORPTION);
        if (absorption == null || absorption.getAmplifier() < 1) {
            return false;
        }
        return player.hasEffect(net.minecraft.world.effect.MobEffects.DIG_SPEED)
                && player.hasEffect(net.minecraft.world.effect.MobEffects.DAMAGE_BOOST)
                && player.hasEffect(net.minecraft.world.effect.MobEffects.MOVEMENT_SPEED);
    }
}
