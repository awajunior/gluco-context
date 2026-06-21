package com.glucocontext.app;

import android.content.Context;
import android.content.SharedPreferences;
import com.getcapacitor.JSArray;
import com.getcapacitor.JSObject;
import com.getcapacitor.Plugin;
import com.getcapacitor.PluginCall;
import com.getcapacitor.PluginMethod;
import com.getcapacitor.annotation.CapacitorPlugin;
import org.json.JSONArray;

@CapacitorPlugin(name = "AapsSmartWidget")
public class AapsSmartWidgetPlugin extends Plugin {
  @PluginMethod
  public void saveSnapshot(PluginCall call) {
    Context ctx = getContext().getApplicationContext();
    SharedPreferences.Editor e = ctx.getSharedPreferences(AapsSmartWidgetProvider.PREFS, Context.MODE_PRIVATE).edit();
    String nsUrl = call.getString("nsUrl", "");
    String nsToken = call.getString("nsToken", "");
    String bg = call.getString("bg", "");
    String unit = call.getString("unit", "mg/dL");
    String trend = call.getString("trend", "");
    String delta = call.getString("delta", "");
    String iob = call.getString("iob", "");
    String cgmAge = call.getString("cgmAge", "");
    String status = call.getString("status", "");
    String historyText = call.getString("historyText", "");
    String historyCsv = call.getString("historyCsv", "");
    String prediction30 = call.getString("prediction30", "");
    String targetBg = call.getString("targetBg", "100");
    String source = call.getString("source", "App");
    Boolean cgmOk = call.getBoolean("cgmOk", false);
    e.putString("nsUrl", nsUrl == null ? "" : nsUrl.trim().replaceAll("/+$", ""));
    e.putString("nsToken", nsToken == null ? "" : nsToken);
    e.putString("bg", empty(bg) ? "—" : bg);
    e.putString("unit", empty(unit) ? "mg/dL" : unit);
    e.putString("trend", trend == null ? "" : trend);
    e.putString("delta", delta == null ? "" : delta);
    e.putString("iob", empty(iob) ? "—" : iob);
    e.putString("cgmAge", empty(cgmAge) ? "CGM —" : cgmAge);
    e.putString("status", empty(status) ? "Sem dado" : status);
    e.putString("historyText", empty(historyText) ? "—" : historyText);
    e.putString("historyCsv", historyCsv == null ? "" : historyCsv);
    e.putString("prediction30", prediction30 == null ? "" : prediction30);
    e.putString("targetBg", empty(targetBg) ? "100" : targetBg);
    e.putString("source", source == null ? "App" : source);
    e.putBoolean("cgmOk", cgmOk != null && cgmOk);
    e.putBoolean("syncing", false);
    e.putLong("updatedAt", System.currentTimeMillis());
    e.apply();
    AapsSmartWidgetProvider.updateAll(ctx);
    JSObject ret = new JSObject();
    ret.put("saved", true);
    call.resolve(ret);
  }

  @PluginMethod
  public void refresh(PluginCall call) {
    AapsSmartWidgetProvider.updateAll(getContext().getApplicationContext());
    JSObject ret = new JSObject(); ret.put("refreshed", true); call.resolve(ret);
  }

  private static boolean empty(String v) { return v == null || v.trim().isEmpty(); }
}
