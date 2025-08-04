package com.kakao.taxi.test.service

import android.content.Context
import android.util.Log
import kotlinx.coroutines.*
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.concurrent.ConcurrentLinkedQueue

/**
 * 백그라운드에서 로그를 파일로 저장하는 서비스
 */
class BackgroundLogger(private val context: Context) {
    companion object {
        private const val TAG = "BackgroundLogger"
        private const val LOG_FOLDER = "KakaoTaxiLogs"
        private const val MAX_LOG_FILES = 7 // 7일치 보관
        private const val FLUSH_INTERVAL = 10000L // 10초마다 저장
    }
    
    private val logQueue = ConcurrentLinkedQueue<LogEntry>()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var flushJob: Job? = null
    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
    private val timeFormat = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault())
    
    data class LogEntry(
        val timestamp: Long,
        val level: String,
        val tag: String,
        val message: String,
        val extra: Map<String, Any>? = null
    )
    
    init {
        startAutoFlush()
        cleanOldLogs()
    }
    
    /**
     * 로그 추가
     */
    fun log(level: String, tag: String, message: String, extra: Map<String, Any>? = null) {
        val entry = LogEntry(
            timestamp = System.currentTimeMillis(),
            level = level,
            tag = tag,
            message = message,
            extra = extra
        )
        logQueue.offer(entry)
        
        // 콘솔에도 출력
        when (level) {
            "ERROR" -> Log.e(tag, message)
            "WARN" -> Log.w(tag, message)
            "INFO" -> Log.i(tag, message)
            "DEBUG" -> Log.d(tag, message)
            else -> Log.v(tag, message)
        }
    }
    
    /**
     * 기능별 상태 로그
     */
    fun logFeatureStatus(feature: String, status: String, success: Boolean, details: String? = null) {
        val extra = mutableMapOf<String, Any>(
            "feature" to feature,
            "status" to status,
            "success" to success
        )
        details?.let { extra["details"] = it }
        
        log(
            level = if (success) "INFO" else "ERROR",
            tag = "FeatureStatus",
            message = "$feature: $status",
            extra = extra
        )
    }
    
    /**
     * 단계별 진행 상황 로그
     */
    fun logProgress(step: String, result: String, data: Map<String, Any>? = null) {
        val extra = mutableMapOf<String, Any>(
            "step" to step,
            "result" to result
        )
        data?.let { extra.putAll(it) }
        
        log(
            level = "INFO",
            tag = "Progress",
            message = "$step -> $result",
            extra = extra
        )
    }
    
    /**
     * 자동 저장 시작
     */
    private fun startAutoFlush() {
        flushJob = scope.launch {
            while (isActive) {
                delay(FLUSH_INTERVAL)
                flushLogs()
            }
        }
    }
    
    /**
     * 로그를 파일로 저장
     */
    private suspend fun flushLogs() = withContext(Dispatchers.IO) {
        if (logQueue.isEmpty()) return@withContext
        
        try {
            val logDir = File(context.getExternalFilesDir(null), LOG_FOLDER)
            if (!logDir.exists()) logDir.mkdirs()
            
            val logFile = File(logDir, "log_${dateFormat.format(Date())}.txt")
            val logs = mutableListOf<String>()
            
            // 큐에서 로그 가져오기
            while (logQueue.isNotEmpty()) {
                val entry = logQueue.poll() ?: break
                val time = timeFormat.format(Date(entry.timestamp))
                var logLine = "[$time] [${entry.level}] [${entry.tag}] ${entry.message}"
                
                // 추가 데이터가 있으면 JSON 형식으로 추가
                entry.extra?.let {
                    logLine += " | ${it.entries.joinToString(", ") { (k, v) -> "$k=$v" }}"
                }
                
                logs.add(logLine)
            }
            
            // 파일에 추가
            logFile.appendText(logs.joinToString("\n") + "\n")
            
            Log.d(TAG, "Flushed ${logs.size} log entries to file")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to flush logs", e)
        }
    }
    
    /**
     * 오래된 로그 파일 삭제
     */
    private fun cleanOldLogs() {
        scope.launch {
            try {
                val logDir = File(context.getExternalFilesDir(null), LOG_FOLDER)
                if (!logDir.exists()) return@launch
                
                val cutoffDate = Calendar.getInstance().apply {
                    add(Calendar.DAY_OF_YEAR, -MAX_LOG_FILES)
                }.time
                
                logDir.listFiles()?.forEach { file ->
                    // log_2024-01-15.txt 형식에서 날짜 추출
                    val match = "log_(\\d{4}-\\d{2}-\\d{2})\\.txt".toRegex().find(file.name)
                    match?.groupValues?.get(1)?.let { dateStr ->
                        val fileDate = dateFormat.parse(dateStr)
                        if (fileDate != null && fileDate.before(cutoffDate)) {
                            file.delete()
                            Log.d(TAG, "Deleted old log file: ${file.name}")
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to clean old logs", e)
            }
        }
    }
    
    /**
     * 현재 세션의 로그 내용 가져오기
     */
    fun getCurrentSessionLogs(): List<String> {
        val logs = mutableListOf<String>()
        logQueue.forEach { entry ->
            val time = timeFormat.format(Date(entry.timestamp))
            logs.add("[$time] [${entry.level}] ${entry.message}")
        }
        return logs
    }
    
    /**
     * 강제로 로그 저장
     */
    fun forceFlush() {
        scope.launch {
            flushLogs()
        }
    }
    
    /**
     * 로거 정리
     */
    fun release() {
        flushJob?.cancel()
        scope.launch {
            flushLogs() // 마지막 로그 저장
            scope.cancel()
        }
    }
}