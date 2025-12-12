/**
 * Phone Agent - 设置界面 Activity
 * 
 * 项目地址: https://github.com/MR-MaoJiu/PhoneAgent
 * 
 * 负责：
 * - AI 服务商选择
 * - API 配置（地址、模型、Key）
 * - 参数调整（Temperature、Top P）
 */
package com.mobileagent.phoneagent

import android.content.SharedPreferences
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.mobileagent.phoneagent.databinding.ActivitySettingsBinding
import com.mobileagent.phoneagent.model.ModelProvider

class SettingsActivity : AppCompatActivity() {
    private lateinit var binding: ActivitySettingsBinding
    private lateinit var prefs: SharedPreferences

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySettingsBinding.inflate(layoutInflater)
        setContentView(binding.root)

        prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)

        setupViews()
        loadSettings()
    }

    private fun setupViews() {
        // 设置AI厂商下拉列表
        val providers = ModelProvider.values().map { it.displayName }.toTypedArray()
        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, providers)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerProvider.adapter = adapter

        // 监听服务商选择变化，更新默认值和API Key显示
        binding.spinnerProvider.onItemSelectedListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedProvider = ModelProvider.values()[position]
                updateProviderSettings(selectedProvider)
            }
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }

        binding.btnSave.setOnClickListener {
            saveSettings()
        }

        binding.btnBack.setOnClickListener {
            finish()
        }
    }

    private fun updateProviderSettings(provider: ModelProvider) {
        // 如果当前输入框为空，填充默认值
        if (binding.etBaseUrl.text.toString().trim().isEmpty()) {
            binding.etBaseUrl.setText(provider.defaultBaseUrl)
        }
        if (binding.etModelName.text.toString().trim().isEmpty()) {
            binding.etModelName.setText(provider.defaultModelName)
        }

        // 显示/隐藏API Key输入框
        if (provider.requiresApiKey) {
            // 需要 API Key 的厂商：显示并标记为必填
            binding.layoutApiKey.visibility = View.VISIBLE
            binding.layoutApiKey.hint = "API Key (必填)"
        } else if (provider == ModelProvider.CUSTOM) {
            // 自定义厂商：显示但标记为可选
            binding.layoutApiKey.visibility = View.VISIBLE
            binding.layoutApiKey.hint = "API Key (可选)"
        } else {
            // 不需要 API Key 的厂商（如 Ollama）：隐藏
            binding.layoutApiKey.visibility = View.GONE
        }
    }

    private fun loadSettings() {
        val providerName = prefs.getString(KEY_PROVIDER, ModelProvider.OLLAMA.name) ?: ModelProvider.OLLAMA.name
        val provider = ModelProvider.fromString(providerName)
        val baseUrl = prefs.getString(KEY_BASE_URL, provider.defaultBaseUrl) ?: provider.defaultBaseUrl
        val modelName = prefs.getString(KEY_MODEL_NAME, provider.defaultModelName) ?: provider.defaultModelName
        val apiKey = prefs.getString(KEY_API_KEY, "") ?: ""
        val temperature = prefs.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE)
        val topP = prefs.getFloat(KEY_TOP_P, DEFAULT_TOP_P)

        // 设置选中的厂商
        val position = ModelProvider.values().indexOf(provider)
        if (position >= 0) {
            binding.spinnerProvider.setSelection(position)
        }

        binding.etBaseUrl.setText(baseUrl)
        binding.etModelName.setText(modelName)
        binding.etApiKey.setText(apiKey)
        binding.etTemperature.setText(temperature.toString())
        binding.etTopP.setText(topP.toString())
        
        // 更新API Key显示状态
        updateProviderSettings(provider)
    }

    private fun saveSettings() {
        val providerName = ModelProvider.values()[binding.spinnerProvider.selectedItemPosition].name
        val provider = ModelProvider.fromString(providerName)
        val baseUrl = binding.etBaseUrl.text.toString().trim()
        val modelName = binding.etModelName.text.toString().trim()
        val apiKey = binding.etApiKey.text.toString().trim()
        val temperatureStr = binding.etTemperature.text.toString().trim()
        val topPStr = binding.etTopP.text.toString().trim()

        if (baseUrl.isEmpty() || modelName.isEmpty()) {
            Toast.makeText(this, "请填写完整的设置信息", Toast.LENGTH_SHORT).show()
            return
        }

        // 检查 API Key：只有 requiresApiKey 为 true 的厂商才强制要求 API Key
        // 自定义厂商（CUSTOM）的 API Key 是可选的
        if (provider.requiresApiKey && apiKey.isEmpty()) {
            Toast.makeText(this, "该服务商需要填写 API Key", Toast.LENGTH_SHORT).show()
            return
        }

        // 验证和解析 temperature 和 top_p
        val temperature = try {
            temperatureStr.toFloat().coerceIn(0f, 2f)
        } catch (e: Exception) {
            DEFAULT_TEMPERATURE
        }
        
        val topP = try {
            topPStr.toFloat().coerceIn(0f, 1f)
        } catch (e: Exception) {
            DEFAULT_TOP_P
        }

        prefs.edit().apply {
            putString(KEY_PROVIDER, providerName)
            putString(KEY_BASE_URL, baseUrl)
            putString(KEY_MODEL_NAME, modelName)
            putString(KEY_API_KEY, apiKey)
            putFloat(KEY_TEMPERATURE, temperature)
            putFloat(KEY_TOP_P, topP)
            apply()
        }

        Toast.makeText(this, "设置已保存", Toast.LENGTH_SHORT).show()
        finish()
    }

    companion object {
        private const val PREFS_NAME = "phone_agent_settings"
        private const val KEY_PROVIDER = "provider"
        private const val KEY_BASE_URL = "base_url"
        private const val KEY_MODEL_NAME = "model_name"
        private const val KEY_API_KEY = "api_key"
        private const val KEY_TEMPERATURE = "temperature"
        private const val KEY_TOP_P = "top_p"
        
        private const val DEFAULT_TEMPERATURE = 0.1f
        private const val DEFAULT_TOP_P = 0.85f
        
        fun getProvider(context: android.content.Context): ModelProvider {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val providerName = prefs.getString(KEY_PROVIDER, ModelProvider.OLLAMA.name) ?: ModelProvider.OLLAMA.name
            return ModelProvider.fromString(providerName)
        }

        fun getBaseUrl(context: android.content.Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val provider = getProvider(context)
            return prefs.getString(KEY_BASE_URL, provider.defaultBaseUrl) ?: provider.defaultBaseUrl
        }

        fun getModelName(context: android.content.Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            val provider = getProvider(context)
            return prefs.getString(KEY_MODEL_NAME, provider.defaultModelName) ?: provider.defaultModelName
        }

        fun getApiKey(context: android.content.Context): String {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getString(KEY_API_KEY, "") ?: ""
        }

        fun getTemperature(context: android.content.Context): Float {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getFloat(KEY_TEMPERATURE, DEFAULT_TEMPERATURE)
        }

        fun getTopP(context: android.content.Context): Float {
            val prefs = context.getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
            return prefs.getFloat(KEY_TOP_P, DEFAULT_TOP_P)
        }
    }
}
