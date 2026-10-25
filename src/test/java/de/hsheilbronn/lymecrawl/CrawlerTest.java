package de.hsheilbronn.lymecrawl;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class CrawlerTest {

    @TempDir
    Path dir;

    /** Serves PMIDs 1..count and records which PMIDs were requested as XML. */
    static class FakeApi implements PubmedApi {
        final int count;
        final List<String> fetched = new ArrayList<>();

        FakeApi(int count) {
            this.count = count;
        }

        public IdPage searchIds(String term, String min, String max, int retmax) {
            List<String> ids = IntStream.rangeClosed(1, Math.min(count, retmax)).mapToObj(Integer::toString).toList();
            return new IdPage(count, ids);
        }

        public String fetchXml(List<String> pmids) {
            fetched.addAll(pmids);
            StringBuilder xml = new StringBuilder("<PubmedArticleSet>");
            for (String id : pmids) {
                xml.append("<PubmedArticle><MedlineCitation><PMID>").append(id)
                        .append("</PMID><Article><ArticleTitle>T").append(id)
                        .append("</ArticleTitle></Article></MedlineCitation></PubmedArticle>");
            }
            return xml.append("</PubmedArticleSet>").toString();
        }
    }

    private Path file() {
        return config(0, 1, true).outputFile();
    }

    private CrawlConfig config(int max, int batch, boolean resume) {
        return new CrawlConfig("t", "q", null, null, null, null, max, batch, dir.resolve("out.jsonl"), resume);
    }

    @Test
    void secondRunOnlyFetchesWhatIsMissing() throws Exception {
        FakeApi first = new FakeApi(10);
        assertEquals(4, new Crawler(config(4, 3, true), first, new PubmedXmlParser()).crawl());
        assertEquals(List.of("1", "2", "3", "4"), first.fetched);

        FakeApi second = new FakeApi(10);
        assertEquals(6, new Crawler(config(0, 3, true), second, new PubmedXmlParser()).crawl());
        assertEquals(List.of("5", "6", "7", "8", "9", "10"), second.fetched);
        assertEquals(11, Files.readAllLines(file()).size());

        FakeApi third = new FakeApi(12); // two new publications appeared
        assertEquals(2, new Crawler(config(0, 3, true), third, new PubmedXmlParser()).crawl());
        assertEquals(List.of("11", "12"), third.fetched);
    }

    @Test
    void resumeDisabledStartsOver() throws Exception {
        new Crawler(config(0, 5, true), new FakeApi(5), new PubmedXmlParser()).crawl();
        FakeApi again = new FakeApi(5);
        assertEquals(5, new Crawler(config(0, 5, false), again, new PubmedXmlParser()).crawl());
        assertEquals(5, again.fetched.size());
        assertEquals(6, Files.readAllLines(file()).size());
    }

    @Test
    void changedConfigGoesToItsOwnFolder() throws Exception {
        CrawlConfig a = new CrawlConfig("t", "q", null, null, null, null, 0, 5, dir.resolve("out.jsonl"), true);
        CrawlConfig b = new CrawlConfig("t", "other", null, null, null, null, 0, 5, dir.resolve("out.jsonl"), true);
        new Crawler(a, new FakeApi(3), new PubmedXmlParser()).crawl();
        FakeApi api = new FakeApi(3);
        new Crawler(b, api, new PubmedXmlParser()).crawl();
        assertEquals(3, api.fetched.size()); // nothing reused from the other config
        assertEquals(dir.resolve("t-" + a.identity().hash()).resolve("out.jsonl"), a.outputFile());
        assertEquals(true, Files.exists(b.outputFile()) && !a.outputFile().equals(b.outputFile()));
    }
}
