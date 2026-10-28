package com.yiqian

import android.app.Activity
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.PixelFormat
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import rikka.shizuku.Shizuku
import java.io.File
import kotlin.math.hypot

// ============ Shizuku 权限管理 ============
object SZ {
    lateinit var ctx: android.content.Context
    fun init(c: android.content.Context) { ctx = c.applicationContext }
    fun ok(): Boolean = Shizuku.pingBinder()
    fun perm(): Boolean = try {
        Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    } catch (e: Exception) { false }
    fun req() { if (ok() && !perm()) Shizuku.requestPermission(0) }
    fun sh(cmd: String): String {
        if (!ok()) return ""
        return try {
            val p = Shizuku.newProcess(arrayOf("sh", "-c", cmd), null, null)
            val out = p.inputStream.bufferedReader().readText()
            p.waitFor()
            out
        } catch (e: Exception) { "" }
    }
}

// ============ 自瞄引擎（方案一 / 方案二） ============
object Aim {
    var mode = 1
    @Volatile var run = false
    private var w = 1080
    private var h = 2400

    fun start() {
        if (run) return
        run = true
        Thread { loop() }.start()
    }
    fun stop() { run = false }

    private fun loop() {
        while (run) {
            try {
                val b = cap()
                if (b == null) { Thread.sleep(80); continue }
                w = b.width; h = b.height
                val ts = det(b)
                if (mode == 1) rng(ts) else lock(ts)
                Thread.sleep(40)
            } catch (e: Exception) { Thread.sleep(120) }
        }
    }

    private fun cap(): Bitmap? {
        val p = "/data/local/tmp/yiqian.png"
        SZ.sh("screencap -p $p")
        val f = File(p)
        return if (f.exists() && f.length() > 0) BitmapFactory.decodeFile(p) else null
    }

    private fun det(b: Bitmap): List<IntArray> {
        val pts = mutableListOf<IntArray>()
        for (y in 0 until b.height step 4) {
            for (x in 0 until b.width step 4) {
                val c = b.getPixel(x, y)
                val r = (c shr 16) and 0xFF
                val g = (c shr 8) and 0xFF
                val bl = c and 0xFF
                if (r > 170 && r - g > 70 && r - bl > 70) pts.add(intArrayOf(x, y))
            }
        }
        return clu(pts)
    }

    private fun clu(pts: List<IntArray>): List<IntArray> {
        val cs = mutableListOf<MutableList<IntArray>>()
        for (p in pts) {
            var added = false
            for (c in cs) {
                val l = c.last()
                if (Math.abs(l[0] - p[0]) < 60 && Math.abs(l[1] - p[1]) < 120) {
                    c.add(p); added = true; break
                }
            }
            if (!added) cs.add(mutableListOf(p))
        }
        return cs.filter { it.size > 15 }.map { c ->
            intArrayOf(c.sumOf { it[0] } / c.size, c.sumOf { it[1] } / c.size)
        }
    }

    private fun rng(ts: List<IntArray>) {
        val cx = w / 2; val cy = h / 2
        for (t in ts) {
            if (hypot((t[0] - cx).toDouble(), (t[1] - cy).toDouble()) < 30f) { mv(t); return }
        }
    }

    private fun lock(ts: List<IntArray>) {
        if (ts.isEmpty()) return
        val cx = w / 2; val cy = h / 2
        val n = ts.minByOrNull { hypot((it[0] - cx).toDouble(), (it[1] - cy).toDouble()) } ?: return
        mv(n)
    }

    private fun mv(t: IntArray) {
        val cx = w / 2; val cy = h / 2
        val dx = t[0] - cx; val dy = t[1] - cy
        val d = (hypot(dx.toDouble(), dy.toDouble()) * 0.3).toInt().coerceIn(10, 80)
        SZ.sh("input swipe $cx $cy ${cx + dx} ${cy + dy} $d")
    }
}

// ============ 游戏进程监听（自动注入） ============
object Watcher {
    private val handler = Handler(Looper.getMainLooper())
    private var on = false
    private val tick = object : Runnable {
        override fun run() {
            if (on) {
                if (SZ.ok() && SZ.sh("pidof com.tencent.tmgp.pubgmhd").isNotBlank()) {
                    Aim.start()
                    Ovl.text("亿千 注入成功")
                }
                handler.postDelayed(this, 2000)
            }
        }
    }
    fun start() { if (!on) { on = true; handler.post(tick) } }
    fun stop() { on = false; handler.removeCallbacks(tick); Aim.stop() }
}

