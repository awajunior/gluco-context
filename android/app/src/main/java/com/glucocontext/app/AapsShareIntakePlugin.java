package com.glucocontext.app;

import android.content.Intent;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.database.Cursor;
import android.util.Base64;
import android.webkit.MimeTypeMap;

import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;

@CapacitorPlugin(name = "AapsShareIntake")
public class AapsShareIntakePlugin extends Plugin {
    private static final int MAX_BYTES = 18 * 1024 * 1024;

    @PluginMethod
    public void getPendingSharedImage(PluginCall call) {
        JSObject ret = new JSObject();
        try {
            Intent intent = getActivity() != null ? getActivity().getIntent() : null;
            if (intent == null || !Intent.ACTION_SEND.equals(intent.getAction())) {
                ret.put("hasImage", false);
                call.resolve(ret);
                return;
            }
            String type = intent.getType();
            if (type == null || !type.startsWith("image/")) {
                ret.put("hasImage", false);
                call.resolve(ret);
                return;
            }
            Uri uri = intent.getParcelableExtra(Intent.EXTRA_STREAM);
            if (uri == null) {
                ret.put("hasImage", false);
                call.resolve(ret);
                return;
            }
            byte[] bytes = readUriBytes(uri);
            String mime = type;
            if (mime == null || mime.trim().isEmpty()) mime = getActivity().getContentResolver().getType(uri);
            if (mime == null || mime.trim().isEmpty()) mime = "image/jpeg";
            String base64 = Base64.encodeToString(bytes, Base64.NO_WRAP);
            String name = queryDisplayName(uri);
            ret.put("hasImage", true);
            ret.put("id", String.valueOf(uri.toString().hashCode()) + "-" + bytes.length);
            ret.put("uri", uri.toString());
            ret.put("name", name != null ? name : "print-compartilhado.jpg");
            ret.put("mime", mime);
            ret.put("size", bytes.length);
            ret.put("dataUrl", "data:" + mime + ";base64," + base64);
            ret.put("suggestedKind", "unknown");
            call.resolve(ret);
        } catch (Exception e) {
            ret.put("hasImage", false);
            ret.put("error", e.getMessage());
            call.resolve(ret);
        }
    }

    @PluginMethod
    public void clearPendingSharedImage(PluginCall call) {
        try {
            if (getActivity() != null) {
                Intent clean = new Intent(getActivity(), MainActivity.class);
                clean.setAction(Intent.ACTION_MAIN);
                getActivity().setIntent(clean);
            }
        } catch (Exception ignored) {}
        JSObject ret = new JSObject();
        ret.put("ok", true);
        call.resolve(ret);
    }

    private byte[] readUriBytes(Uri uri) throws Exception {
        InputStream in = getActivity().getContentResolver().openInputStream(uri);
        if (in == null) throw new Exception("Não foi possível abrir a imagem compartilhada.");
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            int total = 0;
            while ((n = in.read(buf)) != -1) {
                total += n;
                if (total > MAX_BYTES) throw new Exception("Imagem compartilhada muito grande para pré-visualização.");
                out.write(buf, 0, n);
            }
            return out.toByteArray();
        } finally {
            try { in.close(); } catch (Exception ignored) {}
        }
    }

    private String queryDisplayName(Uri uri) {
        try {
            Cursor cursor = getActivity().getContentResolver().query(uri, null, null, null, null);
            if (cursor != null) {
                try {
                    int idx = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                    if (idx >= 0 && cursor.moveToFirst()) return cursor.getString(idx);
                } finally {
                    cursor.close();
                }
            }
        } catch (Exception ignored) {}
        String last = uri.getLastPathSegment();
        return last != null ? last : null;
    }
}
