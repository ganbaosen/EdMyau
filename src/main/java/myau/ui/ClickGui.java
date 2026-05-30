
package myau.ui;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import myau.Myau;
import myau.module.Module;
import myau.module.modules.*;
import myau.property.properties.BooleanProperty;
import myau.property.properties.FloatProperty;
import myau.ui.components.CategoryComponent;
import myau.util.Timer;
import myau.util.shader.BlurUtils;
import myau.util.shader.RoundedUtils;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.gui.inventory.GuiInventory;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import org.lwjgl.input.Mouse;
import org.lwjgl.opengl.GL11;

import java.awt.*;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.List;

public class ClickGui extends GuiScreen {
    private static ClickGui instance;
    private final File configFile = new File("./config/Myau/", "clickgui.txt");
    private final ArrayList<CategoryComponent> categoryList;

    // GUI Settings (merged from Gui module)
    public static final FloatProperty backgroundBlur = new FloatProperty("Background Blur", 0.0f, 0.0f, 100.0f);
    public static final BooleanProperty darkBackground = new BooleanProperty("Dark Background", true);
    public static final BooleanProperty removeWatermark = new BooleanProperty("Remove Watermark", false);
    public static final BooleanProperty removePlayerModel = new BooleanProperty("Remove Player Model", false);
    public static final FloatProperty guiScale = new FloatProperty("GUI Scale", 1.0f, 0.5f, 2.0f);

    // Raven-style additions
    private Timer logoSmoothWidth;
    private Timer logoSmoothLength;
    private Timer smoothEntity;
    private Timer backgroundFade;
    private Timer blurSmooth;
    private ScaledResolution sr;
    private int actualScreenWidth;
    private int actualScreenHeight;
    private double previousScale;
    private static boolean isNotFirstOpen;

