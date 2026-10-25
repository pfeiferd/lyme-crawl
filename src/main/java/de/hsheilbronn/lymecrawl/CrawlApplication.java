package de.hsheilbronn.lymecrawl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

/** Wires configuration, client, parser and crawler together and runs one crawl. */
public class CrawlApplication {

    private final ConfigLoader configLoader;
    private final Log log = LogFactory.getLog(getClass());

    public CrawlApplication(ConfigLoader configLoader) {
        this.configLoader = configLoader;
    }

    public void run() throws Exception {
        CrawlConfig config = configLoader.load();
        log.info("Crawl " + config.name() + ", config hash " + config.identity().hash() + ", output " + config.outputFile());
        Crawler crawler = new Crawler(config, new EutilsClient(config), new PubmedXmlParser());
        int n = crawler.crawl();
        log.info("Wrote " + n + " articles to " + config.outputFile());
    }
}
