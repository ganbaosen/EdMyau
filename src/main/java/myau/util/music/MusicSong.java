package myau.util.music;

public class MusicSong {
    private final String imageUrl;
    private final String name;
    private final String singer;
    private final long id;
    private final long duration;
    private final String localPath;
    private final String lyricPath;

    public MusicSong(String imageUrl, String name, String singer, long id, long duration, String localPath, String lyricPath) {
        this.imageUrl = imageUrl == null ? "" : imageUrl;
        this.name = name == null ? "Unknown" : name;
        this.singer = singer == null ? "Unknown" : singer;
        this.id = id;
        this.duration = duration;
        this.localPath = localPath;
        this.lyricPath = lyricPath;
    }

    public String getImageUrl() {
        return imageUrl;
    }

    public String getName() {
        return name;
    }

    public String getSinger() {
        return singer;
    }

    public long getId() {
        return id;
    }

    public long getDuration() {
        return duration;
    }

    public String getLocalPath() {
        return localPath;
    }

    public String getLyricPath() {
        return lyricPath;
    }

    public boolean isLocal() {
        return localPath != null && !localPath.trim().isEmpty();
    }

    public String getDisplayName() {
        return name + " - " + singer;
    }
}
