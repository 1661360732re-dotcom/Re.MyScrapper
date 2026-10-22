package com.example.scrapper

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.util.concurrent.TimeUnit

class MainActivity : AppCompatActivity() {

    private lateinit var editUrl: EditText
    private lateinit var btnStart: Button
    private lateinit var btnClear: Button
    private lateinit var btnSave: Button
    private lateinit var resultText: TextView

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .writeTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private var scrapedData = mutableListOf<String>()

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (!granted) {
            Toast.makeText(this, "未授予存储权限，保存功能受限", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        editUrl = findViewById(R.id.editUrl)
        btnStart = findViewById(R.id.btnStart)
        btnClear = findViewById(R.id.btnClear)
        btnSave = findViewById(R.id.btnSave)
        resultText = findViewById(R.id.resultText)

        checkStoragePermission()

        btnStart.setOnClickListener {
            val url = editUrl.text.toString().trim()
            if (url.isBlank() || (!url.startsWith("http://") && !url.startsWith("https://"))) {
                Toast.makeText(this, "请输入正确的 URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            startScraping(url)
        }

        btnClear.setOnClickListener {
            editUrl.setText("")
            scrapedData.clear()
            resultText.text = ""
        }

        btnSave.setOnClickListener {
            if (scrapedData.isEmpty()) {
                Toast.makeText(this, "没有数据可以保存", Toast.LENGTH_SHORT).show()
            } else {
                saveResults()
            }
        }
    }

    private fun checkStoragePermission() {
        if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED) {
                permissionLauncher.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
        }
    }

    private fun startScraping(url: String) {
        resultText.text = "正在抓取... 请稍候"
        btnStart.isEnabled = false

        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                runCatching { fetchAndParse(url) }
            }
            btnStart.isEnabled = true

            result.onSuccess { data ->
                scrapedData.clear()
                scrapedData.addAll(data)
                resultText.text = if (data.isEmpty()) "未抓取到任何内容" else "共抓取 ${data.size} 条：\n\n" + data.take(500).joinToString("\n")
                Toast.makeText(this@MainActivity, "抓取完成", Toast.LENGTH_SHORT).show()
            }.onFailure { e ->
                resultText.text = "抓取失败: ${e.message}"
            }
        }
    }

    private fun fetchAndParse(url: String): List<String> {
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36")
            .build()

        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
            val html = response.body?.string() ?: throw RuntimeException("响应为空")
            val doc: Document = Jsoup.parse(html, url)
            return extractData(doc)
        }
    }

    private fun extractData(doc: Document): List<String> {
        val list = mutableListOf<String>()
        doc.title().takeIf { it.isNotBlank() }?.let { list.add("【页面标题】$it") }
        doc.select("h1, h2, h3, p").forEach {
            val t = it.text().trim()
            if (t.isNotEmpty()) list.add(t)
        }
        return list
    }

    private fun saveResults() {
        val fileName = "scraped_${System.currentTimeMillis()}.txt"
        val content = scrapedData.j
