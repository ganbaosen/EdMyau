package myau.module.modules;

import myau.module.Module;
import myau.util.KeyBindUtil;
import myau.events.KeyEvent;
import myau.util.ChatUtil;
import java.util.LinkedHashMap;

public class QQGroup extends Module {
    public final LinkedHashMap<Class<?>, Module> modules = new LinkedHashMap<>();
    public QQGroup() {
        super("BestStore", true);
    }
    public String formatModule() {
        return String.format(
                "%s%s &r(%s&r)",
                this.key == 0 ? "" : String.format("&l[%s] &r", KeyBindUtil.getKeyName(this.key)),
                this.name,
                this.enabled ? "&a&lON" : "&c&lOFF"
        );
    }
    public void onKey(KeyEvent event) {
        for (Module module : this.modules.values()) {
            if (module.getKey() != event.getKey()) {
                continue;
            }
            boolean shouldNotify = module.toggle();
            HUD hud = (HUD) this.modules.get(HUD.class);
            if (hud != null && shouldNotify) {
                shouldNotify = hud.toggleAlerts.getValue();
            }
            if(module instanceof GuiModule){
                shouldNotify = false;
            }
            if (shouldNotify) {
                String message = String.format("%s%s: %s&r", myau.Myau.clientName, module.getName(), "Get Config in 1076927386");
                ChatUtil.sendFormatted(message);
            }
        }
    }
    public String[] getSuffix() {
        return new String[]{"shop.dotshop.top"};
    }
}
