package com.marbleng.app.core

import org.json.JSONObject
import java.util.Base64

/** XHTTP share links carry `extra` as either percent-encoded JSON or base64url JSON. */
object XhttpExtra {
    fun parse(raw: String): JSONObject {
        val value = raw.trim()
        require(value.isNotEmpty() && value.length <= 16_384) { "Invalid XHTTP extra" }
        val json = if (value.startsWith('{')) value else {
            val bytes = runCatching { Base64.getUrlDecoder().decode(value) }
                .getOrElse { Base64.getDecoder().decode(value) }
            require(bytes.size <= 16_384) { "XHTTP extra is too large" }
            String(bytes, Charsets.UTF_8)
        }
        return JSONObject(json)
    }
}