    public ClickGui() {
        instance = this;

        List<Module> combatModules = new ArrayList<>();
        combatModules.add(Myau.moduleManager.getModule(AimAssist.class));
        combatModules.add(Myau.moduleManager.getModule(AutoBlock.class));
        combatModules.add(Myau.moduleManager.getModule(AutoClicker.class));
        combatModules.add(Myau.moduleManager.getModule(KillAura.class));
        combatModules.add(Myau.moduleManager.getModule(Wtap.class));
        combatModules.add(Myau.moduleManager.getModule(Velocity.class));
        combatModules.add(Myau.moduleManager.getModule(Velocity1.class));
        combatModules.add(Myau.moduleManager.getModule(Freeze.class));
        combatModules.add(Myau.moduleManager.getModule(Reach.class));
        combatModules.add(Myau.moduleManager.getModule(TargetStrafe.class));
        combatModules.add(Myau.moduleManager.getModule(NoHitDelay.class));
        combatModules.add(Myau.moduleManager.getModule(AntiFireball.class));
        combatModules.add(Myau.moduleManager.getModule(LagRange.class));
        combatModules.add(Myau.moduleManager.getModule(HitBox.class));
        combatModules.add(Myau.moduleManager.getModule(MoreKB.class));
        combatModules.add(Myau.moduleManager.getModule(Refill.class));
        combatModules.add(Myau.moduleManager.getModule(HitSelect.class));

        List<Module> movementModules = new ArrayList<>();
        movementModules.add(Myau.moduleManager.getModule(AntiAFK.class));
        movementModules.add(Myau.moduleManager.getModule(Fly.class));
        movementModules.add(Myau.moduleManager.getModule(Speed.class));
        movementModules.add(Myau.moduleManager.getModule(LongJump.class));
        movementModules.add(Myau.moduleManager.getModule(Sprint.class));
        movementModules.add(Myau.moduleManager.getModule(SafeWalk.class));
        movementModules.add(Myau.moduleManager.getModule(Jesus.class));
        movementModules.add(Myau.moduleManager.getModule(Blink.class));
        movementModules.add(Myau.moduleManager.getModule(NoFall.class));
        movementModules.add(Myau.moduleManager.getModule(NoSlow.class));
        movementModules.add(Myau.moduleManager.getModule(Noslow1.class));
        movementModules.add(Myau.moduleManager.getModule(KeepSprint.class));
        movementModules.add(Myau.moduleManager.getModule(Eagle.class));
        movementModules.add(Myau.moduleManager.getModule(NoJumpDelay.class));
        movementModules.add(Myau.moduleManager.getModule(AntiVoid.class));
        movementModules.add(Myau.moduleManager.getModule(ReduceVelocity.class));
        movementModules.add(Myau.moduleManager.getModule(Stuck.class));

        List<Module> renderModules = new ArrayList<>();
        renderModules.add(Myau.moduleManager.getModule(ESP.class));
        renderModules.add(Myau.moduleManager.getModule(Chams.class));
        renderModules.add(Myau.moduleManager.getModule(FullBright.class));
        renderModules.add(Myau.moduleManager.getModule(Tracers.class));
        renderModules.add(Myau.moduleManager.getModule(NameTags.class));
        renderModules.add(Myau.moduleManager.getModule(Xray.class));
        renderModules.add(Myau.moduleManager.getModule(TargetHUD.class));
        renderModules.add(Myau.moduleManager.getModule(Indicators.class));
        renderModules.add(Myau.moduleManager.getModule(BedESP.class));
        renderModules.add(Myau.moduleManager.getModule(ItemESP.class));
        renderModules.add(Myau.moduleManager.getModule(ViewClip.class));
        renderModules.add(Myau.moduleManager.getModule(NoHurtCam.class));
        renderModules.add(Myau.moduleManager.getModule(HUD.class));
        renderModules.add(Myau.moduleManager.getModule(ChestESP.class));
        renderModules.add(Myau.moduleManager.getModule(Trajectories.class));
        renderModules.add(Myau.moduleManager.getModule(Radar.class));
        renderModules.add(Myau.moduleManager.getModule(Animations.class));
        renderModules.add(Myau.moduleManager.getModule(QQGroup.class));

        List<Module> playerModules = new ArrayList<>();
        playerModules.add(Myau.moduleManager.getModule(AutoHeal.class));
        playerModules.add(Myau.moduleManager.getModule(AutoTool.class));
        //playerModules.add(Myau.moduleManager.getModule(ChestAura.class));
        playerModules.add(Myau.moduleManager.getModule(ChestStealer.class));
        playerModules.add(Myau.moduleManager.getModule(InvManager.class));
        playerModules.add(Myau.moduleManager.getModule(InvWalk.class));
        playerModules.add(Myau.moduleManager.getModule(Scaffold.class));
        playerModules.add(Myau.moduleManager.getModule(AutoBlockIn.class));
        playerModules.add(Myau.moduleManager.getModule(SpeedMine.class));
        playerModules.add(Myau.moduleManager.getModule(FastPlace.class));
        playerModules.add(Myau.moduleManager.getModule(GhostHand.class));
        playerModules.add(Myau.moduleManager.getModule(MCF.class));
        playerModules.add(Myau.moduleManager.getModule(AntiDebuff.class));
        playerModules.add(Myau.moduleManager.getModule(AutoGapple.class));
        playerModules.add(Myau.moduleManager.getModule(ThrowAura.class));

        List<Module> miscModules = new ArrayList<>();
        miscModules.add(Myau.moduleManager.getModule(Spammer.class));
        miscModules.add(Myau.moduleManager.getModule(BedNuker.class));
        miscModules.add(Myau.moduleManager.getModule(BedTracker.class));
        miscModules.add(Myau.moduleManager.getModule(LightningTracker.class));
        miscModules.add(Myau.moduleManager.getModule(NoRotate.class));
        miscModules.add(Myau.moduleManager.getModule(NickHider.class));
        miscModules.add(Myau.moduleManager.getModule(AntiObbyTrap.class));
        miscModules.add(Myau.moduleManager.getModule(AntiObfuscate.class));
        miscModules.add(Myau.moduleManager.getModule(AutoAnduril.class));
        miscModules.add(Myau.moduleManager.getModule(InventoryClicker.class));
        miscModules.add(Myau.moduleManager.getModule(PartySpammer.class));

        Comparator<Module> comparator = Comparator.comparing(m -> m.getName().toLowerCase());
        combatModules.sort(comparator);
        movementModules.sort(comparator);
        renderModules.sort(comparator);
        playerModules.sort(comparator);
        miscModules.sort(comparator);

        Set<Module> registered = new HashSet<>();
        registered.addAll(combatModules);
        registered.addAll(movementModules);
        registered.addAll(renderModules);
        registered.addAll(playerModules);
        registered.addAll(miscModules);

        for (Module module : Myau.moduleManager.modules.values()) {
            if (!registered.contains(module)) {
                throw new RuntimeException(module.getClass().getName() + " is unregistered to click gui.");
            }
        }

        this.categoryList = new ArrayList<>();
        int topOffset = 5;


        CategoryComponent combat = new CategoryComponent("Combat", combatModules);
        combat.setY(topOffset);
        categoryList.add(combat);
        topOffset += 20;

        CategoryComponent movement = new CategoryComponent("Movement", movementModules);
        movement.setY(topOffset);
        categoryList.add(movement);
        topOffset += 20;

        CategoryComponent render = new CategoryComponent("Render", renderModules);
        render.setY(topOffset);
        categoryList.add(render);
        topOffset += 20;

        CategoryComponent player = new CategoryComponent("Player", playerModules);
        player.setY(topOffset);
        categoryList.add(player);
        topOffset += 20;

        CategoryComponent misc = new CategoryComponent("Misc", miscModules);
        misc.setY(topOffset);
        categoryList.add(misc);

        loadPositions();
    }

