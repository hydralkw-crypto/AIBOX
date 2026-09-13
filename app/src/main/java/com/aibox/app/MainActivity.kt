package com.aibox.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity

/**
 * الشاشة الرئيسية لـ AIBOX.
 * تفتح التطبيق دائمًا بملء الشاشة (كتطبيق عادي).
 * الضغط على TikTok أو Spotify يشغّل FloatingWindowService الذي يفتح
 * الموقع الرسمي لتلك الخدمة داخل نافذة عائمة قابلة للسحب/التكبير/التصغير.
 */
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        findViewById<android.widget.Button>(R.id.btnTikTok).setOnClickListener {
            openBrave()
        }
        findViewById<android.widget.Button>(R.id.btnSpotify).setOnClickListener {
            launchFloating(ServiceTarget.SPOTIFY)
        }
        findViewById<android.widget.Button>(R.id.btnYoutube).setOnClickListener {
            launchFloating(ServiceTarget.YOUTUBE)
        }
        findViewById<android.widget.Button>(R.id.btnLikee).setOnClickListener {
            launchFloating(ServiceTarget.LIKEE)
        }
        findViewById<android.widget.Button>(R.id.btnMyVideos).setOnClickListener {
            startActivity(Intent(this, MyVideosActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }
    }

    /**
     * يفتح متصفح Brave الحقيقي (تطبيق مستقل) - وليس WebView داخل AIBOX.
     * هذا يشتغل بملء الشاشة، مو عائم، لأن أندرويد ما يسمح بتصغير تطبيق ثاني مثبت
     * داخل نافذة يتحكم فيها تطبيق آخر. إذا Brave غير مثبت، نوديك لصفحته بالمتجر.
     */
    private fun openBrave() {
        val bravePackage = "com.brave.browser"
        val launchIntent = packageManager.getLaunchIntentForPackage(bravePackage)
        if (launchIntent != null) {
            startActivity(launchIntent)
        } else {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$bravePackage")))
            } catch (e: Exception) {
                startActivity(
                    Intent(
                        Intent.ACTION_VIEW,
                        Uri.parse("https://play.google.com/store/apps/details?id=$bravePackage")
                    )
                )
            }
        }
    }

    private fun launchFloating(target: ServiceTarget) {
        if (!hasOverlayPermission()) {
            Toast.makeText(this, getString(R.string.overlay_permission_needed), Toast.LENGTH_LONG).show()
            requestOverlayPermission()
            return
        }
        val intent = Intent(this, FloatingWindowService::class.java).apply {
            putExtra(FloatingWindowService.EXTRA_TARGET, target.name)
        }
        startForegroundService(intent)
        // نعيد AIBOX نفسه إلى الخلفية حتى تظهر النافذة العائمة فوق التطبيقات الأخرى
        moveTaskToBack(true)
    }

    private fun hasOverlayPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Settings.canDrawOverlays(this)
        } else true
    }

    private fun requestOverlayPermission() {
        val intent = Intent(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            Uri.parse("package:$packageName")
        )
        startActivity(intent)
    }
}

enum class ServiceTarget(val url: String, val label: String) {
    // كل هذي تشتغل عبر WebView تابع لـ AIBOX (مو تطبيقات مستقلة، ومو نسخ مقلَّدة)
    SPOTIFY("https://open.spotify.com/", "Spotify"),
    YOUTUBE("https://m.youtube.com/", "YouTube"),
    LIKEE("https://likee.video/", "Likee")
}
