package com.llsl.viper4android.effect

import com.llsl.viper4android.data.repository.ViperRepository
import kotlinx.coroutines.flow.first
import org.json.JSONArray
import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.Locale

sealed class EffectPref<T>(
    val effectKey: String,
    val paramId: Int,
    val jsonKey: String,
    val defaultValue: T,
    val get: (EffectState) -> T,
    val set: EffectState.(T) -> EffectState,
    prefKeyOverride: String? = null,
) {
    val prefKey: String =
        prefKeyOverride
            ?: if (paramId != -1) {
                paramId.toString()
            } else if (effectKey.isEmpty()) {
                jsonKey
            } else {
                "${effectKey}_$jsonKey"
            }

    abstract fun toRaw(value: T): Int
}

class IntPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: Int,
    get: (EffectState) -> Int,
    set: EffectState.(Int) -> EffectState,
    private val toRawFn: ((Int) -> Int)? = null,
    val range: IntRange? = null,
) : EffectPref<Int>(effectKey, paramId, jsonKey, defaultValue, get, set) {
    override fun toRaw(value: Int): Int = toRawFn?.invoke(value) ?: value

    fun clamp(value: Int): Int = range?.let { value.coerceIn(it) } ?: value
}

class BoolPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: Boolean,
    get: (EffectState) -> Boolean,
    set: EffectState.(Boolean) -> EffectState,
    prefKeyOverride: String? = null,
) : EffectPref<Boolean>(effectKey, paramId, jsonKey, defaultValue, get, set, prefKeyOverride) {
    override fun toRaw(value: Boolean): Int = if (value) 1 else 0
}

class StringPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: String,
    get: (EffectState) -> String,
    set: EffectState.(String) -> EffectState,
) : EffectPref<String>(effectKey, paramId, jsonKey, defaultValue, get, set) {
    override fun toRaw(value: String): Int = 0
}

class NullableLongPref(
    effectKey: String,
    jsonKey: String,
    get: (EffectState) -> Long?,
    set: EffectState.(Long?) -> EffectState,
) : EffectPref<Long?>(effectKey, -1, jsonKey, null, get, set) {
    override fun toRaw(value: Long?): Int = value?.toInt() ?: -1
}

sealed class ListPref<E>(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: List<E>,
    get: (EffectState) -> List<E>,
    set: EffectState.(List<E>) -> EffectState,
) : EffectPref<List<E>>(effectKey, paramId, jsonKey, defaultValue, get, set) {
    override fun toRaw(value: List<E>): Int = 0

    abstract val padValue: E

    abstract fun elementToRaw(value: E): Int
}

class IntListPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: List<Int>,
    get: (EffectState) -> List<Int>,
    set: EffectState.(List<Int>) -> EffectState,
    private val elementToRawFn: ((Int) -> Int)? = null,
    val range: IntRange? = null,
) : ListPref<Int>(effectKey, paramId, jsonKey, defaultValue, get, set) {
    override val padValue: Int = 0

    override fun elementToRaw(value: Int): Int = elementToRawFn?.invoke(value) ?: value

    fun clampElement(value: Int): Int = range?.let { value.coerceIn(it) } ?: value
}

class BoolListPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: List<Boolean>,
    get: (EffectState) -> List<Boolean>,
    set: EffectState.(List<Boolean>) -> EffectState,
) : ListPref<Boolean>(effectKey, paramId, jsonKey, defaultValue, get, set) {
    override val padValue: Boolean = true

    override fun elementToRaw(value: Boolean): Int = if (value) 1 else 0
}

class DoubleListPref(
    effectKey: String,
    paramId: Int,
    jsonKey: String,
    defaultValue: List<Double>,
    get: (EffectState) -> List<Double>,
    set: EffectState.(List<Double>) -> EffectState,
    val range: ClosedFloatingPointRange<Double>? = null,
) : EffectPref<List<Double>>(effectKey, paramId, jsonKey, defaultValue, get, set) {
    override fun toRaw(value: List<Double>): Int = 0

    fun clampElement(value: Double): Double = range?.let { value.coerceIn(it) } ?: value

    fun toRawArray(value: List<Double>): ByteArray {
        val bytes = ByteArray(256)
        val bb =
            ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        bb.putInt(value.size)
        for (v in value) bb.putFloat(v.toFloat())
        return bytes
    }
}

