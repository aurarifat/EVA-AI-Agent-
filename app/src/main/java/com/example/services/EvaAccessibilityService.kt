package com.example.services

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.models.ScreenDump
import com.example.models.ScreenNode
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

class EvaAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "EvaAccessibility"

        @Volatile
        var instance: EvaAccessibilityService? = null
            private set

        private val _isServiceRunning = MutableStateFlow(false)
        val isServiceRunning: StateFlow<Boolean> = _isServiceRunning.asStateFlow()

        fun isRunning(): Boolean = instance != null

        fun openAccessibilitySettings(context: Context) {
            val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        _isServiceRunning.value = true
        Log.i(TAG, "EVA AI Accessibility Service Connected")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // Active event listening
    }

    override fun onInterrupt() {
        Log.w(TAG, "EVA AI Accessibility Service Interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        _isServiceRunning.value = false
        Log.i(TAG, "EVA AI Accessibility Service Destroyed")
    }

    /**
     * Inspect and dump current active window nodes.
     * Guaranteed redaction of sensitive / password fields.
     */
    fun dumpScreen(): ScreenDump {
        val root = rootInActiveWindow
        val packageName = root?.packageName?.toString() ?: "unknown"
        val nodes = mutableListOf<ScreenNode>()

        if (root != null) {
            var counter = 0
            fun traverse(node: AccessibilityNodeInfo, depth: Int) {
                val bounds = Rect()
                node.getBoundsInScreen(bounds)

                // Skip completely empty zero-dimension nodes
                val isVisible = bounds.width() > 0 && bounds.height() > 0

                val isPasswordNode = node.isPassword ||
                        node.className?.toString()?.contains("Password", ignoreCase = true) == true ||
                        node.viewIdResourceName?.contains("password", ignoreCase = true) == true

                val text = if (isPasswordNode) "[PASSWORD_PROTECTED_FIELD]" else (node.text?.toString() ?: "")
                val contentDesc = if (isPasswordNode) "[PROTECTED]" else (node.contentDescription?.toString() ?: "")

                if (isVisible) {
                    nodes.add(
                        ScreenNode(
                            index = counter++,
                            depth = depth,
                            text = text,
                            contentDescription = contentDesc,
                            className = node.className?.toString() ?: "",
                            isClickable = node.isClickable,
                            isEditable = node.isEditable,
                            isScrollable = node.isScrollable,
                            isPassword = isPasswordNode,
                            bounds = bounds,
                            viewId = node.viewIdResourceName
                        )
                    )
                }

                for (i in 0 until node.childCount) {
                    val child = node.getChild(i)
                    if (child != null) {
                        traverse(child, depth + 1)
                        child.recycle()
                    }
                }
            }

            traverse(root, 0)
            root.recycle()
        }

        return ScreenDump(packageName = packageName, nodes = nodes)
    }

    fun getCurrentPackage(): String {
        return rootInActiveWindow?.packageName?.toString() ?: "unknown"
    }

    /**
     * Click node matching text or description. If the direct node is not clickable,
     * walk up to its nearest clickable ancestor.
     */
    fun clickByText(query: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val cleanQuery = query.trim()

        val matching = root.findAccessibilityNodeInfosByText(cleanQuery)
        if (matching.isNotEmpty()) {
            for (node in matching) {
                var target: AccessibilityNodeInfo? = node
                while (target != null && !target.isClickable) {
                    val parent = target.parent
                    target = parent
                }
                if (target != null && target.isClickable) {
                    val success = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    target.recycle()
                    root.recycle()
                    return success
                }
            }
        }

        // Deep fallback: traverse nodes and check contentDescription or lowercase contains
        var found = false
        fun searchDeep(node: AccessibilityNodeInfo): Boolean {
            val t = node.text?.toString() ?: ""
            val cd = node.contentDescription?.toString() ?: ""
            if (t.contains(cleanQuery, ignoreCase = true) || cd.contains(cleanQuery, ignoreCase = true)) {
                var target: AccessibilityNodeInfo? = node
                while (target != null && !target.isClickable) {
                    target = target.parent
                }
                if (target != null && target.isClickable) {
                    found = target.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                    return true
                }
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (searchDeep(child)) {
                    child.recycle()
                    return true
                }
                child.recycle()
            }
            return false
        }

        searchDeep(root)
        root.recycle()
        return found
    }

    /**
     * Dispatches tap gesture at raw screen coordinates.
     */
    fun clickAt(x: Float, y: Float): Boolean {
        val path = Path().apply {
            moveTo(x, y)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 50))
            .build()

        val done = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        val success = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                done.set(true)
                latch.countDown()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                done.set(false)
                latch.countDown()
            }
        }, null)

        if (!success) return false
        try {
            latch.await(1000, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {}

        return done.get()
    }

    /**
     * Type text into editable field.
     */
    fun typeText(text: String, fieldHint: String? = null): Boolean {
        val root = rootInActiveWindow ?: return false

        var targetNode: AccessibilityNodeInfo? = null

        // 1. Try currently focused input field
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (focused != null && focused.isEditable) {
            targetNode = focused
        }

        // 2. If not found or hint given, find matching editable node
        if (targetNode == null) {
            fun findEditable(node: AccessibilityNodeInfo): Boolean {
                if (node.isEditable) {
                    if (fieldHint.isNullOrBlank()) {
                        targetNode = node
                        return true
                    } else {
                        val t = node.text?.toString() ?: ""
                        val h = node.hintText?.toString() ?: ""
                        val cd = node.contentDescription?.toString() ?: ""
                        if (t.contains(fieldHint, ignoreCase = true) ||
                            h.contains(fieldHint, ignoreCase = true) ||
                            cd.contains(fieldHint, ignoreCase = true)
                        ) {
                            targetNode = node
                            return true
                        }
                    }
                }
                for (i in 0 until node.childCount) {
                    val child = node.getChild(i) ?: continue
                    if (findEditable(child)) return true
                    child.recycle()
                }
                return false
            }
            findEditable(root)
        }

        if (targetNode == null) {
            root.recycle()
            return false
        }

        targetNode!!.performAction(AccessibilityNodeInfo.ACTION_FOCUS)

        // Try ACTION_SET_TEXT
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val setOk = targetNode!!.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)

        if (!setOk) {
            // Fallback: clipboard copy and paste
            try {
                val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                val clip = ClipData.newPlainText("eva_text", text)
                clipboard.setPrimaryClip(clip)
                targetNode!!.performAction(AccessibilityNodeInfo.ACTION_PASTE)
            } catch (e: Exception) {
                Log.e(TAG, "Fallback paste error", e)
            }
        }

        targetNode!!.recycle()
        root.recycle()
        return true
    }

    fun pressEnter(): Boolean {
        // Find focused editable node and perform search / action
        val root = rootInActiveWindow ?: return false
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        focused?.let {
            it.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            it.recycle()
        }
        root.recycle()
        return true
    }

    fun scroll(direction: String): Boolean {
        val root = rootInActiveWindow ?: return false
        val forward = direction.equals("down", ignoreCase = true) || direction.equals("forward", ignoreCase = true)

        var scrolled = false
        fun findScrollable(node: AccessibilityNodeInfo): Boolean {
            if (node.isScrollable) {
                val action = if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
                scrolled = node.performAction(action)
                if (scrolled) return true
            }
            for (i in 0 until node.childCount) {
                val child = node.getChild(i) ?: continue
                if (findScrollable(child)) {
                    child.recycle()
                    return true
                }
                child.recycle()
            }
            return false
        }

        findScrollable(root)
        root.recycle()

        if (!scrolled) {
            // Fallback gesture swipe
            val metrics = resources.displayMetrics
            val cx = metrics.widthPixels / 2f
            val startY = if (forward) metrics.heightPixels * 0.75f else metrics.heightPixels * 0.25f
            val endY = if (forward) metrics.heightPixels * 0.25f else metrics.heightPixels * 0.75f
            return swipe(cx, startY, cx, endY)
        }
        return scrolled
    }

    fun swipe(startX: Float, startY: Float, endX: Float, endY: Float): Boolean {
        val path = Path().apply {
            moveTo(startX, startY)
            lineTo(endX, endY)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 300))
            .build()

        val done = AtomicBoolean(false)
        val latch = CountDownLatch(1)

        val dispatched = dispatchGesture(gesture, object : GestureResultCallback() {
            override fun onCompleted(gestureDescription: GestureDescription?) {
                done.set(true)
                latch.countDown()
            }

            override fun onCancelled(gestureDescription: GestureDescription?) {
                done.set(false)
                latch.countDown()
            }
        }, null)

        if (!dispatched) return false
        try {
            latch.await(1000, TimeUnit.MILLISECONDS)
        } catch (_: InterruptedException) {}
        return done.get()
    }

    fun pressBack(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_BACK)
    }

    fun pressHome(): Boolean {
        return performGlobalAction(GLOBAL_ACTION_HOME)
    }
}
