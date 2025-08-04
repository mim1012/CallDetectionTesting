package com.kakao.taxi.test.module

import android.content.Context
import android.content.SharedPreferences

/**
 * 필터 설정 저장 및 로드
 */
class FilterSettings(context: Context) {
    companion object {
        private const val PREF_NAME = "kakao_taxi_filter"
        private const val KEY_MIN_DISTANCE = "min_distance"
        private const val KEY_MAX_DISTANCE = "max_distance"
        private const val KEY_MIN_AMOUNT = "min_amount"
        private const val KEY_MAX_AMOUNT = "max_amount"
        private const val KEY_KEYWORDS = "keywords"
        
        // 기본값
        private const val DEFAULT_MIN_DISTANCE = 0.5f
        private const val DEFAULT_MAX_DISTANCE = 10.0f
        private const val DEFAULT_MIN_AMOUNT = 5000
        private const val DEFAULT_MAX_AMOUNT = 50000
    }
    
    private val prefs: SharedPreferences = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
    
    /**
     * 필터 조건 저장
     */
    fun saveFilterCriteria(criteria: FilterCriteria) {
        prefs.edit().apply {
            criteria.minDistance?.let { putFloat(KEY_MIN_DISTANCE, it) }
            criteria.maxDistance?.let { putFloat(KEY_MAX_DISTANCE, it) }
            criteria.minAmount?.let { putInt(KEY_MIN_AMOUNT, it) }
            criteria.maxAmount?.let { putInt(KEY_MAX_AMOUNT, it) }
            criteria.keywords?.let { 
                putString(KEY_KEYWORDS, it.joinToString(","))
            }
            apply()
        }
    }
    
    /**
     * 필터 조건 로드
     */
    fun loadFilterCriteria(): FilterCriteria {
        return FilterCriteria(
            minDistance = if (prefs.contains(KEY_MIN_DISTANCE)) 
                prefs.getFloat(KEY_MIN_DISTANCE, DEFAULT_MIN_DISTANCE) else null,
            maxDistance = if (prefs.contains(KEY_MAX_DISTANCE)) 
                prefs.getFloat(KEY_MAX_DISTANCE, DEFAULT_MAX_DISTANCE) else null,
            minAmount = if (prefs.contains(KEY_MIN_AMOUNT)) 
                prefs.getInt(KEY_MIN_AMOUNT, DEFAULT_MIN_AMOUNT) else null,
            maxAmount = if (prefs.contains(KEY_MAX_AMOUNT)) 
                prefs.getInt(KEY_MAX_AMOUNT, DEFAULT_MAX_AMOUNT) else null,
            keywords = prefs.getString(KEY_KEYWORDS, null)?.split(",")?.filter { it.isNotEmpty() }
        )
    }
    
    /**
     * 기본 필터 설정
     */
    fun getDefaultFilterCriteria(): FilterCriteria {
        return FilterCriteria(
            minDistance = DEFAULT_MIN_DISTANCE,
            maxDistance = DEFAULT_MAX_DISTANCE,
            minAmount = DEFAULT_MIN_AMOUNT,
            maxAmount = DEFAULT_MAX_AMOUNT,
            keywords = null
        )
    }
}