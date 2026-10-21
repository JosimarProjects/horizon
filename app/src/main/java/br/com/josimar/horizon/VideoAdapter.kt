package br.com.josimar.horizon

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import br.com.josimar.horizon.databinding.ItemVideoBinding
import coil.decode.VideoFrameDecoder
import coil.load
import java.io.File

class VideoAdapter(
    private var items: List<File>,
    private val onClick: (File) -> Unit,
    private val onLongClick: (File) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    inner class VH(val b: ItemVideoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = items[position]
        holder.b.name.text = file.nameWithoutExtension
        holder.b.thumb.load(file) {
            decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
            crossfade(true)
        }
        holder.b.root.setOnClickListener { onClick(file) }
        holder.b.root.setOnLongClickListener { onLongClick(file); true }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<File>) {
        items = newItems
        notifyDataSetChanged()
    }
}
