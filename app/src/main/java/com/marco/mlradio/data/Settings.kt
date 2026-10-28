package com.marco.mlradio.data

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.marco.mlradio.audio.EqConfig
import org.json.JSONArray
import org.json.JSONObject
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

/** Preferenze osservabili da Compose e salvate su disco. */
class BoolPref(private val sp: SharedPreferences, private val key: String, def: Boolean) :
    ReadWriteProperty<Any?, Boolean> {
    private var value by mutableStateOf(sp.getBoolean(key, def))
    override fun getValue(thisRef: Any?, property: KProperty<*>): Boolean = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Boolean) {
        this.value = value
        sp.edit().putBoolean(key, value).apply()
    }
}

class IntPref(private val sp: SharedPreferences, private val key: String, def: Int) :
    ReadWriteProperty<Any?, Int> {
    private var value by mutableStateOf(sp.getInt(key, def))
    override fun getValue(thisRef: Any?, property: KProperty<*>): Int = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: Int) {
        this.value = value
        sp.edit().putInt(key, value).apply()
    }
}

class StringPref(private val sp: SharedPreferences, private val key: String, def: String) :
    ReadWriteProperty<Any?, String> {
    private var value by mutableStateOf(sp.getString(key, def) ?: def)
    override fun getValue(thisRef: Any?, property: KProperty<*>): String = value
    override fun setValue(thisRef: Any?, property: KProperty<*>, value: String) {
        this.value = value
        sp.edit().putString(key, value).apply()
    }
}

/** Preset dell'equalizzatore salvato dall'utente (configurazione completa). */
data class UserPreset(val name: String, val config: EqConfig)

class Settings(context: Context) {
    private val sp: SharedPreferences =
        context.applicationContext.getSharedPreferences("mlradio_settings", Context.MODE_PRIVATE)

    // Aspetto
    /** 0 = segui il sistema, 1 = chiaro, 2 = scuro */
    var themeMode by IntPref(sp, "theme_mode", 0)
    var dynamicColor by BoolPref(sp, "dynamic_color", true)

    // Comportamento
    var resumeOnConnect by BoolPref(sp, "resume_on_connect", true)
    var hapticScreen by BoolPref(sp, "haptic_screen", true)

    /** Ultima radio ascoltata (JSON), per ritrovarla all'apertura. */
    var lastStationJson by StringPref(sp, "last_station", "")

    // Debug
    var verboseLog by BoolPref(sp, "verbose_log", false)

    // Equalizzatore
    private var eqState by mutableStateOf(EqConfig.fromJson(sp.getString("eq", null)))

    var onEqChanged: ((EqConfig) -> Unit)? = null

    var eq: EqConfig
        get() = eqState
        set(v) {
            eqState = v
            sp.edit().putString("eq", v.toJson()).apply()
            onEqChanged?.invoke(v)
        }

    fun updateEq(block: (EqConfig) -> EqConfig) {
        eq = block(eqState)
    }

    // Preset utente
    private var presetsRaw by StringPref(sp, "eq_user_presets", "[]")

    val userPresets: List<UserPreset>
        get() = try {
            val a = JSONArray(presetsRaw)
            List(a.length()) { i ->
                val o = a.getJSONObject(i)
                UserPreset(o.getString("name"), EqConfig.fromJson(o.getString("cfg")))
            }
        } catch (_: Exception) {
            emptyList()
        }

    /** Salva (o sostituisce, se il nome esiste già) un preset con la configurazione attuale. */
    fun saveUserPreset(name: String, cfg: EqConfig) {
        val n = name.trim().take(24)
        if (n.isEmpty()) return
        val list = userPresets.filter { !it.name.equals(n, ignoreCase = true) } + UserPreset(n, cfg.copy(enabled = true))
        writePresets(list)
    }

    fun deleteUserPreset(name: String) {
        writePresets(userPresets.filter { it.name != name })
    }

    private fun writePresets(list: List<UserPreset>) {
        val a = JSONArray()
        for (p in list) a.put(JSONObject().put("name", p.name).put("cfg", p.config.toJson()))
        presetsRaw = a.toString()
    }
}
