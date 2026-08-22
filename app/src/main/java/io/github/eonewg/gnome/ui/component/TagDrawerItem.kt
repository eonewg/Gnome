package io.github.eonewg.gnome.ui.component

import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Tag
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import io.github.eonewg.gnome.ui.theme.GnomeDesign

@Composable
fun TagDrawerItem(
    tag: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colors = GnomeDesign.colors

    NavigationDrawerItem(
        label = { Text(tag, style = MaterialTheme.typography.bodyLarge) },
        icon = { Icon(Icons.Outlined.Tag, contentDescription = null, modifier = Modifier.size(19.dp)) },
        selected = selected,
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = colors.tagBackground,
            selectedIconColor = colors.tagForeground,
            selectedTextColor = colors.textPrimary,
            unselectedContainerColor = Color.Transparent,
            unselectedIconColor = colors.textSecondary,
            unselectedTextColor = colors.textPrimary,
        ),
        modifier = Modifier.padding(horizontal = 14.dp).height(44.dp),
    )
}