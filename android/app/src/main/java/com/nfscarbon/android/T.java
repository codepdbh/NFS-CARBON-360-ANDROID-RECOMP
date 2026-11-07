// Launcher text in English or Spanish. SPDX-License-Identifier: GPL-3.0-only
package com.nfscarbon.android;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** The launcher's texts are written in Spanish in the code; t() returns the English version when the app language
 *  (option "ui_language": system, es, en) resolves to English. Unknown texts are returned unchanged. */
final class T {
    static final String KEY = "ui_language";
    private static boolean english = true;
    private static final Map<String, String> EN = new HashMap<>();

    static void init(Context context) {
        String value = GameOptions.prefs(context).getString(KEY, "system");
        boolean systemSpanish = "es".equals(Locale.getDefault().getLanguage());
        english = "en".equals(value) || ("system".equals(value) && !systemSpanish);
    }

    static String t(String es) {
        if (!english) return es;
        String en = EN.get(es);
        return en != null ? en : es;
    }

    /** The option titles are built once, so a language change restarts the app. */
    static void restart(Activity activity) {
        Intent intent = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        activity.startActivity(intent);
        activity.finishAffinity();
        Runtime.getRuntime().exit(0);
    }

    private static void p(String es, String en) { EN.put(es, en); }
    static {
        // App language option
        p("Idioma de la aplicación", "App language");
        p("Del sistema", "System");
        // Diagnostics
        p("Driver seleccionado pendiente de probar", "Selected driver not yet tested");
        p("El controlador de ", "The driver of ");
        p("esta GPU", "this GPU");
        p(" no ofrece las funciones que necesita el renderizador nativo.", " does not offer the features the native renderer needs.");
        p("\n\nVulkan del dispositivo: ", "\n\nDevice Vulkan: ");
        p("no disponible", "not available");
        p("\n\nBajar la resolución no resuelve esta incompatibilidad. Puedes enviar el diagnóstico para ayudarnos a estudiar soporte.",
          "\n\nLowering the resolution does not fix this incompatibility. You can send the diagnostic report to help us study support.");
        p("\nTeléfono: ", "\nPhone: ");
        p("\nDriver seleccionado: ", "\nSelected driver: ");
        p("Sistema", "System");
        p("No se pudo crear el informe.", "Could not create the report.");
        p("Fecha: ", "Date: ");
        p("\nRAM total: ", "\nTotal RAM: ");
        p("\nRAM disponible: ", "\nAvailable RAM: ");
        p("\nÚltimo intento de iniciar: ", "\nLast launch attempt: ");
        p("\nArgumentos efectivos:\n", "\nEffective arguments:\n");
        p("\nDescribe qué ocurrió, cómo reproducirlo y la edición del juego.\n", "\nDescribe what happened, how to reproduce it and your game edition.\n");
        p("\n[... registro recortado: inicio y final ...]\n", "\n[... log trimmed: beginning and end ...]\n");
        p("Historial de cierres no disponible", "Exit history not available");
        // DriverArchive
        p("El ZIP contiene demasiados archivos.", "The ZIP contains too many files.");
        p("El ZIP contiene rutas no válidas o repetidas.", "The ZIP contains invalid or duplicate paths.");
        p("No se pudo crear la carpeta.", "Could not create the folder.");
        p("El paquete de driver es demasiado grande.", "The driver package is too large.");
        p("El driver debe ser una biblioteca Android ARM64 (.so), no de PC ni de 32 bits.", "The driver must be an Android ARM64 library (.so), not a PC or 32-bit one.");
        p("Enlace inesperado en el paquete.", "Unexpected link in the package.");
        p("No se pudo eliminar ", "Could not delete ");
        // GameActivity
        p("FPS · cargando…", "FPS · loading…");
        p("libmain no cargada", "libmain not loaded");
        p("Toca un control para elegirlo · arrástralo para moverlo · pellizca para cambiar su tamaño", "Tap a control to select it · drag to move it · pinch to resize it");
        p("Tamaño −", "Size −");
        p("Tamaño +", "Size +");
        p("Mostrar / ocultar", "Show / hide");
        p("Ajustes", "Settings");
        p("Restablecer", "Reset");
        p("Listo", "Done");
        p("Restablecer controles", "Reset controls");
        p("¿Volver a la posición y el tamaño originales de todos los controles?", "Restore the original position and size of all controls?");
        p("Cancelar", "Cancel");
        p("Imagen estirada a toda la pantalla", "Stretch image to the full screen");
        p("Vibrar al pulsar", "Vibrate on press");
        p("Ocultar los controles al usar un mando", "Hide controls when using a controller");
        p("Dirección inclinando el teléfono", "Steer by tilting the phone");
        p("Sensibilidad de la inclinación", "Tilt sensitivity");
        p("Opacidad de los controles", "Control opacity");
        p("Ajustes de los controles", "Control settings");
        p("Cerrar", "Close");
        p("No se pudo instalar ", "Could not install ");
        // GameOptions
        p("Idioma del juego", "Game language");
        p("Inglés", "English");
        p("Renderizador", "Renderer");
        p("Nativo · experimental, más rápido", "Native · experimental, faster");
        p("Xenos · emulación", "Xenos · emulation");
        p("Atajos del renderizador (MW)", "Renderer shortcuts (MW)");
        p("Desactivados · más fiel", "Off · more faithful");
        p("Activados · experimental", "On · experimental");
        p("Idioma de las voces", "Voice language");
        p("Inglés · copias PAL inglesas", "English · English PAL copies");
        p("Igual que los textos · copias dobladas", "Same as the text · dubbed copies");
        p("Resolución interna", "Internal resolution");
        p("640×360 · rendimiento", "640×360 · performance");
        p("1024×576 · equilibrado", "1024×576 · balanced");
        p("1280×720 · calidad", "1280×720 · quality");
        p("1600×900 · solo renderizador nativo", "1600×900 · native renderer only");
        p("1920×1080 · solo renderizador nativo", "1920×1080 · native renderer only");
        p("Ritmo objetivo (FPS)", "Target frame rate (FPS)");
        p("Sin límite · experimental", "Unlimited · experimental");
        p("Antialiasing de la escena", "Scene antialiasing");
        p("Desactivado · rendimiento", "Off · performance");
        p("Original · solo Xenos, más carga", "Original · Xenos only, heavier");
        p("Búfer de audio", "Audio buffer");
        p("Grande · 107 ms, si hay cortes", "Large · 107 ms, if audio stutters");
        p("Sincronización vertical", "Vertical sync");
        p("Activada", "On");
        p("Desactivada", "Off");
        p("Compilación de shaders", "Shader compilation");
        p("Completa · más pausas", "Full · more pauses");
        p("En segundo plano", "In the background");
        p("Trabajadores de shaders", "Shader workers");
        p("2 · recomendado", "2 · recommended");
        p("Automático", "Automatic");
        p("CPU del juego", "Game CPU");
        p("Preferir núcleos rápidos", "Prefer fast cores");
        p("Contador de FPS", "FPS counter");
        p("Oculto", "Hidden");
        p("Registros", "Logs");
        p("Solo avisos y errores", "Warnings and errors only");
        p("Detallados + tiempos", "Detailed + timings");
        // GpuDrivers
        p("Los drivers externos necesitan Android 9 o posterior.", "External drivers need Android 9 or later.");
        p("No se pudo crear la carpeta del driver.", "Could not create the driver folder.");
        p("No se pudo abrir el ZIP.", "Could not open the ZIP.");
        p("Falta meta.json en el ZIP del driver.", "meta.json is missing from the driver ZIP.");
        p("libraryName no es válido.", "libraryName is not valid.");
        p("Este driver necesita una versión más reciente de Android.", "This driver needs a newer Android version.");
        p("Nombre de driver no válido.", "Invalid driver name.");
        p("No se pudo instalar el driver.", "Could not install the driver.");
        p("No se pudo instalar el paquete.", "Could not install the package.");
        p("Hay varios meta.json; el paquete es ambiguo.", "There are several meta.json files; the package is ambiguous.");
        p("No se pudo proteger la biblioteca del driver.", "Could not protect the driver library.");
        p("Ruta de driver no válida.", "Invalid driver path.");
        // GpuProbeActivity
        p("Comprobando el driver Vulkan…", "Checking the Vulkan driver…");
        p("{\"probeError\":\"La prueba del driver excedió 20 segundos\",\"driverLoadFailed\":true}", "{\"probeError\":\"The driver test exceeded 20 seconds\",\"driverLoadFailed\":true}");
        p("{\"probeError\":\"No se pudo cargar la biblioteca de diagnóstico\",\"driverLoadFailed\":true}", "{\"probeError\":\"Could not load the diagnostics library\",\"driverLoadFailed\":true}");
        // MainActivity
        p("ARCHIVOS DEL JUEGO", "GAME FILES");
        p("Comprobando archivos…", "Checking files…");
        p("JUGAR", "PLAY");
        p("Permitir acceso", "Allow access");
        p("Memoria interna/NFSCARBON", "Internal storage/NFSCARBON");
        p("AJUSTES DEL JUEGO", "GAME SETTINGS");
        p("Se aplican al pulsar Jugar. Con antialiasing Original se usa 1280×720. Los FPS altos y Sin límite son experimentales.",
          "Applied when you press Play. With Original antialiasing 1280×720 is used. High FPS and Unlimited are experimental.");
        p("Los idiomas y las voces dependen de tu copia.", "Languages and voices depend on your copy.");
        p("Enviar crash o log", "Send crash or log");
        p("Importar Carbon", "Import Carbon");
        p("Importar mi copia", "Import my copy");
        p("Se copiarán los archivos a NFSCARBON. Necesitas unos 6 GB libres. ", "The files will be copied to NFSCARBON. You need about 6 GB free. ");
        p("La copia existente quedará como respaldo y ocupará espacio adicional.", "The existing copy is kept as a backup and takes extra space.");
        p("Elegir carpeta", "Choose folder");
        p("Aceptar", "OK");
        p("Actualizaciones", "Updates");
        p("Tienes la versión ", "You have version ");
        p(". No hay una versión estable más reciente en GitHub.", ". There is no newer stable version on GitHub.");
        p("Nueva versión disponible: ", "New version available: ");
        p("Puedes actualizar desde GitHub o seguir jugando con esta versión. Instala el APK ", "You can update from GitHub or keep playing this version. Install the APK ");
        p("encima del anterior para conservar tus partidas y ajustes.", "over the previous one to keep your saves and settings.");
        p("Actualizar", "Update");
        p("No se encontró una app para abrir GitHub.", "No app found to open GitHub.");
        p("Más tarde", "Later");
        p("Driver Vulkan", "Vulkan driver");
        p("Probar driver", "Test driver");
        p("Comprobar GPU y Vulkan", "Check GPU and Vulkan");
        p("Buscar actualizaciones", "Check for updates");
        p("Comprobando GitHub…", "Checking GitHub…");
        p("Versión ", "Version ");
        p("Driver del sistema", "System driver");
        p("Importar ZIP", "Import ZIP");
        p("Eliminar seleccionado", "Delete selected");
        p("Volver", "Back");
        p("\nConjuntos de descriptores: ", "\nDescriptor sets: ");
        p("Funciones del renderizador nativo disponibles.", "Native renderer features available.");
        p("Funciones faltantes: ", "Missing features: ");
        p("No se pudo probar el driver. ", "Could not test the driver. ");
        p("Error al cargar Vulkan.", "Error loading Vulkan.");
        p("Prueba del driver Vulkan", "Vulkan driver test");
        p("Usar sistema", "Use system");
        p("Diagnóstico no válido: ", "Invalid diagnostic: ");
        p("Permite el acceso a tu copia.", "Allow access to your copy.");
        p("Tu copia está lista para iniciar.", "Your copy is ready to start.");
        p("Importa tu copia extraída de Xbox 360.", "Import your extracted Xbox 360 copy.");
        p("Comprobando la edición del ejecutable…", "Checking the executable's edition…");
        p("Esta edición de default.xex no coincide con la recompilada. Usa la copia Xbox 360 indicada en el README.",
          "This edition of default.xex does not match the recompiled one. Use the Xbox 360 copy named in the README.");
        p("Generando los shaders del renderizador nativo (solo la primera vez)…", "Generating the native renderer's shaders (first time only)…");
        p("No se pudieron generar los shaders: ", "Could not generate the shaders: ");
        p("{\"probeError\":\"La prueba se cerró o fue cancelada. Puedes volver al driver del sistema.\",\"driverLoadFailed\":true}",
          "{\"probeError\":\"The test was closed or cancelled. You can go back to the system driver.\",\"driverLoadFailed\":true}");
        p("Importando driver Vulkan…", "Importing Vulkan driver…");
        p("Driver importado: ", "Driver imported: ");
        p(". Usa Probar driver para comprobarlo.", ". Use Test driver to check it.");
        p("Copiando tu juego. Deja abierta esta pantalla…", "Copying your game. Keep this screen open…");
        p("La carpeta no se puede abrir.", "The folder cannot be opened.");
        p("Elige la carpeta del juego que contiene default.xex, NFS y Movies.", "Choose the game folder that contains default.xex, NFS and Movies.");
        p("No se pudo crear la carpeta de importación.", "Could not create the import folder.");
        p("La carpeta debe contener default.xex, NFS y Movies.", "The folder must contain default.xex, NFS and Movies.");
        p("No se pudo guardar la copia anterior.", "Could not keep the previous copy.");
        p("No se pudo instalar la carpeta copiada.", "Could not install the copied folder.");
        p("Nombre de archivo inválido.", "Invalid file name.");
        p("No se pudo crear ", "Could not create ");
        p("No se pudo leer ", "Could not read ");
        p("Preparando registro y diagnóstico…", "Preparing log and diagnostics…");
        p("NFS Carbon 360: diagnóstico de ", "NFS Carbon 360: diagnostics from ");
        p("\nDescribe aquí lo que ocurrió.\n", "\nDescribe here what happened.\n");
        p("Diagnóstico Carbon", "Carbon diagnostics");
        p("Enviar registro", "Send log");
        // ReleaseUpdates
        p("No se pudo consultar GitHub. Comprueba tu conexión e inténtalo después.", "Could not reach GitHub. Check your connection and try again later.");
        p("GitHub no disponible", "GitHub not available");
        p("Respuesta demasiado grande", "Response too large");
        // ShaderBuilder
        p("el compilador de shaders se cerró (memoria insuficiente)", "the shader compiler closed (out of memory)");
        p("no se pudo guardar ", "could not save ");
        // TouchControlsView
        p("DIRECCION", "STEER");
        p("FRENO", "BRAKE");
        p("MANO", "E-BRAKE");
        p("CAMARA", "CAMERA");
        p("MIRAR", "LOOK BACK");
        p("OCULTO", "HIDDEN");
        p("INCLINAR", "TILT");
    }
}
