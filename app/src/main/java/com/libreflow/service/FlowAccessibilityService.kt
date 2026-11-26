package com.libreflow.service

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Accessibility service for target input field tracking (via FNV-1a NodeIdentityHash),
 * smart placeholder overwriting (Chrome, WhatsApp, Keep), and cascading text injection
 * (ACTION_PASTE -> ACTION_SET_TEXT -> clipboard fallback).
 */
class FlowAccessibilityService : AccessibilityService() {

    companion object {
        @Volatile
        var instance: FlowAccessibilityService? = null
            private set

        private val KNOWN_PLACEHOLDERS = setOf(
            "search google or type url",
            "search or type web address",
            "message",
            "type a message",
            "note",
            "title",
            "write a reply",
            "ask anything",
            "search"
        )

        private val PLACEHOLDER_PREFIX_REGEX = Regex("""^(search|type|write|enter)\s+.*""", RegexOption.IGNORE_CASE)
    }

    @Volatile
    var activeTargetPackage: String? = null
        private set

    private var activeTargetNode: AccessibilityNodeInfo? = null
    private var targetNodeHash: Long = 0L
    private val mainHandler = Handler(Looper.getMainLooper())

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onDestroy() {
        super.onDestroy()
        activeTargetNode = null
        if (instance === this) instance = null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        event ?: return
        val pkg = event.packageName?.toString()
        if (!pkg.isNullOrBlank() && pkg != packageName) {
            activeTargetPackage = pkg
        }
        if (event.eventType == AccessibilityEvent.TYPE_VIEW_FOCUSED ||
            event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
        ) {
            captureFocusedNode()
        }
    }

    override fun onInterrupt() = Unit

    fun captureFocusedNode() {
        val root = rootInActiveWindow ?: return
        val focused = root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return
        if (focused.packageName?.toString() == packageName) return

        activeTargetNode = focused
        activeTargetPackage = focused.packageName?.toString()
        targetNodeHash = computeNodeIdentityHash(focused)
    }

    fun insertText(text: String): Boolean {
        if (text.isBlank()) return false
        val node = resolveCurrentTargetNode()
        if (node == null) {
            copyToClipboard(text)
            return false
        }

        val currentText = node.text?.toString() ?: ""
        val hintText = node.hintText?.toString() ?: ""
        val isPlaceholder = isPlaceholderText(currentText, hintText)

        // 1. Primary: ACTION_PASTE (cleanly overwrites placeholders & respects cursor/selection)
        val pasted = pasteTextIntoNode(node, text, clearPlaceholder = isPlaceholder)
        if (pasted) return true

        // 2. Fallback: ACTION_SET_TEXT
        val resolved = if (isPlaceholder || currentText.isBlank()) {
            text
        } else {
            val separator = if (currentText.endsWith(" ") || currentText.endsWith("\n")) "" else " "
            currentText + separator + text
        }
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, resolved)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)) {
            return true
        }

        // 3. Final fallback: copy to clipboard
        copyToClipboard(text)
        return false
    }

    private fun resolveCurrentTargetNode(): AccessibilityNodeInfo? {
        val root = rootInActiveWindow
        val liveFocused = root?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
        if (liveFocused != null && liveFocused.packageName?.toString() != packageName) {
            val liveHash = computeNodeIdentityHash(liveFocused)
            if (liveHash == targetNodeHash || activeTargetNode == null) {
                return liveFocused
            }
        }
        return activeTargetNode?.takeIf { it.refresh() } ?: liveFocused
    }

    private fun pasteTextIntoNode(
        node: AccessibilityNodeInfo,
        text: String,
        clearPlaceholder: Boolean
    ): Boolean {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return false
        val previousClip = cm.primaryClip

        if (clearPlaceholder && !node.text.isNullOrEmpty()) {
            val selectAll = Bundle().apply {
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, 0)
                putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, node.text.length)
            }
            node.performAction(AccessibilityNodeInfo.ACTION_SET_SELECTION, selectAll)
        }

        cm.setPrimaryClip(ClipData.newPlainText("LibreFlow", text))
        val success = node.performAction(AccessibilityNodeInfo.ACTION_PASTE)

        if (previousClip != null) {
            mainHandler.postDelayed({
                runCatching { cm.setPrimaryClip(previousClip) }
            }, 350L)
        }
        return success
    }

    private fun copyToClipboard(text: String) {
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager ?: return
        cm.setPrimaryClip(ClipData.newPlainText("LibreFlow", text))
    }

    private fun isPlaceholderText(current: String, hint: String): Boolean {
        val trimmed = current.trim().lowercase()
        if (trimmed.isEmpty()) return true
        if (hint.isNotBlank() && trimmed == hint.trim().lowercase()) return true
        if (trimmed in KNOWN_PLACEHOLDERS) return true
        return PLACEHOLDER_PREFIX_REGEX.matches(trimmed) && trimmed.length <= 42
    }

    private fun computeNodeIdentityHash(node: AccessibilityNodeInfo): Long {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        val raw = "${node.packageName}|${node.className}|${node.viewIdResourceName}|${bounds.flattenToString()}"
        // FNV-1a 64-bit hash
        var hash = -0x340d631b7bdddcdbL
        for (ch in raw) {
            hash = hash xor ch.code.toLong()
            hash *= 0x100000001b3L
        }
        return hash
    }
}
