package com.fta.senior.core

import android.os.Handler
import android.os.Looper
import android.widget.Toast
import com.fta.senior.FtaApplication
import com.fta.senior.overlay.FloatingAccessibilityService
import com.fta.senior.overlay.WindowSnapshot
import org.json.JSONObject
import java.util.concurrent.Executors
import java.util.concurrent.FutureTask
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean

enum class Action(val title: String) { STOP_FOREGROUND("强停当前应用"), CLEAN_BACKGROUND("清理后台") }

class ActionController(private val app: FtaApplication) {
    private val executor = Executors.newSingleThreadExecutor()
    private val main = Handler(Looper.getMainLooper())
    private val running = AtomicBoolean(false)
    private val listeners = linkedSetOf<() -> Unit>()
    private val catalog = AppCatalog(app)
    val busy: Boolean get() = running.get()
    var lastResult = "尚未执行操作"
        private set

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    /** Called on the main thread. target is captured before a confirmation dialog opens. */
    fun execute(action: Action, target: String? = null) {
        if (!app.settings.read().accepted) { toast("请先在首页阅读并确认使用说明"); return }
        if (app.shizuku.service == null) { app.shizuku.refresh(); toast("Shizuku 尚未就绪，请到首页检查授权"); return }
        if (!running.compareAndSet(false, true)) { toast("上一次操作仍在执行"); return }
        listeners.toList().forEach { it() }
        executor.execute {
            val result = runCatching {
                when (action) {
                    Action.STOP_FOREGROUND -> stopForeground(target)
                    Action.CLEAN_BACKGROUND -> cleanBackground()
                }
            }.getOrElse { "操作未完成" to (it.message ?: it.javaClass.simpleName) }
            app.history.append(result.first, result.second)
            main.post {
                running.set(false)
                lastResult = result.first + "\n" + result.second
                toast(result.first)
                listeners.toList().forEach { it() }
            }
        }
    }

    private fun windows(): WindowSnapshot {
        val task = FutureTask {
            FloatingAccessibilityService.instance?.captureWindows()
                ?: error("无障碍服务未连接")
        }
        main.post(task)
        return try { task.get(2, TimeUnit.SECONDS) } finally { task.cancel(false) }
    }

    private fun stopForeground(expected: String?): Pair<String, String> {
        require(!expected.isNullOrBlank()) { "无法可靠识别当前应用，已取消" }
        val settings = app.settings.read()
        val window = windows()
        check(window.reliable && window.focused == expected) { "前台应用已变化，已取消" }
        check(expected !in catalog.protectedPackages(settings.protectVpn)) { "目标是受保护的关键组件" }
        check(expected !in catalog.expandSharedUids(settings.whitelist)) { "目标在白名单中" }
        val service = app.shizuku.service ?: error("Shizuku 已断开")
        val result = JSONObject(service.forceStop(expected, catalog.userId, true, false))
        check(result.getBoolean("ok")) { result.optString("message", "执行失败") }
        val name = catalog.label(expected)
        return if (result.getString("status") == "stopped") "已强停 $name" to expected
        else "已跳过 $name" to result.getString("message")
    }

    private fun cleanBackground(): Pair<String, String> {
        val initialWindows = windows()
        check(initialWindows.reliable) { "无法确认可见应用，请关闭通知栏或最近任务界面后重试" }
        val service = app.shizuku.service ?: error("Shizuku 已断开")
        val snapshot = JSONObject(service.snapshot(catalog.userId))
        check(snapshot.getBoolean("ok")) { snapshot.optString("message", "无法读取运行列表") }
        val apps = snapshot.getJSONArray("apps")
        var stopped = 0
        var skipped = 0
        var failed = 0
        val details = mutableListOf<String>()
        for (index in 0 until apps.length()) {
            val item = apps.getJSONObject(index)
            val name = item.getString("packageName")
            val settings = app.settings.read()
            val visible = windows()
            if (!visible.reliable) {
                val remaining = apps.length() - index
                skipped += remaining
                details.add("窗口状态变化，停止清理；剩余 $remaining 个应用未处理")
                break
            }
            // Re-read rules and windows on every item so changes apply during a batch.
            val reason = CleanupPolicy.skipReason(name, item.getInt("uid"), item.getBoolean("system"),
                catalog.userId, catalog.protectedPackages(settings.protectVpn),
                catalog.expandSharedUids(visible.visible), catalog.expandSharedUids(settings.whitelist),
                item.getInt("importance"), settings.protectImportant)
            if (reason != null) {
                skipped++; details.add("跳过 $name：$reason"); continue
            }
            try {
                val response = JSONObject(service.forceStop(name, catalog.userId, false, settings.protectImportant))
                if (!response.getBoolean("ok")) {
                    failed++; details.add("失败 $name：${response.optString("message")}")
                } else if (response.getString("status") == "stopped") {
                    stopped++; details.add("已强停 $name")
                } else {
                    skipped++; details.add("跳过 $name：${response.optString("message")}")
                }
            } catch (e: Exception) {
                failed++; details.add("失败 $name：${e.message}")
                val remaining = apps.length() - index - 1
                skipped += remaining
                details.add("执行连接异常，剩余 $remaining 个应用未处理")
                break
            }
        }
        return "清理完成：成功 $stopped · 跳过 $skipped · 失败 $failed" to
            details.joinToString("\n").ifBlank { "没有运行中的候选应用" }
    }

    private fun toast(message: String) { Toast.makeText(app, message, Toast.LENGTH_LONG).show() }
}
