package de.hsheilbronn.lymecrawl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.util.List;

/**
 * Searches PubMed, determines which of the hits are not stored yet and downloads those batch by batch.
 * Since the decision is made per PMID, an interrupted crawl continues where it stopped.
 */
public class Crawler {

    private final CrawlConfig config;
    private final PubmedApi api;
    private final PubmedXmlParser parser;
    private final Log log = LogFactory.getLog(getClass());

    public Crawler(CrawlConfig config, PubmedApi api, PubmedXmlParser parser) {
        this.config = config;
        this.api = api;
        this.parser = parser;
    }

    /** @return number of articles newly written */
    public int crawl() throws Exception {
        List<String> ids = new PmidLister(api, config).list(config.max() > 0 ? config.max() : Integer.MAX_VALUE);
        log.info("PMIDs wanted: " + ids.size());

        try (JsonlArticleStore store = new JsonlArticleStore(config.outputFile(), config.name(), config.identity(), config.resume())) {
            List<String> missing = ids.stream().filter(id -> !store.contains(id)).toList();
            log.info("Already stored: " + store.existing() + ", to download: " + missing.size());
            for (int start = 0; start < missing.size(); start += config.batchSize()) {
                List<String> batch = missing.subList(start, Math.min(start + config.batchSize(), missing.size()));
                for (Article a : parser.parse(api.fetchXml(batch))) {
                    store.write(a);
                }
                store.flush();
                log.info("Downloaded " + (start + batch.size()) + " / " + missing.size());
            }
            return store.written();
        }
    }
}
