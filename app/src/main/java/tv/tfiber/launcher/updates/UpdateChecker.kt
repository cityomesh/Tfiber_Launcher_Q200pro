package tv.tfiber.launcher.updates

import android.content.Context
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException

class UpdateChecker(private val context: Context) {
    private val client = OkHttpClient()
    private val updateUrl =
        "https://surekha-55.github.io/TfiberLauncher-Updates/update.json" // Corrected URL

    suspend fun checkForUpdates(): UpdateInfo? = withContext(Dispatchers.IO) {
        Log.d("UpdateChecker", "checkForUpdates() started")
        try {
            Log.d("UpdateChecker", "Inside try block")
            Log.d("UpdateChecker", "Building request")
            val request = Request.Builder().url(updateUrl).build()
            Log.d("UpdateChecker", "Request built")
            Log.d("UpdateChecker", "Executing request")
            val response = client.newCall(request).execute()
            Log.d("UpdateChecker", "Request executed")
            Log.d("UpdateChecker", "Getting response body")
            val responseBody = response.body?.string() ?: return@withContext null
            Log.d("UpdateChecker", "c $responseBody")
            Log.d("UpdateChecker", "Parsing JSON")
            val json = JSONObject(responseBody)
            Log.d("UpdateChecker", "JSON parsed")
            val serverVersionCode = json.getInt("versionCode")
            val serverVersionName = json.getString("versionName")
            val apkUrl = json.getString("apkUrl")

            val releaseNotes = json.getString("releaseNotes")

            val packageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            val currentVersionCode = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.P) {
                packageInfo.longVersionCode.toInt()
            } else {
                packageInfo.versionCode
            }
            Log.d("UpdateChecker", "Current Version Code: $currentVersionCode")

            if (serverVersionCode > currentVersionCode) {
                Log.d("UpdateChecker", "Returning UpdateInfo")
                return@withContext UpdateInfo(
                    serverVersionCode,
                    serverVersionName,
                    apkUrl,
                    releaseNotes
                )
            }
        } catch (e: IOException) {
            Log.e("UpdateChecker", "Error checking for updates", e)
        } catch (e: Exception) {
            Log.e("UpdateChecker", "Error parsing update info", e)
        }
        Log.d("UpdateChecker", "checkForUpdates() finished")
        return@withContext null
    }

    suspend fun downloadApk(apkUrl: String, progressCallback: (Int) -> Unit): File? = withContext(Dispatchers.IO) {
        Log.d("UpdateChecker", "downloadApk() called with URL: $apkUrl")
        try {
            val request = Request.Builder().url(apkUrl).build()
            val response: Response = client.newCall(request).execute()
            val responseBody: ResponseBody? = response.body

            if (!response.isSuccessful || responseBody == null) {
                Log.e("UpdateChecker", "Failed to download APK: ${response.code}")
                return@withContext null
            }
            val timeStamp = System.currentTimeMillis()
            val apkFile = File(context.cacheDir, "new_app_$timeStamp.apk")
            val contentLength = responseBody.contentLength()
            var bytesDownloaded: Long = 0
            responseBody.byteStream().use { input ->
                apkFile.outputStream().use { output ->
                    val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
                    var bytes = input.read(buffer)
                    while (bytes >= 0) {
                        output.write(buffer, 0, bytes)
                        bytesDownloaded += bytes
                        val progress = if (contentLength > 0) {
                            (bytesDownloaded * 100 / contentLength).toInt()
                        } else {
                            0
                        }
                        withContext(Dispatchers.Main) {
                            progressCallback(progress)
                        }
                        bytes = input.read(buffer)
                    }
                }
            }
            return@withContext apkFile
        } catch (e: IOException) {
            Log.e("UpdateChecker", "Error downloading APK", e)
        } catch (e: Exception) {
            Log.e("UpdateChecker", "Error downloading APK", e)
        }
        return@withContext null
    }
}

data class UpdateInfo(
    val versionCode: Int,
    val versionName: String,
    val apkUrl: String,
    val releaseNotes: String
)