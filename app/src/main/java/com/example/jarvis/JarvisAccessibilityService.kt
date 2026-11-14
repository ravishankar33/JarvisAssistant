package com.example.jarvis

import android.accessibilityservice.AccessibilityService
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent

class JarvisAccessibilityService : AccessibilityService() {

    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
    }

    override fun onInterrupt() {
    }

    override fun onDestroy() {
        if (instance === this) {
            instance = null
        }
        super.onDestroy()
    }

    fun goBack(): Boolean {
        mainHandler.post {
            performGlobalAction(GLOBAL_ACTION_BACK)
        }
        return true
    }

    fun goHome(): Boolean {
        mainHandler.post {
            performGlobalAction(GLOBAL_ACTION_HOME)
        }
        return true
    }

    companion object {
        @Volatile
        private var instance: JarvisAccessibilityService? = null

        fun back(): Boolean {
            return instance?.goBack() ?: false
        }

        fun home(): Boolean {
            return instance?.goHome() ?: false
        }
    }
}
