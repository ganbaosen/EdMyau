package myau.util.music;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class MusicLyricParser {
    private static final Pattern TIME_PATTERN = Pattern.compile("\\[(\\d{1,2}):(\\d{1,2})(?:\\.(\\d{1,3}))?\\]");

    private MusicLyricParser() {
    }

    public static List<MusicLyricLine> parse(String text) {
        List<MusicLyricLine> lines = new ArrayList<>();
        if (text == null || text.trim().isEmpty()) {
            return lines;
        }

        String[] rawLines = text.split("\\r?\\n");
        for (String rawLine : rawLines) {
            Matcher matcher = TIME_PATTERN.matcher(rawLine);
            List<Long> times = new ArrayList<>();
            int lastEnd = 0;
            while (matcher.find()) {
                times.add(parseTime(matcher.group(1), matcher.group(2), matcher.group(3)));
                lastEnd = matcher.end();
            }
            if (times.isEmpty()) {
                continue;
            }

            String lyric = rawLine.substring(lastEnd).trim();
            if (lyric.isEmpty()) {
                lyric = "...";
            }

            for (Long time : times) {
                lines.add(new MusicLyricLine(time, lyric));
            }
        }

        lines.sort(Comparator.comparingLong(MusicLyricLine::getTimeMs));
        return lines;
    }

    private static long parseTime(String minuteText, String secondText, String fractionText) {
        int minutes = parseInt(minuteText);
        int seconds = parseInt(secondText);
        int fraction = parseInt(fractionText);
        if (fractionText != null) {
            if (fractionText.length() == 1) {
                fraction *= 100;
            } else if (fractionText.length() == 2) {
                fraction *= 10;
            }
        }
        return minutes * 60_000L + seconds * 1000L + fraction;
    }

    private static int parseInt(String value) {
        if (value == null || value.isEmpty()) {
            return 0;
        }
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