val EFFECT_PREFS: List<EffectPref<*>> =
    listOf(Effects.masterEnable) + EFFECT_GROUPS.flatMap { it.prefs }

val EFFECT_PREFS_BY_PARAM_ID: Map<Int, EffectPref<*>> =
    EFFECT_PREFS
        .filter {
            it.paramId != -1 &&
                it !is IntListPref &&
                it !is BoolListPref &&
                it !is DoubleListPref
        }.associateBy { it.paramId }

val ENABLE_PREF_BY_EFFECT_KEY: Map<String, BoolPref> =
    EFFECT_GROUPS
        .mapNotNull { group ->
            val enable = group.prefs.firstOrNull { it.jsonKey == "enable" } as? BoolPref
            enable?.let { group.effectKey to it }
        }.toMap()

private fun spJoinInts(list: List<Int>): String = list.joinToString(";")

private fun spSplitInts(
    s: String,
    default: List<Int>,
): List<Int> {
    if (s.isBlank()) return default
    val parts = s.split(";").filter { it.isNotBlank() }
    if (parts.isEmpty()) return default
    return parts.mapNotNull { it.toIntOrNull() }
}

private fun spJoinBools(list: List<Boolean>): String = list.joinToString(";") { if (it) "1" else "0" }

private fun spSplitBools(
    s: String,
    default: List<Boolean>,
): List<Boolean> {
    if (s.isBlank()) return default
    val parts = s.split(";").filter { it.isNotBlank() }
    if (parts.isEmpty()) return default
    return parts.map { it == "1" }
}

private fun spJoinDoubles(list: List<Double>): String =
    list.joinToString(";") {
        String.format(Locale.US, "%.1f", it)
    }

private fun spSplitDoubles(
    s: String,
    default: List<Double>,
): List<Double> {
    if (s.isBlank()) return default
    val parts = s.split(";").filter { it.isNotBlank() }
    if (parts.isEmpty()) return default
    return parts.mapNotNull { it.toDoubleOrNull() }
}

suspend fun loadEffectPrefs(
    repository: ViperRepository,
    state: EffectState = EffectState(),
): EffectState {
    var s = state
    for (pref in EFFECT_PREFS) {
        s =
            when (pref) {
                is IntPref -> {
                    pref.set(s, repository.getIntPreference(pref.prefKey, pref.defaultValue).first())
                }

                is BoolPref -> {
                    pref.set(s, repository.getBooleanPreference(pref.prefKey, pref.defaultValue).first())
                }

                is StringPref -> {
                    pref.set(s, repository.getStringPreference(pref.prefKey, pref.defaultValue).first())
                }

                is NullableLongPref -> {
                    val raw = repository.getIntPreference(pref.prefKey, -1).first()
                    pref.set(s, if (raw < 0) null else raw.toLong())
                }

                is IntListPref -> {
                    val raw = repository.getStringPreference(pref.prefKey, spJoinInts(pref.defaultValue)).first()
                    pref.set(s, spSplitInts(raw, pref.defaultValue))
                }

                is BoolListPref -> {
                    val raw = repository.getStringPreference(pref.prefKey, spJoinBools(pref.defaultValue)).first()
                    pref.set(s, spSplitBools(raw, pref.defaultValue))
                }

                is DoubleListPref -> {
                    val raw = repository.getStringPreference(pref.prefKey, spJoinDoubles(pref.defaultValue)).first()
                    pref.set(s, spSplitDoubles(raw, pref.defaultValue))
                }
            }
    }
    return s
}

suspend fun saveEffectPrefs(
    repository: ViperRepository,
    state: EffectState,
) {
    for (pref in EFFECT_PREFS) {
        when (pref) {
            is IntPref -> repository.setIntPreference(pref.prefKey, pref.get(state))
            is BoolPref -> repository.setBooleanPreference(pref.prefKey, pref.get(state))
            is StringPref -> repository.setStringPreference(pref.prefKey, pref.get(state))
            is NullableLongPref -> repository.setIntPreference(pref.prefKey, pref.get(state)?.toInt() ?: -1)
            is IntListPref -> repository.setStringPreference(pref.prefKey, spJoinInts(pref.get(state)))
            is BoolListPref -> repository.setStringPreference(pref.prefKey, spJoinBools(pref.get(state)))
            is DoubleListPref -> repository.setStringPreference(pref.prefKey, spJoinDoubles(pref.get(state)))
        }
    }
}

