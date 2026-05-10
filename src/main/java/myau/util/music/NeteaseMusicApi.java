package myau.util.music;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import myau.Myau;
import myau.util.ChatUtil;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.Response;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public final class NeteaseMusicApi {
    private static final String LOCAL_API_BASE_URL = "http://127.0.0.1:3000";
    private static final String DEFAULT_API_BASE_URL = "https://ncm.zhenxin.me";
    private static final String[] API_CANDIDATES = new String[]{
            DEFAULT_API_BASE_URL,
            "https://music.mcseekeri.com",
            "https://api-mymusic.vercel.app",
            LOCAL_API_BASE_URL
    };
    private static final File DATA_DIR = new File("./config/Myau/musicplayer");
    private static final File CACHE_DIR = new File(DATA_DIR, "cache");
    private static final File API_BASE_FILE = new File(DATA_DIR, "netease_api_base_url.txt");
    private static final File COOKIE_FILE = new File(DATA_DIR, "netease_cookie.txt");
    private static final OkHttpClient CLIENT = new OkHttpClient.Builder()
            .connectTimeout(10, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .build();
    private static volatile String apiBaseUrl = loadApiBaseUrl();
    private static volatile String userNickname = "";
    private static volatile boolean profileLoading;
    private static volatile long lastProfileAttemptMs;

    private NeteaseMusicApi() {
    }

    public static String getApiBaseUrl() {
        return apiBaseUrl;
    }

    public static void setApiBaseUrl(String baseUrl) {
        String normalized = normalizeBaseUrl(baseUrl);
        if (normalized.isEmpty()) {
            return;
        }
        apiBaseUrl = normalized;
        DATA_DIR.mkdirs();
        try {
            java.nio.file.Files.write(API_BASE_FILE.toPath(), normalized.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
    }

    public static void setCookie(String cookie) {
        String normalized = normalizeCookieHeader(cookie == null ? "" : cookie);
        DATA_DIR.mkdirs();
        try {
            java.nio.file.Files.write(COOKIE_FILE.toPath(), normalized.getBytes(StandardCharsets.UTF_8));
        } catch (IOException ignored) {
        }
        userNickname = "";
        if (!normalized.isEmpty()) {
            refreshUserProfileAsync();
        }
    }

    public static boolean hasCookie() {
        return !loadCookie().isEmpty();
    }

    public static String getUserNickname() {
        if (!hasCookie()) {
            userNickname = "";
            return "";
        }
        if (userNickname.isEmpty()) {
            refreshUserProfileAsync();
        }
        return userNickname;
    }

    public static void refreshUserProfileAsync() {
        long now = System.currentTimeMillis();
        if (profileLoading || now - lastProfileAttemptMs < 10_000L) {
            return;
        }
        profileLoading = true;
        lastProfileAttemptMs = now;
        Thread thread = new Thread(() -> {
            try {
                userNickname = loadUserNickname();
            } finally {
                profileLoading = false;
            }
        }, "musicplayer-profile");
        thread.setDaemon(true);
        thread.start();
    }

    public static void logout() {
        setCookie("");
    }

    public static QrLoginData createQrLogin() throws IOException {
        String keyBody = executeTextRequest(getApiBaseUrl() + "/login/qr/key?timestamp=" + System.currentTimeMillis());
        if (keyBody == null || keyBody.isEmpty()) {
            throw new IOException("QR key request failed");
        }
        JsonObject keyRoot = new JsonParser().parse(keyBody).getAsJsonObject();
        JsonObject keyData = getObject(keyRoot, "data");
        String key = keyData == null ? "" : getString(keyData, "unikey", "");
        if (key.isEmpty()) {
            throw new IOException("QR key is empty");
        }

        String imageBody = executeTextRequest(getApiBaseUrl() + "/login/qr/create?key=" + URLEncoder.encode(key, "UTF-8") + "&qrimg=true&timestamp=" + System.currentTimeMillis());
        if (imageBody == null || imageBody.isEmpty()) {
            throw new IOException("QR image request failed");
        }
        JsonObject imageRoot = new JsonParser().parse(imageBody).getAsJsonObject();
        JsonObject imageData = getObject(imageRoot, "data");
        String qrImage = imageData == null ? "" : getString(imageData, "qrimg", "");
        if (qrImage.isEmpty()) {
            throw new IOException("QR image is empty");
        }
        return new QrLoginData(key, qrImage);
    }

    public static QrStatus checkQrStatus(String key) throws IOException {
        QrStatus status = checkQrStatusOnce(key, false);
        if (status == QrStatus.WAITING) {
            status = checkQrStatusOnce(key, true);
        }
        return status;
    }

    public static List<MusicSong> searchSongs(String keyword, int limit) throws IOException {
        String encoded = URLEncoder.encode(keyword, "UTF-8");
        String body = executeTextRequest(getApiBaseUrl() + "/cloudsearch?keywords=" + encoded + "&limit=" + limit);
        List<MusicSong> result = new ArrayList<>();
        if (body == null || body.isEmpty()) {
            return result;
        }

        JsonObject root = new JsonParser().parse(body).getAsJsonObject();
        JsonObject resultObject = getObject(root, "result");
        if (resultObject == null) {
            return result;
        }
        JsonArray songs = getArray(resultObject, "songs");
        if (songs == null) {
            return result;
        }

        for (JsonElement element : songs) {
            JsonObject songObject = element.getAsJsonObject();
            JsonObject album = getObject(songObject, "al");
            JsonArray artists = getArray(songObject, "ar");
            String singer = "Unknown";
            if (artists != null && artists.size() > 0) {
                JsonObject artist = artists.get(0).getAsJsonObject();
                singer = getString(artist, "name", "Unknown");
            }
            result.add(new MusicSong(
                    album == null ? "" : getString(album, "picUrl", ""),
                    getString(songObject, "name", "Unknown"),
                    singer,
                    getLong(songObject, "id", 0L),
                    getLong(songObject, "dt", 0L),
                    null,
                    null
            ));
        }
        return result;
    }

    public static List<MusicLyricLine> getLyrics(long songId) throws IOException {
        String body = executeTextRequest(getApiBaseUrl() + "/lyric?id=" + songId);
        if (body == null || body.isEmpty()) {
            return new ArrayList<>();
        }
        JsonObject root = new JsonParser().parse(body).getAsJsonObject();
        JsonObject lrc = getObject(root, "lrc");
        String lyric = lrc == null ? "" : getString(lrc, "lyric", "");
        return MusicLyricParser.parse(lyric);
    }

    public static List<MusicLyricLine> loadLyricByKeyword(String keyword) {
        try {
            List<MusicSong> songs = searchSongs(keyword, 1);
            if (songs.isEmpty()) {
                return new ArrayList<>();
            }
            return getLyrics(songs.get(0).getId());
        } catch (IOException e) {
            return new ArrayList<>();
        }
    }

    public static boolean playSong(MusicSong song) {
        try {
            CACHE_DIR.mkdirs();
            File cacheFile = new File(CACHE_DIR, song.getId() + ".mp3");
            SongFile songFile = null;
            if (!cacheFile.exists() || cacheFile.length() == 0L || isProbablyTrialCache(song, cacheFile)) {
                songFile = getSongFile(song.getId());
                if (songFile == null || songFile.getUrl() == null || songFile.getUrl().trim().isEmpty()) {
                    return false;
                }
                if (songFile.isTrial()) {
                    if (cacheFile.exists()) {
                        cacheFile.delete();
                    }
                    ChatUtil.sendFormatted(Myau.clientName + "&cNetease returned a trial clip for " + song.getName() + ". Add a valid cookie to musicplayer/netease_cookie.txt.");
                    return false;
                }
                downloadToFile(songFile.getUrl(), cacheFile);
            }
            List<MusicLyricLine> lyrics = getLyrics(song.getId());
            MusicPlayerManager.playFile(cacheFile, song, lyrics);
            return true;
        } catch (Exception e) {
            ChatUtil.sendFormatted(Myau.clientName + "&cFailed to play song: " + e.getMessage());
            return false;
        }
    }

    private static SongFile getSongFile(long songId) throws IOException {
        String body = executeTextRequest(getApiBaseUrl() + "/song/url/v1?id=" + songId + "&level=exhigh&UnblockNeteaseMusic=true", true);
        if (body == null || body.isEmpty()) {
            return null;
        }
        JsonObject root = new JsonParser().parse(body).getAsJsonObject();
        JsonArray data = getArray(root, "data");
        if (data == null || data.size() == 0) {
            return null;
        }
        JsonObject file = data.get(0).getAsJsonObject();
        boolean trial = hasTrialInfo(file);
        return new SongFile(
                getString(file, "url", ""),
                getLong(file, "size", 0L),
                trial
        );
    }

    private static void downloadToFile(String url, File target) throws IOException {
        File temp = new File(target.getParentFile(), target.getName() + ".download");
        Request request = new Request.Builder().url(url).get().build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                throw new IOException("Download failed: " + response.code());
            }
            try (InputStream inputStream = response.body().byteStream();
                 FileOutputStream outputStream = new FileOutputStream(temp)) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, read);
                }
            }
        }
        if (target.exists() && !target.delete()) {
            throw new IOException("Unable to replace cache file");
        }
        if (!temp.renameTo(target)) {
            java.nio.file.Files.copy(temp.toPath(), target.toPath(), java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            temp.delete();
        }
    }

    private static String executeTextRequest(String url) throws IOException {
        return executeTextRequest(url, false);
    }

    private static String executeTextRequest(String url, boolean includeCookie) throws IOException {
        Request request = withNeteaseHeaders(new Request.Builder().url(url).get()).build();
        if (includeCookie) {
            String cookie = loadCookie();
            if (!cookie.isEmpty()) {
                request = request.newBuilder().header("Cookie", cookie).build();
            }
        }
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return null;
            }
            return response.body().string();
        }
    }

    private static QrStatus checkQrStatusOnce(String key, boolean noCookie) throws IOException {
        String suffix = noCookie ? "&noCookie=true" : "";
        Request request = withNeteaseHeaders(new Request.Builder()
                .url(getApiBaseUrl() + "/login/qr/check?key=" + URLEncoder.encode(key, "UTF-8") + "&timestamp=" + System.currentTimeMillis() + suffix)
                .get()).build();
        try (Response response = CLIENT.newCall(request).execute()) {
            if (!response.isSuccessful() || response.body() == null) {
                return QrStatus.WAITING;
            }
            String body = response.body().string();
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();
            int code = (int) getLong(root, "code", 0L);
            if (code == 800) {
                return QrStatus.EXPIRED;
            }
            if (code == 802) {
                return QrStatus.SCANNED;
            }
            if (code == 803) {
                String cookie = extractCookie(body, response.headers("Set-Cookie"));
                if (!cookie.isEmpty()) {
                    setCookie(cookie);
                    refreshUserProfileAsync();
                    return QrStatus.SUCCESS;
                }
                return QrStatus.SCANNED;
            }
            return QrStatus.WAITING;
        }
    }

    private static Request.Builder withNeteaseHeaders(Request.Builder builder) {
        return builder.header("User-Agent", "Mozilla/5.0 Myau/1.0")
                .header("Referer", "https://music.163.com/")
                .header("Origin", "https://music.163.com");
    }

    private static String extractCookie(String body, List<String> setCookieHeaders) {
        StringBuilder rawCookie = new StringBuilder();
        if (body != null && !body.trim().isEmpty()) {
            try {
                JsonObject root = new JsonParser().parse(body).getAsJsonObject();
                appendCookieCandidate(rawCookie, root);
                JsonObject data = getObject(root, "data");
                if (data != null) {
                    appendCookieCandidate(rawCookie, data);
                    JsonObject nestedData = getObject(data, "data");
                    if (nestedData != null) {
                        appendCookieCandidate(rawCookie, nestedData);
                    }
                }
            } catch (Exception ignored) {
            }
        }
        if (setCookieHeaders != null) {
            for (String header : setCookieHeaders) {
                if (header != null && !header.trim().isEmpty()) {
                    if (rawCookie.length() > 0) {
                        rawCookie.append("; ");
                    }
                    rawCookie.append(header);
                }
            }
        }
        return normalizeCookieHeader(rawCookie.toString());
    }

    private static void appendCookieCandidate(StringBuilder builder, JsonObject object) {
        String cookie = getString(object, "cookie", "");
        if (cookie.isEmpty()) {
            return;
        }
        if (builder.length() > 0) {
            builder.append("; ");
        }
        builder.append(cookie);
    }

    private static String normalizeCookieHeader(String rawCookie) {
        if (rawCookie == null || rawCookie.trim().isEmpty()) {
            return "";
        }
        Map<String, String> cookies = new LinkedHashMap<>();
        String normalized = rawCookie.replace(",", ";");
        String[] parts = normalized.split(";");
        for (String part : parts) {
            String trimmed = part.trim();
            if (trimmed.isEmpty() || !trimmed.contains("=")) {
                continue;
            }
            String name = trimmed.substring(0, trimmed.indexOf('=')).trim();
            String value = trimmed.substring(trimmed.indexOf('=') + 1).trim();
            String lowerName = name.toLowerCase();
            if (name.isEmpty()
                    || value.isEmpty()
                    || "path".equals(lowerName)
                    || "domain".equals(lowerName)
                    || "expires".equals(lowerName)
                    || "max-age".equals(lowerName)
                    || "secure".equals(lowerName)
                    || "httponly".equals(lowerName)
                    || "samesite".equals(lowerName)
                    || "priority".equals(lowerName)) {
                continue;
            }
            cookies.put(name, value);
        }
        StringBuilder builder = new StringBuilder();
        for (Map.Entry<String, String> entry : cookies.entrySet()) {
            if (builder.length() > 0) {
                builder.append("; ");
            }
            builder.append(entry.getKey()).append('=').append(entry.getValue());
        }
        return builder.toString();
    }

    private static String loadUserNickname() {
        try {
            String status = executeTextRequest(getApiBaseUrl() + "/login/status?timestamp=" + System.currentTimeMillis(), true);
            String nickname = extractNickname(status);
            if (!nickname.isEmpty()) {
                return nickname;
            }
            String account = executeTextRequest(getApiBaseUrl() + "/user/account?timestamp=" + System.currentTimeMillis(), true);
            return extractNickname(account);
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String extractNickname(String body) {
        if (body == null || body.trim().isEmpty()) {
            return "";
        }
        try {
            JsonObject root = new JsonParser().parse(body).getAsJsonObject();
            String nickname = extractProfileNickname(root);
            if (!nickname.isEmpty()) {
                return nickname;
            }
            JsonObject data = getObject(root, "data");
            if (data != null) {
                nickname = extractProfileNickname(data);
                if (!nickname.isEmpty()) {
                    return nickname;
                }
                JsonObject nestedData = getObject(data, "data");
                if (nestedData != null) {
                    nickname = extractProfileNickname(nestedData);
                    if (!nickname.isEmpty()) {
                        return nickname;
                    }
                }
            }
            JsonObject account = getObject(root, "account");
            return account == null ? "" : getString(account, "userName", "");
        } catch (Exception ignored) {
            return "";
        }
    }

    private static String extractProfileNickname(JsonObject object) {
        JsonObject profile = getObject(object, "profile");
        if (profile == null) {
            return "";
        }
        return getString(profile, "nickname", "");
    }

    private static String loadCookie() {
        String env = System.getenv("MYAU_NETEASE_COOKIE");
        if (env != null && !env.trim().isEmpty()) {
            return env.trim();
        }
        if (!COOKIE_FILE.exists()) {
            return "";
        }
        try {
            return new String(java.nio.file.Files.readAllBytes(COOKIE_FILE.toPath()), StandardCharsets.UTF_8).trim();
        } catch (IOException ignored) {
            return "";
        }
    }

    private static boolean isProbablyTrialCache(MusicSong song, File cacheFile) {
        if (!cacheFile.exists() || cacheFile.length() <= 0L) {
            return true;
        }
        long duration = song.getDuration();
        if (duration <= 45_000L) {
            return false;
        }
        return cacheFile.length() < 768L * 1024L;
    }

    private static boolean hasTrialInfo(JsonObject file) {
        JsonElement freeTrialInfo = file.get("freeTrialInfo");
        if (freeTrialInfo != null && !freeTrialInfo.isJsonNull()) {
            return true;
        }
        JsonElement freeTrialPrivilege = file.get("freeTrialPrivilege");
        if (freeTrialPrivilege != null && freeTrialPrivilege.isJsonObject()) {
            JsonObject privilege = freeTrialPrivilege.getAsJsonObject();
            JsonElement resConsumable = privilege.get("resConsumable");
            JsonElement userConsumable = privilege.get("userConsumable");
            return (resConsumable != null && resConsumable.isJsonPrimitive() && resConsumable.getAsBoolean())
                    || (userConsumable != null && userConsumable.isJsonPrimitive() && userConsumable.getAsBoolean());
        }
        return false;
    }

    private static String loadApiBaseUrl() {
        String configured = System.getenv("MYAU_NETEASE_API_BASE_URL");
        if (configured != null) {
            configured = normalizeBaseUrl(configured);
            if (!configured.isEmpty()) {
                return configured;
            }
        }
        if (API_BASE_FILE.exists()) {
            try {
                String saved = new String(java.nio.file.Files.readAllBytes(API_BASE_FILE.toPath()), StandardCharsets.UTF_8);
                saved = normalizeBaseUrl(saved);
                if (!saved.isEmpty()) {
                    return saved;
                }
            } catch (IOException ignored) {
            }
        }
        for (String candidate : API_CANDIDATES) {
            if (probeApiBase(candidate)) {
                return candidate;
            }
        }
        return DEFAULT_API_BASE_URL;
    }

    private static boolean probeApiBase(String baseUrl) {
        Request request = new Request.Builder()
                .url(normalizeBaseUrl(baseUrl) + "/login/qr/key?timestamp=1")
                .get()
                .build();
        try (Response response = CLIENT.newBuilder()
                .connectTimeout(4, TimeUnit.SECONDS)
                .readTimeout(4, TimeUnit.SECONDS)
                .build()
                .newCall(request)
                .execute()) {
            return response.isSuccessful();
        } catch (IOException ignored) {
            return false;
        }
    }

    private static String normalizeBaseUrl(String baseUrl) {
        if (baseUrl == null) {
            return "";
        }
        String trimmed = baseUrl.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private static JsonObject getObject(JsonObject root, String key) {
        JsonElement element = root.get(key);
        return element != null && element.isJsonObject() ? element.getAsJsonObject() : null;
    }

    private static JsonArray getArray(JsonObject root, String key) {
        JsonElement element = root.get(key);
        return element != null && element.isJsonArray() ? element.getAsJsonArray() : null;
    }

    private static String getString(JsonObject root, String key, String fallback) {
        JsonElement element = root.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsString();
    }

    private static long getLong(JsonObject root, String key, long fallback) {
        JsonElement element = root.get(key);
        return element == null || element.isJsonNull() ? fallback : element.getAsLong();
    }

    public enum QrStatus {
        WAITING,
        SCANNED,
        SUCCESS,
        EXPIRED
    }

    public static final class QrLoginData {
        private final String key;
        private final String qrImage;

        private QrLoginData(String key, String qrImage) {
            this.key = key;
            this.qrImage = qrImage;
        }

        public String getKey() {
            return key;
        }

        public String getQrImage() {
            return qrImage;
        }
    }

    private static final class SongFile {
        private final String url;
        private final long size;
        private final boolean trial;

        private SongFile(String url, long size, boolean trial) {
            this.url = url;
            this.size = size;
            this.trial = trial;
        }

        public String getUrl() {
            return url;
        }

        @SuppressWarnings("unused")
        public long getSize() {
            return size;
        }

        public boolean isTrial() {
            return trial;
        }
    }
}
