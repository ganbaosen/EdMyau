package myau.module;

import myau.Myau;
import myau.event.EventTarget;
import myau.event.types.EventType;
import myau.events.KeyEvent;
import myau.events.TickEvent;
import myau.module.modules.HUD;
import myau.module.modules.QQGroup;
import myau.ui.ClickGui;
import myau.util.ChatUtil;
import myau.util.SoundUtil;
import net.minecraft.client.Minecraft;
import org.lwjgl.input.Keyboard;

import java.util.LinkedHashMap;

public class ModuleManager {
    private boolean sound = false;
    public final LinkedHashMap<Class<?>, Module> modules = new LinkedHashMap<>();
    private ClickGui clickGui;
    private int clickGuiKey = Keyboard.KEY_RSHIFT;

    public Module getModule(String string) {
        return this.modules.values().stream().filter(mD -> mD.getName().equalsIgnoreCase(string)).findFirst().orElse(null);
    }

    public Module getModule(Class<?> clazz){
        return this.modules.get(clazz);
    }

    public void playSound() {
        this.sound = true;
    }

    public void setClickGuiKey(int key) {
        this.clickGuiKey = key;
    }

    public int getClickGuiKey() {
        return this.clickGuiKey;
    }

    @EventTarget
    public void onKey(KeyEvent event) {
        // Handle ClickGui key
        if (event.getKey() == clickGuiKey) {
            if (clickGui == null) {
                clickGui = new ClickGui();
            }
            Minecraft.getMinecraft().displayGuiScreen(clickGui);
            return;
        }

        for (Module module : this.modules.values()) {
            if (module.getKey() != event.getKey()) {
                continue;
            }
            boolean shouldNotify = module.toggle();
            HUD hud = (HUD) this.modules.get(HUD.class);
            QQGroup qqgroup = (QQGroup) this.modules.get(QQGroup.class);
            if (hud != null && shouldNotify) {
                shouldNotify = hud.toggleAlerts.getValue();
            }
            if (shouldNotify) {

                String status = module.isEnabled() ? "&a&lON" : "&c&lOFF";
                String message = String.format("%s%s: %s&r", Myau.clientName, module.getName(), status);
                ChatUtil.sendFormatted(message);
            }
        }
    }

    @EventTarget
    public void onTick(TickEvent event) {
        if (event.getType() == EventType.PRE) {
            if (this.sound) {
                this.sound = false;
                SoundUtil.playSound("random.click");
            }
        }
    }
}
