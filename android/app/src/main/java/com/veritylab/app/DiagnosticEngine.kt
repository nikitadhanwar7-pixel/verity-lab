package com.veritylab.app

import android.content.Context
import android.content.Intent
import android.os.BatteryManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.hardware.SensorManager
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.sqrt

/** Evidence model. Internal states are not customer-facing report labels. */
enum class Outcome { OBSERVED, ATTENTION, NOT_TESTED, INCONCLUSIVE }
data class Attempt(val outcome: Outcome, val evidence: JSONObject, val note: String, val at: Long = System.currentTimeMillis())
data class TestRecord(val code: String, val attempts: List<Attempt>, val selected: Int, val summary: String) {
    val chosen: Attempt get() = attempts[selected]
    fun json(): JSONObject = JSONObject().put("code", code).put("attempts", JSONArray().apply {
        attempts.forEach { put(JSONObject().put("outcome", it.outcome.name).put("evidence", it.evidence).put("note", it.note).put("at", it.at)) }
    }).put("selected_attempt", selected + 1).put("summary", summary)
}

object DiagnosticEngine {
    /** Best-of-two is allowed only for a higher-is-better, repeatable metric. Defect checks must retain worst/repeated evidence. */
    fun selectBestMetric(first: Attempt, second: Attempt, metricKey: String, preferHigher: Boolean = true): Int {
        val a = first.evidence.optDouble(metricKey, Double.NaN)
        val b = second.evidence.optDouble(metricKey, Double.NaN)
        if (!a.isFinite() || !b.isFinite()) return 0
        return if ((preferHigher && b > a) || (!preferHigher && b < a)) 1 else 0
    }

    fun battery(context: Context): Attempt {
        val intent = context.registerReceiver(null, android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED))
            ?: return Attempt(Outcome.NOT_TESTED, JSONObject(), "Android did not provide battery status.")
        val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
        val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
        val pct = if (level >= 0 && scale > 0) level * 100.0 / scale else Double.NaN
        val temp = intent.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, -1) / 10.0
        val status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        val evidence = JSONObject().put("battery_level_percent", if (pct.isFinite()) pct else JSONObject.NULL)
            .put("battery_temperature_c", if (temp >= 0) temp else JSONObject.NULL)
            .put("charging_status_code", status)
            .put("manufacturer", Build.MANUFACTURER).put("model", Build.MODEL)
        return Attempt(Outcome.OBSERVED, evidence, "System-reported battery readings only; this does not measure true capacity, cycle count or remaining lifespan.")
    }

    fun sensors(context: Context): Attempt {
        val sm = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
        val list = sm.getSensorList(android.hardware.Sensor.TYPE_ALL)
        val names = JSONArray().apply { list.forEach { put(JSONObject().put("name", it.name).put("vendor", it.vendor).put("type", it.type).put("wake_up", it.isWakeUpSensor)) } }
        return Attempt(if (list.isEmpty()) Outcome.NOT_TESTED else Outcome.OBSERVED,
            JSONObject().put("sensor_count", list.size).put("sensors", names),
            "Inventory only. A listed sensor is not proof of calibration or correct readings.")
    }

    @Suppress("DEPRECATION")
    fun vibration(context: Context): Attempt {
        val vibrator: Vibrator = if (Build.VERSION.SDK_INT >= 31) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
        } else (context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator)
        if (!vibrator.hasVibrator()) return Attempt(Outcome.NOT_TESTED, JSONObject().put("has_vibrator", false), "This device does not expose a vibrator to Android.")
        if (Build.VERSION.SDK_INT >= 26) vibrator.vibrate(VibrationEffect.createWaveform(longArrayOf(0, 180, 100, 180), -1)) else vibrator.vibrate(longArrayOf(0, 180, 100, 180), -1)
        return Attempt(Outcome.ATTENTION, JSONObject().put("has_vibrator", true).put("pattern_requested", "180ms, pause, 180ms"), "Pattern requested. The person testing must confirm whether vibration was felt; amplitude is not measured.")
    }

    fun performance(): Attempt {
        var checksum = 0.0
        val start = System.nanoTime()
        for (i in 1..350_000) checksum += sqrt(i.toDouble())
        val ms = (System.nanoTime() - start) / 1_000_000.0
        return Attempt(Outcome.OBSERVED, JSONObject().put("duration_ms", ms).put("checksum", checksum),
            "A repeatable app workload completed. Timing varies with load and power state; this alone is not a CPU-health or thermal certification.")
    }
}
