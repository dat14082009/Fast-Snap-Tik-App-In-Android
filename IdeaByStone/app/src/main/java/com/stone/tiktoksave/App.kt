package com.stone.tiktoksave

import android.app.Application
import androidx.room.Room
import com.stone.tiktoksave.data.AppDatabase
import com.stone.tiktoksave.data.HistoryRepository
import com.stone.tiktoksave.data.PosterCache
import com.stone.tiktoksave.data.SettingsRepository
import com.stone.tiktoksave.download.MediaStoreSaver
import com.stone.tiktoksave.download.Notifications
import com.stone.tiktoksave.extractor.DouyinWebExtractor
import com.stone.tiktoksave.extractor.Extractor
import com.stone.tiktoksave.extractor.FallbackExtractor
import com.stone.tiktoksave.extractor.MemoryCookieJar
import com.stone.tiktoksave.extractor.RoutingExtractor
import com.stone.tiktoksave.extractor.TikTokWebExtractor
import com.stone.tiktoksave.extractor.WebViewExtractor
import okhttp3.OkHttpClient
import java.util.concurrent.TimeUnit

class App : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        Notifications.createChannels(this)
    }
}

/** Gom các thành phần dùng chung; Activity, ViewModel và Worker đều lấy từ đây. */
class AppContainer(app: Application) {
    private val httpClient: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(MemoryCookieJar())
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .build()

    /**
     * Chọn theo tên miền của link. TikTok: WebView (trình duyệt thật) trước, OkHttp dự phòng.
     * Douyin: WebView. Hai đường độc lập nhau; có thể thay bằng implementation khác.
     */
    val extractor: Extractor = RoutingExtractor(
        tiktok = FallbackExtractor(
            primary = WebViewExtractor(app),
            secondary = TikTokWebExtractor(httpClient)
        ),
        douyin = DouyinWebExtractor(app)
    )

    val saver = MediaStoreSaver(app, httpClient)
    val posters = PosterCache(app, httpClient)
    val settings = SettingsRepository(app)
    val history = HistoryRepository(
        Room.databaseBuilder(app, AppDatabase::class.java, "idea.db").build().historyDao()
    )
}
