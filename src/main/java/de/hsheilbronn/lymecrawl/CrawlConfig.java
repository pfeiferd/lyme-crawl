package de.hsheilbronn.lymecrawl;

import java.nio.file.Path;

/**
 * Validated, immutable settings for one crawl. See {@code src/main/resources/crawl.properties}
 * for the documentation of each setting and {@link ConfigLoader} for how they are assembled.
 *
 * @param name   label of the crawl, used in the output folder name and the file header; letters, digits, {@code . _ -}
 * @param apiKey optional NCBI API key; raises the rate limit from 3 to 10 requests/s
 * @param email  optional contact address sent to NCBI via the {@code email} parameter
 * @param resume continue an existing output file instead of overwriting it
 * @param output base for the result file, see {@link #outputFile()}
 * @param max    maximum number of records to fetch, or 0 for all matches
 */
public record CrawlConfig(
        String name,
        String query,
        String apiKey,
        String email,
        String minDate,
        String maxDate,
        int max,
        int batchSize,
        Path output,
        boolean resume) {

    public CrawlConfig {
        if (name == null || !name.matches("[A-Za-z0-9._-]+")) {
            throw new IllegalArgumentException("crawl.name is required and may only contain letters, digits, '.', '_' and '-'");
        }
        if (query == null || query.isBlank()) throw new IllegalArgumentException("crawl.query must not be empty");
        if ((minDate == null) != (maxDate == null)) {
            throw new IllegalArgumentException("crawl.from and crawl.to must be set together");
        }
        if (max < 0) throw new IllegalArgumentException("crawl.max must be >= 0");
        if (batchSize < 1 || batchSize > 10_000) {
            throw new IllegalArgumentException("crawl.batch.size must be 1..10000");
        }
        if (output == null) throw new IllegalArgumentException("crawl.output must not be empty");
    }

    /** The part of the configuration that determines which articles a crawl yields. */
    public CrawlIdentity identity() {
        return new CrawlIdentity(query, minDate, maxDate);
    }

    /**
     * The file actually written: {@code crawl.output} with the name and {@link CrawlIdentity#hash() config hash}
     * inserted as a subfolder, e.g. {@code data/articles.jsonl} becomes {@code data/lyme-3f9a1c07/articles.jsonl}.
     */
    public Path outputFile() {
        Path parent = output.getParent();
        Path folder = parent == null ? Path.of(folderName()) : parent.resolve(folderName());
        return folder.resolve(output.getFileName());
    }

    private String folderName() {
        return name + "-" + identity().hash();
    }

    public boolean hasApiKey() {
        return apiKey != null;
    }

    public boolean hasDateRange() {
        return minDate != null;
    }
}
