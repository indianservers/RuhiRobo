package com.indianservers.ruhi

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.bottomsheet.BottomSheetDialog
import org.json.JSONArray
import org.json.JSONObject

data class PerformanceStep(val timeMs: Long, val type: String, val data: String)

class ChoreographyEditor : AppCompatActivity() {
    private val handler = Handler(Looper.getMainLooper())
    private val steps = mutableListOf(
        PerformanceStep(0, "EXPRESSION", """{"value":"HAPPY"}"""),
        PerformanceStep(300, "DRIVE", """{"command":"spin"}"""),
        PerformanceStep(900, "SPEAK", """{"text":"Happy Birthday!"}"""),
        PerformanceStep(1_200, "LED", """{"effect":"confetti"}""")
    )
    private lateinit var list: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        title = "Choreography"
        list = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 24, 24, 24)
        }
        setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            addView(Button(context).apply {
                text = "Add Step"
                setOnClickListener { showAddSheet() }
            })
            addView(Button(context).apply {
                text = "Play"
                setOnClickListener { playTimeline() }
            })
            addView(Button(context).apply {
                text = "Share JSON"
                setOnClickListener { shareJson() }
            })
            addView(list, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f))
        })
        render()
    }

    private fun render() {
        list.removeAllViews()
        steps.sortedBy { it.timeMs }.forEach { step ->
            list.addView(TextView(this).apply {
                text = "${step.timeMs} ms  ${step.type}  ${step.data}"
                textSize = 16f
                setPadding(12, 12, 12, 12)
            })
        }
    }

    private fun showAddSheet() {
        val dialog = BottomSheetDialog(this)
        dialog.setContentView(LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(24, 24, 24, 24)
            listOf("EXPRESSION", "SPEAK", "DRIVE", "LED", "SOUND").forEach { type ->
                addView(Button(context).apply {
                    text = type
                    setOnClickListener {
                        steps += PerformanceStep((steps.size + 1) * 500L, type, "{}")
                        render()
                        dialog.dismiss()
                    }
                })
            }
        })
        dialog.show()
    }

    private fun playTimeline() {
        steps.forEach { step ->
            handler.postDelayed({
                setResult(Activity.RESULT_OK, Intent().putExtra("performance_step", JSONObject().put("type", step.type).put("data", step.data).toString()))
            }, step.timeMs)
        }
    }

    private fun shareJson() {
        val json = JSONArray(steps.map { JSONObject().put("timeMs", it.timeMs).put("type", it.type).put("data", it.data) }).toString(2)
        startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "application/json"
            putExtra(Intent.EXTRA_TEXT, json)
        }, "Share Ruhi choreography"))
    }
}
