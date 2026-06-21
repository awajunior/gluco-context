package com.glucocontext.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.ContentResolver;
import android.content.ContentValues;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.provider.MediaStore;
import android.provider.Settings;
import android.os.Build;
import androidx.annotation.NonNull;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import androidx.work.Worker;
import androidx.work.WorkerParameters;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.TimeUnit;

public class AapsMonitorWorker extends Worker {
  private static final String CHANNEL_ID = "aaps_assist_monitor_worker_v1";
  public AapsMonitorWorker(@NonNull Context context, @NonNull WorkerParameters params) { super(context, params); }
  public static void runOneShot(Context ctx) { WorkManager.getInstance(ctx.getApplicationContext()).enqueue(new OneTimeWorkRequest.Builder(AapsMonitorWorker.class).setInitialDelay(5, TimeUnit.SECONDS).build()); }

  @NonNull @Override public Result doWork() {
    Context ctx = getApplicationContext();
    SharedPreferences prefs = ctx.getSharedPreferences(AapsMonitorWorkerPlugin.PREFS, Context.MODE_PRIVATE);
    prefs.edit().putLong("lastRunAt", System.currentTimeMillis()).putLong("lastRunStartedAt", System.currentTimeMillis()).putString("lastError", "").apply();
    if (!prefs.getBoolean("enabled", false)) { prefs.edit().putString("lastResult", "desativado").putLong("lastRunFinishedAt", System.currentTimeMillis()).apply(); return Result.success(); }
    try {
      String base = prefs.getString("nsUrl", ""); String token = prefs.getString("nsToken", ""); float target = prefs.getFloat("target", 100f); int cooldownMin = prefs.getInt("cooldownMin", 60);
      if (base == null || base.trim().isEmpty()) throw new Exception("Nightscout ausente");
      JSONArray entries = getEntries(base, token, 60);
      JSONArray treatments = new JSONArray(httpGet(base + "/api/v1/treatments.json?count=80", token));
      JSONArray devs = new JSONArray(httpGet(base + "/api/v1/devicestatus.json?count=5", token));
      Eval eval = evaluate(entries, treatments, devs, target);
      long finishTs = System.currentTimeMillis();
      if (!eval.shouldNotify) {
        prefs.edit().putString("lastResult", eval.title + " · sem alerta").putLong("lastRunFinishedAt", finishTs).putString("lastError", "").putString("lastDeliveryStatus", "sem-alerta").apply();
        return Result.success();
      }
      if (!ruleEnabled(prefs, eval.id)) {
        prefs.edit().putString("lastResult", eval.title + " · detectado, não enviado — regra desativada").putLong("lastRunFinishedAt", System.currentTimeMillis()).putString("lastError", "").putString("lastDeliveryStatus", "bloqueado-regra").putString("lastDeliveryAlertId", eval.id).apply();
        return Result.success();
      }
      if (isQuietNow(prefs)) {
        prefs.edit().putString("lastResult", eval.title + " · detectado, não enviado — silêncio noturno").putLong("lastRunFinishedAt", System.currentTimeMillis()).putString("lastError", "").putString("lastDeliveryStatus", "bloqueado-silencio").putString("lastDeliveryAlertId", eval.id).apply();
        return Result.success();
      }
      if (!NotificationManagerCompat.from(ctx).areNotificationsEnabled()) {
        prefs.edit().putString("lastResult", eval.title + " · detectado, não enviado — notificações bloqueadas no Android").putLong("lastRunFinishedAt", System.currentTimeMillis()).putString("lastError", "notificações bloqueadas").putString("lastDeliveryStatus", "bloqueado-permissao").putString("lastDeliveryAlertId", eval.id).apply();
        return Result.success();
      }
      if (shouldNotifyNow(prefs, eval, cooldownMin)) {
        notify(ctx, eval.title, eval.body);
        long nowNotify = System.currentTimeMillis();
        appendNotificationLog(prefs, eval, nowNotify, "worker-enviado");
        prefs.edit()
          .putLong("lastNotifyAt", nowNotify)
          .putString("lastAlertId", eval.id)
          .putFloat("lastNotifyBg", (float)eval.bg)
          .putLong("lastNotifyAt_" + eval.id, nowNotify)
          .putFloat("lastNotifyBg_" + eval.id, (float)eval.bg)
          .putString("lastDeliveryStatus", "enviado")
          .putString("lastDeliveryAlertId", eval.id)
          .putLong("lastDeliveryAt", nowNotify)
          .putString("lastResult", eval.title + " · notificação enviada às " + new java.text.SimpleDateFormat("HH:mm", java.util.Locale.getDefault()).format(new java.util.Date(nowNotify)))
          .putLong("lastRunFinishedAt", nowNotify)
          .putString("lastError", "")
          .apply();
      } else {
        prefs.edit()
          .putString("lastResult", eval.title + " · detectado, não enviado — cooldown ativo")
          .putLong("lastRunFinishedAt", System.currentTimeMillis())
          .putString("lastError", "")
          .putString("lastDeliveryStatus", "cooldown")
          .putString("lastDeliveryAlertId", eval.id)
          .apply();
      }
      return Result.success();
    } catch (Exception e) { String msg = e.getMessage()==null?e.toString():e.getMessage(); prefs.edit().putString("lastResult", "erro: " + msg).putString("lastError", msg).putLong("lastRunFinishedAt", System.currentTimeMillis()).apply(); return Result.retry(); }
  }

