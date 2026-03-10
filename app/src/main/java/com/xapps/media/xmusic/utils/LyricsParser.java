package com.xapps.media.xmusic.lyric;

import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import android.util.Log;
import com.xapps.media.xmusic.models.LyricLine;
import com.xapps.media.xmusic.models.LyricSyllable;
import com.xapps.media.xmusic.models.LyricWord;
import com.xapps.media.xmusic.utils.TtmlParser;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LyricsParser {

    private static final String TAG = "LyricsParserDBG";

    private static final Pattern OFFSET_PATTERN =
            Pattern.compile("\\[offset:([+-]?\\d+)\\]", Pattern.CASE_INSENSITIVE);
    private static final Pattern METADATA_IGNORE_PATTERN =
            Pattern.compile("^\\[(by|ar|ti|al|au|length|re):.*\\]$", Pattern.CASE_INSENSITIVE);
    private static final Pattern LINE_TIME_PATTERN =
            Pattern.compile("\\[(\\d{2}):(\\d{2})\\.(\\d{2,3})\\](.*)");
    private static final Pattern WORD_TIME_PATTERN =
            Pattern.compile("<(\\d{2}):(\\d{2})\\.(\\d{2,3})>([^<]*)");
    private static final Pattern WORD_SPACING_PATTERN = Pattern.compile("(\\S+\\s*)");

    public interface LyricsListener {
        void onParsed(LyricsResult result);
    }

    public static class LyricsResult {
        public final List<LyricLine> lines;
        public final boolean isSynced;

        public LyricsResult(List<LyricLine> lines, boolean isSynced) {
            this.lines = lines;
            this.isSynced = isSynced;
        }
    }

    public static void parse(String lyricsString, LyricsListener listener) {
        new Thread(
                        () -> {
                            List<LyricLine> lines;
                            boolean isSynced = false;

                            if (lyricsString == null || lyricsString.isEmpty()) {
                                lines = Collections.emptyList();
                            } else {
                                try (InputStream is =
                                        new ByteArrayInputStream(
                                                lyricsString.getBytes(StandardCharsets.UTF_8))) {
                                    lines = parseInternal(is);

                                    for (LyricLine line : lines) {
                                        if (line.time > 0) {
                                            isSynced = true;
                                            break;
                                        }
                                    }
                                } catch (Exception e) {
                                    lines = Collections.emptyList();
                                }
                            }

                            final LyricsResult result = new LyricsResult(lines, isSynced);

                            new Handler(Looper.getMainLooper())
                                    .post(
                                            () -> {
                                                if (listener != null) listener.onParsed(result);
                                            });
                        })
                .start();
    }

    private static List<LyricLine> parseInternal(InputStream inputStream) {
    try (PushbackInputStream pb = new PushbackInputStream(inputStream, 20)) {
        int firstByte = pb.read();
        if (firstByte == -1) return Collections.emptyList();
        pb.unread(firstByte);

        List<LyricLine> result;
        if (firstByte == '<') {
            result = handleTtml(pb);
        } else {
            result = parseLrcStream(pb);
        }

        if (result != null && !result.isEmpty()) {
            finalizeSyllableTimings(result);

            Collections.sort(result, (l1, l2) -> {
                if (l1.time != l2.time) {
                    return Integer.compare(l1.time, l2.time);
                }
                return 0;
            });

            for (int i = 1; i < result.size(); i++) {
                LyricLine prev = result.get(i - 1);
                LyricLine current = result.get(i);
                
                if (current.time == prev.time && current.vocalType == prev.vocalType) {
                    if (!current.isBackground && !prev.isBackground) {
                        current.isRomaji = true;
                    }
                }
            }
        }
        return result != null ? result : Collections.emptyList();
    } catch (Exception e) {
        Log.e(TAG, "failure in parseInternal", e);
        return Collections.emptyList();
    }
}


    private static List<LyricLine> handleTtml(InputStream in) throws Exception {
        StringBuilder sb = new StringBuilder();
        try (BufferedReader reader =
                new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line);
            }
        }
        return TtmlParser.parse(sb.toString());
    }

    private static List<LyricLine> parseLrcStream(InputStream in) {
    List<LyricLine> result = new ArrayList<>();
    long globalOffset = 0;
    try (BufferedReader br = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8))) {
        String rawLine;
        while ((rawLine = br.readLine()) != null) {
            String line = rawLine.trim();
            if (line.isEmpty() || METADATA_IGNORE_PATTERN.matcher(line).matches()) continue;

            Matcher om = OFFSET_PATTERN.matcher(line);
            if (om.find()) {
                globalOffset = Long.parseLong(om.group(1));
                continue;
            }

            if (line.startsWith("[bg:") && line.endsWith("]")) {
                String inner = line.substring(4, line.length() - 1).trim();
                Matcher wm = WORD_TIME_PATTERN.matcher(inner);
                if (wm.find()) {
                    long startTime = parseTimestamp(wm.group(1), wm.group(2), wm.group(3)) + globalOffset;
                    LyricLine lyricLine = processContent("bg: " + inner, startTime, globalOffset);
                    if (lyricLine != null) result.add(lyricLine);
                }
                continue;
            }

            Matcher lm = LINE_TIME_PATTERN.matcher(line);
            if (lm.find()) {
                long startTime = parseTimestamp(lm.group(1), lm.group(2), lm.group(3)) + globalOffset;
                LyricLine lyricLine = processContent(lm.group(4).trim(), startTime, globalOffset);
                if (lyricLine != null) result.add(lyricLine);
            } else if (!line.startsWith("[")) {
                result.add(new LyricLine(0, new SpannableString(line), new ArrayList<>()));
            }
        }

        if (!result.isEmpty()) {
            finalizeSyllableTimings(result);

            // in-metadata position. ordering 
            Collections.sort(result, (l1, l2) -> {
                if (l1.time != l2.time) {
                    return Integer.compare(l1.time, l2.time);
                }
                return 0;
            });

            for (int i = 1; i < result.size(); i++) {
                LyricLine prev = result.get(i - 1);
                LyricLine current = result.get(i);

                if (current.time == prev.time && current.isSimpleLRC) {
                    if (!current.isBackground && !prev.isBackground) {
                        current.isRomaji = true;
                        current.vocalType = prev.vocalType;
                    }
                }
            }
        }
    } catch (Exception ignored) {}
    return result;
}


    private static LyricLine processContent(String content, long lineStartTime, long globalOffset) {
        List<LyricWord> words = new ArrayList<>();
        String t = content;
        int vocalType = 1;
        boolean isBackground = false;

        if (t.startsWith("bg:")) {
            isBackground = true;
            t = t.substring(3).trim();
        } else if (t.startsWith("[bg:") && t.endsWith("]")) {
            isBackground = true;
            t = t.substring(4, t.length() - 1).trim();
        }

        String lower = t.toLowerCase();
        if (lower.startsWith("v1:")) {
            vocalType = 1;
            t = t.substring(3).trim();
        } else if (lower.startsWith("v2:")) {
            vocalType = 2;
            t = t.substring(3).trim();
        }

        boolean isNonSpace = isNonSpaceLanguage(t);
        Matcher wm = WORD_TIME_PATTERN.matcher(t);
        List<Integer> timestamps = new ArrayList<>();
        List<String> fragments = new ArrayList<>();
        long explicitEnd = -1;

        while (wm.find()) {
            int ts = (int) (parseTimestamp(wm.group(1), wm.group(2), wm.group(3)) + globalOffset);
            String fragment = wm.group(4);
            if (fragment == null || fragment.isEmpty()) {
                explicitEnd = ts;
                continue;
            }
            timestamps.add(ts);
            fragments.add(fragment);
        }

        boolean allBackground = !fragments.isEmpty(); 
        for (String f : fragments) {
            String trimmed = f.trim();
            if (trimmed.isEmpty()) continue;

            if (!(trimmed.startsWith("(") && trimmed.endsWith(")"))) {
                allBackground = false;
                break;
            }
        }

        if (allBackground) {
            isBackground = true;
            for (int i = 0; i < fragments.size(); i++) {
                fragments.set(i, fragments.get(i).replace("(", "").replace(")", ""));
            }        
        }

        if (fragments.isEmpty()) {
            if (t.isEmpty()) return null;
            List<String> parts =
                    isNonSpace ? splitIntoCharacters(t) : splitIntoWordsPreservingSpaces(t);
            if (parts.isEmpty()) return null;

            int cursor = 0;
            for (String p : parts) {
                LyricWord word = new LyricWord(cursor);
                LyricSyllable syl = new LyricSyllable((int) lineStartTime, p, 0);
                syl.endTime = (int) lineStartTime;
                word.syllables.add(syl);
                words.add(word);
                cursor += p.length();
            }

            StringBuilder rebuilt = new StringBuilder();
            for (String p : parts) rebuilt.append(p);

            LyricLine line =
                    new LyricLine(
                            (int) lineStartTime, new SpannableString(rebuilt.toString()), words);
            line.vocalType = vocalType;
            line.isBackground = isBackground;
            line.isSimpleLRC = true;
            return line;
        }

        StringBuilder rawTextBuilder = new StringBuilder();
        LyricWord currentWord = null;
        int cursor = 0;
        boolean lastHadTrailingSpace = false;

        for (int i = 0; i < fragments.size(); i++) {
    String frag = fragments.get(i);
    int cut = frag.length();
    while (cut > 0 && frag.charAt(cut - 1) == ' ') cut--;

    String core = frag.substring(0, cut);
    String trailing = frag.substring(cut);

    if (currentWord == null || lastHadTrailingSpace || isNonSpace) {
        currentWord = new LyricWord(cursor);
        words.add(currentWord);
    }

    LyricSyllable syl = new LyricSyllable(timestamps.get(i), core, cursor - currentWord.startIndex);

    if (i + 1 < timestamps.size()) {
        syl.endTime = timestamps.get(i + 1);
    } else {
        
        syl.endTime = (explicitEnd > 0) ? (int) explicitEnd : syl.startTime + 1000;
    }

    currentWord.syllables.add(syl);
    rawTextBuilder.append(core);
    cursor += core.length();

    if (!trailing.isEmpty()) {
        rawTextBuilder.append(trailing);
        cursor += trailing.length();
        lastHadTrailingSpace = true;
    } else {
        lastHadTrailingSpace = false;
    }
}


        LyricLine line =
                new LyricLine(
                        (int) lineStartTime, new SpannableString(rawTextBuilder.toString()), words);
        line.vocalType = vocalType;
        line.isBackground = isBackground;

        for (int i = 0; i < words.size(); i++) {
            LyricWord w = words.get(i);
        }
        return line;
    }

    private static boolean isNonSpaceLanguage(String text) {
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            Character.UnicodeBlock block = Character.UnicodeBlock.of(c);
            if (block == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                    || block == Character.UnicodeBlock.HIRAGANA
                    || block == Character.UnicodeBlock.KATAKANA
                    || block == Character.UnicodeBlock.THAI) {
                return true;
            }
        }
        return false;
    }

    private static List<String> splitIntoCharacters(String text) {
        List<String> chars = new ArrayList<>();
        for (int i = 0; i < text.length(); i++) {
            chars.add(String.valueOf(text.charAt(i)));
        }
        return chars;
    }

    private static List<String> splitIntoWordsPreservingSpaces(String text) {
        List<String> parts = new ArrayList<>();
        Matcher matcher = WORD_SPACING_PATTERN.matcher(text);
        while (matcher.find()) {
            parts.add(matcher.group());
        }
        return parts;
    }

    private static long parseTimestamp(String min, String sec, String msStr) {
        int m = Integer.parseInt(min);
        int s = Integer.parseInt(sec);
        int ms = Integer.parseInt(msStr) * (msStr.length() == 2 ? 10 : 1);
        return (m * 60L + s) * 1000L + ms;
    }

    private static void finalizeSyllableTimings(List<LyricLine> lines) {
    final int GAP_THRESHOLD_MS = 600;

    for (int i = 0; i < lines.size(); i++) {
        LyricLine line = lines.get(i);
        if (line.words.isEmpty()) continue;

        for (int w = 0; w < line.words.size(); w++) {
            LyricWord word = line.words.get(w);

            for (int s = 0; s < word.syllables.size(); s++) {
                LyricSyllable current = word.syllables.get(s);
                LyricSyllable next = null;

                if (s + 1 < word.syllables.size()) {
                    next = word.syllables.get(s + 1);
                } else if (w + 1 < line.words.size()) {
                    next = line.words.get(w + 1).syllables.get(0);
                }

                if (next != null && !line.isSimpleLRC) {
                    int originalEnd = current.endTime;
                    int gap = next.startTime - originalEnd;

                    if (gap > 0 && gap <= GAP_THRESHOLD_MS) {
                        int halfGap = gap / 2;
                        
                        current.endTime = originalEnd + halfGap;
                        next.startTime = next.startTime - (gap - halfGap); 
                        
                        current.nextStartTime = next.startTime;
                    } else {
                        current.endTime = Math.max(originalEnd, current.startTime);
                        current.nextStartTime = next.startTime;
                    }
                } else {
                    current.endTime = Math.max(current.endTime, current.startTime);
                    current.nextStartTime = current.endTime;
                }
            }
        }

        if (i + 1 < lines.size()) {
            line.endTime = lines.get(i + 1).time;
        }
    }
}

}
