package app.rodalog.sync

import android.content.Context
import android.content.Intent
import com.google.android.gms.auth.api.identity.AuthorizationRequest
import com.google.android.gms.auth.api.identity.AuthorizationResult
import com.google.android.gms.auth.api.identity.Identity
import com.google.android.gms.common.api.Scope
import kotlinx.coroutines.tasks.await

/** Autorización de Google para leer/escribir hojas de cálculo del usuario. */
object Auth {
    const val SCOPE = "https://www.googleapis.com/auth/spreadsheets"

    private fun request() = AuthorizationRequest.builder()
        .setRequestedScopes(listOf(Scope(SCOPE)))
        .build()

    /** Pide autorización. Si hay que mostrar pantalla de permisos, result.hasResolution() es true. */
    suspend fun authorize(ctx: Context): AuthorizationResult =
        Identity.getAuthorizationClient(ctx).authorize(request()).await()

    fun fromIntent(ctx: Context, data: Intent?): AuthorizationResult =
        Identity.getAuthorizationClient(ctx).getAuthorizationResultFromIntent(data)

    /** Token sin interacción; null si el usuario aún no ha dado permiso. */
    suspend fun silentToken(ctx: Context): String? = try {
        val r = authorize(ctx)
        if (r.hasResolution()) null else r.accessToken
    } catch (e: Exception) {
        null
    }
}