  private static String httpGet(String url, String token) throws Exception {
    HttpURLConnection conn = (HttpURLConnection)new URL(url).openConnection(); conn.setConnectTimeout(12000); conn.setReadTimeout(12000); conn.setRequestMethod("GET");
    if (token != null && token.trim().length() > 0) conn.setRequestProperty("Authorization", "Bearer " + token.trim());
    int code = conn.getResponseCode(); BufferedReader br = new BufferedReader(new InputStreamReader(code >= 200 && code < 300 ? conn.getInputStream() : conn.getErrorStream()));
    StringBuilder sb = new StringBuilder(); String line; while ((line = br.readLine()) != null) sb.append(line); if (code < 200 || code >= 300) throw new Exception("HTTP " + code); return sb.toString();
  }
  private static JSONArray getEntries(String base, String token, int count) throws Exception {
    String primary = base + "/api/v1/entries/sgv.json?count=" + count;
    String fallback = base + "/api/v1/entries.json?count=" + count;
    try {
      return new JSONArray(httpGet(primary, token));
    } catch (Exception first) {
      try {
        return new JSONArray(httpGet(fallback, token));
      } catch (Exception second) {
        throw new Exception("entries falhou: sgv=" + first.getMessage() + "; entries.json=" + second.getMessage());
      }
    }
  }
  private static class Eval { String id; String title; String body; boolean shouldNotify; double bg; double trend; double delta15; double delta40; double iob; double cob; double prediction30; double siteAgeHours; double sensorAgeDays; int lastMealMin; int plateauCount30; double plateauAvg30; double dropFromMax30; double recentBolusMax20; double recentBolusSum20; int recentBolusCount20; }
  private static Eval evaluate(JSONArray entries, JSONArray treatments, JSONArray devs, double target) {
    Eval e = new Eval(); e.id="no-context-alert"; e.title="Sem alerta contextual"; e.body="Nenhuma regra preditiva foi acionada."; e.shouldNotify=false; e.bg=-1; e.trend=Double.NaN; e.delta15=Double.NaN; e.delta40=Double.NaN; e.iob=Double.NaN; e.cob=Double.NaN; e.prediction30=Double.NaN; e.plateauCount30=0; e.plateauAvg30=Double.NaN; e.dropFromMax30=Double.NaN; e.recentBolusMax20=Double.NaN; e.recentBolusSum20=Double.NaN; e.recentBolusCount20=0; e.siteAgeHours=Double.NaN; e.sensorAgeDays=Double.NaN; e.lastMealMin=-1;
    if (entries == null || entries.length() < 2) { e.title="Alertas limitado"; e.body="BG insuficiente no Nightscout."; return e; }
    JSONObject latest = entries.optJSONObject(0); double bg = latest.optDouble("sgv", Double.NaN); long latestDate = latest.optLong("date", 0L);
    if (Double.isNaN(bg) || latestDate <= 0 || System.currentTimeMillis() - latestDate > 20L*60L*1000L) { e.title="Alertas limitado"; e.body="BG ausente ou antigo."; return e; }
    e.bg=bg; JSONObject prev5=findAround(entries, latestDate-5L*60L*1000L), prev15=findAround(entries, latestDate-15L*60L*1000L), prev40=findAround(entries, latestDate-40L*60L*1000L);
    double trend = prev5==null ? 0 : (bg-prev5.optDouble("sgv",bg))/Math.max(1.0,(latestDate-prev5.optLong("date",latestDate))/60000.0);
    double d15 = prev15==null ? Double.NaN : bg-prev15.optDouble("sgv",bg); double d40 = prev40==null ? Double.NaN : bg-prev40.optDouble("sgv",bg);
    double iob=findNumeric(devs,"iob",Double.NaN), cob=findNumeric(devs,"cob",0.0); int lastBolus=lastTreatmentMin(treatments,true), lastMeal=lastTreatmentMin(treatments,false); boolean mealActive=lastMeal>=0&&lastMeal<120; boolean mealWindow24=lastMeal>=120&&lastMeal<=240; double siteAgeHours=lastEventAgeHours(treatments, new String[]{"site","cannula","canula","infusion"}); double sensorAgeHours=lastEventAgeHours(treatments, new String[]{"sensor"});
    BolusStats bolus20=recentBolusStats(treatments,20); boolean relevantBolusRecent=bolus20.maxSingle>=0.30 || bolus20.sum>=0.50;
    e.trend=trend; e.delta15=d15; e.delta40=d40; e.iob=iob; e.cob=cob; e.prediction30=bg + trend*30.0; e.siteAgeHours=siteAgeHours; e.sensorAgeDays=Double.isNaN(sensorAgeHours)?Double.NaN:sensorAgeHours/24.0; e.lastMealMin=lastMeal; e.recentBolusMax20=bolus20.count>0?bolus20.maxSingle:Double.NaN; e.recentBolusSum20=bolus20.count>0?bolus20.sum:Double.NaN; e.recentBolusCount20=bolus20.count;
    if (!Double.isNaN(iob) && bg<=110 && trend<=-1.5 && (Double.isNaN(d15)||d15<=-12) && iob>=0.7 && cob<=8) { e.id="drop-risk-iob"; e.title="Queda rápida com IOB ativo"; e.body="BG "+Math.round(bg)+", tendência "+round1(trend)+" mg/dL/min, IOB "+round2(iob)+" U. Reavalie contexto."; e.shouldNotify=true; return e; }
    if (mealActive && bg>=130 && trend>=2.0 && (Double.isNaN(d15)||d15>=20)) { e.id="rapid-rise-post-meal"; e.title="Subida rápida pós-refeição"; e.body="Refeição há "+lastMeal+" min, BG "+Math.round(bg)+", tendência +"+round1(trend)+" mg/dL/min. Abrir app para revisar contexto."; e.shouldNotify=true; return e; }
    if (mealWindow24 && bg>=140 && trend>=0.5) { e.id="meal-window-2-4h-review"; e.title="Janela 2–4h: avaliar"; e.body="Refeição há "+lastMeal+" min, BG acima do alvo e ainda subindo. Revisar contexto no app."; e.shouldNotify=true; return e; }
    if ((!Double.isNaN(siteAgeHours) && siteAgeHours>72) || (!Double.isNaN(sensorAgeHours) && sensorAgeHours>240)) { e.id="sensor-site-attention"; e.title="Atenção sensor/site"; String siteTxt=Double.isNaN(siteAgeHours)?"":"site "+Math.round(siteAgeHours)+"h"; String sensorTxt=Double.isNaN(sensorAgeHours)?"":"sensor "+round1(sensorAgeHours/24.0)+"d"; String sep=(siteTxt.length()>0 && sensorTxt.length()>0)?" · ":""; e.body=siteTxt+sep+sensorTxt+". Confiança reduzida; revisar antes de decisões automáticas."; e.shouldNotify=true; return e; }
    if (bg>=target+50 && trend>=1 && (Double.isNaN(iob)||iob<0.3) && cob<=5 && (lastBolus<0||lastBolus>=90||!relevantBolusRecent) && !mealActive) { e.id="review-correction-context"; e.title="Revisar contexto no AAPS"; e.body="BG alto/subindo com pouca insulina ativa. Este app não recomenda bolus."; e.shouldNotify=true; return e; }
    return e;
  }
  private static class PlateauStats { int count; double avg=Double.NaN; double max=Double.NaN; double dropFromMax=Double.NaN; }
  private static PlateauStats plateauStats(JSONArray arr, long latestDate, int windowMin) {
    PlateauStats ps = new PlateauStats(); if(arr==null || arr.length()<1 || latestDate<=0) return ps;
    long minMs = latestDate - windowMin*60L*1000L; double sum=0.0, max=-1.0, latestBg=Double.NaN; int count=0;
    for(int i=0;i<arr.length();i++){
      JSONObject o=arr.optJSONObject(i); if(o==null) continue; long t=o.optLong("date",0L); double v=o.optDouble("sgv",Double.NaN);
      if(Double.isNaN(v) || t<minMs || t>latestDate) continue;
      if(t==latestDate) latestBg=v; sum+=v; if(v>max) max=v; count++;
    }
    ps.count=count; if(count>0){ ps.avg=Math.round((sum/count)*10.0)/10.0; ps.max=max; if(Double.isNaN(latestBg)){ JSONObject latest=arr.optJSONObject(0); latestBg=latest==null?Double.NaN:latest.optDouble("sgv",Double.NaN); } if(!Double.isNaN(latestBg)) ps.dropFromMax=Math.round((max-latestBg)*10.0)/10.0; }
    return ps;
  }
  private static class BolusStats { int count; double maxSingle=0.0; double sum=0.0; }
  private static BolusStats recentBolusStats(JSONArray arr, int windowMin) {
    BolusStats bs = new BolusStats(); long now=System.currentTimeMillis(); if(arr==null) return bs;
    for(int i=0;i<arr.length();i++){
      JSONObject o=arr.optJSONObject(i); if(o==null) continue; long t=o.optLong("date",0L); if(t<=0 || now-t>windowMin*60L*1000L || t>now+5L*60L*1000L) continue;
      double v=Math.max(o.optDouble("insulin",0),o.optDouble("enteredinsulin",0)); if(v<=0) continue;
      bs.count++; bs.sum+=v; if(v>bs.maxSingle) bs.maxSingle=v;
    }
    bs.sum=Math.round(bs.sum*100.0)/100.0; bs.maxSingle=Math.round(bs.maxSingle*100.0)/100.0; return bs;
  }
  private static JSONObject findAround(JSONArray arr, long targetMs) { JSONObject best=null; long bestDist=Long.MAX_VALUE; for(int i=1;i<arr.length();i++){ JSONObject o=arr.optJSONObject(i); if(o==null)continue; long d=Math.abs(o.optLong("date",0L)-targetMs); if(d<bestDist){best=o; bestDist=d;}} return best; }
  private static double lastEventAgeHours(JSONArray arr, String[] keywords) { long now=System.currentTimeMillis(), best=0L; if(arr==null)return Double.NaN; for(int i=0;i<arr.length();i++){ JSONObject o=arr.optJSONObject(i); if(o==null)continue; String ev=(o.optString("eventType","")+" "+o.optString("notes","")).toLowerCase(); boolean hit=false; for(String k:keywords){ if(ev.contains(k.toLowerCase())){ hit=true; break; } } if(!hit)continue; long t=o.optLong("date",0L); if(t>best)best=t; } return best>0?Math.max(0.0,(now-best)/3600000.0):Double.NaN; }
  private static int lastTreatmentMin(JSONArray arr, boolean bolus) { long now=System.currentTimeMillis(), best=0L; for(int i=0;i<arr.length();i++){ JSONObject o=arr.optJSONObject(i); if(o==null)continue; double v=bolus?Math.max(o.optDouble("insulin",0),o.optDouble("enteredinsulin",0)):Math.max(o.optDouble("carbs",0),o.optDouble("enteredCarbs",0)); if(v<=0)continue; long t=o.optLong("date",0L); if(t>best)best=t;} return best>0?(int)Math.round((now-best)/60000.0):-1; }
  private static double findNumeric(Object obj, String key, double fallback) { if(obj==null)return fallback; try{ if(obj instanceof JSONArray){JSONArray a=(JSONArray)obj; for(int i=0;i<a.length();i++){double v=findNumeric(a.opt(i),key,Double.NaN); if(!Double.isNaN(v))return v;}} if(obj instanceof JSONObject){JSONObject o=(JSONObject)obj; if(o.has(key)){double v=o.optDouble(key,Double.NaN); if(!Double.isNaN(v)&&v>=0&&v<500)return v;} java.util.Iterator<String> it=o.keys(); while(it.hasNext()){double v=findNumeric(o.opt(it.next()),key,Double.NaN); if(!Double.isNaN(v))return v;}} }catch(Exception ignored){} return fallback; }
  private static int cooldownFor(SharedPreferences prefs, String id, int fallback) {
    if ("drop-risk-iob".equals(id)) return prefs.getInt("cooldownDropMin", 20);
    if ("persistent-high-with-iob".equals(id)) return prefs.getInt("cooldownPersistentHighMin", 60);
    if ("review-correction-context".equals(id)) return prefs.getInt("cooldownReviewCorrectionMin", 90);
    if ("rapid-rise-post-meal".equals(id)) return prefs.getInt("cooldownPostMealRiseMin", 20);
    if ("meal-window-2-4h-review".equals(id)) return prefs.getInt("cooldownMealWindowMin", 60);
    if ("sensor-site-attention".equals(id)) return prefs.getInt("cooldownSensorSiteMin", 720);
    return fallback > 0 ? fallback : 60;
  }

