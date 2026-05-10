package myau.util.music;

import javazoom.jlgui.basicplayer.BasicController;
import javazoom.jlgui.basicplayer.BasicPlayer;
import javazoom.jlgui.basicplayer.BasicPlayerEvent;
import javazoom.jlgui.basicplayer.BasicPlayerException;
import javazoom.jlgui.basicplayer.BasicPlayerListener;
import myau.Myau;
import myau.util.ChatUtil;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Random;

public final class MusicPlayerManager implements BasicPlayerListener {
    private static final File DATA_DIR = new File("./config/Myau/musicplayer");
    private static final File LOCAL_PLAYLIST_FILE = new File(DATA_DIR, "local_music_paths.txt");
    private static final Random RANDOM = new Random();
    private static final MusicPlayerManager INSTANCE = new MusicPlayerManager();

    private final BasicPlayer player = new BasicPlayer();
    private final List<MusicSong> playlist = new ArrayList<>();
    private final List<MusicSong> localSongs = new ArrayList<>();
    private final List<MusicSong> searchResults = new ArrayList<>();
    private final List<MusicLyricLine> lyrics = new ArrayList<>();

    private volatile MusicSong currentSong;
    private volatile File currentFile;
    private volatile boolean playing;
    private volatile boolean changingSong;
    private volatile long currentPositionMs;
    private volatile long totalDurationMs;
    private volatile long totalBytes;
    private volatile float volume = 1.0F;
    private volatile MusicPlaybackMode playbackMode = MusicPlaybackMode.LIST_LOOP;
    private volatile int currentIndex;
    private volatile String lastStatus = "Idle";

    private MusicPlayerManager() {
        this.player.addBasicPlayerListener(this);
    }

    public static void initialize() {
        loadLocalSongs();
    }

    public static synchronized List<MusicSong> getPlaylist() {
        return new ArrayList<>(INSTANCE.playlist);
    }

    public static synchronized List<MusicSong> getLocalSongs() {
        return new ArrayList<>(INSTANCE.localSongs);
    }

    public static synchronized List<MusicSong> getSearchResults() {
        return new ArrayList<>(INSTANCE.searchResults);
    }

    public static synchronized void setSearchResults(List<MusicSong> songs) {
        INSTANCE.searchResults.clear();
        if (songs != null) {
            INSTANCE.searchResults.addAll(songs);
        }
    }

    public static synchronized List<MusicLyricLine> getLyrics() {
        return new ArrayList<>(INSTANCE.lyrics);
    }

    public static synchronized MusicSong getCurrentSong() {
        return INSTANCE.currentSong;
    }

    public static synchronized int getCurrentIndex() {
        return INSTANCE.currentIndex;
    }

    public static synchronized boolean isPlaying() {
        return INSTANCE.playing;
    }

    public static synchronized long getCurrentPositionMs() {
        return INSTANCE.currentPositionMs;
    }

    public static synchronized long getTotalDurationMs() {
        return INSTANCE.totalDurationMs;
    }

    public static synchronized float getVolume() {
        return INSTANCE.volume;
    }

    public static synchronized MusicPlaybackMode getPlaybackMode() {
        return INSTANCE.playbackMode;
    }

    public static synchronized String getLastStatus() {
        return INSTANCE.lastStatus;
    }

    public static synchronized void setStatus(String status) {
        INSTANCE.lastStatus = status == null ? "" : status;
    }

    public static synchronized String getCurrentLyric(long positionMs) {
        String text = "";
        for (MusicLyricLine line : INSTANCE.lyrics) {
            if (positionMs >= line.getTimeMs()) {
                text = line.getText();
            } else {
                break;
            }
        }
        return text;
    }

    public static synchronized String getNextLyric(long positionMs) {
        for (MusicLyricLine line : INSTANCE.lyrics) {
            if (line.getTimeMs() > positionMs) {
                return line.getText();
            }
        }
        return "";
    }

    public static synchronized void setPlaylist(List<MusicSong> songs, int startIndex) {
        INSTANCE.playlist.clear();
        if (songs != null) {
            INSTANCE.playlist.addAll(songs);
        }
        INSTANCE.currentIndex = Math.max(0, Math.min(startIndex, Math.max(INSTANCE.playlist.size() - 1, 0)));
    }

    public static void playPlaylistSong(List<MusicSong> songs, int index) {
        setPlaylist(songs, index);
        playSongAt(index);
    }

