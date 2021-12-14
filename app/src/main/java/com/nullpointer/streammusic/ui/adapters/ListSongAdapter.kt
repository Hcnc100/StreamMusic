package com.nullpointer.streammusic.ui.adapters

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import coil.load
import com.nullpointer.streammusic.R
import com.nullpointer.streammusic.databinding.ItemSongBinding
import com.nullpointer.streammusic.models.Song

class ListSongAdapter(
    private val clickSong: (Song) -> Unit
) : ListAdapter<Song, ListSongAdapter.SongViewHolder>(DiffUtilSong) {

    init {
        setHasStableIds(true)
    }

    inner class SongViewHolder(
        private val binding: ItemSongBinding,
        onItemClicked: (Int) -> Unit
    ) : RecyclerView.ViewHolder(binding.root) {
        init {
            binding.container.setOnClickListener {
                onItemClicked(absoluteAdapterPosition)
            }
        }

        fun bind(song: Song) = with(binding) {
            textNameSong.text=song.title
            textArtist.text=song.subtitle
            imageSong.load(song.imgUrl)
        }
    }

    override fun getItemId(position: Int): Long =
        getItem(position).mediaId.hashCode().toLong()

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SongViewHolder {
        return SongViewHolder(
            ItemSongBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        ) { clickSong(currentList[it]) }
    }


    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        holder.apply { bind(getItem(position)) }
    }

    private object DiffUtilSong : DiffUtil.ItemCallback<Song>() {
        override fun areItemsTheSame(oldItem: Song, newItem: Song): Boolean =
            oldItem.mediaId == newItem.mediaId

        override fun areContentsTheSame(oldItem: Song, newItem: Song): Boolean =
            oldItem == newItem
    }

}