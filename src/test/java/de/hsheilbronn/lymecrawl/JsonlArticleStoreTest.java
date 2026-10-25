package de.hsheilbronn.lymecrawl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JsonlArticleStoreTest {

    private static final CrawlIdentity ID = new CrawlIdentity("lyme", null, null);

    @TempDir
    Path dir;

    private static Article article(String pmid) {
        return new Article(pmid, "t" + pmid, "", "j", 2020, List.of(), "", "", List.of(), List.of());
    }

    private void write(Path f, boolean resume, String... pmids) throws IOException {
        try (JsonlArticleStore s = new JsonlArticleStore(f, "n", ID, resume)) {
            for (String p : pmids) s.write(article(p));
        }
    }

    @Test
    void resumeKeepsExistingAndSkipsDuplicates() throws IOException {
        Path f = dir.resolve("a.jsonl");
        write(f, false, "1", "2");
        try (JsonlArticleStore s = new JsonlArticleStore(f, "n", ID, true)) {
            assertEquals(2, s.existing());
            assertTrue(s.contains("1"));
            s.write(article("2"));
            s.write(article("3"));
            assertEquals(1, s.written());
        }
        assertEquals(4, Files.readAllLines(f).size()); // header + 3
    }

    @Test
    void withoutResumeOverwrites() throws IOException {
        Path f = dir.resolve("a.jsonl");
        write(f, false, "1", "2");
        write(f, false, "3");
        assertEquals(2, Files.readAllLines(f).size()); // header + 1
    }

    @Test
    void dropsTruncatedLastLine() throws IOException {
        Path f = dir.resolve("a.jsonl");
        write(f, false, "1", "2");
        Files.writeString(f, "{\"pmid\":\"3\",\"tit", java.nio.file.StandardOpenOption.APPEND);
        try (JsonlArticleStore s = new JsonlArticleStore(f, "n", ID, true)) {
            assertEquals(2, s.existing());
            assertFalse(s.contains("3"));
            s.write(article("3"));
        }
        assertEquals(4, Files.readAllLines(f).size());
    }

    @Test
    void corruptLineInTheMiddleFails() throws IOException {
        Path f = dir.resolve("a.jsonl");
        Files.writeString(f, "{\"name\":\"n\",\"configHash\":\"" + ID.hash() + "\"}\n{\"pmid\":\"1\"}\nnot json\n{\"pmid\":\"2\"}\n");
        assertThrows(IOException.class, () -> new JsonlArticleStore(f, "n", ID, true));
    }

    @Test
    void firstLineIsConfigHeader() throws IOException {
        Path f = dir.resolve("a.jsonl");
        write(f, false, "1");
        String header = Files.readAllLines(f).get(0);
        assertTrue(header.startsWith("{\"name\":\"n\",\"configHash\":"));
        assertTrue(header.contains("\"configHash\":\"" + ID.hash() + "\""));
        assertTrue(header.contains("\"query\":\"lyme\""));
    }

    @Test
    void differentConfigIsRejectedOnResume() throws IOException {
        Path f = dir.resolve("a.jsonl");
        write(f, false, "1");
        CrawlIdentity other = new CrawlIdentity("borrelia", null, null);
        IOException e = assertThrows(IOException.class, () -> new JsonlArticleStore(f, "n", other, true));
        assertTrue(e.getMessage().contains(ID.hash()));
    }

    @Test
    void differentNameIsRejectedOnResume() throws IOException {
        Path f = dir.resolve("a.jsonl");
        write(f, false, "1");
        assertThrows(IOException.class, () -> new JsonlArticleStore(f, "other", ID, true));
    }

    @Test
    void fileWithoutHeaderIsRejected() throws IOException {
        Path f = dir.resolve("a.jsonl");
        Files.writeString(f, "{\"pmid\":\"1\"}\n");
        assertThrows(IOException.class, () -> new JsonlArticleStore(f, "n", ID, true));
    }

    @Test
    void truncatedHeaderIsRecreated() throws IOException {
        Path f = dir.resolve("a.jsonl");
        Files.writeString(f, "{\"configHash\":\"ab");
        write(f, true, "1");
        assertEquals(2, Files.readAllLines(f).size());
        try (JsonlArticleStore s = new JsonlArticleStore(f, "n", ID, true)) {
            assertTrue(s.contains("1"));
        }
    }
}
