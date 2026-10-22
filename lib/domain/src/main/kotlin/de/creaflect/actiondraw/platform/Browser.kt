package de.creaflect.actiondraw.platform

/**
 * Opens an address outside the app, in whatever the host opens web addresses with. Swappable, so
 * a test can see a link followed without a browser opening.
 */
fun interface Browser {
    /** True when the address was handed over. */
    fun open(url: String): Boolean

    companion object {
        /** No browser: an address goes nowhere. Where a host has none, or a test wants none. */
        val None = Browser { false }
    }
}
