package com.llsl.viper4android.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.SpannableString
import android.text.Spanned
import android.text.style.StyleSpan
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import com.llsl.viper4android.R
import com.llsl.viper4android.data.ActivePresetSelection
import com.llsl.viper4android.data.model.Preset
import com.llsl.viper4android.data.repository.ViperRepository
import com.llsl.viper4android.service.ViperService
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import javax.inject.Inject

@AndroidEntryPoint
class PresetWidgetProvider : AppWidgetProvider() {
    @Inject
    lateinit var repository: ViperRepository

    companion object {
        private const val ACTION_SELECT = "com.llsl.viper4android.widget.SELECT"
        private const val EXTRA_PRESET_ID = "preset_id"

        fun refreshAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, PresetWidgetProvider::class.java))
            if (ids.isEmpty()) return
            context.sendBroadcast(
                Intent(context, PresetWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
                },
            )
        }
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == ACTION_SELECT) {
            val widgetId = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, -1)
            val presetId = intent.getLongExtra(EXTRA_PRESET_ID, -1L)
            val configured = PresetWidgetSettings.load(context, widgetId)
            if (presetId > 0 && configured?.presets?.any { it.id == presetId } == true) {
                ViperService.applyWidgetPreset(context, presetId)
            }
            return
        }
        super.onReceive(context, intent)
    }

    override fun onUpdate(context: Context, manager: AppWidgetManager, widgetIds: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val all = repository.getAllPresets().first().associateBy { it.id }
                widgetIds.forEach { id ->
                    render(context, manager, id, all)
                }
            } finally {
                pending.finish()
            }
        }
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        newOptions: Bundle,
    ) {
        onUpdate(context, manager, intArrayOf(widgetId))
    }

    override fun onDeleted(context: Context, widgetIds: IntArray) {
        PresetWidgetSettings.delete(context, widgetIds)
    }

    private fun makePresetLayer(
        density: Float,
        widthDp: Float,
        heightDp: Float,
        color: Int,
        radiusDp: Int,
        borderDp: Int,
        isBorder: Boolean,
    ): Bitmap {
        val width = (widthDp * density).roundToInt().coerceIn(1, 1024)
        val height = (heightDp * density).roundToInt().coerceIn(1, 1024)
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val borderPx = if (borderDp == 0) 0 else (borderDp * density).roundToInt().coerceAtLeast(1)
        if (isBorder && borderPx == 0) return bitmap
        // The fill stops at the inner edge of the border. Neither alpha channel
        // can reveal the other layer: transparent pixels show the launcher.
        val inset = if (isBorder) borderPx / 2f else borderPx.toFloat()
        val rect = RectF(inset, inset, width - inset, height - inset)
        if (rect.width() > 0f && rect.height() > 0f) {
            val radius = (radiusDp * density - inset).coerceAtLeast(0f)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                style = if (isBorder) Paint.Style.STROKE else Paint.Style.FILL
                if (isBorder) strokeWidth = borderPx.toFloat()
            }
            Canvas(bitmap).drawRoundRect(rect, radius, radius, paint)
        }
        return bitmap
    }

    private fun render(
        context: Context,
        manager: AppWidgetManager,
        widgetId: Int,
        presets: Map<Long, Preset>,
    ) {
        val config = PresetWidgetSettings.load(context, widgetId)
        val options = manager.getAppWidgetOptions(widgetId)
        val vertical =
            when (config?.orientation) {
                "vertical" -> true
                "auto" -> {
                    options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH) <
                        options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT)
                }
                else -> false
            }
        val layout = if (vertical) R.layout.widget_presets_vertical else R.layout.widget_presets_horizontal
        val views = RemoteViews(context.packageName, layout)
        views.removeAllViews(R.id.widget_preset_container)
        val outerInset =
            ((config?.outerInsetDp ?: 0) * context.resources.displayMetrics.density).toInt()
        views.setViewPadding(
            R.id.widget_preset_container,
            outerInset,
            outerInset,
            outerInset,
            outerInset,
        )
        val choices = config?.presets.orEmpty()
        if (choices.isEmpty()) {
            val empty = RemoteViews(context.packageName, R.layout.widget_presets_empty)
            val configure =
                Intent(context, PresetWidgetConfigureActivity::class.java).apply {
                    putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK
                }
            empty.setOnClickPendingIntent(
                R.id.widget_preset_item,
                PendingIntent.getActivity(
                    context,
                    widgetId,
                    configure,
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            views.addView(R.id.widget_preset_container, empty)
        } else {
            val active = ActivePresetSelection.activePresetId(context)
            val gapDp = config?.gapDp ?: 4
            val density = context.resources.displayMetrics.density
            val gapPx = (density * gapDp).toInt()
            val insetDp = config?.outerInsetDp ?: 0
            // Use launcher-reported widget dimensions to size the translucent artwork.
            val widthDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 80).coerceAtLeast(40)
            val heightDp = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 40).coerceAtLeast(40)
            val availableWidthDp =
                (widthDp - 2 * insetDp - (if (vertical) 0 else gapDp * (choices.size - 1))).coerceAtLeast(1)
            val availableHeightDp =
                (heightDp - 2 * insetDp - (if (vertical) gapDp * (choices.size - 1) else 0)).coerceAtLeast(1)
            val buttonWidthDp = availableWidthDp.toFloat() / (if (vertical) 1 else choices.size)
            val buttonHeightDp = availableHeightDp.toFloat() / (if (vertical) choices.size else 1)
            val radiusDp = (config?.cornerRadiusDp ?: 10).coerceIn(0, 32)
            val borderDp = (config?.borderWidthDp ?: 1).coerceIn(0, 4)
            val normalBackground = makePresetLayer(
                density, buttonWidthDp, buttonHeightDp,
                config?.widgetColor ?: DEFAULT_WIDGET_COLOR, radiusDp, borderDp, false,
            )
            val activeBackground = makePresetLayer(
                density, buttonWidthDp, buttonHeightDp,
                config?.activeColor ?: DEFAULT_ACTIVE_COLOR, radiusDp, borderDp, false,
            )
            val normalBorder = makePresetLayer(
                density, buttonWidthDp, buttonHeightDp,
                config?.borderColor ?: DEFAULT_BORDER_COLOR, radiusDp, borderDp, true,
            )
            val activeBorder = makePresetLayer(
                density, buttonWidthDp, buttonHeightDp,
                config?.activeBorderColor ?: DEFAULT_ACTIVE_BORDER_COLOR, radiusDp, borderDp, true,
            )
            choices.forEachIndexed { index, choice ->
                val preset = presets[choice.id]
                val itemLayout =
                    if (vertical) R.layout.widget_preset_item_vertical
                    else R.layout.widget_preset_item_horizontal
                val item = RemoteViews(context.packageName, itemLayout)
                val presetName = preset?.name ?: "Missing preset"
                val styledName = SpannableString(presetName)
                if (presetName.isNotEmpty()) {
                    when (config?.presetTextWeight) {
                        "bold" -> styledName.setSpan(
                            StyleSpan(Typeface.BOLD),
                            0, presetName.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                        )
                    }
                }
                item.setTextViewText(R.id.widget_preset_label, styledName)
                item.setTextViewTextSize(
                    R.id.widget_preset_label,
                    TypedValue.COMPLEX_UNIT_SP,
                    (config?.presetTextSizeSp ?: 13).toFloat(),
                )
                val labelColor =
                    if (active == choice.id) config?.activeTextColor ?: DEFAULT_TEXT_COLOR
                    else config?.textColor ?: DEFAULT_TEXT_COLOR
                item.setTextColor(R.id.widget_preset_label, labelColor)
                val viperIdentity = config?.identityMode != "custom"
                val identity = if (viperIdentity) "ViPER" else config?.identityText?.trim().orEmpty()
                item.setTextViewText(R.id.widget_identity, identity)
                item.setTextColor(R.id.widget_identity, labelColor)
                item.setViewVisibility(
                    R.id.widget_identity,
                    if (identity.isBlank()) View.GONE else View.VISIBLE,
                )
                item.setViewVisibility(
                    R.id.widget_identity_icon,
                    if (viperIdentity) View.VISIBLE else View.GONE,
                )
                if (viperIdentity) {
                    item.setInt(R.id.widget_identity_icon, "setColorFilter", labelColor)
                }
                item.setViewPadding(
                    R.id.widget_identity,
                    ((if (viperIdentity) 18 else 6) * density).toInt(),
                    (2 * density).toInt(),
                    0,
                    0,
                )
                val selected = active == choice.id
                item.setImageViewBitmap(
                    R.id.widget_preset_background,
                    if (selected) activeBackground else normalBackground,
                )
                item.setImageViewBitmap(
                    R.id.widget_preset_border,
                    if (selected) activeBorder else normalBorder,
                )
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    // Margins participate in LinearLayout weight distribution, so every
                    // button (including the first) shrinks equally as spacing increases.
                    if (index > 0) {
                        item.setViewLayoutMargin(
                            R.id.widget_preset_item,
                            if (vertical) RemoteViews.MARGIN_TOP else RemoteViews.MARGIN_LEFT,
                            gapDp.toFloat(),
                            TypedValue.COMPLEX_UNIT_DIP,
                        )
                    }
                } else {
                    // RemoteViews layout-margin actions were added in Android 12.
                    // Keep a usable symmetric visual gap for supported older devices.
                    item.setViewPadding(
                        R.id.widget_preset_item,
                        if (vertical || index == 0) 0 else gapPx / 2,
                        if (!vertical || index == 0) 0 else gapPx / 2,
                        if (vertical || index == choices.lastIndex) 0 else gapPx / 2,
                        if (!vertical || index == choices.lastIndex) 0 else gapPx / 2,
                    )
                }
                if (preset != null) {
                    val click =
                        Intent(context, PresetWidgetProvider::class.java).apply {
                            action = ACTION_SELECT
                            data = Uri.parse("viper-widget://$widgetId/${choice.id}")
                            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                            putExtra(EXTRA_PRESET_ID, choice.id)
                        }
                    item.setOnClickPendingIntent(
                        R.id.widget_preset_item,
                        PendingIntent.getBroadcast(
                            context,
                            widgetId,
                            click,
                            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                        ),
                    )
                }
                views.addView(R.id.widget_preset_container, item)
            }
        }
        manager.updateAppWidget(widgetId, views)
    }
}
