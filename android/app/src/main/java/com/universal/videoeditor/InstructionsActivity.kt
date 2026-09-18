package com.universal.videoeditor
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity

class InstructionsActivity : AppCompatActivity() {
    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        setContentView(R.layout.activity_instructions)
        val container = findViewById<LinearLayout>(android.R.id.content)
            .getChildAt(0) as LinearLayout
        // fallback: findViewById tidak return LinearLayout untuk include
        val steps = listOf(
            "Izinkan akses saat aplikasi pertama kali dibuka",
            "Buka menu utama dan tempel link video",
            "Atur durasi, kualitas, mode watermark & tipe upload",
            "Tekan tombol PROSES VIDEO",
            "Tunggu proses selesai — progress akan muncul",
            "Video otomatis tersimpan di folder Downloads/YadClipper",
            "Buka menu Hasil Video untuk melihat video yang sudah jadi"
        )
        val root = findViewById<LinearLayout>(R.id.step1).parent as LinearLayout
        for ((i, txt) in steps.withIndex()) {
            val item = layoutInflater.inflate(R.layout.item_instruction, root, false)
            item.findViewById<TextView>(R.id.tvNumber).text = (i + 1).toString()
            item.findViewById<TextView>(R.id.tvText).text = txt
            root.addView(item)
        }
    }
}
