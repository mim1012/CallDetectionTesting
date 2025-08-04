package com.kakao.taxi.test

import android.content.Context
import android.content.SharedPreferences
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.util.*

class FilterSettingsActivity : AppCompatActivity() {
    
    private lateinit var prefs: SharedPreferences
    private lateinit var seekBarMinAmount: SeekBar
    private lateinit var tvMinAmountValue: TextView
    private lateinit var seekBarMaxDistance: SeekBar
    private lateinit var tvMaxDistanceValue: TextView
    private lateinit var btnSaveSettings: Button
    
    // 체크박스들
    private lateinit var cbGangnam: CheckBox
    private lateinit var cbSeocho: CheckBox
    private lateinit var cbSongpa: CheckBox
    private lateinit var cbGangdong: CheckBox
    private lateinit var cbYongsan: CheckBox
    private lateinit var cbJongno: CheckBox
    private lateinit var cbJung: CheckBox
    private lateinit var cbMapo: CheckBox
    
    private lateinit var cbAutoAccept: CheckBox
    private lateinit var cbPriorityHigh: CheckBox
    private lateinit var cbAvoidTraffic: CheckBox
    
    private val numberFormat = NumberFormat.getNumberInstance(Locale.KOREA)
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_filter_settings)
        
        prefs = getSharedPreferences("filter_settings", Context.MODE_PRIVATE)
        
        initViews()
        loadSettings()
        setupListeners()
    }
    
    private fun initViews() {
        seekBarMinAmount = findViewById(R.id.seekBarMinAmount)
        tvMinAmountValue = findViewById(R.id.tvMinAmountValue)
        seekBarMaxDistance = findViewById(R.id.seekBarMaxDistance)
        tvMaxDistanceValue = findViewById(R.id.tvMaxDistanceValue)
        btnSaveSettings = findViewById(R.id.btnSaveSettings)
        
        // 지역 체크박스
        cbGangnam = findViewById(R.id.cbGangnam)
        cbSeocho = findViewById(R.id.cbSeocho)
        cbSongpa = findViewById(R.id.cbSongpa)
        cbGangdong = findViewById(R.id.cbGangdong)
        cbYongsan = findViewById(R.id.cbYongsan)
        cbJongno = findViewById(R.id.cbJongno)
        cbJung = findViewById(R.id.cbJung)
        cbMapo = findViewById(R.id.cbMapo)
        
        // 옵션 체크박스
        cbAutoAccept = findViewById(R.id.cbAutoAccept)
        cbPriorityHigh = findViewById(R.id.cbPriorityHigh)
        cbAvoidTraffic = findViewById(R.id.cbAvoidTraffic)
    }
    
    private fun loadSettings() {
        // 최소 금액 (기본값: 5,000원)
        val minAmount = prefs.getInt("min_amount", 5000)
        seekBarMinAmount.progress = minAmount
        tvMinAmountValue.text = "${numberFormat.format(minAmount)}원"
        
        // 최대 거리 (기본값: 3.0km)
        val maxDistance = prefs.getFloat("max_distance", 3.0f)
        seekBarMaxDistance.progress = (maxDistance * 10).toInt()
        tvMaxDistanceValue.text = "${maxDistance}km"
        
        // 선호 지역
        val preferredAreas = prefs.getStringSet("preferred_areas", 
            setOf("강남구", "서초구", "송파구")) ?: setOf()
        
        cbGangnam.isChecked = preferredAreas.contains("강남구")
        cbSeocho.isChecked = preferredAreas.contains("서초구")
        cbSongpa.isChecked = preferredAreas.contains("송파구")
        cbGangdong.isChecked = preferredAreas.contains("강동구")
        cbYongsan.isChecked = preferredAreas.contains("용산구")
        cbJongno.isChecked = preferredAreas.contains("종로구")
        cbJung.isChecked = preferredAreas.contains("중구")
        cbMapo.isChecked = preferredAreas.contains("마포구")
        
        // 추가 옵션
        cbAutoAccept.isChecked = prefs.getBoolean("auto_accept", true)
        cbPriorityHigh.isChecked = prefs.getBoolean("priority_high", false)
        cbAvoidTraffic.isChecked = prefs.getBoolean("avoid_traffic", false)
    }
    
    private fun setupListeners() {
        // 최소 금액 슬라이더
        seekBarMinAmount.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                tvMinAmountValue.text = "${numberFormat.format(progress)}원"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 최대 거리 슬라이더
        seekBarMaxDistance.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val distance = progress / 10.0f
                tvMaxDistanceValue.text = "${distance}km"
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })
        
        // 저장 버튼
        btnSaveSettings.setOnClickListener {
            saveSettings()
        }
    }
    
    private fun saveSettings() {
        // 선택된 지역 수집
        val preferredAreas = mutableSetOf<String>()
        if (cbGangnam.isChecked) preferredAreas.add("강남구")
        if (cbSeocho.isChecked) preferredAreas.add("서초구")
        if (cbSongpa.isChecked) preferredAreas.add("송파구")
        if (cbGangdong.isChecked) preferredAreas.add("강동구")
        if (cbYongsan.isChecked) preferredAreas.add("용산구")
        if (cbJongno.isChecked) preferredAreas.add("종로구")
        if (cbJung.isChecked) preferredAreas.add("중구")
        if (cbMapo.isChecked) preferredAreas.add("마포구")
        
        // SharedPreferences에 저장
        prefs.edit().apply {
            putInt("min_amount", seekBarMinAmount.progress)
            putFloat("max_distance", seekBarMaxDistance.progress / 10.0f)
            putStringSet("preferred_areas", preferredAreas)
            putBoolean("auto_accept", cbAutoAccept.isChecked)
            putBoolean("priority_high", cbPriorityHigh.isChecked)
            putBoolean("avoid_traffic", cbAvoidTraffic.isChecked)
            apply()
        }
        
        // 서버로 설정 전송 (RemoteControlService가 실행 중이면)
        sendSettingsToServer()
        
        Toast.makeText(this, "필터 설정이 저장되었습니다", Toast.LENGTH_SHORT).show()
        finish()
    }
    
    private fun sendSettingsToServer() {
        // RemoteControlService로 브로드캐스트 전송
        val intent = android.content.Intent("com.kakao.taxi.FILTER_UPDATE")
        intent.putExtra("min_amount", seekBarMinAmount.progress)
        intent.putExtra("max_distance", seekBarMaxDistance.progress / 10.0f)
        
        val preferredAreas = mutableListOf<String>()
        if (cbGangnam.isChecked) preferredAreas.add("강남구")
        if (cbSeocho.isChecked) preferredAreas.add("서초구")
        if (cbSongpa.isChecked) preferredAreas.add("송파구")
        if (cbGangdong.isChecked) preferredAreas.add("강동구")
        if (cbYongsan.isChecked) preferredAreas.add("용산구")
        if (cbJongno.isChecked) preferredAreas.add("종로구")
        if (cbJung.isChecked) preferredAreas.add("중구")
        if (cbMapo.isChecked) preferredAreas.add("마포구")
        
        intent.putStringArrayListExtra("preferred_areas", ArrayList(preferredAreas))
        intent.putExtra("auto_accept", cbAutoAccept.isChecked)
        intent.putExtra("priority_high", cbPriorityHigh.isChecked)
        intent.putExtra("avoid_traffic", cbAvoidTraffic.isChecked)
        
        sendBroadcast(intent)
    }
    
    // 현재 설정을 JSON으로 변환 (서버 전송용)
    fun getSettingsAsJson(): JSONObject {
        val json = JSONObject()
        json.put("min_amount", seekBarMinAmount.progress)
        json.put("max_distance", seekBarMaxDistance.progress / 10.0f)
        
        val areas = JSONArray()
        if (cbGangnam.isChecked) areas.put("강남구")
        if (cbSeocho.isChecked) areas.put("서초구")
        if (cbSongpa.isChecked) areas.put("송파구")
        if (cbGangdong.isChecked) areas.put("강동구")
        if (cbYongsan.isChecked) areas.put("용산구")
        if (cbJongno.isChecked) areas.put("종로구")
        if (cbJung.isChecked) areas.put("중구")
        if (cbMapo.isChecked) areas.put("마포구")
        json.put("preferred_areas", areas)
        
        json.put("auto_accept", cbAutoAccept.isChecked)
        json.put("priority_high", cbPriorityHigh.isChecked)
        json.put("avoid_traffic", cbAvoidTraffic.isChecked)
        
        return json
    }
}