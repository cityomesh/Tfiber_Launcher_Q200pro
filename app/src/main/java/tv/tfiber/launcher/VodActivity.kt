package tv.tfiber.launcher

import android.content.*
import android.media.AudioAttributes
import android.media.SoundPool
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class VodActivity : AppCompatActivity() {

    private lateinit var soundPool: SoundPool
    private var clickSoundId: Int = 0
    private lateinit var appGamesAdapter: LauncherAdapter
    private lateinit var videoAdapter: LauncherAdapter // New adapter for videos
    private val appGamesIcons = mutableListOf<IconItem>()
    private val videoIcons = mutableListOf<IconItem>() // New list for video icons
    private lateinit var iconRecyclerView: RecyclerView
    private lateinit var videoRecyclerView: RecyclerView

    private val allIcons = mutableListOf<IconItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vod)

        // Load sound
        initSoundPool()

        iconRecyclerView = findViewById(R.id.iconRecyclerView)
        videoRecyclerView = findViewById(R.id.videoRecyclerView) // Initialize video RecyclerView
        val appGamesTab = findViewById<TextView>(R.id.appGamesTab)
        val videoTab = findViewById<TextView>(R.id.videoTab)

        val appGamesAdditionalIcons = listOf(
            IconItem(R.drawable.diksha, "", "com.google.android.youtube"),
            IconItem(R.drawable.tsat, "", "com.google.android.youtube"),
            IconItem(R.drawable.ndli, "digital library", "com.google.android.youtube"),
            IconItem(R.drawable.byjus, "digital library", "com.google.android.youtube")


        )

        appGamesIcons.addAll(appGamesAdditionalIcons)

        appGamesAdapter = LauncherAdapter(appGamesIcons) { iconItem ->
            handleIconClick(iconItem)
        }

        iconRecyclerView.layoutManager = GridLayoutManager(this, 7)
        iconRecyclerView.adapter = appGamesAdapter

        // --- Video Icons ---
        val videoAdditionalIcons = listOf(
            IconItem(R.drawable.thumbnail1, "", "com.google.android.youtube")
        )
        
        videoIcons.addAll(videoAdditionalIcons)

        videoAdapter = LauncherAdapter(videoIcons) { iconItem ->
            handleIconClick(iconItem)
        }

        videoRecyclerView.layoutManager = GridLayoutManager(this, 7)
        videoRecyclerView.adapter = videoAdapter



        // --- Tab Handling ---
        appGamesTab.setOnClickListener {
            showAppGamesIcons()
        }

        videoTab.setOnClickListener {
            showVideoIcons()
        }


        // --- Initial Focus ---
        iconRecyclerView.post {
            if (appGamesIcons.isNotEmpty()) {
                iconRecyclerView.getChildAt(0)?.requestFocus()
            }
        }

        // --- Shared Focus Change Listener ---
        val onFocusChangeListener = View.OnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                view.scaleX = 1.2f
                view.scaleY = 1.2f
                playSound()
            } else {
                view.scaleX = 1.0f
                view.scaleY = 1.0f
            }
        }

        // Apply focus listener to both RecyclerViews
        iconRecyclerView.addOnChildAttachStateChangeListener(object :
            RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                view.onFocusChangeListener = onFocusChangeListener
            }

            override fun onChildViewDetachedFromWindow(view: View) {}
        })
        videoRecyclerView.addOnChildAttachStateChangeListener(object :
            RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                view.onFocusChangeListener = onFocusChangeListener
            }

            override fun onChildViewDetachedFromWindow(view: View) {}
        })

        // Initially show only Appsgames icons
        showAppGamesIcons()
    }

    private fun showAppGamesIcons() {
        iconRecyclerView.visibility = View.VISIBLE
        videoRecyclerView.visibility = View.GONE
        if (appGamesIcons.isNotEmpty()) {
            iconRecyclerView.getChildAt(0)?.requestFocus()
        }
    }

    private fun showVideoIcons() {
        iconRecyclerView.visibility = View.GONE
        videoRecyclerView.visibility = View.VISIBLE
        if (videoIcons.isNotEmpty()) {
            videoRecyclerView.getChildAt(0)?.requestFocus()
        }
    }

    private fun handleIconClick(iconItem: IconItem) {
        when {
            iconItem.packageName == "com.google.android.youtube" -> {
                try {
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com"))
                    intent.setPackage("com.google.android.youtube")
                    startActivity(intent)
                } catch (e: ActivityNotFoundException) {
                    startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://www.youtube.com")))
                }
            }
            !iconItem.url.isNullOrEmpty() -> {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(iconItem.url))
                startActivity(browserIntent)
            }
            !iconItem.packageName.isNullOrEmpty() -> {
                launchApp(iconItem.packageName)
            }
            else -> {
                Log.e("VodActivity", "No valid action")
            }
        }
    }


    private fun launchApp(packageName: String) {
        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            if (intent != null) {
                startActivity(intent)
            } else {
                Log.e("VodActivity", "App not found: $packageName")
            }
        } catch (e: Exception) {
            Log.e("VodActivity", "Error launching app: $packageName", e)
        }
    }

    private fun initSoundPool() {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()

        soundPool = SoundPool.Builder()
            .setMaxStreams(1)
            .setAudioAttributes(audioAttributes)
            .build()

        clickSoundId = soundPool.load(this, R.raw.click_sound, 1)
    }

    private fun playSound() {
        if (clickSoundId != 0) {
            soundPool.play(clickSoundId, 1f, 1f, 0, 0, 1f)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        soundPool.release()
    }
}
