package tv.tfiber.launcher

import android.app.AlertDialog
import android.app.Dialog
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.SurfaceTexture
import android.graphics.drawable.ColorDrawable
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.SoundPool
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.Surface
import android.view.TextureView
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.launch
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.background
import androidx.core.content.FileProvider
import androidx.core.text.color
import androidx.glance.layout.height
import androidx.glance.layout.width
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import tv.tfiber.launcher.updates.UpdateChecker
import tv.tfiber.launcher.updates.UpdateInfo
import java.io.File
import android.graphics.Rect
import androidx.recyclerview.widget.RecyclerView.ItemDecoration
import androidx.recyclerview.widget.RecyclerView.State
import androidx.recyclerview.widget.RecyclerView.OnChildAttachStateChangeListener
import android.view.ViewGroup
import android.widget.FrameLayout
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import android.content.ActivityNotFoundException

class MainActivity : AppCompatActivity() {

    private lateinit var textureView: TextureView
    private var mediaPlayer: MediaPlayer? = null
    private lateinit var soundPool: SoundPool
    private var clickSoundId: Int = 0
    private var audioFocusRequestGranted = false
    private lateinit var audioManager: AudioManager
    private lateinit var audioFocusRequest: AudioFocusRequest
    private lateinit var updateChecker: UpdateChecker
    private lateinit var updateDialog: AlertDialog
    private lateinit var progressDialog: AlertDialog
    private lateinit var progressBar: ProgressBar
    private val audioFocusChangeListener = AudioManager.OnAudioFocusChangeListener { focusChange ->
        when (focusChange) {
            AudioManager.AUDIOFOCUS_GAIN -> {
                Log.d("MainActivity", "Audio focus gained")
                audioFocusRequestGranted = true
                mediaPlayer?.setVolume(1.0f, 1.0f)
            }

            AudioManager.AUDIOFOCUS_LOSS -> {
                Log.d("MainActivity", "Audio focus lost")
                audioFocusRequestGranted = false
                mediaPlayer?.pause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT -> {
                Log.d("MainActivity", "Audio focus lost transient")
                mediaPlayer?.pause()
            }

            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                Log.d("MainActivity", "Audio focus lost transient can duck")
                mediaPlayer?.setVolume(0.2f, 0.2f)
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize SoundPool
        createSoundPool()
        // Load the click sound
        clickSoundId = soundPool.load(this, R.raw.click_sound, 1)
        if (clickSoundId == 0) {
            Log.e("MainActivity", "Failed to load click sound!")
        } else {
            Log.d("MainActivity", "Click sound loaded with ID: $clickSoundId")
        }
        textureView = findViewById(R.id.bannerTextureView)
        val settingsIcon = findViewById<ImageView>(R.id.settingsIcon)
        val updateIcon: ImageView = findViewById(R.id.updateIcon)

        updateChecker = UpdateChecker(this)

        settingsIcon.setOnClickListener {
            openSettings()
        }

        updateIcon.setOnClickListener {
            openAppDetails()
        }

        settingsIcon.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                playSound()
                highlightIcon(view)
            } else {
                removeHighlight(view)
            }
        }

        updateIcon.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                playSound()
            }
        }

        updateIcon.setOnHoverListener { v, event ->
            if (event.action == MotionEvent.ACTION_HOVER_ENTER) {
                val width = v.width
                val height = v.height
                Toast.makeText(this, "Width: ${width}px, Height: ${height}px", Toast.LENGTH_SHORT).show()
            }
            false
        }

        setupRecyclerView()
        setupTextureView()
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioFocusRequest = createAudioFocusRequest()
        requestAudioFocus()
        checkForUpdates()
    }

    private fun createAudioFocusRequest(): AudioFocusRequest {
        val playbackAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_MEDIA)
            .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC)
            .build()

        return AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(playbackAttributes)
            .setAcceptsDelayedFocusGain(true)
            .setOnAudioFocusChangeListener(audioFocusChangeListener)
            .build()
    }

    private fun checkForUpdates() {
        Log.d("MainActivity", "checkForUpdates() called")
        CoroutineScope(Dispatchers.Main).launch {
            val updateInfo = updateChecker.checkForUpdates()
            Log.d("MainActivity", "updateInfo: $updateInfo")
            if (updateInfo != null) {
                Log.d("MainActivity", "UpdateInfo is not null")
                showUpdateDialog(updateInfo)
            } else {
                Log.d("MainActivity", "UpdateInfo is null")
            }
        }
    }

    private fun showUpdateDialog(updateInfo: UpdateInfo) {
        Log.d("MainActivity", "showUpdateDialog() called")
        val builder = AlertDialog.Builder(this@MainActivity)
        builder.setTitle("Update Available")
        builder.setMessage("Version ${updateInfo.versionName} is available. Release Notes: ${updateInfo.releaseNotes}")
        builder.setPositiveButton("Install") { dialog, _ ->
            Log.d("MainActivity", "Install button clicked")
            dialog.dismiss()
            downloadAndInstallApk(updateInfo.apkUrl)
        }
        builder.setNegativeButton("Cancel") { dialog, _ ->
            dialog.dismiss()
        }
        updateDialog = builder.create()
        Log.d("MainActivity", "updateDialog.show() about to be called")
        updateDialog.show()
    }

    private fun showProgressDialog() {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Downloading Update")
        progressBar = ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal)
        progressBar.isIndeterminate = true
        builder.setView(progressBar)
        builder.setCancelable(false)
        progressDialog = builder.create()
        progressDialog.show()
    }

    private fun hideProgressDialog() {
        if (progressDialog.isShowing) {
            progressDialog.dismiss()
        }
    }

    private fun showErrorDialog(message: String) {
        val builder = AlertDialog.Builder(this)
        builder.setTitle("Download Failed")
        builder.setMessage(message)
        builder.setPositiveButton("OK") { dialog, _ ->
            dialog.dismiss()
        }
        builder.create().show()
    }

    private fun handleUpdate(updateInfo: UpdateInfo) {
        // Display a dialog or notification to the user
        // ... (e.g., show a dialog with updateInfo.releaseNotes)

        // For now, let's just log the update info and start the download/install
        Log.d("MainActivity", "Update available: ${updateInfo.versionName}")
        Log.d("MainActivity", "Release Notes: ${updateInfo.releaseNotes}")

        // Start the download and installation
        downloadAndInstallApk(updateInfo.apkUrl)
    }

    private fun downloadAndInstallApk(apkUrl: String) {
        Log.d("MainActivity", "downloadAndInstallApk() called with URL: $apkUrl")
        showProgressDialog() // Show the dialog
        CoroutineScope(Dispatchers.Main).launch { // Use Dispatchers.Main for UI updates
            val apkFile = updateChecker.downloadApk(apkUrl) { progress ->
                // Update the progress bar on the UI thread
                updateProgress(progress)
            }
            if (apkFile != null) {
                Log.d("MainActivity", "APK file path before installApk(): ${apkFile.absolutePath}")
                Log.d("MainActivity", "APK file size: ${apkFile.length()}")
                installApk(apkFile)
            } else {
                // Handle download failure
                Log.e("MainActivity", "Failed to download APK")
                hideProgressDialog() // Hide the dialog
                showErrorDialog("Failed to download update") // Show error message
            }
        }
    }

    private fun updateProgress(progress: Int) {
        progressBar.isIndeterminate = false
        progressBar.progress = progress
    }

    private fun installApk(apkFile: File) {
        Log.d("MainActivity", "installApk() called with file: $apkFile")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (!packageManager.canRequestPackageInstalls()) {
                Log.d("MainActivity", "Requesting permission to install packages")
                startActivity(Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).setData(Uri.parse("package:$packageName")))
                return
            }
        }
        Log.d("MainActivity", "Permission to install packages granted")
        val uri = FileProvider.getUriForFile(this, "${packageName}.provider", apkFile)
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK
        }
        Log.d("MainActivity", "Starting installation intent")
        startActivity(installIntent)
    }

    private fun requestAudioFocus() {
        // if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        audioManager.requestAudioFocus(audioFocusRequest)
        /* } else {
            audioManager.requestAudioFocus(
                audioFocusChangeListener,
                AudioManager.STREAM_MUSIC,
                AudioManager.AUDIOFOCUS_GAIN
            )
        }     */
    }

    private fun abandonAudioFocus() {
        audioManager.abandonAudioFocusRequest(audioFocusRequest)
    }

    private fun createSoundPool() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            val audioAttributes = AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()

            soundPool = SoundPool.Builder()
                .setMaxStreams(1)
                .setAudioAttributes(audioAttributes)
                .build()
        } else {
            soundPool = SoundPool(1, AudioManager.STREAM_MUSIC, 0)
        }
    }

    private fun playSound() {
        Log.d("MainActivity", "playSound() called. audioFocusRequestGranted: $audioFocusRequestGranted")
        if (audioFocusRequestGranted) {
            soundPool.play(clickSoundId, 1f, 1f, 0, 0, 1f)
        }
    }

    private fun openSettings() {
        val intent = Intent(Settings.ACTION_SETTINGS)
        startActivity(intent)
    }

    private fun openAppDetails() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
    }

    private fun highlightIcon(view: View) {
        val color = (view.background as? ColorDrawable)?.color
        if (color != Color.YELLOW) {
            view.setBackgroundColor(Color.YELLOW)
        }
    }

    private fun removeHighlight(view: View) {
        view.setBackgroundColor(Color.TRANSPARENT)
    }


    private fun setupRecyclerView() {
        val leftIcons = listOf(
            IconItem(R.drawable.livetv_logo, "", "tv.ulka.ulkalite"),
            IconItem(R.drawable.youtube, "", "com.google.android.youtube"), // YOUTUBE APP
            IconItem(R.drawable.vod, "", "com.example.vodapp"),
            IconItem(R.drawable.media_player, "", "com.example.mediaplayer"),
            IconItem(R.drawable.apps, "", "in.webgrid.ulkatv"),
            IconItem(R.drawable.reminders, "", "com.example.remindersapp")
        )

        val rightIcons = listOf(
            IconItem(R.drawable.virtual_pc, "", "com.example.tfibervdi"),
            IconItem(R.drawable.my_files, "", "com.example.myfiles"),
            IconItem(R.drawable.e_health, "", url = "https://health.telangana.gov.in"),
            IconItem(R.drawable.e_education, "", "com.tvapp.tsat"),
            IconItem(R.drawable.about, "", "com.example.about"),
            IconItem(R.drawable.digital_nidhi, "", url = "https://usof.gov.in/en/home")
        )

        val spacingInPixels = resources.getDimensionPixelSize(R.dimen.recycler_view_spacing)

        val recyclerViewLeft = findViewById<RecyclerView>(R.id.recyclerViewLeft)
        val recyclerViewRight = findViewById<RecyclerView>(R.id.recyclerViewRight)

        recyclerViewLeft.addItemDecoration(SpacingItemDecoration(spacingInPixels))
        recyclerViewRight.addItemDecoration(SpacingItemDecoration(spacingInPixels))

        recyclerViewLeft.layoutManager = GridLayoutManager(this, 2)
        recyclerViewRight.layoutManager = GridLayoutManager(this, 2)


        recyclerViewLeft.adapter = LauncherAdapter(leftIcons) { iconItem ->
            when (iconItem.packageName) {
                "com.example.vodapp" -> {
                    val intent = Intent(this, VodActivity::class.java)
                    startActivity(intent)
                }
                "in.webgrid.ulkatv" -> {
                    Log.d("MainActivity", "Opening AppsActivity")
                    val intent = Intent(this, AppsActivity::class.java)
                    startActivity(intent)
                }
                "com.google.android.youtube" -> {
                    try {
                        val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
                        intent.setPackage("com.google.android.youtube")
                        startActivity(intent)
                    } catch (e: ActivityNotFoundException) {
                        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")))
                    }
                }
                else -> {
                    if (iconItem.url != null) {
                        openWebPage(iconItem.url)
                    } else if (iconItem.packageName != null) {
                        launchApp(iconItem.packageName)
                    }
                }
            }
        }


        recyclerViewRight.adapter = LauncherAdapter(rightIcons) { iconItem ->
            when {
                iconItem.packageName == "com.example.myfiles" -> {
                    val intent = Intent(this, VirtualPCActivity::class.java)
                    startActivity(intent)
                }
                iconItem.url == "https://health.telangana.gov.in" -> {
                    val intent = Intent(this, EHealthActivity::class.java)
                    startActivity(intent)
                }
                iconItem.packageName == "com.tvapp.tsat" -> {
                    val intent = Intent(this, EEducationActivity::class.java)
                    startActivity(intent)
                }
                !iconItem.url.isNullOrEmpty() -> {
                    openWebPage(iconItem.url)
                }
                !iconItem.packageName.isNullOrEmpty() -> {
                    launchApp(iconItem.packageName)
                }
                else -> {
                    Log.e("MainActivity", "No valid action found for icon")
                }
            }
        }

        // Set default focus to the first item in the left RecyclerView
        recyclerViewLeft.post {
            val firstItemView = recyclerViewLeft.getChildAt(0)
            firstItemView?.requestFocus()
        }


        // Add focus change listener to each item in the RecyclerView
        recyclerViewLeft.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                view.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                    Log.d("MainActivity", "RecyclerView item focus changed. hasFocus: $hasFocus")
                    if (hasFocus) {
                        playSound()
                    }
                }
            }

            override fun onChildViewDetachedFromWindow(view: View) {
                // No action needed when detached
            }
        })

        recyclerViewRight.addOnChildAttachStateChangeListener(object : RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                view.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        playSound()
                    }
                }
            }

            override fun onChildViewDetachedFromWindow(view: View) {
                // No action needed when detached
            }
        })
    }

    private fun openWebPage(url: String) {
        val webpage: Uri = Uri.parse(url)
        val intent = Intent(Intent.ACTION_VIEW, webpage)
        if (intent.resolveActivity(packageManager) != null) {
            startActivity(intent)
        }
    }

    private fun setupTextureView() {
        textureView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
            override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
                initializeMediaPlayer(surface)
            }

            override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {}

            override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
                releaseMediaPlayer()
                return true
            }

            override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {}
        }
    }

    private fun initializeMediaPlayer(surfaceTexture: SurfaceTexture) {
        try {
            val videoUri = Uri.parse("android.resource://$packageName/${R.raw.tfiber_intro}")
            mediaPlayer = MediaPlayer().apply {
                setDataSource(applicationContext, videoUri)
                setSurface(Surface(surfaceTexture))
                isLooping = true
                prepareAsync()
                setOnPreparedListener { start() }
            }
        } catch (e: Exception) {
            Log.e("MainActivity", "Error initializing MediaPlayer", e)
        }
    }

    private fun releaseMediaPlayer() {
        mediaPlayer?.apply {
            stop()
            release()
        }
        mediaPlayer = null
    }

    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
        abandonAudioFocus()
    }

    override fun onResume() {
        super.onResume()
        mediaPlayer?.start()

    }

    override fun onDestroy() {
        super.onDestroy()
        releaseMediaPlayer()
        soundPool.release()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        Log.d("MainActivity", "onKeyDown: keyCode = $keyCode")
        return when (keyCode) {
            KeyEvent.KEYCODE_SETTINGS -> {
                startActivity(Intent(Settings.ACTION_SETTINGS))
                true
            }
            KeyEvent.KEYCODE_POWER -> {
                Toast.makeText(this, "Power button pressed", Toast.LENGTH_SHORT).show()
                true
            }
            KeyEvent.KEYCODE_BACK -> {
                Toast.makeText(this, "Back button pressed", Toast.LENGTH_SHORT).show()
                super.onKeyDown(keyCode, event)
            }
            else -> super.onKeyDown(keyCode, event)
        }
    }

    private fun launchApp(packageName: String?) {
        Log.d("MainActivity", "Attempting to launch: $packageName")

        if (packageName.isNullOrEmpty()) {
            Log.e("MainActivity", "Package name is null or empty")
            return
        }

        if (packageName == "com.android.settings") {
            startActivity(Intent(Settings.ACTION_SETTINGS))
            return
        }

        val intent = Intent(Intent.ACTION_MAIN).apply {
            addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
            setPackage(packageName)
        }

        val resolveInfo = packageManager.queryIntentActivities(intent, 0).firstOrNull()
        if (resolveInfo != null) {
            val launchIntent = Intent(Intent.ACTION_MAIN).apply {
                addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)
                component = ComponentName(packageName, resolveInfo.activityInfo.name)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            startActivity(launchIntent)
        } else {
            try {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName")))
            } catch (e: android.content.ActivityNotFoundException) {
                startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$packageName")))
            }
        }
    }
}
