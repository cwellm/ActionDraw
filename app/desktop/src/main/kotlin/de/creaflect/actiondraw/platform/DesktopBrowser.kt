package de.creaflect.actiondraw.platform

import java.awt.Desktop
import java.net.URI

/** The desktop's own browser: whatever the system opens web addresses with. */
object DesktopBrowser : Browser {
    /** What the browser is handed for [url]: an address without a scheme is taken to be https. */
    fun addressOf(url: String): URI = URI(if (url.contains("://")) url else "https://$url")

    override fun open(url: String): Boolean = runCatching { Desktop.getDesktop().browse(addressOf(url)) }.isSuccess
}
