package de.hsheilbronn.lymecrawl;

import org.junit.jupiter.api.Test;

import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PmidListerTest {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy/MM/dd");

    /** 40 articles, one per week from 2020-01-01, with a search limit that is far lower than NCBI's. */
    static class DatedApi implements PubmedApi {
        final List<LocalDate> dates = IntStream.range(0, 40)
                .mapToObj(i -> LocalDate.of(2020, 1, 1).plusWeeks(i)).toList();
        int calls;

        public IdPage searchIds(String term, String min, String max, int retmax) {
            calls++;
            LocalDate from = min == null ? LocalDate.MIN : LocalDate.parse(min, DATE);
            LocalDate to = max == null ? LocalDate.MAX : LocalDate.parse(max, DATE);
            List<String> hits = new ArrayList<>();
            for (int i = 0; i < dates.size(); i++) {
                if (!dates.get(i).isBefore(from) && !dates.get(i).isAfter(to)) hits.add(Integer.toString(i));
            }
            return new IdPage(hits.size(), hits.subList(0, Math.min(hits.size(), retmax)));
        }

        public String fetchXml(List<String> pmids) {
            throw new UnsupportedOperationException();
        }
    }

    private CrawlConfig config(String from, String to) {
        return new CrawlConfig("t", "q", null, null, from, to, 0, 10, Path.of("x.jsonl"), true);
    }

    @Test
    void splitsDateRangeUntilSlicesFit() throws Exception {
        DatedApi api = new DatedApi();
        List<String> ids = new PmidLister(api, config(null, null), 7).list(Integer.MAX_VALUE);
        assertEquals(40, ids.size());
        assertEquals(40, new HashSet<>(ids).size());
    }

    @Test
    void respectsConfiguredDateRange() throws Exception {
        List<String> ids = new PmidLister(new DatedApi(), config("2020/01/01", "2020/02/29"), 3).list(Integer.MAX_VALUE);
        assertEquals(9, ids.size()); // weeks starting 01-01 .. 02-26
    }

    @Test
    void stopsEarlyAtWanted() throws Exception {
        DatedApi api = new DatedApi();
        assertEquals(5, new PmidLister(api, config(null, null), 7).list(5).size());
    }

    @Test
    void singleDayOverLimitFails() {
        PubmedApi tooDense = new PubmedApi() {
            public IdPage searchIds(String t, String a, String b, int r) {
                return new IdPage(100, List.of());
            }

            public String fetchXml(List<String> p) {
                return "";
            }
        };
        assertThrows(java.io.IOException.class, () -> new PmidLister(tooDense, config("2020/01/01", "2020/01/03"), 7).list(10));
    }

    @Test
    void noSplitWhenEverythingFits() throws Exception {
        DatedApi api = new DatedApi();
        assertEquals(40, new PmidLister(api, config(null, null), 100).list(Integer.MAX_VALUE).size());
        assertEquals(1, api.calls);
    }
}