    public void initMain() {
        this.blurSmooth = this.backgroundFade = new Timer(500.0f);
        this.smoothEntity = this.backgroundFade;
        this.logoSmoothWidth = this.backgroundFade;
        this.backgroundFade.start();

        // Delayed logo animation
        new Thread(() -> {
            try {
                Thread.sleep(650);
                this.logoSmoothLength = new Timer(650.0f);
                this.logoSmoothLength.start();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }).start();
    }

    public static ClickGui getInstance() {
        return instance;
    }

    public void initGui() {
        super.initGui();

        double configuredScale = this.getConfiguredGuiScale();
        if (!isNotFirstOpen) {
            isNotFirstOpen = true;
            this.previousScale = configuredScale;
            initMain();
        }

        for (CategoryComponent categoryComponent : categoryList) {
            categoryComponent.setScreenSize(this.width, this.height);
        }

        if (Double.compare(this.previousScale, configuredScale) != 0) {
            for (CategoryComponent categoryComponent : categoryList) {
                categoryComponent.limitPositions();
            }
        }

        this.previousScale = configuredScale;
        this.refreshScaledResolution();
    }

    public void drawScreen(int x, int y, float p) {
        int logicalMouseX = this.toLogicalCoordinate(x);
        int logicalMouseY = this.toLogicalCoordinate(y);

        // Background blur effect
        if (backgroundBlur.getValue() != 0.0) {
            BlurUtils.prepareBlur();
            RoundedUtils.drawRound(0.0f, 0.0f, (float)this.actualScreenWidth, (float)this.actualScreenHeight, 0.0f, true, Color.black);
            float inputToRange = (float)(3.0 * ((backgroundBlur.getValue() + 35.0) / 100.0));
            BlurUtils.blurEnd(2, this.blurSmooth.getValueFloat(0.0f, inputToRange, 1));
        }

        // Dark background
        if (darkBackground.getValue()) {
            drawRect(0, 0, this.actualScreenWidth, this.actualScreenHeight,
                    (int)(this.backgroundFade.getValueFloat(0.0f, 0.7f, 2) * 255.0f) << 24);
        }

        GlStateManager.pushMatrix();
        GlStateManager.scale(this.getRenderScale(), this.getRenderScale(), 1.0);

        // Draw watermark logo
        if (!removeWatermark.getValue()) {
            this.drawWatermark();
        }

        // Render categories
        List<CategoryComponent> renderOrder = this.getCategoriesInRenderOrder();
        CategoryComponent topmostUnderCursor = this.getTopmostUnderCursor(renderOrder, logicalMouseX, logicalMouseY);

        for (CategoryComponent c : renderOrder) {
            c.render(this.fontRendererObj);
            c.handleDrag(logicalMouseX, logicalMouseY);

            for (Component component : c.getModules()) {
                component.update(logicalMouseX, logicalMouseY);
            }
        }

        GL11.glColor3f(1.0f, 1.0f, 1.0f);

        // Draw player model
        if (!removePlayerModel.getValue()) {
            GlStateManager.pushMatrix();
            GlStateManager.enableDepth();
            GuiInventory.drawEntityOnScreen(
                this.width + 15 - this.smoothEntity.getValueInt(0, 40, 2),
                this.height - 10,
                40,
                (float)(this.width - 25 - logicalMouseX),
                (float)(this.height - 50 - logicalMouseY),
                (EntityLivingBase)this.mc.thePlayer
            );
            GlStateManager.disableDepth();
            GlStateManager.popMatrix();
        }

        GlStateManager.popMatrix();

        // Handle mouse wheel scrolling
        int wheel = Mouse.getDWheel();
        if (wheel != 0) {
            int scrollDir = wheel > 0 ? 1 : -1;
            for (CategoryComponent category : categoryList) {
                category.onScroll(logicalMouseX, logicalMouseY, scrollDir);
            }
        }
    }

    public void mouseClicked(int x, int y, int mouseButton) {
        Iterator<CategoryComponent> btnCat = categoryList.iterator();
        while (true) {
            CategoryComponent category;
            do {
                do {
                    if (!btnCat.hasNext()) {
                        return;
                    }

                    category = btnCat.next();
                    if (category.insideArea(x, y) && !category.isHovered(x, y) && !category.mousePressed(x, y) && mouseButton == 0) {
                        category.mousePressed(true);
                        category.xx = x - category.getX();
                        category.yy = y - category.getY();
                    }

                    if (category.mousePressed(x, y) && mouseButton == 0) {
                        category.setOpened(!category.isOpened());
                    }

                    if (category.isHovered(x, y) && mouseButton == 0) {
                        category.setPin(!category.isPin());
                    }
                } while (!category.isOpened());
            } while (category.getModules().isEmpty());

            for (Component c : category.getModules()) {
                c.mouseDown(x, y, mouseButton);
            }
        }

    }

    public void mouseReleased(int x, int y, int mouseButton) {
        Iterator<CategoryComponent> iterator = categoryList.iterator();

        CategoryComponent categoryComponent;
        while (iterator.hasNext()) {
            categoryComponent = iterator.next();
            if (mouseButton == 0) {
                categoryComponent.mousePressed(false);
            }
        }

        iterator = categoryList.iterator();

        while (true) {
            do {
                do {
                    if (!iterator.hasNext()) {
                        return;
                    }

                    categoryComponent = iterator.next();
                } while (!categoryComponent.isOpened());
            } while (categoryComponent.getModules().isEmpty());

            for (Component component : categoryComponent.getModules()) {
                component.mouseReleased(x, y, mouseButton);
            }
        }
    }

    public void keyTyped(char typedChar, int key) {
        if (key == 1) {
            this.mc.displayGuiScreen(null);
        } else {
            Iterator<CategoryComponent> btnCat = categoryList.iterator();

            while (true) {
                CategoryComponent cat;
                do {
                    do {
                        if (!btnCat.hasNext()) {
                            return;
                        }

                        cat = btnCat.next();
                    } while (!cat.isOpened());
                } while (cat.getModules().isEmpty());

                for (Component component : cat.getModules()) {
                    component.keyTyped(typedChar, key);
                }
            }
        }
    }

    public void onGuiClosed() {
        savePositions();
    }

    public boolean doesGuiPauseGame() {
        return false;
    }

    private void drawWatermark() {
        int h = this.height / 4;
        int wd = this.width / 2;
        int w_c = 30 - this.logoSmoothWidth.getValueInt(0, 30, 3);

        // Draw "Myau" vertically with color gradient
        this.drawString(this.fontRendererObj, "M", wd + 1 - w_c, h - 25, getChroma(2L, 1500L));
        this.drawString(this.fontRendererObj, "y", wd - w_c, h - 15, getChroma(2L, 1200L));
        this.drawString(this.fontRendererObj, "a", wd - w_c, h - 5, getChroma(2L, 900L));
        this.drawString(this.fontRendererObj, "u", wd - w_c, h + 5, getChroma(2L, 600L));

        // Draw version
        this.drawString(this.fontRendererObj, Myau.version, wd + 1 + w_c, h + 30, getChroma(2L, 0L));

        // Draw lines
        this.drawVerticalLine(wd - 10 - w_c, h - 30, h + 43, Color.white.getRGB());
        this.drawVerticalLine(wd + 10 + w_c, h - 30, h + 43, Color.white.getRGB());

        // Animated horizontal lines
        if (this.logoSmoothLength != null) {
            int r = this.logoSmoothLength.getValueInt(0, 20, 2);
            this.drawHorizontalLine(wd - 10, wd - 10 + r, h - 29, -1);
            this.drawHorizontalLine(wd + 10, wd + 10 - r, h + 42, -1);
        }
    }

    private int getChroma(long offset, long speed) {
        float hue = (System.currentTimeMillis() + offset * speed) % 10000 / 10000.0f;
        return Color.HSBtoRGB(hue, 0.8f, 1.0f);
    }

    private List<CategoryComponent> getCategoriesInRenderOrder() {
        ArrayList<CategoryComponent> renderOrder = new ArrayList<>(categoryList);
        renderOrder.sort(Comparator.comparingLong(c -> c.lastInteractedTime));
        return renderOrder;
    }

    private CategoryComponent getTopmostUnderCursor(List<CategoryComponent> renderOrder, int x, int y) {
        for (int i = renderOrder.size() - 1; i >= 0; --i) {
            if (renderOrder.get(i).insideArea(x, y)) {
                return renderOrder.get(i);
            }
        }
        return null;
    }

    private void refreshScaledResolution() {
        this.sr = new ScaledResolution(this.mc);
        this.actualScreenWidth = this.sr.getScaledWidth();
        this.actualScreenHeight = this.sr.getScaledHeight();
        double targetScaleFactor = this.getTargetGuiScaleFactor();
        this.width = Math.max(1, MathHelper.ceiling_float_int((float)this.mc.displayWidth / (float)targetScaleFactor));
        this.height = Math.max(1, MathHelper.ceiling_float_int((float)this.mc.displayHeight / (float)targetScaleFactor));
    }

    private double getTargetGuiScaleFactor() {
        return Math.max(1.0, Math.min((double)this.getMaximumGuiScaleFactor(), this.getConfiguredGuiScale() * 2.0));
    }

    private int getMaximumGuiScaleFactor() {
        int scaleFactor = 1;
        while (this.mc.displayWidth / (scaleFactor + 1) >= 320 && this.mc.displayHeight / (scaleFactor + 1) >= 240) {
            ++scaleFactor;
        }
        return scaleFactor;
    }

    private int toLogicalCoordinate(int coordinate) {
        return (int)Math.floor((double)coordinate / this.getRenderScale());
    }

    private double getRenderScale() {
        return this.actualScreenWidth <= 0 || this.width <= 0 ? 1.0 : (double)this.actualScreenWidth / (double)this.width;
    }

    private double getConfiguredGuiScale() {
        return guiScale.getValue();
    }

    public static double getClickGuiScale() {
        return guiScale.getValue();
    }

    private void savePositions() {
        JsonObject json = new JsonObject();
        for (CategoryComponent cat : categoryList) {
            JsonObject pos = new JsonObject();
            pos.addProperty("x", cat.getX());
            pos.addProperty("y", cat.getY());
            pos.addProperty("open", cat.isOpened());
            json.add(cat.getName(), pos);
        }
        try (FileWriter writer = new FileWriter(configFile)) {
            new GsonBuilder().setPrettyPrinting().create().toJson(json, writer);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void loadPositions() {
        if (!configFile.exists()) return;
        try (FileReader reader = new FileReader(configFile)) {
            JsonObject json = new JsonParser().parse(reader).getAsJsonObject();
            for (CategoryComponent cat : categoryList) {
                if (json.has(cat.getName())) {
                    JsonObject pos = json.getAsJsonObject(cat.getName());
                    cat.setX(pos.get("x").getAsInt());
                    cat.setY(pos.get("y").getAsInt());
                    cat.setOpened(pos.get("open").getAsBoolean());
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
