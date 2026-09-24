package com.fta.senior.privileged

import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.os.Process
import org.json.JSONObject
import rikka.shizuku.Shizuku
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.Executors

class ShizukuConnection {
    enum class Status(val title: String) {
        STOPPED("Shizuku 未启动"), UNAUTHORIZED("等待 Shizuku 授权"),
        CONNECTING("正在验证系统接口"), READY("Shizuku 已就绪"), ERROR("系统接口连接失败")
    }
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor()
    private val attempts = ConnectionAttempt()
    @Volatile var service: ProcessService? = null
        private set
    var status = Status.STOPPED
        private set
    var error = ""
        private set
    private val listeners = linkedSetOf<() -> Unit>()
    private val timeout = Runnable {
        if (attempts.pending) {
            attempts.invalidate()
            service = null
            update(Status.ERROR, "系统 Binder 查询超时，请重试并采集连接日志")
        }
    }
    private val received = Shizuku.OnBinderReceivedListener {
        main.post { invalidate(); refresh() }
    }
    private val dead = Shizuku.OnBinderDeadListener {
        main.post { invalidate(); update(Status.STOPPED) }
    }
    private val permissionResult = Shizuku.OnRequestPermissionResultListener { _, _ ->
        main.post { invalidate(); refresh() }
    }

    init {
        Shizuku.addBinderReceivedListenerSticky(received)
        Shizuku.addBinderDeadListener(dead)
        Shizuku.addRequestPermissionResultListener(permissionResult)
    }

    fun addListener(listener: () -> Unit) { listeners.add(listener) }
    fun removeListener(listener: () -> Unit) { listeners.remove(listener) }

    fun refresh() {
        try {
            if (!Shizuku.pingBinder()) { invalidate(); update(Status.STOPPED); return }
            if (Shizuku.getVersion() < 12) {
                invalidate(); update(Status.ERROR, "请升级至 Shizuku 12 或更新版本"); return
            }
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                invalidate(); update(Status.UNAUTHORIZED); return
            }
            if (service != null) { update(Status.READY); return }
            if (attempts.pending) return
            val attempt = attempts.start()
            update(Status.CONNECTING)
            main.postDelayed(timeout, 12000)
            worker.execute {
                val result = runCatching {
                    val candidate = PrivilegedService(SystemBinderAccess.connect())
                    // Read-only round trip checks both permission and platform compatibility.
                    // READY is never based solely on Shizuku's authorization result.
                    val probe = JSONObject(candidate.snapshot(Process.myUid() / 100000))
                    check(probe.getBoolean("ok")) { probe.optString("message", "运行列表查询失败") }
                    candidate
                }
                main.post {
                    if (!attempts.finish(attempt)) return@post
                    main.removeCallbacks(timeout)
                    result.onSuccess { candidate ->
                        if (!runCatching { Shizuku.pingBinder() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED }.getOrDefault(false)) {
                            invalidate(); refresh()
                        } else {
                            service = candidate
                            update(Status.READY)
                        }
                    }.onFailure { failure ->
                        service = null
                        val cause = if (failure is InvocationTargetException) failure.targetException else failure
                        update(Status.ERROR, "${cause.javaClass.simpleName}: ${cause.message ?: "连接失败"}")
                    }
                }
            }
        } catch (e: Exception) {
            invalidate(); update(Status.ERROR, "${e.javaClass.simpleName}: ${e.message ?: "连接失败"}")
        }
    }

    fun requestPermission(): String? = try {
        when {
            !Shizuku.pingBinder() -> "请先打开并启动 Shizuku"
            Shizuku.getVersion() < 12 -> "请升级 Shizuku"
            Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED -> { refresh(); null }
            Shizuku.shouldShowRequestPermissionRationale() -> "请在 Shizuku 的应用管理中允许 FTA"
            else -> { Shizuku.requestPermission(100); null }
        }
    } catch (e: Exception) { e.message ?: "授权失败" }

    private fun invalidate() {
        main.removeCallbacks(timeout)
        attempts.invalidate()
        service = null
    }

    private fun update(newStatus: Status, message: String = "") {
        status = newStatus; error = message
        listeners.toList().forEach { it() }
    }
}
