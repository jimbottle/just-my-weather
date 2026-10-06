package io.raylytics.justmyweather.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import io.raylytics.justmyweather.region.RegionRepository
import io.raylytics.justmyweather.region.RegionSettings
import io.raylytics.justmyweather.region.ResolvedRegion
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Backs the Region & units screen: what is in force, what automatic would
 * pick, and the user's overrides. */
class RegionViewModel(
    private val repository: RegionRepository,
    /** After a change lands: the widgets carry the conventions of their last
     * fetch, so they need one more to read in the new units. */
    private val onChanged: () -> Unit = {},
) : ViewModel() {
    val settings: StateFlow<RegionSettings> =
        repository.settings.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RegionSettings.AUTOMATIC)
    val resolved: StateFlow<ResolvedRegion?> =
        repository.resolved.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)
    val automatic: StateFlow<ResolvedRegion?> =
        repository.automatic.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun update(transform: (RegionSettings) -> RegionSettings) {
        viewModelScope.launch {
            repository.update(transform)
            onChanged()
        }
    }
}