const val PRESET_SCHEMA_VERSION = 2
private const val KEY_SCHEMA_VERSION = "schemaVersion"
private const val KEY_NAME = "name"
private const val KEY_CREATED_AT = "createdAt"

fun serializeEffectPrefs(state: EffectState): JSONObject = serializeEffectPrefs(state, name = null, createdAt = null)

fun serializeEffectPrefs(
    state: EffectState,
    name: String?,
    createdAt: Long?,
): JSONObject {
    val root = JSONObject()
    if (name != null || createdAt != null) {
        root.put(KEY_SCHEMA_VERSION, PRESET_SCHEMA_VERSION)
        if (name != null) root.put(KEY_NAME, name)
        if (createdAt != null) root.put(KEY_CREATED_AT, createdAt)
    }
    for (group in EFFECT_GROUPS) {
        val obj = JSONObject()
        for (pref in group.prefs) {
            putPrefValue(obj, pref, state)
        }
        root.put(group.effectKey, obj)
    }
    return root
}

private fun putPrefValue(
    obj: JSONObject,
    pref: EffectPref<*>,
    state: EffectState,
) {
    when (pref) {
        is IntPref -> {
            obj.put(pref.jsonKey, pref.get(state))
        }

        is BoolPref -> {
            obj.put(pref.jsonKey, pref.get(state))
        }

        is StringPref -> {
            obj.put(pref.jsonKey, pref.get(state))
        }

        is NullableLongPref -> {
            val v = pref.get(state)
            if (v == null) obj.put(pref.jsonKey, JSONObject.NULL) else obj.put(pref.jsonKey, v)
        }

        is IntListPref -> {
            val arr = JSONArray()
            for (v in pref.get(state)) arr.put(v)
            obj.put(pref.jsonKey, arr)
        }

        is BoolListPref -> {
            val arr = JSONArray()
            for (v in pref.get(state)) arr.put(v)
            obj.put(pref.jsonKey, arr)
        }

        is DoubleListPref -> {
            val arr = JSONArray()
            for (v in pref.get(state)) arr.put(v)
            obj.put(pref.jsonKey, arr)
        }
    }
}

fun deserializeEffectPrefs(
    obj: JSONObject,
    state: EffectState,
): EffectState {
    var s = state
    for (group in EFFECT_GROUPS) {
        val sub = obj.optJSONObject(group.effectKey) ?: continue
        for (pref in group.prefs) {
            s = applyPrefFromJson(s, pref, sub)
        }
    }
    return s
}

private fun applyPrefFromJson(
    state: EffectState,
    pref: EffectPref<*>,
    obj: JSONObject,
): EffectState {
    if (!obj.has(pref.jsonKey)) return state
    return when (pref) {
        is IntPref -> {
            pref.set(state, pref.clamp(obj.optInt(pref.jsonKey, pref.get(state))))
        }

        is BoolPref -> {
            pref.set(state, obj.optBoolean(pref.jsonKey, pref.get(state)))
        }

        is StringPref -> {
            pref.set(state, obj.optString(pref.jsonKey, pref.get(state)))
        }

        is NullableLongPref -> {
            val v =
                if (obj.isNull(pref.jsonKey)) {
                    null
                } else {
                    val raw = obj.optInt(pref.jsonKey, -1)
                    if (raw < 0) null else raw.toLong()
                }
            pref.set(state, v)
        }

        is IntListPref -> {
            val arr = obj.optJSONArray(pref.jsonKey) ?: return state
            val list = mutableListOf<Int>()
            for (i in 0 until arr.length()) list.add(pref.clampElement(arr.optInt(i, 0)))
            pref.set(state, list.toList())
        }

        is BoolListPref -> {
            val arr = obj.optJSONArray(pref.jsonKey) ?: return state
            val list = mutableListOf<Boolean>()
            for (i in 0 until arr.length()) list.add(arr.optBoolean(i, false))
            pref.set(state, list.toList())
        }

        is DoubleListPref -> {
            val arr = obj.optJSONArray(pref.jsonKey) ?: return state
            val list = mutableListOf<Double>()
            for (i in 0 until arr.length()) list.add(pref.clampElement(arr.optDouble(i, 0.0)))
            pref.set(state, list.toList())
        }
    }
}
