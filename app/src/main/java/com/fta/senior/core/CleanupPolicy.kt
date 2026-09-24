package com.fta.senior.core

/** Pure policy: shared by the privileged service and the UI-side checks. */
object CleanupPolicy {
    const val OWN_PACKAGE = "com.fta.senior"
    const val SHIZUKU_PACKAGE = "moe.shizuku.privileged.api"
    private val packagePattern = Regex("[a-zA-Z][a-zA-Z0-9_]*(\\.[a-zA-Z][a-zA-Z0-9_]*)+")

    fun validPackage(name: String): Boolean = name.length <= 255 && packagePattern.matches(name)

    fun permanentProtection(name: String, uid: Int, system: Boolean, userId: Int): String? = when {
        !validPackage(name) -> "无效包名"
        userId < 0 || uid < 0 || uid / 100000 != userId -> "其他用户的应用"
        name == OWN_PACKAGE -> "本工具"
        name == SHIZUKU_PACKAGE -> "Shizuku"
        name == "com.android.systemui" || name == "android" -> "系统界面"
        system || uid % 100000 < 10000 -> "系统应用"
        else -> null
    }

    fun skipReason(
        name: String,
        uid: Int,
        system: Boolean,
        userId: Int,
        protected: Set<String>,
        visible: Set<String>,
        whitelist: Set<String>,
        importance: Int,
        protectImportant: Boolean,
    ): String? = permanentProtection(name, uid, system, userId)
        ?: when {
            name in protected -> "关键组件或 VPN"
            name in visible -> "当前可见应用"
            name in whitelist -> "白名单"
            protectImportant && importance <= 230 -> "前台服务、媒体或用户可感知进程"
            else -> null
        }
}
