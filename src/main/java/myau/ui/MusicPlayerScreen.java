package myau.ui;

import myau.Myau;
import myau.ui.callback.GuiInput;
import myau.util.ChatUtil;
import myau.util.music.MusicPlayerManager;
import myau.util.music.MusicSong;
import myau.util.music.NeteaseMusicApi;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.util.ResourceLocation;
import org.lwjgl.input.Mouse;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.List;

public class MusicPlayerScreen extends GuiScreen {
    private static final int WINDOW = 0xF2140610;
    private static final int SIDEBAR = 0xF006000A;
    private static final int BOTTOM = 0xFA080007;
    private static final int CARD = 0xFF39262F;
    private static final int CARD_HOVER = 0xFF46313B;
    private static final int ACTIVE = 0xFF741A24;
    private static final int PRIMARY = 0xFFFF4048;
    private static final int TEXT = 0xFFF7EEF3;
    private static final int MUTED = 0xFFB8A5AF;
    private static final int FAINT = 0xFF806C76;

    private static final int WINDOW_WIDTH = 380;
    private static final int WINDOW_HEIGHT = 260;
    private static final int SIDEBAR_WIDTH = 70;
    private static final int BOTTOM_HEIGHT = 42;

    private final GuiScreen parent;
    private Page page = Page.HOME;
    private LoginMode loginMode = LoginMode.COOKIE;
    private int listScroll;
    private String qrKey = "";
    private String qrStatus = "点击二维码获取登录码";
    private String qrImageData = "";
    private boolean qrLoading;
    private long lastQrCheckMs;
    private ResourceLocation qrTexture;

    public MusicPlayerScreen() {
        this(null);
    }

    public MusicPlayerScreen(GuiScreen parent) {
        this.parent = parent;
    }

    @Override
    public void initGui() {
        this.buttonList.clear();
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();

        Layout layout = getLayout();
        drawPanel(layout.x, layout.y, layout.x + layout.width, layout.y + layout.height, WINDOW);
        drawRect(layout.x, layout.y, layout.contentX, layout.y + layout.contentHeight, SIDEBAR);
        drawRect(layout.x, layout.y + layout.contentHeight, layout.x + layout.width, layout.y + layout.height, BOTTOM);

        drawSidebar(layout, mouseX, mouseY);
        drawMain(layout, mouseX, mouseY);
        drawBottom(layout, mouseX, mouseY);

        super.drawScreen(mouseX, mouseY, partialTicks);
    }

    private void drawSidebar(Layout layout, int mouseX, int mouseY) {
        int centerX = layout.x + layout.sidebarWidth / 2;
        drawCenteredText("Music", centerX, layout.y + 18, TEXT);

        boolean loggedIn = NeteaseMusicApi.hasCookie();
        boolean loginSelected = this.page == Page.LOGIN;
        boolean loginHovered = isInside(mouseX, mouseY, layout.x + 5, layout.y + 34, layout.sidebarWidth - 10, 56);
        if (loginSelected || loginHovered) {
            drawPanel(layout.x + 5, layout.y + 34, layout.x + layout.sidebarWidth - 5, layout.y + 90, loginSelected ? ACTIVE : 0x661C0E16);
        }

        int avatarX = centerX - 14;
        int avatarY = layout.y + 40;
        drawRect(avatarX - 1, avatarY - 1, avatarX + 29, avatarY + 29, loggedIn ? PRIMARY : 0xAA5C4A54);
        drawRect(avatarX, avatarY, avatarX + 28, avatarY + 28, loggedIn ? 0xFFFFD8DF : 0xFF2E2029);
        drawRect(avatarX + 2, avatarY + 2, avatarX + 26, avatarY + 26, loggedIn ? 0xFFEAE7F1 : 0xFF4A3540);
        drawRect(avatarX + 9, avatarY + 6, avatarX + 19, avatarY + 22, loggedIn ? 0x99FF8A94 : 0x887D6872);
        String accountText = loggedIn ? NeteaseMusicApi.getUserNickname() : "登录";
        if (accountText == null || accountText.trim().isEmpty()) {
            accountText = loggedIn ? "已登录" : "登录";
        }
        drawCenteredText(trim(accountText, layout.sidebarWidth - 12), centerX, avatarY + 38, loggedIn ? TEXT : MUTED);

        int navY = layout.y + 101;
        int navIndex = 0;
        for (Page value : Page.values()) {
            if (!value.showInNav) {
                continue;
            }
            int itemY = navY + navIndex * 22;
            boolean active = value == this.page;
            boolean hovered = isInside(mouseX, mouseY, layout.x + 4, itemY, layout.sidebarWidth - 8, 22);
            if (active || hovered) {
                drawPanel(layout.x + 4, itemY, layout.x + layout.sidebarWidth - 4, itemY + 22, active ? ACTIVE : 0x661C0E16);
            }
            drawCenteredText(value.display, centerX, itemY + 7, active ? PRIMARY : TEXT);
            navIndex++;
        }

        int accountY = layout.y + layout.contentHeight - 28;
        boolean accountHovered = isInside(mouseX, mouseY, layout.x + 4, accountY, layout.sidebarWidth - 8, 22);
        if (accountHovered) {
            drawPanel(layout.x + 4, accountY, layout.x + layout.sidebarWidth - 4, accountY + 22, 0x661C0E16);
        }
        drawCenteredText(loggedIn ? "退出" : "登录", centerX, accountY + 7, loggedIn ? TEXT : PRIMARY);
    }