  private static boolean ruleEnabled(SharedPreferences prefs, String id) {
    if ("drop-risk-iob".equals(id)) return prefs.getBoolean("notifyDrop", true);
    if ("persistent-high-with-iob".equals(id)) return prefs.getBoolean("notifyPersistentHigh", true);
    if ("review-correction-context".equals(id)) return prefs.getBoolean("notifyReviewCorrection", true);
    if ("rapid-rise-post-meal".equals(id)) return prefs.getBoolean("notifyPostMealRise", true);
    if ("meal-window-2-4h-review".equals(id)) return prefs.getBoolean("notifyMealWindow", true);
    if ("sensor-site-attention".equals(id)) return prefs.getBoolean("notifySensorSite", true);
    return true;
  }
  private static int minutesOfDay(String hhmm, int fallback) {
    try { String[] p = hhmm.split(":"); return Integer.parseInt(p[0])*60 + Integer.parseInt(p[1]); } catch(Exception e) { return fallback; }
  }
  private static boolean isQuietNow(SharedPreferences prefs) {
    if(!prefs.getBoolean("quietEnabled", true)) return false;
    java.util.Calendar cal = java.util.Calendar.getInstance();
    int now = cal.get(java.util.Calendar.HOUR_OF_DAY)*60 + cal.get(java.util.Calendar.MINUTE);
    int start = minutesOfDay(prefs.getString("quietStart", "22:00"), 22*60+30);
    int end = minutesOfDay(prefs.getString("quietEnd", "06:00"), 6*60+30);
    if(start == end) return false;
    if(start < end) return now >= start && now < end;
    return now >= start || now < end;
  }

