package com.glucocontext.app;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.util.Base64;

import com.getcapacitor.JSObject;
import com.getcapacitor.JSArray;
import android.graphics.Rect;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

@CapacitorPlugin(name = "AapsOcr")
public class AapsOcrPlugin extends Plugin {
    @PluginMethod
    public void recognizeText(PluginCall call) {
        try {
            String dataUrl = call.getString("dataUrl", "");
            String kind = call.getString("kind", "unknown");
            if (dataUrl == null || dataUrl.trim().length() == 0) {
                call.reject("Imagem vazia para OCR.");
                return;
            }
            int comma = dataUrl.indexOf(',');
            String base64 = comma >= 0 ? dataUrl.substring(comma + 1) : dataUrl;
            byte[] bytes = Base64.decode(base64, Base64.DEFAULT);
            Bitmap bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.length);
            if (bitmap == null) {
                call.reject("Não foi possível decodificar a imagem para OCR.");
                return;
            }
            InputImage image = InputImage.fromBitmap(bitmap, 0);
            TextRecognizer recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
            recognizer.process(image)
                .addOnSuccessListener(result -> {
                    JSObject ret = new JSObject();
                    ret.put("ok", true);
                    ret.put("kind", kind);
                    ret.put("text", result.getText());
                    ret.put("blocks", result.getTextBlocks().size());
                    JSArray elements = new JSArray();
                    for (Text.TextBlock block : result.getTextBlocks()) {
                        for (Text.Line line : block.getLines()) {
                            for (Text.Element el : line.getElements()) {
                                Rect r = el.getBoundingBox();
                                JSObject item = new JSObject();
                                item.put("text", el.getText());
                                if (r != null) {
                                    item.put("left", r.left);
                                    item.put("top", r.top);
                                    item.put("right", r.right);
                                    item.put("bottom", r.bottom);
                                    item.put("width", Math.max(0, r.width()));
                                    item.put("height", Math.max(0, r.height()));
                                    item.put("cx", r.centerX());
                                    item.put("cy", r.centerY());
                                }
                                elements.put(item);
                            }
                        }
                    }
                    ret.put("elements", elements);
                    call.resolve(ret);
                })
                .addOnFailureListener(e -> call.reject("OCR falhou: " + e.getMessage()));
        } catch (Exception e) {
            call.reject("OCR falhou: " + e.getMessage());
        }
    }
}