    private void drawMain(Layout layout, int mouseX, int mouseY) {
        int x = layout.contentX;
        int y = layout.y;
        int width = layout.width - layout.sidebarWidth;

        if (this.page == Page.LOGIN) {
            drawLoginPage(layout, mouseX, mouseY);
            return;
        }

        List<MusicSong> songs = getVisibleSongs();
        drawCircleButton(x + 18, y + 32, "<", mouseX, mouseY);
        this.fontRendererObj.drawStringWithShadow(trim(getTitle(), width - 112), x + 52, y + 18, TEXT);
        this.fontRendererObj.drawStringWithShadow(getSubtitle(songs), x + 52, y + 42, MUTED);
        drawActionChips(layout, mouseX, mouseY);

        int listX = x + 8;
        int listY = y + 64;
        int listW = width - 16;
        int listH = layout.contentHeight - 72;
        drawSongRows(songs, listX, listY, listW, listH, mouseX, mouseY);
    }

    private void drawLoginPage(Layout layout, int mouseX, int mouseY) {
        int x = layout.contentX;
        int y = layout.y;
        int width = layout.width - layout.sidebarWidth;
        boolean loggedIn = NeteaseMusicApi.hasCookie();
        String nickname = NeteaseMusicApi.getUserNickname();
        if (nickname == null || nickname.trim().isEmpty()) {
            nickname = loggedIn ? "网易云账号" : "未登录";
        }
        if (this.loginMode == LoginMode.QR) {
            pollQrStatus();
        }

        drawCircleButton(x + 18, y + 32, "<", mouseX, mouseY);
        this.fontRendererObj.drawStringWithShadow("登录网易云音乐", x + 52, y + 18, TEXT);
        this.fontRendererObj.drawStringWithShadow(loggedIn ? "当前：" + nickname : "用于获取完整歌曲和喜欢列表", x + 52, y + 42, MUTED);

        int cardX = x + 18;
        int cardY = y + 72;
        int cardW = width - 36;
        int cardH = 96;
        drawPanel(cardX, cardY, cardX + cardW, cardY + cardH, CARD);

        int tabY = cardY + 10;
        drawChip(cardX + 12, tabY, 46, "Cookie", this.loginMode == LoginMode.COOKIE || isInside(mouseX, mouseY, cardX + 12, tabY, 46, 12));
        drawChip(cardX + 64, tabY, 46, "二维码", this.loginMode == LoginMode.QR || isInside(mouseX, mouseY, cardX + 64, tabY, 46, 12));

        if (this.loginMode == LoginMode.QR) {
            drawQrLoginContent(cardX, cardY, cardW, mouseX, mouseY);
            return;
        }

        this.fontRendererObj.drawStringWithShadow("按原版登录页布局，当前接入 Cookie。", cardX + 12, cardY + 36, MUTED);
        this.fontRendererObj.drawStringWithShadow("填入 MUSIC_U Cookie 可播放完整音源。", cardX + 12, cardY + 49, MUTED);

        int buttonX = cardX + 12;
        int buttonY = cardY + 68;
        int buttonW = cardW - 24;
        boolean hovered = isInside(mouseX, mouseY, buttonX, buttonY, buttonW, 18);
        drawPanel(buttonX, buttonY, buttonX + buttonW, buttonY + 18, hovered ? 0xFFFF5960 : PRIMARY);
        drawCenteredText(loggedIn ? "更新 Cookie" : "粘贴 Cookie 登录", buttonX + buttonW / 2, buttonY + 5, 0xFFFFFFFF);
    }

