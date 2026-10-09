package com.nameisjayant.composevideos.media.reels.presentation

import androidx.compose.foundation.background
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.nameisjayant.composevideos.R
import com.nameisjayant.composevideos.media.reels.data.CurrentUser
import com.nameisjayant.composevideos.media.reels.data.Reel
import com.nameisjayant.composevideos.media.reels.data.ReelComment
import com.nameisjayant.composevideos.media.ui.MediaColors

/** Instagram-style comments sheet: scrolling list on top, composer pinned above the keyboard. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReelCommentsSheet(
    reel: Reel,
    likedCommentIds: Set<String>,
    onPost: (text: String, parentId: String?) -> Unit,
    onToggleLike: (commentId: String) -> Unit,
    onDelete: (commentId: String) -> Unit,
    onDismiss: () -> Unit,
) {
    var draft by rememberSaveable { mutableStateOf("") }
    var replyToId by rememberSaveable { mutableStateOf<String?>(null) }
    // Threads start collapsed, like Instagram; the user's own reply opens its thread.
    var expandedThreads by remember { mutableStateOf(emptySet<String>()) }
    val composerFocus = remember { FocusRequester() }

    // Resolved on every pass, so deleting the comment being replied to drops the reply bar.
    val replyTo = reel.comments.flatMap { listOf(it) + it.replies }.firstOrNull { it.id == replyToId }
    val rows = commentRows(reel.comments, expandedThreads)

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
            // Jump to a freshly posted comment or reply, wherever it landed.
            val commentIds = rows.mapNotNullTo(mutableSetOf()) { (it as? CommentListItem.Comment)?.comment?.id }
            var seenIds by remember { mutableStateOf(commentIds) }
            LaunchedEffect(commentIds) {
                val added = commentIds - seenIds
                seenIds = commentIds
                val index = rows.indexOfFirst { it is CommentListItem.Comment && it.comment.id in added }
                if (index >= 0) listState.animateScrollToItem(index)
            }
            if (rows.isEmpty()) {
                Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                    Text("No comments yet. Start the conversation.", color = MediaColors.Muted)
                }
            } else {
                LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                    modifier = Modifier.weight(1f),
                ) {
                    items(rows, key = { it.key }) { row ->
                        when (row) {
                            is CommentListItem.Comment -> CommentRow(
                                comment = row.comment,
                                isReply = row.isReply,
                                channel = reel.channel,
                                isLiked = row.comment.id in likedCommentIds,
                                onReply = {
                                    replyToId = row.comment.id
                                    // Tag who's being answered, as Instagram does, unless it's already there.
                                    val tag = "@${row.comment.author} "
                                    if (!draft.startsWith(tag)) draft = tag + draft
                                    composerFocus.requestFocus()
                                },
                                onToggleLike = { onToggleLike(row.comment.id) },
                                onDelete = { onDelete(row.comment.id) },
                                modifier = Modifier.animateItem(),
                            )

                            is CommentListItem.RepliesToggle -> RepliesToggle(
                                count = row.parent.replies.size,
                                expanded = row.expanded,
                                onClick = {
                                    expandedThreads = if (row.expanded) expandedThreads - row.parent.id
                                    else expandedThreads + row.parent.id
                                },
                                modifier = Modifier.animateItem(),
                            )
                        }
                    }
                }
            }

            HorizontalDivider(color = MediaColors.Hairline)
            if (replyTo != null) {
                ReplyingToBar(author = replyTo.author, onCancel = {
                    draft = draft.removePrefix("@${replyTo.author} ")
                    replyToId = null
                })
            }
            CommentComposer(
                text = draft,
                onTextChange = { draft = it },
                placeholder = if (replyTo != null) "Reply to ${replyTo.author}…" else "Add a comment…",
                onPost = {
                    // A reply lands in its top-level comment's thread; make sure that's open.
                    val thread = reel.comments.firstOrNull { top -> top.id == replyToId || top.replies.any { it.id == replyToId } }
                    if (thread != null) expandedThreads = expandedThreads + thread.id
                    onPost(draft, thread?.let { replyToId })
                    draft = ""
                    replyToId = null
                },
                focusRequester = composerFocus,
                modifier = Modifier.navigationBarsPadding(),
            )
        }
    }
}

/** One line in the comments list: a comment (top-level or reply) or a thread's show/hide toggle. */
private sealed interface CommentListItem {
    val key: String

    data class Comment(val comment: ReelComment, val isReply: Boolean) : CommentListItem {
        override val key get() = comment.id
    }

    data class RepliesToggle(val parent: ReelComment, val expanded: Boolean) : CommentListItem {
        override val key get() = "${parent.id}_replies"
    }
}

