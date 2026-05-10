package myau.util.music;

public enum MusicPlaybackMode {
    LIST_LOOP("List"),
    SINGLE_LOOP("Single"),
    SHUFFLE("Shuffle");

    private final String display;

    MusicPlaybackMode(String display) {
        this.display = display;
    }

    public String getDisplay() {
        return display;
    }
}
