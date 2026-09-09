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
            launchFloating(ServiceTarget.TIKTOK)
        }
        findViewById<android.widget.Button>(R.id.btnSpotify).setOnClickListener {
            launchFloating(ServiceTarget.SPOTIFY)
        }
        findViewById<android.widget.Button>(R.id.btnMyVideos).setOnClickListener {
            startActivity(Intent(this, MyVideosActivity::class.java))
        }
        findViewById<android.widget.Button>(R.id.btnSettings).setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
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
    // نفتح المواقع الرسمية نفسها داخل الـ WebView العائم - لا يوجد أي محتوى مقلَّد
    TIKTOK("https://www.tiktok.com/", "TikTok"),
    SPOTIFY("https://open.spotify.com/", "Spotify")
}
