package com.fta.senior

import android.content.ClipData
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.SwitchCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.fta.senior.core.Action
import com.fta.senior.core.AppCatalog
import com.fta.senior.core.InstalledApp
import com.fta.senior.data.ButtonMode
import com.fta.senior.data.ButtonShape
import com.fta.senior.overlay.ActionButtonView
import com.fta.senior.overlay.FloatingAccessibilityService
import com.google.android.material.button.MaterialButton
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.roundToInt

class MainActivity : AppCompatActivity() {
    private val app get() = application as FtaApplication
    private val main = Handler(Looper.getMainLooper())
    private val loader = Executors.newSingleThreadExecutor()
    private lateinit var content: LinearLayout
    private lateinit var navigation: LinearLayout
    private var selectedPage = 0
    private var pageGeneration = 0
    private var preview: LinearLayout? = null
    private var shizukuState: TextView? = null
    private var accessibilityState: TextView? = null
    private var resultState: TextView? = null
    private var overlaySwitch: SwitchCompat? = null
    private var refreshingSwitch = false
    private val stateListener: () -> Unit = { refreshStatus() }
    private val poll = object : Runnable {
        override fun run() { refreshStatus(); main.postDelayed(this, 1000) }
    }
    private val accent = Color.rgb(0, 47, 167)
    private val ink = Color.rgb(20, 22, 27)
    private val muted = Color.rgb(97, 102, 114)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        selectedPage = savedInstanceState?.getInt("page") ?: 0
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        ViewCompat.setOnApplyWindowInsetsListener(root) { view, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime())
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        val scroll = ScrollView(this).apply { isFillViewport = true }
        content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(24), dp(24), dp(24), dp(24))
        }
        scroll.addView(content)
        root.addView(scroll, LinearLayout.LayoutParams(-1, 0, 1f))
        root.addView(View(this).apply { setBackgroundColor(Color.rgb(224, 227, 233)) }, LinearLayout.LayoutParams(-1, dp(1)))
        navigation = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            setPadding(dp(8), dp(4), dp(8), dp(4))
        }
        listOf("首页", "按钮", "规则", "记录").forEachIndexed { index, label ->
            navigation.addView(MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = label; textSize = 13f; minWidth = 0
                setPadding(0, 0, 0, 0)
                setOnClickListener { selectedPage = index; showPage(); scroll.scrollTo(0, 0) }
            }, LinearLayout.LayoutParams(0, dp(56), 1f))
        }
        root.addView(navigation)
        setContentView(root)
        app.shizuku.addListener(stateListener)
        app.actions.addListener(stateListener)
        showPage()
    }

    override fun onResume() {
        super.onResume()
        app.shizuku.refresh()
        main.removeCallbacks(poll); main.post(poll)
    }
    override fun onPause() { main.removeCallbacks(poll); super.onPause() }
    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        app.shizuku.removeListener(stateListener)
        app.actions.removeListener(stateListener)
        loader.shutdownNow()
        super.onDestroy()
    }
    override fun onSaveInstanceState(outState: Bundle) {
        outState.putInt("page", selectedPage); super.onSaveInstanceState(outState)
    }

    private fun showPage() {
        pageGeneration++
        content.removeAllViews()
        preview = null; shizukuState = null; accessibilityState = null; resultState = null; overlaySwitch = null
        for (i in 0 until navigation.childCount) {
            (navigation.getChildAt(i) as MaterialButton).apply {
                setTextColor(if (i == selectedPage) accent else muted)
                setTypeface(null, if (i == selectedPage) Typeface.BOLD else Typeface.NORMAL)
                isSelected = i == selectedPage
            }
        }
        when (selectedPage) { 0 -> home(); 1 -> appearance(); 2 -> rules(); else -> history() }
    }

    private fun home() {
        text("FTA", 42, true)
        text("FuckingTheApp-Senior-Android", 14, color = muted)
        text("让应用停下来。", 24, true, top = 24)
        text("用悬浮按钮强停当前应用，或按你的规则清理后台。", color = muted)
        section("连接与权限")
        shizukuState = text("", 17, true)
        button("授权 / 重新连接 Shizuku") { app.shizuku.requestPermission()?.let { toast(it) } }
        button("打开 Shizuku", secondary = true) {
            val intent = packageManager.getLaunchIntentForPackage("moe.shizuku.privileged.api")
            if (intent == null) toast("尚未安装 Shizuku，请先安装并启动") else launch(intent)
        }
        accessibilityState = text("", 17, true, top = 16)
        button("开启无障碍服务", secondary = true) { accessibilityDisclosure() }
        overlaySwitch = toggle("显示悬浮按钮", app.settings.read().enabled,
            "开启后，可在其他应用上操作；锁屏时隐藏。") { enabled ->
            if (!refreshingSwitch) {
                if (enabled && !app.settings.read().accepted) {
                    app.settings.put("enabled", false)
                    refreshStatus()
                    consent { app.settings.put("enabled", true); refreshStatus() }
                } else app.settings.put("enabled", enabled)
            }
        }
        section("执行")
        button("清理后台应用") {
            consent {
                if (FloatingAccessibilityService.instance == null) toast("请先开启无障碍服务")
                else if (app.settings.read().confirm) AlertDialog.Builder(this)
                    .setTitle("清理后台应用？")
                    .setMessage("会跳过系统应用、当前可见应用和受保护应用。")
                    .setNegativeButton("取消", null)
                    .setPositiveButton("清理") { _, _ -> main.postDelayed({ app.actions.execute(Action.CLEAN_BACKGROUND) }, 200) }.show()
                else app.actions.execute(Action.CLEAN_BACKGROUND)
            }
        }
        text("强停当前应用：先切换到目标应用，再点悬浮按钮。FTA 本身始终受保护。", 13, color = muted)
        resultState = text("", 14, top = 16)
        section("使用说明")
        text("强停会中断应用，可能丢失未保存内容或停止消息提醒。清理不删除数据、不清缓存，也不保证阻止应用以后再次启动。", 14, color = muted)
        text("按钮依赖无障碍服务。Shizuku 断开时无法执行强停；非 root 设备重启后通常需要重新启动 Shizuku。首版仅支持当前主用户。", 14, color = muted)
        button("阅读权限与隐私说明", secondary = true) { accessibilityDisclosure() }
        refreshStatus()
    }

    private fun appearance() {
        text("你的悬浮按钮", 28, true)
        text("下面就是实际按钮。修改即时应用，并自动保存。", 14, color = muted)
        preview = LinearLayout(this).apply {
            gravity = Gravity.CENTER
            setPadding(dp(16), dp(24), dp(16), dp(24))
            background = GradientDrawable().apply {
                setColor(Color.rgb(247, 247, 248)); setStroke(dp(1), Color.rgb(224, 227, 233))
            }
        }.also { content.addView(it, spaced(-1, -2, 20)) }
        renderPreview()
        val s = app.settings.read()
        section("功能与排列")
        choice("显示功能", ButtonMode.entries.map { it.title }, s.mode.ordinal) {
            app.settings.put("mode", ButtonMode.entries[it].name); renderPreview()
        }
        toggle("横向排列", s.horizontal) { app.settings.put("horizontal", it); renderPreview() }
        toggle("交换按钮顺序", s.reversed) { app.settings.put("reversed", it); renderPreview() }
        section("外观")
        choice("按钮形状", ButtonShape.entries.map { it.title }, s.shape.ordinal) {
            app.settings.put("shape", ButtonShape.entries[it].name); renderPreview()
        }
        toggle("显示功能文字", s.showLabels, "开启后按钮加宽，显示“强停”或“清理”。") {
            app.settings.put("showLabels", it); renderPreview()
        }
        slider("按钮大小", s.size, 40, 80, "dp") { app.settings.put("size", it); renderPreview() }
        slider("正常透明度", s.opacity, 30, 100, "%") { app.settings.put("opacity", it); renderPreview() }
        slider("闲置透明度", s.idleOpacity, 15, 100, "%") { app.settings.put("idleOpacity", it) }
        text("闲置 4 秒后淡出；闲置透明度不会高于正常透明度。", 13, color = muted)
        button("设置背景颜色", true) { colorPicker("backgroundColor", app.settings.read().backgroundColor) }
        button("设置图标与文字颜色", true) { colorPicker("foregroundColor", app.settings.read().foregroundColor) }
        section("位置与反馈")
        toggle("拖动后自动吸边", s.snapToEdge) { app.settings.put("snapToEdge", it) }
        toggle("锁定按钮位置", s.locked) { app.settings.put("locked", it) }
        toggle("触感反馈", s.haptic) { app.settings.put("haptic", it) }
        button("重置横竖屏位置", true) {
            app.settings.savePosition(false, 1f, .35f); app.settings.savePosition(true, 1f, .35f)
            app.settings.put("positionRevision", System.currentTimeMillis().toString())
            toast("位置已重置到屏幕右侧")
        }
        text("横竖屏分别记忆位置。拖动按钮可以移动；滑动手势不会执行强停。", 13, color = muted)
    }

    private fun rules() {
        text("清理规则", 28, true)
        text("先保护需要持续运行的应用。", 14, color = muted)
        val s = app.settings.read()
        section("保护与确认")
        toggle("保护前台服务与可感知进程", s.protectImportant,
            "依据系统进程重要性保守跳过，通常包括播放、导航、上传等；不保证识别所有媒体，请将重要应用加入白名单。") {
            app.settings.put("protectImportant", it)
        }
        toggle("保护 VPN 应用", s.protectVpn, "保守保护所有声明 VPN 服务的应用，无论当前是否连接。") {
            app.settings.put("protectVpn", it)
        }
        toggle("执行前确认", s.confirm, "同时作用于强停当前应用和批量清理。默认关闭，保持一键操作。") {
            app.settings.put("confirm", it)
        }
        text("系统应用、FTA、Shizuku、桌面、输入法、已启用的无障碍服务和通知监听应用始终受保护。白名单同时适用于两个动作。", 13, color = muted)
        section("白名单")
        val search = EditText(this).apply {
            hint = "搜索应用名称或包名"; setSingleLine(); textSize = 15f
            inputType = android.text.InputType.TYPE_CLASS_TEXT
        }
        content.addView(search, spaced(-1, -2, 4))
        val count = text("正在读取已安装应用…", 13, color = muted)
        val bulk = LinearLayout(this)
        content.addView(bulk)
        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        content.addView(list)
        var apps: List<InstalledApp> = emptyList()
        fun filtered() = apps.filter {
            val query = search.text.toString().trim()
            query.isEmpty() || it.label.contains(query, true) || it.packageName.contains(query, true)
        }
        fun updateList() {
            list.removeAllViews()
            val shown = filtered()
            val whitelist = app.settings.read().whitelist
            count.text = "显示 ${shown.size} 个应用 · 已保护 ${apps.count { it.packageName in whitelist }} 个"
            shown.forEach { info ->
                list.addView(CheckBox(this).apply {
                    text = "${info.label}\n${info.packageName}"
                    textSize = 14f; setTextColor(ink)
                    minHeight = dp(64)
                    isChecked = info.packageName in whitelist
                    setOnCheckedChangeListener { _, checked ->
                        app.settings.setWhitelisted(info.packageName, checked)
                        count.text = "显示 ${shown.size} 个应用 · 已保护 ${apps.count { it.packageName in app.settings.read().whitelist }} 个"
                    }
                }, LinearLayout.LayoutParams(-1, -2))
            }
            if (shown.isEmpty()) list.addView(TextView(this).apply { text = "没有匹配的非系统应用"; setPadding(0, dp(20), 0, dp(20)) })
        }
        listOf("保护搜索结果" to true, "取消搜索结果保护" to false).forEach { (label, selected) ->
            bulk.addView(MaterialButton(this, null, com.google.android.material.R.attr.borderlessButtonStyle).apply {
                text = label; textSize = 12f; minWidth = 0
                setPadding(dp(4), 0, dp(4), 0)
                setOnClickListener { filtered().forEach { app.settings.setWhitelisted(it.packageName, selected) }; updateList() }
            }, LinearLayout.LayoutParams(0, -2, 1f))
        }
        search.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) = Unit
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) { updateList() }
            override fun afterTextChanged(s: Editable?) = Unit
        })
        val generation = pageGeneration
        loader.execute {
            val loaded = runCatching { AppCatalog(this).userApps() }
            runOnUiThread {
                if (isDestroyed || generation != pageGeneration) return@runOnUiThread
                loaded.onSuccess { apps = it; updateList() }.onFailure { count.text = "读取失败：${it.message}" }
            }
        }
    }

    private fun history() {
        text("操作记录", 28, true)
        text("只保存在本机，最多保留 50 条。", 14, color = muted)
        button("清空记录", true) { app.history.clear(); showPage() }
        button("复制诊断信息", true) {
            val info = "FTA ${BuildConfig.VERSION_NAME}\nAndroid ${android.os.Build.VERSION.RELEASE} / API ${android.os.Build.VERSION.SDK_INT}\n" +
                "${app.shizuku.status.title}\n无障碍连接：${FloatingAccessibilityService.instance != null}\n${app.shizuku.error}"
            getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText("FTA 诊断", info))
            toast("已复制诊断信息")
        }
        val records = app.history.read()
        if (records.isEmpty()) {
            section("还没有操作记录")
            text("执行操作后，这里会显示结果和跳过原因。", color = muted)
        }
        val format = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())
        records.forEach { record ->
            section(record.title)
            text(format.format(Date(record.time)), 12, color = muted)
            val body = text(record.detail, 13)
            body.maxLines = 4
            button("展开 / 收起详情", true) { body.maxLines = if (body.maxLines == 4) Int.MAX_VALUE else 4 }
        }
        section(getString(R.string.copyright_heading))
        text("FuckingTheApp-Senior-Android\n版本 ${BuildConfig.VERSION_NAME}", 13, color = muted)
        text(getString(R.string.copyright_notice), 14)
        text(getString(R.string.open_source_notice), 13, color = muted)
        button(getString(R.string.view_source), true) {
            launch(Intent(Intent.ACTION_VIEW, Uri.parse(getString(R.string.project_url))))
        }
        button(getString(R.string.view_license), true) { showLicense() }
    }

    private fun showLicense() {
        val license = runCatching { assets.open("LICENSE").bufferedReader().use { it.readText() } }
            .getOrElse { toast("无法读取许可证：${it.message}"); return }
        val body = TextView(this).apply {
            text = license
            textSize = 13f
            setPadding(dp(20), dp(12), dp(20), dp(12))
            setTextIsSelectable(true)
        }
        val scroll = ScrollView(this).apply { addView(body) }
        AlertDialog.Builder(this).setTitle("GNU GPL v3")
            .setView(scroll).setPositiveButton("关闭", null).show()
    }

    private fun refreshStatus() {
        if (isDestroyed) return
        shizukuState?.text = app.shizuku.status.title + app.shizuku.error.let { if (it.isEmpty()) "" else "\n$it" }
        val enabled = Settings.Secure.getString(contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
            ?.split(':')?.any { ComponentName.unflattenFromString(it) == ComponentName(this, FloatingAccessibilityService::class.java) } == true
        accessibilityState?.text = when {
            FloatingAccessibilityService.instance != null -> "无障碍服务已连接"
            enabled -> "无障碍已启用，等待系统连接"
            else -> "无障碍服务未开启"
        }
        resultState?.text = if (app.actions.busy) "正在执行，请稍候…" else app.actions.lastResult
        refreshingSwitch = true
        overlaySwitch?.isChecked = app.settings.read().enabled
        refreshingSwitch = false
    }

    private fun consent(onAccepted: () -> Unit) {
        if (app.settings.read().accepted) { onAccepted(); return }
        AlertDialog.Builder(this).setTitle("开始使用 FTA")
            .setMessage("FTA 使用无障碍服务识别窗口所属应用并显示悬浮按钮，使用你授权的 Shizuku 强制停止应用。\n\n强停可能丢失未保存内容、停止后台任务与消息提醒。请把需要保留的应用加入白名单。\n\n不读取页面文字或输入内容，不截图、不上传数据。操作记录仅保存在本机。")
            .setNegativeButton("取消", null)
            .setPositiveButton("了解并继续") { _, _ -> app.settings.put("accepted", true); onAccepted() }.show()
    }

    private fun accessibilityDisclosure() {
        AlertDialog.Builder(this).setTitle("无障碍权限的用途")
            .setMessage(getString(R.string.accessibility_description) + "\n\n系统权限说明中可能显示“读取屏幕内容”，本应用只使用窗口根节点的包名，不遍历页面文字。服务可随时关闭。\n\n若系统提示“受限设置”，请在系统应用信息页确认安装来源后允许受限设置，再返回开启服务。")
            .setNegativeButton("关闭", null)
            .setPositiveButton("前往系统设置") { _, _ -> launch(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }.show()
    }

    private fun renderPreview() {
        val box = preview ?: return
        val s = app.settings.read()
        box.removeAllViews()
        box.orientation = if (s.horizontal) LinearLayout.HORIZONTAL else LinearLayout.VERTICAL
        val actions = when (s.mode) {
            ButtonMode.BOTH -> listOf(Action.STOP_FOREGROUND, Action.CLEAN_BACKGROUND)
            ButtonMode.FOREGROUND -> listOf(Action.STOP_FOREGROUND)
            ButtonMode.BACKGROUND -> listOf(Action.CLEAN_BACKGROUND)
        }.let { if (s.reversed) it.reversed() else it }
        actions.forEach { action ->
            box.addView(ActionButtonView(this, action, s).apply {
                alpha = s.opacity / 100f
                setOnClickListener { toast("预览：${action.title}") }
            }, LinearLayout.LayoutParams(dp(ActionButtonView.widthDp(s)), dp(s.size)).apply { setMargins(dp(4), dp(4), dp(4), dp(4)) })
        }
    }

    private fun colorPicker(key: String, current: Int) {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(dp(24), dp(8), dp(24), 0) }
        val input = EditText(this).apply {
            hint = "#RRGGBB"; setSingleLine()
            setText(String.format(Locale.ROOT, "#%06X", current and 0xFFFFFF))
        }
        box.addView(input)
        val swatches = LinearLayout(this)
        box.addView(swatches)
        listOf("#002FA7", "#20242B", "#E4002B", "#FFFFFF", "#FFB800").forEach { hex ->
            swatches.addView(View(this).apply {
                background = GradientDrawable().apply { setColor(Color.parseColor(hex)); setStroke(dp(1), Color.GRAY) }
                contentDescription = hex
                setOnClickListener { input.setText(hex) }
            }, LinearLayout.LayoutParams(0, dp(40), 1f).apply { setMargins(dp(3), dp(12), dp(3), dp(12)) })
        }
        val dialog = AlertDialog.Builder(this).setTitle("自定义颜色").setView(box)
            .setNegativeButton("取消", null).setPositiveButton("保存", null).create()
        dialog.setOnShowListener {
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener {
                val value = input.text.toString().trim()
                if (!Regex("#[0-9a-fA-F]{6}").matches(value)) input.error = "请输入 # 加六位十六进制颜色"
                else { app.settings.put(key, Color.parseColor(value)); renderPreview(); dialog.dismiss() }
            }
        }
        dialog.show()
    }

    private fun section(title: String) {
        content.addView(View(this).apply { setBackgroundColor(Color.rgb(224, 227, 233)) }, spaced(-1, dp(1), 24))
        text(title, 18, true, top = 18)
    }
    private fun text(value: String, size: Int = 15, bold: Boolean = false, color: Int = ink, top: Int = 8): TextView =
        TextView(this).apply {
            text = value; textSize = size.toFloat(); setTextColor(color)
            typeface = Typeface.create("sans-serif", if (bold) Typeface.BOLD else Typeface.NORMAL)
            setLineSpacing(dp(3).toFloat(), 1f)
            content.addView(this, spaced(-1, -2, top))
        }

    private fun button(label: String, secondary: Boolean = false, action: () -> Unit): MaterialButton =
        MaterialButton(this, null, if (secondary) com.google.android.material.R.attr.materialButtonOutlinedStyle else com.google.android.material.R.attr.materialButtonStyle).apply {
            text = label; isAllCaps = false; textSize = 14f; cornerRadius = dp(12)
            minHeight = dp(52)
            setOnClickListener { action() }
            content.addView(this, spaced(-1, -2, 8))
        }

    private fun toggle(label: String, value: Boolean, description: String? = null, changed: (Boolean) -> Unit): SwitchCompat {
        val control = SwitchCompat(this).apply {
            text = label; textSize = 15f; setTextColor(ink)
            minHeight = dp(52); isChecked = value
            setOnCheckedChangeListener { _, checked -> changed(checked) }
        }
        content.addView(control, spaced(-1, -2, 8))
        if (description != null) text(description, 13, color = muted, top = 0)
        return control
    }

    private fun choice(label: String, choices: List<String>, selected: Int, changed: (Int) -> Unit) {
        text(label, 14, true, top = 16)
        val spinner = Spinner(this)
        spinner.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, choices)
        spinner.setSelection(selected)
        var previous = selected
        spinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position != previous) { previous = position; changed(position) }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }
        content.addView(spinner, spaced(-1, dp(48), 4))
    }

    private fun slider(label: String, value: Int, minimum: Int, maximum: Int, unit: String, changed: (Int) -> Unit) {
        val title = text("$label · $value$unit", 14, true, top = 16)
        content.addView(SeekBar(this).apply {
            max = maximum - minimum; progress = value - minimum
            contentDescription = label
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    if (fromUser) { title.text = "$label · ${progress + minimum}$unit"; changed(progress + minimum) }
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
        }, spaced(-1, dp(48), 0))
    }
    private fun launch(intent: Intent) { runCatching { startActivity(intent) }.onFailure { toast("无法打开：${it.message}") } }
    private fun toast(value: String) { Toast.makeText(this, value, Toast.LENGTH_LONG).show() }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).roundToInt()
    private fun spaced(width: Int, height: Int, top: Int) = LinearLayout.LayoutParams(width, height).apply { topMargin = dp(top) }
}
