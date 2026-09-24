package com.fta.senior.privileged

/** App-side interface; privileged calls are forwarded by ShizukuBinderWrapper. */
interface ProcessService {
    fun snapshot(userId: Int): String
    fun forceStop(packageName: String, userId: Int, foreground: Boolean, protectImportant: Boolean): String
}
