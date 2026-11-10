package ph.edu.batstateu.ecoconnect.ui.guide

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.HelpOutline
import androidx.compose.material.icons.automirrored.outlined.KeyboardArrowRight
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Verified
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import ph.edu.batstateu.ecoconnect.data.guide.GuideCategory
import ph.edu.batstateu.ecoconnect.data.guide.SearchHit
import ph.edu.batstateu.ecoconnect.navigation.EcoDestinations
import ph.edu.batstateu.ecoconnect.ui.components.EcoBottomBar
import ph.edu.batstateu.ecoconnect.ui.components.WasteCategory
import ph.edu.batstateu.ecoconnect.ui.preview.FakeData
import ph.edu.batstateu.ecoconnect.ui.theme.EcoConnectTheme
import ph.edu.batstateu.ecoconnect.ui.theme.EcoGreen800
import ph.edu.batstateu.ecoconnect.ui.theme.HeaderShape
import ph.edu.batstateu.ecoconnect.ui.theme.PillShape

@Composable
fun GuideHomeScreen(
    community: String,
    policy: String,
    query: String,
    results: List<SearchHit>,
    categories: List<GuideCategory>,
    onQueryChange: (String) -> Unit,
    onResultClick: (SearchHit) -> Unit,
    onCategoryClick: (GuideCategory) -> Unit,
    onStartQuestions: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState()),
    ) {
        // Header
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(EcoGreen800, HeaderShape)
                .windowInsetsPadding(WindowInsets.statusBars)
                .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
        ) {
            Text("Waste Guide", style = MaterialTheme.typography.headlineSmall, color = Color.White)
            Text(community, style = MaterialTheme.typography.bodyMedium, color = Color.White.copy(alpha = 0.85f))
            Surface(
                shape = PillShape,
                color = Color.White.copy(alpha = 0.16f),
                contentColor = Color.White,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Row(
                    Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Outlined.Verified, contentDescription = null, modifier = Modifier.size(16.dp))
                    Text(policy, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(start = 6.dp))
                }
            }
        }

        Column(Modifier.padding(horizontal = 20.dp)) {
            OutlinedTextField(
                value = query,
                onValueChange = onQueryChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 20.dp),
                placeholder = { Text("Search an item, e.g. sachet, saging, baterya") },
                leadingIcon = { Icon(Icons.Outlined.Search, contentDescription = null) },
                trailingIcon = {
                    if (query.isNotEmpty()) {
                        IconButton(onClick = { onQueryChange("") }) {
                            Icon(Icons.Outlined.Close, contentDescription = "Clear search")
                        }
                    }
                },
                singleLine = true,
                shape = PillShape,
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface,
                    focusedContainerColor = MaterialTheme.colorScheme.surface,
                ),
            )

            QuestionsButton(onClick = onStartQuestions, modifier = Modifier.padding(top = 12.dp))

            if (query.isNotBlank()) {
                SectionTitle(if (results.isEmpty()) "No matches" else "Results")
                if (results.isEmpty()) {
                    Text(
                        "We couldn't find \"$query\". Try another word, or answer a few questions above.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    results.forEach { hit -> SearchResultCard(hit, onClick = { onResultClick(hit) }) }
                }
            } else {
                SectionTitle("Categories")
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    categories.forEach { c -> CategoryCard(c, onClick = { onCategoryClick(c) }) }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = MaterialTheme.typography.titleMedium,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(top = 24.dp, bottom = 12.dp),
    )
}

@Composable
private fun QuestionsButton(onClick: () -> Unit, modifier: Modifier = Modifier) {
    Card(
        onClick = onClick,
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer,
            contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.AutoMirrored.Outlined.HelpOutline, contentDescription = null)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text("Not sure? Answer a few questions", style = MaterialTheme.typography.titleSmall)
                Text("Up to 4 yes/no questions", style = MaterialTheme.typography.bodySmall)
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun CategoryIcon(category: WasteCategory?) {
    Box(
        modifier = Modifier
            .size(44.dp)
            .background(category?.accent ?: MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.shapes.small),
        contentAlignment = Alignment.Center,
    ) {
        if (category != null) {
            Icon(category.icon, contentDescription = null, tint = category.strong, modifier = Modifier.size(24.dp))
        }
    }
}

@Composable
private fun CategoryCard(category: GuideCategory, onClick: () -> Unit) {
    val style = WasteCategory.fromId(category.id)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(style)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(category.name, style = MaterialTheme.typography.titleSmall, color = style?.strong ?: Color.Unspecified)
                Text(
                    category.examples.joinToString(),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    scheduleSummary(category),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
private fun SearchResultCard(hit: SearchHit, onClick: () -> Unit) {
    val style = WasteCategory.fromId(hit.category.id)
    Card(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            CategoryIcon(style)
            Column(
                Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp),
            ) {
                Text(hit.item.name, style = MaterialTheme.typography.titleSmall)
                CategoryChip(hit.category, modifier = Modifier.padding(top = 4.dp))
                hit.item.tip?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Icon(Icons.AutoMirrored.Outlined.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@Composable
internal fun CategoryChip(category: GuideCategory, modifier: Modifier = Modifier) {
    val style = WasteCategory.fromId(category.id)
    Surface(
        modifier = modifier,
        shape = PillShape,
        color = style?.accent ?: MaterialTheme.colorScheme.surfaceVariant,
        contentColor = style?.strong ?: MaterialTheme.colorScheme.onSurface,
    ) {
        Text(
            category.name,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 3.dp),
        )
    }
}

internal fun scheduleSummary(category: GuideCategory): String = when {
    !category.requestable -> "Not collected door-to-door"
    category.collectionDays.isEmpty() -> "No published pickup day"
    else -> "Pickup: " + category.collectionDays.joinToString { it.shortLabel }
}

// ---------- Previews ----------

@Preview(showSystemUi = true, name = "Guide home")
@Composable
private fun GuideHomePreview() {
    EcoConnectTheme {
        Scaffold(
            contentWindowInsets = WindowInsets(0),
            bottomBar = { EcoBottomBar(currentRoute = EcoDestinations.WASTE_GUIDE, onNavigate = {}) },
        ) { padding ->
            GuideHomeScreen(
                community = FakeData.guide.community,
                policy = FakeData.guide.policy,
                query = "",
                results = emptyList(),
                categories = FakeData.guideCategories,
                onQueryChange = {}, onResultClick = {}, onCategoryClick = {}, onStartQuestions = {},
                modifier = Modifier.padding(padding),
            )
        }
    }
}

@Preview(showSystemUi = true, name = "Guide home – searching")
@Composable
private fun GuideHomeSearchPreview() {
    EcoConnectTheme {
        GuideHomeScreen(
            community = FakeData.guide.community,
            policy = FakeData.guide.policy,
            query = "shampoo",
            results = FakeData.searchHits,
            categories = FakeData.guideCategories,
            onQueryChange = {}, onResultClick = {}, onCategoryClick = {}, onStartQuestions = {},
        )
    }
}
