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

import android.annotation.SuppressLint
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Point
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.text.method.ScrollingMovementMethod
import android.util.Log
import android.view.*
import android.widget.*
import android.widget.SeekBar.OnSeekBarChangeListener
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import com.android.volley.toolbox.ImageLoader
import com.android.volley.toolbox.NetworkImageView
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.common.images.WebImage
import com.google.sample.cast.refplayer.R
import com.google.sample.cast.refplayer.settings.CastPreference
import com.google.sample.cast.refplayer.utils.CustomVolleyRequest.Companion.getInstance
import com.google.sample.cast.refplayer.utils.MediaItem
import com.google.sample.cast.refplayer.utils.MediaItem.Companion.fromBundle
import com.google.sample.cast.refplayer.utils.Utils.formatMillis
import com.google.sample.cast.refplayer.utils.Utils.getDisplaySize
import com.google.sample.cast.refplayer.utils.Utils.isOrientationPortrait
import com.google.sample.cast.refplayer.utils.Utils.showErrorDialog
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
     *
     * 넘겨진 미디어 정보가 들어있다.
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
        mCastSession = mCastContext!!.sessionManager.currentCastSession // 현재 세션을 가져온다.

        setupCastListener() // 세션 리스너를 생성한다.

        loadViews() // 그냥 layout xml 매핑

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
                /**
                 * 보이는거
                 * - 재생 버튼
                 *
                 * 안보이는거
                 * - 컨트롤러
                 * - 아트커버
                 * - 비디오
                 *
                 * 즉, 재생 버튼만 보인다.
                 * 연결은 되어 있으니, 미디어는 멈춘 상태로 두는거
                 */
                mPlaybackState = PlaybackState.IDLE // 우선 멈춤으로 설정?
                updatePlayButton(mPlaybackState) //idle 로 버튼 업데이트
            }
        }
        if (mTitleView != null) {
            updateMetadata(true)
        }
    }

    /**
     * RemoteMediaClient로 영상을 로드 한다.
     * 즉, TV로 영상을 전송한다.
     *   이건 Cast Session 이 이미 설정되어 있을때 가능하다.
     */
    private fun loadRemoteMedia(position: Int, autoPlay: Boolean) {
        if (mCastSession == null) return
        val remoteMediaClient = mCastSession?.remoteMediaClient ?: return
        remoteMediaClient.load(
            MediaLoadRequestData.Builder()
                .setMediaInfo(buildMediaInfo())
                .setAutoplay(autoPlay)
                .setCurrentTime(position.toLong()) // 시작하는 영상의 위치? Progress
                .build()
        )
    }

    /**
     * 메타 정보 인스턴스를 반환한다.
     *
     * 정보 내용
     * - 타이틀이 무언가
     * - 서브 타이틀이 무언가
     * - 커버 이미지 2개?(왜 2개인지는 모름)
     * - 스트리밍 타입이 어떤가?(여기서는 버퍼드 타입, LIVE 타입도 있긴하다)
     * - 컨텐츠의 타입이 어떤가?(여긴 mp4)
     * - 영상의 길이가 어떤가
     */
    private fun buildMediaInfo(): MediaInfo? {
        val movieMetadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_MOVIE)
        mSelectedMedia?.studio?.let { movieMetadata.putString(MediaMetadata.KEY_SUBTITLE, it) }
        mSelectedMedia?.title?.let { movieMetadata.putString(MediaMetadata.KEY_TITLE, it) }
        movieMetadata.addImage(WebImage(Uri.parse(mSelectedMedia!!.getImage(0))))
        movieMetadata.addImage(WebImage(Uri.parse(mSelectedMedia!!.getImage(1))))
        return mSelectedMedia!!.url?.let {
            MediaInfo.Builder(it)
                .setStreamType(MediaInfo.STREAM_TYPE_BUFFERED) // 버퍼드 타입, LIVE 타입도 있다.
                .setContentType("videos/mp4") // 컨텐츠 타입 설정
                .setMetadata(movieMetadata) // 메타 데이터 설정
                .setStreamDuration((mSelectedMedia!!.duration * 1000).toLong())
                .build()
        }
    }

    /**
     * Cast 프레임워크의 경우 전송 세션에 기기 연결, 실행(또는 연결), 수신기 애플리케이션 연결, 필요한 경우 미디어 제어 채널 초기화 단계가 결합되어 있습니다.
     * 미디어 제어 채널은 Cast 프레임워크가 수신기 미디어 플레이어에서 메시지를 주고받는 방법입니다.
     * 사용자가 전송 버튼에서 기기를 선택하면 전송 세션이 자동으로 시작되고 사용자 연결 해제 시 자동으로 중지됩니다.
     * 네트워크 문제로 인해 수신기 세션에 다시 연결하는 작업도 Cast SDK에서 자동으로 처리됩니다.
     *
     * 로컬에서 세션 리스너를 추가한다.
     */
    private var mSessionManagerListener: SessionManagerListener<CastSession>? = null
    private var mCastSession: CastSession? = null
    private fun setupCastListener() {
        //세션 리스너 생성
        mSessionManagerListener = object : SessionManagerListener<CastSession> {
            override fun onSessionEnded(p0: CastSession, p1: Int) {
                onApplicationDisconnected() // 세션이 끝나면 disconnect
            }

            override fun onSessionResumed(p0: CastSession, p1: Boolean) {
                onApplicationConnected(p0) // 세션이 시작하면 다시 연결
            }

            override fun onSessionStartFailed(p0: CastSession, p1: Int) {
                onApplicationDisconnected() // 세션이 끝나면 disconnect
            }

            override fun onSessionStarted(p0: CastSession, p1: String) {
                onApplicationConnected(p0)// 세션이 시작하면 연결
            }

            override fun onSessionResuming(p0: CastSession, p1: String) {}
            override fun onSessionStarting(p0: CastSession) {}
            override fun onSessionEnding(p0: CastSession) {}
            override fun onSessionResumeFailed(p0: CastSession, p1: Int) {}
            override fun onSessionSuspended(p0: CastSession, p1: Int) {}

            private fun onApplicationConnected(castSession: CastSession) {
                //만약 연결이 되었다면!
                mCastSession = castSession // 연결된 세션을 설정
                if (null != mSelectedMedia) { // 넘어온 미디어 정보 확인
                    if (mPlaybackState == PlaybackState.PLAYING) { // 만약 Play 중이라면
                        mVideoView!!.pause() // 비디오를 멈춘다.(연결되었으니 폰에서는 보이지 않도록 하는거)
                        loadRemoteMedia(mSeekbar!!.progress, true)
                        return
                    } else {
                        //만약 Play 중이 아니라면 버퍼링 중이겠지?
                        mPlaybackState = PlaybackState.IDLE // 현재 로컬에서 재생되는걸 멈추고
                        updatePlaybackLocation(PlaybackLocation.REMOTE) // 리모트로 설정한다
                    }
                }
                updatePlayButton(mPlaybackState) // Playback 상태에 따라서 컨트롤 버튼을 상태 업데이트 하고
                invalidateOptionsMenu() // 메뉴 업데이트
            }

            private fun onApplicationDisconnected() {
                updatePlaybackLocation(PlaybackLocation.LOCAL) // 커버랑, 컨트롤러 노출여부 설정 / 로컬이면 재생 여부에 따라 커버랑 컨트롤러 제어
                mPlaybackState = PlaybackState.IDLE // State 는 Idle 로... 아마도 멈춤?
                mLocation = PlaybackLocation.LOCAL // location 은 로컬로 (끊겼으니 TV에서는 안보이고 폰에서 재생하는 거니깐)
                updatePlayButton(mPlaybackState) // 재생 상태에 따라서 컨트롤러 버튼설정
                invalidateOptionsMenu() // 메뉴 업데이트
            }
        }
    }


    /**
     * 로컬
     * -- 재생중 일때 커버 아트 없애고, 타이머 시작
     * -- 멈춰있을 때 커버 아트 설정하고, 타이머 멈춤
     *
     * 리모트
     * -- 커버아트를 씌운다. / 타이머를 멈추고 컨트롤러를 보이지 않도록 한다.
     */
    private fun updatePlaybackLocation(location: PlaybackLocation) {
        mLocation = location
        if (location == PlaybackLocation.LOCAL) {
            if (mPlaybackState == PlaybackState.PLAYING
                || mPlaybackState == PlaybackState.BUFFERING
            ) {
                //playback 상태가 재생중이거나 버퍼링 상태면
                setCoverArtStatus(null) //로컬의 아트커버 update
                startControllersTimer()
            } else {
                //playback이 멈춰져 있는 상태이면
                stopControllersTimer() //타이머를 멈춘다. 즉, 컨트롤러 계속 보이게함
                setCoverArtStatus(mSelectedMedia!!.getImage(0)) // 아트커버를 selected media 에서 가져온다?
            }
        } else {
            stopControllersTimer() //타이머를 멈춘다. 즉, 컨트롤러 계속 보이게함
            setCoverArtStatus(mSelectedMedia!!.getImage(0)) //커버를 씌우고
            updateControllersVisibility(false) //컨트롤러를 안보이게 한다.
        }
    }

    /**
     * 모바일에서 seek bar 를 이동하고 action up 했을때 호출된다.
     */
    private fun play(position: Int) {
        //말이 스타트 컨트롤이고 만약 리모트이면 그냥 timer cancel 만 하고 만다.
        startControllersTimer()
        when (mLocation) {
            PlaybackLocation.LOCAL -> {
                //로컬에서는 바로 video view 에 seek 포지셔을 설정해서 재생한다.
                mVideoView!!.seekTo(position)
                mVideoView!!.start()
            }

            PlaybackLocation.REMOTE -> {
                //리모트 일경우 UI를 버퍼링 상태로 우선 바꾼다.
                mPlaybackState = PlaybackState.BUFFERING

                /**
                 * BUFFERING 상태
                 * - 컨트롤의 재생 상태 Pause 버튼 비노출
                 * - 로딩 프로그래스 노출
                 */
                updatePlayButton(mPlaybackState)

                //리모트 컨트롤 클라이언트로 Seek 포지션을 새로 설정한다. 그러면 resume 으로 들어오나?
                mCastSession!!.remoteMediaClient?.seek(position.toLong())
            }

            else -> {}
        }

        //Seek Bar Ui 업데이트 timer 를 시작한다.
        restartTrickplayTimer()
    }

    /**
     * 하단 재생 상태 버튼에서 클릭 됐을때
     * 그리고 가운데 재생 상태 버튼을 클릭했을때 호출된다.
     *
     * - 두 군데서 클릭의 toggle 을 설정한다.
     */
    private fun togglePlayback() {
        stopControllersTimer() // 컨트롤 5초뒤 비노출하는 타이머 멈춤
        when (mPlaybackState) {
            PlaybackState.PAUSED -> when (mLocation) {
                //만약 Pause 를 눌렀다면 즉, 재생 하라는 뜻
                PlaybackLocation.LOCAL -> {
                    mVideoView!!.start() //비디오 재생하고
                    Log.d(TAG, "Playing locally...")
                    mPlaybackState = PlaybackState.PLAYING //state Playing 으로 바꾸고
                    startControllersTimer() //컨트롤 타이머 다시 시작하고
                    restartTrickplayTimer() // 시크바 UI 업데이트 타이머 시작하고
                    updatePlaybackLocation(PlaybackLocation.LOCAL) // location 로컬로 설정하고
                }

                PlaybackLocation.REMOTE -> finish() // 리모트 일경우 그냥 화면 finish ???
                else -> {}
            }

            PlaybackState.PLAYING -> {
                //현재 재생에서 눌렀다면, 즉 pause 시켜라!
                mPlaybackState = PlaybackState.PAUSED // 상태 바꾸고
                mVideoView!!.pause() // video view 멈추고... 이래도 cast session 의 비디오도 멈추나?
            }

            PlaybackState.IDLE -> when (mLocation) {
                //IDLE 상태라면?, 즉, 비디오 설정이 안된상태에서 재생 버튼을 눌렀다면!, 재생하라는 뜻이다.
                PlaybackLocation.LOCAL -> {
                    mVideoView!!.setVideoURI(Uri.parse(mSelectedMedia!!.url)) // URI 설정
                    mVideoView!!.seekTo(0) // Seekbar 처음으로 이동
                    mVideoView!!.start() // 영상 재생
                    mPlaybackState = PlaybackState.PLAYING // 상태 재생중으로 바꾸고
                    restartTrickplayTimer() //시크바 UI 업데이트 시작
                    updatePlaybackLocation(PlaybackLocation.LOCAL) //그리고 로컬로 설정
                }

                PlaybackLocation.REMOTE -> {
                    if (mCastSession != null && mCastSession!!.isConnected) {
                        //cast session이 연결되어 있다면
                        loadRemoteMedia(mSeekbar!!.progress, true) // 영상을 로딩한다.
                    }
                }
                else -> {}
            }

            else -> {}
        }

        //컨트롤 버튼들 설정()
        updatePlayButton(mPlaybackState)
    }

    private fun setCoverArtStatus(url: String?) {
        if (url != null) {
            val mImageLoader = getInstance(this.applicationContext)
                ?.imageLoader

            //아크커버 이미지를 다운 받고
            mImageLoader?.get(url, ImageLoader.getImageListener(mCoverArt, 0, 0))
            //커버 ImageView에 설정한다.
            mCoverArt!!.setImageUrl(url, mImageLoader)
            //아트커버를 노출시키고
            mCoverArt!!.visibility = View.VISIBLE
            //Play되고 있는 VideoView를 비노출시킨다.
            mVideoView!!.visibility = View.INVISIBLE
        } else {
            //URL이 문제가 있다면 아트커버 없애고, VideoView 를 노출
            mCoverArt!!.visibility = View.GONE
            mVideoView!!.visibility = View.VISIBLE
        }
    }

    /**
     * seek ui update timer 정지
     */
    private fun stopTrickplayTimer() {
        Log.d(TAG, "Stopped TrickPlay Timer")
        if (mSeekbarTimer != null) {
            mSeekbarTimer!!.cancel()
        }
    }

    /**
     * seek ui update timer 재 시작
     */
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

    /**
     * 하단 컨트롤러가 5초 뒤에 사라지게 하는 Timer 를 시작한다.
     * - 만약 리모트이면 그냥 Timer 만 캔슬하고 만다.
     */
    private fun startControllersTimer() {
        if (mControllersTimer != null) {
            mControllersTimer!!.cancel() // 우선 타이머 멈추고
        }
        if (mLocation == PlaybackLocation.REMOTE) {
            //현재 리모트 이면 타이머는 무시
            return
        }
        mControllersTimer = Timer()
        mControllersTimer!!.schedule(HideControllersTask(), 5000) // 5초뒤에 컨트롤러를 안보이도록 한다.
    }

    // should be called from the main thread
    private fun updateControllersVisibility(show: Boolean) {
        if (show) {
            supportActionBar!!.show() //action bar 보이고
            mControllers!!.visibility = View.VISIBLE // 컨트롤러를 보이게 한다.
        } else {
            if (!isOrientationPortrait(this)) { // 가로 모드라면
                supportActionBar!!.hide() // action bar를 보이지 않도록 한다.
            }
            mControllers!!.visibility = View.INVISIBLE // 컨트롤러를 보이지 않게 한다.
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

        mCastContext!!.sessionManager.removeSessionManagerListener(
            mSessionManagerListener!!, CastSession::class.java)
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

        //캐스팅 session listener 를 추가한다. 연결이라고 해야하나
        mCastContext!!.sessionManager.addSessionManagerListener(mSessionManagerListener!!, CastSession::class.java)

        //세션이 이미 연결되어 있다면
        if (mCastSession != null && mCastSession!!.isConnected) {
            updatePlaybackLocation(PlaybackLocation.REMOTE) // 케이션을 원격으로 설정한다.
        } else {
            updatePlaybackLocation(PlaybackLocation.LOCAL) // 로컬은 당연히 로컬
        }
        super.onResume()
    }

    /**
     * delay 시간 뒤에 안보이도록 한다. 컨트롤러를
     */
    private inner class HideControllersTask : TimerTask() {
        override fun run() {
            looper.thread.join().apply {
                updateControllersVisibility(false) //컨트롤러를 보이지 않도록 한다?
                mControllersVisible = false
            }
        }
    }

    /**
     * 몇초 단위로 Seek bar ui 를 업데이트 하기 위한 Timer
     */
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
                //Seek bar 에서 손을 떼었을때
                if (mPlaybackState == PlaybackState.PLAYING) {
                    play(seekBar.progress) // 플레이 중이라면 로컬이든 리모트이든 seek position 을 재 설정한다.
                } else if (mPlaybackState != PlaybackState.IDLE) {
                    mVideoView!!.seekTo(seekBar.progress) // IDLE 이면 로컬 영상에만 seek position 을 설정한다.
                }

                //하단 컨트롤러 5초뒤에 사라지게 하는 Timer 시작 / 리모트일 경우 타이머만 캔슬한다.
                startControllersTimer()
            }

            override fun onStartTrackingTouch(seekBar: SeekBar) {
                //Seek bar 에서 손을 눌렀을때

                //Seek bar 에 주기적으로 seek position update 하던 타이머 멈춤
                stopTrickplayTimer()

                //로컬 비디어 영상 멈춤
                mVideoView!!.pause()

                //하단 컨트롤러 5초뒤에 사라지게 하는 Timer 정지 / 리모트일 경우 타이머만 캔슬한다.
                stopControllersTimer()
            }

            override fun onProgressChanged(
                seekBar: SeekBar, progress: Int,
                fromUser: Boolean
            ) {
                // Seek bar를 사용자가 이동주

                //이때는 그냥 seek bar 위치만 textview 에 업데이트 한다.
                mStartText!!.text = formatMillis(progress)
            }
        })

        //하단 컨트롤 재생 상태 버튼 클릭시
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

    /**
     * 컨트롤 버튼들 설정(하단, 가운데 모두 포함이다.)
     *
     * PLAYING 상태
     * - 로딩 프로그래스 비노출
     * - 컨트롤의 재생 상태 Pause 버튼 노출
     * - 가운데 Play 버튼 연결 여부에 따라서 노출 혹은 비노출
     *
     * PAUSED 상태
     * - 로딩 프로그래스 비노출
     * - 컨트롤의 재생 상태 Play 버튼 노출
     * - 가운데 Play 버튼 연결 여부에 따라서 노출 혹은 비노출
     *
     * IDLE 상태
     * - Play 버튼 노출
     * - 커버아트 노출
     * - 컨트롤 버튼 비노출
     * - 로컬 VideoView 비노출
     *
     * BUFFERING 상태
     * - 컨트롤의 재생 상태 Pause 버튼 비노출
     * - 로딩 프로그래스 노출
     */
    private fun updatePlayButton(state: PlaybackState?) {
        Log.d(TAG, "Controls: PlayBackState: $state")
        val isConnected = (mCastSession != null
                && (mCastSession!!.isConnected || mCastSession!!.isConnecting))
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