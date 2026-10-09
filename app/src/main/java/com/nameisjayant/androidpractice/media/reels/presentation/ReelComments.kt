package com.nameisjayant.androidpractice.media.reels.presentation

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.nameisjayant.androidpractice.R
import com.nameisjayant.androidpractice.media.reels.data.Reel
import com.nameisjayant.androidpractice.media.reels.data.ReelComment
import com.nameisjayant.androidpractice.media.ui.MediaColors

/** Instagram-style comments sheet: scrolling list on top, composer pinned above the keyboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelCommentsSheet(
    reel: Reel,
    onPost: (String) -> Unit,
    onDismiss: () -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MediaColors.Surface,
        contentColor = MediaColors.OnCanvas,
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .fillMaxHeight(0.7f)
                .imePadding(),
        ) {
            Text(
                text = "Comments",
                style = MaterialTheme.typography.titleMedium,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
            )
            HorizontalDivider(color = MediaColors.Hairline)

            val listState = rememberLazyListState()
            // Jump to a freshly posted comment.
            LaunchedEffect(reel.comments.size) {
                if (reel.comments.isNotEmpty()) listState.animateScrollToItem(reel.comments.lastIndex)
            }
            if (reel.comments.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No comments yet. Start the conversation.", color = MediaColors.Muted)
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(reel.comments, key = { it.id }) { CommentRow(it) }
                }
            }

            HorizontalDivider(color = MediaColors.Hairline)
            CommentComposer(onPost = onPost, modifier = Modifier.navigationBarsPadding())
        }
    }
}

@Composable
private fun CommentRow(comment: ReelComment) {
    Row(Modifier.padding(horizontal = 16.dp)) {
        Avatar(comment.author)
        Column(Modifier.padding(start = 12.dp)) {
            Text(
                text = buildAnnotatedString {
                    withStyle(SpanStyle(fontWeight = FontWeight.SemiBold)) { append(comment.author) }
                    withStyle(SpanStyle(color = MediaColors.Muted)) { append("  ${comment.postedAgo}") }
                },
                style = MaterialTheme.typography.bodySmall,
            )
            Text(
                text = comment.text,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@Composable
private fun Avatar(name: String) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(MediaColors.SurfaceRaised)
            .border(1.dp, MediaColors.Hairline, CircleShape),
    ) {
        Text(
            text = name.first().uppercase(),
            color = MediaColors.Accent,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun CommentComposer(onPost: (String) -> Unit, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    val canPost = text.isNotBlank()
    val post = {
        if (canPost) {
            onPost(text)
            text = ""
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Avatar("you")
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text("Add a comment…", color = MediaColors.Muted) },
            maxLines = 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { post() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
                cursorColor = MediaColors.Accent,
            ),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = post, enabled = canPost) {
            Icon(
                painter = painterResource(R.drawable.ic_send),
                contentDescription = "Post comment",
                tint = if (canPost) MediaColors.Accent else MediaColors.Muted,
                modifier = Modifier.size(22.dp),
            )
        }
    }
}
