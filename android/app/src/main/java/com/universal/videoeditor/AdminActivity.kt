package com.universal.videoeditor
import android.os.Bundle
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
class AdminActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_admin)
        val tvU = findViewById<TextView>(R.id.tvAdminUser)
        val u = SecureConfig.user(this); val t = SecureConfig.token(this)
        tvU.text = if (u != null && !t.isNullOrEmpty())
            "👤 Login: $u\n🔑 Token: ${t.take(8)}…${t.takeLast(4)}"
        else "⚠️ Belum login"
        findViewById<MaterialButton>(R.id.btnLogout).setOnClickListener {
            SecureConfig.clear(this); Toast.makeText(this,"Logout",Toast.LENGTH_SHORT).show()
        }
    }
}
