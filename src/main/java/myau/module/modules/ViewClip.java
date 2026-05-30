package myau.module.modules;

import myau.module.Module;
import net.minecraft.client.Minecraft;

public class ViewClip extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();

    public ViewClip() {
        super("ViewClip", false);
    }

    @Override
    public boolean onEnabled() {
        if (mc.theWorld != null) {
            mc.renderGlobal.loadRenderers();
        }
        return false;
    }

    @Override
    public void onDisabled() {
        if (mc.theWorld != null) {
            mc.renderGlobal.loadRenderers();
        }
    }
}
