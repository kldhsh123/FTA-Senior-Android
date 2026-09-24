package com.fta.senior.privileged

import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import rikka.shizuku.Shizuku
import rikka.shizuku.ShizukuBinderWrapper
import rikka.shizuku.SystemServiceHelper

/** Uses platform-generated proxies, never hard-coded Binder transaction numbers. */
class SystemBinderAccess private constructor(
    val activityManager: Any,
    val activityInterface: Class<*>,
    val packageManager: Any,
    val packageInterface: Class<*>,
) {
    fun checkUser(userId: Int) {
        check(Shizuku.pingBinder()) { "Shizuku 已断开" }
        check(Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) { "Shizuku 授权已撤销" }
        check(Shizuku.getUid() == 0 || Shizuku.getUid() == 2000) { "Shizuku 没有 shell/root 身份" }
        val current = activityInterface.getMethod("getCurrentUser").invoke(activityManager)
            ?: error("系统没有返回当前用户")
        val currentId = current.javaClass.getField("id").getInt(current)
        require(userId >= 0 && userId == currentId) { "首版仅支持当前主用户，不支持工作资料或应用分身" }
    }

    companion object {
        fun connect(): SystemBinderAccess {
            allowPlatformInterfaces()
            val am = Class.forName("android.app.IActivityManager")
            val pm = Class.forName("android.content.pm.IPackageManager")
            return SystemBinderAccess(proxy("activity", am), am, proxy("package", pm), pm)
        }

        private fun proxy(service: String, type: Class<*>): Any {
            val binder = SystemServiceHelper.getSystemService(service)
                ?: error("系统服务不可用：$service")
            return Class.forName(type.name + "\$Stub")
                .getMethod("asInterface", IBinder::class.java)
                .invoke(null, ShizukuBinderWrapper(binder))
                ?: error("无法创建系统服务接口：$service")
        }

        private fun allowPlatformInterfaces() {
            if (Build.VERSION.SDK_INT < 28) return
            // Lazy loading keeps this Android 9+ library off Android 8's class-loading path.
            // This enables reflection only; permissions still come from Shizuku's binder.
            val allowed = Class.forName("org.lsposed.hiddenapibypass.HiddenApiBypass")
                .getMethod("addHiddenApiExemptions", Array<String>::class.java)
                .invoke(null, arrayOf(
                    "Landroid/app/IActivityManager", "Landroid/content/pm/IPackageManager",
                    "Landroid/content/pm/UserInfo;", "Landroid/os/ServiceManager;",
                )) as Boolean
            check(allowed) { "系统接口访问初始化失败" }
        }
    }
}
