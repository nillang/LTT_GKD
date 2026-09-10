package com.ltt.gkd.ocr

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.ScreenshotResult
import android.graphics.Bitmap
import android.graphics.Point
import android.os.Build
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.ltt.gkd.gesture.GestureSimulator
import com.ltt.gkd.data.rule.MatchTarget
import com.ltt.gkd.data.rule.MatchType
import com.ltt.gkd.data.rule.Rule
import com.ltt.gkd.util.Logger
import com.ltt.gkd.util.PatternUtils
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.tasks.await
import kotlin.coroutines.resume

/**
 * OCR 兜底：当控件树中找不到"跳过"等文字时，截图识别文本坐标并点击。
 *
 * 截图方式：
 * - Android 11+（API 30+）：使用 [AccessibilityService.takeScreenshot]
 * - Android 8.x/9.x：不支持（返回 null，调用方自然走兜底失败）
 *
 * 隐私：所有识别均端侧进行，图片不离开设备。
 */
class OcrManager(
    private val service: AccessibilityService,
    private val gesture: GestureSimulator
) {

    private val recognizer = TextRecognition.getClient(
        ChineseTextRecognizerOptions.Builder().build()
    )

    @Volatile
    private var lastOcrTime = 0L
    private val ocrIntervalMs = 1500L // OCR 节流，避免高频截图

    /**
     * 对候选 OCR 规则尝试匹配+点击。
     * @return 命中的规则；未命中或失败返回 null
     */
    suspend fun matchAndClick(rules: List<Rule>, pkg: String): Rule? {
        if (rules.isEmpty()) return null
        val now = System.currentTimeMillis()
        if (now - lastOcrTime < ocrIntervalMs) {
            Logger.d("OCR 节流中，跳过本次")
            return null
        }
        lastOcrTime = now

        val bitmap = captureScreen() ?: run {
            Logger.w("截图失败（可能 Android <11 不支持无障碍截图）")
            return null
        }
        // 注意：必须保证下方 try/finally 中 bitmap 一定被 recycle
        val text: Text? = try {
            recognizeText(bitmap)
        } catch (e: Exception) {
            Logger.w("OCR 识别失败", e)
            null
        } finally {
            // recognizeText 已完成（或抛异常），不再持有 bitmap
            if (!bitmap.isRecycled) bitmap.recycle()
        }
        if (text == null || text.text.isBlank()) return null
        val recognized = text  // smart cast: 此后 recognized 一定非空

        for (rule in rules) {
            val target = rule.match
            val point = findKeyword(recognized, target) ?: continue
            Logger.i("OCR 命中规则 ${rule.id} 在 $point")
            val ok = gesture.tapAt(point.x.toFloat(), point.y.toFloat())
            if (ok) return rule
        }
        return null
    }

    /**
     * 截图：使用无障碍 API（API 30+）。返回可独立使用的 ARGB Bitmap。
     *
     * 资源管理要点：
     * - ScreenshotResult.bitmap 是 HardwareBuffer 支持的 Bitmap，被 result 持有；
     *   文档禁止应用 recycle，且 result.close() 后该 Bitmap 失效。
     * - 因此我们 copy 出一份软 Bitmap（ARGB_8888），再 close 原 result。
     * - copy 失败时直接返回 null，避免返回已失效的 Bitmap。
     */
    private suspend fun captureScreen(): Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null
        val result: ScreenshotResult = suspendCancellableCoroutine { cont ->
            service.takeScreenshot(
                service.mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(result: ScreenshotResult) {
                        if (cont.isActive) cont.resume(result)
                    }

                    override fun onFailure(error: Int) {
                        Logger.w("takeScreenshot 失败 code=$error")
                        if (cont.isActive) cont.resume(null)
                    }
                }
            )
        } ?: return null

        // 必须在 close 之前 copy；否则 close 后 hardwareBitmap 失效
        val hardwareBitmap = result.bitmap
        val copy: Bitmap? = try {
            hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
        } catch (oom: OutOfMemoryError) {
            Logger.w("截图 copy OOM", oom)
            null
        } catch (e: Exception) {
            Logger.w("截图 copy 失败", e)
            null
        }
        // 不对 hardwareBitmap 调用 recycle（它是 HardwareBuffer Bitmap，由 result 管理）
        result.close()
        return copy
    }

    /** 调用 MLKit 中文识别。 */
    private suspend fun recognizeText(bitmap: Bitmap): Text =
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await()

    /** 在 OCR 结果中查找关键词，返回命中文字的中心坐标。 */
    private fun findKeyword(text: Text, target: MatchTarget): Point? {
        val patterns = target.text.map { compilePattern(it, target) }
        for (block in text.textBlocks) {
            for (line in block.lines) {
                val lineText = line.text
                for (p in patterns) {
                    val m = p.matcher(lineText)
                    if (m.find()) {
                        val matchStart = m.start()
                        val matchEnd = m.end()
                        val frame = line.boundingBox ?: continue
                        if (matchStart == 0 && matchEnd == lineText.length) {
                            return Point(frame.centerX(), frame.centerY())
                        }
                        val ratio = (matchStart + matchEnd) / 2f / lineText.length.coerceAtLeast(1)
                        return Point(
                            (frame.left + frame.width() * ratio).toInt(),
                            frame.centerY()
                        )
                    }
                }
            }
        }
        return null
    }

    private fun compilePattern(raw: String, target: MatchTarget) =
        PatternUtils.compile(raw, target)

    /**
     * 释放 MLKit TextRecognizer 资源。
     * 由 [com.ltt.gkd.service.SkipAccessibilityService.onUnbind] 调用。
     */
    fun close() {
        runCatching { recognizer.close() }
            .onFailure { Logger.w("TextRecognizer 关闭失败", it) }
    }
}
