package com.ltt.gkd.ocr // 包声明：本文件属于 OCR 包 com.ltt.gkd.ocr

import android.accessibilityservice.AccessibilityService // 导入 AccessibilityService，作为 service 字段类型
import android.graphics.Bitmap // 导入 Bitmap，图像处理
import android.graphics.Point // 导入 Point，返回命中坐标
import android.hardware.HardwareBuffer // 导入 HardwareBuffer，截图底层资源
import android.os.Build // 导入 Build，判断 SDK 版本
import android.view.Display // 导入 Display，指定主屏
import com.google.mlkit.vision.common.InputImage // 导入 InputImage，MLKit 输入图像
import com.google.mlkit.vision.text.Text // 导入 Text，识别结果
import com.google.mlkit.vision.text.TextRecognition // 导入 TextRecognition，文本识别入口
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions // 导入中文识别器选项
import com.ltt.gkd.gesture.GestureSimulator // 导入 GestureSimulator，OCR 命中后点击
import com.ltt.gkd.data.rule.MatchTarget // 导入 MatchTarget，匹配目标配置
import com.ltt.gkd.data.rule.MatchType // 导入 MatchType，匹配类型
import com.ltt.gkd.data.rule.Rule // 导入 Rule，规则数据类
import com.ltt.gkd.util.Logger // 导入 Logger，日志
import com.ltt.gkd.util.PatternUtils // 导入 PatternUtils，正则编译
import kotlinx.coroutines.suspendCancellableCoroutine // 导入 suspendCancellableCoroutine，回调转挂起
import kotlinx.coroutines.tasks.await // 导入 await，Task 转挂起
import kotlin.coroutines.resume // 导入 resume，恢复协程

/**
 * OCR 兜底：当控件树中找不到"跳过"等文字时，截图识别文本坐标并点击。
 *
 * 截图方式：
 * - Android 11+（API 30+）：使用 [AccessibilityService.takeScreenshot]
 * - Android 8.x/9.x：不支持（返回 null，调用方自然走兜底失败）
 *
 * 隐私：所有识别均端侧进行，图片不离开设备。
 *
 * @param service 无障碍服务，用于调用系统截图 API
 * @param gesture 手势模拟器，用于在 OCR 命中后执行点击
 */
