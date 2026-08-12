package com.wachipayoxx.wbackpacks.client;

import com.wachipayoxx.wbackpacks.WBackpacks;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;
import net.neoforged.fml.loading.FMLPaths;

final class BackpackWindowStateStore {
    private static final Path FILE = FMLPaths.CONFIGDIR.get().resolve("w_backpacks-windows.properties");
    private final Properties values = new Properties();

    BackpackWindowStateStore() {
        load();
    }

    boolean wasOpen(String id) {
        return Boolean.parseBoolean(values.getProperty(key(id, "open"), "false"));
    }

    boolean minimized(String id) {
        return Boolean.parseBoolean(values.getProperty(key(id, "minimized"), "false"));
    }

    int x(String id, int fallback) {
        return readInt(key(id, "x"), fallback);
    }

    int y(String id, int fallback) {
        return readInt(key(id, "y"), fallback);
    }

    int z(String id, int fallback) {
        return readInt(key(id, "z"), fallback);
    }

    int columns(String id, int fallback) {
        return readInt(key(id, "columns"), fallback);
    }

    int rows(String id, int fallback) {
        return readInt(key(id, "rows"), fallback);
    }

    int layoutColumns(String id, int fallback) {
        return readInt(key(id, "layoutColumns"), fallback);
    }

    int scrollColumn(String id) {
        return readInt(key(id, "scrollColumn"), 0);
    }

    int scrollRow(String id, int layoutColumns) {
        String modernKey = key(id, "scrollRow");
        if (values.containsKey(modernKey)) {
            return readInt(modernKey, 0);
        }
        int legacySlot = readInt(key(id, "scrollSlot"), readInt(key(id, "scroll"), 0));
        return legacySlot / Math.max(1, layoutColumns);
    }

    void save(BackpackWindow window) {
        String id = window.id();
        values.setProperty(key(id, "open"), Boolean.toString(window.isOpen()));
        values.setProperty(key(id, "minimized"), Boolean.toString(window.isMinimized()));
        values.setProperty(key(id, "x"), Integer.toString(window.x()));
        values.setProperty(key(id, "y"), Integer.toString(window.y()));
        values.setProperty(key(id, "z"), Integer.toString(window.zIndex()));
        values.setProperty(key(id, "columns"), Integer.toString(window.visibleColumns()));
        values.setProperty(key(id, "rows"), Integer.toString(window.visibleRows()));
        values.setProperty(key(id, "layoutColumns"), Integer.toString(window.layoutColumns()));
        values.setProperty(key(id, "scrollColumn"), Integer.toString(window.scrollColumn()));
        values.setProperty(key(id, "scrollRow"), Integer.toString(window.scrollRow()));
        flush();
    }

    private int readInt(String key, int fallback) {
        try {
            return Integer.parseInt(values.getProperty(key, Integer.toString(fallback)));
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static String key(String id, String field) {
        return id + "." + field;
    }

    private void load() {
        if (!Files.isRegularFile(FILE)) {
            return;
        }
        try (InputStream input = Files.newInputStream(FILE)) {
            values.load(input);
        } catch (IOException exception) {
            WBackpacks.LOGGER.warn("Could not load backpack window state", exception);
        }
    }

    private void flush() {
        try {
            Files.createDirectories(FILE.getParent());
            try (OutputStream output = Files.newOutputStream(FILE)) {
                values.store(output, "WBackpacks client window state");
            }
        } catch (IOException exception) {
            WBackpacks.LOGGER.warn("Could not save backpack window state", exception);
        }
    }
}
