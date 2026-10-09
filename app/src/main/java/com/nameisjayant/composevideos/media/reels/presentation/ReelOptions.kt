package com.nameisjayant.composevideos.media.reels.presentation

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nameisjayant.composevideos.R
import com.nameisjayant.composevideos.media.ui.MediaColors

/** Instagram's report red, softer than the like red so it reads as a warning, not a heart. */
private val ReportRed = Color(0xFFED4956)

/**
 * The ⋮ sheet on a reel: Not interested, or Report, which swaps the sheet to the list of
 * reasons rather than stacking a second sheet on top.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelOptionsSheet(
    onNotInterested: () -> Unit,
    onReport: (reason: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var choosingReason by rememberSaveable { mutableStateOf(false) }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MediaColors.Surface,
        contentColor = MediaColors.OnCanvas,
    ) {
        AnimatedContent(targetState = choosingReason, label = "reelOptions") { reasons ->
            Column(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(bottom = 12.dp),
            ) {
                if (reasons) {
                    Text(
                        text = "Why are you reporting this reel?",
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 4.dp),
                    )
                    Text(
                        text = "Your report is anonymous.",
                        color = MediaColors.Muted,
                        style = MaterialTheme.typography.bodySmall,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                    )
                    HorizontalDivider(color = MediaColors.Hairline)
                    ReportReasons.forEach { reason ->
                        OptionRow(label = reason, onClick = { onReport(reason) })
                    }
                } else {
                    OptionRow(
                        label = "Not interested",
                        icon = R.drawable.ic_not_interested,
                        onClick = onNotInterested,
                    )
                    OptionRow(
                        label = "Report",
                        icon = R.drawable.ic_flag,
                        tint = ReportRed,
                        onClick = { choosingReason = true },
                    )
                }
            }
        }
    }
}

@Composable
private fun OptionRow(
    label: String,
    onClick: () -> Unit,
    icon: Int? = null,
    tint: Color = MediaColors.OnCanvas,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        if (icon != null) {
            Icon(
                painter = painterResource(icon),
                contentDescription = null,
                tint = tint,
                modifier = Modifier
                    .padding(end = 16.dp)
                    .size(22.dp),
            )
        }
        Text(text = label, color = tint, style = MaterialTheme.typography.bodyLarge)
    }
}