class OcrManager(
    private val service: AccessibilityService, // 无障碍服务
    private val gesture: GestureSimulator // 手势模拟器
) {

    // MLKit 中文文本识别器（端侧离线模型）
    private val recognizer = TextRecognition.getClient( // 创建识别器
        ChineseTextRecognizerOptions.Builder().build() // 中文识别器选项
    )

    // 最近一次 OCR 时间戳，配合 ocrIntervalMs 做节流
    @Volatile // 多线程可见
    private var lastOcrTime = 0L // 上次 OCR 时间
    private val ocrIntervalMs = 1500L // OCR 节流，避免高频截图

    /**
     * 对候选 OCR 规则尝试匹配+点击。
     *
     * 流程：节流判断 → 截图 → MLKit 识别 → 在文本块中匹配关键词 → 命中后调用 [gesture] 点击。
     *
     * @param rules 待尝试的 OCR 规则列表
     * @param pkg 当前前台包名（用于日志）
     * @return 命中的规则；未命中或失败返回 null
     */
    suspend fun matchAndClick(rules: List<Rule>, pkg: String): Rule? { // 入口：匹配+点击
        if (rules.isEmpty()) return null // 无规则直接返回
        val now = System.currentTimeMillis() // 当前时间
        // 节流：距上次 OCR 不足 1.5s 则跳过，避免高频截图耗电
        if (now - lastOcrTime < ocrIntervalMs) { // 距上次不足 1.5s
            Logger.d("OCR 节流中，跳过本次") // 记录 debug
            return null // 直接返回
        }
        lastOcrTime = now // 更新上次 OCR 时间

        val bitmap = captureScreen() ?: run { // 截图，失败时
            Logger.w("截图失败（可能 Android <11 不支持无障碍截图）") // 打 warn 日志
            return null // 直接返回
        }
        // 注意：必须保证下方 try/finally 中 bitmap 一定被 recycle
        val text: Text? = try { // 识别文本，捕获异常
            recognizeText(bitmap) // 调用 MLKit 识别
        } catch (e: Exception) { // 出现异常
            Logger.w("OCR 识别失败", e) // 打 warn 日志
            null // 标记为 null
        } finally {
            // recognizeText 已完成（或抛异常），不再持有 bitmap
            if (!bitmap.isRecycled) bitmap.recycle() // 未回收则回收
        }
        if (text == null || text.text.isBlank()) return null // 无识别结果或全空白直接返回
        val recognized = text  // smart cast: 此后 recognized 一定非空

        for (rule in rules) { // 遍历候选规则
            val target = rule.match // 取匹配目标
            val point = findKeyword(recognized, target) ?: continue // 查找关键词，无命中跳过
            Logger.i("OCR 命中规则 ${rule.id} 在 $point") // 记录命中
            val ok = gesture.tapAt(point.x.toFloat(), point.y.toFloat()) // 在命中坐标点击
            if (ok) return rule // 点击成功返回命中的规则
        }
        return null // 全部未命中返回 null
    }

    /**
     * 截图：使用无障碍 API（API 30+）。返回可独立使用的 ARGB Bitmap。
     *
     * 资源管理要点：
     * - [AccessibilityService.ScreenshotResult] 持有 [HardwareBuffer] 与 [android.graphics.ColorSpace]。
     * - 通过 [Bitmap.wrapHardwareBuffer] 包装为硬件 Bitmap，再 copy 出一份软 Bitmap（ARGB_8888）。
     * - 使用完毕后关闭 [HardwareBuffer]（AutoCloseable），避免资源泄漏。
     *
     * @return 软 Bitmap；API 低于 30 或截图失败返回 null
     */
    private suspend fun captureScreen(): Bitmap? { // 内部：截图
        // Android 11 以下无障碍服务不支持截图
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return null // 低版本直接返回 null
        val result: AccessibilityService.ScreenshotResult? = suspendCancellableCoroutine { cont -> // 把异步回调转挂起
            service.takeScreenshot( // 调用系统截图
                Display.DEFAULT_DISPLAY, // 主屏
                service.mainExecutor, // 主线程 executor
                object : AccessibilityService.TakeScreenshotCallback { // 截图回调
                    override fun onSuccess(result: AccessibilityService.ScreenshotResult) { // 成功
                        if (cont.isActive) cont.resume(result) // 协程仍活跃则恢复并返回结果
                    }

                    override fun onFailure(error: Int) { // 失败
                        Logger.w("takeScreenshot 失败 code=$error") // 打 warn 日志
                        if (cont.isActive) cont.resume(null) // 协程仍活跃则恢复并返回 null
                    }
                }
            )
        }
        result ?: return null // 结果为 null 直接返回

        val hardwareBuffer: HardwareBuffer = result.hardwareBuffer // 取底层 HardwareBuffer
        val colorSpace = result.colorSpace // 取颜色空间
        // 包装为硬件 Bitmap，再 copy 出软 Bitmap 以便脱离 HardwareBuffer 生命周期独立使用
        val hardwareBitmap = Bitmap.wrapHardwareBuffer(hardwareBuffer, colorSpace) // 包装为硬件 Bitmap
        val copy: Bitmap? = try { // 复制出软 Bitmap
            hardwareBitmap?.copy(Bitmap.Config.ARGB_8888, false) // ARGB_8888 配置，无 mipmap
        } catch (oom: OutOfMemoryError) { // OOM 异常
            Logger.w("截图 copy OOM", oom) // 打 warn 日志
            null // 置空
        } catch (e: Exception) { // 其他异常
            Logger.w("截图 copy 失败", e) // 打 warn 日志
            null // 置空
        } finally {
            hardwareBitmap?.recycle() // 回收硬件 Bitmap
            hardwareBuffer.close() // HardwareBuffer 必须关闭，否则会泄漏 native 内存
        }
        return copy // 返回软 Bitmap
    }

    /**
     * 调用 MLKit 中文识别。
     *
     * @param bitmap 待识别的图片
     * @return 识别出的 [Text] 结构（含文本块/行/元素及坐标）
     */
    private suspend fun recognizeText(bitmap: Bitmap): Text = // 内部：识别文本
        recognizer.process(InputImage.fromBitmap(bitmap, 0)).await() // 处理图片并等待结果（旋转角 0）

    /**
     * 在 OCR 结果中查找关键词，返回命中文字的中心坐标。
     *
     * 遍历文本块/行，对每个目标字符串编译 Pattern 并在行文本中查找：
     * - 整行命中：直接返回行边界框中心
     * - 部分命中：按命中字符在整行中的比例计算 X 坐标
     *
     * @param text OCR 识别结果
     * @param target 匹配目标配置
     * @return 命中位置 [Point]；未命中返回 null
     */
    private fun findKeyword(text: Text, target: MatchTarget): Point? { // 内部：查找关键词
        val patterns = target.text.map { compilePattern(it, target) } // 编译所有目标字符串为 Pattern
        for (block in text.textBlocks) { // 遍历文本块
            for (line in block.lines) { // 遍历行
                val lineText = line.text // 行文本
                for (p in patterns) { // 遍历每个 Pattern
                    val m = p.matcher(lineText) // 构造 matcher
                    if (m.find()) { // 找到匹配
                        val matchStart = m.start() // 匹配起始索引
                        val matchEnd = m.end() // 匹配结束索引
                        val frame = line.boundingBox ?: continue // 行边界框，无则跳过
                        if (matchStart == 0 && matchEnd == lineText.length) { // 整行命中
                            // 整行命中：使用行边界框中心点
                            return Point(frame.centerX(), frame.centerY()) // 返回行中心
                        }
                        // 部分命中：按命中字符位置在行宽中的比例换算 X 坐标
                        val ratio = (matchStart + matchEnd) / 2f / lineText.length.coerceAtLeast(1) // 计算比例
                        return Point( // 返回换算后的坐标
                            (frame.left + frame.width() * ratio).toInt(), // X 按 ratio 计算
                            frame.centerY() // Y 取行中心
                        )
                    }
                }
            }
        }
        return null // 全部未命中返回 null
    }

    /**
     * 编译单个匹配字符串为 [java.util.regex.Pattern]。
     *
     * @param raw 原始字符串
     * @param target 提供 regex / caseInsensitive 配置
     * @return 编译后的 Pattern
     */
    private fun compilePattern(raw: String, target: MatchTarget) = // 内部：编译 Pattern
        PatternUtils.compile(raw, target) // 委托 PatternUtils

    /**
     * 释放 MLKit TextRecognizer 资源。
     * 由 [com.ltt.gkd.service.SkipAccessibilityService.onUnbind] 调用。
     */
    fun close() { // 入口：关闭资源
        runCatching { recognizer.close() } // 关闭识别器，忽略异常
            .onFailure { Logger.w("TextRecognizer 关闭失败", it) } // 失败时打 warn 日志
    }
}
