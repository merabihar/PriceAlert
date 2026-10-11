package com.example.pricealert

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.widget.*

class MainActivity : Activity() {
    private val tfs = arrayOf("1m", "3m", "5m", "15m", "30m", "1h", "2h", "4h", "1d")

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        if (Build.VERSION.SDK_INT >= 33)
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1)
        val p = getSharedPreferences("cfg", Context.MODE_PRIVATE)
        val d = resources.displayMetrics.density
        val pad = (16 * d).toInt()
        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        fun label(t: String) = TextView(this).apply { text = t; setPadding(0, (14 * d).toInt(), 0, 0) }
        fun edit(v: String, num: Boolean) = EditText(this).apply {
            setText(v); if (num) inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        }

        root.addView(TextView(this).apply { text = "🔔 Price Alert"; textSize = 24f })
        root.addView(label("Symbol (Coin: BTCUSDT, ETHUSDT… | Gold: PAXGUSDT)"))
        val sym = edit(p.getString("sym", "BTCUSDT")!!, false); root.addView(sym)
        root.addView(label("Time frame"))
        val tf = Spinner(this)
        tf.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, tfs)
        tf.setSelection(tfs.indexOf(p.getString("tf", "15m")).coerceAtLeast(0))
        root.addView(tf)
        root.addView(label("Price range – Low"))
        val lo = edit(p.getString("lo", "")!!, true); root.addView(lo)
        root.addView(label("Price range – High"))
        val hi = edit(p.getString("hi", "")!!, true); root.addView(hi)
        val strict = CheckBox(this).apply {
            text = "Break = B ka CLOSE A ke high se upar (off: B ka high A ke high se upar)"
            isChecked = p.getBoolean("strict", false)
        }
        root.addView(strict)

        fun save() = p.edit()
            .putString("sym", sym.text.toString().trim().uppercase())
            .putString("tf", tfs[tf.selectedItemPosition])
            .putString("lo", lo.text.toString()).putString("hi", hi.text.toString())
            .putBoolean("strict", strict.isChecked).putString("last", "").apply()

        val sw = Switch(this).apply { text = "Alert system ACTIVE"; isChecked = p.getBoolean("active", false) }
        val svc = Intent(this, AlertService::class.java)
        sw.setOnCheckedChangeListener { _, on ->
            if (on) { save(); p.edit().putBoolean("active", true).apply(); startForegroundService(svc) }
            else { p.edit().putBoolean("active", false).apply(); stopService(svc) }
        }
        root.addView(sw)
        root.addView(Button(this).apply {
            text = "Settings save karein"
            setOnClickListener { save(); Toast.makeText(context, "Save ho gaya", Toast.LENGTH_SHORT).show() }
        })
        root.addView(Button(this).apply {
            text = "Battery optimization hatayein (24x7 ke liye)"
            setOnClickListener {
                try {
                    startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                        Uri.parse("package:$packageName")))
                } catch (e: Exception) { startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) }
            }
        })
        setContentView(ScrollView(this).apply { addView(root) })
        if (p.getBoolean("active", false)) startForegroundService(svc)
    }
}
