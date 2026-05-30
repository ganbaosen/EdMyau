package myau.util.font;

import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.WorldRenderer;
import net.minecraft.client.renderer.vertex.DefaultVertexFormats;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

public class CustomFont {
    private final Font font;
    private final boolean antiAlias;
    private final boolean fractionalMetrics;
    private final CharData[] charData = new CharData[256];
    private final CharData[] boldCharData = new CharData[256];
    private int fontHeight = -1;
    private int textureID;
    private int boldTextureID;

    public CustomFont(Font font, boolean antiAlias, boolean fractionalMetrics) {
        this.font = font;
        this.antiAlias = antiAlias;
        this.fractionalMetrics = fractionalMetrics;
        setupTexture(false);
        setupTexture(true);
    }

    private void setupTexture(boolean bold) {
        BufferedImage img = generateFontImage(bold);

        if (bold) {
            boldTextureID = setupTexture(img);
        } else {
            textureID = setupTexture(img);
        }
    }

    private BufferedImage generateFontImage(boolean bold) {
        int imgSize = 1024;  // 增加纹理大小到1024
        BufferedImage bufferedImage = new BufferedImage(imgSize, imgSize, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = (Graphics2D) bufferedImage.getGraphics();

        g.setFont(bold ? font.deriveFont(Font.BOLD) : font);
        g.setColor(new Color(255, 255, 255, 0));
        g.fillRect(0, 0, imgSize, imgSize);
        g.setColor(Color.WHITE);

        g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS,
                fractionalMetrics ? RenderingHints.VALUE_FRACTIONALMETRICS_ON : RenderingHints.VALUE_FRACTIONALMETRICS_OFF);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                antiAlias ? RenderingHints.VALUE_TEXT_ANTIALIAS_ON : RenderingHints.VALUE_TEXT_ANTIALIAS_OFF);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                antiAlias ? RenderingHints.VALUE_ANTIALIAS_ON : RenderingHints.VALUE_ANTIALIAS_OFF);

        FontMetrics fontMetrics = g.getFontMetrics();
        int charHeight = 0;
        int positionX = 0;
        int positionY = 1;

        for (int i = 0; i < 256; i++) {
            char ch = (char) i;
            Rectangle2D bounds = fontMetrics.getStringBounds(String.valueOf(ch), g);
            int charWidth = (int) bounds.getWidth() + 8;
            int charHeightValue = fontMetrics.getHeight();

            if (positionX + charWidth >= imgSize) {
                positionX = 0;
                positionY += charHeight;
                charHeight = 0;
            }

            // Draw character first, then store data
            if (charWidth > 0 && i >= 32) { // Only draw printable characters
                g.drawString(String.valueOf(ch), positionX + 2, positionY + fontMetrics.getAscent());
            }

            CharData data = new CharData();
            data.startX = positionX;
            data.startY = positionY;
            data.width = charWidth;
            data.height = charHeightValue;

            if (bold) {
                boldCharData[i] = data;
            } else {
                this.charData[i] = data;
            }

            positionX += charWidth;
            if (charHeightValue > charHeight) {
                charHeight = charHeightValue;
            }
        }

        g.dispose();

        // Debug: save font texture to file and print char data
        try {
            java.io.File outputFile = new java.io.File("font_texture_" + (bold ? "bold" : "normal") + ".png");
            javax.imageio.ImageIO.write(bufferedImage, "png", outputFile);
            System.out.println("Saved font texture to: " + outputFile.getAbsolutePath());

            // Print data for specific characters
            CharData[] dataArray = bold ? boldCharData : this.charData;
            System.out.println("Character data for 'M' (77): " + debugCharData(dataArray[77]));
            System.out.println("Character data for 'y' (121): " + debugCharData(dataArray[121]));
            System.out.println("Character data for 'a' (97): " + debugCharData(dataArray[97]));
            System.out.println("Character data for 'u' (117): " + debugCharData(dataArray[117]));
        } catch (Exception e) {
            e.printStackTrace();
        }

        return bufferedImage;
    }

    private String debugCharData(CharData data) {
        if (data == null) return "null";
        return String.format("x=%d, y=%d, w=%d, h=%d", data.startX, data.startY, data.width, data.height);
    }

    private int setupTexture(BufferedImage img) {
        int textureId = GL11.glGenTextures();
        GL11.glBindTexture(GL11.GL_TEXTURE_2D, textureId);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MIN_FILTER, GL11.GL_LINEAR);
        GL11.glTexParameteri(GL11.GL_TEXTURE_2D, GL11.GL_TEXTURE_MAG_FILTER, GL11.GL_LINEAR);

        int[] pixels = new int[img.getWidth() * img.getHeight()];
        img.getRGB(0, 0, img.getWidth(), img.getHeight(), pixels, 0, img.getWidth());

        java.nio.ByteBuffer buffer = org.lwjgl.BufferUtils.createByteBuffer(img.getWidth() * img.getHeight() * 4);
        for (int y = 0; y < img.getHeight(); y++) {
            for (int x = 0; x < img.getWidth(); x++) {
                int pixel = pixels[y * img.getWidth() + x];
                buffer.put((byte) ((pixel >> 16) & 0xFF));
                buffer.put((byte) ((pixel >> 8) & 0xFF));
                buffer.put((byte) (pixel & 0xFF));
                buffer.put((byte) ((pixel >> 24) & 0xFF));
            }
        }
        buffer.flip();

        GL11.glTexImage2D(GL11.GL_TEXTURE_2D, 0, GL11.GL_RGBA, img.getWidth(), img.getHeight(), 0, GL11.GL_RGBA, GL11.GL_UNSIGNED_BYTE, buffer);
        return textureId;
    }

    public void drawString(String text, float x, float y, int color) {
        drawString(text, x, y, color, false);
    }

    public void drawStringWithShadow(String text, float x, float y, int color) {
        drawString(text, x + 0.5f, y + 0.5f, color, true);
        drawString(text, x, y, color, false);
    }

    private void drawString(String text, float x, float y, int color, boolean shadow) {
        if (text == null) return;

        GlStateManager.pushMatrix();
        GlStateManager.scale(0.5, 0.5, 0.5);
        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GlStateManager.color(
                (float) (color >> 16 & 255) / 255.0F,
                (float) (color >> 8 & 255) / 255.0F,
                (float) (color & 255) / 255.0F,
                shadow ? 0.25f : (float) (color >> 24 & 255) / 255.0F
        );
        GlStateManager.enableTexture2D();
        GlStateManager.bindTexture(shadow ? textureID : textureID);

        float startX = x * 2.0f;
        float startY = y * 2.0f;

        Tessellator tessellator = Tessellator.getInstance();
        WorldRenderer worldRenderer = tessellator.getWorldRenderer();
        worldRenderer.begin(GL11.GL_QUADS, DefaultVertexFormats.POSITION_TEX);

        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character < charData.length && character >= 0) {
                CharData data = this.charData[character];
                if (data != null) {
                    drawChar(worldRenderer, data, startX, startY);
                    startX += data.width - 8;
                }
            }
        }

        tessellator.draw();
        GlStateManager.disableBlend();
        GlStateManager.popMatrix();
    }

    private void drawChar(WorldRenderer worldRenderer, CharData charData, float x, float y) {
        float imgSize = 1024;  // 更新为1024
        float u = charData.startX / imgSize;
        float v = charData.startY / imgSize;
        float u1 = (charData.startX + charData.width) / imgSize;
        float v1 = (charData.startY + charData.height) / imgSize;

        worldRenderer.pos(x, y + charData.height, 0.0D).tex(u, v1).endVertex();
        worldRenderer.pos(x + charData.width, y + charData.height, 0.0D).tex(u1, v1).endVertex();
        worldRenderer.pos(x + charData.width, y, 0.0D).tex(u1, v).endVertex();
        worldRenderer.pos(x, y, 0.0D).tex(u, v).endVertex();
    }

    public float getStringWidth(String text) {
        if (text == null) return 0;
        float width = 0;
        for (int i = 0; i < text.length(); i++) {
            char character = text.charAt(i);
            if (character < charData.length && character >= 0 && charData[character] != null) {
                width += (charData[character].width - 8) / 2.0f;
            }
        }
        return width;
    }

    public float getHeight() {
        return (font.getSize2D() / 2.0f) + 2.0f;
    }

    private static class CharData {
        public int startX;
        public int startY;
        public int width;
        public int height;
    }
}