  private static boolean shouldNotifyNow(SharedPreferences prefs, Eval eval, int cooldownMin) {
    // v1.11.4.21: cooldown persistido por regra, não apenas último alerta global.
    // Isso evita que um alerta de outro tipo sobrescreva a referência de tempo/BG desta regra.
    long last=prefs.getLong("lastNotifyAt_" + eval.id,0L);
    float lastBg=prefs.getFloat("lastNotifyBg_" + eval.id,-1f);
    long age=System.currentTimeMillis()-last; int ruleCooldown=cooldownFor(prefs, eval.id, cooldownMin);
    if(last<=0)return true;
    if(age>=ruleCooldown*60L*1000L)return true;
    // Worsening override: allow a repeated alert inside cooldown only if the same rule clearly worsened.
    if(eval.id.equals("drop-risk-iob")&&lastBg>0&&eval.bg<=lastBg-10)return true;
    if(eval.id.equals("persistent-high-with-iob")&&lastBg>0&&eval.bg>=lastBg+20)return true;
    if(eval.id.equals("review-correction-context")&&lastBg>0&&eval.bg>=lastBg+25)return true;
    if(eval.id.equals("rapid-rise-post-meal")&&lastBg>0&&eval.bg>=lastBg+20)return true;
    if(eval.id.equals("meal-window-2-4h-review")&&lastBg>0&&eval.bg>=lastBg+20)return true;
    return false;
  }
  private static void appendNotificationLog(SharedPreferences prefs, Eval eval, long ts, String origin) {
    try {
      JSONArray old = new JSONArray(prefs.getString("notificationLog", "[]"));
      JSONArray next = new JSONArray();
      JSONObject item = new JSONObject();
      item.put("ts", ts); item.put("id", eval.id); item.put("title", eval.title); item.put("body", eval.body);
      item.put("bg", Double.isNaN(eval.bg) ? JSONObject.NULL : eval.bg);
      item.put("trend", Double.isNaN(eval.trend) ? JSONObject.NULL : eval.trend);
      item.put("delta15", Double.isNaN(eval.delta15) ? JSONObject.NULL : eval.delta15);
      item.put("delta40", Double.isNaN(eval.delta40) ? JSONObject.NULL : eval.delta40);
      item.put("iob", Double.isNaN(eval.iob) ? JSONObject.NULL : eval.iob);
      item.put("cob", Double.isNaN(eval.cob) ? JSONObject.NULL : eval.cob);
      item.put("prediction30", Double.isNaN(eval.prediction30) ? JSONObject.NULL : eval.prediction30);
      item.put("siteAgeHours", Double.isNaN(eval.siteAgeHours) ? JSONObject.NULL : eval.siteAgeHours);
      item.put("sensorAgeDays", Double.isNaN(eval.sensorAgeDays) ? JSONObject.NULL : eval.sensorAgeDays);
      item.put("lastMealMin", eval.lastMealMin < 0 ? JSONObject.NULL : eval.lastMealMin);
      item.put("plateauCount30", eval.plateauCount30);
      item.put("plateauAvg30", Double.isNaN(eval.plateauAvg30) ? JSONObject.NULL : eval.plateauAvg30);
      item.put("dropFromMax30", Double.isNaN(eval.dropFromMax30) ? JSONObject.NULL : eval.dropFromMax30);
      item.put("recentBolusMax20", Double.isNaN(eval.recentBolusMax20) ? JSONObject.NULL : eval.recentBolusMax20);
      item.put("recentBolusSum20", Double.isNaN(eval.recentBolusSum20) ? JSONObject.NULL : eval.recentBolusSum20);
      item.put("origin", origin);
      next.put(item);
      for(int i=0;i<old.length() && i<49;i++) next.put(old.opt(i));
      prefs.edit().putString("notificationLog", next.toString()).putLong("notificationLogUpdatedAt", ts).apply();
    } catch(Exception ignored) {}
  }

  private static void notify(Context ctx, String title, String body) { NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE); if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"AAPS Assist Alertas",NotificationManager.IMPORTANCE_DEFAULT); ch.setDescription("Alertas técnicos opcionais do AAPS Assist."); nm.createNotificationChannel(ch);} NotificationCompat.Builder b=new NotificationCompat.Builder(ctx,CHANNEL_ID).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT); nm.notify((int)(System.currentTimeMillis()%2147483000),b.build()); }
  private static String round1(double v){return String.valueOf(Math.round(v*10.0)/10.0);} private static String round2(double v){return String.valueOf(Math.round(v*100.0)/100.0);} }
