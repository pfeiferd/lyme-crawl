package de.hsheilbronn.lymecrawl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/**
 * Assembles a {@link CrawlConfig} from, lowest to highest precedence: the bundled
 * {@code crawl.properties}, the local file ({@code crawl.config} property, else
 * {@code crawl.local.properties} if present) and the {@code crawl.*} / {@code ncbi.*}
 * system properties. NCBI credentials fall back to environment variables.
 */
public class ConfigLoader {

    private static final String BUNDLED = "/crawl.properties";
    private static final String DEFAULT_LOCAL_FILE = "crawl.local.properties";
    private static final String CONFIG_KEY = "crawl.config";

    private final Properties systemProperties;
    private final Map<String, String> environment;
    private final Log log = LogFactory.getLog(getClass());

    public ConfigLoader(Properties systemProperties, Map<String, String> environment) {
        this.systemProperties = systemProperties;
        this.environment = environment;
    }

    public CrawlConfig load() throws IOException {
        Properties merged = new Properties();
        merge(merged, readBundled());
        readLocalFile().ifPresent(p -> merge(merged, p));
        for (String key : systemProperties.stringPropertyNames()) {
            if (key.startsWith("crawl.") || key.startsWith("ncbi.")) {
                merged.setProperty(key, systemProperties.getProperty(key));
            }
        }
        return toConfig(new PropertyReader(merged));
    }

    private CrawlConfig toConfig(PropertyReader p) {
        return new CrawlConfig(
                p.string("crawl.name").orElse(null),
                p.string("crawl.query").orElse(null),
                p.string("ncbi.api.key").or(() -> env("NCBI_API_KEY")).orElse(null),
                p.string("ncbi.email").or(() -> env("NCBI_EMAIL")).orElse(null),
                p.string("crawl.from").orElse(null),
                p.string("crawl.to").orElse(null),
                p.integer("crawl.max", 0),
                p.integer("crawl.batch.size", 200),
                Path.of(p.string("crawl.output").orElse("data/articles.jsonl")),
                p.bool("crawl.resume", true));
    }

    private Optional<String> env(String name) {
        return Optional.ofNullable(environment.get(name)).filter(v -> !v.isBlank()).map(String::strip);
    }

    private Properties readBundled() throws IOException {
        try (InputStream in = ConfigLoader.class.getResourceAsStream(BUNDLED)) {
            if (in == null) throw new IOException("Bundled " + BUNDLED + " is missing");
            return read(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
    }

    private Optional<Properties> readLocalFile() throws IOException {
        String explicit = systemProperties.getProperty(CONFIG_KEY);
        Path file = Path.of(explicit != null ? explicit : DEFAULT_LOCAL_FILE);
        if (!Files.exists(file)) {
            if (explicit != null) throw new IOException("Config file not found: " + file.toAbsolutePath());
            return Optional.empty();
        }
        log.info("Config file: " + file.toAbsolutePath());
        try (Reader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            return Optional.of(read(r));
        }
    }

    private Properties read(Reader reader) throws IOException {
        Properties p = new Properties();
        p.load(reader);
        return p;
    }

    private void merge(Properties target, Properties source) {
        source.stringPropertyNames().forEach(k -> target.setProperty(k, source.getProperty(k)));
    }
}
