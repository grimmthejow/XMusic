package com.xapps.media.xmusic.utils;

import android.os.Handler;
import android.os.Looper;
import android.text.SpannableString;
import com.xapps.media.xmusic.models.LyricLine;
import com.xapps.media.xmusic.models.LyricSyllable;
import com.xapps.media.xmusic.models.LyricWord;
import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PushbackInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
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

    private static final ExecutorService executor = Executors.newSingleThreadExecutor();

    public interface LyricsListener {
        void onParsed(LyricsResult result);
    }

    public record LyricsResult(List<LyricLine> lines, boolean isSynced) {
    }

    public static void parse(String lyricsString, LyricsListener listener) {
        executor.execute(
                () -> {
                    List<LyricLine> lines = new ArrayList<>();
                    boolean isSynced = false;

                    if (lyricsString != null && !lyricsString.trim().isEmpty()) {
                        try (InputStream is =
                                     new ByteArrayInputStream(
                                             lyricsString.getBytes(StandardCharsets.UTF_8))) {
                            lines.addAll(parseInternal(is));

                            for (LyricLine line : lines) {
                                if (line.time > 0) {
                                    isSynced = true;
                                    break;
                                }
                            }
                        } catch (Exception e) {
                            lines.clear();
                        }
                    }

                    final LyricsResult result = new LyricsResult(lines, isSynced);

                    new Handler(Looper.getMainLooper())
                            .post(
                                    () -> {
                                        if (listener != null) {
                                            listener.onParsed(result);
                                        }
                                    });
                });
    }

    private static List<LyricLine> parseInternal(InputStream inputStream) {
        try (PushbackInputStream pb = new PushbackInputStream(inputStream, 20)) {
            int firstByte = pb.read();
            if (firstByte == -1) {
                return new ArrayList<>();
            }
            pb.unread(firstByte);

            List<LyricLine> result;
            if (firstByte == '<') {
                result = new ArrayList<>(handleTtml(pb));
            } else {
                result = new ArrayList<>(parseLrcStream(pb));
            }

            if (!result.isEmpty()) {
                Comparator<LyricLine> lineComparator = (l1, l2) -> {
                    int t = Integer.compare(l1.time, l2.time);
                    if (t != 0) return t;
                    if (!l1.isLinkedBg && l2.isLinkedBg) return -1;
                    if (l1.isLinkedBg && !l2.isLinkedBg) return 1;
                    if (!l1.isRomaji && l2.isRomaji) return -1;
                    if (l1.isRomaji && !l2.isRomaji) return 1;
                    return 0;
                };

                result.sort(lineComparator);

                for (int i = 1; i < result.size(); i++) {
                    LyricLine prev = result.get(i - 1);
                    LyricLine current = result.get(i);

                    if (!current.isWaitingDots && !prev.isWaitingDots && current.time == prev.time && current.vocalType == prev.vocalType) {
                        if (!current.isBackground && !prev.isBackground && !current.isLinkedBg && !prev.isLinkedBg) {
                            current.isRomaji = true;
                        }
                    }
                }

                finalizeSyllableTimings(result);

                result.sort(lineComparator);
            }
            return result;
        } catch (Exception e) {
            Log.e(TAG, "failure in parseInternal", e);
            return new ArrayList<>();
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
                        if (lyricLine != null) {
                            lyricLine.isLinkedBg = true;
                            result.add(lyricLine);
                        }
                    }
                    continue;
                }

                Matcher lm = LINE_TIME_PATTERN.matcher(line);
                if (lm.find()) {
                    long startTime = parseTimestamp(lm.group(1), lm.group(2), lm.group(3)) + globalOffset;
                    String content = lm.group(4).trim();

                    int bgIndex = content.indexOf("[bg:");
                    if (bgIndex != -1) {
                        String mainContent = content.substring(0, bgIndex).trim();
                        String bgContent = content.substring(bgIndex).trim();

                        boolean hasMain = !mainContent.isEmpty();
                        if (hasMain) {
                            LyricLine mainLine = processContent(mainContent, startTime, globalOffset);
                            if (mainLine != null) result.add(mainLine);
                        }

                        long bgStartTime = startTime;
                        Matcher bgWm = WORD_TIME_PATTERN.matcher(bgContent);
                        if (bgWm.find()) {
                            bgStartTime = parseTimestamp(bgWm.group(1), bgWm.group(2), bgWm.group(3)) + globalOffset;
                        }

                        LyricLine bgLine = processContent(bgContent, bgStartTime, globalOffset);
                        if (bgLine != null) {
                            if (hasMain) {
                                bgLine.isLinkedBg = true;
                            }
                            result.add(bgLine);
                        }
                    } else {
                        LyricLine lyricLine = processContent(content, startTime, globalOffset);
                        if (lyricLine != null) result.add(lyricLine);
                    }
                } else if (!line.startsWith("[")) {
                    result.add(new LyricLine(0, new SpannableString(line), new ArrayList<>()));
                }
            }
        } catch (Exception ignored) {
        }
        return result;
    }

    public static LyricLine processContent(String content, long lineStartTime, long globalOffset) {
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
            fragments.replaceAll(s -> s.replace("(", "").replace(")", ""));
        }

        if (fragments.isEmpty()) {
            if (t.isEmpty()) return null;
            List<String> parts = isNonSpace ? splitIntoCharacters(t) : splitIntoWordsPreservingSpaces(t);
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

            LyricLine line = new LyricLine((int) lineStartTime, new SpannableString(rebuilt.toString()), words);
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

        LyricLine line = new LyricLine((int) lineStartTime, new SpannableString(rawTextBuilder.toString()), words);
        line.vocalType = vocalType;
        line.isBackground = isBackground;

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
        final int INSTRUMENTAL_THRESHOLD_MS = 5000;
        List<LyricLine> injectedLines = new ArrayList<>();

        for (int i = 0; i < lines.size(); i++) {
            LyricLine line = lines.get(i);
            if (line.words == null || line.words.isEmpty() || line.isWaitingDots) continue;

            for (int w = 0; w < line.words.size(); w++) {
                LyricWord word = line.words.get(w);

                for (int s = 0; s < word.syllables.size(); s++) {
                    LyricSyllable current = word.syllables.get(s);
                    LyricSyllable next = null;

                    if (s + 1 < word.syllables.size()) {
                        next = word.syllables.get(s + 1);
                    } else if (w + 1 < line.words.size() && line.words.get(w + 1).syllables != null && !line.words.get(w + 1).syllables.isEmpty()) {
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
        }

        long maxEndTimeSoFar = 0;

        LyricLine firstMain = null;
        for (LyricLine l : lines) {
            if (!l.isRomaji && !l.isLinkedBg && !l.isBackground && !l.isWaitingDots) {
                firstMain = l;
                break;
            }
        }

        if (firstMain != null && !firstMain.isSimpleLRC && firstMain.time >= INSTRUMENTAL_THRESHOLD_MS) {
            LyricLine breakLine = new LyricLine(0, new SpannableString(""), new ArrayList<>());
            breakLine.endTime = firstMain.time;
            breakLine.isWaitingDots = true;
            injectedLines.add(breakLine);
        }

        for (int i = 0; i < lines.size(); i++) {
            LyricLine current = lines.get(i);
            if (current.isRomaji || current.isWaitingDots) continue;

            long currentEnd = current.time;
            if (current.words != null && !current.words.isEmpty()) {
                LyricWord lastWord = current.words.get(current.words.size() - 1);
                if (lastWord.syllables != null && !lastWord.syllables.isEmpty()) {
                    currentEnd = lastWord.syllables.get(lastWord.syllables.size() - 1).endTime;
                }
            }
            maxEndTimeSoFar = Math.max(maxEndTimeSoFar, currentEnd);

            LyricLine next = null;
            for (int j = i + 1; j < lines.size(); j++) {
                LyricLine candidate = lines.get(j);
                if (!candidate.isRomaji && !candidate.isWaitingDots && candidate.time > current.time) {
                    next = candidate;
                    break;
                }
            }

            if (next != null && !current.isSimpleLRC && !next.isSimpleLRC) {
                if (next.time - maxEndTimeSoFar >= INSTRUMENTAL_THRESHOLD_MS) {
                    boolean exists = false;
                    for (LyricLine inj : injectedLines) {
                        if (inj.time == maxEndTimeSoFar && inj.endTime == next.time) {
                            exists = true;
                            break;
                        }
                    }
                    if (!exists) {
                        LyricLine breakLine = new LyricLine((int) maxEndTimeSoFar, new SpannableString(""), new ArrayList<>());
                        breakLine.endTime = next.time;
                        breakLine.isWaitingDots = true;
                        injectedLines.add(breakLine);
                    }
                }
            }
        }

        for (int i = 0; i < lines.size(); i++) {
            LyricLine current = lines.get(i);
            if (current.isWaitingDots) continue;

            long nextDistinctTime = -1;
            for (int j = i + 1; j < lines.size(); j++) {
                LyricLine candidate = lines.get(j);
                if (candidate.time > current.time && !candidate.isRomaji && !candidate.isLinkedBg && !candidate.isBackground) {
                    nextDistinctTime = candidate.time;
                    break;
                }
            }

            long naturalEnd = current.time;
            if (current.words != null && !current.words.isEmpty()) {
                LyricWord lastWord = current.words.get(current.words.size() - 1);
                if (lastWord.syllables != null && !lastWord.syllables.isEmpty()) {
                    naturalEnd = lastWord.syllables.get(lastWord.syllables.size() - 1).endTime;
                }
            }

            if (current.isSimpleLRC) {
                current.endTime = (nextDistinctTime > -1) ? (int) nextDistinctTime : current.time + 5000;
            } else {
                current.endTime = (int) naturalEnd;
            }
        }

        if (!injectedLines.isEmpty()) {
            lines.addAll(injectedLines);
        }
    }
}