package com.logdabordo.app;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
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
    private WebView webView;
    private String pendingBackup;

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
    }

    public class AndroidBridge {
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

        @JavascriptInterface public void pickBackup() {
            runOnUiThread(() -> {
                Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT);
                intent.addCategory(Intent.CATEGORY_OPENABLE);
                intent.setType("application/json");
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
                    Toast.makeText(this, "Backup salvato", Toast.LENGTH_SHORT).show();
                }
            } catch (IOException e) {
                Toast.makeText(this, "Errore salvataggio backup", Toast.LENGTH_LONG).show();
            } finally { pendingBackup = null; }
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
