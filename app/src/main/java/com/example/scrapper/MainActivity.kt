package com.example.scrapper

import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
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
    private lateinit var editSelector: EditText
    private lateinit var editRegex: EditText
    private lateinit var editUserAgent: EditText
    private lateinit var editCookie: EditText
    private lateinit var editDelay: EditText
    private lateinit var spinnerMode: Spinner
    private lateinit var layoutAdvanced: LinearLayout
    private lateinit var tvAdvancedToggle: TextView
    
    private lateinit var layoutUi: LinearLayout
    private lateinit var tvUiToggle: TextView
    private lateinit var seekBarFontSize: SeekBar
    private lateinit var spinnerTextColor: Spinner
    private lateinit var spinnerBgColor: Spinner
    private lateinit var scrollViewResult: ScrollView

    private lateinit var tvStatus: TextView
    private lateinit var resultText: TextView
    private lateinit var btnStart: Button

    private var scrapedData = mutableListOf<String>()
    private var isAdvancedVisible = false
    private var isUiVisible = false

    private val extractModes = arrayOf("提取纯文本 (Text)", "提取链接 (href)", "提取图片地址 (src)", "提取 HTML 源码")
    private val modeKeys = arrayOf("text", "href", "src", "html")

    private val textColorNames = arrayOf("极客绿", "纯白", "黑客红", "科技蓝")
    private val textColorValues = arrayOf("#00FF00", "#FFFFFF", "#FF4444", "#00BFFF")
    
    private val bgColorNames = arrayOf("深邃黑", "暗夜灰", "护眼绿", "纯白")
    private val bgColorValues = arrayOf("#121212", "#2D2D2D", "#CCE8CF", "#FFFFFF")

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // 防崩溃保护：安全初始化
        try {
            initViews()
            setupSpinners()
            setupListeners()
            loadConfig()
        } catch (e: Exception) {
            e.printStackTrace()
            Toast.makeText(this, "启动初始化异常: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews() {
        editUrl = findViewById(R.id.editUrl)
        editSelector = findViewById(R.id.editSelector)
        editRegex = findViewById(R.id.editRegex)
        editUserAgent = findViewById(R.id.editUserAgent)
        editCookie = findViewById(R.id.editCookie)
        editDelay = findViewById(R.id.editDelay)
        spinnerMode = findViewById(R.id.spinnerMode)
        layoutAdvanced = findViewById(R.id.layoutAdvanced)
        tvAdvancedToggle = findViewById(R.id.tvAdvancedToggle)
        
        layoutUi = findViewById(R.id.layoutUi)
        tvUiToggle = findViewById(R.id.tvUiToggle)
        seekBarFontSize = findViewById(R.id.seekBarFontSize)
        spinnerTextColor = findViewById(R.id.spinnerTextColor)
        spinnerBgColor = findViewById(R.id.spinnerBgColor)
        scrollViewResult = findViewById(R.id.scrollViewResult)

        tvStatus = findViewById(R.id.tvStatus)
        resultText = findViewById(R.id.resultText)
        btnStart = findViewById(R.id.btnStart)
    }

    private fun setupSpinners() {
        val adapterMode = ArrayAdapter(this, android.R.layout.simple_spinner_item, extractModes)
        adapterMode.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerMode.adapter = adapterMode

        val adapterTextColor = ArrayAdapter(this, android.R.layout.simple_spinner_item, textColorNames)
        adapterTextColor.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerTextColor.adapter = adapterTextColor

        val adapterBgColor = ArrayAdapter(this, android.R.layout.simple_spinner_item, bgColorNames)
        adapterBgColor.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerBgColor.adapter = adapterBgColor
    }

    private fun setupListeners() {
        tvAdvancedToggle.setOnClickListener {
            isAdvancedVisible = !isAdvancedVisible
            layoutAdvanced.visibility = if (isAdvancedVisible) View.VISIBLE else View.GONE
            tvAdvancedToggle.text = if (isAdvancedVisible) "▼ 高级请求设置" else "▶ 高级请求设置"
        }

        tvUiToggle.setOnClickListener {
            isUiVisible = !isUiVisible
layoutUi.visibility = if (isUiVisible) View.VISIBLE else View.GONE
            tvUiToggle.text = if (isUiVisible) "▼ 外观与界面调节" else "▶ 外观与界面调节"
        }

        seekBarFontSize.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                resultText.textSize = (12 + progress).toFloat()
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) { saveConfig() }
        })

        spinnerTextColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in textColorValues.indices) {
                    resultText.setTextColor(android.graphics.Color.parseColor(textColorValues[position]))
                    saveConfig()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        spinnerBgColor.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                if (position in bgColorValues.indices) {
                    scrollViewResult.setBackgroundColor(android.graphics.Color.parseColor(bgColorValues[position]))
                    saveConfig()
                }
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        btnStart.setOnClickListener {
            val url = editUrl.text.toString().trim()
            if (url.isBlank() || (!url.startsWith("http"))) {
                Toast.makeText(this, "请输入有效的 URL", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            saveConfig()
            startScraping(url)
        }

        findViewById<Button>(R.id.btnSaveTxt).setOnClickListener {
            if (scrapedData.isEmpty()) return@setOnClickListener
            val content = scrapedData.joinToString("\n\n")
            saveFile("txt", "text/plain", content)
        }

        findViewById<Button>(R.id.btnSaveJson).setOnClickListener {
            if (scrapedData.isEmpty()) return@setOnClickListener
            val jsonStr = scrapedData.joinToString(",\n") { "  \"${it.replace("\"", "\\\"").replace("\n", " ")}\"" }
            val content = "{\n  \"count\": ${scrapedData.size},\n  \"data\": [\n$jsonStr\n  ]\n}"
            saveFile("json", "application/json", content)
        }
    }

    private fun startScraping(url: String) {
        tvStatus.text = "状态：正在请求中..."
        resultText.text = ""
        scrapedData.clear()
        btnStart.isEnabled = false

        val selector = editSelector.text.toString().trim()
        val regexStr = editRegex.text.toString().trim()
        val ua = editUserAgent.text.toString().trim().ifBlank { "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36" }
        val cookie = editCookie.text.toString().trim()
        val delayMs = editDelay.text.toString().toLongOrNull() ?: 0L
        val extractMode = modeKeys[spinnerMode.selectedItemPosition]

        lifecycleScope.launch {
            if (delayMs > 0) {
                tvStatus.text = "状态：防封延迟中 (${delayMs}ms)..."
                delay(delayMs)
            }

            val result = withContext(Dispatchers.IO) {
                runCatching { 
                    var data = fetchAndParse(url, selector, ua, cookie, extractMode)
                    if (regexStr.isNotBlank() && data.isNotEmpty()) {
                        val regex = Regex(regexStr)
                        val matchedList = mutableListOf<String>()
                        data.forEach { rawText ->
                            regex.findAll(rawText).forEach { matchResult ->
                                matchedList.add(matchResult.value)
                            }
                        }
                        data = matchedList
                    }
                    data
                }
            }
            btnStart.isEnabled = true

            result.onSuccess { data ->
                scrapedData.addAll(data)
                if (data.isEmpty()) {
                    tvStatus.text = "状态：未找到内容"
                    resultText.text = "未抓取到数据。\n1. 请检查选择器\n2. 尝试提取源码"
                } else {
                    tvStatus.text = "状态：成功提取 ${data.size} 条"
                    val displayList = data.take(2000)
                    val displayStr = displayList.joinToString("\n\n")
                    resultText.text = if (data.size > 2000) {
                        "$displayStr\n\n...(仅显示前2000条)"
                    } else {
                        displayStr
                    }
                }
            }.onFailure { e ->
                tvStatus.text = "状态：执行失败"
                resultText.text = "错误信息：\n${e.message}"
            }
}
    }

    private fun fetchAndParse(url: String, selector: String, ua: String, cookie: String, mode: String): List<String> {
        val reqBuilder = Request.Builder()
            .url(url)
            .header("User-Agent", ua)
        
        if (cookie.isNotBlank()) reqBuilder.header("Cookie", cookie)

        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
            val html = response.body?.string() ?: throw RuntimeException("响应为空")
            val doc: Document = Jsoup.parse(html, url)
            val list = mutableListOf<String>()

            val targetElements = if (selector.isBlank()) doc.select("body") else doc.select(selector)

            targetElements.forEach { element ->
                when (mode) {
                    "text" -> {
                        val t = element.text().trim()
                        if (t.isNotEmpty()) list.add(t)
                    }
                    "href" -> {
                        val href = element.absUrl("href")
                        if (href.isNotEmpty()) list.add(href)
                    }
                    "src" -> {
                        val src = element.absUrl("src")
                        if (src.isNotEmpty()) list.add(src)
                    }
                    "html" -> {
                        val h = element.html().trim()
                        if (h.isNotEmpty()) list.add(h)
                    }
                }
            }
            return list
        }
    }

    private fun saveFile(ext: String, mimeType: String, content: String) {
        val fileName = "scraped_${System.currentTimeMillis()}.$ext"
        lifecycleScope.launch {
            val ok = withContext(Dispatchers.IO) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                        val values = ContentValues().apply {
                            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
                            put(MediaStore.MediaColumns.MIME_TYPE, mimeType)
                            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS)
                        }
                        val uri = contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values)
                        contentResolver.openOutputStream(uri!!)?.use { it.write(content.toByteArray()) }
                    } else {
                        val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
                        File(dir, fileName).writeText(content)
                    }
                    true
                } catch (e: Exception) {
                    false
                }
            }
            Toast.makeText(this@MainActivity, if (ok) "已保存到 Downloads" else "保存失败", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveConfig() {
        val prefs = getSharedPreferences("scrapper_prefs", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("url", editUrl.text.toString())
            putString("selector", editSelector.text.toString())
            putString("regex", editRegex.text.toString())
            putString("ua", editUserAgent.text.toString())
            putString("cookie", editCookie.text.toString())
            putString("delay", editDelay.text.toString())
            putInt("mode", spinnerMode.selectedItemPosition)
            putInt("fontSizeProgress", seekBarFontSize.progress)
            putInt("textColorIndex", spinnerTextColor.selectedItemPosition)
            putInt("bgColorIndex", spinnerBgColor.selectedItemPosition)
            apply()
        }
    }

    private fun loadConfig() {
        val prefs = getSharedPreferences("scrapper_prefs", Context.MODE_PRIVATE)
        editUrl.setText(prefs.getString("url", ""))
        editSelector.setText(prefs.getString("selector", ""))
        editRegex.setText(prefs.getString("regex", ""))
        editUserAgent.setText(prefs.getString("ua", ""))
        editCookie.setText(prefs.getString("cookie", ""))
        editDelay.setText(prefs.getString("delay", "0"))
        
        val modeIdx = prefs.getInt("mode", 0)
        if (modeIdx in extractModes.indices) spinnerMode.setSelection(modeIdx)

        val fontProgress = prefs.getInt("fontSizeProgress", 2)
        seekBarFontSize.progress = fontProgress
        resultText.textSize = (12 + fontProgress).toFloat()

        val textColorIdx = prefs.getInt("textColorIndex", 0)
        if (textColorIdx in textColorNames.indices) {
            spinnerTextColor.setSelection(textColorIdx)
            resultText.setTextColor(android.graphics.Color.parseColor(textColorValues[textColorIdx]))
        }

        val bgColorIdx = prefs.getInt("bgColorIndex", 0)
        if (bgColorIdx in bgColorNames.indices) {
            spinnerBgColor.setSelection(bgColorIdx)
            scrollViewResult.setBackgroundColor(android.graphics.Color.parseColor(bgColorValues[bgColorIdx]))
        }
    }
}
