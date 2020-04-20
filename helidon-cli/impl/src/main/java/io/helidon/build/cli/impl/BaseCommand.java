/*
 * Copyright (c) 2020 Oracle and/or its affiliates.
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

package io.helidon.build.cli.impl;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;

import io.helidon.build.cli.harness.CommandContext;
import io.helidon.build.util.Constants;
import io.helidon.build.util.HelidonVersions;
import io.helidon.build.util.Log;
import io.helidon.build.util.MavenVersion;
import io.helidon.build.util.ProcessMonitor;
import io.helidon.build.util.ProjectConfig;

import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.apache.maven.model.io.xpp3.MavenXpp3Writer;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

import static io.helidon.build.util.MavenVersion.unqualifiedMinimum;
import static io.helidon.build.util.ProjectConfig.DOT_HELIDON;

/**
 * Class BaseCommand.
 */
public abstract class BaseCommand {

    static final String HELIDON_PROPERTIES = "helidon.properties";
    static final String HELIDON_VERSION = "helidon.version";
    static final String MAVEN_EXEC = Constants.OS.mavenExec();
    static final String JAVA_HOME = Constants.javaHome();
    static final String JAVA_HOME_BIN = JAVA_HOME + File.separator + "bin";
    static final long SECONDS_PER_YEAR = 365 * 24 * 60 * 60;
    static final String MINIMUM_MAJOR_HELIDON_VERSION = "2.0.0";
    static final AtomicReference<String> DEFAULT_HELIDON_VERSION = new AtomicReference<>();

    private Properties cliConfig;
    private ProjectConfig projectConfig;
    private Path projectDir;

    protected ProjectConfig projectConfig(Path dir) {
        if (projectConfig != null && dir.equals(projectDir)) {
            return projectConfig;
        }
        File dotHelidon = dir.resolve(DOT_HELIDON).toFile();
        projectConfig = new ProjectConfig(dotHelidon);
        projectDir = dir;
        return projectConfig;
    }

    protected Properties cliConfig() {
        if (cliConfig != null) {
            return cliConfig;
        }
        try {
            InputStream sourceStream = getClass().getResourceAsStream(HELIDON_PROPERTIES);
            try (InputStreamReader isr = new InputStreamReader(sourceStream)) {
                cliConfig = new Properties();
                cliConfig.load(isr);
                return cliConfig;
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected Model readPomModel(File pomFile) {
        try {
            try (FileReader fr = new FileReader(pomFile)) {
                MavenXpp3Reader mvnReader = new MavenXpp3Reader();
                return mvnReader.read(fr);
            }
        } catch (IOException | XmlPullParserException e) {
            throw new RuntimeException(e);
        }
    }

    protected void writePomModel(File pomFile, Model model) {
        try {
            try (FileWriter fw = new FileWriter(pomFile)) {
                MavenXpp3Writer mvnWriter = new MavenXpp3Writer();
                mvnWriter.write(fw, model);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    protected void executeProcess(CommandContext context, ProcessBuilder processBuilder) {
        Map<String, String> env = processBuilder.environment();
        String path = JAVA_HOME_BIN + File.pathSeparatorChar + env.get("PATH");
        env.put("PATH", path);
        env.put("JAVA_HOME", JAVA_HOME);
        try {
            // Fork process and wait for its completion
            ProcessMonitor processMonitor = ProcessMonitor.builder()
                                                          .processBuilder(processBuilder)
                                                          .stdOut(context::logInfo)
                                                          .stdErr(context::logError)
                                                          .capture(false)
                                                          .build()
                                                          .start();
            long pid = processMonitor.toHandle().pid();
            Log.info("Process with PID %d is starting", pid);
            processMonitor.waitForCompletion(SECONDS_PER_YEAR, TimeUnit.SECONDS);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected String helidonVersion() {
        return defaultHelidonVersion();
    }

    private String defaultHelidonVersion() {
        if (DEFAULT_HELIDON_VERSION.get() == null) {
            String version = System.getProperty(HELIDON_VERSION);
            if (version == null) {
                try {
                    Predicate<MavenVersion> filter = unqualifiedMinimum(MINIMUM_MAJOR_HELIDON_VERSION);
                    version = HelidonVersions.releases(filter).latest().toString();
                } catch (Exception e) {
                    version = cliConfig.getProperty(HELIDON_VERSION);
                }
            }
            DEFAULT_HELIDON_VERSION.set(version);
        }
        return DEFAULT_HELIDON_VERSION.get();
    }

    private static final String SPACES = "                                                        ";

    protected static String formatMapAsYaml(String top, Map<String, Object> map) {
        Map<String, Object> topLevel = new LinkedHashMap<>();
        topLevel.put(top, map);
        String yaml = formatMapAsYaml(topLevel, 0);
        return yaml.substring(0, yaml.length() - 1);        // remove last \n
    }

    @SuppressWarnings("unchecked")
    private static String formatMapAsYaml(Map<String, Object> map, int level) {
        StringBuilder builder = new StringBuilder();
        map.forEach((key, v) -> {
            builder.append(SPACES, 0, 2 * level);
            if (v instanceof Map<?, ?>) {
                builder.append(key).append(":\n");
                builder.append(formatMapAsYaml((Map<String, Object>) v, level + 1));
            } else if (v instanceof List<?>) {
                List<String> l = (List<String>) v;
                if (l.size() > 0) {
                    builder.append(key).append(":");
                    l.forEach(s -> builder.append("\n")
                                          .append(SPACES, 0, 2 * (level + 1))
                                          .append("- ").append(s));
                    builder.append("\n");
                }
            } else if (v != null) {     // ignore key if value is null
                builder.append(key).append(":").append(" ")
                       .append(v.toString()).append("\n");
            }
        });
        return builder.toString();
    }
}
