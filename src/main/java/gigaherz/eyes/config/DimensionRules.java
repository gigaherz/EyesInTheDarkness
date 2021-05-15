package gigaherz.eyes.config;

import com.google.common.collect.Lists;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.server.ServerWorld;

import java.util.List;
import java.util.function.Predicate;

public class DimensionRules
{
    private static final List<Rule> rules = Lists.newArrayList();

    /*package*/
    static void parseRules(List<? extends String> dimensionRules)
    {
        rules.clear();
        dimensionRules.forEach(r -> rules.add(parse(r)));
        rules.add(disallowLabel("void")); // Added at the end to make sure it's lowest priority.
    }

    public static boolean isDimensionAllowed(ServerWorld world)
    {
        for (Rule rule : rules)
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

    private static class Rule implements Predicate<ServerWorld>
    {
        public final boolean allow;
        public final boolean isType;
        public final ResourceLocation name;

        private Rule(boolean allow, boolean isType, String key)
        {
            this.allow = allow;
            this.isType = isType;
            this.name = key == null ? null : new ResourceLocation(key);
        }

        @Override
        public boolean test(ServerWorld world)
        {
            if (name == null)
                return allow;
            if (isType)
            {
                return name.equals(world.registryAccess().dimensionTypes().getKey(world.dimensionType()));
            }
            else
            {
                return name.equals(world.dimension().location());
            }
        }
    }
}