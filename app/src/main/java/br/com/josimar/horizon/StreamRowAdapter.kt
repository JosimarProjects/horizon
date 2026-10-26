package br.com.josimar.horizon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import br.com.josimar.horizon.databinding.ItemStreamBinding
import coil.decode.VideoFrameDecoder
import coil.load
import java.io.File

class StreamRowAdapter(
    private val items: List<File>,
    private val onClick: (File) -> Unit,
    private val onLongClick: (File) -> Unit = {},
    private val selecting: () -> Boolean = { false },
    private val isSelected: (File) -> Boolean = { false }
) : RecyclerView.Adapter<StreamRowAdapter.VH>() {

    inner class VH(val b: ItemStreamBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemStreamBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = items[position]
        val ctx = holder.b.root.context
        holder.b.title.text = file.nameWithoutExtension
        holder.b.thumb.load(file) {
            decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
            crossfade(true)
        }
        val f = Resume.fraction(ctx, file.absolutePath)
        if (f > 0f) {
            holder.b.resume.visibility = View.VISIBLE
            holder.b.resume.setProgressCompat((f * 100).toInt(), false)
        } else {
            holder.b.resume.visibility = View.GONE
        }
        holder.b.check.visibility =
            if (selecting() && isSelected(file)) View.VISIBLE else View.GONE
        holder.b.watched.visibility =
            if (!selecting() && Resume.isWatched(ctx, file.absolutePath)) View.VISIBLE else View.GONE
        holder.b.root.setOnClickListener { onClick(file) }
        holder.b.root.setOnLongClickListener { onLongClick(file); true }
    }

    override fun getItemCount() = items.size
}
