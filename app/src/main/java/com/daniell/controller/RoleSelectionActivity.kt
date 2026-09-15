package com.daniell.controller

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button

class RoleSelectionActivity : Activity() {
    companion object {
        const val PREFS = "controller_prefs"
        const val ROLE = "device_role"
        const val TV = "tv"
        const val PHONE = "phone"
    }

    override fun onCreate(state: Bundle?) {
        super.onCreate(state)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        when (prefs.getString(ROLE, null)) {
            TV -> open(ReceiverActivity::class.java)
            PHONE -> open(MainActivity::class.java)
            else -> {
                setContentView(R.layout.activity_role_selection)
                findViewById<Button>(R.id.roleTvButton).setOnClickListener {
                    prefs.edit().putString(ROLE, TV).apply()
                    open(ReceiverActivity::class.java)
                }
                findViewById<Button>(R.id.rolePhoneButton).setOnClickListener {
                    prefs.edit().putString(ROLE, PHONE).apply()
                    open(MainActivity::class.java)
                }
                findViewById<Button>(R.id.roleTvButton).requestFocus()
            }
        }
    }

    private fun open(activity: Class<*>) {
        startActivity(Intent(this, activity))
        finish()
    }
}
