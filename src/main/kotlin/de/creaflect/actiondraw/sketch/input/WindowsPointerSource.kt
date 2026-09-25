package de.creaflect.actiondraw.sketch.input

import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.Structure
import com.sun.jna.platform.win32.User32
import com.sun.jna.platform.win32.WinDef.HWND
import com.sun.jna.platform.win32.WinDef.LPARAM
import com.sun.jna.platform.win32.WinDef.LRESULT
import com.sun.jna.platform.win32.WinDef.POINT
import com.sun.jna.platform.win32.WinDef.WPARAM
import com.sun.jna.platform.win32.WinUser.WNDENUMPROC
import com.sun.jna.platform.win32.WinUser.WindowProc
import com.sun.jna.win32.StdCallLibrary
import com.sun.jna.win32.W32APIOptions
import java.awt.Window

/**
 * Pen input on Windows through Windows Ink: the window procedures are subclassed, and on every
 * `WM_POINTER*` message for a pen `GetPointerPenInfo` gives pressure, tilt and rotation. The
 * original procedure is called for every message afterwards, so AWT still sees the pen as a
 * mouse and Compose keeps working as before — the pen data runs alongside, not instead.
 *
 * **Every window is hooked, not just the frame.** Windows sends a pointer message to the window
 * under the pen, and Compose draws into a heavyweight AWT canvas (skiko's `HardwareLayer`) with
 * a window handle of its own inside the frame: hooking the frame alone saw nothing, and the pen
 * arrived as a mouse at full pressure (LEARNINGS L6). So the frame and each of its child windows
 * get the procedure; positions are made client-relative to the frame either way, whose client
 * area is where Compose's own coordinates start.
 *
 * The callback runs on the thread that owns the window (AWT's toolkit thread), never on the
 * event dispatch thread; whoever listens hands the sample over.
 */
class WindowsPointerSource(private val window: Window) : PenSource {
    override val name: String = "Windows Ink (WM_POINTER)"

    private var frame: HWND? = null
    /** Each hooked window's original procedure, by handle. */
    private val previous = LinkedHashMap<Long, Pointer>()
    // A strong reference: JNA's callback thunk lives as long as this object does, and the windows
    // would call into freed memory if the callback were collected.
    private var proc: WindowProc? = null

    /** How many windows carry the hook — the frame and its children; 0 when not started. */
    val hookedWindows: Int get() = previous.size

    override fun start(onSample: (PenSample) -> Unit): Boolean {
        if (frame != null) return true
        val user32 = runCatching { User32Pointer.INSTANCE }.getOrElse { return false }
        val frameHandle = runCatching { HWND(Native.getWindowPointer(window)) }.getOrElse { return false }
        val callback = WindowProc { hWnd, uMsg, wParam, lParam ->
            if (uMsg == WM_POINTERDOWN || uMsg == WM_POINTERUPDATE || uMsg == WM_POINTERUP) {
                runCatching { read(user32, frameHandle, wParam) }.getOrNull()?.let(onSample)
            }
            val prev = previous[Pointer.nativeValue(hWnd.pointer)]
            if (prev != null) user32.CallWindowProcW(prev, hWnd, uMsg, wParam, lParam) else LRESULT(0)
        }
        proc = callback
        val targets = mutableListOf(frameHandle)
        runCatching {
            User32.INSTANCE.EnumChildWindows(frameHandle, WNDENUMPROC { child, _ -> targets += child; true }, null)
        }
        for (handle in targets) {
            val prev = user32.SetWindowLongPtrW(handle, GWLP_WNDPROC, callback)
            if (prev != null && Pointer.nativeValue(prev) != 0L) previous[Pointer.nativeValue(handle.pointer)] = prev
        }
        if (previous.isEmpty()) {
            proc = null
            return false
        }
        frame = frameHandle
        return true
    }

    override fun stop() {
        if (frame == null) return
        val user32 = runCatching { User32Pointer.INSTANCE }.getOrNull()
        for ((handle, prev) in previous) {
            runCatching { user32?.SetWindowLongPtrW(HWND(Pointer(handle)), GWLP_WNDPROC, prev) }
        }
        previous.clear()
        frame = null
        proc = null
    }

