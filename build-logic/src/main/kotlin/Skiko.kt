/** The skiko (Skia) version Compose 1.7.3 ships: one Skia in the app, not two. */
const val SKIKO_VERSION = "0.8.18"

/** `windows-x64`, `linux-x64`, `macos-arm64` … — the skiko runtime artifact for this machine. */
fun skikoTarget(): String {
    val os = System.getProperty("os.name").lowercase()
    val arch = System.getProperty("os.arch").lowercase()
    val platform = when {
        os.startsWith("windows") -> "windows"
        os.startsWith("mac") -> "macos"
        else -> "linux"
    }
    val cpu = if (arch == "aarch64" || arch == "arm64") "arm64" else "x64"
    return "$platform-$cpu"
}
