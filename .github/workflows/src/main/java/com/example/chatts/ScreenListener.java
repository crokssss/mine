package com.example.chatts;

import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraftforge.client.event.ScreenEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Custom NPCs (and similar mods) often show dialogue in their own GUI window instead of chat.
 * Since we don't have a compile-time dependency on that mod's exact classes (versions/forks differ),
 * this scans the opened screen's fields and widgets via reflection, looking for text.
 *
 * Turn on TTSConfig.DEBUG_SCAN to see exactly what text this finds and where (class.field),
 * then set TTSConfig.TARGET_FIELD_NAME to lock onto the correct one precisely.
 */
public class ScreenListener {

    private Screen lastScreen;

    @SubscribeEvent
    public void onScreenInit(ScreenEvent.Init.Post event) {
        if (!TTSConfig.SCREEN_ENABLED.get()) return;

        Screen screen = event.getScreen();
        if (screen == lastScreen) return;
        lastScreen = screen;

        String className = screen.getClass().getName();
        String keyword = TTSConfig.NPC_SCREEN_KEYWORD.get();
        if (keyword != null && !keyword.isEmpty()
                && !className.toLowerCase().contains(keyword.toLowerCase())) {
            return;
        }

        List<FoundText> found = scanForText(screen);
        if (found.isEmpty()) {
            if (TTSConfig.DEBUG_SCAN.get()) {
                DebugEcho.print("[chatts-debug] Screen matched (" + className + ") but no text fields found.");
            }
            return;
        }

        if (TTSConfig.DEBUG_SCAN.get()) {
            DebugEcho.print("[chatts-debug] Screen: " + className);
            for (FoundText ft : found) {
                DebugEcho.print("[chatts-debug]   " + ft.source + "." + ft.fieldName + " = " + trim(ft.text));
            }
        }

        String targetField = TTSConfig.TARGET_FIELD_NAME.get();
        if (targetField != null && !targetField.isEmpty()) {
            for (FoundText ft : found) {
                if (ft.fieldName.equalsIgnoreCase(targetField)) {
                    TTSManager.getInstance().speak(ft.text);
                    return;
                }
            }
            return;
        }

        // No target field configured yet: fall back to speaking the longest candidate string.
        FoundText best = found.get(0);
        for (FoundText ft : found) {
            if (ft.text.length() > best.text.length()) best = ft;
        }
        TTSManager.getInstance().speak(best.text);
    }

    private String trim(String s) {
        return s.length() > 70 ? s.substring(0, 70) + "..." : s;
    }

    private static class FoundText {
        final String source;
        final String fieldName;
        final String text;

        FoundText(String source, String fieldName, String text) {
            this.source = source;
            this.fieldName = fieldName;
            this.text = text;
        }
    }

    private List<FoundText> scanForText(Screen screen) {
        List<FoundText> results = new ArrayList<>();
        Set<Object> visited = new HashSet<>();

        scanObject(screen, screen.getClass(), results, visited, 0);

        for (Object widget : screen.children()) {
            if (widget instanceof AbstractWidget aw) {
                scanObject(aw, aw.getClass(), results, visited, 0);
            }
        }
        return results;
    }

    private void scanObject(Object obj, Class<?> clazz, List<FoundText> results, Set<Object> visited, int depth) {
        if (obj == null || depth > 2) return;
        if (!visited.add(obj)) return;

        Class<?> c = clazz;
        while (c != null && c != Object.class) {
            for (Field f : c.getDeclaredFields()) {
                try {
                    f.setAccessible(true);
                    Object value = f.get(obj);
                    if (value == null) continue;

                    if (value instanceof String s) {
                        addIfCandidate(results, obj.getClass().getSimpleName(), f.getName(), s);
                    } else if (value instanceof Component comp) {
                        addIfCandidate(results, obj.getClass().getSimpleName(), f.getName(), comp.getString());
                    } else if (value instanceof List<?> list && depth < 1) {
                        for (Object item : list) {
                            if (item instanceof String s) {
                                addIfCandidate(results, obj.getClass().getSimpleName(), f.getName(), s);
                            } else if (item instanceof Component comp) {
                                addIfCandidate(results, obj.getClass().getSimpleName(), f.getName(), comp.getString());
                            }
                        }
                    }
                } catch (Exception ignored) {
                }
            }
            c = c.getSuperclass();
        }
    }

    private void addIfCandidate(List<FoundText> results, String source, String fieldName, String text) {
        if (text == null) return;
        String trimmed = text.trim();
        if (trimmed.length() < 3) return;
        if (trimmed.chars().allMatch(Character::isDigit)) return;
        results.add(new FoundText(source, fieldName, trimmed));
    }
}
