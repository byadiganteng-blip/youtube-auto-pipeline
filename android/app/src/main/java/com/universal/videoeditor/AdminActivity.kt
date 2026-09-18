package com.universal.videoeditor
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
class AdminActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_admin)
        val tvU = findViewById<TextView>(R.id.tvAdminUser)
        val etTok = findViewById<EditText>(R.id.etManualToken)
        val btnSave = findViewById<MaterialButton>(R.id.btnSaveToken)
        val btnLogout = findViewById<MaterialButton>(R.id.btnLogout)
        val u = SecureConfig.user(this); val t = SecureConfig.token(this)
        tvU.text = if (u != null && !t.isNullOrEmpty())
            "👤 Login: $u\n🔑 Token: ${t.take(8)}…${t.takeLast(4)}"
        else "⚠️ Belum login"
        etTok.isEnabled = false
        etTok.setText("(token tertanam)")
        btnSave.isEnabled = false
        btnLogout.setOnClickListener {
            SecureConfig.clear(this)
            Toast.makeText(this,"Logout",Toast.LENGTH_SHORT).show()
        }
    }
}
