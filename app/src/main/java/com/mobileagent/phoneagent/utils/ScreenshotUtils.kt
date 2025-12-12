/**
 * 截图工具类 - 支持复用 VirtualDisplay
 * 
 * 项目地址: https://github.com/MR-MaoJiu/PhoneAgent
 * 
 * 负责：
 * - 屏幕截图管理
 * - 图片编码转换
 */
package com.mobileagent.phoneagent.utils

import android.graphics.Bitmap
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.Looper
import android.util.Base64
import android.util.Log
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * 截图工具类 - 支持复用 VirtualDisplay
 */
class ScreenshotManager(
    private val mediaProjection: MediaProjection,
    private val width: Int,
    private val height: Int,
    private val density: Int
) {
    private val TAG = "ScreenshotManager"
    
    private var virtualDisplay: VirtualDisplay? = null
    private var imageReader: ImageReader? = null
    private var mediaProjectionCallback: MediaProjection.Callback? = null
    private var isInitialized = false

    /**
     * 初始化 VirtualDisplay（只创建一次）
     * 注意：必须在主线程中调用，且 MediaProjection 必须有效
     */
    fun initialize() {
        if (isInitialized) {
            Log.d(TAG, "ScreenshotManager 已初始化，跳过")
            return
        }
        
        // 检查是否在主线程
        if (Looper.myLooper() != Looper.getMainLooper()) {
            Log.w(TAG, "⚠️ initialize() 应在主线程中调用")
        }
        
        try {
            // Android 14+ 要求：必须先注册回调
            val callback = object : MediaProjection.Callback() {
                override fun onStop() {
                    Log.d(TAG, "MediaProjection 已停止")
                    cleanup()
                }
            }
            mediaProjectionCallback = callback
            mediaProjection.registerCallback(callback, Handler(Looper.getMainLooper()))
            Log.d(TAG, "✅ MediaProjection 回调已注册")
            
            // 创建 ImageReader
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            
            // 创建 VirtualDisplay（只创建一次）
            // 注意：必须在创建 MediaProjection 的同一进程中创建 VirtualDisplay
            virtualDisplay = mediaProjection.createVirtualDisplay(
                "ScreenCapture",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader!!.surface,
                null,
                null
            )
            
            Log.d(TAG, "✅ VirtualDisplay 已创建: ${width}x${height}")
            isInitialized = true
            
            // 等待 VirtualDisplay 初始化
            Thread.sleep(200)
        } catch (e: SecurityException) {
            Log.e(TAG, "❌ 初始化失败: MediaProjection 可能已过期或无效", e)
            Log.e(TAG, "提示: 请确保 MediaProjection 是在当前进程中创建的，且未过期")
            cleanup()
            throw e
        } catch (e: Exception) {
            Log.e(TAG, "❌ 初始化失败", e)
            cleanup()
            throw e
        }
    }

    /**
     * 截图（复用已创建的 VirtualDisplay）
     */
    fun captureScreen(): Bitmap? {
        if (!isInitialized || virtualDisplay == null || imageReader == null) {
            Log.e(TAG, "❌ ScreenshotManager 未初始化")
            return null
        }
        
        return try {
            // 尝试获取图像，最多重试 5 次
            var image: Image? = null
            var retryCount = 0
            while (image == null && retryCount < 5) {
                image = imageReader!!.acquireLatestImage()
                if (image == null) {
                    retryCount++
                    Log.d(TAG, "图像尚未就绪，等待中... (重试 $retryCount/5)")
                    Thread.sleep(100)
                }
            }
            
            if (image != null) {
                Log.d(TAG, "✅ 成功获取图像: ${image.width}x${image.height}")
                val bitmap = imageToBitmap(image)
                image.close()
                Log.d(TAG, "✅ 截图成功: ${bitmap.width}x${bitmap.height}")
                bitmap
            } else {
                Log.e(TAG, "❌ 无法获取图像，重试 ${retryCount} 次后仍失败")
                null
            }
        } catch (e: Exception) {
            Log.e(TAG, "❌ 截图失败", e)
            null
        }
    }

    /**
     * 将 Image 转换为 Bitmap
     */
    private fun imageToBitmap(image: Image): Bitmap {
        val planes = image.planes
        val buffer = planes[0].buffer
        val pixelStride = planes[0].pixelStride
        val rowStride = planes[0].rowStride
        val rowPadding = rowStride - pixelStride * image.width

        val bitmap = Bitmap.createBitmap(
            image.width + rowPadding / pixelStride,
            image.height,
            Bitmap.Config.ARGB_8888
        )
        bitmap.copyPixelsFromBuffer(buffer)
        return if (rowPadding == 0) {
            bitmap
        } else {
            Bitmap.createBitmap(bitmap, 0, 0, image.width, image.height)
        }
    }

    /**
     * 清理资源
     */
    fun cleanup() {
        try {
            virtualDisplay?.release()
            virtualDisplay = null
            imageReader?.close()
            imageReader = null
            mediaProjectionCallback?.let {
                mediaProjection.unregisterCallback(it)
            }
            mediaProjectionCallback = null
            isInitialized = false
            Log.d(TAG, "✅ 资源已清理")
        } catch (e: Exception) {
            Log.e(TAG, "清理资源时出错", e)
        }
    }
}

/**
 * 截图工具类（保持向后兼容）
 */
object ScreenshotUtils {
    private const val TAG = "ScreenshotUtils"

    /**
     * 将 Bitmap 转换为 Base64
     */
    fun bitmapToBase64(bitmap: Bitmap): String {
        val outputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, outputStream)
        val byteArray = outputStream.toByteArray()
        return Base64.encodeToString(byteArray, Base64.NO_WRAP)
    }
}

