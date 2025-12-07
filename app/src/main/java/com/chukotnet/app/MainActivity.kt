package com.chukotnet.app

import android.app.AlarmManager
import android.app.AlertDialog
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.Html
import android.text.InputType
import android.text.TextWatcher
import android.view.View
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.*
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.jsoup.Connection
import org.jsoup.Jsoup
import java.nio.charset.Charset
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.HashMap
import java.util.Locale

// ГЛАВНЫЙ ЭКРАН ПРИЛОЖЕНИЯ
// Здесь происходит всё: и вход, и показ новостей, и статистика.
class MainActivity : AppCompatActivity() {

    // === ПЕРЕМЕННЫЕ ИНТЕРФЕЙСА (UI) ===
    // Это ссылки на элементы, которые мы нарисовали в XML.
    // lateinit означает "я инициализирую это позже" (в onCreate), чтобы не писать везде null.

    // Контейнеры для вкладок (переключаем их видимость)
    private lateinit var layoutHome: android.widget.ScrollView // Главная (Кабинет)
    private lateinit var layoutNews: android.widget.LinearLayout // Новости
    private lateinit var layoutTariffs: android.widget.LinearLayout // Тарифы
    private lateinit var layoutStats: android.widget.LinearLayout // Статистика
    private lateinit var bottomNav: BottomNavigationView // Нижнее меню

    // Элементы главной вкладки
    private lateinit var etLogin: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnLogin: Button
    private lateinit var cbRemember: CheckBox
    private lateinit var tvResult: TextView // Сюда пишем баланс и договор
    private lateinit var progressBar: ProgressBar

    // Элементы оплаты
    private lateinit var etPayAccount: EditText
    private lateinit var etPayAmount: EditText
    private lateinit var btnCheckPayment: Button
    private lateinit var tvPayOwnerName: TextView
    private lateinit var btnGoToBank: Button // Зеленая кнопка "Оплатить"

    // Скрытый браузер для проведения оплаты (всплывает поверх всего)
    private lateinit var webView: WebView

    // Вкладка Новости
    private lateinit var webViewNews: WebView
    private lateinit var progressNews: ProgressBar

    // Вкладка Тарифы
    private lateinit var webViewTariffs: WebView
    private lateinit var progressTariffs: ProgressBar

    // Вкладка Статистика
    private lateinit var webViewStats: WebView
    private lateinit var progressStats: ProgressBar
    private lateinit var btnRefreshStats: Button
    private lateinit var spinnerReportType: Spinner // Выпадающий список выбора отчета

    // Кнопка настроек (шестеренка)
    private lateinit var btnSettings: FloatingActionButton

    // === ХРАНЕНИЕ ДАННЫХ ===
    // Куки нужны, чтобы сервер помнил, что мы вошли. Без них статистика не откроется.
    private var loginCookies: MutableMap<String, String> = HashMap()
    // Сюда сохраняем HTML-форму для банка, чтобы показать её в WebView
    private var paymentHtmlForm: String = ""

    // Флаги: загружали мы уже данные или нет? Чтобы не грузить каждый раз при переключении вкладки.
    private var isNewsLoaded = false
    private var isTariffsLoaded = false
    private var isStatsLoaded = false

    // === ЖИЗНЕННЫЙ ЦИКЛ ===
    // Эта функция запускается первой при старте приложения.
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // 1. Восстанавливаем тему (Темная/Светлая), которую выбрал пользователь
        applySavedTheme()

        // 2. Загружаем дизайн из XML
        setContentView(R.layout.activity_main)

        // 3. Находим все кнопки и текстовые поля в макете
        initViews()
        // 4. Настраиваем, что будет происходить при нажатиях
        setupListeners()
        // 5. Если пароль был сохранен, подставляем его
        restoreSession()

