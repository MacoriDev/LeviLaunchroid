package org.levimc.launcher.core.mods.inbuilt.overlay;

import android.app.Activity;
import android.view.MotionEvent;

import org.levimc.launcher.core.mods.inbuilt.manager.InbuiltModManager;
import org.levimc.launcher.core.mods.inbuilt.model.ModIds;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InbuiltOverlayManager {
    private static volatile InbuiltOverlayManager instance;
    private final Activity activity;
    private final List<BaseOverlayButton> overlays = new ArrayList<>();
    private final Map<String, Boolean> modActiveStates = new HashMap<>();
    private final Map<String, BaseOverlayButton> modOverlayMap = new HashMap<>();
    private final Map<String, Integer> modPositionMap = new HashMap<>();
    private ZoomOverlay zoomOverlay;
    private CpsDisplayOverlay cpsDisplayOverlay;
    private ModMenuButton modMenuButton;
    private int baseY = 150;
    private static final int SPACING = 70;
    private static final int START_X = 50;
    private boolean isModMenuMode = false;

    public InbuiltOverlayManager(Activity activity) {
        this.activity = activity;
        instance = this;
    }

    public static InbuiltOverlayManager getInstance() {
        return instance;
    }

    public void showEnabledOverlays() {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        int nextY = baseY;

        isModMenuMode = manager.isModMenuEnabled();

        if (isModMenuMode) {
            nextY = showModMenuMode(manager, nextY);
        } else {
            nextY = showIndividualOverlays(manager, nextY);
        }

    }

    private int showModMenuMode(InbuiltModManager manager, int nextY) {
        modActiveStates.put(ModIds.QUICK_DROP, false);
        modActiveStates.put(ModIds.TOGGLE_HUD, false);
        modActiveStates.put(ModIds.AUTO_SPRINT, false);
        modActiveStates.put(ModIds.ZOOM, false);
        modActiveStates.put(ModIds.CPS_DISPLAY, false);
        modActiveStates.put(ModIds.VIRTUAL_CURSOR, false);

        modPositionMap.put(ModIds.QUICK_DROP, nextY + SPACING);
        modPositionMap.put(ModIds.TOGGLE_HUD, nextY + SPACING * 2);
        modPositionMap.put(ModIds.AUTO_SPRINT, nextY + SPACING * 3);
        modPositionMap.put(ModIds.ZOOM, nextY + SPACING * 4);
        modPositionMap.put(ModIds.CPS_DISPLAY, nextY + SPACING * 5);
        modPositionMap.put(ModIds.VIRTUAL_CURSOR, nextY + SPACING * 6);

        if (zoomOverlay == null) {
            zoomOverlay = new ZoomOverlay(activity);
            zoomOverlay.initializeForKeyboard();
        }


        modMenuButton = new ModMenuButton(activity);
        modMenuButton.show(START_X, nextY);
        return nextY + SPACING;
    }

    public void handleModToggle(String modId, boolean enabled) {
        boolean wasEnabled = modActiveStates.getOrDefault(modId, false);
        modActiveStates.put(modId, enabled);
        
        if (enabled && !wasEnabled) {
            showModOverlay(modId);
        } else if (!enabled && wasEnabled) {
            hideModOverlay(modId);
        }
    }

    private void showModOverlay(String modId) {
        if (modOverlayMap.containsKey(modId)) {
            return;
        }

        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        int posY = modPositionMap.getOrDefault(modId, baseY + SPACING);

        int savedX = manager.getOverlayPositionX(modId, START_X);
        int savedY = manager.getOverlayPositionY(modId, posY);

        switch (modId) {
            case ModIds.QUICK_DROP:
                QuickDropOverlay quickDrop = new QuickDropOverlay(activity);
                quickDrop.show(savedX, savedY);
                overlays.add(quickDrop);
                modOverlayMap.put(modId, quickDrop);
                break;
            case ModIds.TOGGLE_HUD:
                ToggleHudOverlay hud = new ToggleHudOverlay(activity);
                hud.show(savedX, savedY);
                overlays.add(hud);
                modOverlayMap.put(modId, hud);
                break;
            case ModIds.AUTO_SPRINT:
                AutoSprintOverlay sprint = new AutoSprintOverlay(activity, manager.getAutoSprintKeybind());
                sprint.show(savedX, savedY);
                overlays.add(sprint);
                modOverlayMap.put(modId, sprint);
                break;
            case ModIds.ZOOM:
                if (zoomOverlay == null) {
                    zoomOverlay = new ZoomOverlay(activity);
                }
                zoomOverlay.show(savedX, savedY);
                overlays.add(zoomOverlay);
                modOverlayMap.put(modId, zoomOverlay);
                break;
            case ModIds.CPS_DISPLAY:
                if (cpsDisplayOverlay == null) {
                    cpsDisplayOverlay = new CpsDisplayOverlay(activity);
                    cpsDisplayOverlay.show(savedX, savedY);
                }
                break;
            case ModIds.VIRTUAL_CURSOR:
                VirtualCursorOverlay cursorOverlay = new VirtualCursorOverlay(activity);
                cursorOverlay.show(savedX, savedY);
                overlays.add(cursorOverlay);
                modOverlayMap.put(modId, cursorOverlay);
                break;
        }
    }

    private void hideModOverlay(String modId) {
        if (modId.equals(ModIds.ZOOM)) {
            if (zoomOverlay != null) {
                zoomOverlay.hide();
                overlays.remove(zoomOverlay);
                modOverlayMap.remove(modId);

                if (!isModMenuMode) {
                    zoomOverlay = null;
                }
            }
            return;
        }

        if (modId.equals(ModIds.CPS_DISPLAY)) {
            if (cpsDisplayOverlay != null) {
                cpsDisplayOverlay.hide();
                cpsDisplayOverlay = null;
            }
            return;
        }
        
        BaseOverlayButton overlay = modOverlayMap.get(modId);
        if (overlay != null) {
            overlay.hide();
            overlays.remove(overlay);
            modOverlayMap.remove(modId);
        }
    }

    public boolean isModActive(String modId) {
        return modActiveStates.getOrDefault(modId, false);
    }

    private int showIndividualOverlays(InbuiltModManager manager, int nextY) {
        if (manager.isModAdded(ModIds.QUICK_DROP)) {
            int x = manager.getOverlayPositionX(ModIds.QUICK_DROP, START_X);
            int y = manager.getOverlayPositionY(ModIds.QUICK_DROP, nextY);
            QuickDropOverlay overlay = new QuickDropOverlay(activity);
            overlay.show(x, y);
            overlays.add(overlay);
            nextY += SPACING;
        }
        if (manager.isModAdded(ModIds.TOGGLE_HUD)) {
            int x = manager.getOverlayPositionX(ModIds.TOGGLE_HUD, START_X);
            int y = manager.getOverlayPositionY(ModIds.TOGGLE_HUD, nextY);
            ToggleHudOverlay overlay = new ToggleHudOverlay(activity);
            overlay.show(x, y);
            overlays.add(overlay);
            nextY += SPACING;
        }
        if (manager.isModAdded(ModIds.AUTO_SPRINT)) {
            int x = manager.getOverlayPositionX(ModIds.AUTO_SPRINT, START_X);
            int y = manager.getOverlayPositionY(ModIds.AUTO_SPRINT, nextY);
            AutoSprintOverlay overlay = new AutoSprintOverlay(activity, manager.getAutoSprintKeybind());
            overlay.show(x, y);
            overlays.add(overlay);
            nextY += SPACING;
        }

        if (manager.isModAdded(ModIds.ZOOM)) {
            int x = manager.getOverlayPositionX(ModIds.ZOOM, START_X);
            int y = manager.getOverlayPositionY(ModIds.ZOOM, nextY);
            zoomOverlay = new ZoomOverlay(activity);
            zoomOverlay.show(x, y);
            overlays.add(zoomOverlay);
            modOverlayMap.put(ModIds.ZOOM, zoomOverlay);
            nextY += SPACING;
        }

        if (manager.isModAdded(ModIds.CPS_DISPLAY)) {
            int x = manager.getOverlayPositionX(ModIds.CPS_DISPLAY, START_X);
            int y = manager.getOverlayPositionY(ModIds.CPS_DISPLAY, nextY);
            cpsDisplayOverlay = new CpsDisplayOverlay(activity);
            cpsDisplayOverlay.show(x, y);
            nextY += SPACING;
        }

        if (manager.isModAdded(ModIds.VIRTUAL_CURSOR)) {
            int x = manager.getOverlayPositionX(ModIds.VIRTUAL_CURSOR, START_X);
            int y = manager.getOverlayPositionY(ModIds.VIRTUAL_CURSOR, nextY);
            VirtualCursorOverlay overlay = new VirtualCursorOverlay(activity);
            overlay.show(x, y);
            overlays.add(overlay);
            nextY += SPACING;
        }
        return nextY;
    }


    public void hideAllOverlays() {
        for (BaseOverlayButton overlay : overlays) {
            overlay.hide();
        }
        overlays.clear();
        modOverlayMap.clear();
        modActiveStates.clear();
        modPositionMap.clear();
        if (zoomOverlay != null) {
            zoomOverlay.hide();
            zoomOverlay = null;
        }
        if (cpsDisplayOverlay != null) {
            cpsDisplayOverlay.hide();
            cpsDisplayOverlay = null;
        }
        if (modMenuButton != null) {
            modMenuButton.hide();
            modMenuButton = null;
        }
        instance = null;
    }

    public boolean handleKeyEvent(int keyCode, int action) {
        InbuiltModManager manager = InbuiltModManager.getInstance(activity);
        
        boolean zoomEnabled = isModMenuMode 
            ? modActiveStates.getOrDefault(ModIds.ZOOM, false)
            : manager.isModAdded(ModIds.ZOOM);
        
        int zoomKeybind = manager.getZoomKeybind();
        if (zoomEnabled && keyCode == zoomKeybind) {
            if (zoomOverlay != null) {
                if (action == android.view.KeyEvent.ACTION_DOWN) {
                    zoomOverlay.onKeyDown();
                    return true;
                } else if (action == android.view.KeyEvent.ACTION_UP) {
                    zoomOverlay.onKeyUp();
                    return true;
                }
            }
        }


        return false;
    }

    public boolean handleScrollEvent(float scrollDelta) {
        if (zoomOverlay != null && zoomOverlay.isZooming()) {
            zoomOverlay.onScroll(scrollDelta);
            return true;
        }
        return false;
    }

    public boolean handleTouchEvent(MotionEvent event) {
        if (cpsDisplayOverlay != null) {
            return cpsDisplayOverlay.handleTouchEvent(event);
        }
        return false;
    }

    public boolean handleMouseEvent(MotionEvent event) {
        if (cpsDisplayOverlay != null) {
            return cpsDisplayOverlay.handleMouseEvent(event);
        }
        return false;
    }

    public void applyConfigurationChanges(String modId) {
        BaseOverlayButton overlay = modOverlayMap.get(modId);
        if (overlay != null) {
            overlay.applyConfigurationChanges();
        }

        if (modId.equals(ModIds.ZOOM) && zoomOverlay != null) {
            zoomOverlay.applyConfigurationChanges();
        }
        if (modId.equals(ModIds.CPS_DISPLAY) && cpsDisplayOverlay != null) {
            cpsDisplayOverlay.applyConfigurationChanges();
        }
    }

    public void tick() {
        for (BaseOverlayButton overlay : overlays) {
            overlay.tick();
        }
    }
}
