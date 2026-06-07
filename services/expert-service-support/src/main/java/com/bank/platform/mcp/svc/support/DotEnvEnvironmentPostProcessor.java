package com.bank.platform.mcp.svc.support;

import org.apache.commons.logging.Log;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.logging.DeferredLogFactory;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Loads a {@code .env} file into the Spring {@link org.springframework.core.env.Environment}
 * at startup, so the {@code ${VERTEX_BASE_URL}}, {@code ${EXPERT_*}} etc. placeholders in
 * {@code application.yml} resolve from one local file in development — no external
 * dependency, no shell wiring. Registered via {@code META-INF/spring.factories}, it runs
 * for every service that depends on this module.
 *
 * <p>Resolution rules:
 * <ul>
 *   <li>The file is located from {@code dotenv.path}/{@code DOTENV_PATH} if set, otherwise
 *       by walking up from the working directory to the first {@code .env} (so running
 *       from a sub-module still finds the repo-root file).</li>
 *   <li>It is added as the <em>lowest-precedence</em> property source, so real OS
 *       environment variables and {@code -D} system properties always win — {@code .env}
 *       is a convenience default, never an override (standard dotenv semantics).</li>
 * </ul>
 *
 * <p>Format: {@code KEY=VALUE} lines, {@code #} comments, blank lines, optional
 * {@code export } prefix, and optional single/double quotes around the value.
 */
public class DotEnvEnvironmentPostProcessor implements EnvironmentPostProcessor {

    static final String PROPERTY_SOURCE_NAME = "dotenv";
    private static final String PATH_OVERRIDE = "dotenv.path";
    private static final int MAX_PARENT_WALK = 8;

    private final Log log;

    public DotEnvEnvironmentPostProcessor(DeferredLogFactory logFactory) {
        this.log = logFactory.getLog(DotEnvEnvironmentPostProcessor.class);
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        Optional<Path> file = locate();
        if (file.isEmpty()) {
            log.debug("No .env file found; skipping");
            return;
        }
        Path path = file.get();
        try {
            Map<String, Object> values = parse(Files.readAllLines(path, StandardCharsets.UTF_8));
            if (values.isEmpty()) {
                log.debug("Empty .env file at " + path + "; nothing to load");
                return;
            }
            // addLast => lowest precedence, so OS env vars / system properties override it.
            environment.getPropertySources().addLast(new MapPropertySource(PROPERTY_SOURCE_NAME, values));
            // Log keys only — never values — to avoid leaking secrets like the bearer token.
            log.info("Loaded " + values.size() + " properties from " + path + " (.env): " + values.keySet());
        } catch (IOException e) {
            throw new UncheckedIOException("failed reading .env file: " + path, e);
        }
    }

    /** Locates the {@code .env} file via the override property, else by walking up the tree. */
    static Optional<Path> locate() {
        String override = System.getProperty(PATH_OVERRIDE, System.getenv("DOTENV_PATH"));
        if (override != null && !override.isBlank()) {
            Path p = Path.of(override);
            return Files.isRegularFile(p) ? Optional.of(p) : Optional.empty();
        }
        Path dir = Path.of(System.getProperty("user.dir", ".")).toAbsolutePath();
        for (int i = 0; i < MAX_PARENT_WALK && dir != null; i++) {
            Path candidate = dir.resolve(".env");
            if (Files.isRegularFile(candidate)) return Optional.of(candidate);
            dir = dir.getParent();
        }
        return Optional.empty();
    }

    /** Parses {@code .env} lines into a key/value map. Pure and side-effect free. */
    static Map<String, Object> parse(List<String> lines) {
        Map<String, Object> values = new LinkedHashMap<>();
        for (String raw : lines) {
            String line = raw.strip();
            if (line.isEmpty() || line.startsWith("#")) continue;
            if (line.startsWith("export ")) line = line.substring("export ".length()).strip();
            int eq = line.indexOf('=');
            if (eq <= 0) continue; // no key, or no '='
            String key = line.substring(0, eq).strip();
            String value = unquote(line.substring(eq + 1).strip());
            if (!key.isEmpty()) values.put(key, value);
        }
        return values;
    }

    private static String unquote(String v) {
        if (v.length() >= 2
                && ((v.startsWith("\"") && v.endsWith("\"")) || (v.startsWith("'") && v.endsWith("'")))) {
            return v.substring(1, v.length() - 1);
        }
        return v;
    }
}
