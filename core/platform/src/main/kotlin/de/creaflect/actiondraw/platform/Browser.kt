package de.creaflect.actiondraw.platform

import java.awt.Desktop
import java.net.URI

/** Opens an address outside the app. Swappable, so a test can see a link followed without a browser opening. */
fun interface Browser {
    /** True when the address was handed over. */
    fun open(url: String): Boolean
}

/** The desktop's own browser: whatever the system opens web addresses with. */
object DesktopBrowser : Browser {
    /** What the browser is handed for [url]: an address without a scheme is taken to be https. */
    fun addressOf(url: String): URI = URI(if (url.contains("://")) url else "https://$url")

    override fun open(url: String): Boolean = runCatching { Desktop.getDesktop().browse(addressOf(url)) }.isSuccess
}
