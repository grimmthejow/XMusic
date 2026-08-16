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
import java.util.List;

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

    private static List<LyricLine> processParagraph(Element p, String mainVocalistId, String divAgentId) {
        List<LyricLine> results = new ArrayList<>();
        String lineAgent = p.getAttributeNS(NS_TTM, "agent");

        if (lineAgent == null || lineAgent.isEmpty()) {
            lineAgent = divAgentId;
        }

        int vocalType = (lineAgent != null && !lineAgent.isEmpty() && !lineAgent.equals(mainVocalistId)) ? 2 : 1;
        int pStart = (int) parseTimestamp(p.getAttribute("begin"));
        int pEnd = (int) parseTimestamp(p.getAttribute("end"));
        String role = p.getAttributeNS(NS_TTM, "role");

        if ("x-instrumental".equals(role) || "instrumental".equalsIgnoreCase(lineAgent)) {
            if (pEnd - pStart >= 5000) {
                LyricLine breakLine = new LyricLine(pStart, new SpannableString(""), new ArrayList<>());
                breakLine.endTime = pEnd;
                breakLine.isWaitingDots = true;
                results.add(breakLine);
            }
            return results;
        }

        List<Node> mainNodes = new ArrayList<>();
        List<Node> bgNodes = new ArrayList<>();

        NodeList children = p.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            extractNodes(children.item(i), false, mainNodes, bgNodes);
        }

        boolean hasMain = !mainNodes.isEmpty();

        if (!hasMain && bgNodes.isEmpty()) {
            String text = p.getTextContent();
            if (text != null) {
                text = text.trim();
            }
            if (text != null && !text.isEmpty()) {
                String prefix = vocalType == 2 ? "v2: " : "";
                LyricLine line = LyricsParser.processContent(prefix + text, pStart, 0);
                if (line != null) {
                    line.endTime = pEnd;
                    results.add(line);
                }
            }
            return results;
        }

        if (hasMain) {
            results.add(assembleLine(mainNodes, pStart, pEnd, vocalType, false, false));
        }

        if (!bgNodes.isEmpty()) {
            int bgStart = pStart;
            for (Node n : bgNodes) {
                if (n.getNodeType() == Node.ELEMENT_NODE) {
                    bgStart = (int) parseTimestamp(((Element) n).getAttribute("begin"));
                    break;
                }
            }
            results.add(assembleLine(bgNodes, bgStart, pEnd, vocalType, true, hasMain));
        }

        return results;
    }

    private static void extractNodes(Node node, boolean inBg, List<Node> mainNodes, List<Node> bgNodes) {
        if (node.getNodeType() == Node.TEXT_NODE) {
            if (inBg) {
                bgNodes.add(node);
            } else {
                mainNodes.add(node);
            }
            return;
        }

        if (node.getNodeType() == Node.ELEMENT_NODE) {
            Element e = (Element) node;
            boolean isBg = inBg || "x-bg".equals(e.getAttributeNS(NS_TTM, "role"));

            boolean hasChildSpans = false;
            NodeList children = e.getChildNodes();
            for (int i = 0; i < children.getLength(); i++) {
                if (children.item(i).getNodeType() == Node.ELEMENT_NODE && "span".equals(children.item(i).getLocalName())) {
                    hasChildSpans = true;
                    break;
                }
            }

            if ("span".equals(e.getLocalName()) && !hasChildSpans && e.hasAttribute("begin")) {
                if (isBg) {
                    bgNodes.add(e);
                } else {
                    mainNodes.add(e);
                }
            } else {
                for (int i = 0; i < children.getLength(); i++) {
                    extractNodes(children.item(i), isBg, mainNodes, bgNodes);
                }
            }
        }
    }

    private static LyricLine assembleLine(List<Node> nodes, int lineStart, int lineEnd, int vocalType, boolean isBg, boolean isLinkedBg) {
        List<LyricWord> words = new ArrayList<>();
        StringBuilder fullLineText = new StringBuilder();
        int cursor = 0;

        LyricWord currentWord = null;

        for (int i = 0; i < nodes.size(); i++) {
            Node node = nodes.get(i);

            if (node.getNodeType() == Node.TEXT_NODE) {
                String text = node.getTextContent();
                text = text.replace("\n", "").replace("\t", "").replace("\r", "");
                if (isBg) {
                    text = text.replace("(", "").replace(")", "");
                }

                if (text.isEmpty()) continue;

                fullLineText.append(text);
                cursor += text.length();

                if (text.contains(" ")) {
                    currentWord = null;
                }
            } else if (node.getNodeType() == Node.ELEMENT_NODE) {
                Element span = (Element) node;
                String text = span.getTextContent();
                if (isBg) {
                    text = text.replace("(", "").replace(")", "");
                }

                if (text.isEmpty()) continue;

                int start = (int) parseTimestamp(span.getAttribute("begin"));
                int end = (int) parseTimestamp(span.getAttribute("end"));

                if (end == 0) {
                    for (int j = i + 1; j < nodes.size(); j++) {
                        if (nodes.get(j).getNodeType() == Node.ELEMENT_NODE) {
                            end = (int) parseTimestamp(((Element) nodes.get(j)).getAttribute("begin"));
                            break;
                        }
                    }
                    if (end == 0) {
                        end = lineEnd > 0 ? lineEnd : start + 500;
                    }
                }

                if (currentWord == null) {
                    currentWord = new LyricWord(cursor);
                    words.add(currentWord);
                }

                int relStart = cursor - currentWord.startIndex;
                LyricSyllable syllable = new LyricSyllable(start, text, relStart);
                syllable.endTime = end;
                currentWord.syllables.add(syllable);

                fullLineText.append(text);
                cursor += text.length();

                if (text.endsWith(" ")) {
                    currentWord = null;
                }
            }
        }

        LyricLine lyricLine = new LyricLine(lineStart, new SpannableString(fullLineText.toString()), words);
        lyricLine.vocalType = vocalType;
        lyricLine.isBackground = isBg;
        lyricLine.isLinkedBg = isLinkedBg;

        if (lineEnd > 0) {
            lyricLine.endTime = lineEnd;
        } else if (!words.isEmpty()) {
            List<LyricSyllable> lastWordSyllables = words.get(words.size() - 1).syllables;
            lyricLine.endTime = lastWordSyllables.get(lastWordSyllables.size() - 1).endTime;
        } else {
            lyricLine.endTime = lineStart;
        }

        return lyricLine;
    }

    private static long parseTimestamp(String timespan) {
        if (timespan == null || timespan.isEmpty()) return 0;
        timespan = timespan.trim();
        try {
            if (timespan.endsWith("ms")) {
                return (long) Float.parseFloat(timespan.replace("ms", ""));
            }
            if (timespan.endsWith("s")) {
                return (long) (Float.parseFloat(timespan.replace("s", "")) * 1000);
            }
            if (timespan.endsWith("m")) {
                return (long) (Float.parseFloat(timespan.replace("m", "")) * 60000);
            }

            String[] parts = timespan.split(":");
            if (parts.length == 3) {
                return Math.round((Integer.parseInt(parts[0]) * 3600 + Integer.parseInt(parts[1]) * 60 + Double.parseDouble(parts[2])) * 1000);
            } else if (parts.length == 2) {
                return Math.round((Integer.parseInt(parts[0]) * 60 + Double.parseDouble(parts[1])) * 1000);
            } else {
                return Math.round(Double.parseDouble(timespan) * 1000);
            }
        } catch (Exception e) {
            return 0;
        }
    }
}