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
package com.google.sample.cast.refplayer

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.content.Intent
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import com.google.android.gms.cast.framework.CastButtonFactory
import com.google.android.gms.cast.framework.CastContext
import com.google.sample.cast.refplayer.settings.CastPreference

class VideoBrowserActivity : AppCompatActivity() {
    private var mToolbar: Toolbar? = null

    /**
     * Cast 프레임워크에는 모든 전송 상호작용을 조정하는 전역 싱글톤 객체 CastContext
     */
    private var mCastContext: CastContext? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.video_browser)
        setupActionBar()

        mCastContext = CastContext.getSharedInstance(this)
    }

    private fun setupActionBar() {
        mToolbar = findViewById<View>(R.id.toolbar) as Toolbar
        mToolbar?.setTitle(R.string.app_name)
        setSupportActionBar(mToolbar)
    }

    private var mediaRouteMenuItem: MenuItem? = null
    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        super.onCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.browse, menu)

        /**
         * 앱의 작업 모음에 전송 버튼이 표시되며, 이 버튼을 클릭하면 로컬 네트워크에 Cast 기기가 표시됩니다.
         * 기기 검색은 CastContext에서 자동으로 관리됩니다.
         * Cast 기기를 선택하면 샘플 수신기 앱이 Cast 기기에 로드됩니다.
         * 탐색 활동과 로컬 플레이어 활동을 오가며 둘러볼 수 있으며 전송 버튼 상태는 동기화된 상태로 유지됩니다.
         */
        /**
         * - 사용자가 전송 버튼에서 기기를 선택하면 전송 세션이 자동으로 시작
         * - 사용자 연결 해제 시 자동으로 중지
         * - 네트워크 문제로 인해 수신기 세션에 다시 연결하는 작업도 Cast SDK에서 자동으로 처리
         */
        mediaRouteMenuItem = CastButtonFactory.setUpMediaRouteButton(applicationContext, menu, R.id.media_route_menu_item)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val i: Intent
        when (item.itemId) {
            R.id.action_settings -> {
                i = Intent(this@VideoBrowserActivity, CastPreference::class.java)
                startActivity(i)
            }
        }
        return true
    }

    override fun onDestroy() {
        Log.d(TAG, "onDestroy is called")
        super.onDestroy()
    }

    companion object {
        private const val TAG = "VideoBrowserActivity"
    }
}