package de.hsheilbronn.lymecrawl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Minimal client for the NCBI E-utilities (ESearch and EFetch).
 * See https://www.nlm.nih.gov/dataguide/eutilities/utilities.html
 */
public class EutilsClient implements PubmedApi {

    private static final String BASE = "https://eutils.ncbi.nlm.nih.gov/entrez/eutils/";
    private static final String DB = "pubmed";
    private static final String TOOL = "lyme-crawl";
    private static final int MAX_ATTEMPTS = 5;

    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private final ObjectMapper json = new ObjectMapper();
    private final Log log = LogFactory.getLog(getClass());
    private final String baseUrl;
    private final String apiKey;
    private final String email;
    private final RateLimiter rateLimiter;

    /** NCBI allows 3 requests/s without and 10 requests/s with an API key. */
    public EutilsClient(CrawlConfig config) {
        this(BASE, config.apiKey(), config.email(),
                new RateLimiter(Duration.ofMillis(config.hasApiKey() ? 110 : 350)));
    }

    EutilsClient(String baseUrl, String apiKey, String email, RateLimiter rateLimiter) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
        this.email = email;
        this.rateLimiter = rateLimiter;
    }

    @Override
    public IdPage searchIds(String term, String minDate, String maxDate, int retmax)
            throws IOException, InterruptedException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("db", DB);
        p.put("term", term);
        p.put("retmax", Integer.toString(retmax));
        p.put("retmode", "json");
        if (minDate != null && maxDate != null) {
            p.put("datetype", "pdat");
            p.put("mindate", minDate);
            p.put("maxdate", maxDate);
        }
        JsonNode r = json.readTree(get("esearch.fcgi", p)).path("esearchresult");
        if (r.has("ERROR")) {
            throw new IOException("ESearch error: " + r.get("ERROR").asText());
        }
        List<String> ids = new ArrayList<>();
        r.path("idlist").forEach(n -> ids.add(n.asText()));
        return new IdPage(r.path("count").asInt(), ids);
    }

    @Override
    public String fetchXml(List<String> pmids) throws IOException, InterruptedException {
        Map<String, String> p = new LinkedHashMap<>();
        p.put("db", DB);
        p.put("id", String.join(",", pmids));
        p.put("rettype", "abstract");
        p.put("retmode", "xml");
        return get("efetch.fcgi", p);
    }

    private String get(String endpoint, Map<String, String> params) throws IOException, InterruptedException {
        Map<String, String> all = new LinkedHashMap<>(params);
        all.put("tool", TOOL);
        if (email != null && !email.isBlank()) all.put("email", email);
        if (apiKey != null && !apiKey.isBlank()) all.put("api_key", apiKey);
        String query = all.entrySet().stream()
                .map(e -> enc(e.getKey()) + "=" + enc(e.getValue()))
                .collect(Collectors.joining("&"));
        // POST keeps long queries and WebEnv tokens out of URL length limits.
        HttpRequest req = HttpRequest.newBuilder(URI.create(baseUrl + endpoint))
                .timeout(Duration.ofSeconds(120))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(query))
                .build();

        IOException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            rateLimiter.await();
            boolean retryable = true;
            try {
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
                int status = resp.statusCode();
                if (status == 200) return resp.body();
                last = new IOException("HTTP " + status + " from " + endpoint + ": " + abbreviate(resp.body()));
                retryable = status == 429 || status >= 500;
            } catch (IOException e) {
                last = e;
            }
            if (!retryable) throw last;
            log.warn(last.getMessage() + " (attempt " + attempt + "/" + MAX_ATTEMPTS + ")");
            Thread.sleep(1000L * attempt);
        }
        throw last;
    }

    private String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    private String abbreviate(String s) {
        return s.length() > 300 ? s.substring(0, 300) + "..." : s;
    }
}
