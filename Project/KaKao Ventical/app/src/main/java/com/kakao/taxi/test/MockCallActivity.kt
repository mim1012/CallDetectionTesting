package com.kakao.taxi.test

import android.graphics.Color
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import java.util.*

class MockCallActivity : AppCompatActivity() {
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create mock call screen layout
        val layout = ConstraintLayout(this).apply {
            setBackgroundColor(Color.WHITE)
            layoutParams = ConstraintLayout.LayoutParams(
                ConstraintLayout.LayoutParams.MATCH_PARENT,
                ConstraintLayout.LayoutParams.MATCH_PARENT
            )
        }
        
        // Call info container
        val infoContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            id = View.generateViewId()
            setPadding(32, 100, 32, 32)
        }
        
        // Distance text
        val distanceText = TextView(this).apply {
            text = "2.5km"
            textSize = 28f
            setTextColor(Color.BLACK)
            setPadding(0, 16, 0, 8)
        }
        infoContainer.addView(distanceText)
        
        // Amount text
        val amountText = TextView(this).apply {
            text = "예상 요금: 15,000원"
            textSize = 24f
            setTextColor(Color.DKGRAY)
            setPadding(0, 8, 0, 8)
        }
        infoContainer.addView(amountText)
        
        // From location
        val fromText = TextView(this).apply {
            text = "출발: 강남역 11번 출구"
            textSize = 20f
            setTextColor(Color.GRAY)
            setPadding(0, 16, 0, 8)
        }
        infoContainer.addView(fromText)
        
        // To location
        val toText = TextView(this).apply {
            text = "도착: 서초동 123-45"
            textSize = 20f
            setTextColor(Color.GRAY)
            setPadding(0, 8, 0, 32)
        }
        infoContainer.addView(toText)
        
        // Add info container to layout
        val infoParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        }
        layout.addView(infoContainer, infoParams)
        
        // Yellow accept button (카카오 택시 스타일)
        val acceptButton = Button(this).apply {
            text = "수락"
            textSize = 20f
            setTextColor(Color.BLACK)
            setBackgroundColor(Color.parseColor("#FFE500")) // 카카오 노란색
            id = View.generateViewId()
            setPadding(0, 40, 0, 40)
        }
        
        // Button container for proper styling
        val buttonContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            id = View.generateViewId()
            setPadding(32, 32, 32, 100)
        }
        buttonContainer.addView(acceptButton, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.MATCH_PARENT,
            150  // Fixed height for button
        ))
        
        // Add button container to layout
        val buttonParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.MATCH_PARENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        }
        layout.addView(buttonContainer, buttonParams)
        
        // Timer text
        val timerText = TextView(this).apply {
            text = "10초"
            textSize = 36f
            setTextColor(Color.RED)
            id = View.generateViewId()
        }
        
        val timerParams = ConstraintLayout.LayoutParams(
            ConstraintLayout.LayoutParams.WRAP_CONTENT,
            ConstraintLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            topMargin = 50
            rightMargin = 32
        }
        layout.addView(timerText, timerParams)
        
        // Set click listener
        acceptButton.setOnClickListener {
            finish()
        }
        
        // Start countdown timer
        var countdown = 10
        val timer = Timer()
        timer.scheduleAtFixedRate(object : TimerTask() {
            override fun run() {
                runOnUiThread {
                    countdown--
                    timerText.text = "${countdown}초"
                    if (countdown <= 0) {
                        timer.cancel()
                        finish()
                    }
                }
            }
        }, 1000, 1000)
        
        setContentView(layout)
    }
}