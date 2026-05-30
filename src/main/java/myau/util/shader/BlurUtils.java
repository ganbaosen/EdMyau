package myau.util.shader;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.client.shader.Framebuffer;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL14;

import java.awt.*;

public class BlurUtils {
    private static Framebuffer blurBuffer;
    private static final Minecraft mc = Minecraft.getMinecraft();

    public static void prepareBlur() {
        ScaledResolution sr = new ScaledResolution(mc);
        int width = sr.getScaledWidth();
        int height = sr.getScaledHeight();

        if (blurBuffer == null || blurBuffer.framebufferWidth != width || blurBuffer.framebufferHeight != height) {
            if (blurBuffer != null) {
                blurBuffer.deleteFramebuffer();
            }
            blurBuffer = new Framebuffer(width, height, true);
            blurBuffer.setFramebufferColor(0.0F, 0.0F, 0.0F, 0.0F);
        }

        blurBuffer.framebufferClear();
        blurBuffer.bindFramebuffer(true);
    }

    public static void blurEnd(int iterations, float radius) {
        blurBuffer.unbindFramebuffer();

        GlStateManager.enableBlend();
        GlStateManager.blendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);

        // Simple blur approximation - just render the framebuffer with alpha
        blurBuffer.framebufferRenderExt(mc.displayWidth, mc.displayHeight, false);

        GlStateManager.disableBlend();
    }
}
