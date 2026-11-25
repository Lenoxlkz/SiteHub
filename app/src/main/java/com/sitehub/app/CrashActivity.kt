package com.sitehub.app

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.sitehub.app.util.CrashReporter

class CrashActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val report = CrashReporter.read(this) ?: "Sin informe disponible."
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.parseColor("#121212"))
            setPadding(32, 48, 32, 32)
        }
        root.addView(TextView(this).apply {
            text = "SiteHub se cerró por un error"
            setTextColor(Color.WHITE); textSize = 20f; typeface = Typeface.DEFAULT_BOLD
        })
        root.addView(TextView(this).apply {
            text = "Copia este informe y compártelo para poder corregirlo."
            setTextColor(Color.parseColor("#BBBBBB")); textSize = 14f; setPadding(0, 8, 0, 16)
        })
        val scroll = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f)
        }
        scroll.addView(TextView(this).apply {
            text = report; setTextColor(Color.parseColor("#E0E0E0"))
            textSize = 12f; typeface = Typeface.MONOSPACE; setTextIsSelectable(true)
        })
        root.addView(scroll)
        val buttons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL; gravity = Gravity.END; setPadding(0, 16, 0, 0)
        }
        buttons.addView(Button(this).apply {
            text = "Copiar"
            setOnClickListener {
                val cm = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("SiteHub crash", report))
                Toast.makeText(this@CrashActivity, "Informe copiado", Toast.LENGTH_SHORT).show()
            }
        })
        buttons.addView(Button(this).apply {
            text = "Reiniciar app"
            setOnClickListener {
                startActivity(Intent(this@CrashActivity, MainActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
                finish()
            }
        })
        root.addView(buttons)
        setContentView(root)
    }
}
