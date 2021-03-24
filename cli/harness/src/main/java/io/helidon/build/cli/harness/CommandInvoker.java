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

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import java.util.function.Function;

/**
 * This API provides a type system for fluent command invocations.
 * Sub-commands and arguments are represented as types and methods.
 *
 * @param <I> invoker type
 * @param <B> builder type
 */
public interface CommandInvoker<I extends CommandInvoker<I, B>, B extends CommandInvoker.Builder<I, B>> {

    // TODO add other global flags
    // TODO add logic to create the args array
    // TODO create provider implementations:
    // direct: with builder ; takes a package as argument ; uses CommandRunner
    // native-process: with builder ; takes a path to an executable binary ; or searches the PATH for a executable name
    // java-process: with builder ; takes either a location of a jar file ; or searches class-path for a jar file

    // TODO re-evaluate if we need to expose the invoker interfaces, builder should be enough
    // i.e builder is the invoker
    // and replace current invoker with a non parameterized interface to convert builder into string args.
    // Look at webclient as a pattern

    /**
     * Get the value of the verbose flag.
     *
     * @return verbose flag
     */
    boolean verbose();

    /**
     * Get the value of the debug flag.
     *
     * @return debug flag
     */
    boolean debug();

    /**
     * Get the batch input file.
     *
     * @return input file, may be {@code null}
     */
    Path input();

    /**
     * Get the working directory.
     *
     * @return working directory, never {@code null}
     */
    Path workDir();

    /**
     * Sub-command invoker.
     *
     * @param <I> Invoker type param
     * @param <B> Builder type param
     */
    interface SubCommandInvoker<
            I extends CommandInvoker<I, B>,
            B extends CommandInvoker.Builder<I, B>> extends CommandInvoker<I, B> {

    }

    /**
     * Sub-command invoker factory.
     *
     * @param <I> Invoker type param
     * @param <B> Builder type param
     */
    interface SubCommandBuilderFactory<
            I extends CommandInvoker<I, B>,
            B extends CommandInvoker.Builder<I, B>> extends Function<Provider, B> {

    }

    /**
     * Create a new builder.
     *
     * @param provider invoker provider
     * @return UnsupportedOperationException is always thrown
     */
    static CommandInvoker.Builder<?, ?> builder(Provider provider) {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Create a new builder.
     *
     * @return UnsupportedOperationException is always thrown
     */
    static CommandInvoker.Builder<?, ?> builder() {
        throw new UnsupportedOperationException("Not implemented");
    }

    /**
     * Invoker implementation base class.
     */
    class InvokerBase<I extends CommandInvoker<I, B>, B extends Builder<I, B>> implements CommandInvoker<I, B> {

        private final boolean verbose;
        private final boolean debug;
        private final Path input;
        private final Path workDir;

        /**
         * Create a new instance.
         *
         * @param builder builder
         */
        protected InvokerBase(Builder<I, B> builder) {
            verbose = builder.verbose;
            debug = builder.debug;
            input = builder.input;
            try {
                workDir = builder.workDir == null ? Files.createTempDirectory("invoker") : builder.workDir;
            } catch (IOException ex) {
                throw new UncheckedIOException(ex);
            }
        }

        @Override
        public boolean verbose() {
            return verbose;
        }

        @Override
        public boolean debug() {
            return debug;
        }

        @Override
        public Path input() {
            return input;
        }

        @Override
        public Path workDir() {
            return workDir;
        }
    }

    /**
     * Invoker builder.
     *
     * @param <I> invoker type
     * @param <B> builder type
     */
    @SuppressWarnings("unchecked")
    abstract class Builder<I extends CommandInvoker<I, B>, B extends Builder<I, B>> {

        private boolean verbose;
        private boolean debug;
        private Path workDir;
        private Path input;
        private final Provider provider;

        protected Builder(Provider provider) {
            this.provider = Objects.requireNonNull(provider, "provider is null");
        }

        /**
         * Set verbose flag.
         *
         * @param verbose verbose flag
         * @return this builder
         */
        public B verbose(boolean verbose) {
            this.verbose = verbose;
            return (B) this;
        }

        /**
         * Set debug flag.
         *
         * @param debug debug flag
         * @return this builder
         */
        public B debug(boolean debug) {
            this.debug = debug;
            return (B) this;
        }

        /**
         * Set the input file to be used for stdin.
         *
         * @param inputFileName input file
         * @return this builder
         */
        public B input(String inputFileName) {
            URL url = Objects.requireNonNull(getClass().getResource(inputFileName), inputFileName + "not found");
            this.input = new File(url.getFile()).toPath();
            return (B) this;
        }

        /**
         * Set the working directory.
         *
         * @param workDir working directory
         * @return this builder
         */
        public B workDir(Path workDir) {
            this.workDir = workDir;
            return (B) this;
        }

        /**
         * Build the command invoker instance.
         *
         * @return always throws UnsupportedOperationException
         */
        protected I build() {
            throw new UnsupportedOperationException("Not implemented");
        }

        /**
         * Create a new sub-command invoker builder.
         *
         * @param factory builder factory
         * @param <S>     sub-command type param
         * @param <SB>    sub-command builder type param
         * @return builder
         */
        public <S extends SubCommandInvoker<S, SB>, SB extends CommandInvoker.Builder<S, SB>> SB subCommand(
                SubCommandBuilderFactory<S, SB> factory) {

            return factory.apply(provider);
        }

        /**
         * Invoke a command.
         *
         * @param args raw command line arguments.
         * @return Result
         */
        Result invoke(String... args) throws Exception {
            return provider.invoke(build(), args);
        }

        /**
         * Invoke a command.
         *
         * @return Result
         */
        public Result invoke() throws Exception {
            return provider.invoke(build());
        }
    }

    /**
     * Invocation result.
     */
    class Result {

        /**
         * Result status.
         */
        public enum Status {
            /**
             * The invocation was successful.
             */
            SUCCESS,

            /**
             * The invocation was <b>NOT</b> successful.
             */
            FAILURE
        }

        private final String output;
        private final Status status;

        private Result(Status status, String output) {
            this.status = Objects.requireNonNull(status, "status is null");
            this.output = Objects.requireNonNull(output, "output is null");
        }

        /**
         * Create a new result.
         *
         * @param status status
         * @param output output
         * @return Result
         */
        static Result create(Status status, String output) {
            return new Result(status, output);
        }

        /**
         * Get the result status.
         *
         * @return status, never {@code null}
         */
        public Status status() {
            return status;
        }

        /**
         * Get the result output.
         *
         * @return output, never {@code null}
         */
        public String output() {
            return output;
        }
    }

    /**
     * Invoker provider.
     */
    interface Provider {

        /**
         * Invoke the CLI.
         *
         * @param invoker invoker
         * @param args    command line arguments
         * @return invocation result
         */
        Result invoke(CommandInvoker<?, ?> invoker, String... args) throws Exception;
    }
}
