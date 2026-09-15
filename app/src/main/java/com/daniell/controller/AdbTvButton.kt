package com.daniell.controller

import android.content.Context
import android.content.Intent
import android.util.AttributeSet
import androidx.appcompat.widget.AppCompatButton

/** Opens the TV ADB control screen without requiring changes to MainActivity. */
class AdbTvButton @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null
) : AppCompatButton(context, attrs) {
    init {
        text = "CONTROLE GLOBAL DA TV (ADB)"
        setOnClickListener { context.startActivity(Intent(context, AdbTvActivity::class.java)) }
    }
}
