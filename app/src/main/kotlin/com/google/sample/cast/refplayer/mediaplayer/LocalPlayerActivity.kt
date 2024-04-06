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
package com.google.sample.cast.refplayer.mediaplayer

import com.google.sample.cast.refplayer.utils.MediaItem.Companion.fromBundle
import com.google.sample.cast.refplayer.utils.CustomVolleyRequest.Companion.getInstance
import com.google.sample.cast.refplayer.utils.Utils.isOrientationPortrait
import com.google.sample.cast.refplayer.utils.Utils.showErrorDialog
import com.google.sample.cast.refplayer.utils.Utils.formatMillis
import com.google.sample.cast.refplayer.utils.Utils.getDisplaySize
import androidx.appcompat.app.AppCompatActivity
import com.android.volley.toolbox.NetworkImageView
import android.os.Bundle
import com.google.sample.cast.refplayer.R
import android.widget.SeekBar.OnSeekBarChangeListener
import android.annotation.SuppressLint
import android.os.Build
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Point
import android.media.MediaPlayer
import android.net.Uri
import android.os.Looper
import com.google.sample.cast.refplayer.settings.CastPreference
import androidx.core.app.ActivityCompat
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.*
import android.widget.*
import androidx.appcompat.widget.Toolbar
import androidx.core.view.ViewCompat
import com.android.volley.toolbox.ImageLoader
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.sample.cast.refplayer.utils.MediaItem
import java.util.*

/**
 * Activity for the local media player.
 */
class LocalPlayerActivity : AppCompatActivity() {
    private var mVideoView: VideoView? = null
    private var mTitleView: TextView? = null
    private var mDescriptionView: TextView? = null
    private var mStartText: TextView? = null
    private var mEndText: TextView? = null
    private var mSeekbar: SeekBar? = null
    private var mPlayPause: ImageView? = null
    private var mLoading: ProgressBar? = null
    private var mControllers: View? = null
    private var mContainer: View? = null
    private var mCoverArt: NetworkImageView? = null
    private var mSeekbarTimer: Timer? = null
    private var mControllersTimer: Timer? = null
    private var mPlaybackState: PlaybackState? = null
    private val looper = Looper.getMainLooper()
    private val mAspectRatio = 72f / 128

    /**
     * 이전 섹션에서 이미 2단계를 완료했습니다.
     * 3단계는 Cast 프레임워크에서 쉽게 할 수 있습니다.
     * 1단계는 한 객체를 다른 객체에 매핑하는 것입니다.
     * MediaInfo는 Cast 프레임워크가 이해할 수 있는 내용이고 MediaItem은 미디어 항목에 관한 앱의 캡슐화입니다.
     * 따라서 MediaItem을 MediaInfo에 쉽게 매핑할 수 있습니다.
     */
    private var mSelectedMedia: MediaItem? = null
    private var mControllersVisible = false
    private var mDuration = 0
    private var mAuthorView: TextView? = null
    private var mPlayCircle: ImageButton? = null
    private var mLocation: PlaybackLocation? = null

    /**
     * indicates whether we are doing a local or a remote playback
     */
    enum class PlaybackLocation {
        LOCAL, REMOTE
    }

    /**
     * List of various states that we can be in
     */
    enum class PlaybackState {
        PLAYING, PAUSED, BUFFERING, IDLE
    }

