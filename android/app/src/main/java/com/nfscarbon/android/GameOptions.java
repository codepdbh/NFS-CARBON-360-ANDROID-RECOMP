package com.nfscarbon.android;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.List;

/** Carbon's launcher options: SDK and Xenos settings, and the native renderer. */
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
    static final Option LANGUAGE = new Option("language", T.t("Idioma del juego"), "carbon_text_language", "1",
        new String[]{"1", "5", "4", "3", "6"},
        new String[]{T.t("Inglés"), "Español", "Français", "Deutsch", "Italiano"});
    // The native renderer of NFSMW Android Evolved (docs/renderizador-nativo.md) instead of Xenos emulation.
    static final Option RENDERER = new Option("renderer", T.t("Renderizador"), "nfsmw_renderizador", "nativo",
        new String[]{"nativo", "xenos"},
        new String[]{T.t("Nativo · experimental, más rápido"), T.t("Xenos · emulación")});
    // Most Wanted's measured shortcuts in the native renderer, still to be validated in Carbon.
    static final Option NATIVE_SHORTCUTS = new Option("native_shortcuts", T.t("Atajos del renderizador (MW)"), null,
        "off", new String[]{"off", "on"}, new String[]{T.t("Desactivados · más fiel"), T.t("Activados · experimental")});
    static final String[] MW_SHORTCUT_CVARS = {
        "nfsmw_sombras_sin_vegetacion", "nfsmw_nativo_dedupe_vertices", "nfsmw_nativo_resolver_sin_copia",
        "nfsmw_nativo_intercambiar_sin_borrado", "nfsmw_nativo_saltar_borrados_repetidos",
        "nfsmw_nativo_z_temprana", "nfsmw_nativo_cielo_aplazado", "nfsmw_nativo_sin_ps_sin_color",
        "nfsmw_nativo_ps_solo_alfa", "nfsmw_nativo_frontal_perezoso", "nfsmw_nativo_profundidad_perezosa",
        "nfsmw_nativo_pase_area_util", "nfsmw_nativo_restaurar_area_util",
        "nfsmw_nativo_cache_texturas_entre_fotogramas", "nfsmw_nativo_sin_desenfoque", "nfsmw_nativo_pcf_barato",
        "nfsmw_nativo_sombra_minimo", "nfsmw_nativo_saltar_invisibles", "nfsmw_nativo_resolver_contenido_valido",
    };
    // Voices, race intros and videos: the console language. The PAL English disc only has them in English.
    static final Option VOICES = new Option("voices", T.t("Idioma de las voces"), null, "english",
        new String[]{"english", "text"}, new String[]{T.t("Inglés · copias PAL inglesas"), T.t("Igual que los textos · copias dobladas")});
    static final Option UI_LANGUAGE = new Option(T.KEY, T.t("Idioma de la aplicación"), null, "system",
        new String[]{"system", "es", "en"}, new String[]{T.t("Del sistema"), "Español", "English"});
    static final Option[] ALL = {
        UI_LANGUAGE,
        RENDERER,
        NATIVE_SHORTCUTS,
        LANGUAGE,
        VOICES,
        new Option("resolution", T.t("Resolución interna"), "carbon_resolution", "1024x576",
            new String[]{"640x360", "1024x576", "1280x720", "1600x900", "1920x1080"},
            new String[]{T.t("640×360 · rendimiento"), T.t("1024×576 · equilibrado"), T.t("1280×720 · calidad"),
                T.t("1600×900 · solo renderizador nativo"), T.t("1920×1080 · solo renderizador nativo")}),
        new Option("fps", T.t("Ritmo objetivo (FPS)"), null, "60",
            new String[]{"30", "60", "90", "120", "unlimited"},
            new String[]{"30 FPS", "60 FPS", "90 FPS · experimental", "120 FPS · experimental", T.t("Sin límite · experimental")}),
        new Option("single_pass", T.t("Antialiasing de la escena"), "carbon_single_pass", "true",
            new String[]{"true", "false"}, new String[]{T.t("Desactivado · rendimiento"), T.t("Original · solo Xenos, más carga")}),
        // SDL pump frames queued ahead (5.3 ms each): more of them ride over hitches without audio cuts, at the cost
        // of latency.
        new Option("audio_buffer", T.t("Búfer de audio"), "audio_sdl_bomba_cola", "12",
            new String[]{"12", "20"}, new String[]{"Normal · 64 ms", T.t("Grande · 107 ms, si hay cortes")}),
        new Option("vsync", T.t("Sincronización vertical"), "vsync", "true",
            new String[]{"true", "false"}, new String[]{T.t("Activada"), T.t("Desactivada")}),
        new Option("async", T.t("Compilación de shaders"), "async_shader_compilation", "true",
            new String[]{"false", "true"}, new String[]{T.t("Completa · más pausas"), T.t("En segundo plano")}),
        new Option("workers", T.t("Trabajadores de shaders"), "vulkan_pipeline_creation_threads", "2",
            new String[]{"1", "2", "4", "-1"}, new String[]{"1", T.t("2 · recomendado"), "4", T.t("Automático")}),
        new Option("cpu", T.t("CPU del juego"), "carbon_fast_cores", "true",
            new String[]{"true", "false"}, new String[]{T.t("Preferir núcleos rápidos"), T.t("Sistema")}),
        new Option("fps_overlay", T.t("Contador de FPS"), null, "true",
            new String[]{"true", "false"}, new String[]{"Visible", T.t("Oculto")}),
        new Option("diagnostics", T.t("Registros"), null, "normal",
            new String[]{"normal", "detailed"}, new String[]{T.t("Solo avisos y errores"), T.t("Detallados + tiempos")}),
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
        // Classic render passes: on Adreno every vkCmdBeginRendering made the driver calloc (and later munmap)
        // a large block; with dynamic rendering that was ~40 % of the GPU command thread (simpleperf, S25).
        args.add("--vulkan_dynamic_rendering=false");
        args.add("--vulkan_require_geometry_shader=false");
        args.add("--vulkan_require_fill_mode_non_solid=false");
        args.add("--headless=true");
        boolean englishVoices = !"text".equals(get(context, VOICES.key));
        args.add("--user_language=" + (englishVoices ? "1" : get(context, LANGUAGE.key)));
        // The speech banks follow the text language: with English voices they are opened as _en (carbon_graphics.cpp).
        if (englishVoices && !"1".equals(get(context, LANGUAGE.key))) args.add("--carbon_sound_english=true");
        if ("nativo".equals(get(context, RENDERER.key))) {
            // Most Wanted's shortcuts that need its D3D layout: Carbon's VS objects keep several patched copies
            // (no IM_LOAD without memcmp) and its game-side vegetation filter reads MW's device mirror.
            args.add("--nfsmw_nativo_im_load_sin_memcmp=false");
            args.add("--nfsmw_d3d_vegetacion_juego=false");
            args.add("--nfsmw_nativo_sombra_d3d=false");
            if (!"on".equals(get(context, NATIVE_SHORTCUTS.key))) {
                for (String cvar : MW_SHORTCUT_CVARS) args.add("--" + cvar + "=false");
            }
        }
        // The original antialiasing splits the scene in EDRAM tiles with MSAA, which only Xenos reproduces: with the
        // native renderer it covered half the screen in menus and cutscenes. The native renderer always draws one pass.
        boolean singlePass = "true".equals(get(context, "single_pass")) || "nativo".equals(get(context, RENDERER.key));
        String size = singlePass ? get(context, "resolution") : "1280x720";
        // Xenos keeps the scene in the 10 MB of emulated EDRAM: 720p at most.
        if (!"nativo".equals(get(context, RENDERER.key)) && (size.equals("1600x900") || size.equals("1920x1080"))) {
            size = "1280x720";
        }
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
        // The native renderer is experimental: its [nativo] reports are info lines.
        boolean nativeRenderer = "nativo".equals(get(context, RENDERER.key));
        args.add("--log_level=" + (detailed || nativeRenderer ? "info" : "warn"));
        args.add("--carbon_perf_csv=" + detailed);
        for (Option option : ALL) if (option.cvar != null) {
            String value = "resolution".equals(option.key) ? size
                    : "single_pass".equals(option.key) ? Boolean.toString(singlePass) : get(context, option.key);
            if ("vsync".equals(option.key) && "unlimited".equals(fps)) value = "false";
            args.add("--" + option.cvar + "=" + value);
        }
        GpuDrivers.arguments(context, args);
        return args;
    }
}
