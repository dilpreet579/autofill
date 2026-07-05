package com.example.bloappassistant.services

import android.accessibilityservice.AccessibilityService
import android.graphics.Rect
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class InspectorAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "InspectorService"
        // If we know the BLO app package name, we can filter here, e.g. "com.eci.blo"
        // For now, let's just log everything to see what's what.
        private var lastEventTime = 0L
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event == null) return

        // To avoid spamming, let's only dump on Window State Changed or if it's been a while
        val currentTime = System.currentTimeMillis()
        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED || 
            (event.eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED && (currentTime - lastEventTime > 5000))) {
            
            lastEventTime = currentTime
            
            val packageName = event.packageName?.toString() ?: "Unknown"
            Log.d(TAG, "--- Accessibility Event: ${AccessibilityEvent.eventTypeToString(event.eventType)} in $packageName ---")

            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                Log.d(TAG, "Dumping Accessibility Tree for: $packageName")
                dumpNodeTree(rootNode, 0)
                rootNode.recycle() // Recycle when done
            } else {
                Log.d(TAG, "Root node is null")
            }
        }
    }

    private fun dumpNodeTree(node: AccessibilityNodeInfo, depth: Int) {
        val indent = "  ".repeat(depth)
        
        val className = node.className ?: "UnknownClass"
        val text = node.text?.toString() ?: ""
        
        var hint = ""
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            hint = node.hintText?.toString() ?: ""
        }
        
        val contentDesc = node.contentDescription?.toString() ?: ""
        val viewId = node.viewIdResourceName ?: "NoViewId"
        val isEditable = node.isEditable
        val isClickable = node.isClickable
        
        val bounds = Rect()
        node.getBoundsInScreen(bounds)

        val logMessage = buildString {
            append(indent)
            append("[$className]")
            if (text.isNotEmpty()) append(" Text: '$text'")
            if (hint.isNotEmpty()) append(" Hint: '$hint'")
            if (contentDesc.isNotEmpty()) append(" Desc: '$contentDesc'")
            append(" ID: $viewId")
            if (isEditable) append(" [Editable]")
            if (isClickable) append(" [Clickable]")
            append(" Bounds: ${bounds.toShortString()}")
        }

        Log.d(TAG, logMessage)

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                dumpNodeTree(child, depth + 1)
                child.recycle()
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }
}
