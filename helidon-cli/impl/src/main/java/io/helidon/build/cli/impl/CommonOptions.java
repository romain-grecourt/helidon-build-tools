package io.helidon.build.cli.impl;

import java.io.File;

import io.helidon.build.cli.harness.Option;
import io.helidon.build.cli.harness.CommandFragment;
import io.helidon.build.cli.harness.Creator;

/**
 * Common options.
 */
@CommandFragment
final class CommonOptions {

    final File project;

    @Creator
    CommonOptions(@Option(name = "project", description = "The project directory", required = false) File project) {
        this.project = project;
    }
}
