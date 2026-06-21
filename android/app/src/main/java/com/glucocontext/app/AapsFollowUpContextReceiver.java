package com.glucocontext.app;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
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
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

public class AapsFollowUpContextReceiver extends BroadcastReceiver {
  public static final String ACTION = "com.glucocontext.app.FOLLOWUP_CONTEXT_ALARM";
  private static final String CHANNEL_ID = "aaps_assist_followup_context_v1";

  @Override public void onReceive(Context ctx, Intent intent) {
    final BroadcastReceiver.PendingResult pendingResult = goAsync();
    final Context appCtx = ctx.getApplicationContext();
    final Intent safeIntent = new Intent(intent);
    new Thread(new Runnable() {
      @Override public void run() {
        try {
          handleAlarm(appCtx, safeIntent);
        } catch (Exception e) {
          try {
            SharedPreferences prefs = appCtx.getSharedPreferences(AapsMonitorWorkerPlugin.PREFS, Context.MODE_PRIVATE);
            String msg = "Follow-up contextual async error: " + (e.getMessage()==null?e.toString():e.getMessage());
            prefs.edit().putString("lastError", msg).putString("lastFollowUpContextResult", msg).apply();
          } catch(Exception ignored) {}
        } finally {
          pendingResult.finish();
        }
      }
    }, "AAPS-FollowUpContext").start();
  }

