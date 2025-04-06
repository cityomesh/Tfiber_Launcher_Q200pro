package tv.tfiber.launcher

import android.content.*
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.MotionEvent
import android.view.View
import android.widget.ImageView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.core.content.ContextCompat

class VodActivity : AppCompatActivity() {

    private lateinit var soundPool: SoundPool
    private var clickSoundId: Int = 0
    private lateinit var adapter: LauncherAdapter
    private val allIcons = mutableListOf<IconItem>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_vod)

        // Load sound
        initSoundPool()

        val recyclerView = findViewById<RecyclerView>(R.id.iconRecyclerView)
        val settingsIcon = findViewById<ImageView>(R.id.settingsIcon)
        val updateIcon = findViewById<ImageView>(R.id.updateIcon)
        settingsIcon.background = ContextCompat.getDrawable(this, R.drawable.hover_background)
        val bottomImage: ImageView = findViewById(R.id.bottomImage)

        // Set focus navigation directions
        settingsIcon.nextFocusDownId = R.id.iconRecyclerView
        updateIcon.nextFocusDownId = R.id.iconRecyclerView
        updateIcon.nextFocusLeftId = R.id.settingsIcon
        recyclerView.nextFocusUpId = R.id.settingsIcon

        // Make sure settingsIcon is focused by default
        settingsIcon.requestFocus()

        val appGamesIcon = listOf(
            IconItem(R.drawable.appsgames, "", "in.webgrid.ulkatv", bottomImageResId = R.drawable.media_player)
        )

        val additionalIcons = listOf(
            IconItem(R.drawable.netflix, "", url = "https://www.netflix.com/in/"),
            IconItem(R.drawable.youtube, "", "com.google.android.youtube"),
            IconItem(R.drawable.hostar, "", url = "https://www.hotstar.com/in/home?ref=%2Fin"),
            IconItem(R.drawable.nbc, "", url = "https://www.nbc.com/")
        )

        allIcons.addAll(appGamesIcon)

        adapter = LauncherAdapter(allIcons) { iconItem ->
            if (iconItem.packageName == "in.webgrid.ulkatv") {
                // Toggle logic: Expand or Collapse
                if (allIcons.size == 1) {
                    allIcons.addAll(additionalIcons)
                    adapter.notifyItemRangeInserted(1, additionalIcons.size)
                } else {
                    allIcons.clear()
                    allIcons.addAll(appGamesIcon)
                    adapter.notifyDataSetChanged()

                    // Refocus on Appsgames icon
                    recyclerView.post {
                        recyclerView.findViewHolderForAdapterPosition(0)?.itemView?.requestFocus()
                    }
                }
            } else {
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
        }

        recyclerView.layoutManager = GridLayoutManager(this, 7)
        recyclerView.adapter = adapter

        recyclerView.post {
            recyclerView.getChildAt(0)?.requestFocus()
        }

        recyclerView.addOnChildAttachStateChangeListener(object :
            RecyclerView.OnChildAttachStateChangeListener {
            override fun onChildViewAttachedToWindow(view: View) {
                view.onFocusChangeListener = View.OnFocusChangeListener { _, hasFocus ->
                    if (hasFocus) {
                        view.scaleX = 1.2f
                        view.scaleY = 1.2f
                        playSound()
                    } else {
                        view.scaleX = 1.0f
                        view.scaleY = 1.0f
                    }
                }
            }

            override fun onChildViewDetachedFromWindow(view: View) {}
        })

        settingsIcon.setOnClickListener {
            openSettings()
        }

        updateIcon.setOnClickListener {
            openAppDetails()
        }

        settingsIcon.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                playSound()
                view.scaleX = 1.2f
                view.scaleY = 1.2f
            } else {
                view.scaleX = 1.0f
                view.scaleY = 1.0f
            }
        }

        settingsIcon.setOnHoverListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_HOVER_ENTER -> {
                    v.setBackgroundColor(resources.getColor(android.R.color.darker_gray, null))
                }
                MotionEvent.ACTION_HOVER_EXIT -> {
                    v.setBackgroundColor(android.graphics.Color.TRANSPARENT)
                }
            }
            false
        }

        updateIcon.setOnFocusChangeListener { view, hasFocus ->
            if (hasFocus) {
                playSound()
                view.scaleX = 1.2f
                view.scaleY = 1.2f
            } else {
                view.scaleX = 1.0f
                view.scaleY = 1.0f
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

    private fun openSettings() {
        try {
            val intent = Intent(android.provider.Settings.ACTION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("VodActivity", "Unable to open settings", e)
        }
    }

    private fun openAppDetails() {
        val intent = Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
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
