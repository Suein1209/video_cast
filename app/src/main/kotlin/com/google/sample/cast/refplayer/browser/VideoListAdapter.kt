/*
 * Copyright (C) 2022 Google LLC. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.sample.cast.refplayer.browser

import android.content.Context
import com.google.sample.cast.refplayer.utils.CustomVolleyRequest.Companion.getInstance
import androidx.recyclerview.widget.RecyclerView
import android.view.ViewGroup
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import com.google.sample.cast.refplayer.R
import com.android.volley.toolbox.NetworkImageView
import android.widget.TextView
import com.android.volley.toolbox.ImageLoader
import com.google.sample.cast.refplayer.utils.MediaItem

/**
 * An [ArrayAdapter] to populate the list of videos.
 */
class VideoListAdapter(private val mClickListener: ItemClickListener, context: Context) : RecyclerView.Adapter<VideoListAdapter.ViewHolder>() {
    private val mAppContext: Context
    private var videos: List<MediaItem>? = null
    override fun onCreateViewHolder(viewGroup: ViewGroup, viewType: Int): ViewHolder {
        val context = viewGroup.context
        val parent = LayoutInflater.from(context).inflate(R.layout.browse_row, viewGroup, false)
        return ViewHolder.newInstance(parent)
    }

    override fun onBindViewHolder(viewHolder: ViewHolder, position: Int) {
        val item = videos!![position]
        viewHolder.setTitle(item.title)
        viewHolder.setDescription(item.studio)
        viewHolder.setImage(item.getImage(0), mAppContext)
        viewHolder.mImgView.setOnClickListener { v -> mClickListener.itemClicked(v, item, position) }
        viewHolder.mTextContainer.setOnClickListener { v -> mClickListener.itemClicked(v, item, position) }
    }

    override fun getItemCount(): Int {
        return if (videos == null) 0 else videos!!.size
    }

    class ViewHolder private constructor(private val mParent: View, val mImgView: NetworkImageView, val mTextContainer: View, private val mTitleView: TextView,
                                         private val mDescriptionView: TextView) : RecyclerView.ViewHolder(mParent) {
        private var mImageLoader: ImageLoader? = null
        fun setTitle(title: String?) {
            mTitleView.text = title
        }

        fun setDescription(description: String?) {
            mDescriptionView.text = description
        }

        fun setImage(imgUrl: String?, context: Context?) {
            mImageLoader = getInstance(context!!)
                    ?.imageLoader
            mImageLoader!![imgUrl, ImageLoader.getImageListener(mImgView, 0, 0)]
            mImgView.setImageUrl(imgUrl, mImageLoader)
        }

        fun setOnClickListener(listener: View.OnClickListener?) {
            mParent.setOnClickListener(listener)
        }

        val imageView: ImageView
            get() = mImgView

        companion object {
            fun newInstance(parent: View): ViewHolder {
                val imgView = parent.findViewById<View>(R.id.imageView1) as NetworkImageView
                val titleView = parent.findViewById<View>(R.id.textView1) as TextView
                val descriptionView = parent.findViewById<View>(R.id.textView2) as TextView
                val textContainer = parent.findViewById<View>(R.id.text_container)
                return ViewHolder(parent, imgView, textContainer, titleView, descriptionView)
            }
        }
    }

    fun setData(data: List<MediaItem>?) {
        videos = data
        notifyDataSetChanged()
    }

    interface ItemClickListener {
        fun itemClicked(v: View?, item: MediaItem?, position: Int)
    }

    override fun getItemId(position: Int): Long {
        return super.getItemId(position)
    }

    companion object {
        private const val mAspectRatio = 9f / 16f
    }

    init {
        mAppContext = context.applicationContext
    }
}