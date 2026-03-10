package com.xapps.media.xmusic.utils;

import android.text.SpannableString;
import com.xapps.media.xmusic.models.LyricLine;
import com.xapps.media.xmusic.models.LyricSyllable;
import com.xapps.media.xmusic.models.LyricWord;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;
import org.xml.sax.InputSource;

import java.io.StringReader;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;

public class TtmlParser {

    private static final String NS_TTML = "http://www.w3.org/ns/ttml";
    private static final String NS_TTM = "http://www.w3.org/ns/ttml#metadata";
    private static final String NS_XML = "http://www.w3.org/XML/1998/namespace";

    private static String findMainVocalistId(Element root) {
        NodeList agents = root.getElementsByTagNameNS(NS_TTM, "agent");
        for (int i = 0; i < agents.getLength(); i++) {
            Element agent = (Element) agents.item(i);
            if ("person".equals(agent.getAttribute("type"))) {
                String id = agent.getAttributeNS(NS_XML, "id");
                if (id != null && !id.isEmpty()) return id;
            }
        }
        return "v1";
    }

    public static List<LyricLine> parse(String ttmlContent) {
        List<LyricLine> lyricLines = new ArrayList<>();
        if (ttmlContent == null || ttmlContent.isEmpty()) return lyricLines;

        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(new InputSource(new StringReader(ttmlContent)));

            Element root = doc.getDocumentElement();
            String mainVocalistId = findMainVocalistId(root);

            NodeList bodies = root.getElementsByTagNameNS(NS_TTML, "body");
            if (bodies.getLength() == 0) return lyricLines;
            Element body = (Element) bodies.item(0);

            NodeList divs = body.getElementsByTagNameNS(NS_TTML, "div");
            for (int i = 0; i < divs.getLength(); i++) {
                Element div = (Element) divs.item(i);
                String divAgentId = div.getAttributeNS(NS_TTM, "agent");
                NodeList paragraphs = div.getElementsByTagNameNS(NS_TTML, "p");

                for (int j = 0; j < paragraphs.getLength(); j++) {
                    Element p = (Element) paragraphs.item(j);
                    lyricLines.addAll(processParagraph(p, mainVocalistId, divAgentId));
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return lyricLines;
    }

    private static List<LyricLine> processParagraph(
            Element p, String mainVocalistId, String divAgentId) {
        List<LyricLine> results = new ArrayList<>();
        String lineAgent = p.getAttributeNS(NS_TTM, "agent");
        if (lineAgent == null || lineAgent.isEmpty()) lineAgent = divAgentId;
        int vocalType =
                (lineAgent != null && !lineAgent.isEmpty() && !lineAgent.equals(mainVocalistId))
                        ? 2
                        : 1;

        List<Element> mainSpans = new ArrayList<>();
        List<Element> bgSpans = new ArrayList<>();

        NodeList children = p.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child instanceof Element) {
                Element e = (Element) child;
                if ("span".equals(e.getLocalName())) {
                    if ("x-bg".equals(e.getAttributeNS(NS_TTM, "role"))) {
                        findSpansRecursive(e, bgSpans);
                    } else {
                        mainSpans.add(e);
                    }
                }
            }
        }

        if (!mainSpans.isEmpty()) {
            int start = (int) parseTimestamp(p.getAttribute("begin"));
            results.add(assembleLine(mainSpans, start, vocalType, false));
        }

        if (!bgSpans.isEmpty()) {
            int start = (int) parseTimestamp(bgSpans.get(0).getAttribute("begin"));
            results.add(assembleLine(bgSpans, start, vocalType, true));
        }

        return results;
    }

    private static LyricLine assembleLine(
        List<Element> spans, int lineStart, int vocalType, boolean isBg) {
    List<LyricWord> words = new ArrayList<>();
    StringBuilder fullLineText = new StringBuilder();
    int cursor = 0;

    for (int i = 0; i < spans.size(); i++) {
        Element span = spans.get(i);
        String text = span.getTextContent();
        if (isBg) text = text.replace("(", "").replace(")", "");
        if (text.isEmpty()) continue;

        int start = (int) parseTimestamp(span.getAttribute("begin"));
        
        int end;
        if (i < spans.size() - 1) {
            // Use the next span's begin as this span's end for continuity
            end = (int) parseTimestamp(spans.get(i + 1).getAttribute("begin"));
        } else {
            // Last span uses its own end attribute or a default duration
            String endAttr = span.getAttribute("end");
            if (!endAttr.isEmpty()) {
                end = (int) parseTimestamp(endAttr);
            } else {
                end = start + 300; 
            }
        }

        LyricWord word = new LyricWord(cursor);
        LyricSyllable syllable = new LyricSyllable(start, text, 0);
        syllable.endTime = end;
        word.syllables.add(syllable);
        words.add(word);

        fullLineText.append(text);
        cursor += text.length();

        if (i < spans.size() - 1 && !text.endsWith(" ")) {
            fullLineText.append(" ");
            cursor++;
        }
    }

    LyricLine lyricLine =
            new LyricLine(lineStart, new SpannableString(fullLineText.toString()), words);
    lyricLine.vocalType = vocalType;
    lyricLine.isBackground = isBg;
    return lyricLine;
}


    private static void findBackgroundSpans(Node node, Set<Node> result, boolean isBgParent) {
        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element e = (Element) node;
            if ("x-bg".equals(e.getAttributeNS(NS_TTM, "role"))) isBgParent = true;
            if (isBgParent && "span".equals(e.getLocalName())) result.add(e);
        }
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            findBackgroundSpans(children.item(i), result, isBgParent);
        }
    }

    private static void findSpansRecursive(Node node, List<Element> result) {
        NodeList children = node.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            Node child = children.item(i);
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element e = (Element) child;
                if ("span".equals(e.getLocalName()) && e.hasAttribute("begin")) {
                    result.add(e);
                }
                findSpansRecursive(child, result);
            }
        }
    }

    private static long parseTimestamp(String timespan) {
        if (timespan == null || timespan.isEmpty()) return 0;
        timespan = timespan.trim();
        try {
            if (timespan.endsWith("ms")) return (long) Float.parseFloat(timespan.replace("ms", ""));
            if (timespan.endsWith("s"))
                return (long) (Float.parseFloat(timespan.replace("s", "")) * 1000);
            if (timespan.endsWith("m"))
                return (long) (Float.parseFloat(timespan.replace("m", "")) * 60000);

            String[] parts = timespan.split(":");
            if (parts.length == 3) {
                return Math.round(
                        (Integer.parseInt(parts[0]) * 3600
                                        + Integer.parseInt(parts[1]) * 60
                                        + Double.parseDouble(parts[2]))
                                * 1000);
            } else if (parts.length == 2) {
                return Math.round(
                        (Integer.parseInt(parts[0]) * 60 + Double.parseDouble(parts[1])) * 1000);
            } else {
                return Math.round(Double.parseDouble(timespan) * 1000);
            }
        } catch (Exception e) {
            return 0;
        }
    }
}
