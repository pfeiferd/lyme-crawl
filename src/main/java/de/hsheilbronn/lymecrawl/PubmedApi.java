package de.hsheilbronn.lymecrawl;

import java.io.IOException;
import java.util.List;

/** The part of the NCBI E-utilities the crawler needs. */
public interface PubmedApi {

    /** @param count total number of hits, which may exceed {@code ids.size()} */
    record IdPage(int count, List<String> ids) {
    }

    /**
     * Searches and returns the total hit count with the first {@code retmax} PMIDs.
     *
     * @param minDate publication date range start (YYYY/MM/DD), or null for no date restriction
     *                (then {@code maxDate} is null too)
     */
    IdPage searchIds(String term, String minDate, String maxDate, int retmax) throws IOException, InterruptedException;

    /** Fetches the given records as PubMed XML ({@code PubmedArticleSet}). */
    String fetchXml(List<String> pmids) throws IOException, InterruptedException;
}
