package com.aibox.app

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

/**
 * شاشة الإعدادات: إذن النافذة العائمة، وربط/فصل حساب Spotify.
 * ملاحظة: ربط Spotify الحقيقي (OAuth PKCE) يحتاج Client ID خاص بك من
 * Spotify Developer Dashboard - راجع README.md لمعرفة كيفية الحصول عليه ووضعه.
 */
class SettingsActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        findViewById<Button>(R.id.btnOverlayPermission).setOnClickListener {
            startActivity(
                Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
            )
        }

        findViewById<Button>(R.id.btnConnectSpotify).setOnClickListener {
            // TODO (المرحلة القادمة): تنفيذ Spotify OAuth PKCE الفعلي هنا
            // بعد أن تضع Client ID الخاص بك في gradle.properties كما هو موضح في README.md
        }

        findViewById<Button>(R.id.btnMinimizeFloating).setOnClickListener {
            sendFloatingCommand(FloatingWindowService.ACTION_MINIMIZE)
        }
        findViewById<Button>(R.id.btnCloseFloating).setOnClickListener {
            sendFloatingCommand(FloatingWindowService.ACTION_CLOSE)
        }

        updateOverlayStatus()
        updateFloatingStatus()
    }

    override fun onResume() {
        super.onResume()
        updateOverlayStatus()
        updateFloatingStatus()
    }

    /** يرسل أمر تصغير أو إغلاق إلى FloatingWindowService إن كانت شغالة حاليًا */
    private fun sendFloatingCommand(action: String) {
        if (!isFloatingRunning()) return
        val intent = Intent(action).setPackage(packageName)
        sendBroadcast(intent)
        if (action == FloatingWindowService.ACTION_CLOSE) {
            // نحدّث الحالة فورًا بالواجهة بدل انتظار إعادة فتح الشاشة
            findViewById<TextView>(R.id.floatingStatus).text = "لا توجد نافذة عائمة نشطة حاليًا"
        }
    }

    private fun isFloatingRunning(): Boolean =
        getSharedPreferences(FloatingWindowService.RUNNING_PREFS, MODE_PRIVATE)
            .getBoolean(FloatingWindowService.KEY_RUNNING, false)

    private fun updateFloatingStatus() {
        findViewById<TextView>(R.id.floatingStatus).text =
            if (isFloatingRunning()) "النافذة العائمة نشطة الآن ✅"
            else "لا توجد نافذة عائمة نشطة حاليًا"
    }

    private fun updateOverlayStatus() {
        val granted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
            Settings.canDrawOverlays(this) else true
        findViewById<TextView>(R.id.overlayStatus).text =
            if (granted) "Overlay permission: granted ✅" else "Overlay permission: not granted ❌"
    }
}