    private void drawQrLoginContent(int cardX, int cardY, int cardW, int mouseX, int mouseY) {
        int qrX = cardX + 13;
        int qrY = cardY + 34;
        int qrSize = 58;
        drawRect(qrX - 2, qrY - 2, qrX + qrSize + 2, qrY + qrSize + 2, 0xFFFFFFFF);
        drawRect(qrX, qrY, qrX + qrSize, qrY + qrSize, 0xFFEFECEF);
        loadQrTextureIfNeeded();
        if (this.qrTexture != null) {
            GlStateManager.color(1.0F, 1.0F, 1.0F, 1.0F);
            this.mc.getTextureManager().bindTexture(this.qrTexture);
            drawModalRectWithCustomSizedTexture(qrX, qrY, 0.0F, 0.0F, qrSize, qrSize, qrSize, qrSize);
        } else {
            drawCenteredText("QR", qrX + qrSize / 2, qrY + qrSize / 2 - 4, FAINT);
        }

        int textX = cardX + 86;
        this.fontRendererObj.drawStringWithShadow(this.qrLoading ? "正在连接网易云..." : trim(this.qrStatus, cardW - 98), textX, cardY + 37, TEXT);
        this.fontRendererObj.drawStringWithShadow("用网易云音乐 App 扫码", textX, cardY + 51, MUTED);
        this.fontRendererObj.drawStringWithShadow("确认后会自动写入 Cookie", textX, cardY + 64, MUTED);

        int buttonX = textX;
        int buttonY = cardY + 78;
        int buttonW = cardW - 98;
        boolean hovered = isInside(mouseX, mouseY, buttonX, buttonY, buttonW, 18);
        drawPanel(buttonX, buttonY, buttonX + buttonW, buttonY + 18, hovered ? 0xFFFF5960 : PRIMARY);
        drawCenteredText(this.qrKey.isEmpty() ? "获取二维码" : "刷新二维码", buttonX + buttonW / 2, buttonY + 5, 0xFFFFFFFF);
    }

    private void drawActionChips(Layout layout, int mouseX, int mouseY) {
        int y = layout.y + 8;
        int x = layout.x + layout.width - 132;
        drawChip(x, y, 24, "搜", isInside(mouseX, mouseY, x, y, 24, 12));
        drawChip(x + 28, y, 24, "文件", isInside(mouseX, mouseY, x + 28, y, 24, 12));
        drawChip(x + 56, y, 24, "目录", isInside(mouseX, mouseY, x + 56, y, 24, 12));
        drawChip(x + 84, y, 20, "API", isInside(mouseX, mouseY, x + 84, y, 20, 12));
        drawChip(x + 108, y, 24, NeteaseMusicApi.hasCookie() ? "CK" : "登录", isInside(mouseX, mouseY, x + 108, y, 24, 12));
    }

