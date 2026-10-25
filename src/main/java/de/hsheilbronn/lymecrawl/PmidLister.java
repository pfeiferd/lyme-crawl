package de.hsheilbronn.lymecrawl;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Lists the PMIDs matching the configured search.
 * <p>
 * PubMed returns at most 9,999 PMIDs per search. If there are more hits, the publication date range is
 * bisected until every slice fits, and the slices are searched separately.
 */
public class PmidLister {

    /** NCBI's maximum number of retrievable PMIDs per search. */
    public static final int NCBI_LIMIT = 9_999;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");
    private static final LocalDate EARLIEST = LocalDate.of(1800, 1, 1);

    private final PubmedApi api;
    private final CrawlConfig config;
    private final int limit;
    private final Log log = LogFactory.getLog(getClass());

    public PmidLister(PubmedApi api, CrawlConfig config) {
        this(api, config, NCBI_LIMIT);
    }

    PmidLister(PubmedApi api, CrawlConfig config, int limit) {
        this.api = api;
        this.config = config;
        this.limit = limit;
    }

    /** @return at most {@code wanted} distinct PMIDs */
    public List<String> list(int wanted) throws IOException, InterruptedException {
        PubmedApi.IdPage all = config.hasDateRange()
                ? api.searchIds(config.query(), config.minDate(), config.maxDate(), limit)
                : api.searchIds(config.query(), null, null, limit);
        log.info("PubMed matches: " + all.count());
        if (all.count() <= limit) return all.ids().stream().limit(wanted).toList();

        log.info("More than " + limit + " matches, splitting the date range");
        Set<String> ids = new LinkedHashSet<>();
        LocalDate from = config.hasDateRange() ? periodStart(config.minDate()) : EARLIEST;
        LocalDate to = config.hasDateRange() ? periodEnd(config.maxDate()) : LocalDate.now().plusYears(1);
        collect(from, to, ids, wanted);
        return ids.stream().limit(wanted).toList();
    }

    private void collect(LocalDate from, LocalDate to, Set<String> ids, int wanted)
            throws IOException, InterruptedException {
        if (ids.size() >= wanted) return;
        PubmedApi.IdPage page = api.searchIds(config.query(), DATE.format(from), DATE.format(to), limit);
        if (page.count() <= limit) {
            ids.addAll(page.ids());
            return;
        }
        if (from.equals(to)) {
            throw new IOException("More than " + limit + " matches on the single day " + from + "; narrow the query");
        }
        LocalDate mid = from.plusDays(ChronoUnit.DAYS.between(from, to) / 2);
        collect(mid.plusDays(1), to, ids, wanted); // newest first
        collect(from, mid, ids, wanted);
    }

    /** "2020" -> 2020-01-01, "2020/03" -> 2020-03-01, "2020/03/15" -> 2020-03-15 */
    private LocalDate periodStart(String date) {
        String[] p = date.split("/");
        return LocalDate.of(Integer.parseInt(p[0]), p.length > 1 ? Integer.parseInt(p[1]) : 1,
                p.length > 2 ? Integer.parseInt(p[2]) : 1);
    }

    /** "2020" -> 2020-12-31, "2020/03" -> 2020-03-31, "2020/03/15" -> 2020-03-15 */
    private LocalDate periodEnd(String date) {
        String[] p = date.split("/");
        int year = Integer.parseInt(p[0]);
        if (p.length > 2) return LocalDate.of(year, Integer.parseInt(p[1]), Integer.parseInt(p[2]));
        if (p.length > 1) return YearMonth.of(year, Integer.parseInt(p[1])).atEndOfMonth();
        return LocalDate.of(year, 12, 31);
    }
}
