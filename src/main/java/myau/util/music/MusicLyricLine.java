package myau.util.music;

public class MusicLyricLine {
    private final long timeMs;
    private final String text;

    public MusicLyricLine(long timeMs, String text) {
        this.timeMs = timeMs;
        this.text = text == null ? "" : text;
    }

    public long getTimeMs() {
        return timeMs;
    }

    public String getText() {
        return text;
    }
}
