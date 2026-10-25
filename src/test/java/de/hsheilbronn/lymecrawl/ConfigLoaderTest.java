package de.hsheilbronn.lymecrawl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ConfigLoaderTest {

    @TempDir
    Path dir;

    private CrawlConfig load(Map<String, String> env, String... systemKv) throws IOException {
        Properties sys = new Properties();
        for (int i = 0; i < systemKv.length; i += 2) sys.setProperty(systemKv[i], systemKv[i + 1]);
        return new ConfigLoader(sys, env).load();
    }

    private String file(String content) throws IOException {
        Path f = dir.resolve("local.properties");
        Files.writeString(f, content);
        return f.toString();
    }

    @Test
    void bundledDefaultsAndEnvFallback() throws IOException {
        CrawlConfig c = load(Map.of("NCBI_API_KEY", "k", "NCBI_EMAIL", "a@b.c"), "crawl.config", file(""), "ncbi.email", "", "ncbi.api.key", "");
        assertTrue(c.query().contains("Lyme"));
        assertEquals("lyme", c.name());
        assertEquals("k", c.apiKey());
        assertTrue(c.hasApiKey());
        assertEquals("a@b.c", c.email());
        assertEquals(0, c.max());
        assertEquals(200, c.batchSize());
        assertEquals(Path.of("data/articles.jsonl"), c.output());
        assertTrue(c.resume());
        assertFalse(c.hasDateRange());
    }

    @Test
    void precedenceIsBundledThenFileThenSystemProperty() throws IOException {
        String f = file("crawl.max=10\ncrawl.batch.size=50\nncbi.api.key=fromfile\n");
        CrawlConfig c = load(Map.of("NCBI_API_KEY", "env"), "crawl.config", f, "crawl.max", "5");
        assertEquals(5, c.max());
        assertEquals(50, c.batchSize());
        assertEquals("fromfile", c.apiKey());
    }

    @Test
    void missingExplicitFileFails() {
        assertThrows(IOException.class, () -> load(Map.of(), "crawl.config", dir.resolve("nope").toString()));
    }

    @Test
    void rejectsInvalidValues() throws IOException {
        String f = file("");
        assertThrows(IllegalArgumentException.class, () -> load(Map.of(), "crawl.config", f, "crawl.from", "2000"));
        assertThrows(IllegalArgumentException.class, () -> load(Map.of(), "crawl.config", f, "crawl.max", "x"));
        assertThrows(IllegalArgumentException.class, () -> load(Map.of(), "crawl.config", f, "crawl.batch.size", "0"));
        assertThrows(IllegalArgumentException.class, () -> load(Map.of(), "crawl.config", f, "crawl.max", "-1"));
        assertThrows(IllegalArgumentException.class, () -> load(Map.of(), "crawl.config", f, "crawl.name", "a/b"));
        assertThrows(IllegalArgumentException.class, () -> load(Map.of(), "crawl.config", f, "crawl.name", ""));
    }
}
