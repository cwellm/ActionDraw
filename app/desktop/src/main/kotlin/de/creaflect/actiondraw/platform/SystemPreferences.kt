package de.creaflect.actiondraw.platform

import com.sun.jna.Native
import com.sun.jna.ptr.IntByReference
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions

/** What the person set in Windows that the lib should honour — the desktop's [HostPreferences]. */
object SystemPreferences : HostPreferences {
    /**
     * Windows' *Show animations in Windows* (`SPI_GETCLIENTAREAANIMATION`): off means the person
     * asked for less movement. Anywhere else, or if it cannot be read, movement is on.
     */
    override fun prefersReducedMotion(): Boolean = runCatching {
        if (!System.getProperty("os.name").orEmpty().startsWith("Windows")) return false
        val on = IntByReference(1)
        User32Spi.INSTANCE.SystemParametersInfoW(SPI_GETCLIENTAREAANIMATION, 0, on, 0) && on.value == 0
    }.getOrDefault(false)

    private const val SPI_GETCLIENTAREAANIMATION = 0x1042
}

@Suppress("FunctionName")
private interface User32Spi : StdCallLibrary {
    fun SystemParametersInfoW(uiAction: Int, uiParam: Int, pvParam: IntByReference, fWinIni: Int): Boolean

    companion object {
        val INSTANCE: User32Spi by lazy { Native.load("user32", User32Spi::class.java, W32APIOptions.DEFAULT_OPTIONS) }
    }
}
