package com.logdabordo.app;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;

public class MainActivity extends Activity {
    private static final int CREATE_BACKUP = 1001;
    private static final int OPEN_BACKUP = 1002;
    private static final int CREATE_CSV = 1003;
    private static final String PREFS = "logdabordo_prefs";
    private static final String KEY_BACKUP_URI = "backup_uri";

    private WebView webView;
    private String pendingBackup;
    private String pendingCsv;

    @Override protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(true);
        settings.setAllowContentAccess(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);
        settings.setSupportZoom(false);
        settings.setTextZoom(100);

        webView.setWebViewClient(new WebViewClient());
        webView.setWebChromeClient(new WebChromeClient());
        webView.addJavascriptInterface(new AndroidBridge(), "Android");
        webView.loadUrl("file:///android_asset/index.html");

        enableImmersiveMode();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().getDecorView().setOnApplyWindowInsetsListener((v, insets) -> {
                enableImmersiveMode();
                return v.onApplyWindowInsets(insets);
            });
        }
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) enableImmersiveMode();
    }

    /** Nasconde barra di stato e tasti di navigazione, lasciando solo il contenuto dell'app. */
    private void enableImmersiveMode() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            getWindow().setDecorFitsSystemWindows(false);
            WindowInsetsController controller = getWindow().getInsetsController();
            if (controller != null) {
                controller.hide(WindowInsets.Type.statusBars() | WindowInsets.Type.navigationBars());
                controller.setSystemBarsBehavior(
                        WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        } else {
            View decor = getWindow().getDecorView();
            decor.setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }
    }

    private Uri getSavedBackupUri() {
        String s = getSharedPreferences(PREFS, MODE_PRIVATE).getString(KEY_BACKUP_URI, null);
        return s != null ? Uri.parse(s) : null;
    }

    private void setSavedBackupUri(Uri uri) {
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putString(KEY_BACKUP_URI, uri.toString()).apply();
    }

    public class AndroidBridge {

        @JavascriptInterface public boolean hasSavedLocation() {
            return getSavedBackupUri() != null;
        }

        @JavascriptInterface public String getSavedLocationName() {
            Uri uri = getSavedBackupUri();
            if (uri == null) return "";
            String name = uri.getLastPathSegment();
            return name != null ? name : uri.toString();
        }

        @JavascriptInterface public void forgetSavedLocation() {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_BACKUP_URI).apply();
        }

        /** Salva direttamente nella posizione già scelta in precedenza, senza chiedere di nuovo. */
        @JavascriptInterface public void quickSave(String json) {
            Uri uri = getSavedBackupUri();
            if (uri == null) { saveBackup(json); return; }
            boolean ok;
            try (OutputStream out = getContentResolver().openOutputStream(uri, "wt")) {
                if (out != null) {
                    out.write(json.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    ok = true;
                } else {
                    ok = false;
                }
            } catch (Exception e) {
                ok = false;
            }
            final boolean success = ok;
            runOnUiThread(() -> {
                if (success) {
                    webView.evaluateJavascript("onQuickSaveResult(true)", null);
                } else {
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit().remove(KEY_BACKUP_URI).apply();
                    webView.evaluateJavascript("onQuickSaveResult(false)", null);
                    saveBackup(json);
                }
            });
        }

        /** Chiede all'utente dove salvare (prima volta, o quando si cambia posizione). */
        @JavascriptInterface public void saveBackup(String json) {
            pendingBackup = json;
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
                intent.putExtra(Intent.EXTRA_TITLE, "LogDiBordo_backup.json");
                startActivityForResult(intent, CREATE_BACKUP);
            });
        }

        @JavascriptInterface public void saveCsv(String csv) {
            pendingCsv = csv;
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_CREATE_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("text/csv");
                intent.putExtra(Intent.EXTRA_TITLE, "LogDiBordo_export.csv");
                startActivityForResult(intent, CREATE_CSV);
            });
        }

        @JavascriptInterface public void pickBackup() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("*/*");
                intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{
                        "application/json", "text/csv", "text/comma-separated-values", "text/plain"
                });
                startActivityForResult(intent, OPEN_BACKUP);
            });
        }
    }

    @Override protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();

        if (requestCode == CREATE_BACKUP) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out != null && pendingBackup != null) {
                    out.write(pendingBackup.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    try {
                        getContentResolver().takePersistableUriPermission(uri,
                                Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION);
                    } catch (SecurityException se) {
                        // alcuni provider non supportano il permesso persistente: va bene lo stesso
                    }
                    setSavedBackupUri(uri);
                    String name = uri.getLastPathSegment();
                    String escapedName = org.json.JSONObject.quote(name != null ? name : "backup");
                    webView.evaluateJavascript("onBackupSaved(" + escapedName + ")", null);
                    Toast.makeText(this, "Backup salvato", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                Toast.makeText(this, "Errore salvataggio backup", Toast.LENGTH_LONG).show();
            } finally {
                pendingBackup = null;
            }

        } else if (requestCode == CREATE_CSV) {
            try (OutputStream out = getContentResolver().openOutputStream(uri)) {
                if (out != null && pendingCsv != null) {
                    out.write(pendingCsv.getBytes(StandardCharsets.UTF_8));
                    out.flush();
                    Toast.makeText(this, "CSV esportato", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                Toast.makeText(this, "Errore esportazione CSV", Toast.LENGTH_LONG).show();
            } finally {
                pendingCsv = null;
            }

        } else if (requestCode == OPEN_BACKUP) {
            try (InputStream in = getContentResolver().openInputStream(uri)) {
                if (in == null) throw new IOException("file non leggibile");
                java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream();
                byte[] chunk = new byte[8192];
                int n;
                while ((n = in.read(chunk)) != -1) buffer.write(chunk, 0, n);
                String json = buffer.toString(StandardCharsets.UTF_8.name());
                String escaped = org.json.JSONObject.quote(json);
                webView.evaluateJavascript("onNativeBackupSelected(" + escaped + ")", null);
            } catch (Exception e) {
                Toast.makeText(this, "Errore lettura backup", Toast.LENGTH_LONG).show();
            }
        }
    }

    @Override public void onBackPressed() {
        if (webView.canGoBack()) webView.goBack(); else super.onBackPressed();
    }
}
