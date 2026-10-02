package com.nfscarbon.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Button;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Spinner;
import android.widget.TextView;
import android.view.View;
import android.widget.Toast;
import androidx.core.content.FileProvider;
import androidx.documentfile.provider.DocumentFile;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;

/** Standalone Carbon launcher. Game assets stay in the player's shared folder. */
public final class MainActivity extends Activity {
    static final String GAME_FOLDER_NAME = "NFSCARBON";
    private static final int IMPORT = 10;
    private static final String EXPECTED_XEX = "b1e423914f5feb0871c8903aded4d86852d34be3b831b42f5c6473c7c7304221";
    private TextView status;
    private Button play, importButton;
    private boolean busy;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        ScrollView scroll = new ScrollView(this);
        scroll.setBackgroundColor(0xFF111820);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        int padding = dp(22);
        content.setPadding(padding, padding, padding, padding);
        scroll.addView(content);
        TextView title = text("NEED FOR SPEED CARBON", 26);
        title.setTextColor(0xFFFFA33A);
        content.addView(title);
        content.addView(text("Xbox 360 · prueba Android · controles táctiles", 15));
        content.addView(text("Coloca tu copia extraída de Xbox 360 en Memoria interna/" + GAME_FOLDER_NAME
                + ", con default.xex, NFS y Movies. También puedes importar una carpeta.", 14));
        status = text("Comprobando archivos…", 14);
        content.addView(status);
        content.addView(text("Idioma del juego", 16));
        Spinner language = new Spinner(this);
        ArrayAdapter<String> languages = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, GameOptions.LANGUAGE.labels);
        languages.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        language.setAdapter(languages);
        for (int i = 0; i < GameOptions.LANGUAGE.values.length; i++) {
            if (GameOptions.LANGUAGE.values[i].equals(GameOptions.get(this, "language")))
                language.setSelection(i);
        }
        language.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                GameOptions.set(MainActivity.this, "language", GameOptions.LANGUAGE.values[position]);
            }
            @Override public void onNothingSelected(AdapterView<?> parent) {}
        });
        content.addView(language);
        content.addView(text("Los textos y las voces disponibles dependen de los idiomas incluidos en tu copia.", 13));
        play = button(content, "Jugar", this::play);
        button(content, "Permitir acceso a la carpeta del juego", this::permission);
        importButton = button(content, "Importar mi copia de Xbox 360", () -> {
            if (!storageAllowed()) { permission(); return; }
            new AlertDialog.Builder(this).setTitle("Importar Carbon")
                .setMessage("Se copiarán los archivos seleccionados a " + GAME_FOLDER_NAME
                        + ". Necesitas unos 5 GB libres. La copia existente quedará como respaldo.")
                .setPositiveButton("Elegir carpeta", (dialog, which) ->
                    startActivityForResult(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), IMPORT))
                .setNegativeButton("Cancelar", null).show();
        });
        button(content, "Opciones", this::options);
        button(content, "Enviar crash o log", this::report);
        button(content, "Abrir issues en GitHub", () ->
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(Diagnostics.ISSUES))));
        content.addView(text("Primera prueba experimental. Los shaders se compilan durante el juego;"
                + " las primeras cargas pueden tardar. No se incluyen archivos del juego en el APK.", 13));
        setContentView(scroll);
    }
    @Override public void onResume() { super.onResume(); refresh(); }
    private TextView text(String message, int size) {
        TextView view = new TextView(this); view.setText(message); view.setTextSize(size);
        view.setTextColor(Color.WHITE); view.setPadding(0, dp(5), 0, dp(8)); return view;
    }
    private Button button(LinearLayout parent, String title, Runnable action) {
        Button view = new Button(this); view.setText(title); view.setAllCaps(false);
        view.setOnClickListener(ignored -> action.run()); parent.addView(view); return view;
    }
    private int dp(int value) { return Math.round(value * getResources().getDisplayMetrics().density); }
    private File gameRoot() { return new File(Environment.getExternalStorageDirectory(), GAME_FOLDER_NAME); }
    private boolean storageAllowed() {
        return Build.VERSION.SDK_INT >= 30 ? Environment.isExternalStorageManager()
                : checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }
    private void permission() {
        if (Build.VERSION.SDK_INT >= 30) {
            startActivity(new Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
        } else requestPermissions(new String[]{Manifest.permission.READ_EXTERNAL_STORAGE,
                Manifest.permission.WRITE_EXTERNAL_STORAGE}, 11);
    }
    private static boolean valid(File root) {
        return new File(root, "default.xex").isFile() && new File(root, "NFS").isDirectory()
                && (new File(root, "Movies").isDirectory() || new File(root, "MOVIES").isDirectory());
    }
    private void refresh() {
        if (status == null || busy) return;
        boolean ready = storageAllowed() && valid(gameRoot());
        play.setEnabled(ready);
        status.setText(!storageAllowed() ? "Permite el acceso a la carpeta para cargar tu copia."
                : ready ? "Archivos encontrados en " + gameRoot().getAbsolutePath()
                : "Faltan default.xex, NFS o Movies en " + gameRoot().getAbsolutePath());
    }
    private void play() {
        if (busy || !storageAllowed() || !valid(gameRoot())) { refresh(); return; }
        busy = true; play.setEnabled(false); status.setText("Comprobando la edición del ejecutable…");
        new Thread(() -> {
            try {
                MessageDigest digest = MessageDigest.getInstance("SHA-256");
                try (InputStream input = new FileInputStream(new File(gameRoot(), "default.xex"))) {
                    byte[] buffer = new byte[65536];
                    for (int count; (count = input.read(buffer)) != -1;) digest.update(buffer, 0, count);
                }
                StringBuilder hash = new StringBuilder();
                for (byte value : digest.digest()) hash.append(String.format(java.util.Locale.ROOT, "%02x", value & 255));
                if (!EXPECTED_XEX.equals(hash.toString())) throw new IOException(
                        "Esta edición de default.xex no coincide con la recompilada. Usa la copia Xbox 360 indicada en el README.");
                runOnUiThread(() -> {
                    busy = false; refresh(); Diagnostics.recordLaunch(this);
                    startActivity(new Intent(this, GameActivity.class));
                });
            } catch (Exception error) { failed(error); }
        }, "CarbonEditionCheck").start();
    }
    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != IMPORT || result != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData(); busy = true; play.setEnabled(false); importButton.setEnabled(false);
        status.setText("Copiando tu juego. Deja abierta esta pantalla…");
        new Thread(() -> {
            File staging = new File(Environment.getExternalStorageDirectory(), GAME_FOLDER_NAME + "-import-" + System.currentTimeMillis());
            try {
                DocumentFile source = DocumentFile.fromTreeUri(this, uri);
                if (source == null || !source.isDirectory()) throw new IOException("La carpeta no se puede abrir.");
                if (source.findFile("default.xex") == null || source.findFile("NFS") == null
                        || (source.findFile("Movies") == null && source.findFile("MOVIES") == null))
                    throw new IOException("Elige la carpeta del juego que contiene default.xex, NFS y Movies.");
                if (!staging.mkdirs()) throw new IOException("No se pudo crear la carpeta de importación.");
                copy(source, staging);
                if (!valid(staging)) throw new IOException("La carpeta debe contener default.xex, NFS y Movies.");
                File target = gameRoot();
                File backup = new File(target.getParentFile(), GAME_FOLDER_NAME + "-backup-" + System.currentTimeMillis());
                boolean backedUp = target.exists();
                if (backedUp && !target.renameTo(backup)) throw new IOException("No se pudo guardar la copia anterior.");
                if (!staging.renameTo(target)) {
                    if (backedUp) backup.renameTo(target);
                    throw new IOException("No se pudo instalar la carpeta copiada.");
                }
                runOnUiThread(() -> { busy = false; importButton.setEnabled(true); refresh(); });
            } catch (Exception error) { failed(error); }
        }, "CarbonImport").start();
    }
    private void copy(DocumentFile source, File target) throws IOException {
        for (DocumentFile child : source.listFiles()) {
            String name = child.getName();
            if (name == null || name.equals(".") || name.equals("..") || name.contains("/") || name.contains("\\"))
                throw new IOException("Nombre de archivo inválido.");
            File destination = new File(target, name);
            if (child.isDirectory()) {
                if (!destination.mkdir()) throw new IOException("No se pudo crear " + name);
                copy(child, destination);
            } else {
                try (InputStream input = getContentResolver().openInputStream(child.getUri());
                        FileOutputStream output = new FileOutputStream(destination)) {
                    if (input == null) throw new IOException("No se pudo leer " + name);
                    byte[] buffer = new byte[1024 * 1024];
                    for (int count; (count = input.read(buffer)) != -1;) output.write(buffer, 0, count);
                }
            }
        }
    }
    private void options() {
        String[] titles = new String[GameOptions.ALL.length];
        for (int i = 0; i < titles.length; i++) {
            GameOptions.Option option = GameOptions.ALL[i];
            titles[i] = option.title + ": " + option.label(GameOptions.get(this, option.key));
        }
        new AlertDialog.Builder(this).setTitle("Opciones para la próxima sesión").setItems(titles, (dialog, index) -> {
            GameOptions.Option option = GameOptions.ALL[index]; int selected = 0;
            for (int i = 0; i < option.values.length; i++) if (option.values[i].equals(GameOptions.get(this, option.key))) selected = i;
            new AlertDialog.Builder(this).setTitle(option.title).setSingleChoiceItems(option.labels, selected, (choice, value) -> {
                GameOptions.set(this, option.key, option.values[value]); choice.dismiss();
            }).setNegativeButton("Cerrar", null).show();
        }).setNegativeButton("Cerrar", null).show();
    }
    private void report() {
        if (busy) return;
        busy = true; status.setText("Preparando registro y diagnóstico…");
        new Thread(() -> {
            try {
                File report = Diagnostics.createReport(this);
                runOnUiThread(() -> {
                    busy = false; refresh();
                    Uri uri = FileProvider.getUriForFile(this, getPackageName() + ".reports", report);
                    Intent email = new Intent(Intent.ACTION_SEND).setType("application/zip")
                        .putExtra(Intent.EXTRA_EMAIL, new String[]{Diagnostics.EMAIL})
                        .putExtra(Intent.EXTRA_SUBJECT, "NFS Carbon 360: diagnóstico de " + Build.MODEL)
                        .putExtra(Intent.EXTRA_TEXT, Diagnostics.summary(this) + "\nDescribe aquí lo que ocurrió.\n")
                        .putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
                    email.setClipData(ClipData.newUri(getContentResolver(), "Diagnóstico Carbon", uri));
                    try { startActivity(Intent.createChooser(email, "Enviar registro")); }
                    catch (Exception error) { failed(error); }
                });
            } catch (Exception error) { failed(error); }
        }, "CarbonReport").start();
    }
    private void failed(Exception error) {
        runOnUiThread(() -> {
            busy = false; importButton.setEnabled(true); refresh(); status.setText(error.getMessage());
            Toast.makeText(this, error.getMessage(), Toast.LENGTH_LONG).show();
        });
    }
}
