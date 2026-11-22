package com.zyren.app

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.zyren.app.core.memory.ZyrenDatabase
import com.zyren.app.core.security.Capability
import com.zyren.app.core.security.PermissionManager
import com.zyren.app.core.security.SecureStore
import com.zyren.app.core.workspace.Workspace
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

/** Emülatör/cihazda çalışır (CI'da yalnızca elle tetiklenen iş). Keystore, Room ve Workspace'i gerçek Android ile sınar. */
@RunWith(AndroidJUnit4::class)
class DeviceSmokeTest {
    private val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test fun apiKeyEncryptedRoundTrip() {
        val s = SecureStore(ctx); s.setApiKey("t1", "gizli-123")
        try {
            assertEquals("gizli-123", s.apiKey("t1"))
            val raw = ctx.getSharedPreferences("zyren_keys", android.content.Context.MODE_PRIVATE).getString("k_t1", "")!!
            assertFalse(raw.contains("gizli-123"))
        } finally { s.setApiKey("t1", null) }
    }

    @Test fun workspaceBlocksEscapeAndReservedAndDeniesWithoutApproval() = runBlocking {
        val db = Room.inMemoryDatabaseBuilder(ctx, ZyrenDatabase::class.java).build()
        val perms = PermissionManager(ctx, db.auditDao())
        val ws = Workspace(ctx, perms)
        assertTrue(ws.write("../disari.txt", "x").isFailure)
        assertTrue(ws.write(".zyren/x.json", "x").isFailure)
        assertTrue(ws.write("selftest-a.txt", "1").isSuccess)
        assertTrue("onay penceresi yokken üzerine yazma REDDEDİLMELİ", ws.write("selftest-a.txt", "2").isFailure)
        perms.setEnabled(Capability.FILE_DELETE, true)
        assertTrue("onay yokken silme REDDEDİLMELİ", ws.delete("selftest-a.txt").isFailure)
        db.close()
    }
}
