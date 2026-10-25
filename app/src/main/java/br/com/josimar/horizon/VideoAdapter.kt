package br.com.josimar.horizon

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import br.com.josimar.horizon.databinding.ItemVideoBinding
import coil.decode.VideoFrameDecoder
import coil.load
import java.io.File

class VideoAdapter(
    private var items: List<File>,
    private val onClick: (File) -> Unit,
    private val onLongClick: (File) -> Unit,
    private val onShare: (File) -> Unit,
    private val onMore: (File) -> Unit
) : RecyclerView.Adapter<VideoAdapter.VH>() {

    var selectionMode = false
        private set
    val selected = linkedSetOf<File>()

    inner class VH(val b: ItemVideoBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val b = ItemVideoBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(b)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val file = items[position]
        val ctx = holder.b.root.context
        holder.b.name.text = file.nameWithoutExtension
        holder.b.thumb.load(file) {
            decoderFactory { result, options, _ -> VideoFrameDecoder(result.source, options) }
            crossfade(true)
        }

        val isSel = file in selected
        holder.b.check.visibility = if (selectionMode && isSel) View.VISIBLE else View.GONE
        holder.b.actions.visibility = if (selectionMode) View.GONE else View.VISIBLE

        val density = ctx.resources.displayMetrics.density
        val folder = if (file.parentFile?.name == "Horizon") "" else file.parentFile?.name
        val folderColor = Palette.colorFor(folder)
        if (selectionMode && isSel) {
            holder.b.card.strokeColor = Palette.ACCENT
            holder.b.card.strokeWidth = (3 * density).toInt()
        } else {
            // borda fina com a cor da pasta
            holder.b.card.strokeColor = folderColor
            holder.b.card.strokeWidth = (1.5f * density).toInt()
        }
        holder.b.name.setTextColor(folderColor)

        holder.b.card.setOnClickListener { onClick(file) }
        holder.b.card.setOnLongClickListener { onLongClick(file); true }
        holder.b.thumb.setOnClickListener { onClick(file) }
        holder.b.name.setOnClickListener { onClick(file) }
        holder.b.btnShare.setOnClickListener { onShare(file) }
        holder.b.btnMore.setOnClickListener { onMore(file) }
    }

    override fun getItemCount() = items.size

    fun update(newItems: List<File>) {
        items = newItems
        // remove da seleção itens que não existem mais
        selected.retainAll(newItems.toSet())
        notifyDataSetChanged()
    }

    fun setSelectionMode(on: Boolean) {
        selectionMode = on
        if (!on) selected.clear()
        notifyDataSetChanged()
    }

    fun toggle(file: File) {
        if (!selected.add(file)) selected.remove(file)
        notifyDataSetChanged()
    }

    fun selectedList(): List<File> = selected.toList()
}
