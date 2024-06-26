package dev.gigaherz.eyes.config;

import com.google.common.collect.Lists;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

public class DimensionRules
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

    public static boolean isDimensionAllowed(ServerLevel world)
    {
        for (Rule rule : rules.get())
        {
            if (rule == null)
                continue;
            if (rule.test(world))
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

    private static class Rule implements Predicate<ServerLevel>
    {
        public final boolean allow;
        public final boolean isType;
        public final ResourceLocation name;

        private Rule(boolean allow, boolean isType, String key)
        {
            this.allow = allow;
            this.isType = isType;
            this.name = key == null ? null : ResourceLocation.parse(key);
        }

        @Override
        public boolean test(ServerLevel world)
        {
            if (name == null)
                return true;
            if (isType)
            {
                return name.equals(world.registryAccess().registryOrThrow(Registries.DIMENSION_TYPE).getKey(world.dimensionType()));
            }
            else
            {
                return name.equals(world.dimension().location());
            }
        }
    }
}