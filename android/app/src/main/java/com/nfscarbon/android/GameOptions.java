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
    // Xbox 360 language IDs, consumed by Carbon through XGetLanguage.
    static final Option LANGUAGE = new Option("language", "Idioma del juego", "user_language", "1",
        new String[]{"1", "5", "9", "4", "3", "6", "2", "7", "8"},
        new String[]{"Inglés", "Español", "Português", "Français", "Deutsch", "Italiano",
            "日本語", "한국어", "繁體中文"});
    static final Option[] ALL = {
        LANGUAGE,
        new Option("vsync", "Sincronización vertical", "vsync", "true",
            new String[]{"true", "false"}, new String[]{"Activada", "Desactivada"}),
        new Option("async", "Compilación de shaders", "async_shader_compilation", "false",
            new String[]{"false", "true"}, new String[]{"Completa · primera prueba", "Asíncrona · experimental"}),
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
        for (Option option : ALL) args.add("--" + option.cvar + "=" + get(context, option.key));
        return args;
    }
}
