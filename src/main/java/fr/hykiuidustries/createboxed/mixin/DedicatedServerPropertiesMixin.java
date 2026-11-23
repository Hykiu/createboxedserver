package fr.hykiuidustries.createboxed.mixin;

import net.minecraft.server.dedicated.DedicatedServerProperties;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.ModifyVariable;

import java.util.Properties;

/**
 * Makes the vanilla "The Void" superflat preset the default world generation
 * for dedicated servers, without overriding an explicit server.properties value.
 */
@Mixin(DedicatedServerProperties.class)
public abstract class DedicatedServerPropertiesMixin {
    @Unique private static final String LEVEL_TYPE = "level-type";
    @Unique private static final String GENERATOR_SETTINGS = "generator-settings";
    @Unique private static final String VOID_PRESET = "minecraft:flat";
    // Same settings as the vanilla Flat Level Generator Preset "the_void" (Minecraft 1.21.1).
    @Unique private static final String VOID_SETTINGS =
        "{\"structure_overrides\":[],\"layers\":[{\"height\":1,\"block\":\"minecraft:air\"}],"
            + "\"lakes\":false,\"features\":true,\"biome\":\"minecraft:the_void\"}";

    @ModifyVariable(method = "<init>", at = @At("HEAD"), argsOnly = true)
    private static Properties applyDefaultVoidPreset(Properties properties) {
        properties.putIfAbsent(LEVEL_TYPE, VOID_PRESET);
        properties.putIfAbsent(GENERATOR_SETTINGS, VOID_SETTINGS);
        return properties;
    }
}
