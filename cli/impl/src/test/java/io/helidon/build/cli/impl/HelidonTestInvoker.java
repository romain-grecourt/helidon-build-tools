package io.helidon.build.cli.impl;

import io.helidon.build.cli.harness.CommandInvoker;
import io.helidon.build.util.ProjectConfig;
import org.apache.maven.model.Model;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static io.helidon.build.cli.impl.TestUtils.execWithDirAndInput;
import static io.helidon.build.util.FileUtils.assertDir;
import static io.helidon.build.util.FileUtils.assertFile;
import static io.helidon.build.util.PomUtils.readPomModel;
import static io.helidon.build.util.ProjectConfig.DOT_HELIDON;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.is;

/**
 * Extension of {@link HelidonTestInvoker} that provides assertion methods.
 */
interface HelidonTestInvoker {
//        extends HelidonInvoker<HelidonTestInvoker.Result, HelidonTestInvoker, HelidonTestInvoker.Builder> {

//    /**
//     * Assert the generated project, and build it if {@code buildProject} is {@code true}.
//     *
//     * @return this invoker
//     * @throws Exception if an error occurs
//     */
//    HelidonTestInvoker validateProject() throws Exception;
//
//    /**
//     * Assert that the JAR file exists.
//     *
//     * @return this invoker
//     */
//    HelidonTestInvoker assertJarExists();
//
//    /**
//     * Assert that the project directory exists.
//     *
//     * @return this invoker
//     */
//    HelidonTestInvoker assertProjectExists();
//
//    /**
//     * Assert that the generated {@code pom.xml} exists and corresponds to the invocation parameters.
//     *
//     * @return this invoker
//     */
//    HelidonTestInvoker assertExpectedPom();
//
//    /**
//     * Assert that the root directory of the Java package exists under the given "source root" directory.
//     *
//     * @param sourceRoot source root directory
//     * @return this invoker
//     */
//    HelidonTestInvoker assertPackageExists(Path sourceRoot);
//
//    /**
//     * Assert that there is at least one {@code .java} file in the given "source root" directory.
//     *
//     * @param sourceRoot source root directory
//     * @return this invoker
//     * @throws IOException if an IO error occurs
//     */
//    HelidonTestInvoker assertSourceFilesExist(Path sourceRoot) throws IOException;
//
//    /**
//     * Assert that the {@code .helidon} file exists under the project directory.
//     * If {@code buildProject} is {@code true}, check that the last successful build timestamp is {@code >0}.
//     *
//     * @return this invoker
//     */
//    HelidonTestInvoker assertProjectConfig();
//
//    final class InvokerImpl extends HelidonInvoker.InvokerBase<HelidonTestInvoker.Result, HelidonTestInvoker, HelidonTestInvoker.Builder>
//            implements HelidonTestInvoker {
//
//        final boolean buildProject;
//
//        InvokerImpl(HelidonTestInvoker.Builder builder) {
//            super(builder);
//            buildProject = builder.buildProject;
//        }
//
//        @Override
//        public HelidonTestInvoker validateProject() throws Exception {
//            assertProjectExists();
//            assertExpectedPom();
//            Path sourceRoot = projectDir().resolve("src/main/java");
//            assertPackageExists(sourceRoot);
//            assertSourceFilesExist(sourceRoot);
//            if (buildProject) {
//                invokeBuild();
//                assertJarExists();
//                assertProjectConfig();
//            }
//            return this;
//        }
//
//        @Override
//        public HelidonTestInvoker assertJarExists() {
//            assertFile(projectDir().resolve("target").resolve(artifactId() + ".jar"));
//            return this;
//        }
//
//        @Override
//        public HelidonTestInvoker assertProjectExists() {
//            assertDir(projectDir());
//            return this;
//        }
//
//        @Override
//        public HelidonTestInvoker assertExpectedPom() {
//            // Check pom and read model
//            Path pomFile = assertFile(projectDir().resolve("pom.xml"));
//            Model model = readPomModel(pomFile.toFile());
//
//            // Flavor
//            String parentArtifact = model.getParent().getArtifactId();
//            assertThat(parentArtifact, containsString(flavor().toLowerCase()));
//
//            // GroupId
//            assertThat(model.getGroupId(), is(groupId()));
//
//            // ArtifactId
//            assertThat(model.getArtifactId(), is(artifactId()));
//
//            // Project Name
//            assertThat(model.getName(), is(projectName()));
//            return this;
//        }
//
//        @Override
//        public HelidonTestInvoker assertPackageExists(Path sourceRoot) {
//            TestUtils.assertPackageExists(projectDir(), packageName());
//            return this;
//        }
//
//        @Override
//        public HelidonTestInvoker assertSourceFilesExist(Path sourceRoot) throws IOException {
//            long sourceFiles = Files.walk(sourceRoot)
//                                    .filter(file -> file.getFileName().toString().endsWith(".java"))
//                                    .count();
//            assertThat(sourceFiles, is(greaterThan(0L)));
//            return this;
//        }
//
//        @Override
//        public HelidonTestInvoker assertProjectConfig() {
//            Path dotHelidon = projectDir().resolve(DOT_HELIDON);
//            ProjectConfig config = new ProjectConfig(dotHelidon);
//            assertThat(config.exists(), is(true));
//            if (buildProject) {
//                assertThat(config.lastSuccessfulBuildTime(), is(greaterThan(0L)));
//            }
//            return this;
//        }
//    }
//
//    /**
//     * {@link CommandInvoker.Provider} implementation that supports {@link HelidonTestInvoker}.
//     */
//    final class Provider implements CommandInvoker.Provider<Result, HelidonTestInvoker, Builder> {
//
//        @Override
//        public Result invoke(HelidonTestInvoker invoker, Path dir, Path input, String... args) throws Exception {
//            return new Result(invoker, execWithDirAndInput(dir.toFile(), input.toFile(), args));
//        }
//
//        @Override
//        public boolean supports(Class<? extends CommandInvoker<?, ?, ?>> invokerType) {
//            return HelidonTestInvoker.class.equals(invokerType);
//        }
//    }
//
//    /**
//     * {@link HelidonInvoker.Result} sub-class that implements a {@link HelidonTestInvoker} delegate.
//     */
//    final class Result extends HelidonInvoker.Result<Result, HelidonTestInvoker, Builder> implements HelidonTestInvoker {
//
//        private Result(HelidonTestInvoker delegate, String output) {
//            super(delegate, output);
//        }
//
//        @Override
//        public HelidonTestInvoker validateProject() throws Exception {
//            return delegate().validateProject();
//        }
//
//        @Override
//        public HelidonTestInvoker assertJarExists() {
//            return delegate().assertJarExists();
//        }
//
//        @Override
//        public HelidonTestInvoker assertProjectExists() {
//            return delegate().assertProjectExists();
//        }
//
//        @Override
//        public HelidonTestInvoker assertExpectedPom() {
//            return delegate().assertExpectedPom();
//        }
//
//        @Override
//        public HelidonTestInvoker assertPackageExists(Path sourceRoot) {
//            return delegate().assertPackageExists(sourceRoot);
//        }
//
//        @Override
//        public HelidonTestInvoker assertSourceFilesExist(Path sourceRoot) throws IOException {
//            return delegate().assertSourceFilesExist(sourceRoot);
//        }
//
//        @Override
//        public HelidonTestInvoker assertProjectConfig() {
//            return delegate().assertProjectConfig();
//        }
//    }
//
//    /**
//     * {@link HelidonInvoker.Builder} that implements the {@link HelidonTestInvoker} specific builder methods.
//     */
//    final class Builder extends HelidonInvoker.Builder<Result, HelidonTestInvoker, Builder> {
//
//        boolean buildProject;
//
//        private Builder(HelidonTestInvoker.Provider provider) {
//            super(provider);
//        }
//
//        /**
//         * Set the {@code buildProject} flag.
//         *
//         * @param buildProject flag
//         * @return this builder
//         */
//        Builder buildProject(boolean buildProject) {
//            this.buildProject = buildProject;
//            return this;
//        }
//
//        @Override
//        public HelidonTestInvoker build() {
//            return new InvokerImpl(this);
//        }
//    }
}
