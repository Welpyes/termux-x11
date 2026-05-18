package com.termux.x11.utils;

import android.app.UiModeManager;
import android.content.Context;
import android.content.res.Configuration;
import android.hardware.display.DisplayManager;
import android.os.Build;
import android.view.Display;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class DesktopModeOutputHelper {
    private DesktopModeOutputHelper() {}

    public static final class ResolutionPreset {
        public final int width;
        public final int height;
        public final String label;

        ResolutionPreset(int width, int height, String label) {
            this.width = width;
            this.height = height;
            this.label = label;
        }

        public String value() {
            return width + "x" + height;
        }

        public String title() {
            return width + " × " + height + "  " + label;
        }

        public int area() {
            return width * height;
        }
    }

    private static final ResolutionPreset[] PRESETS = new ResolutionPreset[] {
        new ResolutionPreset(1600, 900, "HD+"),
        new ResolutionPreset(1920, 1080, "FHD"),
        new ResolutionPreset(2560, 1440, "WQHD/QHD"),
        new ResolutionPreset(1920, 1200, "WUXGA"),
        new ResolutionPreset(2560, 1600, "WQXGA"),
        new ResolutionPreset(2560, 1080, "UWFHD"),
        new ResolutionPreset(3440, 1440, "UWQHD"),
        new ResolutionPreset(3840, 2160, "UHD/4K"),
    };

    public static boolean isDesktopMode(Context context) {
        try {
            UiModeManager uiModeManager =
                (UiModeManager) context.getSystemService(Context.UI_MODE_SERVICE);

            if (uiModeManager != null &&
                uiModeManager.getCurrentModeType() == Configuration.UI_MODE_TYPE_DESK) {
                return true;
            }
        } catch (Throwable ignored) {
        }

        // Samsung DeX fallback.
        try {
            Configuration config = context.getResources().getConfiguration();
            Class<?> cls = config.getClass();
            int enabled = cls.getField("SEM_DESKTOP_MODE_ENABLED").getInt(cls);
            int current = cls.getField("semDesktopModeEnabled").getInt(config);
            return current == enabled;
        } catch (Throwable ignored) {
        }

        /*
         * Fallback for Samsung DeX / Android Desktop Mode variants where
         * UI_MODE_TYPE_DESK or Samsung semDesktopModeEnabled is not reliable.
         *
         * If Termux:X11 is running on, or can see, a non-default external display,
         * treat it as Desktop Mode for the separate output profile.
         */
        return hasDesktopLikeExternalDisplay(context);
    }


    private static boolean hasDesktopLikeExternalDisplay(Context context) {
        return findDesktopDisplay(context) != null;
    }

    public static Display findDesktopDisplay(Context context) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                Display current = context.getDisplay();
                if (current != null && current.getDisplayId() != Display.DEFAULT_DISPLAY) {
                    return current;
                }
            }
        } catch (Throwable ignored) {
        }

        DisplayManager dm =
            (DisplayManager) context.getSystemService(Context.DISPLAY_SERVICE);
        if (dm == null) return null;

        try {
            Display[] presentation =
                dm.getDisplays(DisplayManager.DISPLAY_CATEGORY_PRESENTATION);
            if (presentation != null && presentation.length > 0) {
                return presentation[0];
            }
        } catch (Throwable ignored) {
        }

        try {
            for (Display display : dm.getDisplays()) {
                if (display != null && display.getDisplayId() != Display.DEFAULT_DISPLAY) {
                    return display;
                }
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private static String normalizeResolution(int width, int height) {
        int w = Math.max(width, height);
        int h = Math.min(width, height);
        return w + "x" + h;
    }

    public static Set<String> supportedResolutionValues(Context context) {
        LinkedHashSet<String> values = new LinkedHashSet<>();
        Display display = findDesktopDisplay(context);
        if (display == null) return values;

        try {
            for (Display.Mode mode : display.getSupportedModes()) {
                if (mode == null) continue;
                values.add(normalizeResolution(
                    mode.getPhysicalWidth(),
                    mode.getPhysicalHeight()
                ));
            }
        } catch (Throwable ignored) {
        }

        return values;
    }

    public static List<ResolutionPreset> supportedPresets(Context context) {
        Set<String> supported = supportedResolutionValues(context);
        ArrayList<ResolutionPreset> result = new ArrayList<>();

        if (supported.isEmpty()) {
            return result;
        }

        for (ResolutionPreset preset : PRESETS) {
            if (supported.contains(preset.value())) {
                result.add(preset);
            }
        }

        return result;
    }

    public static int[] parseResolution(String value) {
        if (value == null) return null;

        try {
            String[] parts = value.trim().toLowerCase().split("x");
            if (parts.length != 2) return null;
            int width = Integer.parseInt(parts[0].trim());
            int height = Integer.parseInt(parts[1].trim());
            if (width <= 0 || height <= 0) return null;
            return new int[] { width, height };
        } catch (Throwable ignored) {
            return null;
        }
    }

    public static String resolveDesktopModeResolutionString(
        Context context,
        boolean desktopModeOutputEnabled,
        String savedValue,
        String normalFallback
    ) {
        if (!desktopModeOutputEnabled || !isDesktopMode(context)) {
            return null;
        }

        List<ResolutionPreset> presets = supportedPresets(context);

        if (presets.isEmpty()) {
            return normalFallback;
        }

        if (savedValue != null) {
            for (ResolutionPreset preset : presets) {
                if (preset.value().equals(savedValue)) {
                    return savedValue;
                }
            }
        }

        for (ResolutionPreset preset : presets) {
            if ("1920x1080".equals(preset.value())) {
                return preset.value();
            }
        }

        ResolutionPreset best = presets.get(0);
        for (ResolutionPreset preset : presets) {
            if (preset.area() > best.area()) {
                best = preset;
            }
        }

        return best.value();
    }

    private static int parseScalePercent(String value) {
        try {
            int scale = Integer.parseInt(value);
            if (scale < 100) return 100;
            if (scale > 300) return 300;
            return scale;
        } catch (Throwable ignored) {
            return 100;
        }
    }

    public static int resolveX11Dpi(
        Context context,
        boolean desktopModeOutputEnabled,
        String normalScalePercent,
        String desktopModeScalePercent
    ) {
        String value = normalScalePercent;

        if (desktopModeOutputEnabled && isDesktopMode(context)) {
            value = desktopModeScalePercent;
        }

        int scale = parseScalePercent(value);
        return 96 * scale / 100;
    }
}
