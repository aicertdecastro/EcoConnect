package ph.edu.batstateu.ecoconnect.ui.guide

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.data.guide.Answer
import ph.edu.batstateu.ecoconnect.data.guide.GuideStep
import ph.edu.batstateu.ecoconnect.ui.preview.FakeData
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import ph.edu.batstateu.ecoconnect.ui.theme.PillShape

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GuideQuestionScreen(
    step: GuideStep.Ask,
    onAnswer: (Answer) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Which bin?") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "Back")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                ),
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .windowInsetsPadding(WindowInsets.navigationBars)
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Button(
                    onClick = { onAnswer(Answer.YES) },
                    shape = PillShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text("Yes", style = MaterialTheme.typography.titleMedium) }
                OutlinedButton(
                    onClick = { onAnswer(Answer.NO) },
                    shape = PillShape,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(52.dp),
                ) { Text("No", style = MaterialTheme.typography.titleMedium) }
                TextButton(
                    onClick = { onAnswer(Answer.UNSURE) },
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("I'm not sure") }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            Text(
                "Question ${step.number} of up to ${step.maxQuestions}",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 8.dp),
            )
            LinearProgressIndicator(
                progress = { step.number.toFloat() / step.maxQuestions },
                modifier = Modifier.fillMaxWidth(),
                trackColor = MaterialTheme.colorScheme.primaryContainer,
            )
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 24.dp),
                shape = MaterialTheme.shapes.large,
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp),
            ) {
                Column(Modifier.padding(24.dp)) {
                    Text(step.question.text, style = MaterialTheme.typography.headlineSmall)
                    step.question.hint?.let {
                        Spacer(Modifier.height(12.dp))
                        Text(it, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }
        }
    }
}

@Preview(showSystemUi = true, name = "Guide question")
@Composable
private fun GuideQuestionPreview() {
    EcoConnectTheme {
        GuideQuestionScreen(
            step = GuideStep.Ask(FakeData.guide.questions.first(), number = 1, maxQuestions = 4),
            onAnswer = {},
            onBack = {},
        )
    }
}
