package com.universal.videoeditor

import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import org.w3c.dom.Document
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.StringWriter
import javax.xml.parsers.DocumentBuilderFactory
import javax.xml.transform.OutputKeys
import javax.xml.transform.TransformerFactory
import javax.xml.transform.dom.DOMSource
import javax.xml.transform.stream.StreamResult

class FeatureAdderActivity : AppCompatActivity() {

    private lateinit var etName: EditText
    private lateinit var etCode: EditText
    private lateinit var spType: Spinner
    private lateinit var tvStatus: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_feature_adder)

        etName   = findViewById(R.id.etFeatureName)
        etCode   = findViewById(R.id.etCode)
        spType   = findViewById(R.id.spType)
        tvStatus = findViewById(R.id.tvStatus)

        spType.adapter = ArrayAdapter(this,
            android.R.layout.simple_spinner_dropdown_item,
            arrayOf("manifest-permission", "manifest-activity", "manifest-service",
                    "manifest-receiver", "kotlin-file"))

        findViewById<Button>(R.id.btnApply).setOnClickListener { applyFeature() }
    }

    private fun applyFeature() {
        val name = etName.text.toString().trim()
        val code = etCode.text.toString().trim()
        val type = spType.selectedItem.toString()

        if (name.isEmpty()) {
            toast("Nama wajib diisi"); return
        }

        tvStatus.text = "⏳ Processing..."

        lifecycleScope.launch {
            try {
                val result = when (type) {
                    "kotlin-file" -> {
                        if (code.isEmpty()) return@launch
                        val path = "android/app/src/main/java/com/universal/videoeditor/$name.kt"
                        val (ok, msg) = AdminApi.updateFile(path, code, "feat: add $name")
                        if (ok) "✅ $name.kt ditambahkan" else "❌ $msg"
                    }
                    else -> editManifest(type.removePrefix("manifest-"), name, code)
                }
                tvStatus.text = result
            } catch (e: Exception) {
                tvStatus.text = "❌ ${e.message}"
            }
        }
    }

    private suspend fun editManifest(tag: String, name: String, extra: String): String {
        val path = "android/app/src/main/AndroidManifest.xml"
        val existing = AdminApi.getFile(path) ?: return "❌ Gagal baca manifest"

        val xml = existing.first
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true

        val doc = factory.newDocumentBuilder()
            .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))

        val root = doc.documentElement ?: return "❌ Manifest kosong"

        // Cek duplikat untuk permission / activity
        val existingEls = root.getElementsByTagName(tag.replace("permission", "uses-permission"))
        for (i in 0 until existingEls.length) {
            val el = existingEls.item(i) as? Element ?: continue
            if (el.getAttribute("android:name") == name) {
                return "⚠️ $name sudah ada"
            }
        }

        when (tag) {
            "permission" -> {
                val el = doc.createElement("uses-permission")
                el.setAttribute("android:name", name)
                // Insert sebelum <application>
                val app = root.getElementsByTagName("application").item(0)
                if (app != null) root.insertBefore(el, app) else root.appendChild(el)
            }
            "activity", "service", "receiver" -> {
                val app = root.getElementsByTagName("application").item(0) as? Element
                    ?: return "❌ <application> tidak ditemukan"
                val el = doc.createElement(tag)
                el.setAttribute("android:name", name)
                if (extra.contains("exported", true)) el.setAttribute("android:exported", "true")
                app.appendChild(el)
            }
        }

        // Serialize — TANPA re-declare namespace (sudah ada di root)
        val tf = TransformerFactory.newInstance().newTransformer()
        tf.setOutputProperty(OutputKeys.INDENT, "yes")
        tf.setOutputProperty(OutputKeys.ENCODING, "utf-8")
        tf.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "no")
        tf.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "4")

        val writer = StringWriter()
        tf.transform(DOMSource(doc), StreamResult(writer))
        var newXml = writer.toString()

        // FIX: pastikan xmlns:android tetap ada
        if ("xmlns:android=" !in newXml) {
            newXml = newXml.replace("<manifest", """<manifest xmlns:android="http://schemas.android.com/apk/res/android"""")
        }

        val (ok, msg) = AdminApi.updateFile(path, newXml, "feat(manifest): add $name")
        return if (ok) "✅ $name ditambahkan ke manifest" else "❌ $msg"
    }

    private fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()
}
