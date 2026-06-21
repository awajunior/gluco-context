package com.glucocontext.app

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.SystemClock
import com.getcapacitor.JSObject
import com.getcapacitor.Plugin
import com.getcapacitor.PluginCall
import com.getcapacitor.PluginMethod
import com.getcapacitor.annotation.CapacitorPlugin

// ── Background sync receiver ─────────────────────────────────────────────────
// Fires every INTERVAL_MS milliseconds via AlarmManager.
// v1.11.4.5: persiste timestamp em SharedPreferences — sobrevive se o processo morrer.
// Quando o WebView estiver vivo, notifica via bridge. Se não estiver, o JS lê
// PREF_LAST_BG_SYNC ao retomar e detecta que perdeu disparos.
class AapsBackgroundReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        // Persistir timestamp antes de tudo — independente do estado do WebView
        val prefs = context.getSharedPreferences(AapsBackgroundPlugin.PREFS_NAME, Context.MODE_PRIVATE)
        prefs.edit().putLong(AapsBackgroundPlugin.PREF_LAST_BG_SYNC, System.currentTimeMillis()).apply()

        // Re-agendar próximo alarme
        AapsBackgroundPlugin.scheduleNext(context)

        // Notificar o WebView se o app estiver vivo
        AapsBackgroundPlugin.notifyBridge()
    }
}

@CapacitorPlugin(name = "AapsBackground")
class AapsBackgroundPlugin : Plugin() {

    companion object {
        const val ACTION = "com.glucocontext.app.BACKGROUND_SYNC"
        const val INTERVAL_MS = 3 * 60 * 1000L  // 3 minutos
        const val REQUEST_CODE = 7001
        const val PREFS_NAME = "aaps_assist_bg"
        const val PREF_LAST_BG_SYNC = "last_bg_sync_at"

        private var instance: AapsBackgroundPlugin? = null

        fun scheduleNext(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AapsBackgroundReceiver::class.java).apply {
                action = ACTION
            }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
            val pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
            val triggerAt = SystemClock.elapsedRealtime() + INTERVAL_MS
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            } else {
                alarmManager.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, triggerAt, pi)
            }
        }

        fun cancel(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, AapsBackgroundReceiver::class.java).apply { action = ACTION }
            val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            else PendingIntent.FLAG_UPDATE_CURRENT
            val pi = PendingIntent.getBroadcast(context.applicationContext, REQUEST_CODE, intent, flags)
            alarmManager.cancel(pi)
        }

        fun notifyBridge() {
            instance?.notifyListeners("backgroundSync", JSObject().apply {
                put("timestamp", System.currentTimeMillis())
            })
        }
    }

    override fun load() {
        instance = this
    }

    @PluginMethod
    fun start(call: PluginCall) {
        scheduleNext(context)
        val ret = JSObject()
        ret.put("started", true)
        ret.put("intervalMs", INTERVAL_MS)
        call.resolve(ret)
    }

    @PluginMethod
    fun stop(call: PluginCall) {
        cancel(context)
        val ret = JSObject()
        ret.put("stopped", true)
        call.resolve(ret)
    }

    @PluginMethod
    fun isRunning(call: PluginCall) {
        val intent = Intent(context, AapsBackgroundReceiver::class.java).apply { action = ACTION }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        else PendingIntent.FLAG_NO_CREATE
        val pi = PendingIntent.getBroadcast(context, REQUEST_CODE, intent, flags)
        val ret = JSObject()
        ret.put("running", pi != null)
        call.resolve(ret)
    }

    // v1.11.4.5: expõe o timestamp persistido para o JS ler ao retomar
    @PluginMethod
    fun getLastSyncAt(call: PluginCall) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val ts = prefs.getLong(PREF_LAST_BG_SYNC, 0L)
        val ret = JSObject()
        ret.put("lastSyncAt", ts)
        call.resolve(ret)
    }
}
