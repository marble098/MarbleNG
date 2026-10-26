package com.marbleng.app.model

/**
 * MARBLE_PERSIAN_CONNECT_DOCK_COUNCIL_V233 — three connection buttons, three real behaviours.
 *
 * The council refused "three skins over one click". Each action names the repository entry point
 * it is allowed to call ([behavior]) and the light it turns on ([light]); both are machine-checked
 * so a future refactor cannot quietly make two buttons do the same thing again.
 */
enum class ConnectDeckLight {
    /** A core that ignites: one halo breathing around a bright centre. */
    IGNITION,

    /** A ring that sweeps: an arc orbiting the core while the selector is thinking. */
    ORBIT,

    /** Two rings that travel outward: a local-only, light-as-air connection. */
    RIPPLE
}

enum class ConnectDeckAction(
    val id: String,
    val light: ConnectDeckLight,
    val behavior: String,
    val titleKey: String,
    val hintKey: String
) {
    QUICK(
        id = "quick",
        light = ConnectDeckLight.IGNITION,
        behavior = "reconnect_last_or_auto",
        titleKey = "Quick connect",
        hintKey = "Last route, one tap"
    ),
    SMART(
        id = "smart",
        light = ConnectDeckLight.ORBIT,
        behavior = "auto_selector_winner",
        titleKey = "Smart connect",
        hintKey = "Best route, picked for you"
    ),
    LIGHT(
        id = "light",
        light = ConnectDeckLight.RIPPLE,
        behavior = "local_proxy_only",
        titleKey = "Light connect",
        hintKey = "Local SOCKS, no device VPN"
    );

    companion object {
        fun fromId(raw: String?): ConnectDeckAction =
            entries.firstOrNull { it.id.equals(raw?.trim(), ignoreCase = true) } ?: QUICK
    }
}

object ConnectDeckCatalog {
    /** The three buttons are always shown in this order; their ids are stable across releases. */
    val ordered: List<ConnectDeckAction> = listOf(
        ConnectDeckAction.QUICK,
        ConnectDeckAction.SMART,
        ConnectDeckAction.LIGHT
    )

    fun behaviorOf(id: String?): String = fromId(id).behavior

    /** True only when every button has its own light and its own repository entry point. */
    fun isIndependent(): Boolean =
        ordered.size >= 3 &&
            ordered.map { it.light }.toSet().size == ordered.size &&
            ordered.map { it.behavior }.toSet().size == ordered.size &&
            ordered.map { it.id }.toSet().size == ordered.size
}