        // 6. Настраиваем кнопку "Назад" на телефоне
        // Если открыт WebView оплаты - закрываем его. Иначе - закрываем приложение.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (webView.visibility == View.VISIBLE) {
                    if (webView.canGoBack()) {
                        webView.goBack() // Вернуться на страницу назад в браузере
                    } else {
                        webView.visibility = View.GONE // Скрыть браузер
                        webView.loadUrl("about:blank") // Очистить память
                    }
                } else {
                    isEnabled = false
                    onBackPressedDispatcher.onBackPressed() // Закрыть приложение
                }
            }
        })
    }

    // Функция поиска всех элементов на экране
    private fun initViews() {
        // Сначала ищем контейнеры
        layoutHome = findViewById(R.id.layoutHome)
        layoutNews = findViewById(R.id.layoutNews)
        layoutTariffs = findViewById(R.id.layoutTariffs)
        layoutStats = findViewById(R.id.layoutStats)
        bottomNav = findViewById(R.id.bottomNav)

        // Элементы входа
        etLogin = findViewById(R.id.etLogin)
        etPassword = findViewById(R.id.etPassword)
        cbRemember = findViewById(R.id.cbRemember)
        btnLogin = findViewById(R.id.btnLogin)
        tvResult = findViewById(R.id.tvResult)
        progressBar = findViewById(R.id.progressBar)

        // Элементы оплаты
        etPayAccount = findViewById(R.id.etPayAccount)
        etPayAmount = findViewById(R.id.etPayAmount)
        btnCheckPayment = findViewById(R.id.btnCheckPayment)
        tvPayOwnerName = findViewById(R.id.tvPayOwnerName)
        btnGoToBank = findViewById(R.id.btnGoToBank)
        webView = findViewById(R.id.webView)

        // Новости и Тарифы
        webViewNews = findViewById(R.id.webViewNews)
        progressNews = findViewById(R.id.progressNews)
        webViewTariffs = findViewById(R.id.webViewTariffs)
        progressTariffs = findViewById(R.id.progressTariffs)

        // Статистика
        webViewStats = findViewById(R.id.webViewStats)
        progressStats = findViewById(R.id.progressStats)
        btnRefreshStats = findViewById(R.id.btnRefreshStats)
        spinnerReportType = findViewById(R.id.spinnerReportType)

        // Кнопка настроек
        btnSettings = findViewById(R.id.btnSettings)

        // Заполняем выпадающий список отчетов
        val reports = arrayOf("Трафик (Интернет)", "История платежей", "Абонентская плата")
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, reports)
        spinnerReportType.adapter = adapter

        // Включаем JavaScript в WebView (нужно для отображения контента)
        val webSettings = webView.settings
        webSettings.javaScriptEnabled = true
        webSettings.domStorageEnabled = true
        webView.webViewClient = object : WebViewClient() {
            // Ссылка должна открываться внутри приложения, а не в Chrome
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                view.loadUrl(url)
                return true
            }
        }
    }

    // Настройка всех нажатий и действий
    private fun setupListeners() {
        // Переключение вкладок внизу
        bottomNav.setOnItemSelectedListener { item ->
            // Сначала скрываем все
            layoutHome.visibility = View.GONE
            layoutNews.visibility = View.GONE
            layoutTariffs.visibility = View.GONE
            layoutStats.visibility = View.GONE

            // Показываем нужную вкладку
            when (item.itemId) {
                R.id.nav_home -> {
                    layoutHome.visibility = View.VISIBLE
                    true
                }
                R.id.nav_news -> {
                    layoutNews.visibility = View.VISIBLE
                    if (!isNewsLoaded) loadNews() // Грузим новости, если еще не грузили
                    true
                }
                R.id.nav_tariffs -> {
                    layoutTariffs.visibility = View.VISIBLE
                    if (!isTariffsLoaded) loadTariffs()
                    true
                }
                R.id.nav_stats -> {
                    layoutStats.visibility = View.VISIBLE
                    // Грузим отчет, который выбран сейчас в списке
                    if (!isStatsLoaded) loadStatistics(spinnerReportType.selectedItemPosition)
                    true
                }
                else -> false
            }
        }

        // Если пользователь меняет сумму или счет - прячем кнопку "Оплатить", заставляем проверить снова
        val resetWatcher = object : TextWatcher {
            override fun afterTextChanged(s: Editable?) {
                if (btnGoToBank.visibility == View.VISIBLE) {
                    btnGoToBank.visibility = View.GONE
                    tvPayOwnerName.visibility = View.GONE
                    btnCheckPayment.visibility = View.VISIBLE
                }
            }
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
        }
        etPayAccount.addTextChangedListener(resetWatcher)
        etPayAmount.addTextChangedListener(resetWatcher)

        // Кнопка "Войти"
        btnLogin.setOnClickListener {
            val login = etLogin.text.toString().trim()
            val pass = etPassword.text.toString().trim()
            if (login.isNotEmpty() && pass.isNotEmpty()) {
                performLoginAndParse(login, pass, cbRemember.isChecked)
            } else {
                Toast.makeText(this, "Введите логин и пароль", Toast.LENGTH_SHORT).show()
            }
        }

        // Кнопка "Обновить статистику" (или загрузить другой отчет)
        btnRefreshStats.setOnClickListener {
            loadStatistics(spinnerReportType.selectedItemPosition)
        }

        // Кнопка "Проверить" перед оплатой
        btnCheckPayment.setOnClickListener {
            val amount = etPayAmount.text.toString().trim()
            val account = etPayAccount.text.toString().trim()
            if (amount.isNotEmpty() && account.isNotEmpty()) {
                checkPaymentData(account, amount)
            } else {
                Toast.makeText(this, "Введите номер счета и сумму", Toast.LENGTH_SHORT).show()
            }
        }

        // Кнопка "Оплатить картой" (переход в банк)
        btnGoToBank.setOnClickListener {
            if (paymentHtmlForm.isNotEmpty()) {
                webView.visibility = View.VISIBLE
                // Загружаем форму, которая автоматически отправится (onload=submit)
                webView.loadData(paymentHtmlForm, "text/html; charset=UTF-8", "UTF-8")
            }
        }

        // Кнопка Настроек (шестеренка)
        btnSettings.setOnClickListener {
            showSettingsDialog()
        }
    }

    // Восстанавливаем логин/пароль, если пользователь нажал "Запомнить меня"
    private fun restoreSession() {
        val prefs = getSharedPreferences("ChukotnetPrefs", Context.MODE_PRIVATE)
        if (prefs.getBoolean("IS_REMEMBERED", false)) {
            etLogin.setText(prefs.getString("LOGIN", ""))
            etPassword.setText(prefs.getString("PASS", ""))
            cbRemember.isChecked = true

            // Можно сразу попробовать войти автоматически
            // performLoginAndParse(...)
        }
    }

    // Применяем тему (темную/светлую) при запуске
    private fun applySavedTheme() {
        val prefs = getSharedPreferences("ChukotnetPrefs", Context.MODE_PRIVATE)
        val themeMode = prefs.getInt("THEME_MODE", 0) // 0 - авто
        val mode = when (themeMode) {
            1 -> AppCompatDelegate.MODE_NIGHT_NO // Светлая
            2 -> AppCompatDelegate.MODE_NIGHT_YES // Темная
            else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM // Как в системе
        }
        AppCompatDelegate.setDefaultNightMode(mode)
    }

    // === ВСПОМОГАТЕЛЬНЫЕ ФУНКЦИИ ДЛЯ ЗАГРУЗКИ ===

    // "Умная" функция для скачивания HTML.
    // Она игнорирует ошибку "Unexpected end of stream", которая часто бывает на старых серверах.
    private fun downloadHtmlSafe(urlString: String): String {
        val url = java.net.URL(urlString)
        val conn = url.openConnection() as java.net.HttpURLConnection
        conn.requestMethod = "GET"
        conn.connectTimeout = 15000
        conn.readTimeout = 15000
        conn.setRequestProperty("User-Agent", "Mozilla/5.0")
        conn.setRequestProperty("Connection", "close") // Важно: просим сервер закрывать соединение

        val sb = StringBuilder()
        try {
            // Используем кодировку windows-1251, так как сайт старый
            conn.inputStream.bufferedReader(Charset.forName("windows-1251")).use { reader ->
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    sb.append(line)
                }
            }
        } catch (e: java.io.IOException) {
            // Если данные пришли, но сервер оборвал связь в конце - не считаем это ошибкой
            if (sb.isEmpty()) throw e
        } finally {
            conn.disconnect()
        }
        return sb.toString()
    }

    // Функция, которая возвращает правильные CSS цвета
    // Если тема темная - вернет черный фон и белый текст. Если светлая - наоборот.
    private fun getCssColors(): Map<String, String> {
        val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        val isNight = nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES

        return if (isNight) {
            mapOf(
                "bg" to "#121212",       // Фон страницы
                "card" to "#1E1E1E",     // Фон карточек
                "text" to "#E0E0E0",     // Текст
                "border" to "#333333",   // Рамки таблиц
                "accent" to "#FFB74D",   // Оранжевый (светлый)
                "accent_bg" to "#3E2723",// Фон заголовков
                "row_even" to "#252525"  // Чередование строк
            )
        } else {
            mapOf(
                "bg" to "#fcfcfc",
                "card" to "#ffffff",
                "text" to "#333333",
                "border" to "#dddddd",
                "accent" to "#FF9800",   // Оранжевый (яркий)
                "accent_bg" to "#FF9800",
                "row_even" to "#f9f9f9"
            )
        }
    }

    private fun isNightMode(): Boolean {
        val nightModeFlags = resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK
        return nightModeFlags == android.content.res.Configuration.UI_MODE_NIGHT_YES
    }

    // === 1. ЛОГИКА НОВОСТЕЙ ===
    private fun loadNews() {
        progressNews.visibility = View.VISIBLE
        webViewNews.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val html = downloadHtmlSafe("http://www.chukotnet.ru/")
                val doc = Jsoup.parse(html)

                // Функция вырезает новости из "грязного" HTML
                fun extractNews(doc: org.jsoup.nodes.Document): String {
                    val sbContent = StringBuilder()
                    val dates = doc.select("span.date")

                    if (dates.isEmpty()) return "<p style='text-align:center;'>Новости не найдены</p>"

                    for (dateElement in dates) {
                        val dateText = dateElement.text()
                        var nextElement = dateElement.nextElementSibling()

                        // Ищем следующий блок .news, пропуская пустые <br>
                        while (nextElement != null && (nextElement.tagName() == "br" || nextElement.className() != "news")) {
                            if (nextElement.className() == "date") { nextElement = null; break }
                            nextElement = nextElement.nextElementSibling()
                        }

                        val newsBody = nextElement
                        if (newsBody != null) {
                            // Убираем старые шрифты (Arial), чтобы наши CSS работали
                            newsBody.select("*").removeAttr("style")
                            sbContent.append("<div class='news-card'><div class='news-date'>$dateText</div><div class='news-content'>${newsBody.html()}</div></div>")
                        }
                    }
                    return sbContent.toString()
                }

                val newsHtml = extractNews(doc)
                val colors = getCssColors()

                // Собираем красивую страницу
                val sb = StringBuilder()
                sb.append("<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'><style>")
                sb.append("body { font-family: sans-serif; background-color: ${colors["bg"]}; margin: 0; padding: 12px; color: ${colors["text"]}; }")
                sb.append("h1.page-title { color: ${colors["accent"]}; font-size: 22px; margin: 10px 0 20px; font-weight: bold; text-align: center; }")
                // Карточка новости
                sb.append(".news-card { background-color: ${colors["card"]}; border-radius: 12px; padding: 16px; margin-bottom: 16px; box-shadow: 0 2px 5px rgba(0,0,0,0.1); border-left: 5px solid ${colors["accent"]}; }")
                sb.append(".news-date { color: ${colors["accent"]}; font-weight: bold; font-size: 14px; margin-bottom: 10px; text-transform: uppercase; }")
                sb.append(".news-content { font-size: 15px; line-height: 1.5; }")
                sb.append(".news-content div { margin-bottom: 8px; }")
                sb.append("a { color: #0277BD; text-decoration: none; border-bottom: 1px dotted #0277BD; }")
                sb.append("</style></head><body>")
                sb.append("<h1 class='page-title'>Новости провайдера</h1>")
                sb.append(newsHtml)
                sb.append("</body></html>")

                withContext(Dispatchers.Main) {
                    progressNews.visibility = View.GONE
                    webViewNews.visibility = View.VISIBLE
                    webViewNews.loadDataWithBaseURL("http://www.chukotnet.ru/", sb.toString(), "text/html", "UTF-8", null)
                    isNewsLoaded = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressNews.visibility = View.GONE // Ошибка - просто прячем прогресс
                }
            }
        }
    }

    // === 2. ЛОГИКА ТАРИФОВ ===
    private fun loadTariffs() {
        progressTariffs.visibility = View.VISIBLE
        webViewTariffs.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val htmlInet = downloadHtmlSafe("http://www.chukotnet.ru/prices/internet.html")
                val htmlTv = downloadHtmlSafe("http://www.chukotnet.ru/prices/tv.html")
                val docInet = Jsoup.parse(htmlInet)
                val docTv = Jsoup.parse(htmlTv)

                fun extractContent(doc: org.jsoup.nodes.Document): String {
                    // Берем только полезные блоки (Таблицы, Подсказки, Заголовки)
                    val elements = doc.select("table.price, p.hint, p.information, h1, h4")
                    val sb = StringBuilder()
                    for (element in elements) {
                        if (element.text().trim().isEmpty()) continue
                        element.removeAttr("style") // Чистим старые стили
                        sb.append(element.outerHtml())
                    }
                    return sb.toString()
                }

                val inetClean = extractContent(docInet)
                val tvClean = extractContent(docTv)
                val colors = getCssColors()

                val sb = StringBuilder()
                sb.append("<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'><style>")
                sb.append("body { font-family: sans-serif; font-size: 14px; margin: 0; padding: 12px; background-color: ${colors["bg"]}; color: ${colors["text"]}; }")

                // Стили таблиц с поддержкой темной темы
                sb.append(".price { width: 100% !important; border-collapse: collapse; margin-bottom: 20px; font-size: 12px; background-color: ${colors["card"]}; }")
                sb.append(".price td, .price th { border: 1px solid ${colors["border"]}; padding: 8px; vertical-align: middle; }")

                // Шапка таблицы (белый текст днем, цветной ночью)
                if (isNightMode()) {
                    sb.append(".price th { background-color: ${colors["accent_bg"]}; color: ${colors["accent"]}; font-weight: bold; text-align: center; }")
                } else {
                    sb.append(".price th { background-color: ${colors["accent_bg"]}; color: white; font-weight: bold; text-align: center; }")
                }

                sb.append(".price tr:first-child td { background-color: ${colors["accent_bg"]}; font-weight: bold; text-align: center; color: white; }")
                sb.append(".price tr:nth-child(even) { background-color: ${colors["row_even"]}; }")

                sb.append(".hint { background-color: ${colors["card"]}; padding: 8px; border-left: 3px solid #ccc; margin-bottom: 10px; font-size: 12px; color: ${colors["text"]}; }")
                sb.append(".information { background-color: ${colors["card"]}; padding: 12px; border-radius: 4px; margin-bottom: 20px; font-size: 13px; border: 1px solid ${colors["border"]}; }")
                sb.append("h1 { color: ${colors["accent"]}; font-size: 18px; margin: 30px 0 15px; text-align: center; font-weight: bold; text-transform: uppercase; }")
                sb.append("h4 { color: ${colors["accent"]}; font-size: 16px; margin: 20px 0 10px; display: inline-block; }")
                sb.append("h2.main-title { background-color: ${colors["accent"]}; color: white; padding: 10px; text-align: center; margin-top: 0; border-radius: 4px; }")
                sb.append("</style></head><body>")

                if (inetClean.isNotEmpty()) {
                    sb.append("<h2 class='main-title'>ИНТЕРНЕТ</h2>")
                    sb.append(inetClean)
                }
                sb.append("<div style='height: 40px;'></div>")
                if (tvClean.isNotEmpty()) {
                    sb.append("<h2 class='main-title'>ТЕЛЕВИДЕНИЕ</h2>")
                    sb.append(tvClean)
                }
                sb.append("</body></html>")

                withContext(Dispatchers.Main) {
                    progressTariffs.visibility = View.GONE
                    webViewTariffs.visibility = View.VISIBLE
                    webViewTariffs.loadDataWithBaseURL("http://www.chukotnet.ru", sb.toString(), "text/html", "UTF-8", null)
                    isTariffsLoaded = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressTariffs.visibility = View.GONE
                }
            }
        }
    }

    // === 3. ЛОГИКА СТАТИСТИКИ (3 ТИПА ОТЧЕТОВ) ===
    private fun loadStatistics(reportType: Int) {
        if (loginCookies.isEmpty()) {
            Toast.makeText(this, "Сначала войдите в кабинет!", Toast.LENGTH_LONG).show()
            bottomNav.selectedItemId = R.id.nav_home // Перекидываем на вкладку входа
            return
        }

        progressStats.visibility = View.VISIBLE
        webViewStats.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Вычисляем даты: сегодня и 3 месяца назад (чтобы точно были данные)
                val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                val calendar = Calendar.getInstance()
                val dateTo = sdf.format(calendar.time)
                calendar.add(Calendar.MONTH, -3) // Берем за квартал
                val dateFrom = sdf.format(calendar.time)

                val statsUrl: String
                val connection: Connection
                var title = ""

                // Выбираем тип отчета
                if (reportType == 0) {
                    title = "Трафик"
                    statsUrl = "http://user.chukotnet.ru/main.php?parm=10"
                    connection = Jsoup.connect(statsUrl)
                        .data("parm", "10").data("interval", "2").data("view", "0").data("currency", "1").data("unit", "3").data("byzones", "0")
                } else if (reportType == 1) {
                    title = "История платежей"
                    statsUrl = "http://user.chukotnet.ru/main.php?parm=8"
                    connection = Jsoup.connect(statsUrl).data("parm", "8")
                } else {
                    title = "Абонентская плата"
                    statsUrl = "http://user.chukotnet.ru/main.php?parm=11"
                    connection = Jsoup.connect(statsUrl).data("parm", "11")
                }

                // Общие параметры
                connection.cookies(loginCookies).userAgent("Mozilla/5.0")
                    .data("period", "1").data("pg", "1").data("show_params", "1").data("cardid", "")
                    .data("date_from", dateFrom).data("date_to", dateTo)

                val response = connection.method(Connection.Method.POST).execute()
                val doc = response.parse()

                // Ищем таблицу (внутри table-responsive или просто большую таблицу)
                var statsTable: org.jsoup.nodes.Element? = null
                val container = doc.select("div.table-responsive").first()
                if (container != null) statsTable = container.select("table").first()
                if (statsTable == null) statsTable = doc.select("table:contains(Дата), table:contains(Сумма)").first()

                // === ЧИСТКА ТАБЛИЦЫ ===
                if (statsTable != null) {
                    // Удаляем жесткие размеры, чтобы влезло в экран
                    statsTable.removeAttr("width").removeAttr("style").removeAttr("border")
                    statsTable.select("td, th").removeAttr("style").removeAttr("width").removeAttr("height")
                    // Удаляем мусор (кнопки глаз, скрытые строки)
                    statsTable.select("button").remove()
                    statsTable.select("tr.info").remove()
                }

                val colors = getCssColors()
                val sb = StringBuilder()
                sb.append("<html><head><meta name='viewport' content='width=device-width, initial-scale=1.0'><style>")
                sb.append("body { font-family: sans-serif; font-size: 12px; padding: 8px; margin: 0; background-color: ${colors["bg"]}; color: ${colors["text"]}; }")
                sb.append("h3 { color: ${colors["accent"]}; text-align: center; margin-bottom: 15px; font-size: 16px; }")

                sb.append("table { width: 100% !important; border-collapse: collapse; font-size: 10px; background-color: ${colors["card"]}; box-shadow: 0 1px 3px rgba(0,0,0,0.1); }")
                sb.append("th, td { border: 1px solid ${colors["border"]}; padding: 6px 3px; text-align: center; vertical-align: middle; }")

                if (isNightMode()) {
                    sb.append("th { background-color: ${colors["accent_bg"]}; color: ${colors["accent"]}; font-weight: bold; }")
                } else {
                    sb.append("th { background-color: ${colors["accent_bg"]}; color: white; font-weight: bold; }")
                }

                sb.append("tr:nth-child(even) { background-color: ${colors["row_even"]}; }")
                // Итоговая строка жирная
                sb.append("tr:last-child { font-weight: bold; background-color: ${colors["accent_bg"]}; color: white; }")

                sb.append("a { color: ${colors["text"]}; text-decoration: none; pointer-events: none; }")
                sb.append("</style></head><body>")

                sb.append("<h3>$title: $dateFrom - $dateTo</h3>")

                if (statsTable != null && statsTable.select("tr").size > 1) {
                    sb.append(statsTable.outerHtml())
                } else {
                    sb.append("<p style='text-align:center; margin-top:50px; color:#777;'>Нет данных за этот период.</p>")
                }

                sb.append("</body></html>")

                withContext(Dispatchers.Main) {
                    progressStats.visibility = View.GONE
                    webViewStats.visibility = View.VISIBLE
                    webViewStats.loadDataWithBaseURL("http://user.chukotnet.ru", sb.toString(), "text/html", "UTF-8", null)
                    isStatsLoaded = true
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressStats.visibility = View.GONE
                    Toast.makeText(this@MainActivity, "Ошибка: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    // === 4. ЛОГИКА АВТОРИЗАЦИИ И БАЛАНСА ===
    private fun performLoginAndParse(login: String, pass: String, isRemember: Boolean) {
        progressBar.visibility = View.VISIBLE
        btnLogin.isEnabled = false
        tvResult.text = "Вход..."

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val loginUrl = "http://user.chukotnet.ru/main.php"
                val cabinetUrl = "http://user.chukotnet.ru/main.php?parm=1"
                val formLoginName = "UserName"
                val formPassName = "PWDD"
                val userAgent = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"

                // 1. Получаем куки
                val initialResponse = Jsoup.connect(loginUrl).userAgent(userAgent).execute()
                loginCookies.putAll(initialResponse.cookies())
                val loginDoc = initialResponse.parse()

                // 2. Отправляем логин/пароль
                val formData = HashMap<String, String>()
                val inputs = loginDoc.select("form input[type=hidden]")
                for (input in inputs) formData[input.attr("name")] = input.attr("value")
                formData[formLoginName] = login
                formData[formPassName] = pass

                val loginAction = Jsoup.connect(loginUrl)
                    .userAgent(userAgent).cookies(loginCookies).data(formData).method(Connection.Method.POST).execute()
                loginCookies.putAll(loginAction.cookies()) // Обновляем куки авторизации

                // 3. Заходим в кабинет
                val cabinetDoc = Jsoup.connect(cabinetUrl).cookies(loginCookies).userAgent(userAgent).get()

                val sb = StringBuilder()
                var foundAccount = ""

                if (cabinetDoc.select("input[name=$formPassName]").isEmpty()) {
                    // Ищем таблицу с лицевым счетом
                    val table = cabinetDoc.select("div.table-responsive table:contains(Лицевые счета)").first()
                        ?: cabinetDoc.select("table:contains(Лицевые счета)").first()

                    if (table != null) {
                        val rows = table.select("tbody tr")
                        for (row in rows) {
                            val cells = row.select("td")
                            if (cells.size >= 3) {
                                val acc = cells[1].text()
                                val balString = cells[2].text()

                                // Логика цвета баланса
                                val balValue = balString.replace(Regex("[^0-9,-]"), "").replace(",", ".").toDoubleOrNull() ?: 0.0
                                val color = when {
                                    balValue < 0 -> "#D32F2F"      // Красный (долг)
                                    balValue < 450 -> "#F57C00"    // Оранжевый (мало)
                                    else -> "#388E3C"              // Зеленый (ок)
                                }

                                sb.append("Договор: <b>$acc</b><br>")
                                sb.append("Баланс: <font color='$color'><b>$balString руб.</b></font><br><br>")

                                if (foundAccount.isEmpty()) foundAccount = acc
                            }
                        }
                    } else {
                        sb.append("Таблица не найдена.")
                    }
                } else {
                    sb.append("Ошибка входа. Проверьте логин/пароль.")
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true
                    tvResult.text = Html.fromHtml(sb.toString(), Html.FROM_HTML_MODE_COMPACT)

                    if (foundAccount.isNotEmpty()) {
                        etPayAccount.setText(foundAccount)
                        val prefs = getSharedPreferences("ChukotnetPrefs", Context.MODE_PRIVATE)
                        val edit = prefs.edit()
                        if (isRemember) {
                            edit.putString("LOGIN", login)
                            edit.putString("PASS", pass)
                            edit.putBoolean("IS_REMEMBERED", true)
                        } else {
                            edit.clear()
                        }
                        edit.apply()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnLogin.isEnabled = true
                    tvResult.text = "Ошибка: ${e.message}"
                }
            }
        }
    }

    // === 5. ЛОГИКА ОПЛАТЫ ===
    private fun checkPaymentData(account: String, amount: String) {
        progressBar.visibility = View.VISIBLE
        tvPayOwnerName.visibility = View.GONE
        btnGoToBank.visibility = View.GONE
        btnCheckPayment.visibility = View.GONE

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // Отправляем данные на сайт, чтобы он сформировал ссылку на банк
                val url = java.net.URL("http://www.chukotnet.ru/payment/add.html")
                val postData = "account=$account&money=$amount"
                val postDataBytes = postData.toByteArray(Charset.forName("UTF-8"))

                val conn = url.openConnection() as java.net.HttpURLConnection
                conn.requestMethod = "POST"
                conn.doOutput = true
                conn.setRequestProperty("Content-Type", "application/x-www-form-urlencoded")
                conn.setRequestProperty("Content-Length", postDataBytes.size.toString())
                conn.setRequestProperty("User-Agent", "Mozilla/5.0")
                conn.setRequestProperty("Connection", "close")

                conn.outputStream.use { it.write(postDataBytes) }

                val sbResponse = StringBuilder()
                conn.inputStream.bufferedReader(Charset.forName("UTF-8")).use { reader ->
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        sbResponse.append(line)
                    }
                }
                conn.disconnect()

                val doc = Jsoup.parse(sbResponse.toString())
                val bankForm = doc.select("form[action^=http]").first()
                    ?: doc.select("form").first()

                // Ищем ФИО абонента, чтобы пользователь проверил
                var ownerInfo = "Абонент найден"
                val elements = doc.select("td, div, p, b")
                for (el in elements) {
                    if (el.text().contains("ФИО") || el.text().contains("Абонент")) {
                        ownerInfo = el.text()
                        break
                    }
                }

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE

                    if (bankForm != null) {
                        // Создаем скрытую форму для WebView, которая сама отправится в банк
                        val actionUrl = bankForm.attr("abs:action")
                        val method = bankForm.attr("method").ifEmpty { "POST" }
                        val sbForm = StringBuilder()
                        sbForm.append("<html><body onload='document.forms[0].submit()'>")
                        sbForm.append("<h3 style='text-align:center; margin-top:50px;'>Переход в банк...</h3>")
                        sbForm.append("<form method='$method' action='$actionUrl'>")
                        val inputs = bankForm.select("input")
                        for (input in inputs) {
                            if (input.attr("type") != "submit" && input.attr("type") != "button") {
                                val name = input.attr("name")
                                val value = input.attr("value")
                                sbForm.append("<input type='hidden' name='$name' value='$value'>")
                            }
                        }
                        sbForm.append("</form></body></html>")
                        paymentHtmlForm = sbForm.toString()

                        tvPayOwnerName.text = ownerInfo
                        tvPayOwnerName.visibility = View.VISIBLE

                        btnCheckPayment.visibility = View.GONE
                        btnGoToBank.visibility = View.VISIBLE
                        btnGoToBank.text = "Оплатить $amount руб."
                    } else {
                        btnCheckPayment.visibility = View.VISIBLE
                        Toast.makeText(this@MainActivity, "Ошибка: форма оплаты не найдена", Toast.LENGTH_LONG).show()
                    }
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    btnCheckPayment.visibility = View.VISIBLE
                    Toast.makeText(this@MainActivity, "Ошибка соединения: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    // === 6. ДИАЛОГ НАСТРОЕК (ТЕМА + БУДИЛЬНИК) ===
    private fun showSettingsDialog() {
        val prefs = getSharedPreferences("ChukotnetPrefs", Context.MODE_PRIVATE)

        val savedDay = prefs.getInt("REMINDER_DAY", 25)
        val isRemindEnabled = prefs.getBoolean("REMINDER_ENABLED", false)
        val currentThemeMode = prefs.getInt("THEME_MODE", 0)

        val builder = AlertDialog.Builder(this)
        builder.setTitle("Настройки")

        val layout = LinearLayout(this)
        layout.orientation = LinearLayout.VERTICAL
        layout.setPadding(50, 40, 50, 10)

        // Тема
        val txtTheme = TextView(this)
        txtTheme.text = "Тема оформления:"
        txtTheme.textSize = 16f
        txtTheme.setTextColor(ContextCompat.getColor(this, R.color.orange_primary))
        layout.addView(txtTheme)

        val themeGroup = RadioGroup(this)
        val rbAuto = RadioButton(this).apply { text = "Системная (Авто)"; id = 0 }
        val rbLight = RadioButton(this).apply { text = "Светлая"; id = 1 }
        val rbDark = RadioButton(this).apply { text = "Темная"; id = 2 }

        themeGroup.addView(rbAuto)
        themeGroup.addView(rbLight)
        themeGroup.addView(rbDark)

        when (currentThemeMode) {
            1 -> rbLight.isChecked = true
            2 -> rbDark.isChecked = true
            else -> rbAuto.isChecked = true
        }
        layout.addView(themeGroup)

        // Разделитель
        val divider = View(this)
        divider.layoutParams = LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, 2)
        divider.setBackgroundColor(0xFFEEEEEE.toInt())
        divider.setPadding(0, 20, 0, 20)
        layout.addView(divider)

        // Напоминания
        val txtRemind = TextView(this)
        txtRemind.text = "\nНапоминание об оплате:"
        txtRemind.textSize = 16f
        txtRemind.setTextColor(ContextCompat.getColor(this, R.color.orange_primary))
        layout.addView(txtRemind)

        val switchEnable = Switch(this)
        switchEnable.text = "Включить уведомление"
        switchEnable.isChecked = isRemindEnabled
        layout.addView(switchEnable)

        val txtLabel = TextView(this)
        txtLabel.text = "День месяца (1-31):"
        layout.addView(txtLabel)

        val inputDay = EditText(this)
        inputDay.inputType = InputType.TYPE_CLASS_NUMBER
        inputDay.setText(savedDay.toString())
        inputDay.isEnabled = isRemindEnabled
        layout.addView(inputDay)

        switchEnable.setOnCheckedChangeListener { _, checked -> inputDay.isEnabled = checked }

        builder.setView(layout)

        builder.setPositiveButton("Сохранить") { _, _ ->
            val dayStr = inputDay.text.toString()
            val day = dayStr.toIntOrNull() ?: 25
            val validDay = if (day < 1) 1 else if (day > 31) 31 else day

            // Сохраняем напоминания
            prefs.edit()
                .putInt("REMINDER_DAY", validDay)
                .putBoolean("REMINDER_ENABLED", switchEnable.isChecked)
                .apply()

            scheduleNotification(switchEnable.isChecked, validDay)

            // Применяем тему
            val selectedId = themeGroup.checkedRadioButtonId
            val newMode = when (selectedId) {
                1 -> AppCompatDelegate.MODE_NIGHT_NO
                2 -> AppCompatDelegate.MODE_NIGHT_YES
                else -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
            }
            prefs.edit().putInt("THEME_MODE", selectedId).apply()
            AppCompatDelegate.setDefaultNightMode(newMode)
        }

        builder.setNegativeButton("Отмена", null)
        builder.show()
    }

    // Установка будильника с умной проверкой даты
    private fun scheduleNotification(isEnabled: Boolean, userSelectedDay: Int) {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, ReminderReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        alarmManager.cancel(pendingIntent)

        if (isEnabled) {
            val now = Calendar.getInstance()
            val target = Calendar.getInstance()

            target.set(Calendar.HOUR_OF_DAY, 10)
            target.set(Calendar.MINUTE, 0)
            target.set(Calendar.SECOND, 0)

            // Умная проверка: если в феврале нет 30-го числа, ставим на 28-е
            var maxDay = target.getActualMaximum(Calendar.DAY_OF_MONTH)
            var dayToSet = if (userSelectedDay > maxDay) maxDay else userSelectedDay
            target.set(Calendar.DAY_OF_MONTH, dayToSet)

            if (target.before(now)) {
                target.add(Calendar.MONTH, 1)
                maxDay = target.getActualMaximum(Calendar.DAY_OF_MONTH)
                dayToSet = if (userSelectedDay > maxDay) maxDay else userSelectedDay
                target.set(Calendar.DAY_OF_MONTH, dayToSet)
            }

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                } else {
                    alarmManager.setExact(AlarmManager.RTC_WAKEUP, target.timeInMillis, pendingIntent)
                }
            } catch (e: SecurityException) {
                Toast.makeText(this, "Нет прав на будильник", Toast.LENGTH_SHORT).show()
            }
        }
    }
}
