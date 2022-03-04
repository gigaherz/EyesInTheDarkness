package dev.gigaherz.eyes.config;

import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.registries.ForgeRegistryEntry;

import javax.annotation.Nullable;
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
        list.add(disallowVoidBiomes()); // Added at the end to make sure it's lowest priority.
        rules.set(list);
    }

    public static boolean isBiomeAllowed(Holder<Biome> key)
    {
        for (Rule rule : rules.get())
        {
            if (rule.test(key))
                return rule.allow;
        }
        return true;
    }

    private static Rule parse(String rule)
    {
        boolean allow = true;
        if (rule.startsWith("!"))
        {
            allow = false;
            rule = rule.substring(1);
        }
        if (rule.startsWith("#"))
        {
            return new Rule(allow, null, null, rule.substring(1));
        }
        else if (rule.startsWith("$"))
        {
            return new Rule(allow, null, rule.substring(1), null);
        }
        else if (rule.equals("*"))
        {
            return new Rule(allow, null, null, null);
        }
        else
        {
            return new Rule(allow, rule, null, null);
        }
    }

    private static Rule disallowVoidBiomes()
    {
        return new Rule(false, null, null, "void");
    }

    private static class Rule implements Predicate<Holder<Biome>>
    {
        public final boolean allow;
        public final ResourceLocation registryName;
        public final TagKey<Biome> tagKey;
        public final BiomeDictionary.Type labelType;

        private Rule(boolean allow, @Nullable String registryName, @Nullable String tagName, @Nullable String dictionaryLabel)
        {
            this.allow = allow;
            this.registryName = registryName != null ? new ResourceLocation(registryName) : null;
            this.tagKey = tagName != null ? TagKey.create(Registry.BIOME_REGISTRY, new ResourceLocation(tagName)) : null;
            this.labelType = dictionaryLabel != null ? BiomeDictionary.Type.getType(dictionaryLabel) : null;
        }

        @Override
        public boolean test(Holder<Biome> biome)
        {
            if (labelType != null)
            {
                ResourceKey<Biome> name = biome.unwrap().map(key -> key,
                        value -> ResourceKey.create(Registry.BIOME_REGISTRY, Objects.requireNonNull(value.getRegistryName())));
                return BiomeDictionary.hasType(name, labelType) == allow;
            }
            else if (registryName != null)
            {
                ResourceLocation name = biome.unwrap().map(ResourceKey::location, ForgeRegistryEntry::getRegistryName);
                return registryName.equals(name) == allow;
            }
            else if(tagKey != null)
            {
                return biome.is(tagKey) == allow;
            }
            else
            {
                return allow;
            }
        }
    }
}