package com.example.models

import android.graphics.Rect

data class ScreenNode(
    val index: Int = 0,
    val depth: Int = 0,
    val text: String = "",
    val contentDescription: String = "",
    val className: String = "",
    val isClickable: Boolean = false,
    val isEditable: Boolean = false,
    val isScrollable: Boolean = false,
    val isPassword: Boolean = false,
    val bounds: Rect = Rect(),
    val viewId: String? = null
) {
    val centerX: Int get() = bounds.centerX()
    val centerY: Int get() = bounds.centerY()

    /**
     * Compact single-line summary formatted for LLMs.
     * Guaranteed redaction of password fields.
     */
    fun toLlmRepresentation(): String {
        val safeText = if (isPassword) "[PASSWORD_PROTECTED_FIELD]" else text.trim()
        val safeDesc = if (isPassword) "[PROTECTED]" else contentDescription.trim()

        val label = when {
            safeText.isNotEmpty() -> "\"$safeText\""
            safeDesc.isNotEmpty() -> "desc:\"$safeDesc\""
            else -> "<unlabeled>"
        }

        val tags = mutableListOf<String>()
        if (isClickable) tags.add("clickable")
        if (isEditable) tags.add("editable")
        if (isScrollable) tags.add("scrollable")
        if (isPassword) tags.add("password_redacted")

        val tagStr = if (tags.isNotEmpty()) "[${tags.joinToString(",")}]" else ""
        val coords = "(${bounds.centerX()},${bounds.centerY()})"
        val shortClass = className.substringAfterLast('.')

        return "[$index] $shortClass $label $tagStr at $coords"
    }
}

data class ScreenDump(
    val packageName: String,
    val nodes: List<ScreenNode>,
    val timestamp: Long = System.currentTimeMillis()
) {
    fun toCompactDescription(compress: Boolean = true): String {
        val sb = StringBuilder()
        sb.append("Current App Package: ").append(packageName).append("\n")
        sb.append("Screen Elements:\n")

        val relevantNodes = if (compress) {
            // Deduplicate and filter out empty invisible non-interactive nodes
            nodes.filter { node ->
                (node.isClickable || node.isEditable || node.isScrollable ||
                 node.text.isNotBlank() || node.contentDescription.isNotBlank()) &&
                node.bounds.width() > 0 && node.bounds.height() > 0
            }.distinctBy { "${it.text}|${it.contentDescription}|${it.bounds.centerX()}|${it.bounds.centerY()}" }
        } else {
            nodes.filter { it.bounds.width() > 0 && it.bounds.height() > 0 }
        }

        if (relevantNodes.isEmpty()) {
            sb.append("  (No interactive elements detected on screen)\n")
        } else {
            relevantNodes.take(60).forEach { node ->
                sb.append("  ").append(node.toLlmRepresentation()).append("\n")
            }
        }
        return sb.toString()
    }
}
