/**
 * 语音活动检测器（VAD）
 * 
 * 项目地址: https://github.com/MR-MaoJiu/PhoneAgent
 * 
 * 用于检测是否有语音输入
 */
package com.mobileagent.phoneagent.utils

import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import kotlinx.coroutines.*
import kotlin.coroutines.coroutineContext
import kotlin.math.abs

/**
 * 语音活动检测器（VAD）
 * 用于检测是否有语音输入
 */
class VoiceActivityDetector(
    private val onVoiceDetected: () -> Unit,
    private val onSilenceDetected: () -> Unit
) {
    private val TAG = "VoiceActivityDetector"
    private var audioRecord: AudioRecord? = null
    private var isDetecting = false
    private var detectionJob: Job? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    
    // VAD参数
    private val sampleRate = 16000
    private val channelConfig = android.media.AudioFormat.CHANNEL_IN_MONO
    private val audioFormat = android.media.AudioFormat.ENCODING_PCM_16BIT
    private val bufferSize = AudioRecord.getMinBufferSize(sampleRate, channelConfig, audioFormat)
    
    // 检测阈值
    private val voiceThreshold = 3000.0 // 音量阈值
    private val silenceDuration = 1000L // 静音持续时间（毫秒）
    
    /**
     * 开始检测
     */
    fun start() {
        if (isDetecting) {
            Log.w(TAG, "VAD已在运行中")
            return
        }
        
        try {
            audioRecord = AudioRecord(
                MediaRecorder.AudioSource.MIC,
                sampleRate,
                channelConfig,
                audioFormat,
                bufferSize * 2
            )
            
            if (audioRecord?.state != AudioRecord.STATE_INITIALIZED) {
                Log.e(TAG, "AudioRecord初始化失败")
                return
            }
            
            isDetecting = true
            audioRecord?.startRecording()
            
            detectionJob = scope.launch {
                detectVoiceActivity()
            }
            
            Log.d(TAG, "✅ VAD已启动")
        } catch (e: Exception) {
            Log.e(TAG, "启动VAD失败", e)
            isDetecting = false
        }
    }
    
    /**
     * 停止检测
     */
    fun stop() {
        isDetecting = false
        detectionJob?.cancel()
        detectionJob = null
        
        try {
            audioRecord?.stop()
            audioRecord?.release()
            audioRecord = null
        } catch (e: Exception) {
            Log.e(TAG, "停止VAD失败", e)
        }
        
        Log.d(TAG, "VAD已停止")
    }
    
    /**
     * 检测语音活动
     */
    private suspend fun detectVoiceActivity() {
        val buffer = ShortArray(bufferSize)
        var lastVoiceTime = 0L
        var isVoiceActive = false
        
        while (isDetecting) {
            try {
                // 检查协程是否仍然活跃
                coroutineContext.ensureActive()
                
                val readSize = audioRecord?.read(buffer, 0, buffer.size) ?: 0
                
                if (readSize > 0) {
                    // 计算音频能量
                    val energy = calculateEnergy(buffer, readSize)
                    
                    if (energy > voiceThreshold) {
                        // 检测到语音
                        if (!isVoiceActive) {
                            isVoiceActive = true
                            withContext(Dispatchers.Main) {
                                onVoiceDetected()
                            }
                        }
                        lastVoiceTime = System.currentTimeMillis()
                    } else {
                        // 静音
                        if (isVoiceActive && System.currentTimeMillis() - lastVoiceTime > silenceDuration) {
                            isVoiceActive = false
                            withContext(Dispatchers.Main) {
                                onSilenceDetected()
                            }
                        }
                    }
                }
                
                delay(100) // 每100ms检测一次
            } catch (e: CancellationException) {
                // 协程被取消，正常退出
                Log.d(TAG, "VAD检测被取消")
                break
            } catch (e: Exception) {
                Log.e(TAG, "VAD检测错误", e)
                break
            }
        }
    }
    
    /**
     * 计算音频能量
     */
    private fun calculateEnergy(buffer: ShortArray, size: Int): Double {
        var sum = 0.0
        for (i in 0 until size) {
            sum += abs(buffer[i].toDouble())
        }
        return sum / size
    }
    
    /**
     * 释放资源
     */
    fun release() {
        stop()
        scope.cancel()
    }
}