    private void drawSongRows(List<MusicSong> songs, int x, int y, int width, int height, int mouseX, int mouseY) {
        int rowHeight = 37;
        int visible = Math.max(1, height / rowHeight);
        int start = Math.max(0, Math.min(listScroll, Math.max(0, songs.size() - visible)));
        int end = Math.min(songs.size(), start + visible);
        MusicSong current = MusicPlayerManager.getCurrentSong();

        for (int i = start; i < end; i++) {
            int rowY = y + (i - start) * rowHeight;
            MusicSong song = songs.get(i);
            boolean hovered = isInside(mouseX, mouseY, x, rowY, width, rowHeight - 4);
            boolean selected = current != null && current.getId() == song.getId() && equalsNullable(current.getLocalPath(), song.getLocalPath());
            drawPanel(x, rowY, x + width, rowY + rowHeight - 4, hovered || selected ? CARD_HOVER : CARD);

            this.fontRendererObj.drawStringWithShadow(String.valueOf(i + 1), x + 6, rowY + 7, FAINT);
            drawAlbumStub(x + 24, rowY + 7, i);
            this.fontRendererObj.drawStringWithShadow(trim(song.getName(), width - 86), x + 49, rowY + 7, TEXT);
            this.fontRendererObj.drawStringWithShadow(trim(song.getSinger(), width - 86), x + 49, rowY + 22, MUTED);
        }

        if (songs.isEmpty()) {
            drawCenteredText("暂无歌曲", x + width / 2, y + height / 2 - 4, MUTED);
        }

        if (songs.size() > visible) {
            int barX = x + width - 4;
            int barY = y + 4;
            int barH = height - 8;
            int thumbH = Math.max(14, barH * visible / songs.size());
            int thumbY = barY + (barH - thumbH) * start / Math.max(1, songs.size() - visible);
            drawRect(barX, barY, barX + 3, barY + barH, 0x442B1B23);
            drawRect(barX, thumbY, barX + 3, thumbY + thumbH, 0xAA9D8B94);
        }
    }

    private void drawBottom(Layout layout, int mouseX, int mouseY) {
        MusicSong current = MusicPlayerManager.getCurrentSong();
        long currentMs = MusicPlayerManager.getCurrentPositionMs();
        long totalMs = MusicPlayerManager.getTotalDurationMs();
        int y = layout.y + layout.contentHeight;
        String title = current == null ? "暂无播放" : trim(current.getDisplayName(), 118);

        this.fontRendererObj.drawStringWithShadow(title, layout.x + 8, y + 17, current == null ? MUTED : TEXT);
        if (current != null) {
            this.fontRendererObj.drawStringWithShadow(MusicPlayerManager.formatTime(currentMs) + " / " + MusicPlayerManager.formatTime(totalMs), layout.x + 8, y + 30, MUTED);
        }

        int controlsX = layout.x + layout.width - 78;
        drawTextButton(controlsX, y + 22, "|<", mouseX, mouseY);
        drawPlayButton(controlsX + 20, y + 8, mouseX, mouseY);
        drawTextButton(controlsX + 58, y + 22, ">|", mouseX, mouseY);

        int barX = layout.x + 150;
        int barY = y + 29;
        int barW = Math.max(70, layout.width - 250);
        drawRect(barX, barY, barX + barW, barY + 3, 0x5525151D);
        if (totalMs > 0L) {
            int fill = (int) (barW * Math.min(1.0D, Math.max(0.0D, currentMs / (double) totalMs)));
            drawRect(barX, barY, barX + fill, barY + 3, PRIMARY);
        }
    }

    private List<MusicSong> getVisibleSongs() {
        switch (page) {
            case SEARCH:
                return MusicPlayerManager.getSearchResults();
            case LOCAL:
                return MusicPlayerManager.getLocalSongs();
            case LIKE:
            case HOME:
                List<MusicSong> search = MusicPlayerManager.getSearchResults();
                return search.isEmpty() ? MusicPlayerManager.getLocalSongs() : search;
            default:
                return MusicPlayerManager.getPlaylist();
        }
    }

    private String getTitle() {
        switch (page) {
            case SEARCH:
                return "搜索音乐";
            case LOCAL:
                return "本地音乐";
            case LIKE:
                return "喜欢的音乐";
            default:
                return "今天从《Crisscross》听起私人雷达";
        }
    }

