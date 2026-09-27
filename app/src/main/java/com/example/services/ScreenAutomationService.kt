package com.example.services

import android.content.Context
import android.util.Log
import com.example.models.ScreenDump
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

class ScreenAutomationService(private val context: Context) {

    companion object {
        private const val TAG = "ScreenAutomation"
        private const val TIMEOUT_MS = 3500L
    }

    val isAccessibilityActive: Boolean
        get() = EvaAccessibilityService.isRunning()

    suspend fun getScreenDump(): ScreenDump = withContext(Dispatchers.Main) {
        val service = EvaAccessibilityService.instance
        if (service == null) {
            ScreenDump(packageName = "accessibility_inactive", nodes = emptyList())
        } else {
            service.dumpScreen()
        }
    }

    suspend fun getScreenDescription(compress: Boolean = true): String {
        val dump = getScreenDump()
        return dump.toCompactDescription(compress = compress)
    }

    suspend fun clickByText(text: String): Boolean = withTimeoutOrNull(TIMEOUT_MS) {
        withContext(Dispatchers.Main) {
            EvaAccessibilityService.instance?.clickByText(text) ?: false
        }
    } ?: false

    suspend fun clickAt(x: Float, y: Float): Boolean = withTimeoutOrNull(TIMEOUT_MS) {
        withContext(Dispatchers.Main) {
            EvaAccessibilityService.instance?.clickAt(x, y) ?: false
        }
    } ?: false

    suspend fun typeText(text: String, fieldHint: String? = null): Boolean = withTimeoutOrNull(TIMEOUT_MS) {
        withContext(Dispatchers.Main) {
            EvaAccessibilityService.instance?.typeText(text, fieldHint) ?: false
        }
    } ?: false

    suspend fun pressEnter(): Boolean = withTimeoutOrNull(TIMEOUT_MS) {
        withContext(Dispatchers.Main) {
            EvaAccessibilityService.instance?.pressEnter() ?: false
        }
    } ?: false

    suspend fun scroll(direction: String): Boolean = withTimeoutOrNull(TIMEOUT_MS) {
        withContext(Dispatchers.Main) {
            EvaAccessibilityService.instance?.scroll(direction) ?: false
        }
    } ?: false

    suspend fun swipe(startX: Float, startY: Float, endX: Float, endY: Float): Boolean = withTimeoutOrNull(TIMEOUT_MS) {
        withContext(Dispatchers.Main) {
            EvaAccessibilityService.instance?.swipe(startX, startY, endX, endY) ?: false
        }
    } ?: false

    suspend fun pressBack(): Boolean = withTimeoutOrNull(TIMEOUT_MS) {
        withContext(Dispatchers.Main) {
            EvaAccessibilityService.instance?.pressBack() ?: false
        }
    } ?: false

    suspend fun pressHome(): Boolean = withTimeoutOrNull(TIMEOUT_MS) {
        withContext(Dispatchers.Main) {
            EvaAccessibilityService.instance?.pressHome() ?: false
        }
    } ?: false
}
