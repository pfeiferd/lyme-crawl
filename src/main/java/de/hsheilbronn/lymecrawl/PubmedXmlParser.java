package de.hsheilbronn.lymecrawl;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpressionException;
import javax.xml.xpath.XPathFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.List;

/**
 * Converts EFetch PubMed XML ({@code PubmedArticleSet}) into {@link Article}s.
 * Not thread-safe (holds an {@link XPath}); use one instance per thread.
 */
public class PubmedXmlParser {

    private final DocumentBuilderFactory documents;
    private final XPath xpath = XPathFactory.newInstance().newXPath();

    public PubmedXmlParser() throws ParserConfigurationException {
        documents = DocumentBuilderFactory.newInstance();
        // PubMed XML carries a DOCTYPE; never resolve it or any external entity.
        documents.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        documents.setFeature("http://apache.org/xml/features/nonvalidating/load-external-dtd", false);
        documents.setFeature("http://xml.org/sax/features/external-general-entities", false);
        documents.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
    }

    public List<Article> parse(String xml) throws Exception {
        Document doc = documents.newDocumentBuilder().parse(new InputSource(new StringReader(xml)));
        List<Article> out = new ArrayList<>();
        for (Node n : nodes("/PubmedArticleSet/PubmedArticle", doc)) {
            out.add(toArticle((Element) n));
        }
        return out;
    }

    private Article toArticle(Element a) throws XPathExpressionException {
        List<String> authors = new ArrayList<>();
        for (Node n : nodes("MedlineCitation/Article/AuthorList/Author", a)) {
            String last = text("LastName", n);
            String fore = text("ForeName", n);
            String name = last.isEmpty() ? text("CollectiveName", n) : (fore + " " + last).trim();
            if (!name.isEmpty()) authors.add(name);
        }
        return new Article(
                text("MedlineCitation/PMID", a),
                text("MedlineCitation/Article/ArticleTitle", a),
                abstractText(a),
                text("MedlineCitation/Article/Journal/Title", a),
                year(a),
                authors,
                text("PubmedData/ArticleIdList/ArticleId[@IdType='doi']", a),
                text("PubmedData/ArticleIdList/ArticleId[@IdType='pmc']", a),
                texts("MedlineCitation/MeshHeadingList/MeshHeading/DescriptorName", a),
                texts("MedlineCitation/Article/PublicationTypeList/PublicationType", a));
    }

    /** Structured abstracts have several labelled sections. */
    private String abstractText(Element a) throws XPathExpressionException {
        StringBuilder abs = new StringBuilder();
        for (Node n : nodes("MedlineCitation/Article/Abstract/AbstractText", a)) {
            String label = ((Element) n).getAttribute("Label");
            if (abs.length() > 0) abs.append("\n");
            if (!label.isEmpty()) abs.append(label).append(": ");
            abs.append(n.getTextContent().strip());
        }
        return abs.toString();
    }

    private Integer year(Element a) throws XPathExpressionException {
        String year = text("MedlineCitation/Article/Journal/JournalIssue/PubDate/Year", a);
        if (year.isEmpty()) {
            // MedlineDate looks like "2019 Jan-Feb"
            String md = text("MedlineCitation/Article/Journal/JournalIssue/PubDate/MedlineDate", a);
            if (md.length() >= 4) year = md.substring(0, 4);
        }
        return year.matches("\\d{4}") ? Integer.valueOf(year) : null;
    }

    private String text(String expr, Node ctx) throws XPathExpressionException {
        return ((String) xpath.evaluate(expr, ctx, XPathConstants.STRING)).strip();
    }

    private List<String> texts(String expr, Node ctx) throws XPathExpressionException {
        List<String> l = new ArrayList<>();
        for (Node n : nodes(expr, ctx)) l.add(n.getTextContent().strip());
        return l;
    }

    private List<Node> nodes(String expr, Node ctx) throws XPathExpressionException {
        NodeList nl = (NodeList) xpath.evaluate(expr, ctx, XPathConstants.NODESET);
        List<Node> l = new ArrayList<>(nl.getLength());
        for (int i = 0; i < nl.getLength(); i++) l.add(nl.item(i));
        return l;
    }
}
