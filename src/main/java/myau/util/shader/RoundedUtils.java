package myau.util.shader;

import net.minecraft.client.gui.Gui;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.*;

public class RoundedUtils {

    public static void drawRound(float x, float y, float width, float height, float radius, boolean filled, Color color) {
        GlStateManager.enableBlend();
        GlStateManager.disableTexture2D();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        float r = color.getRed() / 255.0f;
        float g = color.getGreen() / 255.0f;
        float b = color.getBlue() / 255.0f;
        float a = color.getAlpha() / 255.0f;

        GlStateManager.color(r, g, b, a);

        // Simple rectangle for now - proper rounded corners would need shaders
        Gui.drawRect((int)x, (int)y, (int)(x + width), (int)(y + height), color.getRGB());

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }
}
