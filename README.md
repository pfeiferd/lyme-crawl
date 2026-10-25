# lyme-crawl

Maven/Java crawler for PubMed publications on Lyme disease and *Borrelia burgdorferi*, built on the
[NCBI E-utilities](https://www.nlm.nih.gov/dataguide/eutilities/utilities.html).

Flow: `ESearch` (with `usehistory=y`) stores the result set on NCBI's history server; `PmidLister` lists the PMIDs (bisecting the publication date range if there are more than the 9,999 NCBI returns per search),
then fetches the records not stored yet in batches and the PubMed XML is parsed into JSON Lines (PMID, title, abstract, journal, year,
authors, DOI, PMCID, MeSH terms, publication types).

## Run

    cp crawl.local.properties.example crawl.local.properties   # set your email / API key
    mvn exec:java

Override single values on the command line:

    mvn exec:java -Dcrawl.max=50 -Dcrawl.from=2020/01/01 -Dcrawl.to=2024/12/31
    mvn exec:java -Dcrawl.config=/path/to/other.properties

## Configuration

All settings are properties. They are merged in this order (later wins):

1. `src/main/resources/crawl.properties` – bundled defaults, every key documented there
2. `crawl.local.properties` in the working directory (git-ignored), or the file named by `-Dcrawl.config=...`
   (an explicitly named file must exist)
3. JVM system properties (`-Dkey=value`) for any `crawl.*` / `ncbi.*` key

An empty value means "not set".

| Key | Default | Meaning |
|---|---|---|
| `crawl.name` | `lyme` | Label of the crawl: part of the output folder name and of the file header. Letters, digits, `.`, `_`, `-`. |
| `crawl.query` | MeSH + title/abstract terms for Lyme disease / *B. burgdorferi* | PubMed search syntax, as in the web search. |
| `crawl.from`, `crawl.to` | empty | Publication date range (`YYYY`, `YYYY/MM`, `YYYY/MM/DD`). Set both or neither. |
| `crawl.max` | `0` | Maximum records to download; `0` = all matches. Use a small number to test. |
| `crawl.batch.size` | `200` | Records per EFetch request (1–10000). |
| `crawl.output` | `data/articles.jsonl` | Base path of the JSON Lines output; name and config hash are inserted as a subfolder (`data/<name>-<hash>/articles.jsonl`). |
| `crawl.resume` | `true` | `true`: keep the existing output file and download only missing PMIDs. `false`: overwrite it. |
| `ncbi.email` | empty → env `NCBI_EMAIL` | Contact address sent to NCBI with each request. |
| `ncbi.api.key` | empty → env `NCBI_API_KEY` | NCBI API key. Raises the client's rate limit from 3 to 10 requests/s. |

## Resuming

The crawl is resumable by default. A run first lists the PMIDs of all hits (cheap: a few requests), then
compares them with the PMIDs already in the output file and downloads only the missing ones, flushing after
every batch. So you can simply start the same command again after an interruption, and later again to pick up
newly published articles. A half-written last line left by a crash is discarded automatically.

### Config hash

Query and date range decide what a crawl yields. They are hashed (SHA-256, first 8 hex digits; whitespace in
the query is ignored) and the hash, preceded by `crawl.name`, becomes a subfolder of the output path (`data/lyme-3f9a1c07/articles.jsonl`), so every configuration has its own
file and results of different configurations never mix. The name is only a label and does not enter the hash. Changing `crawl.max`, `crawl.batch.size`, the API key
etc. does not change the hash.

The first line of each file is a header holding the name, the hash and the configuration it was made with:

    {"name":"lyme","configHash":"3f9a1c07","config":{"query":"...","from":null,"to":null},"createdAt":"2026-10-25T10:50:00Z"}

followed by one article per line. On resume the header is verified; if it does not match the current
configuration (for example the file was moved or edited by hand) the crawl stops with an error instead of
continuing. `crawl.resume=false` recreates the file of the current configuration.

## Layout

- `ConfigLoader` – layered property loading; `CrawlConfig` – validated settings; `PropertyReader` – typed property access
- `PubmedApi` – interface for search / fetch XML; `PmidLister` – lists all PMIDs, splitting by date range when NCBI's 9,999 limit is exceeded; `EutilsClient` – its HTTP implementation with retries; `RateLimiter` – request throttling
- `PubmedXmlParser` – EFetch XML to `Article`
- `Crawler` – search, diff against stored PMIDs, batch fetch; `JsonlArticleStore` – resumable JSONL file with config header; `CrawlIdentity` – query/date range and their hash; `CrawlApplication` – wiring
- `Main` – entry point, the only static method (run through the exec plugin)

`mvn package` also builds a runnable jar (`java -jar target/lyme-crawl-0.1.0-SNAPSHOT.jar`), which reads the same properties.

## Next steps

Full texts: `Article.pmcid` marks records available in PubMed Central; they can be fetched with
`efetch db=pmc` (open-access subset) or located through `elink`.

## Logging

Output goes through Apache Commons Logging. Without further setup it uses `java.util.logging` (INFO to stderr,
one line per record). To use another backend, e.g. Log4j 2 via `log4j-jcl`, add it as a dependency; Commons
Logging picks it up automatically.
