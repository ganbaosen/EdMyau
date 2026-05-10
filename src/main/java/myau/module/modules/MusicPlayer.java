package myau.module.modules;

import myau.module.Module;
import myau.ui.MusicPlayerScreen;
import myau.util.music.MusicPlayerManager;
import net.minecraft.client.Minecraft;

public class MusicPlayer extends Module {
    private static final Minecraft mc = Minecraft.getMinecraft();
    private MusicPlayerScreen screen;

    public MusicPlayer() {
        super("MusicPlayer", false);
        MusicPlayerManager.initialize();
    }

    @Override
    public boolean onEnabled() {
        MusicPlayerScreen newScreen = new MusicPlayerScreen(mc.currentScreen);
        setEnabled(false);
        screen = newScreen;
        mc.displayGuiScreen(screen);
        return false;
    }
}
