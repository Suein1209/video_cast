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
import android.util.Log
import androidx.loader.content.AsyncTaskLoader
import com.google.sample.cast.refplayer.browser.VideoProvider.Companion.buildMedia
import com.google.sample.cast.refplayer.utils.MediaItem
import java.lang.Exception

class VideoItemLoader(context: Context?, private val mUrl: String) : AsyncTaskLoader<List<MediaItem>?>(context!!) {
    override fun loadInBackground(): List<MediaItem>? {
        return try {
            buildMedia(mUrl)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch media data", e)
            null
        }
    }

    override fun onStartLoading() {
        super.onStartLoading()
        forceLoad()
    }

    /**
     * Handles a request to stop the Loader.
     */
    override fun onStopLoading() {
        // Attempt to cancel the current load task if possible.
        cancelLoad()
    }

    companion object {
        private const val TAG = "VideoItemLoader"
    }
}