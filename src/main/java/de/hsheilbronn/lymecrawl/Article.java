package de.hsheilbronn.lymecrawl;

import java.util.List;

/** A PubMed record reduced to the fields relevant for this crawler. */
public record Article(
        String pmid,
        String title,
        String abstractText,
        String journal,
        Integer year,
        List<String> authors,
        String doi,
        String pmcid,
        List<String> meshTerms,
        List<String> publicationTypes) {
}
