package com.example.bloappassistant.services

import android.accessibilityservice.AccessibilityButtonController
import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Rect
import android.os.Bundle
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import org.json.JSONObject

class InspectorAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "InspectorService"
        private const val TARGET_PACKAGE = "in.gov.eci.bloapp"
        private var lastEventTime = 0L
    }

    private var lastTextViewContext = ""

    private val autofillReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "com.example.bloappassistant.AUTOFILL_DATA") {
                val jsonData = intent.getStringExtra("json_data")
                if (jsonData != null) {
                    performAutofill(jsonData)
                }
            }
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        val filter = IntentFilter("com.example.bloappassistant.AUTOFILL_DATA")
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(autofillReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            registerReceiver(autofillReceiver, filter)
        }

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            accessibilityButtonController.registerAccessibilityButtonCallback(
                object : AccessibilityButtonController.AccessibilityButtonCallback() {
                    override fun onClicked(controller: AccessibilityButtonController) {
                        Log.d(TAG, "Stickman clicked! Launching camera...")
                        val captureIntent = Intent(this@InspectorAccessibilityService, com.example.bloappassistant.ui.CaptureActivity::class.java)
                        captureIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        startActivity(captureIntent)
                    }
                }
            )
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(autofillReceiver)
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
                // Log.d(TAG, "Dumping Accessibility Tree for: $packageName")
                // dumpNodeTree(rootNode, 0)
                // Disabled dumping to keep logs clean for now
                rootNode.recycle()
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

            }
        }
    }

    private fun performAutofill(jsonString: String) {
        try {
            val jsonObject = JSONObject(jsonString)
            val rootNode = rootInActiveWindow
            if (rootNode != null) {
                lastTextViewContext = ""
                traverseAndFill(rootNode, jsonObject)
                rootNode.recycle()
            } else {
                Log.e(TAG, "Cannot autofill: Root node is null")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to parse JSON for autofill", e)
        }
    }

    private fun traverseAndFill(node: AccessibilityNodeInfo, json: JSONObject) {
        val className = node.className?.toString() ?: ""
        val text = node.text?.toString() ?: ""
        
        // Track context for EPIC fields
        if (className == "android.widget.TextView" && text.isNotEmpty()) {
            lastTextViewContext = text
        }

        var hint = ""
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            hint = node.hintText?.toString() ?: ""
        }
        
        if (node.isEditable) {
            var valueToInject: String? = null
            val lowerHint = hint.lowercase()

            if (lowerHint.contains("date of birth")) {
                valueToInject = json.optString("date_of_birth", "")
            } else if (lowerHint.contains("aadhaar no")) {
                valueToInject = json.optString("aadhaar_no", "")
            } else if (lowerHint.contains("mobile no")) {
                valueToInject = json.optString("mobile_no", "")
            } else if (lowerHint.contains("father's/legal guardian name")) {
                valueToInject = json.optString("father_name", "")
            } else if (lowerHint.contains("mother's name")) {
                valueToInject = json.optString("mother_name", "")
            } else if (lowerHint.contains("spouse's name")) {
                valueToInject = json.optString("spouse_name", "")
            } else if (lowerHint.contains("epic number")) {
                val lowerContext = lastTextViewContext.lowercase()
                if (lowerContext.contains("spouse's epic")) {
                    valueToInject = json.optString("spouse_epic", "")
                }
            }

            if (!valueToInject.isNullOrEmpty()) {
                Log.d(TAG, "Injecting '$valueToInject' into field with hint '$hint'")
                val arguments = Bundle()
                arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, valueToInject)
                node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
            }
        }

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                traverseAndFill(child, json)
                child.recycle()
            }
        }
    }

    override fun onInterrupt() {
        Log.d(TAG, "Service Interrupted")
    }
}