    private String getSubtitle(List<MusicSong> songs) {
        if (page == Page.LOCAL) {
            return songs.size() + "首本地歌曲";
        }
        if (page == Page.SEARCH) {
            return songs.size() + "首搜索结果";
        }
        return songs.size() + "首歌曲";
    }

    private void drawPanel(int left, int top, int right, int bottom, int color) {
        drawRect(left + 3, top, right - 3, bottom, color);
        drawRect(left, top + 3, right, bottom - 3, color);
        drawRect(left + 1, top + 1, right - 1, bottom - 1, color);
    }

    private void drawCircleButton(int x, int y, String text, int mouseX, int mouseY) {
        boolean hovered = Math.abs(mouseX - x) <= 9 && Math.abs(mouseY - y) <= 9;
        drawPanel(x - 9, y - 9, x + 9, y + 9, hovered ? 0xFF4A3440 : 0xFF3A2732);
        drawCenteredText(text, x, y - 4, TEXT);
    }

    private void drawChip(int x, int y, int width, String text, boolean hovered) {
        drawPanel(x, y, x + width, y + 12, hovered ? 0xAA7A1C28 : 0x552D1B24);
        drawCenteredText(text, x + width / 2, y + 2, TEXT);
    }

    private void drawAlbumStub(int x, int y, int index) {
        int[] colors = new int[]{0xFFB74325, 0xFF7CA43C, 0xFF7B45DA, 0xFF2E92A8, 0xFFD45F7E};
        int color = colors[Math.abs(index) % colors.length];
        drawRect(x, y, x + 20, y + 20, color);
        drawRect(x + 3, y + 3, x + 17, y + 17, 0x55100010);
        drawRect(x + 7, y + 5, x + 14, y + 15, 0x88FFFFFF);
    }

    private void drawPlayButton(int x, int y, int mouseX, int mouseY) {
        boolean hovered = isInside(mouseX, mouseY, x, y, 29, 29);
        drawPanel(x, y, x + 29, y + 29, hovered ? 0xFFFF5960 : PRIMARY);
        drawCenteredText(MusicPlayerManager.isPlaying() ? "II" : ">", x + 14, y + 10, 0xFFFFFFFF);
    }

    private void drawTextButton(int x, int y, String text, int mouseX, int mouseY) {
        this.fontRendererObj.drawStringWithShadow(text, x, y, isInside(mouseX, mouseY, x - 4, y - 4, 18, 18) ? PRIMARY : TEXT);
    }

    private void drawCenteredText(String text, int x, int y, int color) {
        this.fontRendererObj.drawStringWithShadow(text, x - this.fontRendererObj.getStringWidth(text) / 2, y, color);
    }

    private void requestQrCode() {
        if (this.qrLoading) {
            return;
        }
        this.qrLoading = true;
        this.qrStatus = "正在获取二维码...";
        this.qrKey = "";
        clearQrTexture();
        Thread thread = new Thread(() -> {
            try {
                NeteaseMusicApi.QrLoginData data = NeteaseMusicApi.createQrLogin();
                this.qrKey = data.getKey();
                this.qrImageData = data.getQrImage();
                this.qrStatus = "等待扫码";
                this.lastQrCheckMs = 0L;
            } catch (Exception e) {
                this.qrStatus = "二维码获取失败";
                ChatUtil.sendFormatted(Myau.clientName + "&cQR login failed: " + e.getMessage());
            } finally {
                this.qrLoading = false;
            }
        }, "musicplayer-qr-create");
        thread.setDaemon(true);
        thread.start();
    }

    private void pollQrStatus() {
        if (this.qrLoading || this.qrKey.isEmpty() || NeteaseMusicApi.hasCookie()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now - this.lastQrCheckMs < 2_000L) {
            return;
        }
        this.lastQrCheckMs = now;
        Thread thread = new Thread(() -> {
            try {
                NeteaseMusicApi.QrStatus status = NeteaseMusicApi.checkQrStatus(this.qrKey);
                if (status == NeteaseMusicApi.QrStatus.SCANNED) {
                    this.qrStatus = "已扫码，请在手机确认";
                } else if (status == NeteaseMusicApi.QrStatus.SUCCESS) {
                    this.qrStatus = "登录成功";
                    this.loginMode = LoginMode.COOKIE;
                    this.page = Page.HOME;
                    this.listScroll = 0;
                } else if (status == NeteaseMusicApi.QrStatus.EXPIRED) {
                    this.qrStatus = "二维码已过期，请刷新";
                    this.qrKey = "";
                } else {
                    this.qrStatus = "等待扫码";
                }
            } catch (Exception e) {
                this.qrStatus = "扫码状态检查失败";
            }
        }, "musicplayer-qr-check");
        thread.setDaemon(true);
        thread.start();
    }

