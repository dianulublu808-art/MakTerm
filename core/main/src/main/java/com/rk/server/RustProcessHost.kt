package com.rk.server

/**
 * JNI bridge to the Rust process supervisor (`core/rust`).
 *
 * Kotlin still builds the Android/proot command line and environment, while
 * Rust owns the child process, merges stdout/stderr into a bounded log and
 * performs keep-alive restarts. The UI polls [nativeSnapshot] and [nativeLog]
 * instead of receiving callbacks, so no Rust thread ever touches the JVM.
 */
object RustProcessHost {

    val available: Boolean = runCatching {
        System.loadLibrary("makterm_core")
        true
    }.getOrDefault(false)

    /** Returns an empty string on success, or a human-readable error. */
    external fun nativeStart(configJson: String): String

    external fun nativeStop(id: String)

    external fun nativeStopAll()

    external fun nativeClearLog(id: String)

    external fun nativeLog(id: String): String

    /** JSON array of `{id, running, restarting, startedAt, exitCode, restarts}`. */
    external fun nativeSnapshot(): String

    external fun nativeVersion(): String
}
