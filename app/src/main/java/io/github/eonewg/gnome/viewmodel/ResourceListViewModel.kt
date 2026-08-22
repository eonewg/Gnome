package io.github.eonewg.gnome.viewmodel

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.skydoves.sandwich.suspendOnSuccess
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.launch
import io.github.eonewg.gnome.data.local.entity.ResourceEntity
import io.github.eonewg.gnome.data.service.MemoService
import javax.inject.Inject

@HiltViewModel
class ResourceListViewModel @Inject constructor(
    private val memoService: MemoService
): ViewModel() {
    var resources = mutableStateListOf<ResourceEntity>()
        private set

    fun loadResources() = viewModelScope.launch {
        memoService.getRepository().listResources().suspendOnSuccess {
            resources.clear()
            resources.addAll(data.sortedByDescending { it.date })
        }
    }
}
