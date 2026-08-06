/*
 * FirstAid
 * Copyright (C) 2017-2024
 */

package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import net.minecraft.client.renderer.EffectInstance;
import net.minecraft.client.renderer.PostChain;
import net.minecraft.client.renderer.PostPass;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;

/**
 * Sets float uniforms on every pass of a classic PostChain (1.20.x / 1.21.1).
 */
public final class PostChainUniforms {
    private static Field passesField;
    private static Field effectField;
    private static Method getUniformMethod;
    private static boolean resolved;
    private static boolean loggedMissing;
    private static boolean loggedOk;

    private PostChainUniforms() {
    }

    public static void setFloat(PostChain chain, String uniformName, float value) {
        if (chain == null) {
            return;
        }
        resolve();
        if (passesField == null || effectField == null) {
            if (!loggedMissing) {
                loggedMissing = true;
                FirstAid.LOGGER.warn("PostChain uniform reflection unavailable; color grade may stick at defaults");
            }
            return;
        }
        try {
            @SuppressWarnings("unchecked")
            List<PostPass> passes = (List<PostPass>) passesField.get(chain);
            if (passes == null) {
                return;
            }
            int applied = 0;
            for (PostPass pass : passes) {
                EffectInstance effect = (EffectInstance) effectField.get(pass);
                if (effect == null) {
                    continue;
                }
                Object uniform = getUniform(effect, uniformName);
                if (uniform == null) {
                    continue;
                }
                // Uniform.set(float) — present on mapped 1.20.1 / 1.21.1
                Method set = uniform.getClass().getMethod("set", float.class);
                set.invoke(uniform, value);
                applied++;
            }
            if (applied > 0 && !loggedOk) {
                loggedOk = true;
                FirstAid.LOGGER.info("PostChain uniforms active (first set {}={})", uniformName, value);
            } else if (applied == 0 && !loggedMissing) {
                // Not fatal: blit passes have neither Amount nor Strength
            }
        } catch (Exception e) {
            if (!loggedMissing) {
                loggedMissing = true;
                FirstAid.LOGGER.warn("Could not set post uniform {}={}", uniformName, value, e);
            }
        }
    }

    private static Object getUniform(EffectInstance effect, String name) {
        try {
            if (getUniformMethod != null) {
                return getUniformMethod.invoke(effect, name);
            }
            return effect.getUniform(name);
        } catch (Exception e) {
            return null;
        }
    }

    private static void resolve() {
        if (resolved) {
            return;
        }
        resolved = true;
        try {
            passesField = PostChain.class.getDeclaredField("passes");
            passesField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            for (Field field : PostChain.class.getDeclaredFields()) {
                if (List.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    passesField = field;
                    break;
                }
            }
        }
        try {
            effectField = PostPass.class.getDeclaredField("effect");
            effectField.setAccessible(true);
        } catch (NoSuchFieldException e) {
            for (Field field : PostPass.class.getDeclaredFields()) {
                if (EffectInstance.class.isAssignableFrom(field.getType())) {
                    field.setAccessible(true);
                    effectField = field;
                    break;
                }
            }
        }
        try {
            getUniformMethod = EffectInstance.class.getMethod("getUniform", String.class);
        } catch (NoSuchMethodException ignored) {
        }
        if (passesField == null || effectField == null) {
            FirstAid.LOGGER.warn("PostChain uniform reflection failed; dynamic morphine/suppression grade may not apply");
        }
    }
}
