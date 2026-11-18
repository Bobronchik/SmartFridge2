package com.example.smartfridge.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.example.smartfridge.repository.SmartFridgeRepository

class FridgeViewModelFactory(
    private val repository: SmartFridgeRepository
) : ViewModelProvider.Factory {

    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(
        modelClass: Class<T>
    ): T {

        if (
            modelClass.isAssignableFrom(
                FridgeViewModel::class.java
            )
        ) {

            return FridgeViewModel(
                repository
            ) as T
        }

        throw IllegalArgumentException(
            "Unknown ViewModel"
        )
    }
}