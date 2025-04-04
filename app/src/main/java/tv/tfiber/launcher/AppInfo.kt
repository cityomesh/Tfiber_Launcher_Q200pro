package tv.tfiber.launcher

import android.content.Intent
import android.graphics.drawable.Drawable

data class AppInfo(
    val label: String,
    val icon: Drawable,
    val launchIntent: Intent
)