package com.example.claudechat.viewmodel

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.example.claudechat.model.UserProfile
import com.example.claudechat.repository.UserProfileRepository
import kotlinx.coroutines.launch

/**
 * ViewModel для управления персональным профилем пользователя
 */
class UserProfileViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = UserProfileRepository(application.applicationContext)

    // Профиль пользователя как LiveData
    val userProfile: LiveData<UserProfile?> = repository.getUserProfile().asLiveData()

    private val _isLoading = MutableLiveData(false)
    val isLoading: LiveData<Boolean> = _isLoading

    private val _error = MutableLiveData<String?>()
    val error: LiveData<String?> = _error

    private val _saveSuccess = MutableLiveData<Boolean>()
    val saveSuccess: LiveData<Boolean> = _saveSuccess

    /**
     * Сохраняет профиль пользователя
     */
    fun saveProfile(profile: UserProfile) {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                repository.saveUserProfile(profile)
                _saveSuccess.value = true
                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Ошибка сохранения профиля: ${e.message}"
                _isLoading.value = false
                _saveSuccess.value = false
            }
        }
    }

    /**
     * Удаляет профиль пользователя
     */
    fun deleteProfile() {
        _isLoading.value = true
        _error.value = null

        viewModelScope.launch {
            try {
                repository.deleteUserProfile()
                _isLoading.value = false
            } catch (e: Exception) {
                _error.value = "Ошибка удаления профиля: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    /**
     * Сбрасывает флаг успешного сохранения
     */
    fun resetSaveSuccess() {
        _saveSuccess.value = false
    }
}
