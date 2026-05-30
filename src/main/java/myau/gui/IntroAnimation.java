package myau.gui;

import myau.event.EventManager;
import myau.event.EventTarget;
import myau.events.Render2DEvent;
import myau.util.RenderUtil;
import myau.util.font.CustomFont;
import myau.util.font.FontManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import org.lwjgl.opengl.GL11;

import java.awt.Color;

public class IntroAnimation {
    private static volatile boolean isActive = false;
    private long startTime = -1L;
    private boolean finished = false;
    private final Minecraft mc = Minecraft.getMinecraft();
    private CustomFont mFont;
    private CustomFont yauFont;

    public IntroAnimation() {
        isActive = true;
        // Pre-load fonts
        try {
            mFont = FontManager.axiformaBold(64.0f);  // 调大字体到64
            yauFont = FontManager.axiformaBold(64.0f);
            System.out.println("IntroAnimation: Fonts loaded successfully");
        } catch (Exception e) {
            System.err.println("IntroAnimation: Failed to load fonts");
            e.printStackTrace();
        }
        EventManager.register(this);
    }

    public static boolean isRunning() {
        return isActive;
    }

    @EventTarget(value = 4)
    public void onRender(Render2DEvent event) {
        if (this.finished) {
            return;
        }

        if (this.startTime < 0L) {
            this.startTime = System.currentTimeMillis();
        }

        long elapsed = System.currentTimeMillis() - this.startTime;
        ScaledResolution sr = new ScaledResolution(mc);
        float screenWidth = sr.getScaledWidth();
        float screenHeight = sr.getScaledHeight();
        float centerX = screenWidth / 2.0f;
        float centerY = screenHeight / 2.0f;

        // Timing constants
        long mAppearStart = 1100L;
        long fadeOutStart = mAppearStart + 900L + 700L + 500L + 300L + 500L + 1300L;

        // Background fade
        float bgAlpha;
        if (elapsed <= 800L) {
            float fadeIn = easeOutCubic(clamp01((float) elapsed / 800.0f));
            bgAlpha = 0.6f * fadeIn;
        } else if (elapsed <= fadeOutStart) {
            bgAlpha = 0.6f;
        } else if (elapsed <= fadeOutStart + 700L) {
            float fadeOut = 1.0f - easeInCubic(clamp01((float) (elapsed - fadeOutStart) / 700.0f));
            bgAlpha = 0.6f * fadeOut;
        } else {
            this.finish();
            return;
        }

        // Draw blurred background
        drawBlurredBackground(bgAlpha);

        // Check if fonts are loaded
        if (mFont == null || yauFont == null) {
            System.err.println("IntroAnimation: Fonts not loaded, skipping render");
            return;
        }

        // "M" letter animation
        float mScale = 1.0f;
        float mAlpha = 0.0f;
        if (elapsed >= mAppearStart) {
            long sinceM = elapsed - mAppearStart;
            if (sinceM <= 900L) {
                float mProgress = easeOutCubic(clamp01((float) sinceM / 900.0f));
                mScale = lerp(2.0f, 1.0f, mProgress);
                mAlpha = mProgress;
            } else {
                mScale = 1.0f;
                mAlpha = 1.0f;
            }
        }

        // Slide animation
        float slideProgress = 0.0f;
        long slideStart = mAppearStart + 900L + 700L;
        if (elapsed > slideStart && elapsed <= slideStart + 500L) {
            slideProgress = easeOutCubic((float) (elapsed - slideStart) / 500.0f);
        } else if (elapsed > slideStart + 500L) {
            slideProgress = 1.0f;
        }

        // Calculate text dimensions
        String mText = "M";
        String yauText = "yau";

        float mWidth = mFont.getStringWidth(mText) * mScale;
        float mHeight = mFont.getHeight() * mScale;
        float yauWidth = yauFont.getStringWidth(yauText);
        float yauHeight = yauFont.getHeight();

        float spacing = 1.0f;  // 减小间距让文字更紧凑
        float totalWidth = mWidth + spacing + yauWidth;

        float mCenterX = centerX - mWidth / 2.0f;
        float mSlideX = centerX - totalWidth / 2.0f;
        float mRenderX = lerp(mCenterX, mSlideX, slideProgress);
        float mRenderY = centerY - mHeight / 2.0f;

        // "yau" fade in animation
        float yauAlpha = 0.0f;
        float yauOffsetY = 12.0f;
        long yauStart = slideStart + 500L + 300L;
        if (elapsed > yauStart && elapsed <= yauStart + 500L) {
            float fadeFactor = easeOutCubic((float) (elapsed - yauStart) / 500.0f);
            yauAlpha = fadeFactor;
            yauOffsetY = (1.0f - fadeFactor) * 12.0f;
        } else if (elapsed > yauStart + 500L) {
            yauAlpha = 1.0f;
            yauOffsetY = 0.0f;
        }

        // Global fade out
        float fadeFactor = 1.0f;
        if (elapsed > fadeOutStart) {
            fadeFactor = 1.0f - clamp01((float) (elapsed - fadeOutStart) / 700.0f);
        }

        // Enable blending for text rendering
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        // Render "M" with shadow and scale
        if (mAlpha > 0.0f) {
            int mColor = new Color(1.0f, 1.0f, 1.0f, clamp01(mAlpha * fadeFactor)).getRGB();

            // Calculate the actual render position considering scale
            float scaledWidth = mWidth / mScale;
            float scaledHeight = mHeight / mScale;
            float renderX = mRenderX + (mWidth - scaledWidth) / 2.0f;
            float renderY = mRenderY + (mHeight - scaledHeight) / 2.0f;

            GL11.glPushMatrix();
            GL11.glTranslatef(renderX, renderY, 0);
            GL11.glScalef(mScale, mScale, 1.0f);
            mFont.drawStringWithShadow(mText, 0, 0, mColor);
            GL11.glPopMatrix();
        }

        // Render "yau" with shadow
        if (yauAlpha > 0.0f) {
            float yauX = mRenderX + mWidth + spacing;
            float yauY = centerY - yauHeight / 2.0f + yauOffsetY;
            int yauColor = new Color(1.0f, 1.0f, 1.0f, clamp01(yauAlpha * fadeFactor)).getRGB();
            yauFont.drawStringWithShadow(yauText, yauX, yauY, yauColor);
        }

        GlStateManager.disableBlend();
    }

