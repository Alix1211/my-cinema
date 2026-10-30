package com.usecinema.game;

import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.UriPermission;
import android.database.Cursor;
import android.content.res.AssetFileDescriptor;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.DocumentsContract;
import android.util.Log;
import android.view.View;
import android.webkit.JavascriptInterface;
import android.webkit.RenderProcessGoneDetail;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import androidx.webkit.WebViewAssetLoader;
import java.io.FilterInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class MainActivity extends Activity {
    private static final String TAG = "UseCinema";
    // 자동 업데이트: 켤 때마다 인터넷의 최신 게임을 불러오고, 안 되면 앱 안에 든 게임으로 실행
    private static final String REMOTE = "https://alix1211.github.io/my-cinema/";
    private static final String LOCAL = "https://appassets.androidplatform.net/assets/index.html";
    private boolean usingLocal = false, remoteOk = false;
    private void loadLocal(String why) {
        if (usingLocal) return; usingLocal = true;
        Toast.makeText(this, "인터넷판을 못 받아 내장판으로 엽니다 (" + why + ")", Toast.LENGTH_LONG).show();
        if (game != null) game.loadUrl(LOCAL);
    }
    private static final String MEDIA = "https://appassets.androidplatform.net/media/";
    private static final int REQ_TREE = 601;

    private WebView game;
    private SharedPreferences prefs;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private long lastBack = 0;

    // 폴더 안 그림·영상 목록 (번호 → 파일 주소). 스캔할 때마다 통째로 교체
    private volatile List<Uri> mediaUris = new ArrayList<>();
    private volatile List<String> mediaTypes = new ArrayList<>();
    private volatile String listJson = "[]";
    private volatile boolean scanning = false;

    @Override public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        prefs = getSharedPreferences("cinema", MODE_PRIVATE);
        hideBars();
        getWindow().getDecorView().setOnSystemUiVisibilityChangeListener(v -> ui.postDelayed(this::hideBars, 1500));

        game = new WebView(this);
        game.setLayerType(View.LAYER_TYPE_HARDWARE, null);
        game.setBackgroundColor(Color.rgb(20, 10, 13));
        WebSettings s = game.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setSupportZoom(false);
        s.setBuiltInZoomControls(false);
        s.setMediaPlaybackRequiresUserGesture(false);

        final WebViewAssetLoader loader = new WebViewAssetLoader.Builder()
            .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this)).build();
        game.setWebViewClient(new WebViewClient() {
            @Override public WebResourceResponse shouldInterceptRequest(WebView v, WebResourceRequest r) {
                Uri u = r.getUrl();
                if (u != null && u.toString().startsWith(MEDIA)) return serveMedia(u, r.getRequestHeaders());
                return loader.shouldInterceptRequest(u);
            }
            @Override public void onReceivedError(WebView v, WebResourceRequest r, android.webkit.WebResourceError e) {
                final String why = "연결 오류 " + e.getErrorCode() + " " + e.getDescription();
                if (r.isForMainFrame() && !usingLocal) ui.post(() -> loadLocal(why));
            }
            @Override public void onReceivedHttpError(WebView v, WebResourceRequest r, WebResourceResponse e) {
                final String why = "서버 응답 " + e.getStatusCode();
                if (r.isForMainFrame() && !usingLocal) ui.post(() -> loadLocal(why));
            }
            @Override public void onPageFinished(WebView v, String url) {
                if (url != null && url.startsWith(REMOTE)) remoteOk = true;
            }
            @Override public boolean onRenderProcessGone(WebView v, RenderProcessGoneDetail d) {
                Toast.makeText(MainActivity.this, "화면 엔진이 멈춰 다시 시작합니다", Toast.LENGTH_LONG).show();
                recreate();
                return true;
            }
        });
        game.setWebChromeClient(new android.webkit.WebChromeClient() {
            @Override public void onProgressChanged(WebView v, int p) {   // 화면이 6할 이상 열리면 인터넷판으로 확정(그림 내려받느라 늦어도 내장판으로 안 바꿈)
                String u = v.getUrl();
                if (p >= 60 && !usingLocal && u != null && u.startsWith(REMOTE)) remoteOk = true;
            }
        });
        game.addJavascriptInterface(new Bridge(), "CinemaBridge");
        setContentView(game);
        game.loadUrl(REMOTE + "index.html?t=" + System.currentTimeMillis());   // 켤 때마다 새 주소로 받아 옛 화면이 남지 않게
        ui.postDelayed(() -> { if (!remoteOk) loadLocal("30초 안에 안 열림"); }, 30000);   // 30초 안에 못 받으면 앱 안의 게임으로
    }

    private void hideBars() {
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY | View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION);
    }

    @Override public void onWindowFocusChanged(boolean hasFocus) { super.onWindowFocusChanged(hasFocus); if (hasFocus) hideBars(); }

    /* ---------- 뒤로가기: 게임 안에서 먼저 처리, 처음 화면에서는 두 번 눌러 종료 ---------- */
    @Override public void onBackPressed() {
        if (game == null) { super.onBackPressed(); return; }
        game.evaluateJavascript("(function(){try{return window.__back?window.__back():false}catch(e){return false}})()", v -> {
            if ("true".equals(v)) return;
            long now = System.currentTimeMillis();
            if (now - lastBack < 2000) finish();
            else { lastBack = now; Toast.makeText(MainActivity.this, "한 번 더 누르면 종료합니다", Toast.LENGTH_SHORT).show(); }
        });
    }

    private void js(final String code) { if (game != null) game.post(() -> game.evaluateJavascript(code, null)); }
    private void msg(String m) { js("window.onCinemaMsg&&window.onCinemaMsg(" + JSONObject.quote(m) + ")"); }

    /* ---------- 그림 폴더 ---------- */
    private Uri treeUri() { String u = prefs.getString("tree", null); return u == null ? null : Uri.parse(u); }

    private boolean treeValid() {
        Uri t = treeUri(); if (t == null) return false;
        for (UriPermission p : getContentResolver().getPersistedUriPermissions()) if (p.getUri().equals(t) && p.isReadPermission()) return true;
        return false;
    }

    @Override protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_TREE) return;
        if (res == RESULT_OK && data != null && data.getData() != null) {
            Uri u = data.getData();
            try { getContentResolver().takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION); } catch (Exception e) { Log.w(TAG, "persist " + e); }
            prefs.edit().putString("tree", u.toString()).apply();
            scan();
        } else msg("폴더를 고르지 않았습니다.");
    }

    private String nameOf(Uri docUri) {
        try (Cursor c = getContentResolver().query(docUri, new String[]{DocumentsContract.Document.COLUMN_DISPLAY_NAME}, null, null, null)) {
            if (c != null && c.moveToFirst()) return c.getString(0);
        } catch (Exception e) { Log.w(TAG, "name " + e); }
        return "root";
    }

    private static String guessType(String name, String mime) {
        if (mime != null && (mime.startsWith("image/") || mime.startsWith("video/"))) return mime;
        String n = name == null ? "" : name.toLowerCase();
        if (n.endsWith(".gif")) return "image/gif";
        if (n.endsWith(".jpg") || n.endsWith(".jpeg")) return "image/jpeg";
        if (n.endsWith(".png")) return "image/png";
        if (n.endsWith(".webp")) return "image/webp";
        if (n.endsWith(".mp4") || n.endsWith(".m4v")) return "video/mp4";
        if (n.endsWith(".webm")) return "video/webm";
        if (n.endsWith(".mov")) return "video/quicktime";
        if (n.endsWith(".ts")) return "video/mp2t";
        return null;
    }

    private void walk(Uri tree, String docId, String path, ArrayList<Uri> uris, ArrayList<String> types, JSONArray out) throws Exception {
        Uri kids = DocumentsContract.buildChildDocumentsUriUsingTree(tree, docId);
        ArrayList<String[]> dirs = new ArrayList<>();
        try (Cursor c = getContentResolver().query(kids, new String[]{
            DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME, DocumentsContract.Document.COLUMN_MIME_TYPE}, null, null, null)) {
            while (c != null && c.moveToNext()) {
                String id = c.getString(0), name = c.getString(1), mime = c.getString(2);
                if (DocumentsContract.Document.MIME_TYPE_DIR.equals(mime)) { dirs.add(new String[]{id, name}); continue; }
                String t = guessType(name, mime);
                if (t == null) continue;
                int idx = uris.size();
                uris.add(DocumentsContract.buildDocumentUriUsingTree(tree, id));
                types.add(t);
                JSONObject o = new JSONObject();
                o.put("n", name); o.put("t", t); o.put("p", path + name); o.put("u", MEDIA + idx);
                out.put(o);
            }
        }
        for (String[] d : dirs) walk(tree, d[0], path + d[1] + "/", uris, types, out);
    }

    private void doScan() {
        if (scanning) return;
        scanning = true;
        msg("그림 폴더를 읽는 중…");
        new Thread(() -> {
            try {
                Uri tree = treeUri();
                if (tree == null || !treeValid()) { msg("그림 폴더를 다시 지정해 주세요."); return; }
                String rootId = DocumentsContract.getTreeDocumentId(tree);
                String rootName = nameOf(DocumentsContract.buildDocumentUriUsingTree(tree, rootId));
                ArrayList<Uri> uris = new ArrayList<>(); ArrayList<String> types = new ArrayList<>(); JSONArray out = new JSONArray();
                walk(tree, rootId, rootName + "/", uris, types, out);
                mediaUris = uris; mediaTypes = types; listJson = out.toString();
                js("window.onCinemaReady&&window.onCinemaReady()");
            } catch (Throwable e) {
                Log.w(TAG, "scan " + e);
                msg("폴더를 읽지 못했습니다. 폴더를 다시 지정해 주세요.");
            } finally { scanning = false; }
        }).start();
    }
    private void scan() { doScan(); }

    /* ---------- 폴더 안 파일을 게임 화면에 주소로 내어 줌 (몇 기가여도 통째로 읽지 않고 필요한 만큼만 흘려보냄) ---------- */
    private static final class Limited extends FilterInputStream {
        private long left;
        Limited(InputStream in, long n) { super(in); left = n; }
        @Override public int read() throws IOException { if (left <= 0) return -1; int b = super.read(); if (b >= 0) left--; return b; }
        @Override public int read(byte[] b, int o, int l) throws IOException {
            if (left <= 0) return -1;
            int n = super.read(b, o, (int) Math.min(l, left)); if (n > 0) left -= n; return n;
        }
    }

    private WebResourceResponse serveMedia(Uri u, Map<String, String> req) {
        try {
            int idx = Integer.parseInt(u.getLastPathSegment());
            List<Uri> uris = mediaUris; List<String> types = mediaTypes;
            if (idx < 0 || idx >= uris.size()) return notFound();
            Uri src = uris.get(idx); String mime = types.get(idx);
            AssetFileDescriptor afd = getContentResolver().openAssetFileDescriptor(src, "r");
            if (afd == null) return notFound();
            long len = afd.getLength();
            Map<String, String> h = new HashMap<>();
            h.put("Access-Control-Allow-Origin", "*");
            h.put("Accept-Ranges", "bytes");
            String range = null;
            if (req != null) { range = req.get("Range"); if (range == null) range = req.get("range"); }
            if (range != null && range.startsWith("bytes=") && len > 0) {
                String[] ab = range.substring(6).split("-", 2);
                long a = ab[0].isEmpty() ? 0 : Long.parseLong(ab[0].trim());
                long b = (ab.length < 2 || ab[1].trim().isEmpty()) ? len - 1 : Math.min(len - 1, Long.parseLong(ab[1].trim()));
                if (a > b || a >= len) { afd.close(); return notFound(); }
                InputStream in = afd.createInputStream();
                long skipped = 0; while (skipped < a) { long k = in.skip(a - skipped); if (k <= 0) break; skipped += k; }
                h.put("Content-Range", "bytes " + a + "-" + b + "/" + len);
                h.put("Content-Length", String.valueOf(b - a + 1));
                return new WebResourceResponse(mime, null, 206, "Partial Content", h, new Limited(in, b - a + 1));
            }
            if (len > 0) h.put("Content-Length", String.valueOf(len));
            return new WebResourceResponse(mime, null, 200, "OK", h, afd.createInputStream());
        } catch (Throwable e) {
            Log.w(TAG, "media " + e);
            return notFound();
        }
    }

    private WebResourceResponse notFound() {
        return new WebResourceResponse("text/plain", "utf-8", 404, "Not Found", new HashMap<String, String>(), new java.io.ByteArrayInputStream(new byte[0]));
    }

    /* ---------- 게임 → 앱 ---------- */
    private final class Bridge {
        @JavascriptInterface public boolean hasFolder() { return treeValid(); }
        @JavascriptInterface public void pickFolder() {
            runOnUiThread(() -> {
                Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                try { startActivityForResult(i, REQ_TREE); } catch (Exception e) { msg("폴더 선택 창을 열 수 없습니다."); }
            });
        }
        @JavascriptInterface public void scan() { MainActivity.this.scan(); }
        @JavascriptInterface public String getList() { return listJson; }
        // 저장: 게임을 인터넷판으로 켜든 앱 안의 판으로 켜든 같은 저장을 쓰도록 앱이 직접 보관
        @JavascriptInterface public String loadSave() { return prefs.getString("save", ""); }
        @JavascriptInterface public void saveSave(String json) {
            if (json != null && json.length() > 1 && json.length() < 8000000) prefs.edit().putString("save", json).apply();
        }
    }
}
