package com.glucocontext.app;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import java.util.Locale;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.net.Uri;
import android.os.Build;
import android.view.View;
import android.widget.RemoteViews;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class AapsSmartWidgetProvider extends AppWidgetProvider {
  public static final String PREFS = "aaps_smart_widget";
  public static final String ACTION_SYNC = "com.glucocontext.app.WIDGET_SYNC";
  public static final String ACTION_OPEN = "com.glucocontext.app.WIDGET_OPEN";

  @Override public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
    for (int id: ids) updateWidget(context, manager, id, false);
  }

  @Override public void onReceive(Context context, Intent intent) {
    super.onReceive(context, intent);
    String action = intent == null ? "" : intent.getAction();
    if (ACTION_SYNC.equals(action)) {
      markSyncing(context);
      updateAll(context);
      runSync(context.getApplicationContext());
    } else if (ACTION_OPEN.equals(action)) {
      Intent open = context.getPackageManager().getLaunchIntentForPackage(context.getPackageName());
      if (open != null) {
        open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(open);
      }
    }
  }

  public static void updateAll(Context context) {
    AppWidgetManager mgr = AppWidgetManager.getInstance(context);
    int[] ids = mgr.getAppWidgetIds(new ComponentName(context, AapsSmartWidgetProvider.class));
    for (int id: ids) updateWidget(context, mgr, id, false);
  }

  private static PendingIntent pending(Context ctx, String action, int requestCode) {
    Intent i = new Intent(ctx, AapsSmartWidgetProvider.class);
    i.setAction(action);
    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
    return PendingIntent.getBroadcast(ctx, requestCode, i, flags);
  }

  private static PendingIntent openPending(Context ctx, int requestCode) {
    Intent open = ctx.getPackageManager().getLaunchIntentForPackage(ctx.getPackageName());
    if (open == null) open = new Intent();
    open.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
    int flags = PendingIntent.FLAG_UPDATE_CURRENT;
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
    return PendingIntent.getActivity(ctx, requestCode, open, flags);
  }

  private static void updateWidget(Context ctx, AppWidgetManager mgr, int widgetId, boolean unused) {
    SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    RemoteViews v = new RemoteViews(ctx.getPackageName(), R.layout.aaps_smart_widget);
    String rawBg = p.getString("bg", "—");
    String bg = safeBgForDisplay(p, rawBg);
    String unit = p.getString("unit", "mg/dL");
    String trend = p.getString("trend", "");
    String delta = p.getString("delta", "");
    String iob = p.getString("iob", "—");
    String cgmAge = p.getString("cgmAge", "—");
    String status = p.getString("status", "Sem dado");
    String history = p.getString("historyText", "—");
    String pred = p.getString("prediction30", "");
    long updatedAt = p.getLong("updatedAt", 0L);
    boolean syncing = p.getBoolean("syncing", false);
    boolean cgmOk = p.getBoolean("cgmOk", false);
    String source = p.getString("source", "Widget");

    v.setTextViewText(R.id.widget_title, "Gluco Context");
    v.setTextViewText(R.id.widget_bg, bg);
    v.setTextViewText(R.id.widget_unit, unit);
    v.setTextViewText(R.id.widget_iob, iob);
    v.setTextViewText(R.id.widget_iob_label, "U IOB");
    v.setTextViewText(R.id.widget_trend, trend.length() > 0 ? trend : "→");
    String[] deltaParts = splitDelta(delta);
    v.setTextViewText(R.id.widget_delta, deltaParts[0]);
    v.setTextViewText(R.id.widget_delta_speed, deltaParts[1]);
    v.setTextViewText(R.id.widget_status, status);
    v.setTextViewText(R.id.widget_history, compactHistory(history));
    v.setTextViewText(R.id.widget_pred_label, "30 min · " + (pred.length() > 0 ? pred : "—"));
    v.setTextViewText(R.id.widget_age, cgmAge);
    v.setTextViewText(R.id.widget_updated, syncing ? (isEnglish() ? "Syncing..." : "Sincronizando...") : updatedLabel(updatedAt));
    v.setTextViewText(R.id.widget_sync_hint, syncing ? (isEnglish() ? "Please wait" : "Aguarde") : ("Sem internet".equals(status) ? (isEnglish() ? "Tap to retry" : "Toque para tentar novamente") : (isEnglish() ? "Tap to sync" : "Toque para sincronizar")));
    v.setTextColor(R.id.widget_bg, bgColor(bg));
    v.setTextViewText(R.id.widget_source, source);
    v.setImageViewBitmap(R.id.widget_chart, buildChart(p));
    v.setOnClickPendingIntent(R.id.widget_sync_area, pending(ctx, ACTION_SYNC, 101));
    v.setOnClickPendingIntent(R.id.widget_open_area, openPending(ctx, 102));
    v.setOnClickPendingIntent(R.id.widget_root, openPending(ctx, 103));
    mgr.updateAppWidget(widgetId, v);
  }

  private static boolean isEnglish() {
    String lang = Locale.getDefault().getLanguage();
    return lang != null && lang.startsWith("en");
  }

  private static String updatedLabel(long ts) {
    if (ts <= 0) return isEnglish() ? "No update yet" : "Sem atualização";
    long min = Math.max(0L, Math.round((System.currentTimeMillis() - ts) / 60000.0));
    if (min <= 0) return isEnglish() ? "Updated now" : "Atualizado agora";
    return (isEnglish() ? "Updated " : "Atualizado há ") + min + (isEnglish() ? " min ago" : " min");
  }

  private static Bitmap buildChart(SharedPreferences p) {
    int w = 360, h = 52;
    Bitmap bm = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
    Canvas c = new Canvas(bm);
    Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setStrokeWidth(3.6f);
    paint.setColor(Color.argb(210, 255, 255, 255));
    String csv = p.getString("historyCsv", "");
    List<Double> vals = parseCsv(csv);
    String pred = p.getString("prediction30", "");
    Double predVal = null;
    try { if (pred != null && pred.trim().length() > 0) predVal = Double.parseDouble(pred.replace(',', '.')); } catch(Exception ignored) {}
    List<Double> chartVals = new ArrayList<>(vals);
    if (predVal != null) chartVals.add(predVal);
    if (chartVals.size() < 2) {
      c.drawLine(10, h/2, w-10, h/2, paint);
      return bm;
    }
    double min = Collections.min(chartVals), max = Collections.max(chartVals);
    int targetBg = 100;
    try { targetBg = (int)Math.round(Double.parseDouble(p.getString("targetBg", "100").replace(',', '.'))); } catch(Exception ignored) {}
    min = Math.min(min, targetBg);
    max = Math.max(max, targetBg);
    if (Math.abs(max-min) < 1) { max += 5; min -= 5; }
    final double minV = min;
    final double maxV = max;
    Paint targetPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    targetPaint.setStyle(Paint.Style.STROKE);
    targetPaint.setStrokeWidth(1.5f);
    targetPaint.setColor(Color.argb(135, 255, 255, 255));
    targetPaint.setPathEffect(new android.graphics.DashPathEffect(new float[]{5f, 4f}, 0f));
    float targetY = chartY(targetBg, minV, maxV, h);
    c.drawLine(8, targetY, w-8, targetY, targetPaint);

    float lastX = -1, lastY = -1;
    int actualN = vals.size();
    for (int i=0;i<actualN;i++) {
      float x = 8 + (w-16) * (i/(float)Math.max(1, actualN + (predVal!=null?1:0) -1));
      float y = chartY(vals.get(i), minV, maxV, h);
      if (i>0) c.drawLine(lastX,lastY,x,y,paint);
      lastX=x; lastY=y;
    }
    if (predVal != null && actualN > 0) {
      Paint predPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
      predPaint.setStyle(Paint.Style.STROKE);
      predPaint.setStrokeWidth(2.8f);
      predPaint.setColor(Color.argb(250,255,255,255));
      float predX = 8 + (w-16);
      float predY = chartY(predVal, minV, maxV, h);
      c.drawLine(lastX, lastY, predX, predY, predPaint);
    }
    Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    basePaint.setStrokeWidth(1.2f);
    basePaint.setColor(Color.argb(55,255,255,255));
    c.drawLine(8,h-8,w-8,h-8,basePaint);
    return bm;
  }


  private static String compactHistory(String history) {
    if (history == null || history.trim().length() == 0) return "—";
    return history.replace(" → ", " ").replace("→", " ").trim();
  }

  private static String[] splitDelta(String delta) {
    if (delta == null || delta.trim().length() == 0) return new String[]{"Δ1 —", "—/min"};
    String d = delta.trim().replace("\\n", "\n");
    String[] lines = d.split("\n");
    String first = lines.length > 0 ? lines[0].trim() : "Δ1 —";
    String second = lines.length > 1 ? lines[1].trim() : "";
    if (second.length() == 0) {
      int idx = first.indexOf("/min");
      if (idx > 0) {
        int cut = first.lastIndexOf(' ', idx);
        if (cut > 0) {
          second = first.substring(cut + 1).trim();
          first = first.substring(0, cut).trim();
        }
      }
    }
    if (!first.startsWith("Δ1")) first = "Δ1 " + first;
    first = first.replace("Δ1 ", "Δ1 ").trim();
    if (second.length() == 0) second = "—/min";
    second = second.replace("mg/dL/min", "/min").replace(" ", "");
    return new String[]{first, second};
  }

  private static float chartY(double value, double minV, double maxV, int h) {
    return (float)(h - 8 - ((value - minV) / (maxV - minV)) * (h - 16));
  }

  private static List<Double> parseCsv(String csv){
    List<Double> out = new ArrayList<>();
    if(csv==null) return out;
    for(String s: csv.split(",")){
      try{ if(s.trim().length()>0) out.add(Double.parseDouble(s.trim().replace(',', '.'))); }catch(Exception ignored){}
    }
    return out;
  }

  private static void markSyncing(Context ctx) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putBoolean("syncing", true)
      .putString("status", "Sincronizando NS")
      .apply();
  }

  private static void runSync(Context ctx) {
    new Thread(() -> {
      SharedPreferences p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
      String base = p.getString("nsUrl", "");
      String token = p.getString("nsToken", "");
      try {
        if (base == null || base.trim().isEmpty()) throw new Exception("NS não configurado");
        base = base.trim().replaceAll("/+$", "");
        JSONArray entries = new JSONArray(httpGet(base + "/api/v1/entries/sgv.json?count=12", token));
        JSONArray devs;
        try { devs = new JSONArray(httpGet(base + "/api/v1/devicestatus.json?count=5", token)); } catch(Exception e) { devs = new JSONArray(); }
        JSONObject latest = entries.optJSONObject(0);
        if (latest == null) throw new Exception("sem CGM");
        double bg = latest.optDouble("sgv", Double.NaN);
        long date = latest.optLong("date", 0L);
        if (Double.isNaN(bg) || date <= 0 || bg < 40 || bg > 450) throw new Exception("CGM inválido/suspeito");
        long ageMin = Math.round((System.currentTimeMillis()-date)/60000.0);
        double prev = bg;
        if (entries.length() > 1) prev = entries.optJSONObject(1).optDouble("sgv", bg);
        double delta = bg - prev;
        double trend = 0.0;
        if (entries.length() > 1) {
          JSONObject e1 = entries.optJSONObject(1);
          long d1 = e1.optLong("date", date-300000L);
          trend = (bg - e1.optDouble("sgv", bg)) / Math.max(1.0, (date-d1)/60000.0);
        }
        double iob = findNumeric(devs, "iob", Double.NaN);
        double pred = findPrediction30FromDevicestatus(devs);
        String predSource = Double.isNaN(pred) ? "unavailable" : "ns_devicestatus_predBGs";
        List<String> hist = new ArrayList<>();
        for (int i=Math.min(entries.length()-1,4); i>=0; i--) {
          double hv = entries.optJSONObject(i).optDouble("sgv", Double.NaN);
          if (!Double.isNaN(hv) && hv >= 40 && hv <= 450) hist.add(String.valueOf(Math.round(hv)));
        }
        boolean ok = ageMin <= 10;
        p.edit()
          .putString("bg", String.valueOf(Math.round(bg)))
          .putString("lastGoodBg", String.valueOf(Math.round(bg)))
          .putString("unit", "mg/dL")
          .putString("trend", arrow(trend))
          .putString("delta", "Δ1 " + signed0(delta) + "\n" + signed1(trend) + "/min")
          .putString("iob", Double.isNaN(iob) ? "—" : fmt2(iob))
          .putString("prediction30", Double.isNaN(pred) ? "" : String.valueOf(Math.round(pred)))
          .putString("prediction30Source", predSource)
          .putString("historyText", String.join("→", hist))
          .putString("historyCsv", String.join(",", hist))
          .putString("cgmAge", "CGM " + ageMin + " min")
          .putBoolean("cgmOk", ok)
          .putString("status", ok ? "CGM atual" : "CGM atrasado")
          .putString("source", "Widget/NS")
          .putLong("updatedAt", System.currentTimeMillis())
          .putBoolean("syncing", false)
          .putString("lastError", "")
          .apply();
      } catch (Exception ex) {
        String msg = ex.getMessage()==null?ex.toString():ex.getMessage();
        String shortStatus = networkLikeError(msg) ? "Sem internet" : "Falha sync";
        p.edit()
          .putBoolean("syncing", false)
          .putString("status", shortStatus)
          .putString("source", "Widget/NS")
          .putString("lastError", msg)
          .apply();
      }
      updateAll(ctx);
        try { AapsMonitorWorker.runOneShot(ctx); } catch(Exception ignored) {}
    }).start();
  }

  private static String httpGet(String url, String token) throws Exception {
    HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection();
    conn.setConnectTimeout(12000); conn.setReadTimeout(12000); conn.setRequestMethod("GET");
    if (token != null && token.trim().length() > 0) conn.setRequestProperty("Authorization", "Bearer " + token.trim());
    int code = conn.getResponseCode();
    BufferedReader br = new BufferedReader(new InputStreamReader(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream()));
    StringBuilder sb = new StringBuilder(); String line; while((line=br.readLine())!=null) sb.append(line);
    if(code < 200 || code >= 300) throw new Exception("HTTP " + code);
    return sb.toString();
  }


  private static double findPrediction30FromDevicestatus(JSONArray devs) {
    if (devs == null) return Double.NaN;
    for (int i = 0; i < devs.length(); i++) {
      JSONObject d = devs.optJSONObject(i);
      if (d == null) continue;
      double v = findPrediction30InObject(d);
      if (validPrediction(v)) return v;
    }
    return Double.NaN;
  }

  private static double findPrediction30InObject(JSONObject obj) {
    if (obj == null) return Double.NaN;
    double v;

    v = predictionAtIndex(obj); if (validPrediction(v)) return v;

    JSONObject predBGs = obj.optJSONObject("predBGs");
    v = predictionAtIndex(predBGs); if (validPrediction(v)) return v;

    JSONObject openaps = obj.optJSONObject("openaps");
    if (openaps != null) {
      JSONObject suggested = openaps.optJSONObject("suggested");
      v = findPrediction30InObject(suggested); if (validPrediction(v)) return v;
      JSONObject enacted = openaps.optJSONObject("enacted");
      v = findPrediction30InObject(enacted); if (validPrediction(v)) return v;
      v = findPrediction30InObject(openaps); if (validPrediction(v)) return v;
    }

    JSONObject suggested = obj.optJSONObject("suggested");
    v = findPrediction30InObject(suggested); if (validPrediction(v)) return v;
    JSONObject enacted = obj.optJSONObject("enacted");
    v = findPrediction30InObject(enacted); if (validPrediction(v)) return v;

    return Double.NaN;
  }

  private static double predictionAtIndex(JSONObject obj) {
    if (obj == null) return Double.NaN;
    String[] keys = new String[]{"IOB", "COB", "UAM", "ZT", "aCOB"};
    for (String k : keys) {
      JSONArray arr = obj.optJSONArray(k);
      double v = predictionArrayAt30(arr);
      if (validPrediction(v)) return v;
    }
    return Double.NaN;
  }

  private static double predictionArrayAt30(JSONArray arr) {
    if (arr == null || arr.length() < 7) return Double.NaN;
    double v = arr.optDouble(6, Double.NaN); // arrays em ~5 min; índice 6 ≈ 30 min
    return validPrediction(v) ? v : Double.NaN;
  }

  private static boolean validPrediction(double v) {
    return !Double.isNaN(v) && v >= 40.0 && v <= 450.0;
  }

  private static double findNumeric(Object obj, String key, double fallback) {
    if(obj==null)return fallback;
    try{
      if(obj instanceof JSONArray){JSONArray a=(JSONArray)obj; for(int i=0;i<a.length();i++){double v=findNumeric(a.opt(i),key,Double.NaN); if(!Double.isNaN(v))return v;}}
      if(obj instanceof JSONObject){JSONObject o=(JSONObject)obj; if(o.has(key)){double v=o.optDouble(key,Double.NaN); if(!Double.isNaN(v)&&v>-100&&v<500)return v;} java.util.Iterator<String> it=o.keys(); while(it.hasNext()){double v=findNumeric(o.opt(it.next()),key,Double.NaN); if(!Double.isNaN(v))return v;}}
    }catch(Exception ignored){}
    return fallback;
  }

  private static String safeBgForDisplay(SharedPreferences p, String raw) {
    String s = raw == null ? "" : raw.trim();
    try {
      double v = Double.parseDouble(s.replace(',', '.'));
      String hist = p.getString("historyCsv", "");
      List<Double> vals = parseCsv(hist);
      if (v < 40 || v > 450) {
        String last = p.getString("lastGoodBg", "");
        if (last != null && last.trim().length() > 0) return last.trim();
        return "—";
      }
      // Proteção contra truncamento visual/parsing: ex. “18” quando a série recente estava 180–190.
      if (v < 40 && vals.size() >= 2) {
        double avg = 0; int n = 0;
        for (Double d : vals) { if (d != null && d >= 100) { avg += d; n++; } }
        if (n >= 2 && avg / n > 100) {
          String last = p.getString("lastGoodBg", "");
          if (last != null && last.trim().length() > 0) return last.trim();
          return String.valueOf(Math.round(vals.get(vals.size()-1)));
        }
      }
      return String.valueOf(Math.round(v));
    } catch(Exception ignored) {
      String last = p.getString("lastGoodBg", "");
      return s.length() > 0 ? s : (last == null || last.length() == 0 ? "—" : last);
    }
  }

  private static int bgColor(String bg) {
    try {
      double v = Double.parseDouble(String.valueOf(bg).replace(',', '.'));
      if (v < 70) return Color.rgb(231, 76, 60);       // vermelho
      if (v <= 180) return Color.rgb(40, 190, 95);    // verde
      if (v <= 250) return Color.rgb(255, 213, 74);   // amarelo AAPS
      return Color.rgb(231, 76, 60);                  // >250 vermelho/alerta
    } catch(Exception e) { return Color.rgb(255, 255, 255); }
  }

  private static boolean networkLikeError(String msg) {
    String m = msg == null ? "" : msg.toLowerCase();
    return m.contains("internet") || m.contains("host") || m.contains("timeout") || m.contains("network") || m.contains("dns") || m.contains("failed") || m.contains("http");
  }
  private static String arrow(double v){ if(v>=2) return "↗"; if(v<=-2) return "↘"; return "→"; }
  private static String signed0(double v){ long r=Math.round(v); return (r>0?"+":"") + r; }
  private static String signed1(double v){ double r=Math.round(v*10.0)/10.0; return (r>0?"+":"") + String.valueOf(r).replace('.', ','); }
  private static String fmt2(double v){ return String.valueOf(Math.round(v*100.0)/100.0).replace('.', ','); }
}
