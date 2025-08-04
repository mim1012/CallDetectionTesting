package com.kakao.taxi.test.module

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Log
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.TextRecognizer
import com.google.mlkit.vision.text.korean.KoreanTextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.google.android.gms.tasks.Tasks
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OCRResult(
    val text: String,
    val confidence: Float,
    val boundingBox: Rect
)

data class FilterCriteria(
    val minAmount: Int? = null,
    val maxAmount: Int? = null,
    val minDistance: Float? = null,
    val maxDistance: Float? = null,
    val keywords: List<String>? = null,
    val filterEnabled: Boolean = true
) : java.io.Serializable

// Extension function to convert Task to suspend function
private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T {
    return suspendCancellableCoroutine { cont ->
        addOnSuccessListener { result ->
            cont.resume(result)
        }
        addOnFailureListener { exception ->
            cont.resumeWithException(exception)
        }
    }
}

class OCRProcessor(private val context: Context) {
    companion object {
        private const val TAG = "OCRProcessor"
    }

    private var textRecognizer: TextRecognizer? = null
    private var isInitialized = false

    suspend fun initialize(): Boolean = withContext(Dispatchers.IO) {
        try {
            // Initialize ML Kit Text Recognition with Korean support
            textRecognizer = TextRecognition.getClient(KoreanTextRecognizerOptions.Builder().build())
            isInitialized = true
            
            Log.d(TAG, "OCR initialization succeeded")
            true
        } catch (e: Exception) {
            Log.e(TAG, "OCR initialization failed", e)
            false
        }
    }

    suspend fun extractText(bitmap: Bitmap): String? = withContext(Dispatchers.IO) {
        if (!isInitialized) {
            Log.e(TAG, "OCR not initialized")
            return@withContext null
        }

        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val result = textRecognizer?.process(image)?.await()
            result?.text
        } catch (e: Exception) {
            Log.e(TAG, "Text extraction failed", e)
            null
        }
    }

    suspend fun extractTextWithRegions(bitmap: Bitmap): List<OCRResult> = withContext(Dispatchers.IO) {
        val results = mutableListOf<OCRResult>()
        
        if (!isInitialized) {
            Log.e(TAG, "OCR not initialized")
            return@withContext results
        }

        try {
            val image = InputImage.fromBitmap(bitmap, 0)
            val visionText = textRecognizer?.process(image)?.await()
            
            visionText?.textBlocks?.forEach { block: Text.TextBlock ->
                block.lines.forEach { line: Text.Line ->
                    line.elements.forEach { element: Text.Element ->
                        val text = element.text
                        val box = element.boundingBox
                        val confidence = element.confidence ?: 0.0f
                        
                        if (text.isNotEmpty() && box != null) {
                            results.add(
                                OCRResult(
                                    text = text,
                                    confidence = confidence,
                                    boundingBox = box
                                )
                            )
                        }
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Text extraction with regions failed", e)
        }

        results
    }

    suspend fun extractAmountAndDistance(bitmap: Bitmap): Pair<Int?, Float?> = withContext(Dispatchers.IO) {
        val text = extractText(bitmap) ?: return@withContext Pair(null, null)
        
        var amount: Int? = null
        var distance: Float? = null

        // Extract amount (looking for patterns like "12,000원" or "12000원")
        val amountPattern = """(\d{1,3}(,\d{3})*|\d+)\s*원""".toRegex()
        amountPattern.find(text)?.let { match ->
            val amountStr = match.groupValues[1].replace(",", "")
            amount = amountStr.toIntOrNull()
        }

        // Extract distance (looking for patterns like "2.5km" or "2.5㎞")
        val distancePattern = """(\d+\.?\d*)\s*(km|㎞|킬로미터)""".toRegex()
        distancePattern.find(text)?.let { match ->
            distance = match.groupValues[1].toFloatOrNull()
        }

        Log.d(TAG, "Extracted amount: $amount, distance: $distance")
        Pair(amount, distance)
    }

    fun applyFilter(
        ocrResults: List<OCRResult>,
        criteria: FilterCriteria
    ): Boolean {
        var amountFound: Int? = null
        var distanceFound: Float? = null
        var keywordsFound = mutableSetOf<String>()

        for (result in ocrResults) {
            val text = result.text

            // Check for amount
            if (amountFound == null) {
                val amountPattern = """(\d{1,3}(,\d{3})*|\d+)\s*원""".toRegex()
                amountPattern.find(text)?.let { match ->
                    val amountStr = match.groupValues[1].replace(",", "")
                    amountFound = amountStr.toIntOrNull()
                }
            }

            // Check for distance
            if (distanceFound == null) {
                val distancePattern = """(\d+\.?\d*)\s*(km|㎞|킬로미터)""".toRegex()
                distancePattern.find(text)?.let { match ->
                    distanceFound = match.groupValues[1].toFloatOrNull()
                }
            }

            // Check for keywords
            criteria.keywords?.forEach { keyword ->
                if (text.contains(keyword, ignoreCase = true)) {
                    keywordsFound.add(keyword)
                }
            }
        }

        // Apply filters
        criteria.minAmount?.let { min ->
            if (amountFound == null || amountFound!! < min) return false
        }
        
        criteria.maxAmount?.let { max ->
            if (amountFound == null || amountFound!! > max) return false
        }
        
        criteria.minDistance?.let { min ->
            if (distanceFound == null || distanceFound!! < min) return false
        }
        
        criteria.maxDistance?.let { max ->
            if (distanceFound == null || distanceFound!! > max) return false
        }
        
        criteria.keywords?.let { keywords ->
            if (keywordsFound.size < keywords.size) return false
        }

        return true
    }

    fun release() {
        textRecognizer?.close()
        textRecognizer = null
        isInitialized = false
    }
}