    private void loadQrTextureIfNeeded() {
        if (this.qrTexture != null || this.qrImageData == null || this.qrImageData.trim().isEmpty()) {
            return;
        }
        try {
            String data = this.qrImageData;
            int comma = data.indexOf(',');
            if (comma >= 0) {
                data = data.substring(comma + 1);
            }
            byte[] bytes = Base64.getDecoder().decode(data);
            BufferedImage image = ImageIO.read(new ByteArrayInputStream(bytes));
            if (image != null) {
                this.qrTexture = this.mc.getTextureManager().getDynamicTextureLocation("musicplayer_qr", new DynamicTexture(image));
            }
        } catch (Exception e) {
            this.qrStatus = "二维码解析失败";
            this.qrImageData = "";
        }
    }

    private void clearQrTexture() {
        if (this.qrTexture != null && this.mc != null) {
            this.mc.getTextureManager().deleteTexture(this.qrTexture);
        }
        this.qrTexture = null;
        this.qrImageData = "";
    }

    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        Layout layout = getLayout();

        if (isInside(mouseX, mouseY, layout.x + 5, layout.y + 34, layout.sidebarWidth - 10, 56)) {
            this.page = Page.LOGIN;
            this.listScroll = 0;
            return;
        }

        if (isInside(mouseX, mouseY, layout.contentX + 9, layout.y + 23, 18, 18)) {
            this.page = Page.HOME;
            this.listScroll = 0;
            return;
        }

        int controlsX = layout.x + layout.width - 78;
        if (isInside(mouseX, mouseY, controlsX - 4, layout.y + layout.contentHeight + 14, 22, 24)) {
            MusicPlayerManager.playPrevious(true);
            return;
        }
        if (isInside(mouseX, mouseY, controlsX + 20, layout.y + layout.contentHeight + 8, 29, 29)) {
            MusicPlayerManager.togglePlayPause();
            return;
        }
        if (isInside(mouseX, mouseY, controlsX + 54, layout.y + layout.contentHeight + 14, 22, 24)) {
            MusicPlayerManager.playNext(true);
            return;
        }

        int chipY = layout.y + 8;
        int chipX = layout.x + layout.width - 132;
        if (this.page != Page.LOGIN && isInside(mouseX, mouseY, chipX, chipY, 24, 12)) {
            promptSearch();
            return;
        }
        if (this.page != Page.LOGIN && isInside(mouseX, mouseY, chipX + 28, chipY, 24, 12)) {
            promptLocalFile();
            return;
        }
        if (this.page != Page.LOGIN && isInside(mouseX, mouseY, chipX + 56, chipY, 24, 12)) {
            promptLocalDirectory();
            return;
        }
        if (this.page != Page.LOGIN && isInside(mouseX, mouseY, chipX + 84, chipY, 20, 12)) {
            promptApiBase();
            return;
        }
        if (this.page != Page.LOGIN && isInside(mouseX, mouseY, chipX + 108, chipY, 24, 12)) {
            promptCookie();
            return;
        }

        int navY = layout.y + 101;
        int navIndex = 0;
        for (Page value : Page.values()) {
            if (!value.showInNav) {
                continue;
            }
            int itemY = navY + navIndex * 22;
            if (isInside(mouseX, mouseY, layout.x + 4, itemY, layout.sidebarWidth - 8, 22)) {
                this.page = value;
                this.listScroll = 0;
                return;
            }
            navIndex++;
        }

