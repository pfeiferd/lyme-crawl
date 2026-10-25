package de.hsheilbronn.lymecrawl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

/**
 * The settings that determine <em>which</em> articles a crawl yields: the query and the date range.
 * Settings that only affect how the crawl runs (batch size, API key, {@code crawl.max}, ...) are not part
 * of it, so changing them keeps a crawl resumable.
 * <p>
 * Whitespace in the query is normalised, so re-wrapping a long query does not change the {@link #hash()}.
 */
public record CrawlIdentity(String query, String from, String to) {

    public CrawlIdentity {
        query = query.strip().replaceAll("\\s+", " ");
    }

    /** First 8 hex digits of the SHA-256 over the canonical form of the settings. */
    public String hash() {
        String canonical = "query=" + query + "\nfrom=" + nullToEmpty(from) + "\nto=" + nullToEmpty(to);
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(canonical.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest, 0, 4);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e); // SHA-256 is mandatory in every JRE
        }
    }

    private static String nullToEmpty(String s) {
        return s == null ? "" : s;
    }
}