    private var mCastContext: CastContext? = null
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.player_activity)

        mCastContext = CastContext.getSharedInstance(this) // CastContext 를 다시 가져온다.

        /**
         * LocalPlayerActivity 활동에서 Cast 기기가 연결되거나 연결 해제될 때 알림을 받아 로컬 플레이어로 전환하거나 로컬 플레이어에서 다른 기기로 전환하려고 합니다.
         * 사용자의 휴대기기에서 실행 중인 애플리케이션의 인스턴스뿐만 아니라 다른 휴대기기에서 실행 중인 사용자 또는 다른 사람의 애플리케이션 인스턴스로부터 연결이 방해를 받을 수 있습니다.
         *
         * 현재 활성 세션에는 SessionManager.getCurrentSession()으로 액세스할 수 있습니다. 세션은 사용자의 Cast 대화상자 상호작용에 반응하여 자동으로 생성되고 해제됩니다.
         */
        mCastSession = mCastContext!!.sessionManager.currentCastSession

        setupCastListener()

        loadViews()
        setupControlsCallbacks()
        // see what we need to play and where
        val bundle = intent.extras
        if (bundle != null) {
            mSelectedMedia = fromBundle(intent.getBundleExtra("media"))
            setupActionBar()
            val shouldStartPlayback = bundle.getBoolean("shouldStart")
            val startPosition = bundle.getInt("startPosition", 0)
            mVideoView!!.setVideoURI(Uri.parse(mSelectedMedia!!.url))
            Log.d(TAG, "Setting url of the VideoView to: " + mSelectedMedia!!.url)
            if (shouldStartPlayback) {
                // this will be the case only if we are coming from the
                // CastControllerActivity by disconnecting from a device
                mPlaybackState = PlaybackState.PLAYING
                updatePlaybackLocation(PlaybackLocation.LOCAL)
                updatePlayButton(mPlaybackState)
                if (startPosition > 0) {
                    mVideoView!!.seekTo(startPosition)
                }
                mVideoView!!.start()
                startControllersTimer()
            } else {

                if (mCastSession != null && mCastSession!!.isConnected) { // 만약 세션이 연결되어 있다면
                    updatePlaybackLocation(PlaybackLocation.REMOTE) // 리모트로 설정
                } else {
                    updatePlaybackLocation(PlaybackLocation.LOCAL) // 세션이 연결되어 있지 않다면 local 로 설정
                }

                // we should load the video but pause it
                // and show the album art.
//                updatePlaybackLocation(PlaybackLocation.LOCAL)
                mPlaybackState = PlaybackState.IDLE
                updatePlayButton(mPlaybackState)
            }
        }
        if (mTitleView != null) {
            updateMetadata(true)
        }
    }

    private fun loadRemoteMedia(position: Int, autoPlay: Boolean) {

    }

    /**
     * Cast 프레임워크의 경우 전송 세션에 기기 연결, 실행(또는 연결), 수신기 애플리케이션 연결, 필요한 경우 미디어 제어 채널 초기화 단계가 결합되어 있습니다.
     * 미디어 제어 채널은 Cast 프레임워크가 수신기 미디어 플레이어에서 메시지를 주고받는 방법입니다.
     * 사용자가 전송 버튼에서 기기를 선택하면 전송 세션이 자동으로 시작되고 사용자 연결 해제 시 자동으로 중지됩니다.
     * 네트워크 문제로 인해 수신기 세션에 다시 연결하는 작업도 Cast SDK에서 자동으로 처리됩니다.
     */
    private var mSessionManagerListener: SessionManagerListener<CastSession>? = null
    private var mCastSession: CastSession? = null
    private fun setupCastListener() {
        mSessionManagerListener = object : SessionManagerListener<CastSession> {
            override fun onSessionEnded(p0: CastSession, p1: Int) {
                onApplicationDisconnected()
            }

            override fun onSessionResumed(p0: CastSession, p1: Boolean) {
                onApplicationConnected(p0)
            }

            override fun onSessionStartFailed(p0: CastSession, p1: Int) {
                onApplicationDisconnected()
            }

            override fun onSessionStarted(p0: CastSession, p1: String) {
                onApplicationConnected(p0)
            }

            override fun onSessionResuming(p0: CastSession, p1: String) {}
            override fun onSessionStarting(p0: CastSession) {}
            override fun onSessionEnding(p0: CastSession) {}
            override fun onSessionResumeFailed(p0: CastSession, p1: Int) {}
            override fun onSessionSuspended(p0: CastSession, p1: Int) {}

            private fun onApplicationConnected(castSession: CastSession) {
                mCastSession = castSession // 연결된 세션을 반환?
                if (null != mSelectedMedia) {
                    if (mPlaybackState == PlaybackState.PLAYING) {
                        mVideoView!!.pause()
//                        loadRemoteMedia(mSeekbar!!.progress, true)
                        return
                    } else {
                        mPlaybackState = PlaybackState.IDLE // 현재 로컬에서 재생되는걸 멈추고
                        updatePlaybackLocation(PlaybackLocation.REMOTE) // 리모트로 설정한다
                    }
                }
                updatePlayButton(mPlaybackState)
                invalidateOptionsMenu()
            }

            private fun onApplicationDisconnected() {
                updatePlaybackLocation(PlaybackLocation.LOCAL)
                mPlaybackState = PlaybackState.IDLE
                mLocation = PlaybackLocation.LOCAL
                updatePlayButton(mPlaybackState)
                invalidateOptionsMenu()
            }
        }
    }


    private fun updatePlaybackLocation(location: PlaybackLocation) {
        mLocation = location
        if (location == PlaybackLocation.LOCAL) {
            if (mPlaybackState == PlaybackState.PLAYING
                || mPlaybackState == PlaybackState.BUFFERING
            ) {
                setCoverArtStatus(null)
                startControllersTimer()
            } else {
                stopControllersTimer()
                setCoverArtStatus(mSelectedMedia!!.getImage(0))
            }
        } else {
            stopControllersTimer() // 리모트라면 로컬 영상을 멈추고
            setCoverArtStatus(mSelectedMedia!!.getImage(0)) //커버를 씌우고
            updateControllersVisibility(false) //컨트롤러를 안보이게 한다.
        }
    }

    private fun play(position: Int) {
        startControllersTimer()
        when (mLocation) {
            PlaybackLocation.LOCAL -> {
                mVideoView!!.seekTo(position)
                mVideoView!!.start()
            }

            PlaybackLocation.REMOTE -> {
                mPlaybackState = PlaybackState.BUFFERING
                updatePlayButton(mPlaybackState)
            }

            else -> {}
        }
        restartTrickplayTimer()
    }

    private fun togglePlayback() {
        stopControllersTimer()
        when (mPlaybackState) {
            PlaybackState.PAUSED -> when (mLocation) {
                PlaybackLocation.LOCAL -> {
                    mVideoView!!.start()
                    Log.d(TAG, "Playing locally...")
                    mPlaybackState = PlaybackState.PLAYING
                    startControllersTimer()
                    restartTrickplayTimer()
                    updatePlaybackLocation(PlaybackLocation.LOCAL)
                }

                PlaybackLocation.REMOTE -> finish()
                else -> {}
            }

            PlaybackState.PLAYING -> {
                mPlaybackState = PlaybackState.PAUSED
                mVideoView!!.pause()
            }

            PlaybackState.IDLE -> when (mLocation) {
                PlaybackLocation.LOCAL -> {
                    mVideoView!!.setVideoURI(Uri.parse(mSelectedMedia!!.url))
                    mVideoView!!.seekTo(0)
                    mVideoView!!.start()
                    mPlaybackState = PlaybackState.PLAYING
                    restartTrickplayTimer()
                    updatePlaybackLocation(PlaybackLocation.LOCAL)
                }

                PlaybackLocation.REMOTE -> {}
                else -> {}
            }

            else -> {}
        }
        updatePlayButton(mPlaybackState)
    }

    private fun setCoverArtStatus(url: String?) {
        if (url != null) {
            val mImageLoader = getInstance(this.applicationContext)
                ?.imageLoader
            mImageLoader?.get(url, ImageLoader.getImageListener(mCoverArt, 0, 0))
            mCoverArt!!.setImageUrl(url, mImageLoader)
            mCoverArt!!.visibility = View.VISIBLE
            mVideoView!!.visibility = View.INVISIBLE
        } else {
            mCoverArt!!.visibility = View.GONE
            mVideoView!!.visibility = View.VISIBLE
        }
    }

    private fun stopTrickplayTimer() {
        Log.d(TAG, "Stopped TrickPlay Timer")
        if (mSeekbarTimer != null) {
            mSeekbarTimer!!.cancel()
        }
    }

    private fun restartTrickplayTimer() {
        stopTrickplayTimer()
        mSeekbarTimer = Timer()
        mSeekbarTimer!!.scheduleAtFixedRate(UpdateSeekbarTask(), 100, 1000)
        Log.d(TAG, "Restarted TrickPlay Timer")
    }

    private fun stopControllersTimer() {
        if (mControllersTimer != null) {
            mControllersTimer!!.cancel()
        }
    }

    private fun startControllersTimer() {
        if (mControllersTimer != null) {
            mControllersTimer!!.cancel()
        }
        if (mLocation == PlaybackLocation.REMOTE) {
            return
        }
        mControllersTimer = Timer()
        mControllersTimer!!.schedule(HideControllersTask(), 5000)
    }

    // should be called from the main thread
    private fun updateControllersVisibility(show: Boolean) {
        if (show) {
            supportActionBar!!.show()
            mControllers!!.visibility = View.VISIBLE
        } else {
            if (!isOrientationPortrait(this)) {
                supportActionBar!!.hide()
            }
            mControllers!!.visibility = View.INVISIBLE
        }
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "onPause() was called")
        if (mLocation == PlaybackLocation.LOCAL) {
            if (mSeekbarTimer != null) {
                mSeekbarTimer!!.cancel()
                mSeekbarTimer = null
            }
            if (mControllersTimer != null) {
                mControllersTimer!!.cancel()
            }
            // since we are playing locally, we need to stop the playback of
            // video (if user is not watching, pause it!)
            mVideoView!!.pause()
            mPlaybackState = PlaybackState.PAUSED
            updatePlayButton(PlaybackState.PAUSED)
        }
    }

    override fun onStop() {
        Log.d(TAG, "onStop() was called")
        super.onStop()
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy() is called")
        stopControllersTimer()
        stopTrickplayTimer()
        super.onDestroy()
    }

    override fun onStart() {
        Log.d(TAG, "onStart was called")
        super.onStart()
    }

    override fun onResume() {
        Log.d(TAG, "onResume() was called")
        updatePlaybackLocation(PlaybackLocation.LOCAL)
        super.onResume()
    }

    private inner class HideControllersTask : TimerTask() {
        override fun run() {
            looper.thread.join().apply {
                updateControllersVisibility(false)
                mControllersVisible = false
            }
        }
    }

    private inner class UpdateSeekbarTask : TimerTask() {
        override fun run() {
            looper.thread.join().apply {
                if (mLocation == PlaybackLocation.LOCAL) {
                    val currentPos = mVideoView!!.currentPosition
                    updateSeekbar(currentPos, mDuration)
                }
            }
        }
    }

    private fun setupControlsCallbacks() {
        mVideoView!!.setOnErrorListener { _, what, extra ->
            Log.e(
                TAG, "OnErrorListener.onError(): VideoView encountered an "
                        + "error, what: " + what + ", extra: " + extra
            )
            val msg: String
            msg = if (extra == MediaPlayer.MEDIA_ERROR_TIMED_OUT) {
                getString(R.string.video_error_media_load_timeout)
            } else if (what == MediaPlayer.MEDIA_ERROR_SERVER_DIED) {
                getString(R.string.video_error_server_unaccessible)
            } else {
                getString(R.string.video_error_unknown_error)
            }
            showErrorDialog(this@LocalPlayerActivity, msg)
            mVideoView!!.stopPlayback()
            mPlaybackState = PlaybackState.IDLE
            updatePlayButton(mPlaybackState)
            true
        }
        mVideoView!!.setOnPreparedListener { mp ->
            Log.d(TAG, "onPrepared is reached")
            mDuration = mp.duration
            mEndText!!.text = formatMillis(mDuration)
            mSeekbar!!.max = mDuration
            restartTrickplayTimer()
        }
        mVideoView!!.setOnCompletionListener {
            stopTrickplayTimer()
            Log.d(TAG, "setOnCompletionListener()")
            mPlaybackState = PlaybackState.IDLE
            updatePlayButton(mPlaybackState)
        }
        mVideoView!!.setOnTouchListener { _, _ ->
            if (!mControllersVisible) {
                updateControllersVisibility(true)
            }
            startControllersTimer()
            false
        }
        mSeekbar!!.setOnSeekBarChangeListener(object : OnSeekBarChangeListener {
            override fun onStopTrackingTouch(seekBar: SeekBar) {
                if (mPlaybackState == PlaybackState.PLAYING) {
                    play(seekBar.progress)
                } else if (mPlaybackState != PlaybackState.IDLE) {
                    mVideoView!!.seekTo(seekBar.progress)
                }
                startControllersTimer()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                stopTrickplayTimer()
                mVideoView!!.pause()
                stopControllersTimer()
            }

            override fun onProgressChanged(
                seekBar: SeekBar, progress: Int,
                fromUser: Boolean
            ) {
                mStartText!!.text = formatMillis(progress)
            }
        })
        mPlayPause!!.setOnClickListener {
            if (mLocation == PlaybackLocation.LOCAL) {
                togglePlayback()
            }
        }
    }

    private fun updateSeekbar(position: Int, duration: Int) {
        mSeekbar!!.progress = position
        mSeekbar!!.max = duration
        mStartText!!.text = formatMillis(position)
        mEndText!!.text = formatMillis(duration)
    }

    private fun updatePlayButton(state: PlaybackState?) {
        Log.d(TAG, "Controls: PlayBackState: $state")
        val isConnected = false
        mControllers!!.visibility = if (isConnected) View.GONE else View.VISIBLE
        mPlayCircle!!.visibility = if (isConnected) View.GONE else View.VISIBLE
        when (state) {
            PlaybackState.PLAYING -> {
                mLoading!!.visibility = View.INVISIBLE
                mPlayPause!!.visibility = View.VISIBLE
                mPlayPause!!.setImageDrawable(
                    resources.getDrawable(R.drawable.ic_av_pause_dark)
                )
                mPlayCircle!!.visibility = if (isConnected) View.VISIBLE else View.GONE
            }

            PlaybackState.IDLE -> {
                mPlayCircle!!.visibility = View.VISIBLE
                mControllers!!.visibility = View.GONE
                mCoverArt!!.visibility = View.VISIBLE
                mVideoView!!.visibility = View.INVISIBLE
            }

            PlaybackState.PAUSED -> {
                mLoading!!.visibility = View.INVISIBLE
                mPlayPause!!.visibility = View.VISIBLE
                mPlayPause!!.setImageDrawable(
                    resources.getDrawable(R.drawable.ic_av_play_dark)
                )
                mPlayCircle!!.visibility = if (isConnected) View.VISIBLE else View.GONE
            }

            PlaybackState.BUFFERING -> {
                mPlayPause!!.visibility = View.INVISIBLE
                mLoading!!.visibility = View.VISIBLE
            }

            else -> {}
        }
    }

    @SuppressLint("NewApi")
    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        supportActionBar!!.show()
        if (newConfig.orientation == Configuration.ORIENTATION_LANDSCAPE) {
            window.clearFlags(WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN)
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LOW_PROFILE
            }
            updateMetadata(false)
            mContainer!!.setBackgroundColor(resources.getColor(R.color.black))
        } else {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FORCE_NOT_FULLSCREEN
            )
            window.clearFlags(
                WindowManager.LayoutParams.FLAG_FULLSCREEN
            )
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.ICE_CREAM_SANDWICH) {
                window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_VISIBLE
            }
            updateMetadata(true)
            mContainer!!.setBackgroundColor(resources.getColor(R.color.white))
        }
    }

    private fun updateMetadata(visible: Boolean) {
        val displaySize: Point
        if (!visible) {
            mDescriptionView!!.visibility = View.GONE
            mTitleView!!.visibility = View.GONE
            mAuthorView!!.visibility = View.GONE
            displaySize = getDisplaySize(this)
            val lp = RelativeLayout.LayoutParams(
                displaySize.x,
                displaySize.y + supportActionBar!!.height
            )
            lp.addRule(RelativeLayout.CENTER_IN_PARENT)
            mVideoView!!.layoutParams = lp
            mVideoView!!.invalidate()
        } else {
            mDescriptionView!!.text = mSelectedMedia!!.subTitle
            mTitleView!!.text = mSelectedMedia!!.title
            mAuthorView!!.text = mSelectedMedia!!.studio
            mDescriptionView!!.visibility = View.VISIBLE
            mTitleView!!.visibility = View.VISIBLE
            mAuthorView!!.visibility = View.VISIBLE
            displaySize = getDisplaySize(this)
            val lp = RelativeLayout.LayoutParams(displaySize.x, (displaySize.x * mAspectRatio).toInt())
            lp.addRule(RelativeLayout.BELOW, R.id.toolbar)
            mVideoView!!.layoutParams = lp
            mVideoView!!.invalidate()
        }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.browse, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val intent: Intent
        if (item.itemId == R.id.action_settings) {
            intent = Intent(this@LocalPlayerActivity, CastPreference::class.java)
            startActivity(intent)
        } else if (item.itemId == android.R.id.home) {
            ActivityCompat.finishAfterTransition(this)
        }
        return true
    }

    private fun setupActionBar() {
        val toolbar = findViewById<View>(R.id.toolbar) as Toolbar
        toolbar.title = mSelectedMedia!!.title
        setSupportActionBar(toolbar)
        supportActionBar!!.setDisplayHomeAsUpEnabled(true)
    }

    private fun loadViews() {
        mVideoView = findViewById<View>(R.id.videoView1) as VideoView
        mTitleView = findViewById<View>(R.id.textView1) as TextView
        mDescriptionView = findViewById<View>(R.id.textView2) as TextView
        mDescriptionView!!.movementMethod = ScrollingMovementMethod()
        mAuthorView = findViewById<View>(R.id.textView3) as TextView
        mStartText = findViewById<View>(R.id.startText) as TextView
        mStartText!!.text = formatMillis(0)
        mEndText = findViewById<View>(R.id.endText) as TextView
        mSeekbar = findViewById<View>(R.id.seekBar1) as SeekBar
        mPlayPause = findViewById<View>(R.id.imageView2) as ImageView
        mLoading = findViewById<View>(R.id.progressBar1) as ProgressBar
        mControllers = findViewById(R.id.controllers)
        mContainer = findViewById(R.id.container)
        mCoverArt = findViewById<View>(R.id.coverArtView) as NetworkImageView
        ViewCompat.setTransitionName(mCoverArt!!, getString(R.string.transition_image))
        mPlayCircle = findViewById<View>(R.id.play_circle) as ImageButton
        mPlayCircle!!.setOnClickListener { togglePlayback() }
    }

    companion object {
        private const val TAG = "LocalPlayerActivity"
    }
}