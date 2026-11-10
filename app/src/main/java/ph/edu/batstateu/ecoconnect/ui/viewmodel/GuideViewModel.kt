package ph.edu.batstateu.ecoconnect.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ph.edu.batstateu.ecoconnect.EcoConnectApp
import ph.edu.batstateu.ecoconnect.data.guide.Answer
import ph.edu.batstateu.ecoconnect.data.guide.GuideEngine
import ph.edu.batstateu.ecoconnect.data.guide.GuideResult
import ph.edu.batstateu.ecoconnect.data.guide.GuideSearch
import ph.edu.batstateu.ecoconnect.data.guide.GuideStep
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuide
import ph.edu.batstateu.ecoconnect.data.guide.WasteGuideRepository

data class GuideUiState(
    val guide: WasteGuide? = null,
    val query: String = "",
    val results: List<ph.edu.batstateu.ecoconnect.data.guide.SearchHit> = emptyList(),
    /** Current quiz step, or a category result shown directly (e.g. from search). */
    val step: GuideStep? = null,
) {
    val loading: Boolean get() = guide == null
}

/** State for the waste guide screens: search, the question flow, and results. */
class GuideViewModel(private val repository: WasteGuideRepository) : ViewModel() {

    private val _uiState = MutableStateFlow(GuideUiState())
    val uiState: StateFlow<GuideUiState> = _uiState.asStateFlow()

    private var engine: GuideEngine? = null

    init {
        viewModelScope.launch {
            val guide = repository.guide()
            engine = GuideEngine(guide)
            _uiState.update { it.copy(guide = guide, results = GuideSearch.search(guide, it.query)) }
        }
    }

    fun onQueryChange(query: String) {
        _uiState.update { state ->
            state.copy(query = query, results = state.guide?.let { GuideSearch.search(it, query) }.orEmpty())
        }
    }

    fun startQuiz() {
        val e = engine ?: return
        _uiState.update { it.copy(step = e.restart()) }
    }

    fun answer(answer: Answer) {
        val e = engine ?: return
        if (_uiState.value.step !is GuideStep.Ask) return
        _uiState.update { it.copy(step = e.answer(answer)) }
    }

    /** Goes back one question. Returns false at the first question (caller should leave the quiz). */
    fun back(): Boolean {
        val e = engine ?: return false
        if (!e.back()) return false
        _uiState.update { it.copy(step = e.current) }
        return true
    }

    /** Shows a category's result directly (from search or a category card). */
    fun showCategory(categoryId: String) {
        viewModelScope.launch {
            val category = repository.guide().category(categoryId) ?: return@launch
            _uiState.update { it.copy(step = GuideStep.Done(GuideResult.Category(category))) }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as EcoConnectApp
                GuideViewModel(app.wasteGuideRepository)
            }
        }
    }
}