    public static void playSongAt(int index) {
        MusicSong targetSong;
        synchronized (MusicPlayerManager.class) {
            if (INSTANCE.playlist.isEmpty()) {
                return;
            }
            INSTANCE.currentIndex = Math.max(0, Math.min(index, INSTANCE.playlist.size() - 1));
            targetSong = INSTANCE.playlist.get(INSTANCE.currentIndex);
            INSTANCE.changingSong = true;
            setStatus("Loading " + targetSong.getName());
        }

        Thread thread = new Thread(() -> {
            try {
                if (targetSong.isLocal()) {
                    playLocalSong(targetSong);
                } else if (!NeteaseMusicApi.playSong(targetSong)) {
                    setStatus("Unable to load " + targetSong.getName());
                }
            } finally {
                INSTANCE.changingSong = false;
            }
        }, "musicplayer-load-song");
        thread.setDaemon(true);
        thread.start();
    }

    public static boolean playLocalSong(MusicSong song) {
        String localPath = song.getLocalPath();
        if (localPath == null || localPath.trim().isEmpty()) {
            return false;
        }
        File file = new File(localPath);
        if (!file.exists() || !file.isFile()) {
            setStatus("Missing file: " + file.getName());
            return false;
        }
        List<MusicLyricLine> localLyrics = loadLocalLyrics(song, file);
        playFile(file, song, localLyrics);
        return true;
    }

    public static void playFile(File file, MusicSong song, List<MusicLyricLine> newLyrics) {
        synchronized (MusicPlayerManager.class) {
            INSTANCE.currentSong = song;
            INSTANCE.currentFile = file;
            INSTANCE.currentPositionMs = 0L;
            INSTANCE.totalDurationMs = song.getDuration() > 0L ? song.getDuration() : calculateDuration(file);
            INSTANCE.totalBytes = 0L;
            INSTANCE.lyrics.clear();
            if (newLyrics != null) {
                INSTANCE.lyrics.addAll(newLyrics);
            }
            INSTANCE.lastStatus = "Playing " + song.getName();
        }

        Thread thread = new Thread(() -> {
            try {
                INSTANCE.player.stop();
                INSTANCE.player.open(file);
                INSTANCE.player.play();
                INSTANCE.applyVolume();
            } catch (BasicPlayerException e) {
                INSTANCE.playing = false;
                setStatus("Failed to open media");
                ChatUtil.sendFormatted(Myau.clientName + "&cMusicPlayer failed: " + e.getMessage());
            }
        }, "musicplayer-play-file");
        thread.setDaemon(true);
        thread.start();
    }

    public static void togglePlayPause() {
        Thread thread = new Thread(() -> {
            try {
                if (INSTANCE.currentSong == null) {
                    if (!INSTANCE.playlist.isEmpty()) {
                        playSongAt(INSTANCE.currentIndex);
                    }
                    return;
                }
                if (INSTANCE.playing) {
                    INSTANCE.player.pause();
                } else {
                    INSTANCE.player.resume();
                    INSTANCE.applyVolume();
                }
            } catch (BasicPlayerException e) {
                ChatUtil.sendFormatted(Myau.clientName + "&cMusic control failed: " + e.getMessage());
            }
        }, "musicplayer-toggle");
        thread.setDaemon(true);
        thread.start();
    }

    public static void stop() {
        Thread thread = new Thread(() -> {
            try {
                INSTANCE.player.stop();
                INSTANCE.playing = false;
                INSTANCE.currentPositionMs = 0L;
                setStatus("Stopped");
            } catch (BasicPlayerException e) {
                ChatUtil.sendFormatted(Myau.clientName + "&cMusic stop failed: " + e.getMessage());
            }
        }, "musicplayer-stop");
        thread.setDaemon(true);
        thread.start();
    }

    public static void seekToPercent(float percent) {
        final long bytes = INSTANCE.totalBytes;
        if (bytes <= 0L) {
            return;
        }
        final float clamped = Math.max(0.0F, Math.min(1.0F, percent));
        Thread thread = new Thread(() -> {
            try {
                INSTANCE.player.seek((long) (bytes * clamped));
                INSTANCE.applyVolume();
            } catch (BasicPlayerException e) {
                ChatUtil.sendFormatted(Myau.clientName + "&cSeek failed: " + e.getMessage());
            }
        }, "musicplayer-seek");
        thread.setDaemon(true);
        thread.start();
    }

