package tv.tfiber.launcher

data class IconItem(
    val iconResId: Int,
    val label: String,
    val packageName: String? = null,
    val url: String?= null)