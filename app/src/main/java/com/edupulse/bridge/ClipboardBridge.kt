package com.edupulse.bridge

import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.widget.Toast

object ClipboardBridge {
    fun readFromLaptopClipboard(context: Context): String? {
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return null

        if (clipboard.hasPrimaryClip() && 
            (clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_PLAIN) == true ||
             clipboard.primaryClipDescription?.hasMimeType(ClipDescription.MIMETYPE_TEXT_HTML) == true)
        ) {
            val text = clipboard.primaryClip?.getItemAt(0)?.text?.toString()?.trim()
            if (!text.isNullOrBlank()) {
                Toast.makeText(context, "Synced from Laptop via Office Kit", Toast.LENGTH_SHORT).show()
                return text
            }
        }
        Toast.makeText(context, "Clipboard empty. Copy question on laptop first!", Toast.LENGTH_SHORT).show()
        return null
    }
}
