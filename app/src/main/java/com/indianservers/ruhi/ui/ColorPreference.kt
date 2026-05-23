package com.indianservers.ruhi.ui

import android.content.Context
import android.graphics.Color
import android.util.AttributeSet
import androidx.preference.ListPreference

class ColorPreference @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : ListPreference(context, attrs) {
    init {
        entries = arrayOf("Ruhi Cyan", "Warm Pink", "Sun Gold", "Soft Blue", "Leaf Green")
        entryValues = arrayOf("#00FFCC", "#FF6B6B", "#F7C948", "#4DABF7", "#66FF99")
        summaryProvider = SummaryProvider<ColorPreference> { preference ->
            preference.entry ?: preference.value
        }
    }

    fun selectedColor(): Int = runCatching { Color.parseColor(value) }.getOrDefault(Color.parseColor("#00FFCC"))
}
