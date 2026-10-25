package de.hsheilbronn.lymecrawl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Stores {@link Article}s as JSON Lines (one article per line) behind a header line:
 * <pre>{"name":"lyme","configHash":"3f9a1c07","config":{"query":...,"from":...,"to":...},"createdAt":"..."}</pre>
 * <p>
 * With {@code resume} an existing file is kept: its PMIDs are available through {@link #contains(String)}
 * and new articles are appended. The header must carry the given name and the hash of the given {@link CrawlIdentity},
 * otherwise the file belongs to a different crawl and opening it fails. A truncated last line (crash during
 * writing) is dropped. Without {@code resume} the file is replaced.
 */
public class JsonlArticleStore implements Closeable {

    private static final String HASH_FIELD = "configHash";

    private final ObjectMapper json = new ObjectMapper();
    private final Log log = LogFactory.getLog(getClass());
    private final Set<String> pmids = new HashSet<>();
    private final BufferedWriter out;
    private int written;

    public JsonlArticleStore(Path file, String name, CrawlIdentity identity, boolean resume) throws IOException {
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) Files.createDirectories(parent);
        if (resume && Files.exists(file) && loadExisting(file, name, identity)) {
            out = Files.newBufferedWriter(file, StandardCharsets.UTF_8, StandardOpenOption.APPEND);
        } else {
            out = Files.newBufferedWriter(file, StandardCharsets.UTF_8);
            writeHeader(name, identity);
        }
    }

    private void writeHeader(String name, CrawlIdentity identity) throws IOException {
        ObjectNode header = json.createObjectNode();
        header.put("name", name);
        header.put(HASH_FIELD, identity.hash());
        header.set("config", json.valueToTree(identity));
        header.put("createdAt", Instant.now().toString());
        out.write(json.writeValueAsString(header));
        out.newLine();
        out.flush();
    }

    /** @return true if the file holds a valid header and can be appended to; false if it has to be recreated */
    private boolean loadExisting(Path file, String name, CrawlIdentity identity) throws IOException {
        List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8).stream()
                .filter(l -> !l.isBlank()).toList();
        List<String> valid = new ArrayList<>(lines.size());
        boolean truncated = false;
        for (int i = 0; i < lines.size(); i++) {
            JsonNode node;
            try {
                node = json.readTree(lines.get(i));
            } catch (IOException e) {
                if (i < lines.size() - 1) {
                    throw new IOException("Corrupt line " + (i + 1) + " in " + file + ": " + e.getMessage(), e);
                }
                log.warn("Dropping incomplete last line of " + file);
                truncated = true;
                break;
            }
            if (i == 0) {
                checkHeader(file, node, name, identity);
            } else {
                pmids.add(node.path("pmid").asText());
            }
            valid.add(lines.get(i));
        }
        if (valid.isEmpty()) return false; // empty file or only a truncated header
        // Rewrite also normalises a missing final newline, which would glue the next article to the last line.
        if (truncated || !endsWithNewline(file)) Files.write(file, valid, StandardCharsets.UTF_8);
        return true;
    }

    private void checkHeader(Path file, JsonNode header, String name, CrawlIdentity identity) throws IOException {
        String found = header.path(HASH_FIELD).asText(null);
        if (found == null) {
            throw new IOException(file + " has no config header (not written by this version); move it away or set crawl.resume=false");
        }
        if (!found.equals(identity.hash()) || !name.equals(header.path("name").asText(null))) {
            throw new IOException(file + " was written with a different configuration (name "
                    + header.path("name").asText() + ", hash " + found + "; current: name " + name + ", hash "
                    + identity.hash() + "): " + header.path("config"));
        }
    }

    private boolean endsWithNewline(Path file) throws IOException {
        try (var ch = Files.newByteChannel(file)) {
            ch.position(ch.size() - 1);
            var b = java.nio.ByteBuffer.allocate(1);
            ch.read(b);
            return b.get(0) == '\n';
        }
    }

    /** @return number of articles that were already in the file when it was opened */
    public int existing() {
        return pmids.size() - written;
    }

    public boolean contains(String pmid) {
        return pmids.contains(pmid);
    }

    /** Appends the article unless its PMID is already stored. */
    public void write(Article article) throws IOException {
        if (!pmids.add(article.pmid())) return;
        out.write(json.writeValueAsString(article));
        out.newLine();
        written++;
    }

    /** Pushes buffered articles to disk; called after each batch so that an interruption loses little. */
    public void flush() throws IOException {
        out.flush();
    }

    /** @return number of articles added by this instance */
    public int written() {
        return written;
    }

    @Override
    public void close() throws IOException {
        out.close();
    }
}
