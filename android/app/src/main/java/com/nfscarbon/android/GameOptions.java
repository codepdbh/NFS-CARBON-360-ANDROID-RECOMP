package com.nfscarbon.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/** Only SDK options supported by Carbon's Xenos renderer. */
final class GameOptions {
    static final class Option {
        final String key, title, cvar, defaultValue;
        final String[] values, labels;
        Option(String key, String title, String cvar, String defaultValue, String[] values, String[] labels) {
            this.key = key; this.title = title; this.cvar = cvar; this.defaultValue = defaultValue;
            this.values = values; this.labels = labels;
        }
        String label(String value) {
            for (int i = 0; i < values.length; i++) if (values[i].equals(value)) return labels[i];
            return value;
        }
    }
    // Xbox 360 language IDs, mapped to Carbon's startup resource IDs by its hook.
    static final Option LANGUAGE = new Option("language", "Idioma del juego", "user_language", "1",
        new String[]{"1", "5", "4", "3", "6"},
        new String[]{"Inglés", "Español", "Français", "Deutsch", "Italiano"});
    static final Option[] ALL = {
        LANGUAGE,
        new Option("resolution", "Resolución interna", "carbon_resolution", "1024x576",
            new String[]{"640x360", "1024x576", "1280x720"},
            new String[]{"640×360 · rendimiento", "1024×576 · equilibrado", "1280×720 · calidad"}),
        new Option("fps", "Ritmo objetivo (FPS)", null, "60",
            new String[]{"30", "60", "90", "120", "unlimited"},
            new String[]{"30 FPS", "60 FPS", "90 FPS · experimental", "120 FPS · experimental", "Sin límite · experimental"}),
        new Option("single_pass", "Antialiasing de la escena", "carbon_single_pass", "true",
            new String[]{"true", "false"}, new String[]{"Desactivado · rendimiento", "Original · más carga"}),
        new Option("vsync", "Sincronización vertical", "vsync", "true",
            new String[]{"true", "false"}, new String[]{"Activada", "Desactivada"}),
        new Option("async", "Compilación de shaders", "async_shader_compilation", "true",
            new String[]{"false", "true"}, new String[]{"Completa · más pausas", "En segundo plano"}),
        new Option("workers", "Trabajadores de shaders", "vulkan_pipeline_creation_threads", "2",
            new String[]{"1", "2", "4", "-1"}, new String[]{"1", "2 · recomendado", "4", "Automático"}),
        new Option("cpu", "CPU del juego", "carbon_fast_cores", "true",
            new String[]{"true", "false"}, new String[]{"Preferir núcleos rápidos", "Sistema"}),
        new Option("fps_overlay", "Contador de FPS", null, "true",
            new String[]{"true", "false"}, new String[]{"Visible", "Oculto"}),
        new Option("diagnostics", "Registros", null, "normal",
            new String[]{"normal", "detailed"}, new String[]{"Solo avisos y errores", "Detallados + tiempos"}),
    };
    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences("nfscarbon_game", Context.MODE_PRIVATE);
    }
    static String get(Context context, String key) {
        for (Option option : ALL) if (option.key.equals(key)) {
            String value = prefs(context).getString(key, option.defaultValue);
            for (String allowed : option.values) if (allowed.equals(value)) return value;
            return option.defaultValue;
        }
        throw new IllegalArgumentException(key);
    }
    static void set(Context context, String key, String value) {
        prefs(context).edit().putString(key, value).apply();
    }
    static List<String> arguments(Context context) {
        List<String> args = new ArrayList<>();
        args.add("--vulkan_native_shader_features=false");
        args.add("--render_target_path_vulkan=fbo");
        args.add("--vulkan_require_geometry_shader=false");
        args.add("--vulkan_require_fill_mode_non_solid=false");
        args.add("--headless=true");
        String size = "true".equals(get(context, "single_pass")) ? get(context, "resolution") : "1280x720";
        String[] dimensions = size.split("x");
        args.add("--resolution=");
        args.add("--video_mode_width=" + dimensions[0]);
        // The SDK's console video mode has a 480-line minimum. The scene/output
        // hooks independently apply the selected 360-line extent.
        args.add("--video_mode_height=" + Math.max(480, Integer.parseInt(dimensions[1])));
        String fps = get(context, "fps");
        args.add("--video_mode_refresh_rate=" + ("unlimited".equals(fps) ? "240" : fps));
        args.add("--vulkan_async_skip_incomplete_frames=true");
        boolean detailed = "detailed".equals(get(context, "diagnostics"));
        args.add("--log_level=" + (detailed ? "info" : "warn"));
        args.add("--carbon_perf_csv=" + detailed);
        for (Option option : ALL) if (option.cvar != null) {
            String value = get(context, option.key);
            if ("vsync".equals(option.key) && "unlimited".equals(fps)) value = "false";
            args.add("--" + option.cvar + "=" + value);
        }
        return args;
    }
}
