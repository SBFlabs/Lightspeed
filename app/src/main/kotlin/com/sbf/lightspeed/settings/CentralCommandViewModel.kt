package com.sbf.lightspeed.settings

import com.sbf.lightspeed.system.safeReloadPreferences

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sbf.lightspeed.system.LightspeedBackupEngine
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class CentralCommandViewModel : ViewModel() {

    private val _importStatusMessage = MutableStateFlow<String?>(null)
    val importStatusMessage: StateFlow<String?> = _importStatusMessage

    private val _isImportSuccess = MutableStateFlow(false)
    val isImportSuccess: StateFlow<Boolean> = _isImportSuccess

    fun exportToFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            LightspeedBackupEngine.exportToFile(context, uri)
        }
    }

    fun importFromFile(context: Context, uri: Uri) {
        viewModelScope.launch(Dispatchers.IO) {
            val res = LightspeedBackupEngine.importFromFile(context, uri)
            res.onSuccess { count ->
                _isImportSuccess.value = true
                _importStatusMessage.value = "Successfully restored $count settings and shortcut configurations!"
                safeReloadPreferences()
            }.onFailure { err ->
                _isImportSuccess.value = false
                _importStatusMessage.value = "Import Failed:\n${err.message ?: err.javaClass.simpleName}"
            }
        }
    }

    fun resetToDefaults(context: Context) {
        viewModelScope.launch(Dispatchers.IO) {
            LightspeedBackupEngine.resetToDefaults(context)
            safeReloadPreferences()
        }
    }

    fun importFromJson(context: Context, jsonText: String) {
        if (jsonText.isBlank()) {
            _isImportSuccess.value = false
            _importStatusMessage.value = "Pasted text is empty."
            return
        }
        
        viewModelScope.launch(Dispatchers.IO) {
            val res = LightspeedBackupEngine.importFromJson(context, jsonText)
            res.onSuccess { count ->
                _isImportSuccess.value = true
                _importStatusMessage.value = "Successfully restored $count settings and shortcut configurations!"
                safeReloadPreferences()
            }.onFailure { err ->
                _isImportSuccess.value = false
                _importStatusMessage.value = "Import Failed:\n${err.message}"
            }
        }
    }

    fun clearImportStatus() {
        _importStatusMessage.value = null
        _isImportSuccess.value = false
    }
}