// ============ 悬浮窗 ============
object Ovl {
    var tv: TextView? = null
    fun text(s: String) { tv?.post { tv?.text = s } }
}

// ============ 方案三：美化 ============
object Beauty {
    fun apply() {
        val cfg = "/sdcard/Android/data/com.tencent.tmgp.pubgmhd/files/UE4Game/ShadowTrackerExtra/ShadowTrackerExtra/Saved/Config/Android/GameUserSettings.ini"
        val content = "[ScalabilityGroups]\n" +
            "sg.ResolutionQuality=100\n" +
            "sg.ViewDistanceQuality=3\n" +
            "sg.AntiAliasingQuality=3\n" +
            "sg.ShadowQuality=3\n" +
            "sg.TextureQuality=3\n" +
            "sg.EffectsQuality=3\n" +
            "sg.PostProcessQuality=3\n" +
            "FrameRateLimit=90.000000\n"
        val b64 = android.util.Base64.encodeToString(content.toByteArray(), android.util.Base64.NO_WRAP)
        val dir = cfg.substringBeforeLast('/')
        SZ.sh("mkdir -p '$dir' && echo '$b64' | base64 -d > '$cfg'")
    }
}

// ============ 前台服务 + 悬浮窗 ============
class AimService : Service() {
    override fun onBind(i: Intent?): IBinder? = null
    override fun onStartCommand(i: Intent?, f: Int, id: Int): Int {
        startForeground(1, notif())
        showOvl()
        return START_STICKY
    }
    private fun notif(): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel("yiqian", "亿千", NotificationManager.IMPORTANCE_LOW)
            (getSystemService(NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
        }
        return Notification.Builder(this, "yiqian")
            .setContentTitle("亿千")
            .setContentText("运行中")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .build()
    }
    private fun showOvl() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        val t = TextView(this)
        t.text = "亿千 待机"
        t.setTextColor(Color.GREEN)
        t.textSize = 12f
        t.setBackgroundColor(0x88000000)
        t.setPadding(20, 10, 20, 10)
        val lp = WindowManager.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT
        )
        lp.gravity = Gravity.TOP or Gravity.START
        lp.x = 30; lp.y = 200
        wm.addView(t, lp)
        Ovl.tv = t
    }
}

// ============ 主界面 ============
class MainActivity : Activity() {
    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        SZ.init(this)

        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), 1)
        }

        val scroll = ScrollView(this)
        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(60, 80, 60, 80)
        scroll.addView(root)
        setContentView(scroll)

        val t1 = TextView(this); t1.text = "亿千"; t1.textSize = 36f
        val t2 = TextView(this); t2.text = "和平精英 · Shizuku 自动注入"; t2.textSize = 15f
        root.addView(t1); root.addView(t2)

        val swRange = Switch(this); swRange.text = "方案一：0.95 范围"
        val swLock = Switch(this); swLock.text = "方案二：强锁自瞄"
        val swBeauty = Switch(this); swBeauty.text = "方案三：美化（高画质）"
        val status = TextView(this); status.text = "状态：未连接"; status.textSize = 14f
        root.addView(swRange); root.addView(swLock); root.addView(swBeauty)

        val b1 = Button(this); b1.text = "连接 Shizuku 并授权"
        b1.setOnClickListener {
            if (SZ.ok()) { SZ.req(); status.text = "已请求授权，去 Shizuku 点允许" }
            else { status.text = "Shizuku 未启动，请先打开 Shizuku" }
        }
        val b2 = Button(this); b2.text = "启动自动注入"
        b2.setOnClickListener {
            if (Build.VERSION.SDK_INT >= 23 && !Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
                Toast.makeText(this, "先开悬浮窗权限", Toast.LENGTH_SHORT).show()
            } else {
                startService(Intent(this, AimService::class.java))
                Watcher.start()
                status.text = "已启动，进游戏自动注入"
            }
        }
        root.addView(b1); root.addView(b2); root.addView(status)

        swRange.setOnCheckedChangeListener { _, on ->
            if (on) { swLock.isChecked = false; Aim.mode = 1; status.text = "方案一启用：0.95 范围" }
        }
        swLock.setOnCheckedChangeListener { _, on ->
            if (on) { swRange.isChecked = false; Aim.mode = 2; status.text = "方案二启用：强锁自瞄" }
        }
        swBeauty.setOnCheckedChangeListener { _, on ->
            if (on) { Beauty.apply(); status.text = "方案三：美化已应用" }
        }

        Shizuku.addRequestPermissionResultListener { _, grant ->
            runOnUiThread { status.text = if (grant == 1) "Shizuku 授权成功" else "授权被拒绝" }
        }
    }
}
