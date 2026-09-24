package com.fta.senior.privileged

import android.app.ActivityManager
import android.content.pm.ApplicationInfo
import com.fta.senior.core.CleanupPolicy
import org.json.JSONArray
import org.json.JSONObject
import java.lang.reflect.InvocationTargetException

/** Policy runs in the app; every system operation uses a Shizuku-wrapped proxy. */
class PrivilegedService(private val access: SystemBinderAccess) : ProcessService {
    private val activityManager get() = access.activityManager
    private val activityInterface get() = access.activityInterface
    private val packageManager get() = access.packageManager
    private val packageInterface get() = access.packageInterface

    override fun snapshot(userId: Int): String = result {
        access.checkUser(userId)
        val apps = linkedMapOf<String, Pair<Int, Int>>()
        for (process in runningProcesses()) {
            if (process.uid / 100000 != userId) continue
            for (name in process.pkgList.orEmpty()) {
                val old = apps[name]
                apps[name] = process.uid to minOf(old?.second ?: Int.MAX_VALUE, process.importance)
            }
        }
        val result = JSONArray()
        apps.forEach { (name, state) ->
            val info = applicationInfo(name, userId) ?: return@forEach
            result.put(JSONObject().put("packageName", name).put("uid", info.uid)
                .put("system", isSystem(info)).put("importance", state.second))
        }
        JSONObject().put("ok", true).put("apps", result).toString()
    }

    override fun forceStop(packageName: String, userId: Int, foreground: Boolean, protectImportant: Boolean): String = result {
        access.checkUser(userId)
        require(CleanupPolicy.validPackage(packageName)) { "无效包名" }
        val info = applicationInfo(packageName, userId) ?: error("应用已卸载或不可见")
        val protected = CleanupPolicy.permanentProtection(packageName, info.uid, isSystem(info), userId)
        if (protected != null) return@result skipped(protected)

        // An app sharing a UID with a protected package must not be stopped indirectly.
        val siblings = packageInterface.getMethod("getPackagesForUid", Int::class.javaPrimitiveType)
            .invoke(packageManager, info.uid) as? Array<*>
            ?: error("无法确认共享 UID，已取消")
        if (siblings.any { sibling ->
                val name = sibling as String
                val siblingInfo = applicationInfo(name, userId)
                siblingInfo == null || CleanupPolicy.permanentProtection(name, siblingInfo.uid, isSystem(siblingInfo), userId) != null
            }) return@result skipped("共享 UID 中含有受保护应用")

        val processes = runningProcesses().filter { it.uid == info.uid }
        if (processes.isEmpty()) return@result skipped("应用已不在运行")
        val importance = processes.minOf { it.importance }
        if (!foreground) {
            if (importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND ||
                importance == ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE ||
                importance < ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND) {
                return@result skipped("应用已切换到前台或可见状态")
            }
            if (protectImportant && importance <= 230) return@result skipped("用户可感知进程")
        } else if (importance > ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE) {
            return@result skipped("目标已离开前台")
        }
        activityInterface.getMethod("forceStopPackage", String::class.java, Int::class.javaPrimitiveType)
            .invoke(activityManager, packageName, userId)
        JSONObject().put("ok", true).put("status", "stopped").put("message", "已发送强停请求").toString()
    }

    @Suppress("UNCHECKED_CAST")
    private fun runningProcesses(): List<ActivityManager.RunningAppProcessInfo> =
        activityInterface.getMethod("getRunningAppProcesses").invoke(activityManager)
            as? List<ActivityManager.RunningAppProcessInfo> ?: error("系统未返回运行进程列表")

    private fun applicationInfo(name: String, userId: Int): ApplicationInfo? {
        val method = packageInterface.methods.firstOrNull {
            it.name == "getApplicationInfo" && it.parameterTypes.size == 3 && it.parameterTypes[0] == String::class.java
        } ?: error("系统不支持查询应用信息")
        val flags: Any = if (method.parameterTypes[1] == Long::class.javaPrimitiveType) 0L else 0
        return method.invoke(packageManager, name, flags, userId) as? ApplicationInfo
    }

    private fun isSystem(info: ApplicationInfo) =
        info.flags and (ApplicationInfo.FLAG_SYSTEM or ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) != 0

    private fun skipped(message: String) = JSONObject().put("ok", true)
        .put("status", "skipped").put("message", message).toString()

    private inline fun result(block: () -> String): String {
        return try { block() } catch (error: Exception) {
            val cause = if (error is InvocationTargetException) error.targetException else error
            JSONObject().put("ok", false).put("message", cause.message ?: cause.javaClass.simpleName).toString()
        }
    }

}
