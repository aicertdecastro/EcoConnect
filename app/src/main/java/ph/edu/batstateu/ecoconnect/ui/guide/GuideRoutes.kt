package ph.edu.batstateu.ecoconnect.ui.guide

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ph.edu.batstateu.ecoconnect.data.guide.GuideStep
import ph.edu.batstateu.ecoconnect.ui.viewmodel.GuideViewModel

@Composable
private fun Loading(modifier: Modifier = Modifier) {
    Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
}

/** Guide home: live search + entry to the question flow. */
@Composable
fun GuideHomeRoute(
    onStartQuestions: () -> Unit,
    onOpenCategory: (categoryId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuideViewModel = viewModel(factory = GuideViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val guide = state.guide ?: return Loading(modifier)

    GuideHomeScreen(
        community = guide.community,
        policy = guide.policy,
        query = state.query,
        results = state.results,
        categories = guide.categories,
        onQueryChange = viewModel::onQueryChange,
        onResultClick = { onOpenCategory(it.category.id) },
        onCategoryClick = { onOpenCategory(it.id) },
        onStartQuestions = onStartQuestions,
        modifier = modifier,
    )
}

/** Question flow; shows the result in place when finished. System back steps back one question. */
@Composable
fun GuideQuizRoute(
    onExit: () -> Unit,
    onRequestPickup: (categoryId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuideViewModel = viewModel(factory = GuideViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(state.loading) {
        if (!state.loading && state.step == null) viewModel.startQuiz()
    }
    val goBack = { if (!viewModel.back()) onExit() }
    BackHandler(onBack = goBack)

    val guide = state.guide
    when (val step = state.step) {
        is GuideStep.Ask -> GuideQuestionScreen(
            step = step,
            onAnswer = viewModel::answer,
            onBack = goBack,
            modifier = modifier,
        )
        is GuideStep.Done -> GuideResultScreen(
            result = step.result,
            generalNote = guide?.generalNote.orEmpty(),
            onBack = goBack,
            onRequestPickup = onRequestPickup,
            onStartOver = viewModel::startQuiz,
            modifier = modifier,
        )
        null -> Loading(modifier)
    }
}

/** Result for a category opened directly from search or a category card. */
@Composable
fun GuideResultRoute(
    categoryId: String,
    onBack: () -> Unit,
    onRequestPickup: (categoryId: String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: GuideViewModel = viewModel(factory = GuideViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    LaunchedEffect(categoryId) { viewModel.showCategory(categoryId) }

    val step = state.step as? GuideStep.Done ?: return Loading(modifier)
    GuideResultScreen(
        result = step.result,
        generalNote = state.guide?.generalNote.orEmpty(),
        onBack = onBack,
        onRequestPickup = onRequestPickup,
        modifier = modifier,
    )
}
