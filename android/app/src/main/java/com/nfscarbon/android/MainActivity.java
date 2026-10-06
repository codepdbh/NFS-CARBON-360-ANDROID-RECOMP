// Launcher styling adapted from codepdbh/nfsmw-android (5f581c6). SPDX-License-Identifier: GPL-3.0-only
package com.nfscarbon.android;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.ImageView;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
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
    private ShaderBuilder shaderBuilder;
    private static final int IMPORT = 10;
    private static final String EXPECTED_XEX = "b1e423914f5feb0871c8903aded4d86852d34be3b831b42f5c6473c7c7304221";
    private TextView status;
    private Button play, importButton;
    private boolean busy;
    private LinearLayout checks, optionsList;
    private Button accessButton;
    private static final int ACCENT = 0xFFFF4A43;

    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY |
                View.SYSTEM_UI_FLAG_HIDE_NAVIGATION | View.SYSTEM_UI_FLAG_FULLSCREEN);
        setContentView(buildScreen());
    }

    private View buildScreen() {
        LinearLayout columns = new LinearLayout(this);
        columns.setOrientation(LinearLayout.HORIZONTAL);
        columns.setPadding(dp(24), dp(16), dp(24), dp(16));
        columns.setBackground(new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[]{0xFF0A1019, 0xFF261316, 0xFF0A1019}));

        LinearLayout left = new LinearLayout(this);
        left.setOrientation(LinearLayout.VERTICAL);
        columns.addView(left, new LinearLayout.LayoutParams(0, -1, 1.05f));

        LinearLayout heading = new LinearLayout(this);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.mipmap.ic_launcher);
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        heading.addView(icon, new LinearLayout.LayoutParams(dp(78), dp(78)));
        LinearLayout name = new LinearLayout(this);
        name.setOrientation(LinearLayout.VERTICAL);
        name.setPadding(dp(14), 0, 0, 0);
        name.addView(label("NEED FOR SPEED", 12, 0xFFB8C1CE, true));
        name.addView(label("CARBON", 32, Color.WHITE, true));
        name.addView(label("ANDROID EVOLVED · XBOX 360", 11, ACCENT, true));
        heading.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        left.addView(heading);

        LinearLayout files = card();
        files.setPadding(dp(16), dp(10), dp(16), dp(10));
        ScrollView filesScroll = new ScrollView(this);
        filesScroll.setFillViewport(true);
        filesScroll.addView(files);
        LinearLayout.LayoutParams fileParams = new LinearLayout.LayoutParams(-1, 0, 1);
        fileParams.topMargin = dp(12);
        left.addView(filesScroll, fileParams);
        files.addView(label("ARCHIVOS DEL JUEGO", 12, ACCENT, true));
        status = label("Comprobando archivos…", 13, 0xFFCAD2DD, false);
        status.setPadding(0, dp(6), 0, dp(8));
        files.addView(status);
        checks = new LinearLayout(this);
        checks.setOrientation(LinearLayout.HORIZONTAL);
        files.addView(checks);

        play = action("JUGAR", true, this::play);
        LinearLayout.LayoutParams playParams = new LinearLayout.LayoutParams(-1, dp(56));
        playParams.topMargin = dp(12);
        left.addView(play, playParams);
        LinearLayout fileActions = new LinearLayout(this);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(-1, dp(48));
        actionParams.topMargin = dp(10);
        left.addView(fileActions, actionParams);
        accessButton = action("Permitir acceso", false, this::permission);
        fileActions.addView(accessButton, new LinearLayout.LayoutParams(0, -1, 1));
        importButton = action("Importar mi copia", false, this::importGame);
        LinearLayout.LayoutParams importParams = new LinearLayout.LayoutParams(0, -1, 1);
        importParams.leftMargin = dp(8);
        fileActions.addView(importButton, importParams);
        TextView note = label("Memoria interna/NFSCARBON", 11, 0xFF96A1B1, false);
        note.setPadding(dp(2), dp(6), 0, 0);
        left.addView(note);

        LinearLayout right = card();
        LinearLayout.LayoutParams rightParams = new LinearLayout.LayoutParams(0, -1, 1);
        rightParams.leftMargin = dp(22);
        columns.addView(right, rightParams);
        right.addView(label("AJUSTES DEL JUEGO", 12, ACCENT, true));
        TextView nextSession = label("Se aplican al pulsar Jugar. Con antialiasing Original se usa 1280×720. Los FPS altos y Sin límite son experimentales.", 12, 0xFF96A1B1, false);
        nextSession.setPadding(0, dp(3), 0, dp(5));
        right.addView(nextSession);
        ScrollView optionsScroll = new ScrollView(this);
        optionsList = new LinearLayout(this);
        optionsList.setOrientation(LinearLayout.VERTICAL);
        optionsScroll.addView(optionsList);
        right.addView(optionsScroll, new LinearLayout.LayoutParams(-1, 0, 1));
        TextView languageNote = label("Los idiomas y las voces dependen de tu copia.", 11, 0xFF96A1B1, false);
        languageNote.setPadding(0, dp(6), 0, dp(8));
        right.addView(languageNote);
        LinearLayout reports = new LinearLayout(this);
        reports.addView(action("Enviar crash o log", false, this::report),
                new LinearLayout.LayoutParams(0, dp(46), 1.5f));
        LinearLayout.LayoutParams githubParams = new LinearLayout.LayoutParams(0, dp(46), 1);
        githubParams.leftMargin = dp(8);
        reports.addView(action("GitHub", false, () -> startActivity(new Intent(Intent.ACTION_VIEW,
                Uri.parse(Diagnostics.ISSUES)))), githubParams);
        right.addView(reports);
        refreshOptions();
        return columns;
    }

    private void importGame() {
        if (busy) return;
        if (!storageAllowed()) { permission(); return; }
        new AlertDialog.Builder(this).setTitle("Importar Carbon")
            .setMessage("Se copiarán los archivos a NFSCARBON. Necesitas unos 6 GB libres. "
                    + "La copia existente quedará como respaldo y ocupará espacio adicional.")
            .setPositiveButton("Elegir carpeta", (dialog, which) -> startActivityForResult(
                new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), IMPORT))
            .setNegativeButton("Cancelar", null).show();
    }

    @Override public void onResume() {
        super.onResume();
        refreshOptions();
        refresh();
    }

    private LinearLayout card() {
        LinearLayout view = new LinearLayout(this);
        view.setOrientation(LinearLayout.VERTICAL);
        view.setPadding(dp(16), dp(13), dp(16), dp(13));
        GradientDrawable bg = new GradientDrawable();
        bg.setColor(0xD9101823);
        bg.setCornerRadius(dp(16));
        bg.setStroke(dp(1), 0x28FFFFFF);
        view.setBackground(bg);
        return view;
    }

    private TextView label(String message, int size, int color, boolean bold) {
        TextView view = new TextView(this);
        view.setText(message);
        view.setTextSize(size);
        view.setTextColor(color);
        if (bold) view.setTypeface(Typeface.DEFAULT_BOLD);
        return view;
    }

    private Button action(String title, boolean primary, Runnable action) {
        Button view = new Button(this);
        view.setText(title);
        view.setAllCaps(false);
        view.setTextColor(Color.WHITE);
        view.setTextSize(primary ? 20 : 13);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setStateListAnimator(null);
        GradientDrawable bg = primary
            ? new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, new int[]{0xFFFF6250, 0xFFD82B44})
            : new GradientDrawable();
        if (!primary) { bg.setColor(0x16FFFFFF); bg.setStroke(dp(1), 0x38FFFFFF); }
        bg.setCornerRadius(dp(12));
        view.setBackground(bg);
        view.setOnClickListener(ignored -> action.run());
        return view;
    }

    private void refreshOptions() {
        if (optionsList == null) return;
        optionsList.removeAllViews();
        for (GameOptions.Option option : GameOptions.ALL) {
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(dp(12), dp(10), dp(12), dp(10));
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(0x12FFFFFF); bg.setCornerRadius(dp(10));
            row.setBackground(bg);
            row.addView(label(option.title, 13, Color.WHITE, false), new LinearLayout.LayoutParams(0, -2, 1));
            TextView value = label(option.label(GameOptions.get(this, option.key)) + "  ›", 12, ACCENT, true);
            value.setGravity(Gravity.END);
            row.addView(value, new LinearLayout.LayoutParams(0, -2, 1.1f));
            row.setOnClickListener(ignored -> chooseOption(option));
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(6);
            optionsList.addView(row, params);
        }
    }

    private void chooseOption(GameOptions.Option option) {
        int selected = 0;
        for (int i = 0; i < option.values.length; i++)
            if (option.values[i].equals(GameOptions.get(this, option.key))) selected = i;
        new AlertDialog.Builder(this).setTitle(option.title)
            .setSingleChoiceItems(option.labels, selected, (choice, value) -> {
                GameOptions.set(this, option.key, option.values[value]);
                refreshOptions(); choice.dismiss();
            }).setNegativeButton("Cerrar", null).show();
    }

    private void showChecks() {
        checks.removeAllViews();
        File root = gameRoot();
        check("default.xex", new File(root, "default.xex").isFile());
        check("NFS", new File(root, "NFS").isDirectory());
        check("Movies", new File(root, "Movies").isDirectory() || new File(root, "MOVIES").isDirectory());
    }

    private void check(String name, boolean found) {
        TextView row = label((found ? "✓ " : "✗ ") + name, 12,
                found ? 0xFF85DDA6 : 0xFFFF8585, false);
        row.setPadding(0, dp(3), 0, dp(3));
        checks.addView(row, new LinearLayout.LayoutParams(0, -2, name.equals("default.xex") ? 2 : 1));
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
        play.setAlpha(ready ? 1f : .45f);
        accessButton.setVisibility(storageAllowed() ? View.GONE : View.VISIBLE);
        showChecks();
        status.setText(!storageAllowed() ? "Permite el acceso a tu copia."
                : ready ? "Tu copia está lista para iniciar."
                : "Importa tu copia extraída de Xbox 360.");
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
                    busy = false;
                    if ("nativo".equals(GameOptions.get(this, GameOptions.RENDERER.key))
                            && !ShaderBuilder.hasLibrary(shaderFolder())) {
                        buildShaders();
                        return;
                    }
                    refresh(); Diagnostics.recordLaunch(this);
                    startActivity(new Intent(this, GameActivity.class));
                });
            } catch (Exception error) { failed(error); }
        }, "CarbonEditionCheck").start();
    }
    // Where the native renderer looks first for its library: the executable folder (REX_APP_FOLDER in GameActivity).
    private File shaderFolder() { return new File(getFilesDir(), "nfscarbon/user"); }

    /** Builds nfscarbon_shaders.nfsp from the game files (a minute or two, only once), then starts the game. */
    private void buildShaders() {
        if (shaderBuilder != null) return;
        busy = true; play.setEnabled(false); play.setAlpha(.45f);
        status.setText("Generando los shaders del renderizador nativo (solo la primera vez)…");
        getWindow().addFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        shaderBuilder = new ShaderBuilder(this, gameRoot(), shaderFolder(), new ShaderBuilder.Listener() {
            @Override public void onProgress(float fraction, String text) {
                status.setText(Math.round(fraction * 100) + " % · " + text);
            }
            @Override public void onDone(File library) {
                shadersFinished();
                refresh(); Diagnostics.recordLaunch(MainActivity.this);
                startActivity(new Intent(MainActivity.this, GameActivity.class));
            }
            @Override public void onError(String message) {
                shadersFinished();
                refresh();
                status.setText("No se pudieron generar los shaders: " + message);
            }
        });
        shaderBuilder.start();
    }

    private void shadersFinished() {
        shaderBuilder = null; busy = false;
        getWindow().clearFlags(android.view.WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
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
