package io.helidon.build.cli.impl;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.Test;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.CoreMatchers.equalTo;
import static org.hamcrest.CoreMatchers.is;

/**
 * CLI main class test.
 */
public class MainTest {

    static final String CLI_USAGE = resourceAsString("cli-usage.txt");
    static final String DEV_CMD_HELP = resourceAsString("dev-cmd-help.txt");

    static String javaPath() {
        String javaHome = System.getProperty("java.home");
        if (javaHome != null) {
            File javaHomeBin = new File(javaHome, "bin");
            if (javaHomeBin.exists() && javaHomeBin.isDirectory()) {
                File javaBin = new File(javaHomeBin, "java");
                if (javaBin.exists() && javaBin.isFile()) {
                    return javaBin.getAbsolutePath();
                }
            }
        }
        return "java";
    }

    static String resourceAsString(String name) {
        InputStream is = MainTest.class.getResourceAsStream(name);
        try {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            throw new RuntimeException(ex);
        }
    }

    static class ExecResult {

        final int code;
        final String output;

        ExecResult(int code, String output) {
            this.code = code;
            this.output = output;
        }
    }

    static ExecResult exec(String... args) throws IOException, InterruptedException {
        List<String> cmdArgs = new ArrayList<>();
        cmdArgs.addAll(List.of(javaPath(), "-cp", System.getProperty("java.class.path"), Main.class.getName()));
        for (String arg : args) {
            cmdArgs.add(arg);
        }
        ProcessBuilder pb = new ProcessBuilder(cmdArgs);
        Process p = pb.redirectErrorStream(true).start();
        String output = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        if (!p.waitFor(10, TimeUnit.SECONDS)) {
            throw new IllegalStateException("timeout waiting for process");
        }
        return new ExecResult(p.exitValue(), output);
    }

    @Test
    public void testUsage() throws IOException, InterruptedException {
        ExecResult res = exec("--help");
        assertThat(res.code, is(equalTo(0)));
        assertThat(res.output, is(equalTo(CLI_USAGE)));

        res = exec("help");
        assertThat(res.code, is(equalTo(0)));
        assertThat(res.output, is(equalTo(CLI_USAGE)));

        res = exec();
        assertThat(res.code, is(equalTo(0)));
        assertThat(res.output, is(equalTo(CLI_USAGE)));
    }

    @Test
    public void testHelp() throws IOException, InterruptedException {
        ExecResult res = exec("dev" ,"--help");
        System.out.println(res.output);
        assertThat(res.code, is(equalTo(0)));
        assertThat(res.output, is(equalTo(DEV_CMD_HELP)));

        res = exec("help" ,"dev");
        assertThat(res.code, is(equalTo(0)));
        assertThat(res.output, is(equalTo(DEV_CMD_HELP)));
    }
}
