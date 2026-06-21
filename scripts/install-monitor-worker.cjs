const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..');
const javaDir = path.join(root, 'android', 'app', 'src', 'main', 'java', 'com', 'glucocontext', 'app');
const gradlePath = path.join(root, 'android', 'app', 'build.gradle');
if (!fs.existsSync(javaDir)) {
  console.error('Pasta Java Android não encontrada. Rode primeiro: npx cap add android');
  process.exit(1);
}
fs.mkdirSync(javaDir, { recursive: true });
fs.writeFileSync(path.join(javaDir, 'AapsMonitorWorkerPlugin.java'), String.raw`package com.glucocontext.app;

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
import androidx.core.content.FileProvider;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.TimeUnit;

@CapacitorPlugin(name = "AapsMonitorWorker")
public class AapsMonitorWorkerPlugin extends Plugin {
  public static final String PREFS = "aaps_monitor_worker";
  public static final String UNIQUE_WORK = "aaps_assist_monitor_work_v1";

  private void saveBaseConfig(Context ctx, String nsUrl, String nsToken, Double target, JSObject settings) {
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putBoolean("enabled", true)
      .putLong("activatedAt", System.currentTimeMillis())
      .putString("nsUrl", nsUrl == null ? "" : nsUrl.trim().replaceAll("/+$", ""))
      .putString("nsToken", nsToken == null ? "" : nsToken)
      .putFloat("target", target == null ? 100.0f : target.floatValue())
      .putInt("cooldownMin", 60)
      .putInt("cooldownDropMin", settings == null ? 20 : settings.optInt("cooldownDropMin", 20))
      .putInt("cooldownPersistentHighMin", settings == null ? 60 : settings.optInt("cooldownPersistentHighMin", 60))
      .putInt("cooldownReviewCorrectionMin", settings == null ? 90 : settings.optInt("cooldownReviewCorrectionMin", 90))
      .putInt("cooldownPostMealRiseMin", settings == null ? 20 : settings.optInt("cooldownPostMealRiseMin", 20))
      .putInt("cooldownMealWindowMin", settings == null ? 60 : settings.optInt("cooldownMealWindowMin", 60))
      .putInt("cooldownSensorSiteMin", settings == null ? 720 : settings.optInt("cooldownSensorSiteMin", 720))
      .putBoolean("quietEnabled", settings == null ? true : settings.optBoolean("quietEnabled", true))
      .putString("quietStart", settings == null ? "22:00" : settings.optString("quietStart", "22:00"))
      .putString("quietEnd", settings == null ? "06:00" : settings.optString("quietEnd", "06:00"))
      .putBoolean("notifyDrop", settings == null ? true : settings.optBoolean("notifyDrop", true))
      .putBoolean("notifyPersistentHigh", settings == null ? true : settings.optBoolean("notifyPersistentHigh", true))
      .putBoolean("notifyReviewCorrection", settings == null ? true : settings.optBoolean("notifyReviewCorrection", true))
      .putBoolean("notifyPostMealRise", settings == null ? true : settings.optBoolean("notifyPostMealRise", true))
      .putBoolean("notifyMealWindow", settings == null ? true : settings.optBoolean("notifyMealWindow", true))
      .putBoolean("notifySensorSite", settings == null ? true : settings.optBoolean("notifySensorSite", true))
      .putString("lastError", "")
      .apply();
  }

  private void enqueuePeriodic(Context ctx) {
    Constraints constraints = new Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build();
    PeriodicWorkRequest req = new PeriodicWorkRequest.Builder(AapsMonitorWorker.class, 15, TimeUnit.MINUTES)
      .setConstraints(constraints)
      .addTag("aaps-assist-monitor-periodic")
      .build();
    WorkManager.getInstance(ctx).enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, req);
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("workEnqueuedAt", System.currentTimeMillis()).apply();
  }

  @PluginMethod
  public void start(PluginCall call) {
    String nsUrl = call.getString("nsUrl", "");
    String nsToken = call.getString("nsToken", "");
    Double target = call.getDouble("target", 100.0);
    JSObject settings = call.getObject("settings", new JSObject());
    if (nsUrl == null || nsUrl.trim().isEmpty()) { call.reject("URL Nightscout ausente"); return; }
    Context ctx = getContext().getApplicationContext();
    saveBaseConfig(ctx, nsUrl, nsToken, target, settings);
    enqueuePeriodic(ctx);
    AapsMonitorWorker.runOneShot(ctx);
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("oneShotRequestedAt", System.currentTimeMillis()).apply();
    JSObject ret = buildStatus(ctx); ret.put("started", true); call.resolve(ret);
  }


  @PluginMethod
  public void configure(PluginCall call) {
    Context ctx = getContext().getApplicationContext();
    JSObject settings = call.getObject("settings", new JSObject());
    SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    prefs.edit()
      .putInt("cooldownDropMin", settings.optInt("cooldownDropMin", prefs.getInt("cooldownDropMin", 20)))
      .putInt("cooldownPersistentHighMin", settings.optInt("cooldownPersistentHighMin", prefs.getInt("cooldownPersistentHighMin", 60)))
      .putInt("cooldownReviewCorrectionMin", settings.optInt("cooldownReviewCorrectionMin", prefs.getInt("cooldownReviewCorrectionMin", 90)))
      .putInt("cooldownPostMealRiseMin", settings.optInt("cooldownPostMealRiseMin", prefs.getInt("cooldownPostMealRiseMin", 20)))
      .putInt("cooldownMealWindowMin", settings.optInt("cooldownMealWindowMin", prefs.getInt("cooldownMealWindowMin", 60)))
      .putInt("cooldownSensorSiteMin", settings.optInt("cooldownSensorSiteMin", prefs.getInt("cooldownSensorSiteMin", 720)))
      .putBoolean("quietEnabled", settings.optBoolean("quietEnabled", prefs.getBoolean("quietEnabled", true)))
      .putString("quietStart", settings.optString("quietStart", prefs.getString("quietStart", "22:00")))
      .putString("quietEnd", settings.optString("quietEnd", prefs.getString("quietEnd", "06:00")))
      .putBoolean("notifyDrop", settings.optBoolean("notifyDrop", prefs.getBoolean("notifyDrop", true)))
      .putBoolean("notifyPersistentHigh", settings.optBoolean("notifyPersistentHigh", prefs.getBoolean("notifyPersistentHigh", true)))
      .putBoolean("notifyReviewCorrection", settings.optBoolean("notifyReviewCorrection", prefs.getBoolean("notifyReviewCorrection", true)))
      .putBoolean("notifyPostMealRise", settings.optBoolean("notifyPostMealRise", prefs.getBoolean("notifyPostMealRise", true)))
      .putBoolean("notifyMealWindow", settings.optBoolean("notifyMealWindow", prefs.getBoolean("notifyMealWindow", true)))
      .putBoolean("notifySensorSite", settings.optBoolean("notifySensorSite", prefs.getBoolean("notifySensorSite", true)))
      .putLong("settingsUpdatedAt", System.currentTimeMillis())
      .apply();
    JSObject ret = buildStatus(ctx); ret.put("configured", true); call.resolve(ret);
  }

  @PluginMethod
  public void stop(PluginCall call) {
    Context ctx = getContext().getApplicationContext();
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putBoolean("enabled", false)
      .putLong("deactivatedAt", System.currentTimeMillis())
      .apply();
    WorkManager.getInstance(ctx).cancelUniqueWork(UNIQUE_WORK);
    JSObject ret = buildStatus(ctx); ret.put("stopped", true); call.resolve(ret);
  }

  @PluginMethod
  public void status(PluginCall call) {
    Context ctx = getContext().getApplicationContext();
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putLong("lastStatusReadAt", System.currentTimeMillis()).apply();
    call.resolve(buildStatus(ctx));
  }

  @PluginMethod
  public void runNow(PluginCall call) {
    Context ctx = getContext().getApplicationContext();
    SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    if (!prefs.getBoolean("enabled", false)) { call.reject("Monitor em segundo plano desativado"); return; }
    AapsMonitorWorker.runOneShot(ctx);
    prefs.edit().putLong("manualRunRequestedAt", System.currentTimeMillis()).apply();
    JSObject ret = buildStatus(ctx); ret.put("manualRunRequested", true); call.resolve(ret);
  }

  @PluginMethod
  public void scheduleFollowUpContextAlarm(PluginCall call) {
    Context ctx = getContext().getApplicationContext();
    String nsUrl = call.getString("nsUrl", "");
    String nsToken = call.getString("nsToken", "");
    Double target = call.getDouble("target", 100.0);
    Long targetAt = call.getLong("targetAt", System.currentTimeMillis() + 120000L);
    String decisionId = call.getString("decisionId", "");
    String window = call.getString("window", "follow-up");
    Boolean test = call.getBoolean("test", false);
    String chainId = call.getString("chainId", "");
    Integer chainStep = call.getInt("chainStep", 1);
    Long mealStartAt = call.getLong("mealStartAt", System.currentTimeMillis());
    Integer maxAgeMin = call.getInt("maxAgeMin", 240);
    Integer maxSteps = call.getInt("maxSteps", 5);
    Boolean silentMode = call.getBoolean("silentMode", false);
    Boolean autoReschedule = call.getBoolean("autoReschedule", false);
    Double delayedDose = call.getDouble("delayedDose", 0.0);
    String splitStrategy = call.getString("splitStrategy", "");
    if (targetAt == null || targetAt < System.currentTimeMillis() + 1000L) targetAt = System.currentTimeMillis() + 5000L;
    if (nsUrl == null || nsUrl.trim().isEmpty()) { call.reject("URL Nightscout ausente para filtro contextual"); return; }
    try {
      ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putString("followUpNsUrl", nsUrl == null ? "" : nsUrl.trim().replaceAll("/+$", ""))
        .putString("followUpNsToken", nsToken == null ? "" : nsToken)
        .putFloat("followUpTarget", target == null ? 100.0f : target.floatValue())
        .apply();
      SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
      try {
        int oldId = prefs.getInt("lastFollowUpContextPendingId", -1);
        if (oldId > 0) {
          Intent oldIntent = new Intent(ctx, AapsFollowUpContextReceiver.class);
          oldIntent.setAction(AapsFollowUpContextReceiver.ACTION);
          int oldFlags = PendingIntent.FLAG_NO_CREATE;
          if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) oldFlags |= PendingIntent.FLAG_IMMUTABLE;
          PendingIntent oldPi = PendingIntent.getBroadcast(ctx, oldId, oldIntent, oldFlags);
          if (oldPi != null) {
            AlarmManager oldAm = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
            oldAm.cancel(oldPi);
            oldPi.cancel();
          }
        }
      } catch (Exception ignored) {}
      int id = (int)(System.currentTimeMillis() % 2147480000L);
      Intent intent = new Intent(ctx, AapsFollowUpContextReceiver.class);
      intent.setAction(AapsFollowUpContextReceiver.ACTION);
      intent.putExtra("id", id);
      intent.putExtra("nsUrl", nsUrl == null ? "" : nsUrl.trim().replaceAll("/+$", ""));
      intent.putExtra("nsToken", nsToken == null ? "" : nsToken);
      intent.putExtra("target", target == null ? 100.0 : target.doubleValue());
      intent.putExtra("targetAt", targetAt.longValue());
      intent.putExtra("decisionId", decisionId == null ? "" : decisionId);
      intent.putExtra("window", window == null ? "follow-up" : window);
      intent.putExtra("test", test != null && test.booleanValue());
      intent.putExtra("chainId", chainId == null ? "" : chainId);
      intent.putExtra("chainStep", chainStep == null ? 1 : chainStep.intValue());
      intent.putExtra("mealStartAt", mealStartAt == null ? System.currentTimeMillis() : mealStartAt.longValue());
      intent.putExtra("maxAgeMin", maxAgeMin == null ? 240 : maxAgeMin.intValue());
      intent.putExtra("maxSteps", maxSteps == null ? 5 : maxSteps.intValue());
      intent.putExtra("silentMode", silentMode != null && silentMode.booleanValue());
      intent.putExtra("autoReschedule", autoReschedule != null && autoReschedule.booleanValue());
      intent.putExtra("delayedDose", delayedDose == null ? 0.0 : delayedDose.doubleValue());
      intent.putExtra("splitStrategy", splitStrategy == null ? "" : splitStrategy);
      int flags = PendingIntent.FLAG_UPDATE_CURRENT;
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) flags |= PendingIntent.FLAG_IMMUTABLE;
      PendingIntent pi = PendingIntent.getBroadcast(ctx, id, intent, flags);
      AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
      if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, targetAt.longValue(), pi);
      else am.setExact(AlarmManager.RTC_WAKEUP, targetAt.longValue(), pi);
      ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        .putLong("lastFollowUpContextScheduledAt", System.currentTimeMillis())
        .putLong("lastFollowUpContextTargetAt", targetAt.longValue())
        .putInt("lastFollowUpContextPendingId", id)
        .putString("followUpChainId", chainId == null ? "" : chainId)
        .putInt("followUpChainStep", chainStep == null ? 1 : chainStep.intValue())
        .putLong("followUpChainMealStartAt", mealStartAt == null ? System.currentTimeMillis() : mealStartAt.longValue())
        .putInt("followUpChainMaxAgeMin", maxAgeMin == null ? 240 : maxAgeMin.intValue())
        .putInt("followUpChainMaxSteps", maxSteps == null ? 5 : maxSteps.intValue())
        .putString("lastFollowUpContextWindow", window == null ? "follow-up" : window)
        .putBoolean("followUpSilentMode", silentMode != null && silentMode.booleanValue())
        .putBoolean("followUpAutoReschedule", autoReschedule != null && autoReschedule.booleanValue())
        .putFloat("followUpDelayedDose", delayedDose == null ? 0.0f : delayedDose.floatValue())
        .putString("followUpSplitStrategy", splitStrategy == null ? "" : splitStrategy)
        .putString("lastError", "")
        .apply();
      JSObject ret = buildStatus(ctx);
      ret.put("scheduled", true); ret.put("id", id); ret.put("targetAt", targetAt.longValue()); ret.put("chainId", chainId); ret.put("chainStep", chainStep);
      call.resolve(ret);
    } catch (SecurityException se) { call.reject("Permissão de alarme exato ausente: " + se.getMessage()); }
      catch (Exception e) { call.reject(e.getMessage() == null ? e.toString() : e.getMessage()); }
  }


  @PluginMethod
  public void saveBackupToDownloads(PluginCall call) {
    String fileName = call.getString("fileName", "aaps-assist-backup.json");
    String content = call.getString("content", "");
    if (fileName == null || fileName.trim().isEmpty()) fileName = "aaps-assist-backup.json";
    fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    if (!fileName.toLowerCase().endsWith(".json")) fileName = fileName + ".json";
    if (content == null || content.trim().isEmpty()) { call.reject("conteúdo do backup ausente"); return; }
    try {
      Context ctx = getContext().getApplicationContext();
      String displayPath = "Downloads/GlucoContext/" + fileName;
      if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        ContentResolver resolver = ctx.getContentResolver();
        ContentValues values = new ContentValues();
        values.put(MediaStore.Downloads.DISPLAY_NAME, fileName);
        values.put(MediaStore.Downloads.MIME_TYPE, "application/json");
        values.put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/GlucoContext");
        values.put(MediaStore.Downloads.IS_PENDING, 1);
        Uri uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values);
        if (uri == null) { call.reject("não foi possível criar arquivo em Downloads"); return; }
        try (OutputStream out = resolver.openOutputStream(uri, "w")) {
          if (out == null) { call.reject("não foi possível abrir arquivo em Downloads"); return; }
          out.write(content.getBytes(StandardCharsets.UTF_8));
          out.flush();
        }
        ContentValues done = new ContentValues();
        done.put(MediaStore.Downloads.IS_PENDING, 0);
        resolver.update(uri, done, null, null);
        JSObject ret = new JSObject();
        ret.put("saved", true);
        ret.put("displayName", fileName);
        ret.put("path", displayPath);
        ret.put("uri", uri.toString());
        call.resolve(ret);
      } else {
        File downloads = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS);
        if (!downloads.exists()) downloads.mkdirs();
        File folder = new File(downloads, "GlucoContext");
        if (!folder.exists()) folder.mkdirs();
        File outFile = new File(folder, fileName);
        try (FileOutputStream out = new FileOutputStream(outFile)) {
          out.write(content.getBytes(StandardCharsets.UTF_8));
          out.flush();
        }
        JSObject ret = new JSObject();
        ret.put("saved", true);
        ret.put("displayName", fileName);
        ret.put("path", outFile.getAbsolutePath());
        call.resolve(ret);
      }
    } catch (Exception e) {
      call.reject(e.getMessage() == null ? e.toString() : e.getMessage());
    }
  }

  @PluginMethod
  public void shareBackupJson(PluginCall call) {
    String fileName = call.getString("fileName", "GlucoContext_backup.json");
    String content = call.getString("content", "");
    if (fileName == null || fileName.trim().isEmpty()) fileName = "GlucoContext_backup.json";
    fileName = fileName.replaceAll("[^a-zA-Z0-9._-]", "_");
    if (!fileName.toLowerCase().endsWith(".json")) fileName = fileName + ".json";
    if (content == null || content.trim().isEmpty()) { call.reject("conteúdo do backup ausente"); return; }
    try {
      Context ctx = getContext().getApplicationContext();
      File dir = new File(ctx.getCacheDir(), "backup-share");
      if (!dir.exists()) dir.mkdirs();
      File outFile = new File(dir, fileName);
      try (FileOutputStream out = new FileOutputStream(outFile)) {
        out.write(content.getBytes(StandardCharsets.UTF_8));
        out.flush();
      }
      Uri uri = FileProvider.getUriForFile(ctx, ctx.getPackageName() + ".fileprovider", outFile);
      Intent sendIntent = new Intent(Intent.ACTION_SEND);
      sendIntent.setType("application/json");
      sendIntent.putExtra(Intent.EXTRA_SUBJECT, fileName);
      sendIntent.putExtra(Intent.EXTRA_TITLE, fileName);
      sendIntent.putExtra(Intent.EXTRA_TEXT, "Backup local do AAPS Assist 2.6. Não misturar com AAPS/export.");
      sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
      sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      Intent chooser = Intent.createChooser(sendIntent, "Salvar/compartilhar backup AAPS Assist 2.6");
      chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      ctx.startActivity(chooser);
      JSObject ret = new JSObject();
      ret.put("shared", true);
      ret.put("displayName", fileName);
      ret.put("uri", uri.toString());
      call.resolve(ret);
    } catch (Exception e) {
      call.reject(e.getMessage() == null ? e.toString() : e.getMessage());
    }
  }

  @PluginMethod
  public void clearFollowUpContextLog(PluginCall call) {
    Context ctx = getContext().getApplicationContext();
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putString("followUpContextLog", "[]")
      .putLong("followUpContextLogUpdatedAt", System.currentTimeMillis())
      .apply();
    JSObject ret = buildStatus(ctx); ret.put("followUpContextLogCleared", true); call.resolve(ret);
  }

  @PluginMethod
  public void clearNotificationLog(PluginCall call) {
    Context ctx = getContext().getApplicationContext();
    ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
      .putString("notificationLog", "[]")
      .putLong("notificationLogUpdatedAt", System.currentTimeMillis())
      .apply();
    JSObject ret = buildStatus(ctx); ret.put("notificationLogCleared", true); call.resolve(ret);
  }

  @PluginMethod
  public void openAppSettings(PluginCall call) {
    try {
      Context ctx = getContext().getApplicationContext();
      Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
      intent.setData(Uri.parse("package:" + ctx.getPackageName()));
      intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
      ctx.startActivity(intent);
      JSObject ret = new JSObject();
      ret.put("opened", true);
      call.resolve(ret);
    } catch (Exception e) {
      call.reject(e.getMessage() == null ? e.toString() : e.getMessage());
    }
  }

  private JSObject buildStatus(Context ctx) {
    SharedPreferences prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    JSObject ret = new JSObject();
    ret.put("enabled", prefs.getBoolean("enabled", false));
    ret.put("activatedAt", prefs.getLong("activatedAt", 0L));
    ret.put("deactivatedAt", prefs.getLong("deactivatedAt", 0L));
    ret.put("workEnqueuedAt", prefs.getLong("workEnqueuedAt", 0L));
    ret.put("oneShotRequestedAt", prefs.getLong("oneShotRequestedAt", 0L));
    ret.put("manualRunRequestedAt", prefs.getLong("manualRunRequestedAt", 0L));
    ret.put("lastStatusReadAt", prefs.getLong("lastStatusReadAt", 0L));
    ret.put("lastRunAt", prefs.getLong("lastRunAt", 0L));
    ret.put("lastRunStartedAt", prefs.getLong("lastRunStartedAt", 0L));
    ret.put("lastRunFinishedAt", prefs.getLong("lastRunFinishedAt", 0L));
    ret.put("lastNotifyAt", prefs.getLong("lastNotifyAt", 0L));
    ret.put("notificationLog", prefs.getString("notificationLog", "[]"));
    ret.put("notificationLogUpdatedAt", prefs.getLong("notificationLogUpdatedAt", 0L));
    ret.put("followUpContextLog", prefs.getString("followUpContextLog", "[]"));
    ret.put("followUpContextLogUpdatedAt", prefs.getLong("followUpContextLogUpdatedAt", 0L));
    ret.put("lastFollowUpContextAlarmAt", prefs.getLong("lastFollowUpContextAlarmAt", 0L));
    ret.put("lastFollowUpContextScheduledAt", prefs.getLong("lastFollowUpContextScheduledAt", 0L));
    ret.put("lastFollowUpContextTargetAt", prefs.getLong("lastFollowUpContextTargetAt", 0L));
    ret.put("lastFollowUpContextPendingId", prefs.getInt("lastFollowUpContextPendingId", -1));
    long contextTargetAt = prefs.getLong("lastFollowUpContextTargetAt", 0L);
    long contextAlarmAt = prefs.getLong("lastFollowUpContextAlarmAt", 0L);
    int contextPendingId = prefs.getInt("lastFollowUpContextPendingId", -1);
    ret.put("followUpContextPending", contextPendingId > 0 && contextTargetAt > System.currentTimeMillis() + 1000L && contextAlarmAt < contextTargetAt);
    ret.put("followUpChainId", prefs.getString("followUpChainId", ""));
    ret.put("followUpChainStep", prefs.getInt("followUpChainStep", 1));
    ret.put("followUpChainMealStartAt", prefs.getLong("followUpChainMealStartAt", 0L));
    ret.put("lastFollowUpContextResult", prefs.getString("lastFollowUpContextResult", ""));
    ret.put("lastFollowUpSchedulerState", prefs.getString("lastFollowUpSchedulerState", ""));
    ret.put("lastFollowUpSchedulerReason", prefs.getString("lastFollowUpSchedulerReason", ""));
    ret.put("followUpSilentMode", prefs.getBoolean("followUpSilentMode", false));
    ret.put("followUpAutoReschedule", prefs.getBoolean("followUpAutoReschedule", false));
    ret.put("lastResult", prefs.getString("lastResult", ""));
    ret.put("lastError", prefs.getString("lastError", ""));
    ret.put("lastAlertId", prefs.getString("lastAlertId", ""));
    ret.put("lastDeliveryStatus", prefs.getString("lastDeliveryStatus", ""));
    ret.put("lastDeliveryAlertId", prefs.getString("lastDeliveryAlertId", ""));
    ret.put("lastDeliveryAt", prefs.getLong("lastDeliveryAt", 0L));
    ret.put("cooldownMin", prefs.getInt("cooldownMin", 60));
    ret.put("cooldownDropMin", prefs.getInt("cooldownDropMin", 20));
    ret.put("cooldownPersistentHighMin", prefs.getInt("cooldownPersistentHighMin", 60));
    ret.put("cooldownReviewCorrectionMin", prefs.getInt("cooldownReviewCorrectionMin", 90));
    ret.put("cooldownPostMealRiseMin", prefs.getInt("cooldownPostMealRiseMin", 20));
    ret.put("cooldownMealWindowMin", prefs.getInt("cooldownMealWindowMin", 60));
    ret.put("cooldownSensorSiteMin", prefs.getInt("cooldownSensorSiteMin", 720));
    ret.put("quietEnabled", prefs.getBoolean("quietEnabled", true));
    ret.put("quietStart", prefs.getString("quietStart", "22:00"));
    ret.put("quietEnd", prefs.getString("quietEnd", "06:00"));
    ret.put("notifyDrop", prefs.getBoolean("notifyDrop", true));
    ret.put("notifyPersistentHigh", prefs.getBoolean("notifyPersistentHigh", true));
    ret.put("notifyReviewCorrection", prefs.getBoolean("notifyReviewCorrection", true));
    ret.put("notifyPostMealRise", prefs.getBoolean("notifyPostMealRise", true));
    ret.put("notifyMealWindow", prefs.getBoolean("notifyMealWindow", true));
    ret.put("notifySensorSite", prefs.getBoolean("notifySensorSite", true));
    ret.put("settingsUpdatedAt", prefs.getLong("settingsUpdatedAt", 0L));
    ret.put("lastNotifyAtDrop", prefs.getLong("lastNotifyAt_drop-risk-iob", 0L));
    ret.put("lastNotifyBgDrop", prefs.getFloat("lastNotifyBg_drop-risk-iob", -1f));
    ret.put("lastNotifyAtPersistentHigh", prefs.getLong("lastNotifyAt_persistent-high-with-iob", 0L));
    ret.put("lastNotifyBgPersistentHigh", prefs.getFloat("lastNotifyBg_persistent-high-with-iob", -1f));
    ret.put("lastNotifyAtReviewCorrection", prefs.getLong("lastNotifyAt_review-correction-context", 0L));
    ret.put("lastNotifyBgReviewCorrection", prefs.getFloat("lastNotifyBg_review-correction-context", -1f));
    ret.put("lastNotifyAtPostMealRise", prefs.getLong("lastNotifyAt_rapid-rise-post-meal", 0L));
    ret.put("lastNotifyBgPostMealRise", prefs.getFloat("lastNotifyBg_rapid-rise-post-meal", -1f));
    ret.put("lastNotifyAtMealWindow", prefs.getLong("lastNotifyAt_meal-window-2-4h-review", 0L));
    ret.put("lastNotifyBgMealWindow", prefs.getFloat("lastNotifyBg_meal-window-2-4h-review", -1f));
    ret.put("lastNotifyAtSensorSite", prefs.getLong("lastNotifyAt_sensor-site-attention", 0L));
    ret.put("lastNotifyBgSensorSite", prefs.getFloat("lastNotifyBg_sensor-site-attention", -1f));
    ret.put("nsConfigured", prefs.getString("nsUrl", "").length() > 0);
    try {
      List<WorkInfo> infos = WorkManager.getInstance(ctx).getWorkInfosForUniqueWork(UNIQUE_WORK).get(1500, TimeUnit.MILLISECONDS);
      ret.put("workInfoCount", infos == null ? 0 : infos.size());
      if (infos != null && infos.size() > 0) {
        WorkInfo wi = infos.get(0);
        ret.put("workState", wi.getState().toString());
        ret.put("workTags", wi.getTags().toString());
      } else {
        ret.put("workState", "NONE");
      }
    } catch (Exception e) {
      ret.put("workState", "UNKNOWN");
      ret.put("workQueryError", e.getMessage() == null ? e.toString() : e.getMessage());
    }
    return ret;
  }
}
`);
fs.writeFileSync(path.join(javaDir, 'AapsMonitorWorker.java'), String.raw`package com.glucocontext.app;

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
    if (entries == null || entries.length() < 2) { e.title="Monitor limitado"; e.body="BG insuficiente no Nightscout."; return e; }
    JSONObject latest = entries.optJSONObject(0); double bg = latest.optDouble("sgv", Double.NaN); long latestDate = latest.optLong("date", 0L);
    if (Double.isNaN(bg) || latestDate <= 0 || System.currentTimeMillis() - latestDate > 20L*60L*1000L) { e.title="Monitor limitado"; e.body="BG ausente ou antigo."; return e; }
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
`);
fs.writeFileSync(path.join(javaDir, 'AapsFollowUpContextReceiver.java'), String.raw`package com.glucocontext.app;

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
    String title = test ? "AAPS Assist 2.6 Follow-up — teste contextual" : notificationTitle(eval);
    String body = notificationBody(eval, window);
    if(shouldSilence){
      String previousTitle = eval.title;
      eval.action = "silenced";
      eval.title = previousTitle != null && previousTitle.indexOf("normal") >= 0 ? "Follow-up normal silenciado" : "Follow-up silenciado";
      eval.reason = previousTitle != null && previousTitle.indexOf("normal") >= 0 ? "Sem risco claro e sem piora relevante. Notificação silenciada; nova reavaliação será agendada se ainda dentro dos limites." : "Contexto estável no disparo. Silenciamento conservador ativo; nenhuma notificação foi emitida.";
      title = "AAPS Assist 2.6 Follow-up — silenciado";
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
          appendLog(prefs, res, firedAt+1L, "alarm-native", "AAPS Assist 2.6 Follow-up — autoagendado", res.reason, chainId, chainStep + 1, nextAge, maxAgeMin, maxSteps, nextAt);
          prefs.edit().putString("lastFollowUpContextResult", eval.title + " · " + eval.action + " · scheduler=reagendado · nextAt=" + nextAt).apply();
        } else {
          Eval terminal = new Eval(); terminal.action="chain-ended"; terminal.bg=eval.bg; terminal.trend=eval.trend; terminal.delta15=eval.delta15; terminal.delta40=eval.delta40; terminal.iob=eval.iob; terminal.cob=eval.cob; terminal.prediction30=eval.prediction30;
          if(alreadyClosed){ terminal.title="Follow-up encerrado"; terminal.reason="Estado final do scheduler: encerrado. Motivo: limite de tempo/etapas já atingido; nenhum novo alarme pendente."; }
          else if(stable){ terminal.title="Follow-up encerrado por estabilidade"; terminal.reason="Estado final do scheduler: encerrado. Motivo: contexto estável; nenhum novo alarme pendente. Inicie novo Follow-up se houver nova refeição, correção ou mudança clínica."; }
          else if(!withinSteps || !withinAge){ terminal.title="Follow-up encerrado por limite"; terminal.reason="Estado final do scheduler: encerrado. Motivo: limite de etapas ou janela máxima atingido; nenhum novo alarme pendente."; }
          else { terminal.title="Follow-up encerrado sem reagendamento"; terminal.reason="Estado final do scheduler: encerrado. Motivo: classificação não elegível para auto-reagendamento; nenhum novo alarme pendente."; }
          prefs.edit().putInt("lastFollowUpContextPendingId", -1).putString("lastFollowUpSchedulerState", "encerrado").putString("lastFollowUpSchedulerReason", terminal.reason).putString("lastFollowUpContextResult", eval.title + " · " + eval.action + " · scheduler=encerrado").apply();
          appendLog(prefs, terminal, firedAt+1L, "alarm-native", "AAPS Assist 2.6 Follow-up — encerrado", terminal.reason, chainId, chainStep, chainAgeMin, maxAgeMin, maxSteps, 0L);
        }
      } catch(Exception e) {
        prefs.edit().putString("lastError", "Falha ao finalizar scheduler do Follow-up: " + (e.getMessage()==null?e.toString():e.getMessage())).apply();
      }
    } else if(!test) {
      Eval terminal = new Eval(); terminal.action="chain-ended"; terminal.title="Follow-up sem auto-reagendamento"; terminal.reason="Estado final do scheduler: encerrado. Reagendamento automático desligado; nenhum novo alarme pendente. Use Recriar alarme se quiser continuar a cadeia."; terminal.bg=eval.bg; terminal.trend=eval.trend; terminal.delta15=eval.delta15; terminal.delta40=eval.delta40; terminal.iob=eval.iob; terminal.cob=eval.cob; terminal.prediction30=eval.prediction30;
      prefs.edit().putInt("lastFollowUpContextPendingId", -1).putString("lastFollowUpSchedulerState", "encerrado").putString("lastFollowUpSchedulerReason", terminal.reason).putString("lastFollowUpContextResult", eval.title + " · " + eval.action + " · scheduler=encerrado(auto desligado)").apply();
      appendLog(prefs, terminal, firedAt+1L, "alarm-native", "AAPS Assist 2.6 Follow-up — sem auto-reagendamento", terminal.reason, chainId, chainStep, chainAgeMin, maxAgeMin, maxSteps, 0L);
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

  private static String notificationTitle(Eval e){ if("notify-attention".equals(e.action)) return "AAPS Assist 2.6 Follow-up — atenção"; if("suppress-preview".equals(e.action)) return "AAPS Assist 2.6 Follow-up — estável"; if("limited".equals(e.action)) return "AAPS Assist 2.6 Follow-up — revisar"; if("chain-ended".equals(e.action)) return "AAPS Assist 2.6 Follow-up — encerrado"; return "AAPS Assist 2.6 Follow-up"; }
  private static String notificationBody(Eval e, String window){ String metrics=""; if(!Double.isNaN(e.bg)) metrics=" BG " + Math.round(e.bg) + " mg/dL, prev.30 " + Math.round(e.prediction30) + " mg/dL."; return e.title + ". " + e.reason + metrics + " Janela: " + window + "."; }
  private static void appendLog(SharedPreferences prefs, Eval e, long ts, String source, String title, String body, String chainId, int chainStep, long chainAgeMin, int maxAgeMin, int maxSteps, long scheduledFor){ try{ JSONArray old = new JSONArray(prefs.getString("followUpContextLog", "[]")); JSONArray next = new JSONArray(); JSONObject item = new JSONObject(); item.put("id", "fuf-native-"+ts); item.put("ts", ts); item.put("action", e.action); item.put("title", e.title); item.put("reason", e.reason); item.put("bg", Double.isNaN(e.bg)?JSONObject.NULL:e.bg); item.put("trend", Double.isNaN(e.trend)?JSONObject.NULL:e.trend); item.put("prediction30", Double.isNaN(e.prediction30)?JSONObject.NULL:e.prediction30); item.put("iob", Double.isNaN(e.iob)?JSONObject.NULL:e.iob); item.put("cob", Double.isNaN(e.cob)?JSONObject.NULL:e.cob); item.put("delta15", Double.isNaN(e.delta15)?JSONObject.NULL:e.delta15); item.put("delta40", Double.isNaN(e.delta40)?JSONObject.NULL:e.delta40); item.put("source", source); item.put("chainId", chainId==null?"":chainId); item.put("chainStep", chainStep); item.put("chainAgeMin", chainAgeMin); item.put("chainStatus", chainAgeMin>maxAgeMin || chainStep>maxSteps ? "encerrada" : (chainAgeMin>=180 ? "zona-final" : "ativa")); item.put("maxAgeMin", maxAgeMin); item.put("maxSteps", maxSteps); item.put("notificationTitle", title); item.put("notificationBody", body); if(scheduledFor > 0L) item.put("scheduledFor", scheduledFor); next.put(item); for(int i=0;i<old.length() && i<49;i++) next.put(old.opt(i)); prefs.edit().putString("followUpContextLog", next.toString()).putLong("followUpContextLogUpdatedAt", ts).apply(); }catch(Exception ignored){} }
  private static void notify(Context ctx, String title, String body){ NotificationManager nm=(NotificationManager)ctx.getSystemService(Context.NOTIFICATION_SERVICE); if(Build.VERSION.SDK_INT>=Build.VERSION_CODES.O){NotificationChannel ch=new NotificationChannel(CHANNEL_ID,"AAPS Assist 2.6 Follow-up",NotificationManager.IMPORTANCE_DEFAULT); ch.setDescription("Alarmes cronometrados do Follow-up com filtro contextual."); nm.createNotificationChannel(ch);} NotificationCompat.Builder b=new NotificationCompat.Builder(ctx,CHANNEL_ID).setSmallIcon(android.R.drawable.ic_dialog_info).setContentTitle(title).setContentText(body).setStyle(new NotificationCompat.BigTextStyle().bigText(body)).setAutoCancel(true).setPriority(NotificationCompat.PRIORITY_DEFAULT); nm.notify((int)(System.currentTimeMillis()%2147483000), b.build()); }
  private static String httpGet(String url, String token) throws Exception { HttpURLConnection conn=(HttpURLConnection)new URL(url).openConnection(); conn.setConnectTimeout(12000); conn.setReadTimeout(12000); conn.setRequestMethod("GET"); if(token!=null && token.trim().length()>0) conn.setRequestProperty("Authorization","Bearer "+token.trim()); int code=conn.getResponseCode(); BufferedReader br=new BufferedReader(new InputStreamReader(code>=200&&code<300?conn.getInputStream():conn.getErrorStream())); StringBuilder sb=new StringBuilder(); String line; while((line=br.readLine())!=null) sb.append(line); if(code<200||code>=300) throw new Exception("HTTP "+code); return sb.toString(); }
  private static JSONArray getEntries(String base, String token, int count) throws Exception { String primary=base+"/api/v1/entries/sgv.json?count="+count; String fallback=base+"/api/v1/entries.json?count="+count; try{ return new JSONArray(httpGet(primary, token)); } catch(Exception first){ try{ return new JSONArray(httpGet(fallback, token)); } catch(Exception second){ throw new Exception("entries falhou: sgv="+first.getMessage()+"; entries.json="+second.getMessage()); } } }
  private static JSONObject findAround(JSONArray arr, long targetMs){ JSONObject best=null; long bestDist=Long.MAX_VALUE; for(int i=1;i<arr.length();i++){ JSONObject o=arr.optJSONObject(i); if(o==null) continue; long d=Math.abs(o.optLong("date",0L)-targetMs); if(d<bestDist){ best=o; bestDist=d; } } return best; }
  private static double findNumeric(Object obj, String key, double fallback){ if(obj==null)return fallback; try{ if(obj instanceof JSONArray){ JSONArray a=(JSONArray)obj; for(int i=0;i<a.length();i++){ double v=findNumeric(a.opt(i),key,Double.NaN); if(!Double.isNaN(v)) return v; } } if(obj instanceof JSONObject){ JSONObject o=(JSONObject)obj; if(o.has(key)){ double v=o.optDouble(key,Double.NaN); if(!Double.isNaN(v)&&v>=0&&v<500)return v; } java.util.Iterator<String> it=o.keys(); while(it.hasNext()){ double v=findNumeric(o.opt(it.next()),key,Double.NaN); if(!Double.isNaN(v)) return v; } } }catch(Exception ignored){} return fallback; }
}
`);
fs.writeFileSync(path.join(javaDir, 'MainActivity.java'), String.raw`package com.glucocontext.app;

import android.content.Intent;
import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(AapsMonitorWorkerPlugin.class);
        registerPlugin(AapsShareIntakePlugin.class);
        registerPlugin(AapsOcrPlugin.class);
        registerPlugin(AapsSmartWidgetPlugin.class);
        super.onCreate(savedInstanceState);
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
    }
}
`);
if (fs.existsSync(gradlePath)) {
  let gradle = fs.readFileSync(gradlePath, 'utf8');
  let changed = false;
  if (!gradle.includes('androidx.work:work-runtime')) {
    gradle = gradle.replace(/dependencies\s*\{/, 'dependencies {\n    implementation "androidx.work:work-runtime:2.9.1"');
    changed = true;
  }
  if (!gradle.includes('com.google.mlkit:text-recognition')) {
    gradle = gradle.replace(/dependencies\s*\{/, 'dependencies {\n    implementation "com.google.mlkit:text-recognition:16.0.1"');
    changed = true;
  }
  if (changed) fs.writeFileSync(gradlePath, gradle);
}
console.log('WorkManager de Alertas instalado + Follow-up contextual com silenciamento conservador opcional + logs separados.');
