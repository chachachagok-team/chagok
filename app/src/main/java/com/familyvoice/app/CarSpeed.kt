package com.familyvoice.app

import android.content.Context

/**
 * 차 속도 읽기 (안드로이드 차량 표준 android.car).
 *
 * Pleos Vehicle SDK 에는 "현재 속도" API 가 없다 (기어·와이퍼·주행거리만 열어 줬다).
 * 그런데 에뮬레이터 계기판은 속도를 실시간으로 보여 주므로, 값 자체는 차 서비스 안에 있다.
 * 그래서 안드로이드 차량 표준 통로로 직접 읽어 본다.
 *
 * 그 통로가 없는 기기에서도 빌드·실행이 깨지지 않도록 전부 리플렉션으로 부르고,
 * 한 번이라도 실패하면 조용히 꺼진다 (그러면 앱이 만든 속도 곡선을 쓴다).
 */
object CarSpeed {

    private const val PERF_VEHICLE_SPEED = 291504647   // 안드로이드 차량 표준 속성 번호 (m/s)
    private var manager: Any? = null
    private var getFloat: java.lang.reflect.Method? = null
    private var tried = false
    private var dead = false

    /** 준비. 한 번만 시도하고, 안 되면 다시 시도하지 않는다 */
    private fun prepare(context: Context) {
        if (tried) return
        tried = true
        try {
            val carClass = Class.forName("android.car.Car")
            val car = carClass.getMethod("createCar", Context::class.java)
                .invoke(null, context.applicationContext) ?: run { dead = true; return }
            val m = carClass.getMethod("getCarManager", String::class.java).invoke(car, "property")
                ?: run { dead = true; return }
            manager = m
            getFloat = m.javaClass.getMethod(
                "getFloatProperty", Int::class.javaPrimitiveType, Int::class.javaPrimitiveType
            )
        } catch (_: Throwable) {
            dead = true
        }
    }

    /** 지금 속도(km/h). 읽을 수 없으면 null */
    fun kmh(context: Context): Int? {
        if (dead) return null
        prepare(context)
        val m = manager ?: return null
        val f = getFloat ?: return null
        return try {
            val ms = f.invoke(m, PERF_VEHICLE_SPEED, 0) as? Float ?: return null
            (kotlin.math.abs(ms) * 3.6f).toInt()
        } catch (_: Throwable) {
            dead = true      // 권한이 없거나 속성이 없는 차 → 다시 시도하지 않는다
            null
        }
    }

    /** 왜 안 되는지 확인용 (테스트 화면) */
    fun status(context: Context): String {
        val v = kmh(context)
        return when {
            v != null -> "android.car 로 속도 읽힘: ${v}km/h"
            dead -> "android.car 로는 못 읽어요 (통로 없음 또는 권한 없음) → 앱이 만든 속도를 씁니다"
            else -> "아직 값이 없어요"
        }
    }
}
