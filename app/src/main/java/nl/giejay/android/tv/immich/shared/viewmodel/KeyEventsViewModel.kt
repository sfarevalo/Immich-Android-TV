package nl.giejay.android.tv.immich.shared.viewmodel

import android.view.KeyEvent
import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import timber.log.Timber

class KeyEventsViewModel : ViewModel() {

    private val keyEventState = MutableStateFlow<KeyEvent?>(null)
    val state: StateFlow<KeyEvent?>
        get() = keyEventState

    private val deleteEventTriggerState = MutableStateFlow<Int>(0)
    val deleteEventTrigger: StateFlow<Int>
        get() = deleteEventTriggerState

    fun postKeyEvent(keyEvent: KeyEvent?){
        keyEventState.value = keyEvent
    }

    fun postDeleteEvent() {
        Timber.d("VIEWMODEL: postDeleteEvent called, incrementing trigger")
        // Increment counter to trigger event (works better than boolean for single events)
        deleteEventTriggerState.value++
    }
}