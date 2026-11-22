package com.zyren.app.core

import android.content.Context
import android.content.pm.PackageManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.withTimeoutOrNull

/** Android çalışma-zamanı izinlerini MainActivity üzerinden ister; sonucu bekler. */
object PermissionBridge {
    class Req(val perms: Array<String>, val result: CompletableDeferred<Boolean>)
    val requests = MutableSharedFlow<Req>(extraBufferCapacity = 4)

    fun has(ctx: Context, perm: String) = ctx.checkSelfPermission(perm) == PackageManager.PERMISSION_GRANTED

    suspend fun ensure(ctx: Context, vararg perms: String): Boolean {
        val missing = perms.filter { !has(ctx, it) }
        if (missing.isEmpty()) return true
        val d = CompletableDeferred<Boolean>()
        requests.emit(Req(missing.toTypedArray(), d))
        return withTimeoutOrNull(60_000) { d.await() } ?: false
    }
}
