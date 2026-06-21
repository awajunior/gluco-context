package com.glucocontext.app;

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
      sendIntent.putExtra(Intent.EXTRA_TEXT, "Backup local do Gluco Context v2.32. Não misturar com AAPS/export.");
      sendIntent.putExtra(Intent.EXTRA_STREAM, uri);
      sendIntent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
      Intent chooser = Intent.createChooser(sendIntent, "Salvar/compartilhar backup Gluco Context v2.32");
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
