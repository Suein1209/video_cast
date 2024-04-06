package com.google.sample.cast.refplayer

import android.content.Context
import com.google.android.gms.cast.framework.CastOptions
import com.google.android.gms.cast.framework.OptionsProvider
import com.google.android.gms.cast.framework.SessionProvider

/**
 * Cast 프레임워크에는 모든 전송 상호작용을 조정하는 전역 싱글톤 객체 CastContext가 있습니다.
 * OptionsProvider 인터페이스를 구현하여 CastContext 싱글톤을 초기화하는 데 필요한 CastOptions를 제공해야 합니다.
 *
 * 1. 가장 중요한 옵션은 Cast 기기 검색 결과를 필터링하고
 * 2. Cast 세션이 시작될 때 수신기 애플리케이션을 실행하는 데 사용되는 수신기 애플리케이션 ID입니다.
 */
class CastOptionsProvider : OptionsProvider {
    override fun getCastOptions(context: Context): CastOptions {
        return CastOptions.Builder()
            .setReceiverApplicationId(context.getString(R.string.app_id))
            .build()
    }

    override fun getAdditionalSessionProviders(p0: Context): MutableList<SessionProvider>? {
        return null
    }
}