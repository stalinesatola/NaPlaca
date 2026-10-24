package ao.naplaca.app.data.remote

import ao.naplaca.app.BuildConfig
import kotlinx.serialization.json.Json
import okhttp3.Cache
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.io.File
import java.util.concurrent.TimeUnit

object Network {
    val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    fun okHttp(cacheDir: File): OkHttpClient = OkHttpClient.Builder()
        .cache(Cache(File(cacheDir, "http"), 20L * 1024 * 1024))
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .addInterceptor { chain ->
            chain.proceed(
                chain.request().newBuilder()
                    .header("User-Agent", "NaPlaca/${BuildConfig.VERSION_NAME} (Android)")
                    .build(),
            )
        }
        .apply {
            if (BuildConfig.DEBUG) {
                addInterceptor(HttpLoggingInterceptor().setLevel(HttpLoggingInterceptor.Level.BASIC))
            }
        }
        .build()

    private fun retrofit(client: OkHttpClient, baseUrl: String): Retrofit = Retrofit.Builder()
        .baseUrl(baseUrl)
        .client(client)
        .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
        .build()

    fun espn(client: OkHttpClient): EspnApi =
        retrofit(client, "https://site.api.espn.com/").create(EspnApi::class.java)

    fun sportsDb(client: OkHttpClient): SportsDbApi =
        retrofit(client, "https://www.thesportsdb.com/api/v1/json/123/").create(SportsDbApi::class.java)
}
