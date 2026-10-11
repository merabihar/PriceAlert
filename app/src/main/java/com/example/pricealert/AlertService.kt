package com.example.pricealert

import android.app.*
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationCompat
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL

class AlertService : Service() {
    @Volatile private var running = false
    private var t: Thread? = null
    private var wl: PowerManager.WakeLock? = null

    override fun onBind(i: Intent?): IBinder? = null

    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel("svc", "Service", NotificationManager.IMPORTANCE_LOW))
        val ch = NotificationChannel("alert", "Price Alerts", NotificationManager.IMPORTANCE_HIGH)
        ch.enableVibration(true)
        nm.createNotificationChannel(ch)
        val n = NotificationCompat.Builder(this, "svc")
            .setContentTitle("Price Alert chalu hai")
            .setContentText("Candles monitor ho rahe hain")
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setOngoing(true).build()
        if (Build.VERSION.SDK_INT >= 34) startForeground(1, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE)
        else startForeground(1, n)
        if (wl == null) {
            wl = (getSystemService(Context.POWER_SERVICE) as PowerManager)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "pricealert:wl")
            wl?.acquire()
        }
        if (!running) {
            running = true
            t = Thread { loop() }.also { it.start() }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        running = false
        t?.interrupt()
        wl?.let { if (it.isHeld) it.release() }
        super.onDestroy()
    }

    private fun loop() {
        val p = getSharedPreferences("cfg", MODE_PRIVATE)
        while (running) {
            try { check(p) } catch (e: Exception) {}
            try { Thread.sleep(15000) } catch (e: InterruptedException) { break }
        }
    }

    private fun check(p: SharedPreferences) {
        val sym = p.getString("sym", "BTCUSDT")!!.trim().uppercase()
        val tf = p.getString("tf", "15m")!!
        val lo = p.getString("lo", "")!!.toDoubleOrNull() ?: 0.0
        val hi = p.getString("hi", "")!!.toDoubleOrNull() ?: Double.MAX_VALUE
        val strict = p.getBoolean("strict", false)
        val c = fetch(sym, tf) ?: return
        val n = c.length()
        if (n < 3) return
        val a = c.getJSONArray(n - 3)   // candle A (closed)
        val b = c.getJSONArray(n - 2)   // candle B (closed)
        val cc = c.getJSONArray(n - 1)  // candle C (running)
        val aHigh = a.getString(2).toDouble()
        val bHigh = b.getString(2).toDouble()
        val bClose = b.getString(4).toDouble()
        val cHigh = cc.getString(2).toDouble()
        val price = cc.getString(4).toDouble()
        if (price < lo || price > hi) return          // price range condition
        val broke = if (strict) bClose > aHigh else bHigh > aHigh
        if (!broke) return                             // B must break A's high
        if (cHigh < bHigh) return                      // C must touch B's high
        val key = "$sym|$tf|${b.getLong(0)}"
        if (p.getString("last", "") == key) return     // one alert per B candle
        p.edit().putString("last", key).apply()
        val msg = "$sym ($tf): C ne B ke high $bHigh ko touch kiya. Price: $price"
        val nt = NotificationCompat.Builder(this, "alert")
            .setContentTitle("🔔 Price Alert")
            .setContentText(msg)
            .setStyle(NotificationCompat.BigTextStyle().bigText(msg))
            .setSmallIcon(android.R.drawable.ic_popup_reminder)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setDefaults(NotificationCompat.DEFAULT_ALL)
            .setAutoCancel(true).build()
        getSystemService(NotificationManager::class.java).notify(System.currentTimeMillis().toInt(), nt)
    }

    private fun fetch(sym: String, tf: String): JSONArray? {
        for (h in arrayOf("https://api.binance.com", "https://data-api.binance.vision")) {
            try {
                val cn = URL("$h/api/v3/klines?symbol=$sym&interval=$tf&limit=5").openConnection() as HttpURLConnection
                cn.connectTimeout = 10000; cn.readTimeout = 10000
                if (cn.responseCode == 200) return JSONArray(cn.inputStream.bufferedReader().readText())
            } catch (e: Exception) {}
        }
        return null
    }
}