        int accountY = layout.y + layout.contentHeight - 28;
        if (isInside(mouseX, mouseY, layout.x + 4, accountY, layout.sidebarWidth - 8, 22)) {
            if (NeteaseMusicApi.hasCookie()) {
                NeteaseMusicApi.logout();
                MusicPlayerManager.setStatus("Cookie cleared");
                this.page = Page.HOME;
            } else {
                this.page = Page.LOGIN;
            }
            this.listScroll = 0;
            return;
        }

        if (this.page == Page.LOGIN) {
            int cardX = layout.contentX + 18;
            int cardY = layout.y + 72;
            int cardW = layout.width - layout.sidebarWidth - 36;
            int tabY = cardY + 10;
            if (isInside(mouseX, mouseY, cardX + 12, tabY, 46, 12)) {
                this.loginMode = LoginMode.COOKIE;
                return;
            }
            if (isInside(mouseX, mouseY, cardX + 64, tabY, 46, 12)) {
                this.loginMode = LoginMode.QR;
                if (this.qrKey.isEmpty() && this.qrImageData.isEmpty()) {
                    requestQrCode();
                }
                return;
            }

            int buttonX = cardX + 12;
            int buttonY = cardY + 68;
            if (this.loginMode == LoginMode.COOKIE && isInside(mouseX, mouseY, buttonX, buttonY, cardW - 24, 18)) {
                promptCookie();
            } else if (this.loginMode == LoginMode.QR && isInside(mouseX, mouseY, cardX + 86, cardY + 78, cardW - 98, 18)) {
                requestQrCode();
            }
            return;
        }

        int listX = layout.contentX + 8;
        int listY = layout.y + 64;
        int listW = layout.width - layout.sidebarWidth - 16;
        int listH = layout.contentHeight - 72;
        if (isInside(mouseX, mouseY, listX, listY, listW, listH)) {
            List<MusicSong> songs = getVisibleSongs();
            int index = getClickedSongIndex(mouseY, layout);
            if (index >= 0 && index < songs.size()) {
                if (mouseButton == 1 && page == Page.LOCAL) {
                    MusicPlayerManager.removeLocalSong(index);
                } else {
                    MusicPlayerManager.playPlaylistSong(songs, index);
                }
            }
            return;
        }

