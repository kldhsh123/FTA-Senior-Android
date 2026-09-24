package com.fta.senior.overlay

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityWindowInfo
import android.widget.LinearLayout
import android.widget.Toast
import com.fta.senior.FtaApplication
import com.fta.senior.MainActivity
import com.fta.senior.core.Action
import com.fta.senior.core.AppCatalog
import com.fta.senior.data.ButtonMode
import kotlin.math.abs
import kotlin.math.roundToInt

data class WindowSnapshot(val focused: String?, val visible: Set<String>, val reliable: Boolean)

class FloatingAccessibilityService : AccessibilityService() {
    companion object {
        var instance: FloatingAccessibilityService? = null
            private set
    }
    private val app get() = application as FtaApplication
    private val wm get() = getSystemService(WindowManager::class.java)
    private val handler = Handler(Looper.getMainLooper())
    private var overlay: LinearLayout? = null
    private var params: WindowManager.LayoutParams? = null
    private var dialog: AlertDialog? = null
    private var receiverRegistered = false
    private val settingsChanged: () -> Unit = { rebuild() }
    private val statusChanged: () -> Unit = { updateAppearance() }
    private val idle = Runnable {
        val settings = app.settings.read()
        overlay?.alpha = minOf(settings.idleOpacity, settings.opacity) / 100f
    }
    private val screenReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == Intent.ACTION_SCREEN_OFF) {
                dialog?.dismiss(); removeOverlay()
            } else rebuild()
        }
    }

    override fun onServiceConnected() {
        instance = this
        app.settings.addListener(settingsChanged)
        app.shizuku.addListener(statusChanged)
        app.actions.addListener(statusChanged)
        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_SCREEN_OFF); addAction(Intent.ACTION_USER_PRESENT)
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(screenReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        else @Suppress("UnspecifiedRegisterReceiverFlag") registerReceiver(screenReceiver, filter)
        receiverRegistered = true
        app.shizuku.refresh()
        rebuild()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) {
            dialog?.dismiss(); removeOverlay()
        } else if (overlay == null && app.settings.read().enabled) rebuild()
    }

    /** Window roots are used only for their package names; no text/tree traversal. */
    @Suppress("DEPRECATION")
    fun captureWindows(): WindowSnapshot {
        if (getSystemService(KeyguardManager::class.java).isKeyguardLocked) return WindowSnapshot(null, emptySet(), false)
        val visible = linkedSetOf<String>()
        var focused: String? = null
        var reliable = true
        var blocked = false
        return try {
            for (window in windows) {
                try {
                    if (window.type == AccessibilityWindowInfo.TYPE_ACCESSIBILITY_OVERLAY) continue
                    if (window.type == AccessibilityWindowInfo.TYPE_SYSTEM && (window.isFocused || window.isActive)) blocked = true
                    if (window.type != AccessibilityWindowInfo.TYPE_APPLICATION) continue
                    val root = window.root
                    val name = try { root?.packageName?.toString() } finally { root?.recycle() }
                    if (name.isNullOrBlank()) { reliable = false; continue }
                    visible.add(name)
                    if (window.isFocused) focused = name
                } finally { window.recycle() }
            }
            WindowSnapshot(if (blocked) null else focused, visible, reliable && !blocked && focused != null && visible.isNotEmpty())
        } catch (_: Exception) { WindowSnapshot(null, emptySet(), false) }
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        dialog?.dismiss()
        rebuild()
    }

    private fun rebuild() {
        removeOverlay()
        val settings = app.settings.read()
        if (!settings.enabled || !settings.accepted || getSystemService(KeyguardManager::class.java).isKeyguardLocked) return
        val container = LinearLayout(this).apply {
            orientation = if (settings.horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
            setPadding(dp(4), dp(4), dp(4), dp(4))
            layoutDirection = View.LAYOUT_DIRECTION_LTR
        }
        val actions = when (settings.mode) {
            ButtonMode.BOTH -> listOf(Action.STOP_FOREGROUND, Action.CLEAN_BACKGROUND)
            ButtonMode.FOREGROUND -> listOf(Action.STOP_FOREGROUND)
            ButtonMode.BACKGROUND -> listOf(Action.CLEAN_BACKGROUND)
        }.let { if (settings.reversed) it.reversed() else it }
        actions.forEach { action ->
            val button = ActionButtonView(this, action, settings)
            val layout = LinearLayout.LayoutParams(dp(ActionButtonView.widthDp(settings)), dp(settings.size))
            layout.setMargins(dp(3), dp(3), dp(3), dp(3))
            container.addView(button, layout)
            button.setOnClickListener { requestAction(action) }
            attachDrag(button)
        }
        val layout = WindowManager.LayoutParams(WindowManager.LayoutParams.WRAP_CONTENT, WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
            PixelFormat.TRANSLUCENT).apply {
            gravity = Gravity.TOP or Gravity.LEFT
            title = "FTA 悬浮按钮"
        }
        overlay = container; params = layout
        try {
            wm.addView(container, layout)
            container.post {
                if (overlay !== container) return@post
                val (x, y) = app.settings.position(isLandscape())
                val (maxX, maxY) = movementBounds()
                layout.x = (x * maxX).roundToInt(); layout.y = (y * maxY).roundToInt()
                updatePosition()
            }
            updateAppearance()
        } catch (e: Exception) {
            overlay = null; params = null
            Toast.makeText(this, "悬浮按钮显示失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun requestAction(action: Action) {
        wake()
        if (app.actions.busy) { Toast.makeText(this, "操作正在执行", Toast.LENGTH_SHORT).show(); return }
        if (app.shizuku.service == null) {
            startActivity(Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            Toast.makeText(this, "请先连接并授权 Shizuku", Toast.LENGTH_LONG).show()
            return
        }
        val snapshot = captureWindows()
        if (!snapshot.reliable) { Toast.makeText(this, "无法确认当前应用，请收起通知栏或最近任务后重试", Toast.LENGTH_LONG).show(); return }
        val settings = app.settings.read()
        if (settings.haptic) overlay?.performHapticFeedback(HapticFeedbackConstants.CONTEXT_CLICK)
        if (!settings.confirm) { app.actions.execute(action, snapshot.focused); return }
        if (dialog?.isShowing == true) return
        val target = snapshot.focused
        val message = if (action == Action.STOP_FOREGROUND) "强停 ${target?.let { AppCatalog(this).label(it) }}？未保存内容可能丢失。"
        else "强停后台非系统应用？可见应用、关键组件和白名单会被跳过。"
        dialog = AlertDialog.Builder(this, android.R.style.Theme_Material_Light_Dialog_Alert)
            .setTitle(action.title).setMessage(message).setNegativeButton("取消", null)
            .setPositiveButton("执行") { _, _ ->
                // Let the overlay dialog disappear before checking interactive windows again.
                handler.postDelayed({ app.actions.execute(action, target) }, 150)
            }.create().apply {
                window?.setType(WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY)
                show()
            }
    }

    @Suppress("ClickableViewAccessibility")
    private fun attachDrag(button: View) {
        var downX = 0f; var downY = 0f
        var initialX = 0; var initialY = 0
        var dragged = false
        val slop = ViewConfiguration.get(this).scaledTouchSlop
        button.setOnTouchListener { view, event ->
            val p = params ?: return@setOnTouchListener false
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    wake(); downX = event.rawX; downY = event.rawY
                    initialX = p.x; initialY = p.y; dragged = false; view.isPressed = true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = event.rawX - downX; val dy = event.rawY - downY
                    if (abs(dx) > slop || abs(dy) > slop) dragged = true
                    if (dragged) {
                        view.isPressed = false
                        if (!app.settings.read().locked) {
                            p.x = initialX + dx.roundToInt(); p.y = initialY + dy.roundToInt(); updatePosition()
                        }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    view.isPressed = false
                    if (!dragged) view.performClick() else if (!app.settings.read().locked) {
                        val (maxX, maxY) = movementBounds()
                        if (app.settings.read().snapToEdge) p.x = if (p.x < maxX / 2) 0 else maxX
                        updatePosition()
                        app.settings.savePosition(isLandscape(), p.x.toFloat() / maxX.coerceAtLeast(1), p.y.toFloat() / maxY.coerceAtLeast(1))
                    }
                    wake()
                }
                MotionEvent.ACTION_CANCEL -> { view.isPressed = false; wake() }
            }
            true
        }
    }

    @Suppress("DEPRECATION")
    private fun movementBounds(): Pair<Int, Int> {
        val size = android.graphics.Point()
        wm.defaultDisplay.getSize(size)
        val insets = overlay?.rootWindowInsets
        val top = insets?.systemWindowInsetTop ?: dp(24)
        val bottom = insets?.systemWindowInsetBottom ?: dp(24)
        return (size.x - (overlay?.width ?: 0)).coerceAtLeast(0) to
            (size.y - top - bottom - (overlay?.height ?: 0)).coerceAtLeast(0)
    }

    private fun updatePosition() {
        val view = overlay ?: return
        val p = params ?: return
        val (maxX, maxY) = movementBounds()
        p.x = p.x.coerceIn(0, maxX); p.y = p.y.coerceIn(0, maxY)
        runCatching { wm.updateViewLayout(view, p) }
    }

    private fun updateAppearance() {
        overlay?.let { row ->
            for (i in 0 until row.childCount) row.getChildAt(i).isEnabled = !app.actions.busy
            row.contentDescription = if (app.actions.busy) "FTA 正在执行" else app.shizuku.status.title
        }
        wake()
    }

    private fun wake() {
        handler.removeCallbacks(idle)
        overlay?.alpha = app.settings.read().opacity / 100f
        handler.postDelayed(idle, 4000)
    }
    private fun removeOverlay() {
        handler.removeCallbacks(idle)
        overlay?.let { runCatching { wm.removeViewImmediate(it) } }
        overlay = null; params = null
    }
    private fun isLandscape() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    override fun onInterrupt() { dialog?.dismiss(); removeOverlay() }
    override fun onDestroy() {
        if (instance === this) instance = null
        app.settings.removeListener(settingsChanged)
        app.shizuku.removeListener(statusChanged)
        app.actions.removeListener(statusChanged)
        if (receiverRegistered) unregisterReceiver(screenReceiver)
        dialog?.dismiss(); removeOverlay(); handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
