package id.astra.wifibooster

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.net.wifi.ScanResult
import android.net.wifi.WifiManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.net.HttpURLConnection
import java.net.URL
import java.util.Locale
import java.util.concurrent.Executors
import kotlin.math.abs

class MainActivity : Activity() {
    private val bg = Color.rgb(7, 17, 31)
    private val cardColor = Color.rgb(15, 31, 51)
    private val blue = Color.rgb(73, 166, 255)
    private val white = Color.rgb(237, 244, 252)
    private val muted = Color.rgb(157, 177, 199)
    private val green = Color.rgb(75, 210, 155)
    private val orange = Color.rgb(255, 184, 92)

    private lateinit var wifi: WifiManager
    private lateinit var connectivity: ConnectivityManager
    private lateinit var root: LinearLayout
    private lateinit var statusView: TextView
    private lateinit var detailView: TextView
    private lateinit var rssiView: TextView
    private lateinit var bandView: TextView
    private lateinit var speedView: TextView
    private lateinit var gatewayView: TextView
    private lateinit var adviceView: TextView
    private lateinit var scanView: TextView
    private lateinit var testView: TextView
    private lateinit var scanButton: Button

    private val executor = Executors.newSingleThreadExecutor()
    private val handler = Handler(Looper.getMainLooper())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        wifi = applicationContext.getSystemService(Context.WIFI_SERVICE) as WifiManager
        connectivity = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        buildScreen()
        refreshNetwork()
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    private fun buildScreen() {
        val scroll = ScrollView(this).apply {
            setBackgroundColor(bg)
            isFillViewport = true
        }
        root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(22), dp(20), dp(28))
        }
        scroll.addView(root)
        setContentView(scroll)

        addText("ASTRA NETWORK TOOLS", 12f, blue, true, 4, 5)
        addText("WiFi Signal Booster", 27f, white, true, 0, 4)
        addText("Diagnostik dan optimasi koneksi Android", 14f, muted, false, 0, 18)

        val statusCard = card()
        statusView = makeText("Memeriksa koneksi…", 19f, white, true)
        detailView = makeText("Status jaringan akan tampil di sini.", 13f, muted)
        statusCard.addView(statusView)
        statusCard.addView(detailView, topMargin(8))
        root.addView(statusCard, spaced())

        section("KONDISI WI-FI")
        val metrics = LinearLayout(this).apply { orientation = LinearLayout.HORIZONTAL }
        val rssiMetric = metric("SIGNAL RSSI", "—", "dBm")
        val bandMetric = metric("FREKUENSI", "—", "band")
        rssiView = rssiMetric.second
        bandView = bandMetric.second
        metrics.addView(rssiMetric.first, weighted())
        metrics.addView(spacer(dp(10)))
        metrics.addView(bandMetric.first, weighted())
        root.addView(metrics, spaced())

        val infoCard = card()
        speedView = makeText("Kecepatan link: —", 14f, white, true)
        gatewayView = makeText("Gateway router: —", 13f, muted)
        infoCard.addView(speedView)
        infoCard.addView(gatewayView, topMargin(8))
        root.addView(infoCard, spaced())

        section("REKOMENDASI")
        val recommendationCard = card()
        adviceView = makeText("Jalankan diagnosa untuk mendapatkan saran.", 14f, white)
        recommendationCard.addView(adviceView)
        root.addView(recommendationCard, spaced())

        section("ALAT OPTIMASI")
        root.addView(actionButton("Diagnosa koneksi", blue) { refreshNetwork() }, spaced())
        scanButton = actionButton("Pindai kanal Wi-Fi", cardColor) { requestScanPermission() }
        root.addView(scanButton, spaced())
        root.addView(actionButton("Uji latensi & kestabilan", cardColor) { runConnectivityTest() }, spaced())
        root.addView(actionButton("Buka pengaturan Wi-Fi", cardColor) { openWifiSettings() }, spaced())
        root.addView(actionButton("Buka pengaturan Private DNS", cardColor) { openPrivateDnsSettings() }, spaced())
        root.addView(actionButton("Buka halaman router", cardColor) { openRouterPage() }, spaced())
        root.addView(actionButton("Panduan optimasi router", cardColor) { showRouterGuide() }, spaced())

        section("HASIL ANALISIS KANAL")
        val scanCard = card()
        scanView = makeText("Belum ada hasil pemindaian. Tekan tombol pindai dan berikan izin yang diminta Android.", 13f, muted)
        scanCard.addView(scanView)
        root.addView(scanCard, spaced())

        section("UJI KONEKSI INTERNET")
        val testCard = card()
        testView = makeText("Belum diuji. Tes memakai HTTPS dan bukan ICMP ping murni.", 13f, muted)
        testCard.addView(testView)
        root.addView(testCard, spaced())

        addText("Batasan: aplikasi tidak dapat menambah daya antena HP atau memaksa sinyal menjadi penuh. Perubahan kanal, QoS, dan band router harus diterapkan oleh pemilik router.", 12f, muted, false, 16, 12)
        addText("WiFi Signal Booster • Android 8.0+", 11f, muted, false, 0, 4)
    }

    private fun refreshNetwork() {
        try {
            val network = connectivity.activeNetwork
            val caps = network?.let { connectivity.getNetworkCapabilities(it) }
            val wifiConnected = caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true
            val hasInternet = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
            val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
            val transport = when {
                wifiConnected -> "Wi-Fi"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "Data seluler"
                caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "Ethernet"
                else -> "Tidak terhubung"
            }

            statusView.text = when {
                validated -> transport + " • Internet terverifikasi"
                hasInternet -> transport + " • Internet belum terverifikasi"
                else -> "Internet belum terdeteksi"
            }
            statusView.setTextColor(if (validated) green else orange)
            detailView.text = if (validated) "Android melaporkan akses internet tersedia." else "Periksa router, captive portal, paket data, atau layanan ISP."

            if (!wifiConnected) {
                rssiView.text = "—"
                bandView.text = "—"
                speedView.text = "Kecepatan link: tidak tersedia (bukan Wi-Fi)"
                gatewayView.text = "Gateway router: —"
                adviceView.text = if (validated) {
                    "Internet aktif melalui " + transport + ". Sambungkan ke Wi-Fi untuk menganalisis RSSI dan kanal."
                } else {
                    "Hubungkan ke Wi-Fi atau data seluler, lalu jalankan diagnosa kembali."
                }
                return
            }

            @Suppress("DEPRECATION")
            val info = wifi.connectionInfo
            if (info == null || info.networkId == -1) {
                rssiView.text = "—"
                bandView.text = "—"
                speedView.text = "Kecepatan link: detail tidak tersedia"
                gatewayView.text = "Gateway router: —"
                adviceView.text = "Android tidak memberikan detail koneksi pada perangkat ini."
                return
            }

            val signal = info.rssi
            val frequency = info.frequency
            val band = when (frequency) {
                in 2400..2500 -> "2.4 GHz"
                in 4900..5900 -> "5 GHz"
                in 5925..7125 -> "6 GHz"
                else -> "Tidak diketahui"
            }
            rssiView.text = if (signal in -127..0) signal.toString() else "—"
            bandView.text = band
            speedView.text = "Kecepatan link: " + info.linkSpeed.coerceAtLeast(0) + " Mbps"
            @Suppress("DEPRECATION")
            val gateway = gatewayAddress(wifi.dhcpInfo.gateway)
            gatewayView.text = "Gateway router: " + gateway
            adviceView.text = signalAdvice(signal, frequency, validated)
        } catch (_: SecurityException) {
            rssiView.text = "Izin"
            bandView.text = "Izin"
            speedView.text = "Kecepatan link: dibatasi Android"
            gatewayView.text = "Gateway router: tidak tersedia"
            adviceView.text = "Detail Wi-Fi dibatasi Android. Pastikan izin yang diperlukan sudah diberikan."
        } catch (error: Exception) {
            adviceView.text = "Informasi jaringan tidak tersedia: " + (error.message ?: "kesalahan tidak diketahui")
        }
    }

    private fun signalAdvice(signal: Int, frequency: Int, internetOk: Boolean): String {
        val quality = when {
            signal >= -55 -> "Sangat kuat"
            signal >= -67 -> "Baik"
            signal >= -72 -> "Cukup"
            signal >= -80 -> "Lemah"
            else -> "Sangat lemah"
        }
        val tip = when {
            signal >= -67 -> "Jika internet tetap lambat, periksa kepadatan kanal, router, atau ISP."
            signal >= -72 -> "Coba mendekat ke router dan kurangi penghalang seperti dinding tebal."
            signal >= -80 -> "Pindah lebih dekat ke router. Band 2.4 GHz biasanya menjangkau lebih jauh daripada 5 GHz."
            else -> "Sinyal sangat lemah. Dekatkan HP ke router atau pertimbangkan access point/repeater yang dipasang dengan benar."
        }
        val bandTip = if (frequency in 4900..5900 && signal < -67) " Band 5 GHz biasanya lebih cepat tetapi jangkauannya lebih pendek; coba 2.4 GHz jika tersedia." else ""
        val internetTip = if (!internetOk) " Android belum memverifikasi akses internet; periksa modem/router atau ISP." else ""
        return "Kualitas sinyal: " + quality + " (" + signal + " dBm). " + tip + bandTip + internetTip
    }

    private fun requestScanPermission() {
        val needed = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            needed.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (Build.VERSION.SDK_INT >= 33 &&
            checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.NEARBY_WIFI_DEVICES)
        }
        if (needed.isEmpty()) {
            scanWifi()
        } else {
            AlertDialog.Builder(this)
                .setTitle("Izin analisis Wi-Fi")
                .setMessage("Android memerlukan izin lokasi dan/atau perangkat sekitar untuk menampilkan hasil pemindaian Wi-Fi. Izin ini digunakan saat kamu meminta pemindaian.")
                .setNegativeButton("Batal", null)
                .setPositiveButton("Lanjutkan") { _, _ -> requestPermissions(needed.toTypedArray(), REQUEST_WIFI_PERMISSIONS) }
                .show()
        }
    }

    @Deprecated("Deprecated by Android; retained for compatibility.")
    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_WIFI_PERMISSIONS) {
            if (grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }) {
                scanWifi()
            } else {
                scanView.text = "Izin tidak diberikan. Tes koneksi internet tetap tersedia."
            }
        }
    }

    @Suppress("DEPRECATION")
    private fun scanWifi() {
        scanButton.isEnabled = false
        scanButton.text = "Memindai…"
        scanView.text = "Meminta pemindaian kepada Android…"
        try {
            if (!wifi.startScan()) {
                scanView.text = "Android membatasi pemindaian saat ini. Coba lagi nanti."
                scanButton.isEnabled = true
                scanButton.text = "Pindai kanal Wi-Fi"
                return
            }
        } catch (error: Exception) {
            scanView.text = "Pemindaian tidak tersedia: " + (error.message ?: "dibatasi perangkat")
            scanButton.isEnabled = true
            scanButton.text = "Pindai kanal Wi-Fi"
            return
        }

        handler.postDelayed({
            try {
                scanView.text = analyzeChannels(wifi.scanResults.orEmpty())
            } catch (_: SecurityException) {
                scanView.text = "Android menolak hasil pemindaian. Periksa izin dan layanan lokasi perangkat."
            } catch (error: Exception) {
                scanView.text = "Gagal membaca hasil pemindaian: " + (error.message ?: "kesalahan tidak diketahui")
            } finally {
                scanButton.isEnabled = true
                scanButton.text = "Pindai kanal Wi-Fi"
            }
        }, 3500L)
    }

    private fun analyzeChannels(results: List<ScanResult>): String {
        if (results.isEmpty()) return "Tidak ada hasil. Pastikan Wi-Fi dan layanan lokasi aktif. Android dapat membatasi atau menyimpan hasil scan sebelumnya."
        val known = results.mapNotNull { result ->
            channelForFrequency(result.frequency)?.let { Pair(it, result) }
        }
        val twoFour = known.filter { it.second.frequency in 2400..2500 }
        val five = known.filter { it.second.frequency in 4900..5900 }
        val six = known.filter { it.second.frequency in 5925..7125 }
        val uniqueNames = results.map { it.SSID }.filter { it.isNotBlank() && it != "<unknown ssid>" }.distinct().size
        val output = mutableListOf("Terdeteksi " + results.size + " access point dan " + uniqueNames + " nama jaringan unik.")

        if (twoFour.isNotEmpty()) {
            val counts = twoFour.groupingBy { it.first }.eachCount().toSortedMap()
            val candidates = listOf(1, 6, 11)
            val best = candidates.minByOrNull { candidate ->
                twoFour.sumOf { pair ->
                    val overlap = maxOf(0, 5 - abs(pair.first - candidate))
                    val signalWeight = (pair.second.level + 100).coerceAtLeast(1) / 10
                    overlap * signalWeight
                }
            } ?: 1
            val seen = counts.entries.joinToString { entry -> entry.key.toString() + " (" + entry.value + ")" }
            output.add("2.4 GHz: " + twoFour.size + " access point. Kanal teramati: " + seen + ".")
            output.add("Kandidat kanal 1/6/11 dengan kepadatan teramati paling rendah: " + best + ". Ini estimasi scan HP, bukan jaminan; ubah kanal manual di router.")
        } else {
            output.add("2.4 GHz: tidak ada access point yang terbaca.")
        }
        output.add("5 GHz: " + five.size + " access point terdeteksi.")
        output.add("6 GHz: " + six.size + " access point terdeteksi.")
        output.add("Catatan: Android dapat membatasi pemindaian. Tidak semua gangguan radio dapat dideteksi oleh pemindaian HP.")
        return output.joinToString("\n\n")
    }

    private fun channelForFrequency(frequency: Int): Int? = when (frequency) {
        2484 -> 14
        in 2412..2472 -> (frequency - 2407) / 5
        in 5000..5900 -> (frequency - 5000) / 5
        in 5955..7115 -> (frequency - 5950) / 5
        else -> null
    }

    private fun runConnectivityTest() {
        testView.text = "Menguji koneksi HTTPS (4 percobaan)…"
        executor.execute {
            val times = mutableListOf<Long>()
            var failures = 0
            repeat(4) {
                var connection: HttpURLConnection? = null
                try {
                    val start = SystemClock.elapsedRealtime()
                    connection = URL("https://connectivitycheck.gstatic.com/generate_204").openConnection() as HttpURLConnection
                    connection.requestMethod = "GET"
                    connection.connectTimeout = 3500
                    connection.readTimeout = 3500
                    connection.instanceFollowRedirects = false
                    connection.useCaches = false
                    if (connection.responseCode == 204) {
                        times.add(SystemClock.elapsedRealtime() - start)
                    } else {
                        failures++
                    }
                } catch (_: Exception) {
                    failures++
                } finally {
                    connection?.disconnect()
                }
            }
            val result = if (times.isEmpty()) {
                "Semua percobaan gagal. Periksa koneksi, captive portal, DNS, atau blokir jaringan."
            } else {
                val average = times.average()
                val variation = if (times.size > 1) times.zipWithNext { a, b -> abs(a - b).toDouble() }.average() else 0.0
                val failurePercent = failures * 25
                "Respons HTTPS rata-rata: " + "%.0f".format(Locale.US, average) + " ms\n" +
                    "Variasi antarpercobaan: " + "%.0f".format(Locale.US, variation) + " ms\n" +
                    "Percobaan gagal: " + failures + "/4 (" + failurePercent + "%).\n\n" +
                    "Ini bukan ICMP ping murni. DNS, TLS, server tujuan, dan rute internet memengaruhi hasil."
            }
            handler.post { testView.text = result }
        }
    }

    private fun openWifiSettings() {
        try {
            startActivity(Intent(Settings.ACTION_WIFI_SETTINGS))
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun openPrivateDnsSettings() {
        if (Build.VERSION.SDK_INT >= 28) {
            try {
                startActivity(Intent(Settings.ACTION_PRIVATE_DNS_SETTINGS))
                return
            } catch (_: Exception) {
                // Fall through to general settings.
            }
        }
        startActivity(Intent(Settings.ACTION_SETTINGS))
    }

    private fun showRouterGuide() {
        val message = "2.4 GHz\n• Pada router yang mendukungnya, coba kanal 1, 6, atau 11 dan lebar kanal 20 MHz. Gunakan hasil scan sebagai petunjuk.\n\n" +
            "5 GHz / 6 GHz\n• Gunakan band ini untuk perangkat yang dekat dengan router dan mendukungnya. Pilih kanal yang diizinkan di wilayahmu.\n\n" +
            "Kualitas koneksi\n• Letakkan router di tempat terbuka dan agak tinggi.\n" +
            "• Perbarui firmware dari produsen router.\n" +
            "• Aktifkan QoS hanya jika router mendukungnya dan atur prioritas perangkat dengan benar.\n" +
            "• Hindari repeater yang menerima sinyal sangat lemah dari router utama.\n\n" +
            "Perubahan harus dilakukan oleh pemilik router. Catat pengaturan awal agar dapat dikembalikan."
        AlertDialog.Builder(this).setTitle("Panduan optimasi router").setMessage(message)
            .setPositiveButton("Mengerti", null).show()
    }

    @Suppress("DEPRECATION")
    private fun openRouterPage() {
        val gateway = try { gatewayAddress(wifi.dhcpInfo.gateway) } catch (_: Exception) { "" }
        if (gateway.isBlank() || gateway == "0.0.0.0") {
            AlertDialog.Builder(this).setMessage("Alamat router belum tersedia. Sambungkan HP ke Wi-Fi terlebih dahulu.")
                .setPositiveButton("OK", null).show()
            return
        }
        AlertDialog.Builder(this).setTitle("Buka pengaturan router?")
            .setMessage("Halaman router akan dibuka di browser. Gunakan hanya jaringan yang kamu miliki atau yang kamu punya izin untuk kelola.")
            .setNegativeButton("Batal", null)
            .setPositiveButton("Buka") { _, _ ->
                try {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("http://" + gateway)))
                } catch (_: Exception) {
                    // No compatible browser.
                }
            }.show()
    }

    @Suppress("DEPRECATION")
    private fun gatewayAddress(value: Int): String =
        listOf(0, 8, 16, 24).joinToString(".") { shift -> ((value shr shift) and 0xff).toString() }

    private fun section(label: String) = addText(label, 11f, blue, true, 20, 9)

    private fun addText(value: String, size: Float, color: Int, bold: Boolean, top: Int = 0, bottom: Int = 0) {
        root.addView(makeText(value, size, color, bold), LinearLayout.LayoutParams(-1, -2).apply {
            topMargin = dp(top)
            bottomMargin = dp(bottom)
        })
    }

    private fun makeText(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, Typeface.BOLD)
            gravity = Gravity.START
            setLineSpacing(dp(2).toFloat(), 1f)
        }

    private fun card() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(16), dp(16), dp(16), dp(16))
        background = rounded(cardColor, 18)
    }

    private fun metric(label: String, initial: String, unit: String): Pair<LinearLayout, TextView> {
        val box = card()
        box.addView(makeText(label, 10f, muted, true))
        val value = makeText(initial, 25f, white, true)
        box.addView(value, topMargin(8))
        box.addView(makeText(unit, 11f, muted), topMargin(1))
        return Pair(box, value)
    }

    private fun actionButton(label: String, color: Int, action: () -> Unit) =
        Button(this).apply {
            text = label
            isAllCaps = false
            textSize = 14f
            setTextColor(if (color == blue) Color.WHITE else white)
            background = rounded(color, 14)
            minHeight = dp(50)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setOnClickListener { action() }
        }

    private fun rounded(color: Int, radius: Int) = GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radius).toFloat()
    }

    private fun spaced() = LinearLayout.LayoutParams(-1, -2).apply { bottomMargin = dp(8) }
    private fun topMargin(value: Int) = LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(value) }
    private fun weighted() = LinearLayout.LayoutParams(0, -2, 1f)
    private fun spacer(width: Int) = View(this).apply { layoutParams = LinearLayout.LayoutParams(width, 1) }
    private fun dp(value: Int) = (value * resources.displayMetrics.density).toInt()

    companion object {
        private const val REQUEST_WIFI_PERMISSIONS = 4107
    }
}
