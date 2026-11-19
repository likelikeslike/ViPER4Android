package com.llsl.viper4android.widget

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

data class WidgetPreset(val id: Long)

data class WidgetConfiguration(
    val presets: List<WidgetPreset>,
    val orientation: String = "horizontal",
    val gapDp: Int = 4,
    val outerInsetDp: Int = 0,
    val cornerRadiusDp: Int = 10,
    val borderWidthDp: Int = 1,
    val textColor: Int = DEFAULT_TEXT_COLOR,
    val presetTextSizeSp: Int = 13,
    val presetTextWeight: String = "normal",
    val activeTextColor: Int = DEFAULT_TEXT_COLOR,
    val identityMode: String = "viper",
    val identityText: String = "",
    val widgetColor: Int = DEFAULT_WIDGET_COLOR,
    val activeColor: Int = DEFAULT_ACTIVE_COLOR,
    val borderColor: Int = DEFAULT_BORDER_COLOR,
    val activeBorderColor: Int = DEFAULT_ACTIVE_BORDER_COLOR,
)

const val DEFAULT_TEXT_COLOR: Int = -0x1
const val DEFAULT_WIDGET_COLOR: Int = 0xFF30343D.toInt()
const val DEFAULT_ACTIVE_COLOR: Int = 0xFF4068B7.toInt()
const val DEFAULT_BORDER_COLOR: Int = 0xFF646B78.toInt()
const val DEFAULT_ACTIVE_BORDER_COLOR: Int = 0xFF94B5FF.toInt()

object PresetWidgetSettings {
    const val CHANGE_REVISION = "widget_preset_revision"
    private const val STORAGE = "viper_preset_widgets"

    private fun preferences(context: Context) =
        context.getSharedPreferences(STORAGE, Context.MODE_PRIVATE)

    fun load(context: Context, widgetId: Int): WidgetConfiguration? {
        val raw = preferences(context).getString("widget_$widgetId", null) ?: return null
        return try {
            val json = JSONObject(raw)
            val list = json.optJSONArray("presets") ?: JSONArray()
            val presets =
                (0 until list.length()).mapNotNull { index ->
                    val value = list.optJSONObject(index) ?: return@mapNotNull null
                    val id = value.optLong("id", 0L)
                    if (id <= 0) null else WidgetPreset(id)
                }
            WidgetConfiguration(
                presets = presets,
                orientation = json.optString("orientation", "horizontal"),
                gapDp = json.optInt("gapDp", 4).coerceIn(0, 96),
                outerInsetDp = json.optInt("outerInsetDp", 0).coerceIn(0, 64),
                cornerRadiusDp = json.optInt("cornerRadiusDp", 10).coerceIn(0, 32),
                borderWidthDp = json.optInt("borderWidthDp", 1).coerceIn(0, 4),
                textColor = json.optInt("textColor", DEFAULT_TEXT_COLOR),
                presetTextSizeSp = json.optInt("presetTextSizeSp", 13).coerceIn(10, 30),
                presetTextWeight = json.optString("presetTextWeight", "normal")
                    .takeIf { it in setOf("normal", "bold") } ?: "normal",
                activeTextColor = json.optInt("activeTextColor", DEFAULT_TEXT_COLOR),
                identityMode = json.optString("identityMode", "viper").takeIf { it in setOf("viper", "custom") } ?: "viper",
                identityText = json.optString("identityText", "").take(18),
                widgetColor = json.optInt("widgetColor", DEFAULT_WIDGET_COLOR),
                activeColor = json.optInt("activeColor", DEFAULT_ACTIVE_COLOR),
                borderColor = json.optInt("borderColor", DEFAULT_BORDER_COLOR),
                activeBorderColor = json.optInt("activeBorderColor", DEFAULT_ACTIVE_BORDER_COLOR),
            )
        } catch (_: Exception) {
            null
        }
    }

    fun save(context: Context, widgetId: Int, config: WidgetConfiguration) {
        val list = JSONArray()
        config.presets.distinctBy { it.id }.forEach {
            list.put(JSONObject().put("id", it.id))
        }
        val json =
            JSONObject()
                .put("presets", list)
                .put("orientation", config.orientation)
                .put("gapDp", config.gapDp.coerceIn(0, 96))
                .put("outerInsetDp", config.outerInsetDp.coerceIn(0, 64))
                .put("cornerRadiusDp", config.cornerRadiusDp.coerceIn(0, 32))
                .put("borderWidthDp", config.borderWidthDp.coerceIn(0, 4))
                .put("textColor", config.textColor)
                .put("presetTextSizeSp", config.presetTextSizeSp.coerceIn(10, 30))
                .put("presetTextWeight", config.presetTextWeight)
                .put("activeTextColor", config.activeTextColor)
                .put("identityMode", config.identityMode)
                .put("identityText", config.identityText.take(18))
                .put("widgetColor", config.widgetColor)
                .put("activeColor", config.activeColor)
                .put("borderColor", config.borderColor)
                .put("activeBorderColor", config.activeBorderColor)
        preferences(context).edit().putString("widget_$widgetId", json.toString()).apply()
    }

    fun delete(context: Context, widgetIds: IntArray) {
        preferences(context).edit().apply {
            widgetIds.forEach { remove("widget_$it") }
        }.apply()
    }

}
