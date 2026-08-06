package ichttt.mods.firstaid.common.potion;

import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;

/**
 * Applies status effects that milk cannot permanently clear on Forge 1.20.1.
 */
public final class MilkImmuneMobEffectInstance {
    private static final Method GET_CURATIVE_ITEMS;
    private static final Method SET_CURATIVE_ITEMS;

    static {
        Method getCurative = null;
        Method setCurative = null;
        try {
            getCurative = MobEffectInstance.class.getMethod("getCurativeItems");
        } catch (ReflectiveOperationException ignored) {
        }
        try {
            setCurative = MobEffectInstance.class.getMethod("setCurativeItems", Collection.class);
        } catch (ReflectiveOperationException ignored) {
        }
        GET_CURATIVE_ITEMS = getCurative;
        SET_CURATIVE_ITEMS = setCurative;
    }

    private MilkImmuneMobEffectInstance() {
    }

    public static MobEffectInstance create(MobEffect effect, int durationTicks) {
        return create(effect, durationTicks, 0, true);
    }

    public static MobEffectInstance create(MobEffect effect, int durationTicks, int amplifier, boolean showIcon) {
        MobEffectInstance instance = new MobEffectInstance(effect, Math.max(2, durationTicks), amplifier, false, true, showIcon);
        stripCures(instance);
        return instance;
    }

    public static void apply(LivingEntity entity, MobEffect effect, int durationTicks) {
        apply(entity, effect, durationTicks, 0, true);
    }

    public static void apply(LivingEntity entity, MobEffect effect, int durationTicks, int amplifier, boolean showIcon) {
        entity.addEffect(create(effect, durationTicks, amplifier, showIcon));
    }

    public static void ensure(LivingEntity entity, MobEffect effect, int durationTicks) {
        ensure(entity, effect, durationTicks, 0, true);
    }

    public static void ensure(LivingEntity entity, MobEffect effect, int durationTicks, int amplifier, boolean showIcon) {
        int remaining = Math.max(2, durationTicks);
        MobEffectInstance active = entity.getEffect(effect);
        if (active == null || active.getDuration() < remaining - 1 || active.getAmplifier() != amplifier) {
            apply(entity, effect, remaining, amplifier, showIcon);
        } else {
            stripCures(active);
        }
    }

    public static void stripCures(MobEffectInstance instance) {
        if (instance == null) {
            return;
        }
        try {
            if (SET_CURATIVE_ITEMS != null) {
                SET_CURATIVE_ITEMS.invoke(instance, Collections.emptyList());
            } else if (GET_CURATIVE_ITEMS != null) {
                Object curative = GET_CURATIVE_ITEMS.invoke(instance);
                if (curative instanceof Collection<?> collection) {
                    collection.clear();
                }
            }
        } catch (ReflectiveOperationException | UnsupportedOperationException ignored) {
        }
    }
}