    private void drawBlurredBackground(float alpha) {
        ScaledResolution sr = new ScaledResolution(mc);
        float screenWidth = sr.getScaledWidth();
        float screenHeight = sr.getScaledHeight();

        // Draw dark overlay with blur effect simulation
        GlStateManager.disableTexture2D();
        GlStateManager.enableBlend();
        GlStateManager.tryBlendFuncSeparate(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA, GL11.GL_ONE, GL11.GL_ZERO);

        // Draw multiple layers for blur effect
        for (int i = 0; i < 3; i++) {
            float layerAlpha = alpha * 0.25f;
            RenderUtil.drawRect(0.0f, 0.0f, screenWidth, screenHeight,
                new Color(0, 0, 0, (int) (layerAlpha * 255.0f)).getRGB());
        }

        GlStateManager.enableTexture2D();
        GlStateManager.disableBlend();
    }

    private void finish() {
        if (!this.finished) {
            this.finished = true;
            try {
                EventManager.unregister(this);
            } catch (Throwable throwable) {
                // empty catch block
            }
            isActive = false;
        }
    }

    private static float clamp01(float value) {
        return value < 0.0f ? 0.0f : (value > 1.0f ? 1.0f : value);
    }

    private static float lerp(float from, float to, float t) {
        return from + (to - from) * t;
    }

    private static float easeOutCubic(float t) {
        float clamped = clamp01(t);
        clamped = (float) (1.0 - Math.pow(1.0f - clamped, 3.0));
        return clamped;
    }

    private static float easeInCubic(float t) {
        float clamped = clamp01(t);
        clamped = clamped * clamped * clamped;
        return clamped;
    }
}
