package myau.module.modules;

import myau.module.Module;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;

public class Gui extends Module {
    public static final FloatProperty backgroundBlur = new FloatProperty("Background Blur", 0.0f, 0.0f, 100.0f);
    public static final BooleanProperty darkBackground = new BooleanProperty("Dark Background", true);
    public static final BooleanProperty removeWatermark = new BooleanProperty("Remove Watermark", false);
    public static final BooleanProperty removePlayerModel = new BooleanProperty("Remove Player Model", false);
    public static final FloatProperty guiScale = new FloatProperty("GUI Scale", 1.0f, 0.5f, 2.0f);

    public Gui() {
        super("GUI Settings", true, true);
    }

    public static double getClickGuiScale() {
        return guiScale.getValue();
    }
}
