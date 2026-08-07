/*
 * FirstAid
 * Copyright (C) 2017-2024
 */

package ichttt.mods.firstaid.client;

import ichttt.mods.firstaid.FirstAid;
import net.minecraft.client.renderer.PostChain;

/**
 * Sets float uniforms on a classic PostChain (1.20.x / 1.21.1).
 * Prefers the public {@link PostChain#setUniform(String, float)} API.
 */
public final class PostChainUniforms {
    private static boolean loggedOk;

    private PostChainUniforms() {
    }

    public static void setFloat(PostChain chain, String uniformName, float value) {
        if (chain == null) {
            return;
        }
        try {
            // Walks every pass; missing uniforms become no-ops via safeGetUniform.
            chain.setUniform(uniformName, value);
            if (!loggedOk) {
                loggedOk = true;
                FirstAid.LOGGER.info("PostChain uniforms active (first set {}={})", uniformName, value);
            }
        } catch (Exception e) {
            FirstAid.LOGGER.warn("Could not set post uniform {}={}", uniformName, value, e);
        }
    }
}
