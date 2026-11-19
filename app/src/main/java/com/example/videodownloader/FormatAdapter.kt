package com.example.videodownloader

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

/**
 * RecyclerView adapter for the format list.
 * Used if you go the XML/View route instead of Compose.
 *
 * @param onFormatClick Callback invoked when the user taps a format row.
 */
class FormatAdapter(
    private var formats: List<MediaFormat> = emptyList(),
    private val onFormatClick: (MediaFormat) -> Unit
) : RecyclerView.Adapter<FormatAdapter.FormatViewHolder>() {

    /** Replaces the current list and refreshes the UI. */
    fun submitList(newFormats: List<MediaFormat>) {
        formats = newFormats
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): FormatViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_format, parent, false)
        return FormatViewHolder(view)
    }

    override fun onBindViewHolder(holder: FormatViewHolder, position: Int) {
        holder.bind(formats[position])
    }

    override fun getItemCount(): Int = formats.size

    inner class FormatViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val qualityText: TextView = itemView.findViewById(R.id.qualityText)
        private val detailsText: TextView = itemView.findViewById(R.id.detailsText)

        fun bind(format: MediaFormat) {
            // Build a human-readable quality label
            val quality = when {
                format.isCombined -> "${format.resolution} (combined)"
                format.hasVideo -> format.resolution.ifEmpty { format.formatNote }
                else -> format.formatNote.ifEmpty { "Audio" }
            }
            qualityText.text = quality

            // Build the detail line: EXT • bitrate • file size
            detailsText.text = buildString {
                append(format.extension.uppercase())
                if (format.bitrate > 0) {
                    append(" • ${format.bitrate.toInt()} kbps")
                }
                if (format.fileSizeBytes > 0) {
                    append(" • ${formatFileSize(format.fileSizeBytes)}")
                }
            }

            // Click listener
            itemView.setOnClickListener { onFormatClick(format) }
        }
    }
}

/** Simple item layout – create `res/layout/item_format.xml` */
/*
<?xml version="1.0" encoding="utf-8"?>
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:orientation="vertical"
    android:padding="12dp"
    android:background="?attr/selectableItemBackground">

    <TextView
        android:id="@+id/qualityText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textStyle="bold"
        android:textSize="14sp" />

    <TextView
        android:id="@+id/detailsText"
        android:layout_width="match_parent"
        android:layout_height="wrap_content"
        android:textSize="12sp"
        android:alpha="0.7" />
</LinearLayout>
*/