package io.github.eonewg.gnome.ui.component

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
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
import androidx.compose.runtime.getValue
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

    val shape = RoundedCornerShape(14.dp)
    val motionColors = rememberDrawerItemMotionColors(
        selected = selected,
        selectedContainerColor = colors.tagBackground,
        selectedIconColor = colors.tagForeground,
        selectedTextColor = colors.textPrimary,
        unselectedIconColor = colors.textSecondary,
        unselectedTextColor = colors.textPrimary,
    )
    NavigationDrawerItem(
        label = { Text(tag, style = MaterialTheme.typography.bodyLarge) },
        icon = { Icon(Icons.Outlined.Tag, contentDescription = null, modifier = Modifier.size(19.dp)) },
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
            .padding(horizontal = 14.dp)
            .background(motionColors.container, shape)
            .height(44.dp),
    )
}

internal data class DrawerItemMotionColors(
    val container: Color,
    val icon: Color,
    val text: Color,
)

@Composable
internal fun rememberDrawerItemMotionColors(
    selected: Boolean,
    selectedContainerColor: Color,
    selectedIconColor: Color,
    selectedTextColor: Color,
    unselectedIconColor: Color,
    unselectedTextColor: Color,
): DrawerItemMotionColors {
    val animationSpec = tween<Color>(
        durationMillis = DrawerItemColorDurationMillis,
        easing = FastOutSlowInEasing,
    )
    val container by animateColorAsState(
        targetValue = if (selected) selectedContainerColor else Color.Transparent,
        animationSpec = animationSpec,
        label = "DrawerItemContainer",
    )
    val icon by animateColorAsState(
        targetValue = if (selected) selectedIconColor else unselectedIconColor,
        animationSpec = animationSpec,
        label = "DrawerItemIcon",
    )
    val text by animateColorAsState(
        targetValue = if (selected) selectedTextColor else unselectedTextColor,
        animationSpec = animationSpec,
        label = "DrawerItemText",
    )
    return DrawerItemMotionColors(container = container, icon = icon, text = text)
}

private const val DrawerItemColorDurationMillis = 120
