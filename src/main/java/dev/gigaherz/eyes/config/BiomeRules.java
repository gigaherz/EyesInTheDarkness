package dev.gigaherz.eyes.config;

import com.google.common.collect.Lists;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.core.Registry;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public class BiomeRules
{
    private static final AtomicReference<List<Rule>> rules = new AtomicReference<>(new ArrayList<>());

    /*package*/
    static void parseRules(List<? extends String> dimensionRules)
    {
        var list = new ArrayList<Rule>();
        dimensionRules.forEach(r -> list.add(parse(r)));
        list.add(disallowLabel("void")); // Added at the end to make sure it's lowest priority.
        rules.set(list);
    }

    public static boolean isBiomeAllowed(ResourceKey<Biome> key)
    {
        for (Rule rule : rules.get())
        {
            if (rule.test(key))
                return rule.allow;
        }
        return true;
    }

    public static boolean isBiomeAllowed(Biome biome)
    {
        ResourceKey<Biome> key = ResourceKey.create(Registry.BIOME_REGISTRY, Objects.requireNonNull(biome.getRegistryName()));
        //RegistryKey<Biome> key = RegistryKey.getOrCreateKey(Registry.BIOME_KEY, world.registryAccess().getRegistry(Registry.BIOME_KEY).getKey(biome));
        //RegistryKey<Biome> key = world.registryAccess().getRegistry(Registry.BIOME_KEY).getOptionalKey(biome).orElseThrow(() -> new IllegalStateException("The Biome did not have a key."));
        return isBiomeAllowed(key);
    }

    private static Rule parse(String rule)
    {
        boolean allow = true;
        if (rule.startsWith("!"))
        {
            allow = false;
            rule = rule.substring(1);
        }
        boolean isLabel = false;
        if (rule.startsWith("#"))
        {
            isLabel = true;
            rule = rule.substring(1);
        }
        else if (rule.equals("*"))
        {
            rule = null;
        }
        return new Rule(allow, isLabel, rule);
    }

    private static Rule disallowLabel(String label)
    {
        return new Rule(false, true, label);
    }

    private static class Rule implements Predicate<ResourceKey<Biome>>
    {
        public final boolean allow;
        public final boolean isLabel;
        public final String labelName;
        public final ResourceLocation registryName;
        public final BiomeDictionary.Type labelType;

        private Rule(boolean allow, boolean isLabel, String labelName)
        {
            this.allow = allow;
            this.isLabel = isLabel;
            this.labelName = labelName;
            if (labelName == null)
            {
                this.registryName = null;
                this.labelType = null;
            }
            else if (isLabel)
            {
                this.registryName = null;
                this.labelType = BiomeDictionary.Type.getType(labelName);
            }
            else
            {
                this.registryName = new ResourceLocation(labelName);
                this.labelType = null;
            }
        }

        @Override
        public boolean test(ResourceKey<Biome> biome)
        {
            if (labelName == null)
                return allow;
            if (isLabel)
            {
                return BiomeDictionary.hasType(biome, labelType);
            }
            else
            {
                return registryName.equals(biome.location());
            }
        }
    }
}