        int barX = layout.x + 150;
        int barY = layout.y + layout.contentHeight + 29;
        int barW = Math.max(70, layout.width - 250);
        if (isInside(mouseX, mouseY, barX, barY - 4, barW, 11)) {
            MusicPlayerManager.seekToPercent((mouseX - barX) / (float) barW);
        }
    }

    @Override
    public void onGuiClosed() {
        clearQrTexture();
        super.onGuiClosed();
    }

    private void promptSearch() {
        GuiInput.prompt("Search Songs", "", value -> {
            final String keyword = value == null ? "" : value.trim();
            if (keyword.isEmpty()) {
                return;
            }
            MusicPlayerManager.setStatus("Searching " + keyword);
            Thread thread = new Thread(() -> {
                try {
                    List<MusicSong> songs = NeteaseMusicApi.searchSongs(keyword, 35);
                    MusicPlayerManager.setSearchResults(songs);
                    MusicPlayerManager.setStatus("Found " + songs.size() + " songs");
                    this.page = Page.SEARCH;
                    this.listScroll = 0;
                } catch (Exception e) {
                    MusicPlayerManager.setStatus("Search failed");
                    ChatUtil.sendFormatted(Myau.clientName + "&cMusic search failed: " + e.getMessage());
                }
            }, "musicplayer-search");
            thread.setDaemon(true);
            thread.start();
        }, this);
    }

    private void promptLocalFile() {
        GuiInput.prompt("Add Local File", "", value -> {
            MusicSong song = MusicPlayerManager.addLocalFile(value);
            if (song == null) {
                ChatUtil.sendFormatted(Myau.clientName + "&cUnable to add local file");
            } else {
                MusicPlayerManager.setStatus("Added " + song.getName());
                this.page = Page.LOCAL;
            }
        }, this);
    }

    private void promptLocalDirectory() {
        GuiInput.prompt("Add Local Folder", "", value -> {
            int added = MusicPlayerManager.addLocalDirectory(value);
            MusicPlayerManager.setStatus("Added " + added + " file(s)");
            this.page = Page.LOCAL;
        }, this);
    }

    private void promptApiBase() {
        GuiInput.prompt("Netease API Base", NeteaseMusicApi.getApiBaseUrl(), value -> {
            NeteaseMusicApi.setApiBaseUrl(value);
            MusicPlayerManager.setStatus("API base updated");
        }, this);
    }

    private void promptCookie() {
        GuiInput.prompt("Netease Cookie", "", value -> {
            NeteaseMusicApi.setCookie(value);
            MusicPlayerManager.setStatus(NeteaseMusicApi.hasCookie() ? "Cookie updated" : "Cookie cleared");
            if (NeteaseMusicApi.hasCookie()) {
                this.page = Page.HOME;
                this.listScroll = 0;
            }
        }, this);
    }

    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel == 0) {
            return;
        }
        if (this.page == Page.LOGIN) {
            return;
        }
        Layout layout = getLayout();
        int mouseX = Mouse.getEventX() * this.width / this.mc.displayWidth;
        int mouseY = this.height - Mouse.getEventY() * this.height / this.mc.displayHeight - 1;
        int listX = layout.contentX + 8;
        int listY = layout.y + 64;
        int listW = layout.width - layout.sidebarWidth - 16;
        int listH = layout.contentHeight - 72;
        if (!isInside(mouseX, mouseY, listX, listY, listW, listH)) {
            return;
        }
        List<MusicSong> songs = getVisibleSongs();
        int visible = Math.max(1, listH / 37);
        int maxScroll = Math.max(0, songs.size() - visible);
        listScroll += wheel < 0 ? 1 : -1;
        if (listScroll < 0) {
            listScroll = 0;
        }
        if (listScroll > maxScroll) {
            listScroll = maxScroll;
        }
    }

    private int getClickedSongIndex(int mouseY, Layout layout) {
        int listY = layout.y + 64;
        int relative = (mouseY - listY) / 37;
        return listScroll + relative;
    }

    private Layout getLayout() {
        Layout layout = new Layout();
        layout.width = Math.min(WINDOW_WIDTH, Math.max(300, this.width - 24));
        layout.height = Math.min(WINDOW_HEIGHT, Math.max(210, this.height - 24));
        layout.sidebarWidth = Math.min(SIDEBAR_WIDTH, Math.max(58, layout.width / 5));
        layout.contentHeight = layout.height - BOTTOM_HEIGHT;
        layout.contentX = layout.x + layout.sidebarWidth;
        layout.x = (this.width - layout.width) / 2;
        layout.y = (this.height - layout.height) / 2;
        layout.contentX = layout.x + layout.sidebarWidth;
        return layout;
    }

    private boolean isInside(int mouseX, int mouseY, int x, int y, int width, int height) {
        return mouseX >= x && mouseX <= x + width && mouseY >= y && mouseY <= y + height;
    }

    private boolean equalsNullable(String first, String second) {
        return first == null ? second == null : first.equals(second);
    }

    private String trim(String text, int maxWidth) {
        if (text == null) {
            return "";
        }
        if (this.fontRendererObj.getStringWidth(text) <= maxWidth) {
            return text;
        }
        String suffix = "...";
        String trimmed = text;
        while (!trimmed.isEmpty() && this.fontRendererObj.getStringWidth(trimmed + suffix) > maxWidth) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed + suffix;
    }

    private enum Page {
        HOME("首页", true),
        SEARCH("搜索", true),
        LOCAL("本地", true),
        LIKE("喜欢", true),
        LOGIN("登录", false);

        private final String display;
        private final boolean showInNav;

        Page(String display, boolean showInNav) {
            this.display = display;
            this.showInNav = showInNav;
        }
    }

    private enum LoginMode {
        COOKIE,
        QR
    }

    private static class Layout {
        private int x;
        private int y;
        private int width;
        private int height;
        private int sidebarWidth;
        private int contentHeight;
        private int contentX;
    }
}
