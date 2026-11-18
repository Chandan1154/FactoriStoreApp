package com.factori.storeapp

import android.net.Uri
import java.net.URI
import java.net.URISyntaxException
import java.util.Locale

internal object StoreUrlPolicy {
    private val paymentHosts = setOf(
        "pay.google.com",
        "paytm.com",
        "phonepe.com",
        "razorpay.com",
        "cashfree.com",
        "payu.in",
        "ccavenue.com",
        "stripe.com"
    )

    fun normalizeStorefrontUrl(rawUrl: String): String? {
        val trimmed = rawUrl.trim()
        if (trimmed.isEmpty()) return null
        val candidate = if ("://" in trimmed) trimmed else "https://$trimmed"
        val uri = try {
            URI(candidate)
        } catch (_: URISyntaxException) {
            return null
        }
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return null
        if (!uri.scheme.equals("https", ignoreCase = true) ||
            uri.rawUserInfo != null ||
            (uri.port != -1 && uri.port != 443) ||
            host == "localhost" ||
            listOf(".local", ".localhost", ".internal", ".test", ".example", ".invalid")
                .any(host::endsWith) ||
            !host.contains('.') ||
            host.matches(Regex("""[0-9.]+"""))
        ) {
            return null
        }
        val authority = host + if (uri.port == -1) "" else ":${uri.port}"
        val normalized = URI(
            "https://$authority${uri.rawPath.orEmpty()}" +
                (uri.rawQuery?.let { "?$it" } ?: "") +
                (uri.rawFragment?.let { "#$it" } ?: "")
        )
        return normalized.normalize().toASCIIString()
    }

    fun isStorefrontHost(candidate: String, storefrontHost: String): Boolean =
        candidate.equals(storefrontHost, ignoreCase = true) ||
            candidate.endsWith(".$storefrontHost", ignoreCase = true)

    fun isSupportedPaymentHost(candidate: String): Boolean =
        paymentHosts.any { isStorefrontHost(candidate, it) }

    fun isAllowedWebViewUrl(candidate: Uri, storefrontHost: String): Boolean {
        if (!candidate.scheme.equals("https", ignoreCase = true)) return false
        val host = candidate.host ?: return false
        if (candidate.userInfo != null || candidate.port != -1 && candidate.port != 443) return false
        return isStorefrontHost(host, storefrontHost) || isSupportedPaymentHost(host)
    }
}
