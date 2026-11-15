package com.familyvoice.app

import android.annotation.SuppressLint
import android.content.Context
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper

/**
 * 현재 위치를 계속 받아온다 (안드로이드 기본 위치 기능).
 * Pleos FusedLocation SDK가 열리면 그걸로 바꿀 예정.
 */
class LocationTracker(context: Context, private val onLocation: (Location) -> Unit) {

    private val lm = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    var last: Location? = null
        private set

    private val listener = LocationListener { loc ->
        last = loc
        onLocation(loc)
    }

    /** 권한이 있을 때만 호출할 것 */
    @SuppressLint("MissingPermission")
    fun start(): String {
        val providers = lm.getProviders(true)
        if (providers.isEmpty()) return "켜진 위치 제공자가 없어요"
        providers.forEach { p ->
            lm.requestLocationUpdates(p, 1000L, 0f, listener, Looper.getMainLooper())   // 1초마다 (실제 차 GPS도 보통 1초)
            lm.getLastKnownLocation(p)?.let { if (last == null) { last = it; onLocation(it) } }
        }
        return "위치 받는 중 (${providers.joinToString()})"
    }

    fun stop() = lm.removeUpdates(listener)

    companion object {
        /** 두 지점 사이 거리 (미터) */
        fun distanceM(lat1: Double, lng1: Double, lat2: Double, lng2: Double): Float {
            val out = FloatArray(1)
            Location.distanceBetween(lat1, lng1, lat2, lng2, out)
            return out[0]
        }
    }
}