    public static synchronized void setVolume(float newVolume) {
        INSTANCE.volume = Math.max(0.0F, Math.min(1.0F, newVolume));
        INSTANCE.applyVolume();
    }

    private void applyVolume() {
        try {
            this.player.setGain(Math.max(0.0001D, this.volume));
        } catch (BasicPlayerException ignored) {
        }
    }

    public static synchronized void cyclePlaybackMode() {
        switch (INSTANCE.playbackMode) {
            case LIST_LOOP:
                INSTANCE.playbackMode = MusicPlaybackMode.SINGLE_LOOP;
                break;
            case SINGLE_LOOP:
                INSTANCE.playbackMode = MusicPlaybackMode.SHUFFLE;
                break;
            default:
                INSTANCE.playbackMode = MusicPlaybackMode.LIST_LOOP;
                break;
        }
    }

    public static void playNext(boolean manual) {
        Integer nextIndex = resolvePlaylistIndex(false, manual);
        if (nextIndex != null) {
            playSongAt(nextIndex);
        }
    }

    public static void playPrevious(boolean manual) {
        Integer previousIndex = resolvePlaylistIndex(true, manual);
        if (previousIndex != null) {
            playSongAt(previousIndex);
        }
    }

    private static synchronized Integer resolvePlaylistIndex(boolean reverse, boolean manual) {
        if (INSTANCE.playlist.isEmpty()) {
            return null;
        }
        if (INSTANCE.playlist.size() == 1) {
            return 0;
        }
        switch (INSTANCE.playbackMode) {
            case LIST_LOOP:
                return reverse ? (INSTANCE.currentIndex > 0 ? INSTANCE.currentIndex - 1 : INSTANCE.playlist.size() - 1) : (INSTANCE.currentIndex + 1) % INSTANCE.playlist.size();
            case SINGLE_LOOP:
                if (manual) {
                    return reverse ? (INSTANCE.currentIndex > 0 ? INSTANCE.currentIndex - 1 : INSTANCE.playlist.size() - 1) : (INSTANCE.currentIndex + 1) % INSTANCE.playlist.size();
                }
                return INSTANCE.currentIndex;
            case SHUFFLE:
                int next = INSTANCE.currentIndex;
                while (next == INSTANCE.currentIndex) {
                    next = RANDOM.nextInt(INSTANCE.playlist.size());
                }
                return next;
            default:
                return null;
        }
    }

    public static synchronized MusicSong addLocalFile(String path) {
        if (path == null || path.trim().isEmpty()) {
            return null;
        }
        File file = new File(path.trim());
        if (!file.exists() || !file.isFile()) {
            return null;
        }
        for (MusicSong existing : INSTANCE.localSongs) {
            if (path.equalsIgnoreCase(existing.getLocalPath())) {
                return existing;
            }
        }
        MusicSong song = createLocalSong(file);
        INSTANCE.localSongs.add(song);
        saveLocalSongs();
        return song;
    }

    public static synchronized int addLocalDirectory(String path) {
        if (path == null || path.trim().isEmpty()) {
            return 0;
        }
        File directory = new File(path.trim());
        if (!directory.exists() || !directory.isDirectory()) {
            return 0;
        }
        int added = 0;
        File[] files = directory.listFiles();
        if (files == null) {
            return 0;
        }
        for (File file : files) {
            if (!file.isFile() || !isSupportedAudio(file)) {
                continue;
            }
            if (addLocalFile(file.getAbsolutePath()) != null) {
                added++;
            }
        }
        return added;
    }

    public static synchronized void removeLocalSong(int index) {
        if (index < 0 || index >= INSTANCE.localSongs.size()) {
            return;
        }
        INSTANCE.localSongs.remove(index);
        saveLocalSongs();
    }

    public static synchronized void loadLocalSongs() {
        INSTANCE.localSongs.clear();
        if (!LOCAL_PLAYLIST_FILE.exists()) {
            return;
        }
        try {
            List<String> lines = Files.readAllLines(LOCAL_PLAYLIST_FILE.toPath(), StandardCharsets.UTF_8);
            for (String line : lines) {
                if (line == null || line.trim().isEmpty()) {
                    continue;
                }
                File file = new File(line.trim());
                if (file.exists() && file.isFile()) {
                    INSTANCE.localSongs.add(createLocalSong(file));
                }
            }
        } catch (IOException ignored) {
        }
    }

