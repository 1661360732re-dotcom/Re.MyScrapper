package com.example.scrapper

import android.annotation.SuppressLint
import android.content.ContentValues
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.View
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONTokener
import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random

class MainActivity : AppCompatActivity() {

    private lateinit var editUrl: EditText
    private lateinit var editSelector: EditText
    private lateinit var editRegex: EditText
    private lateinit var editUserAgent: EditText
    private lateinit var editCookie: EditText
    private lateinit var editDelay: EditText
    private lateinit var editProxy: EditText
    private lateinit var cbUseWebView: CheckBox
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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        try {
            initViews()
            setupSpinners()
            setupListeners()
            loadConfig()
        } catch (e: Exception) {
            Toast.makeText(this, "初始化异常: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initViews() {
        editUrl = findViewById(R.id.editUrl)
        editSelector = findViewById(R.id.editSelector)
        editRegex = findViewById(R.id.editRegex)
        editUserAgent = findViewById(R.id.editUserAgent)
        editCookie = findViewById(R.id.editCookie)
        editDelay = findViewById(R.id.editDelay)
        editProxy = findViewById(R.id.editProxy)
        cbUseWebView = findViewById(R.id.cbUseWebView)
        
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
        spinnerMode.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, extractModes).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerTextColor.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, textColorNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
        spinnerBgColor.adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, bgColorNames).apply {
            setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        }
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
            saveFile("txt", "text/plain", scrapedData.joinToString("\n\n"))
        }

        findViewById<Button>(R.id.btnSaveJson).setOnClickListener {
            if (scrapedData.isEmpty()) return@setOnClickListener
            val jsonStr = scrapedData.joinToString(",\n") { "  \"${it.replace("\"", "\\\"").replace("\n", " ")}\"" }
            saveFile("json", "application/json", "{\n  \"count\": ${scrapedData.size},\n  \"data\": [\n$jsonStr\n  ]\n}")
        }
    }

    private fun startScraping(url: String) {
        tvStatus.text = "状态：正在请求中..."
        resultText.text = ""
        scrapedData.clear()
        btnStart.isEnabled = false

        val selector = editSelector.text.toString().trim()
        val regexStr = editRegex.text.toString().trim()
        val uaInput = editUserAgent.text.toString().trim()
        val cookie = editCookie.text.toString().trim()
        val delayMs = editDelay.text.toString().toLongOrNull() ?: 0L
        val proxyStr = editProxy.text.toString().trim()
        val useWebView = cbUseWebView.isChecked
        val extractMode = modeKeys[spinnerMode.selectedItemPosition]

        val ua = if (uaInput.isBlank()) {
            val uaList = arrayOf(
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36",
                "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 Chrome/120.0.0.0 Safari/537.36",
                "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/605.1.15 Safari/605.1.15"
            )
            uaList[Random.nextInt(uaList.size)]
        } else uaInput

        lifecycleScope.launch {
            val actualDelay = if (delayMs > 0) delayMs + Random.nextLong(500, 1500) else Random.nextLong(100, 500)
            tvStatus.text = "状态：防封延迟中 (${actualDelay}ms)..."
            delay(actualDelay)

            val result = withContext(Dispatchers.IO) {
                runCatching { 
                    val html = if (useWebView) {
                        fetchWithWebView(url)
                    } else {
                        fetchWithOkHttp(url, ua, cookie, proxyStr)
                    }

                    val doc: Document = Jsoup.parse(html, url)
                    val list = mutableListOf<String>()
                    val targetElements = if (selector.isBlank()) doc.select("body") else doc.select(selector)

                    targetElements.forEach { element ->
                        when (extractMode) {
                            "text" -> element.text().trim().takeIf { it.isNotEmpty() }?.let { list.add(it) }
                            "href" -> element.absUrl("href").takeIf { it.isNotEmpty() }?.let { list.add(it) }
                            "src" -> element.absUrl("src").takeIf { it.isNotEmpty() }?.let { list.add(it) }
                            "html" -> element.html().trim().takeIf { it.isNotEmpty() }?.let { list.add(it) }
                        }
                    }

                    var data = list
                    if (regexStr.isNotBlank() && data.isNotEmpty()) {
                        val regex = Regex(regexStr)
                        val matchedList = mutableListOf<String>()
                        data.forEach { rawText ->
                            regex.findAll(rawText).forEach { matchResult -> matchedList.add(matchResult.value) }
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
                resultText.text = "未抓取到数据。\n1. 网页可能是动态加载(请勾选WebView)。\n2. 尝试提取 HTML 源码。"
            } else {
                tvStatus.text = "状态：成功提取 ${data.size} 条"
                val displayStr = data.take(2000).joinToString("\n\n")
                resultText.text = if (data.size > 2000) "$displayStr\n\n...(仅显示前2000条)" else displayStr
            }
        }.onFailure { e ->
            tvStatus.text = "状态：执行失败"
            resultText.text = "错误信息：\n${e.message}"
        }
    }
}

private fun fetchWithOkHttp(url: String, ua: String, cookie: String, proxyStr: String): String {
    val builder = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .followRedirects(true)
        
        if (proxyStr.isNotBlank() && proxyStr.contains(":")) {
            val parts = proxyStr.split(":")
            val ip = parts[0]
            val port = parts.getOrNull(1)?.toIntOrNull() ?: 8080
            builder.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress(ip, port)))
        }

        val client = builder.build()
        val reqBuilder = Request.Builder().url(url).header("User-Agent", ua)
        if (cookie.isNotBlank()) reqBuilder.header("Cookie", cookie)

        client.newCall(reqBuilder.build()).execute().use { response ->
            if (!response.isSuccessful) throw RuntimeException("HTTP ${response.code}")
            return response.body?.string() ?: throw RuntimeException("响应为空")
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private suspend fun fetchWithWebView(url: String): String = withContext(Dispatchers.Main) {
        suspendCoroutine { continuation ->
            val webView = WebView(this@MainActivity).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                settings.userAgentString = editUserAgent.text.toString().trim().ifBlank { 
                    "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 Chrome/120.0 Mobile Safari/537.36" 
                }
            }

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    view?.evaluateJavascript("document.documentElement.outerHTML") { value ->
                        try {
                            if (value.isNullOrBlank() || value == "null") {
                                continuation.resumeWithException(RuntimeException("WebView 返回空数据"))
                            } else {
                                val unescapedHtml = JSONTokener(value).nextValue().toString()
                                continuation.resumeWith(Result.success(unescapedHtml))
                            }
                        } catch (e: Exception) {
                            continuation.resumeWithException(e)
                        } finally {
                            webView.destroy()
                        }
                    }
                }

                override fun onReceivedError(view: WebView?, request: WebResourceRequest?, error: WebResourceError?) {
                    super.onReceivedError(view, request, error)
                    if (request?.isForMainFrame == true) {
                        continuation.resumeWithException(RuntimeException("WebView 加载失败: ${error?.description}"))
                        webView.destroy()
                    }
                }
            }
            webView.loadUrl(url)
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
            putString("proxy", editProxy.text.toString())
            putBoolean("useWebView", cbUseWebView.isChecked)
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
        editProxy.setText(prefs.getString("proxy", ""))
        cbUseWebView.isChecked = prefs.getBoolean("useWebView", false)
        
        spinnerMode.setSelection(prefs.getInt("mode", 0))

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
            
