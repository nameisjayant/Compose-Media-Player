package com.nameisjayant.composevideos.media.navigation

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandHorizontally
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import com.nameisjayant.composevideos.media.ui.MediaColors

private val BarShape = RoundedCornerShape(percent = 50)

@Composable
fun MediaBottomBar(
    navController: NavHostController,
    currentDestination: NavDestination?,
    modifier: Modifier = Modifier,
) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .fillMaxWidth()
            // Floating pill: lifted above the gesture/nav bar and centred, not edge to edge.
            .navigationBarsPadding()
            .padding(bottom = 14.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .shadow(elevation = 24.dp, shape = BarShape, ambientColor = Color.Black, spotColor = Color.Black)
                .clip(BarShape)
                // Glass: a smoky translucent tint, plus a rim that catches light along the top edge.
                .background(MediaColors.Glass)
                .border(
                    width = 1.dp,
                    brush = Brush.verticalGradient(
                        listOf(Color.White.copy(alpha = 0.22f), Color.White.copy(alpha = 0.04f)),
                    ),
                    shape = BarShape,
                )
                .padding(6.dp),
        ) {
            MediaTab.entries.forEach { tab ->
                BarItem(
                    tab = tab,
                    selected = currentDestination.isOn(tab),
                    onClick = {
                        navController.navigate(tab.route) {
                            // Standard bottom-nav behaviour: one copy per tab, state kept per tab.
                            popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                            launchSingleTop = true
                            restoreState = true
                        }
                    },
                )
            }
        }
    }
}

@Composable
private fun BarItem(
    tab: MediaTab,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val springy = spring<Color>(stiffness = Spring.StiffnessMediumLow)
    val container by animateColorAsState(
        if (selected) MediaColors.Accent else Color.Transparent,
        springy,
        label = "container",
    )
    val content by animateColorAsState(
        if (selected) MediaColors.Canvas else MediaColors.Muted,
        springy,
        label = "content",
    )

    // The selected tab grows into a filled capsule with its label; the others stay as quiet icons.
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .height(48.dp)
            .clip(BarShape)
            .background(container)
            .selectable(
                selected = selected,
                onClick = onClick,
                role = Role.Tab,
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(color = MediaColors.Accent),
            )
            .padding(horizontal = 18.dp),
    ) {
        Icon(
            painter = painterResource(tab.icon),
            contentDescription = tab.label,
            tint = content,
            modifier = Modifier.size(22.dp),
        )
        AnimatedVisibility(
            visible = selected,
            enter = fadeIn() + expandHorizontally(),
            exit = fadeOut() + shrinkHorizontally(),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Spacer(Modifier.width(8.dp))
                Text(tab.label, color = content, style = MaterialTheme.typography.labelLarge)
            }
        }
    }
}

fun NavDestination?.isOn(tab: MediaTab): Boolean =
    this?.hierarchy?.any { it.hasRoute(tab.route::class) } == true
