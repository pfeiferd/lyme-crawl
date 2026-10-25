package de.hsheilbronn.lymecrawl;

/** Process entry point; the only static method of the project. */
public final class Main {

    private Main() {
    }

    public static void main(String[] args) throws Exception {
        // Commons Logging falls back to java.util.logging; make its default output one line per record.
        System.setProperty("java.util.logging.SimpleFormatter.format", "%1$tT %4$-7s %3$s - %5$s%6$s%n");
        new CrawlApplication(new ConfigLoader(System.getProperties(), System.getenv())).run();
    }
}
