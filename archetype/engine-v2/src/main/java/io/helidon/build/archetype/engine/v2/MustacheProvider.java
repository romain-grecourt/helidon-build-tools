package io.helidon.build.archetype.engine.v2;

import io.helidon.build.archetype.engine.v2.ast.Block;
import io.helidon.build.archetype.engine.v2.spi.TemplateSupport;
import io.helidon.build.archetype.engine.v2.spi.TemplateSupportProvider;

public class MustacheProvider implements TemplateSupportProvider {

    @Override
    public String name() {
        return "mustache";
    }

    @Override
    public TemplateSupport create(Block block, Context context) {
        return new MustacheSupport(block, context);
    }
}
