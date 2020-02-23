package io.helidon.build.cli.impl;

import java.io.File;

import io.helidon.build.cli.harness.CommandFragment;
import io.helidon.build.cli.harness.Creator;
import io.helidon.build.cli.harness.Option.KeyValue;

/**
 * Common options.
 */
@CommandFragment
final class CommonOptions {

    final File project;

    @Creator
    CommonOptions(@KeyValue(name = "project", description = "The project directory") File project) {
        this.project = project;
    }
}
