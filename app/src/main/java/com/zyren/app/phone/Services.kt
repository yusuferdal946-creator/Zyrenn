package com.zyren.app.phone

import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.content.Intent
import android.os.Bundle
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

/** Son bildirimler yalnızca bellekte tutulur (diske yazılmaz). */
object NotificationStore {
    data class Item(val pkg: String, val title: String, val text: String, val time: Long)
    private val items = ArrayDeque<Item>()
    @Synchronized fun add(i: Item) { items.addFirst(i); while (items.size > 50) items.removeLast() }
    @Synchronized fun recent(n: Int): List<Item> = items.take(n)
}

class ZyrenNotificationListener : NotificationListenerService() {
    override fun onNotificationPosted(sbn: StatusBarNotification) {
        if (sbn.packageName == packageName) return
        val n = sbn.notification ?: return
        if ((n.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return
        val title = n.extras.getCharSequence(Notification.EXTRA_TITLE)?.toString().orEmpty()
        val text = n.extras.getCharSequence(Notification.EXTRA_TEXT)?.toString().orEmpty()
        if (title.isBlank() && text.isBlank()) return
        NotificationStore.add(NotificationStore.Item(sbn.packageName, title, text, sbn.postTime))
    }
}

/** Ekran okuma / dokunma / geri-ana ekran. Kullanıcı Ayarlar > Erişilebilirlik'ten açmadıkça çalışmaz. */
class ZyrenAccessibilityService : AccessibilityService() {
    companion object { @Volatile var instance: ZyrenAccessibilityService? = null }

    override fun onServiceConnected() { instance = this }
    override fun onUnbind(intent: Intent?): Boolean { instance = null; return super.onUnbind(intent) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    fun screenText(): String {
        val root = rootInActiveWindow ?: return "Ekran okunamadı"
        val sb = StringBuilder()
        fun walk(n: AccessibilityNodeInfo?) {
            if (n == null || sb.length > 3000) return
            val t = n.text ?: n.contentDescription
            if (!t.isNullOrBlank()) sb.append(t).append('\n')
            for (i in 0 until n.childCount) walk(n.getChild(i))
        }
        walk(root)
        return sb.toString().take(3000)
    }

    fun clickText(text: String): Boolean {
        val root = rootInActiveWindow ?: return false
        for (n in root.findAccessibilityNodeInfosByText(text)) {
            var c: AccessibilityNodeInfo? = n
            while (c != null && !c.isClickable) c = c.parent
            if (c != null && c.performAction(AccessibilityNodeInfo.ACTION_CLICK)) return true
        }
        return false
    }

    fun typeText(text: String): Boolean {
        val f = rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: return false
        val args = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text) }
        return f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
    }

    fun global(action: String): Boolean = when (action) {
        "geri" -> performGlobalAction(GLOBAL_ACTION_BACK)
        "ana_ekran" -> performGlobalAction(GLOBAL_ACTION_HOME)
        "son_uygulamalar" -> performGlobalAction(GLOBAL_ACTION_RECENTS)
        "bildirimler" -> performGlobalAction(GLOBAL_ACTION_NOTIFICATIONS)
        else -> false
    }
}