/** Pinned first, then each comment followed by its thread's toggle and, when open, its replies. */
private fun commentRows(comments: List<ReelComment>, expanded: Set<String>): List<CommentListItem> = buildList {
    for (top in comments.sortedByDescending { it.isPinned }) {
        add(CommentListItem.Comment(top, isReply = false))
        if (top.replies.isEmpty()) continue
        val isOpen = top.id in expanded
        if (isOpen) top.replies.forEach { add(CommentListItem.Comment(it, isReply = true)) }
        add(CommentListItem.RepliesToggle(top, isOpen))
    }
}

/** Replies are indented past the parent's avatar, like Instagram. */
private val ReplyIndent = 62.dp

@Composable
private fun CommentRow(
    comment: ReelComment,
    isReply: Boolean,
    channel: String,
    isLiked: Boolean,
    onReply: () -> Unit,
    onToggleLike: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(modifier.padding(start = if (isReply) ReplyIndent else 16.dp, end = 4.dp)) {
        Avatar(comment.author, size = if (isReply) 26.dp else 34.dp)
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            if (comment.isPinned) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 2.dp)) {
                    Icon(
                        painter = painterResource(R.drawable.ic_pin),
                        contentDescription = null,
                        tint = MediaColors.Muted,
                        modifier = Modifier.size(12.dp),
                    )
                    Text(
                        text = "Pinned by $channel",
                        color = MediaColors.Muted,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier.padding(start = 4.dp),
                    )
                }
            }
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
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.offset(x = (-8).dp),
            ) {
                CommentTextAction("Reply", onClick = onReply)
                if (comment.isMine) CommentTextAction("Delete", onClick = onDelete)
            }
        }
        CommentLikeButton(
            isLiked = isLiked,
            count = comment.likeCount + if (isLiked) 1 else 0,
            onClick = onToggleLike,
        )
    }
}

@Composable
private fun CommentTextAction(label: String, onClick: () -> Unit) {
    Text(
        text = label,
        color = MediaColors.Muted,
        style = MaterialTheme.typography.labelMedium,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 6.dp),
    )
}

/** Small heart over its count on the right of each comment. */
@Composable
private fun CommentLikeButton(isLiked: Boolean, count: Int, onClick: () -> Unit) {
    val haptics = LocalHapticFeedback.current
    val tint by animateColorAsState(if (isLiked) LikeRed else MediaColors.Muted, label = "commentLikeTint")
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClickLabel = if (isLiked) "Unlike comment" else "Like comment") {
                haptics.performHapticFeedback(if (isLiked) HapticFeedbackType.ToggleOff else HapticFeedbackType.ToggleOn)
                onClick()
            }
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Icon(
            painter = painterResource(if (isLiked) R.drawable.ic_heart_filled else R.drawable.ic_heart),
            contentDescription = null,
            tint = tint,
            modifier = Modifier.size(16.dp),
        )
        if (count > 0) {
            Text(
                text = formatCount(count),
                color = MediaColors.Muted,
                style = MaterialTheme.typography.labelSmall,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

/** "—— View 2 replies" under a comment, or "—— Hide replies" once the thread is open. */
@Composable
private fun RepliesToggle(count: Int, expanded: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .padding(start = ReplyIndent)
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .padding(vertical = 2.dp, horizontal = 4.dp),
    ) {
        Box(
            Modifier
                .size(width = 24.dp, height = 1.dp)
                .background(MediaColors.Muted),
        )
        Text(
            text = when {
                expanded -> "Hide replies"
                count == 1 -> "View 1 reply"
                else -> "View $count replies"
            },
            color = MediaColors.Muted,
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
}

@Composable
private fun ReplyingToBar(author: String, onCancel: () -> Unit) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .background(MediaColors.SurfaceRaised)
            .padding(start = 16.dp),
    ) {
        Text(
            text = "Replying to $author",
            color = MediaColors.Muted,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = onCancel) {
            Icon(
                painter = painterResource(R.drawable.ic_close),
                contentDescription = "Cancel reply",
                tint = MediaColors.Muted,
                modifier = Modifier.size(16.dp),
            )
        }
    }
}

@Composable
private fun Avatar(name: String, size: Dp = 34.dp) {
    Box(
        contentAlignment = Alignment.Center,
        modifier = Modifier
            .size(size)
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
private fun CommentComposer(
    text: String,
    onTextChange: (String) -> Unit,
    placeholder: String,
    onPost: () -> Unit,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
) {
    val canPost = text.isNotBlank()
    val post = { if (canPost) onPost() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier.padding(start = 16.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
    ) {
        Avatar(CurrentUser)
        TextField(
            value = text,
            onValueChange = onTextChange,
            placeholder = { Text(placeholder, color = MediaColors.Muted) },
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
            modifier = Modifier
                .weight(1f)
                .focusRequester(focusRequester),
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
