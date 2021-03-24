/*
 * Copyright (c) 2021 Oracle and/or its affiliates.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.helidon.build.cli.harness;

import java.nio.file.Path;

import io.helidon.build.cli.harness.CommandInvoker.Result;
import io.helidon.build.cli.harness.CommandInvoker.Result.Status;
import io.helidon.build.cli.harness.CommandInvoker.SubCommandInvoker;
import io.helidon.build.cli.harness.CommandInvoker.SubCommandBuilderFactory;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.is;

/**
 * Demonstrates and tests the {@link CommandInvoker} API.
 */
public class CommandInvokerTest {

    /**
     * Factories for all known sub-commands.
     */
    abstract static class SubCommands {

        /**
         * Factory for the {@code bar} sub-command.
         */
        public static final SubCommandBuilderFactory<BarSubCommand, BarSubCommandBuilder> BAR = BarSubCommandBuilder::new;

        private SubCommands() {
        }
    }

    @Test
    public void simpleTest() throws Exception {
        Result result = FooCli.builder()
                              .debug(true)
                              .verbose(true)
                              .subCommand(SubCommands.BAR)
                              .projectDir(Path.of("my-project-dir"))
                              .invoke();

        assertThat(result.status(), is(Status.SUCCESS));
        // TODO include the project directory in the output
        assertThat(result.output(), is("bar!"));
    }

    /**
     * Foo CLI invoker.
     *
     * @param <I> invoker type param
     * @param <B> builder type param
     */
    interface FooCli<
            I extends FooCli<I, B>,
            B extends FooCliBuilder<I, B>> extends CommandInvoker<I, B> {

        /**
         * Create a new Foo Cli builder.
         *
         * @return FooCliBuilder
         */
        static FooCliBuilder<?, ?> builder() {
            return new FooCliBuilder<>(new FooCliProvider());
        }
    }

    /**
     * Bar sub-command invoker.
     */
    interface BarSubCommand extends
            FooCli<BarSubCommand, BarSubCommandBuilder>,
            SubCommandInvoker<BarSubCommand, BarSubCommandBuilder> {

        /**
         * Get the project directory.
         *
         * @return project directory
         */
        Path projectDir();
    }

    /**
     * Bar sub-command builder.
     */
    static final class BarSubCommandBuilder extends FooCliBuilder<BarSubCommand, BarSubCommandBuilder> {

        private Path projectDir;

        BarSubCommandBuilder(CommandInvoker.Provider provider) {
            super(provider);
        }

        /**
         * Set the project directory.
         *
         * @param projectDir project directory
         * @return this builder
         */
        public BarSubCommandBuilder projectDir(Path projectDir) {
            this.projectDir = projectDir;
            return this;
        }

        @Override
        public BarSubCommand build() {
            return new MySubCommandImpl(this);
        }
    }

    private static final class MySubCommandImpl extends FooCliImpl<BarSubCommand, BarSubCommandBuilder>
            implements BarSubCommand {

        private final Path projectDir;

        MySubCommandImpl(BarSubCommandBuilder builder) {
            super(builder);
            this.projectDir = builder.projectDir;
        }

        @Override
        public Path projectDir() {
            return projectDir;
        }
    }

    private static class FooCliImpl<
            I extends FooCli<I, B>,
            B extends FooCliBuilder<I, B>> extends CommandInvoker.InvokerBase<I, B> implements FooCli<I, B> {

        FooCliImpl(B builder) {
            super(builder);
        }
    }

    /**
     * Foo CLI builder.
     *
     * @param <I> invoker type param
     * @param <B> builder type param
     */
    public static class FooCliBuilder<
            I extends FooCli<I, B>,
            B extends FooCliBuilder<I, B>> extends CommandInvoker.Builder<I, B> {

        private FooCliBuilder(CommandInvoker.Provider provider) {
            super(provider);
        }

        @SuppressWarnings("unchecked")
        @Override
        public I build() {
            return (I) new FooCliImpl<>((B) this);
        }
    }

    static class FooCliProvider implements CommandInvoker.Provider {

        @Override
        public Result invoke(CommandInvoker<?, ?> invoker, String... args) {
            return Result.create(Status.SUCCESS, "bar!");
        }
    }
}