  private static void handleAlarm(Context ctx, Intent intent) {

    SharedPreferences prefs = ctx.getSharedPreferences(AapsMonitorWorkerPlugin.PREFS, Context.MODE_PRIVATE);
    long firedAt = System.currentTimeMillis();
    prefs.edit().putLong("lastFollowUpContextAlarmAt", firedAt).putInt("lastFollowUpContextPendingId", -1).putString("lastError", "").apply();
    String nsUrl = intent.getStringExtra("nsUrl"); if(nsUrl == null || nsUrl.length()==0) nsUrl = prefs.getString("followUpNsUrl", "");
    String nsToken = intent.getStringExtra("nsToken"); if(nsToken == null) nsToken = prefs.getString("followUpNsToken", "");
    double target = intent.getDoubleExtra("target", prefs.getFloat("followUpTarget", 100f));
    String window = intent.getStringExtra("window"); if(window == null) window = "follow-up";
    boolean test = intent.getBooleanExtra("test", false);
    String chainId = intent.getStringExtra("chainId"); if(chainId == null) chainId = prefs.getString("followUpChainId", "");
    int chainStep = intent.getIntExtra("chainStep", prefs.getInt("followUpChainStep", 1));
    long mealStartAt = intent.getLongExtra("mealStartAt", prefs.getLong("followUpChainMealStartAt", firedAt));
    int maxAgeMin = intent.getIntExtra("maxAgeMin", prefs.getInt("followUpChainMaxAgeMin", 240));
    int maxSteps = intent.getIntExtra("maxSteps", prefs.getInt("followUpChainMaxSteps", 5));
    boolean silentMode = intent.getBooleanExtra("silentMode", prefs.getBoolean("followUpSilentMode", false));
    boolean autoReschedule = intent.getBooleanExtra("autoReschedule", prefs.getBoolean("followUpAutoReschedule", false));
    double delayedDose = intent.getDoubleExtra("delayedDose", prefs.getFloat("followUpDelayedDose", 0f));
    String splitStrategy = intent.getStringExtra("splitStrategy");
    if(splitStrategy == null) splitStrategy = prefs.getString("followUpSplitStrategy", "");
    long chainAgeMin = Math.max(0L, Math.round((firedAt - mealStartAt)/60000.0));
    Eval eval;
    try {
      if(nsUrl == null || nsUrl.trim().isEmpty()) throw new Exception("Nightscout ausente");
      JSONArray entries = getEntries(nsUrl, nsToken, 60);
      JSONArray devs = new JSONArray(httpGet(nsUrl + "/api/v1/devicestatus.json?count=5", nsToken));
      eval = evaluate(entries, devs, target);
      eval = applySplitAwareness(eval, delayedDose, splitStrategy, target);
    } catch(Exception e) {
      eval = new Eval(); eval.action="limited"; eval.title="Follow-up contextual limitado"; eval.reason="Não foi possível sincronizar o Nightscout no disparo: " + (e.getMessage()==null?e.toString():e.getMessage()); eval.bg=Double.NaN; eval.trend=Double.NaN; eval.delta15=Double.NaN; eval.delta40=Double.NaN; eval.iob=Double.NaN; eval.cob=Double.NaN; eval.prediction30=Double.NaN;
      prefs.edit().putString("lastError", eval.reason).apply();
    }
    if(!test && (chainAgeMin > maxAgeMin || chainStep > maxSteps)){
      eval = new Eval(); eval.action="chain-ended"; eval.title="Follow-up encerrado"; eval.reason="Cadeia encerrada automaticamente por limite de tempo/etapas. Nova refeição ou evento deve iniciar novo Follow-up."; eval.bg=Double.NaN; eval.trend=Double.NaN; eval.delta15=Double.NaN; eval.delta40=Double.NaN; eval.iob=Double.NaN; eval.cob=Double.NaN; eval.prediction30=Double.NaN;
    }
    boolean silentRecheck = !test && silentMode && "suppress".equals(eval.action) && eval.title != null && eval.title.indexOf("normal") >= 0;
    boolean shouldSilence = !test && silentMode && ("suppress-preview".equals(eval.action) || "suppress".equals(eval.action));
    String title = test ? "Gluco Context v2.32 Follow-up — teste contextual" : notificationTitle(eval);
    String body = notificationBody(eval, window);
    if(shouldSilence){
      String previousTitle = eval.title;
      eval.action = "silenced";
      eval.title = previousTitle != null && previousTitle.indexOf("normal") >= 0 ? "Follow-up normal silenciado" : "Follow-up silenciado";
      eval.reason = previousTitle != null && previousTitle.indexOf("normal") >= 0 ? "Sem risco claro e sem piora relevante. Notificação silenciada; nova reavaliação será agendada se ainda dentro dos limites." : "Contexto estável no disparo. Silenciamento conservador ativo; nenhuma notificação foi emitida.";
      title = "Gluco Context v2.32 Follow-up — silenciado";
      body = notificationBody(eval, window);
    } else {
      notify(ctx, title, body);
    }
    appendLog(prefs, eval, firedAt, test ? "alarm-native-test" : "alarm-native", title, body, chainId, chainStep, chainAgeMin, maxAgeMin, maxSteps, 0L);

    // v1.12.22 — Follow-up scheduler audit fix
    // Every native follow-up evaluation must end with an explicit scheduler state:
    // rescheduled, silenced+rescheduled, or closed with a reason.
    if(!test && autoReschedule){
      try {
        boolean attentionOrNormal = ("notify-attention".equals(eval.action) || "notify-normal".equals(eval.action) || silentRecheck);
        boolean stable = ("suppress-preview".equals(eval.action) || "silenced".equals(eval.action) || "suppress".equals(eval.action));
        boolean alreadyClosed = "chain-ended".equals(eval.action);
        boolean withinSteps = chainStep < maxSteps;
        long nextMin = 15L;
        long nextAge = chainAgeMin + nextMin;
        boolean withinAge = nextAge <= maxAgeMin;
        if(attentionOrNormal && withinSteps && withinAge){
          long nextAt = firedAt + nextMin*60000L;
          int nextId = (int)((System.currentTimeMillis()+7919L) % 2147480000L);
          Intent nextIntent = new Intent(ctx, AapsFollowUpContextReceiver.class);
          nextIntent.setAction(ACTION);
          nextIntent.putExtra("id", nextId);
          nextIntent.putExtra("nsUrl", nsUrl == null ? "" : nsUrl.trim().replaceAll("/+$", ""));
          nextIntent.putExtra("nsToken", nsToken == null ? "" : nsToken);
          nextIntent.putExtra("target", target);
          nextIntent.putExtra("targetAt", nextAt);
          nextIntent.putExtra("decisionId", intent.getStringExtra("decisionId") == null ? "" : intent.getStringExtra("decisionId"));
          nextIntent.putExtra("window", "auto 15 min reavaliação");
          nextIntent.putExtra("test", false);
          nextIntent.putExtra("chainId", chainId == null ? "" : chainId);
          nextIntent.putExtra("chainStep", chainStep + 1);
          nextIntent.putExtra("mealStartAt", mealStartAt);
          nextIntent.putExtra("maxAgeMin", maxAgeMin);
          nextIntent.putExtra("maxSteps", maxSteps);
          nextIntent.putExtra("silentMode", silentMode);
          nextIntent.putExtra("autoReschedule", autoReschedule);
          nextIntent.putExtra("delayedDose", delayedDose);
          nextIntent.putExtra("splitStrategy", splitStrategy == null ? "" : splitStrategy);
          int flags = PendingIntent.FLAG_UPDATE_CURRENT;
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
          PendingIntent nextPi = PendingIntent.getBroadcast(ctx, nextId, nextIntent, flags);
          AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, nextAt, nextPi);
          else am.setExact(AlarmManager.RTC_WAKEUP, nextAt, nextPi);
          prefs.edit()
            .putLong("lastFollowUpContextScheduledAt", firedAt)
            .putLong("lastFollowUpContextTargetAt", nextAt)
            .putInt("lastFollowUpContextPendingId", nextId)
            .putString("followUpChainId", chainId == null ? "" : chainId)
            .putInt("followUpChainStep", chainStep + 1)
            .putLong("followUpChainMealStartAt", mealStartAt)
            .putString("lastFollowUpContextWindow", "auto 15 min reavaliação")
            .putString("lastFollowUpSchedulerState", "reagendado")
            .putString("lastFollowUpSchedulerReason", "atenção/normal ainda exige nova reavaliação")
            .apply();
          Eval res = new Eval(); res.action="auto-rescheduled"; res.title="Reavaliação autoagendada"; res.reason="Estado final do scheduler: reagendado. Nova reavaliação contextual agendada em 15 min, dentro dos limites da cadeia."; res.bg=eval.bg; res.trend=eval.trend; res.delta15=eval.delta15; res.delta40=eval.delta40; res.iob=eval.iob; res.cob=eval.cob; res.prediction30=eval.prediction30;
          appendLog(prefs, res, firedAt+1L, "alarm-native", "Gluco Context v2.32 Follow-up — autoagendado", res.reason, chainId, chainStep + 1, nextAge, maxAgeMin, maxSteps, nextAt);
          prefs.edit().putString("lastFollowUpContextResult", eval.title + " · " + eval.action + " · scheduler=reagendado · nextAt=" + nextAt).apply();
        } else {
          Eval terminal = new Eval(); terminal.action="chain-ended"; terminal.bg=eval.bg; terminal.trend=eval.trend; terminal.delta15=eval.delta15; terminal.delta40=eval.delta40; terminal.iob=eval.iob; terminal.cob=eval.cob; terminal.prediction30=eval.prediction30;
          if(alreadyClosed){ terminal.title="Follow-up encerrado"; terminal.reason="Estado final do scheduler: encerrado. Motivo: limite de tempo/etapas já atingido; nenhum novo alarme pendente."; }
          else if(stable){ terminal.title="Follow-up encerrado por estabilidade"; terminal.reason="Estado final do scheduler: encerrado. Motivo: contexto estável; nenhum novo alarme pendente. Inicie novo Follow-up se houver nova refeição, correção ou mudança clínica."; }
          else if(!withinSteps || !withinAge){ terminal.title="Follow-up encerrado por limite"; terminal.reason="Estado final do scheduler: encerrado. Motivo: limite de etapas ou janela máxima atingido; nenhum novo alarme pendente."; }
          else { terminal.title="Follow-up encerrado sem reagendamento"; terminal.reason="Estado final do scheduler: encerrado. Motivo: classificação não elegível para auto-reagendamento; nenhum novo alarme pendente."; }
          prefs.edit().putInt("lastFollowUpContextPendingId", -1).putString("lastFollowUpSchedulerState", "encerrado").putString("lastFollowUpSchedulerReason", terminal.reason).putString("lastFollowUpContextResult", eval.title + " · " + eval.action + " · scheduler=encerrado").apply();
          appendLog(prefs, terminal, firedAt+1L, "alarm-native", "Gluco Context v2.32 Follow-up — encerrado", terminal.reason, chainId, chainStep, chainAgeMin, maxAgeMin, maxSteps, 0L);
        }
      } catch(Exception e) {
        prefs.edit().putString("lastError", "Falha ao finalizar scheduler do Follow-up: " + (e.getMessage()==null?e.toString():e.getMessage())).apply();
      }
    } else if(!test) {
      Eval terminal = new Eval(); terminal.action="chain-ended"; terminal.title="Follow-up sem auto-reagendamento"; terminal.reason="Estado final do scheduler: encerrado. Reagendamento automático desligado; nenhum novo alarme pendente. Use Recriar alarme se quiser continuar a cadeia."; terminal.bg=eval.bg; terminal.trend=eval.trend; terminal.delta15=eval.delta15; terminal.delta40=eval.delta40; terminal.iob=eval.iob; terminal.cob=eval.cob; terminal.prediction30=eval.prediction30;
      prefs.edit().putInt("lastFollowUpContextPendingId", -1).putString("lastFollowUpSchedulerState", "encerrado").putString("lastFollowUpSchedulerReason", terminal.reason).putString("lastFollowUpContextResult", eval.title + " · " + eval.action + " · scheduler=encerrado(auto desligado)").apply();
      appendLog(prefs, terminal, firedAt+1L, "alarm-native", "Gluco Context v2.32 Follow-up — sem auto-reagendamento", terminal.reason, chainId, chainStep, chainAgeMin, maxAgeMin, maxSteps, 0L);
    } else {
      prefs.edit().putString("lastFollowUpContextResult", eval.title + " · " + eval.action + " · teste").apply();
    }
  }

  private static class Eval { String action; String title; String reason; double bg; double trend; double delta15; double delta40; double iob; double cob; double prediction30; double siteAgeHours; double sensorAgeDays; int lastMealMin; int plateauCount30; double plateauAvg30; double dropFromMax30; double recentBolusMax20; double recentBolusSum20; int recentBolusCount20; }
  private static Eval evaluate(JSONArray entries, JSONArray devs, double target) throws Exception {
    Eval e = new Eval(); e.action="limited"; e.title="Follow-up contextual limitado"; e.reason="Dados insuficientes."; e.bg=Double.NaN; e.trend=Double.NaN; e.delta15=Double.NaN; e.delta40=Double.NaN; e.iob=Double.NaN; e.cob=Double.NaN; e.prediction30=Double.NaN; e.plateauCount30=0; e.plateauAvg30=Double.NaN; e.dropFromMax30=Double.NaN; e.recentBolusMax20=Double.NaN; e.recentBolusSum20=Double.NaN; e.recentBolusCount20=0; e.siteAgeHours=Double.NaN; e.sensorAgeDays=Double.NaN; e.lastMealMin=-1;
    if(entries == null || entries.length() < 2) return e;
    JSONObject latest = entries.optJSONObject(0); double bg = latest.optDouble("sgv", Double.NaN); long latestDate = latest.optLong("date", 0L);
    if(Double.isNaN(bg) || latestDate <= 0 || System.currentTimeMillis()-latestDate > 20L*60L*1000L) { e.reason="BG ausente ou antigo no Nightscout."; return e; }
    JSONObject prev5 = findAround(entries, latestDate-5L*60L*1000L), prev15 = findAround(entries, latestDate-15L*60L*1000L), prev40 = findAround(entries, latestDate-40L*60L*1000L);
    double trend = prev5==null ? 0.0 : (bg-prev5.optDouble("sgv",bg))/Math.max(1.0,(latestDate-prev5.optLong("date",latestDate))/60000.0);
    double d15 = prev15==null ? Double.NaN : bg-prev15.optDouble("sgv",bg); double d40 = prev40==null ? Double.NaN : bg-prev40.optDouble("sgv",bg);
    double iob = findNumeric(devs,"iob",Double.NaN), cob = findNumeric(devs,"cob",0.0); double pred = bg + trend*30.0;
    e.bg=bg; e.trend=trend; e.delta15=d15; e.delta40=d40; e.iob=iob; e.cob=cob; e.prediction30=pred;
    boolean lowPrediction = pred < 75;
    boolean dropRisk = bg <= 115 && trend <= -1.5 && !Double.isNaN(iob) && iob >= 0.7 && cob <= 8;
    boolean risingConcern = bg >= Math.max(160, target+50) && trend >= 1.2;
    boolean stableSafe = bg >= 90 && bg <= 150 && pred >= 85 && pred <= 170 && Math.abs(trend) < 1.2;
    boolean predictionWorsening = pred >= bg + 20;
    boolean persistentHigh = bg >= 180 && pred >= 180 && trend >= 0;
    if(lowPrediction || dropRisk){ e.action="notify-attention"; e.title="Follow-up: atenção real"; e.reason="Risco de queda detectado no disparo do alarme: predição baixa ou queda com IOB ativo."; }
    else if(risingConcern){ e.action="notify-attention"; e.title="Follow-up: revisar subida"; e.reason="BG alto/subindo no disparo do alarme. Revisar contexto no AAPS, sem recomendação de bolus."; }
    else if(stableSafe){ e.action="suppress-preview"; e.title="Follow-up estável"; e.reason="Contexto estável. Silenciamento conservador ativo."; }
    else if(predictionWorsening || persistentHigh){ e.action="notify-normal"; e.title="Follow-up: atenção leve"; e.reason="Sem alerta forte, mas a previsão piorou ou segue alta. Reavaliação curta mantida."; }
    else { e.action="suppress"; e.title="Follow-up normal silenciado"; e.reason="Sem risco claro e sem piora relevante. Silenciar notificação e reagendar nova reavaliação."; }
    return e;
  }

  private static Eval applySplitAwareness(Eval e, double delayedDose, String splitStrategy, double target){
    if(e == null) return e;
    String action = e.action == null ? "" : e.action;
    if("limited".equals(action) || "chain-ended".equals(action)) return e;
    String split = splitStrategy == null ? "" : splitStrategy.trim();
    boolean hasSplit = delayedDose > 0.05 || split.length() > 0;
    if(!hasSplit) return e;

    double bg = e.bg;
    double pred = e.prediction30;
    double trend = e.trend;
    double iob = e.iob;
    boolean bgKnown = !Double.isNaN(bg);
    boolean predKnown = !Double.isNaN(pred);
    boolean trendKnown = !Double.isNaN(trend);
    boolean iobKnown = !Double.isNaN(iob);

    boolean fallingOrImproving = (predKnown && bgKnown && pred <= bg + 5.0) || (trendKnown && trend <= 0.2);
    boolean predictionWorsening = predKnown && bgKnown && pred >= bg + 20.0;
    boolean persistentHigh = bgKnown && predKnown && bg >= Math.max(170.0, target + 60.0) && pred >= Math.max(180.0, target + 70.0) && (!trendKnown || trend >= 0.0);
    boolean rising = trendKnown && trend >= 1.2;
    boolean lowOrDropRisk = (predKnown && pred < 80.0) || (bgKnown && bg <= 115.0 && trendKnown && trend <= -1.5 && iobKnown && iob >= 0.7);

    if(lowOrDropRisk){
      e.action = "notify-attention";
      e.title = "Split: segunda parte retida por segurança";
      e.reason = "Split previsto, mas a reavaliação mostra risco de queda ou predição baixa. Não automatizar segunda parte; revisar segurança.";
      return e;
    }
    if(predictionWorsening || persistentHigh || rising || "notify-attention".equals(action)){
      e.action = "notify-normal";
      e.title = "Split: atenção leve";
      e.reason = "Split previsto e contexto não está claramente resolvido. A lógica de split não foi alterada; revisar a segunda parte de forma contextual.";
      return e;
    }
    if(fallingOrImproving){
      e.action = "suppress";
      e.title = "Split reavaliado — segunda parte retida";
      e.reason = "Split previsto, mas o contexto melhorou ou a previsão não piorou. Notificação silenciada; registrar e manter reavaliação conforme limites.";
      return e;
    }
    e.action = "suppress";
    e.title = "Split reavaliado — segunda parte adiada";
    e.reason = "Split previsto, contexto intermediário. Notificação silenciada; reavaliação curta mantida sem alterar a lógica de split.";
    return e;
  }

  private static String notificationTitle(Eval e){ if("notify-attention".equals(e.action)) return "Gluco Context v2.32 Follow-up — atenção"; if("suppress-preview".equals(e.action)) return "Gluco Context v2.32 Follow-up — estável"; if("limited".equals(e.action)) return "Gluco Context v2.32 Follow-up — revisar"; if("chain-ended".equals(e.action)) return "Gluco Context v2.32 Follow-up — encerrado"; return "Gluco Context v2.32 Follow-up"; }
  private static String notificationBody(Eval e, String window){ String metrics=""; if(!Double.isNaN(e.bg)) metrics=" BG " + Math.round(e.bg) + " mg/dL, prev.30 " + Math.round(e.prediction30) + " mg/dL."; return e.title + ". " + e.reason + metrics + " Janela: " + window + "."; }
  private static void appendLog(SharedPreferences prefs, Eval e, long ts, String source, String title, String body, String chainId, int chainStep, long chainAgeMin, int maxAgeMin, int maxSteps, long scheduledFor){ try{ JSONArray old = new JSONArray(prefs.getString("followUpContextLog", "[]")); JSONArray next = new JSONArray(); JSONObject item = new JSONObject(); item.put("id", "fuf-native-"+ts); item.put("ts", ts); item.put("action", e.action); item.put("title", e.title); item.put("reason", e.reason); item.put("bg", Double.isNaN(e.bg)?JSONObject.NULL:e.bg); item.put("trend", Double.isNaN(e.trend)?JSONObject.NULL:e.trend); item.put("prediction30", Double.isNaN(e.prediction30)?JSONObject.NULL:e.prediction30); item.put("iob", Double.isNaN(e.iob)?JSONObject.NULL:e.iob); item.put("cob", Double.isNaN(e.cob)?JSONObject.NULL:e.cob); item.put("delta15", Double.isNaN(e.delta15)?JSONObject.NULL:e.delta15); item.put("delta40", Double.isNaN(e.delta40)?JSONObject.NULL:e.delta40); item.put("source", source); item.put("chainId", chainId==null?"":chainId); item.put("chainStep", chainStep); item.put("chainAgeMin", chainAgeMin); item.put("chainStatus", chainAgeMin>maxAgeMin || chainStep>maxSteps ? "encerrada" : (chainAgeMin>=180 ? "zona-final" : "ativa")); item.put("maxAgeMin", maxAgeMin); item.put("maxSteps", maxSteps); item.put("notificationTitle", title); item.put("notificationBody", body); if(scheduledFor > 0L) item.put("scheduledFor", scheduledFor); next.put(item); for(int i=0;i<old.length() && i<49;i++) next.put(old.opt(i)); prefs.edit().putString("followUpContextLog", next.toString()).putLong("followUpContextLogUpdatedAt", ts).apply(); }catch(Exception ignored){} }
  private static void notify(Context ctx, String title, String body){ NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE); if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"Gluco Context v2.32 Follow-up",NotificationManager.IMPORTANCE_DEFAULT); ch.setDescription("Alarmes cronometrados do Follow-up com filtro contextual."); nm.createNotificationChannel(ch);} NotificationCompat.Builder b=new NotificationCompat.Builder(ctx,CHANNEL_ID).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT); nm.notify((int)(System.currentTimeMillis()%2147483000), b.build()); }
  private static String httpGet(String url, String token) throws Exception { HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection(); conn.setConnectTimeout(12000); conn.setReadTimeout(12000); conn.setRequestMethod("GET"); if(token!=null && token.trim().length()>0) conn.setRequestProperty("Authorization","Bearer "+token.trim()); int code=conn.getResponseCode(); BufferedReader br=new BufferedReader(new InputStreamReader(code>=200&&code<300?conn.getInputStream():conn.getErrorStream())); StringBuilder sb=new StringBuilder(); String line; while((line=br.readLine())!=null) sb.append(line); if(code<200||code>=300) throw new Exception("HTTP "+code); return sb.toString(); }
  private static JSONArray getEntries(String base, String token, int count) throws Exception { String primary=base+"/api/v1/entries/sgv.json?count="+count; String fallback=base+"/api/v1/entries.json?count="+count; try{ return new JSONArray(httpGet(primary, token)); } catch(Exception first){ try{ return new JSONArray(httpGet(fallback, token)); } catch(Exception second){ throw new Exception("entries falhou: sgv="+first.getMessage()+"; entries.json="+second.getMessage()); } } }
  private static JSONObject findAround(JSONArray arr, long targetMs){ JSONObject best=null; long bestDist=Long.MAX_VALUE; for(int i=1;i<arr.length();i++){ JSONObject o=arr.optJSONObject(i); if(o==null) continue; long d=Math.abs(o.optLong("date",0L)-targetMs); if(d<bestDist){ best=o; bestDist=d; } } return best; }
  private static double findNumeric(Object obj, String key, double fallback){ if(obj==null)return fallback; try{ if(obj instanceof JSONArray){ JSONArray a=(JSONArray)obj; for(int i=0;i<a.length();i++){ double v=findNumeric(a.opt(i),key,Double.NaN); if(!Double.isNaN(v)) return v; } } if(obj instanceof JSONObject){ JSONObject o=(JSONObject)obj; if(o.has(key)){ double v=o.optDouble(key,Double.NaN); if(!Double.isNaN(v)&&v>=0&&v<500)return v; } java.util.Iterator<String> it=o.keys(); while(it.hasNext()){ double v=findNumeric(o.opt(it.next()),key,Double.NaN); if(!Double.isNaN(v)) return v; } } }catch(Exception ignored){} return fallback; }
}
