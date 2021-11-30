package io.helidon.build.archetype.engine.v2;

import io.helidon.build.archetype.engine.v2.spi.TemplateSupport;
import io.helidon.build.archetype.engine.v2.spi.TemplateSupportProvider;

public class MustacheProvider implements TemplateSupportProvider {

    @Override
    public String name() {
        return "mustache";
    }

    @Override
    public TemplateSupport create(Context ctx) {
        return new MustacheSupport(ctx);
    }
}