    private fun read(user32: User32Pointer, frameHandle: HWND, wParam: WPARAM): PenSample? {
        val pointerId = wParam.toInt() and 0xFFFF
        val type = IntArray(1)
        if (!user32.GetPointerType(pointerId, type)) return null
        val kind = when (type[0]) {
            PT_PEN -> PenSample.PointerKind.PEN
            PT_TOUCH -> PenSample.PointerKind.TOUCH
            PT_MOUSE -> PenSample.PointerKind.MOUSE
            else -> PenSample.PointerKind.OTHER
        }
        if (kind != PenSample.PointerKind.PEN) return null
        val info = POINTER_PEN_INFO()
        if (!user32.GetPointerPenInfo(pointerId, info)) return null
        val at = POINT(info.pointerInfo.ptPixelLocation.x, info.pointerInfo.ptPixelLocation.y)
        user32.ScreenToClient(frameHandle, at)
        val flags = info.pointerInfo.pointerFlags
        return PenSample(
            x = at.x.toFloat(),
            y = at.y.toFloat(),
            pressure = (info.pressure / 1024f).coerceIn(0f, 1f),
            tiltX = info.tiltX,
            tiltY = info.tiltY,
            rotation = info.rotation,
            contact = flags and POINTER_FLAG_INCONTACT != 0,
            barrel = info.penFlags and PEN_FLAG_BARREL != 0,
            eraser = info.penFlags and PEN_FLAG_ERASER != 0 || info.penFlags and PEN_FLAG_INVERTED != 0,
            pointerType = kind,
            timeNanos = System.nanoTime(),
        )
    }

    private companion object {
        const val GWLP_WNDPROC = -4
        const val WM_POINTERUPDATE = 0x0245
        const val WM_POINTERDOWN = 0x0246
        const val WM_POINTERUP = 0x0247
        const val PT_TOUCH = 2
        const val PT_PEN = 3
        const val PT_MOUSE = 4
        const val POINTER_FLAG_INCONTACT = 0x00000004
        const val PEN_FLAG_BARREL = 0x00000001
        const val PEN_FLAG_INVERTED = 0x00000002
        const val PEN_FLAG_ERASER = 0x00000004
    }
}

/** The few user32 entry points this needs, beyond what jna-platform already binds. */
@Suppress("FunctionName")
internal interface User32Pointer : StdCallLibrary {
    fun SetWindowLongPtrW(hWnd: HWND, nIndex: Int, dwNewLong: WindowProc): Pointer?
    fun SetWindowLongPtrW(hWnd: HWND, nIndex: Int, dwNewLong: Pointer): Pointer?
    fun CallWindowProcW(lpPrevWndFunc: Pointer, hWnd: HWND, msg: Int, wParam: WPARAM, lParam: LPARAM): LRESULT
    fun GetPointerType(pointerId: Int, pointerType: IntArray): Boolean
    fun GetPointerPenInfo(pointerId: Int, penInfo: POINTER_PEN_INFO): Boolean
    fun ScreenToClient(hWnd: HWND, point: POINT): Boolean

    companion object {
        val INSTANCE: User32Pointer by lazy {
            Native.load("user32", User32Pointer::class.java, W32APIOptions.DEFAULT_OPTIONS)
        }
    }
}

/** `POINTER_INFO` from `winuser.h`, field for field. */
@Structure.FieldOrder(
    "pointerType", "pointerId", "frameId", "pointerFlags", "sourceDevice", "hwndTarget",
    "ptPixelLocation", "ptHimetricLocation", "ptPixelLocationRaw", "ptHimetricLocationRaw",
    "dwTime", "historyCount", "inputData", "dwKeyStates", "performanceCount", "buttonChangeType",
)
open class POINTER_INFO : Structure() {
    @JvmField var pointerType: Int = 0
    @JvmField var pointerId: Int = 0
    @JvmField var frameId: Int = 0
    @JvmField var pointerFlags: Int = 0
    @JvmField var sourceDevice: Pointer? = null
    @JvmField var hwndTarget: Pointer? = null
    @JvmField var ptPixelLocation: POINT = POINT()
    @JvmField var ptHimetricLocation: POINT = POINT()
    @JvmField var ptPixelLocationRaw: POINT = POINT()
    @JvmField var ptHimetricLocationRaw: POINT = POINT()
    @JvmField var dwTime: Int = 0
    @JvmField var historyCount: Int = 0
    @JvmField var inputData: Int = 0
    @JvmField var dwKeyStates: Int = 0
    @JvmField var performanceCount: Long = 0
    @JvmField var buttonChangeType: Int = 0
}

/** `POINTER_PEN_INFO`: the pointer info plus what only a pen has. Pressure is 0..1024. */
@Structure.FieldOrder("pointerInfo", "penFlags", "penMask", "pressure", "rotation", "tiltX", "tiltY")
class POINTER_PEN_INFO : Structure() {
    @JvmField var pointerInfo: POINTER_INFO = POINTER_INFO()
    @JvmField var penFlags: Int = 0
    @JvmField var penMask: Int = 0
    @JvmField var pressure: Int = 0
    @JvmField var rotation: Int = 0
    @JvmField var tiltX: Int = 0
    @JvmField var tiltY: Int = 0
}
