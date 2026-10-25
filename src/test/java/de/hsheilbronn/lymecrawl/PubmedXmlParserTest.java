package de.hsheilbronn.lymecrawl;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PubmedXmlParserTest {

    @Test
    void parsesSampleRecord() throws Exception {
        String xml = new String(getClass().getResourceAsStream("/sample-pubmed.xml").readAllBytes(),
                StandardCharsets.UTF_8);
        List<Article> articles = new PubmedXmlParser().parse(xml);

        assertEquals(1, articles.size());
        Article a = articles.get(0);
        assertEquals("12345", a.pmid());
        assertEquals("Borrelia burgdorferi in Ixodes ticks.", a.title());
        assertEquals("BACKGROUND: Lyme disease is common.\nRESULTS: Prevalence was 20%.", a.abstractText());
        assertEquals(2019, a.year());
        assertEquals(List.of("Allen Steere", "Lyme Study Group"), a.authors());
        assertEquals("10.1000/xyz", a.doi());
        assertEquals("PMC999", a.pmcid());
        assertEquals(List.of("Lyme Disease"), a.meshTerms());
        assertEquals(List.of("Journal Article"), a.publicationTypes());
    }
}
