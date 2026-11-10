package ph.edu.batstateu.ecoconnect.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.LocalShipping
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.data.guide.GuideResult
import ph.edu.batstateu.ecoconnect.ui.components.UnsureStyle
import ph.edu.batstateu.ecoconnect.ui.components.WasteCategory
import ph.edu.batstateu.ecoconnect.ui.preview.FakeData
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import ph.edu.batstateu.ecoconnect.ui.theme.PillShape

/**
 * Shows where an item goes. The "Request pickup" button appears only for requestable
 * categories and only when [onRequestPickup] is given; SPECIAL and UNSURE are advice only.
 * [onStartOver] is null when the result didn't come from the questions.
 */
@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun GuideResultScreen(
    result: GuideResult,
    generalNote: String,
    onBack: () -> Unit,
    onRequestPickup: ((categoryId: String) -> Unit)?,
    modifier: Modifier = Modifier,
    onStartOver: (() -> Unit)? = null,
) {
    val header = when (result) {
        is GuideResult.Category -> {
            val style = WasteCategory.fromId(result.category.id)
            Header(
                title = result.category.name,
                eyebrow = "This goes in",
                icon = style?.icon ?: UnsureStyle.icon,
                strong = style?.strong ?: UnsureStyle.strong,
                accent = style?.accent ?: UnsureStyle.accent,
            )
        }
        is GuideResult.Unsure -> Header(
            title = result.advice.title,
            eyebrow = "Result",
            icon = UnsureStyle.icon,
            strong = UnsureStyle.strong,
            accent = UnsureStyle.accent,
        )
    }
    val requestableId = (result as? GuideResult.Category)?.category?.takeIf { it.requestable }?.id

    Scaffold(
        modifier = modifier,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Waste Guide") },
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
                if (requestableId != null && onRequestPickup != null) {
                    Button(
                        onClick = { onRequestPickup(requestableId) },
                        shape = PillShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        Icon(Icons.Outlined.LocalShipping, contentDescription = null, modifier = Modifier.size(20.dp))
                        Text("Request pickup", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 8.dp))
                    }
                }
                if (onStartOver != null) {
                    OutlinedButton(
                        onClick = onStartOver,
                        shape = PillShape,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                    ) { Text("Start over") }
                }
            }
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            HeaderCard(header)

            when (result) {
                is GuideResult.Category -> {
                    val category = result.category
                    InfoSection("How to dispose") { Bullets(category.instructions) }
                    InfoSection("Collection days") {
                        if (category.collectionDays.isNotEmpty()) {
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                category.collectionDays.forEach { day ->
                                    Surface(shape = PillShape, color = header.accent, contentColor = header.strong) {
                                        Text(
                                            day.label,
                                            style = MaterialTheme.typography.labelLarge,
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                        )
                                    }
                                }
                            }
                        }
                        category.scheduleNote?.let {
                            Text(it, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
                        }
                        if (!category.requestable) {
                            Text(
                                "Not included in door-to-door pickup requests.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = header.strong,
                                modifier = Modifier.padding(top = 4.dp),
                            )
                        }
                    }
                    if (category.examples.isNotEmpty()) {
                        InfoSection("Examples") {
                            Text(category.examples.joinToString(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                is GuideResult.Unsure -> InfoSection("What to do") { Bullets(result.advice.instructions) }
            }

            GeneralNote(generalNote)
            Spacer(Modifier.height(8.dp))
        }
    }
}

private data class Header(
    val title: String,
    val eyebrow: String,
    val icon: ImageVector,
    val strong: Color,
    val accent: Color,
)

@Composable
private fun HeaderCard(header: Header) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = header.strong,
        contentColor = Color.White,
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(Color.White.copy(alpha = 0.18f), MaterialTheme.shapes.medium),
                contentAlignment = Alignment.Center,
            ) {
                Icon(header.icon, contentDescription = null, modifier = Modifier.size(32.dp))
            }
            Column(Modifier.padding(start = 16.dp)) {
                Text(header.eyebrow, style = MaterialTheme.typography.labelLarge, color = Color.White.copy(alpha = 0.85f))
                Text(header.title, style = MaterialTheme.typography.headlineSmall)
            }
        }
    }
}

@Composable
private fun InfoSection(title: String, content: @Composable () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun Bullets(lines: List<String>) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        lines.forEach { line ->
            Row {
                Text("•", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(end = 8.dp))
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun GeneralNote(note: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small)
            .padding(12.dp),
    ) {
        Icon(
            Icons.Outlined.Info,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(end = 8.dp)
                .size(18.dp),
        )
        Text(note, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

// ---------- Previews ----------

@Preview(showSystemUi = true, name = "Result – Recyclable (requestable)")
@Composable
private fun ResultRecyclablePreview() {
    EcoConnectTheme {
        GuideResultScreen(
            result = GuideResult.Category(FakeData.guideCategories[1]),
            generalNote = FakeData.guide.generalNote,
            onBack = {}, onRequestPickup = {}, onStartOver = {},
        )
    }
}

@Preview(showSystemUi = true, name = "Result – Residual")
@Composable
private fun ResultResidualPreview() {
    EcoConnectTheme {
        GuideResultScreen(
            result = GuideResult.Category(FakeData.guideCategories[2]),
            generalNote = FakeData.guide.generalNote,
            onBack = {}, onRequestPickup = {},
        )
    }
}

@Preview(showSystemUi = true, name = "Result – Special (no button)")
@Composable
private fun ResultSpecialPreview() {
    EcoConnectTheme {
        GuideResultScreen(
            result = GuideResult.Category(FakeData.guideCategories[3]),
            generalNote = FakeData.guide.generalNote,
            onBack = {}, onRequestPickup = {}, onStartOver = {},
        )
    }
}

@Preview(showSystemUi = true, name = "Result – Unsure (no button)")
@Composable
private fun ResultUnsurePreview() {
    EcoConnectTheme {
        GuideResultScreen(
            result = GuideResult.Unsure(FakeData.guide.unsure),
            generalNote = FakeData.guide.generalNote,
            onBack = {}, onRequestPickup = {}, onStartOver = {},
        )
    }
}
