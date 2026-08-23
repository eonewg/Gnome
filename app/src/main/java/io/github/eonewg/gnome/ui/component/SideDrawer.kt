package io.github.eonewg.gnome.ui.component

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.GridView
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Inventory2
import androidx.compose.material.icons.outlined.PhotoLibrary
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.feature.drawer.DrawerUiState
import io.github.eonewg.gnome.nav.ArchivedKey
import io.github.eonewg.gnome.nav.ExploreKey
import io.github.eonewg.gnome.nav.GnomeNavKey
import io.github.eonewg.gnome.nav.ResourcesKey
import io.github.eonewg.gnome.nav.SettingsKey
import io.github.eonewg.gnome.nav.TagKey
import io.github.eonewg.gnome.nav.TimelineKey
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import java.time.LocalDate

/**
 * The navigation drawer. Navigation itself happens at the hosting page: the
 * drawer only reports which destination is selected ([currentKey]) and
 * forwards pure click callbacks.
 */
@Composable
fun SideDrawer(
    uiState: DrawerUiState = DrawerUiState(),
    currentKey: GnomeNavKey? = null,
    onStatsClick: () -> Unit = {},
    onMemosClick: () -> Unit = {},
    onExploreClick: () -> Unit = {},
    onResourcesClick: () -> Unit = {},
    onArchivedClick: () -> Unit = {},
    onSettingsClick: () -> Unit = {},
    onTagClick: (String) -> Unit = {},
    onDateClick: (LocalDate) -> Unit = {},
) {
    val colors = GnomeDesign.colors
    val displayName = uiState.displayName.ifBlank { R.string.gnome.string }

    fun isSelected(route: GnomeNavKey): Boolean {
        return currentKey == route
    }

    fun isTagSelected(tag: String): Boolean {
        return currentKey is TagKey && currentKey.tag == tag
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxHeight()
            .background(colors.cardBackground)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 28.dp),
    ) {
        item {
            Column(
                modifier = Modifier.padding(start = 22.dp, top = 18.dp, end = 22.dp, bottom = 8.dp),
            ) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.headlineSmall,
                    color = colors.textPrimary,
                )

            }
        }

        item {
            Stats(
                memoCount = uiState.memoCount,
                tagCount = uiState.tags.size,
                days = uiState.days,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                onClick = onStatsClick,
            )
        }

        item {
            Heatmap(
                matrix = uiState.matrix,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(126.dp)
                    .padding(start = 18.dp, top = 10.dp, end = 18.dp, bottom = 10.dp),
                onDateClick = onDateClick,
            )
        }

        item { Spacer(Modifier.height(10.dp)) }

        item {
            DrawerNavigationItem(
                label = R.string.memos.string,
                icon = Icons.Outlined.GridView,
                selected = isSelected(TimelineKey),
                onClick = onMemosClick,
            )
        }

        if (uiState.isRemoteAccount) {
            item {
                DrawerNavigationItem(
                    label = R.string.explore.string,
                    icon = Icons.Outlined.Home,
                    selected = isSelected(ExploreKey),
                    onClick = onExploreClick,
                )
            }
        }

        item {
            DrawerNavigationItem(
                label = R.string.resources.string,
                icon = Icons.Outlined.PhotoLibrary,
                selected = isSelected(ResourcesKey),
                onClick = onResourcesClick,
            )
        }

        item {
            DrawerNavigationItem(
                label = R.string.archived.string,
                icon = Icons.Outlined.Inventory2,
                selected = isSelected(ArchivedKey),
                onClick = onArchivedClick,
            )
        }

        item {
            DrawerNavigationItem(
                label = R.string.settings.string,
                icon = Icons.Outlined.Settings,
                selected = isSelected(SettingsKey),
                onClick = onSettingsClick,
            )
        }

        item {
            HorizontalDivider(
                modifier = Modifier.padding(top = 18.dp, bottom = 18.dp),
                color = colors.divider.copy(alpha = 0.72f),
            )
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(R.string.tags.string, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                Text(uiState.tags.size.toString(), style = MaterialTheme.typography.labelLarge, color = colors.textSecondary)
            }
        }

        items(items = uiState.tags, key = { it }) { tag ->
            TagDrawerItem(
                tag = tag,
                selected = isTagSelected(tag),
                onClick = { onTagClick(tag) },
            )
        }
    }
}

@Composable
private fun DrawerNavigationItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = GnomeDesign.colors
    val shape = RoundedCornerShape(15.dp)
    val motionColors = rememberDrawerItemMotionColors(
        selected = selected,
        selectedContainerColor = colors.accent,
        selectedIconColor = MaterialTheme.colorScheme.onPrimary,
        selectedTextColor = MaterialTheme.colorScheme.onPrimary,
        unselectedIconColor = colors.textSecondary,
        unselectedTextColor = colors.textPrimary,
    )
    NavigationDrawerItem(
        label = { Text(label, style = MaterialTheme.typography.bodyLarge) },
        icon = { Icon(icon, contentDescription = null, modifier = Modifier.size(22.dp)) },
        selected = selected,
        onClick = onClick,
        shape = shape,
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = Color.Transparent,
            selectedIconColor = motionColors.icon,
            selectedTextColor = motionColors.text,
            unselectedContainerColor = Color.Transparent,
            unselectedIconColor = motionColors.icon,
            unselectedTextColor = motionColors.text,
        ),
        modifier = Modifier
            .padding(horizontal = 14.dp, vertical = 1.dp)
            .background(motionColors.container, shape)
            .height(48.dp),
    )
}