    public static synchronized void saveLocalSongs() {
        DATA_DIR.mkdirs();
        List<String> lines = new ArrayList<>();
        for (MusicSong song : INSTANCE.localSongs) {
            if (song.getLocalPath() != null && !song.getLocalPath().trim().isEmpty()) {
                lines.add(song.getLocalPath());
            }
        }
        try {
            Files.write(LOCAL_PLAYLIST_FILE.toPath(), lines, StandardCharsets.UTF_8);
        } catch (IOException ignored) {
        }
    }

    public static MusicSong createLocalSong(File file) {
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        if (dot > 0) {
            name = name.substring(0, dot);
        }
        File lyricFile = findLocalLyricFile(file);
        return new MusicSong(
                "",
                name,
                "Local Music",
                Math.abs(file.getAbsolutePath().hashCode()),
                calculateDuration(file),
                file.getAbsolutePath(),
                lyricFile == null ? null : lyricFile.getAbsolutePath()
        );
    }

    private static List<MusicLyricLine> loadLocalLyrics(MusicSong song, File file) {
        File lyricFile = song.getLyricPath() == null ? findLocalLyricFile(file) : new File(song.getLyricPath());
        if (lyricFile != null && lyricFile.exists() && lyricFile.isFile()) {
            try {
                return MusicLyricParser.parse(new String(Files.readAllBytes(lyricFile.toPath()), StandardCharsets.UTF_8));
            } catch (IOException ignored) {
            }
        }
        return NeteaseMusicApi.loadLyricByKeyword(song.getName());
    }

    private static File findLocalLyricFile(File audioFile) {
        File parent = audioFile.getParentFile();
        if (parent == null) {
            return null;
        }
        String baseName = audioFile.getName();
        int dot = baseName.lastIndexOf('.');
        if (dot > 0) {
            baseName = baseName.substring(0, dot);
        }
        File lrc = new File(parent, baseName + ".lrc");
        if (lrc.exists() && lrc.isFile()) {
            return lrc;
        }
        File txt = new File(parent, baseName + ".txt");
        return txt.exists() && txt.isFile() ? txt : null;
    }

    private static boolean isSupportedAudio(File file) {
        String lower = file.getName().toLowerCase();
        return lower.endsWith(".mp3") || lower.endsWith(".wav") || lower.endsWith(".ogg");
    }

    private static long calculateDuration(File file) {
        try (AudioInputStream stream = AudioSystem.getAudioInputStream(file)) {
            AudioFormat format = stream.getFormat();
            long frames = stream.getFrameLength();
            if (frames <= 0L || format.getFrameRate() <= 0.0F) {
                return 0L;
            }
            return (long) ((frames / format.getFrameRate()) * 1000.0F);
        } catch (Exception ignored) {
            return 0L;
        }
    }

    public static String formatTime(long timeMs) {
        long totalSeconds = Math.max(0L, timeMs / 1000L);
        long minutes = totalSeconds / 60L;
        long seconds = totalSeconds % 60L;
        return String.format("%02d:%02d", minutes, seconds);
    }

    @Override
    public void opened(Object stream, Map properties) {
        Object durationValue = properties.get("duration");
        if (durationValue instanceof Long) {
            this.totalDurationMs = ((Long) durationValue) / 1000L;
        }
        Object bytesValue = properties.get("audio.length.bytes");
        if (bytesValue instanceof Integer) {
            this.totalBytes = ((Integer) bytesValue).longValue();
        } else if (bytesValue instanceof Long) {
            this.totalBytes = (Long) bytesValue;
        }
    }

    @Override
    public void progress(int bytesread, long microseconds, byte[] pcmdata, Map properties) {
        this.currentPositionMs = microseconds / 1000L;
    }

    @Override
    public void stateUpdated(BasicPlayerEvent event) {
        switch (event.getCode()) {
            case BasicPlayerEvent.PLAYING:
            case BasicPlayerEvent.RESUMED:
                this.playing = true;
                this.lastStatus = "Playing " + (this.currentSong == null ? "" : this.currentSong.getName());
                this.applyVolume();
                break;
            case BasicPlayerEvent.PAUSED:
                this.playing = false;
                this.lastStatus = "Paused";
                break;
            case BasicPlayerEvent.STOPPED:
                this.playing = false;
                if (!this.changingSong) {
                    this.lastStatus = "Stopped";
                }
                break;
            case BasicPlayerEvent.EOM:
                this.playing = false;
                if (!this.changingSong) {
                    playNext(false);
                }
                break;
            default:
                break;
        }
    }

    @Override
    public void setController(BasicController controller) {
    }
}
