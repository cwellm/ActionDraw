package de.creaflect.actiondraw.platform

/** What the person set in their operating system that the lib should honour. */
fun interface HostPreferences {
    /** True when they asked their system for less movement on screen. */
    fun prefersReducedMotion(): Boolean
}
