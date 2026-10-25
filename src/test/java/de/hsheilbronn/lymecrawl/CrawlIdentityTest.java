package de.hsheilbronn.lymecrawl;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CrawlIdentityTest {

    @Test
    void hashIsEightHexDigitsAndStable() {
        String h = new CrawlIdentity("lyme", null, null).hash();
        assertTrue(h.matches("[0-9a-f]{8}"));
        assertEquals(h, new CrawlIdentity("lyme", null, null).hash());
    }

    @Test
    void whitespaceInQueryDoesNotMatter() {
        assertEquals(new CrawlIdentity("a OR\n   b", null, null).hash(), new CrawlIdentity(" a OR b ", null, null).hash());
    }

    @Test
    void queryAndDatesMatter() {
        String base = new CrawlIdentity("lyme", null, null).hash();
        assertNotEquals(base, new CrawlIdentity("lyme2", null, null).hash());
        assertNotEquals(base, new CrawlIdentity("lyme", "2020", "2021").hash());
    }
}
