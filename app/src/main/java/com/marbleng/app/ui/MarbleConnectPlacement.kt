package com.marbleng.app.ui

// MARBLE_CONNECT_PLACEMENT_V123
// MARBLE_CONNECT_BUTTON_STYLES_V132
// MARBLE_HOME_V137
//
// One file, no Compose: where a connection control sits inside a Home presentation is a pure
// mapping from the user's choice, so it stays unit-testable without a UI runtime.

import com.marbleng.app.model.ConnectButtonStyle

/**
 * Where a connection control lives inside a Home presentation.
 *
 * The silhouettes are physically different metaphors, so they never share one spot:
 *
 *  - [HERO_CENTER] the round shutter is the focal instrument → centred in the hero;
 *  - [HERO_FLOOR]  the wide drag track is a console floor bar → docked at the hero floor, low
 *    and full width, with settle room underneath the drag travel;
 *  - [POWER_DOCK]  the rectangular power bar is a piece of hardware → docks below the hero in
 *    its own slim capsule, clear of the orbiting artwork;
 *  - [PAGE_FLOOR]  the travelling-band bar → docked at the floor of the PAGE, so the band stays
 *    visible without scrolling;
 *  - [PAGE_PILL]   the floating circular shutter → pinned to the bottom-end corner of the page
 *    (v2rayNG-style, clear of the nav dock and the system gesture bar), where a thumb already
 *    rests. It is a round 76 dp instrument, never a centred bar, so it floats above the content
 *    instead of consuming a hero row.
 */
internal enum class ConnectControlZone {
    HERO_CENTER, HERO_FLOOR, POWER_DOCK, PAGE_FLOOR, PAGE_PILL
}

/** True when the presentation pins the control to the floor of the page instead of the hero. */
internal fun ConnectControlZone.isPageDocked(): Boolean =
    this == ConnectControlZone.PAGE_FLOOR || this == ConnectControlZone.PAGE_PILL

internal fun connectControlZone(style: ConnectButtonStyle): ConnectControlZone = when (style) {
    ConnectButtonStyle.ROUND -> ConnectControlZone.HERO_CENTER
    ConnectButtonStyle.SLIDE -> ConnectControlZone.HERO_FLOOR
    ConnectButtonStyle.CLASSIC -> ConnectControlZone.POWER_DOCK
    ConnectButtonStyle.STREAM -> ConnectControlZone.PAGE_FLOOR
    ConnectButtonStyle.FLOATING -> ConnectControlZone.PAGE_PILL
}
