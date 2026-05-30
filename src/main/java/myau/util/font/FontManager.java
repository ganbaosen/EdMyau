package myau.util.font;

import java.awt.*;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

public class FontManager {
    private static final Map<String, CustomFont> fonts = new HashMap<>();

    public static CustomFont getFont(String fontName, float size) {
        String key = fontName + "_" + size;

        // Force reload if already exists (for debugging)
        if (fonts.containsKey(key)) {
            fonts.remove(key);
        }

        if (!fonts.containsKey(key)) {
            Font font = loadFont(fontName, size);
            if (font != null) {
                fonts.put(key, new CustomFont(font, true, true));
            }
        }
        return fonts.get(key);
    }

    private static Font loadFont(String fontName, float size) {
        try {
            InputStream is = FontManager.class.getResourceAsStream("/assets/myau/fonts/" + fontName);
            if (is == null) {
                System.err.println("Font not found: " + fontName + ", using Arial fallback");
                return new Font("Arial", Font.PLAIN, (int) size);
            }
            Font font = Font.createFont(Font.TRUETYPE_FONT, is);
            Font derived = font.deriveFont(Font.PLAIN, size);
            System.out.println("Successfully loaded font: " + fontName + " at size " + size);
            System.out.println("Font family: " + derived.getFamily() + ", Font name: " + derived.getFontName());
            return derived;
        } catch (Exception e) {
            System.err.println("Error loading font: " + fontName);
            e.printStackTrace();
            return new Font("Arial", Font.PLAIN, (int) size);
        }
    }

    public static CustomFont axiformaBold(float size) {
        return getFont("axiforma_bold.ttf", size);
    }
}
