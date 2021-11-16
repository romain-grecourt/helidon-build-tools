package io.helidon.build.archetype.engine.v2;

import io.helidon.build.archetype.engine.v2.UnresolvedOutputs.UnresolvedOutput;
import io.helidon.build.archetype.engine.v2.UnresolvedOutputs.UnresolvedVisitor;
import io.helidon.build.archetype.engine.v2.ast.Output;

public class OutputInterpreter implements UnresolvedVisitor {

    // TODO add stuff from the template package
    // i.e logic to merge model by order etc

    @Override
    public void visit(UnresolvedOutput entry, Output.Builder builder) {
    }
}
