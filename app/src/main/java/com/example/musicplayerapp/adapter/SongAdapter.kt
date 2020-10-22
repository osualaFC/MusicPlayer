package com.example.musicplayerapp.adapter

import androidx.recyclerview.widget.AsyncListDiffer
import com.bumptech.glide.RequestManager
import com.example.musicplayerapp.R
import kotlinx.android.synthetic.main.list_item.view.*
import javax.inject.Inject

class SongAdapter @Inject constructor(
    private val glide: RequestManager
) : BaseSongAdapter(R.layout.list_item) {

    override val differ = AsyncListDiffer(this, diffCallback)

    override fun onBindViewHolder(holder: SongViewHolder, position: Int) {
        val song = songs[position]
        holder.itemView.apply {
            tvPrimary.text = song.title
            tvSecondary.text = song.artist
            glide.load(song.thumbnail).into(ivItemImage)

            setOnClickListener {
                onItemClickListener?.let { click ->
                    click(song)
                }
            }
        }
    }

}
