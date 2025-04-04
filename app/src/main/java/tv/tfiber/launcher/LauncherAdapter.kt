package tv.tfiber.launcher

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView



// Adapter for the RecyclerView
class LauncherAdapter(
    private val icons: List<IconItem>,
    private val onItemClick: (IconItem) -> Unit
) :
    RecyclerView.Adapter<LauncherAdapter.IconViewHolder>() {

    // ViewHolder class to represent each item in the grid
    class IconViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.iconImageView)


        fun bind(iconItem: IconItem, itemClickListener: (IconItem) -> Unit) {
            imageView.setImageResource(iconItem.iconResId)

            itemView.setOnClickListener {
                itemClickListener(iconItem)  // Handle the click event
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): IconViewHolder {
        val itemview = LayoutInflater.from(parent.context).inflate(R.layout.item_icon, parent, false)
        return IconViewHolder(itemview)
    }

    override fun onBindViewHolder(holder: IconViewHolder, position: Int) {
        val iconItem = icons[position]
        holder.bind(iconItem, onItemClick)
    }

    override fun getItemCount(): Int {
        return icons.size
    }
}