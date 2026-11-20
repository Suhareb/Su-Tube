package com.decio.skipii

import android.util.Log

import androidx.compose.foundation.background
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsBottomHeight
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.Velocity
import android.view.inputmethod.InputMethodManager
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material.icons.rounded.Search
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ThumbUp
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.PushPin
import androidx.compose.material.icons.rounded.ThumbUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Comments for the open video: first page, paging, per-comment replies, likes and posting. */
@Stable
class CommentsState(private val scope: CoroutineScope) {
    /** The watch page the first comments come from also carries the video's chapters. */
    var onChapters: ((videoId: String, chapters: List<Chapter>) -> Unit)? = null

    private var videoId: String? = null
    var comments by mutableStateOf<List<Comment>>(emptyList())
        private set
    var total by mutableStateOf(-1)
        private set
    var disabled by mutableStateOf(false)
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
    /** YouTube's sort menu tokens (Top, Newest first) for this video; fewer than two = no sorting offered. */
    var sortTokens by mutableStateOf<List<String>>(emptyList())
        private set
    var newestFirst by mutableStateOf(false)
        private set
    private var next: String? = null
    val hasMore get() = next != null

    /** Replies already fetched, by comment id. */
    val replies = mutableStateMapOf<String, List<Comment>>()
    /** Comments the account has liked: from YouTube, plus taps this session. */
    val liked = mutableStateMapOf<String, Boolean>()

    /** Optimistic like toggle; reverts with a note if YouTube refuses. */
    fun toggleLike(comment: Comment, onError: (String) -> Unit) {
        val id = videoId ?: return
        val now = liked[comment.id] != true
        liked[comment.id] = now
        scope.launch {
            runCatching { CommentPosting.setLiked(id, comment.id, now) }
                .onFailure { liked[comment.id] = !now; onError(it.message ?: str(R.string.comments_error_like)) }
        }
    }
    val repliesLoading = mutableStateMapOf<String, Boolean>()
    /** Replies (by id) whose answers are folded away (false); a reply that was answered shows them under it to begin with. */
    val nestedOpen = mutableStateMapOf<String, Boolean>()
    /** The token for the next batch of replies under a comment (by id); absent when all are in. */
    val repliesNext = mutableStateMapOf<String, String>()

    /** The search over the loaded comments (the search button in the sheet's title). */
    var searchOpen by mutableStateOf(false)
    var searchQuery by mutableStateOf("")

    /** Whether [comment] shows while searching: its text or author, or any open reply's. */
    fun matchesSearch(comment: Comment): Boolean {
        val q = if (searchOpen && CommentPrefs.search) searchQuery.trim() else ""
        if (q.isEmpty()) return true
        fun hit(c: Comment) = c.text.contains(q, ignoreCase = true) || c.author.contains(q, ignoreCase = true) || c.name?.contains(q, ignoreCase = true) == true
        return hit(comment) || replies[comment.id]?.any { hit(it) } == true
    }

    private var job: Job? = null

    /** A new video opened: drop the old comments now; [load] follows once its data is in. */
    fun clearFor(id: String) {
        job?.cancel()
        videoId = id
        comments = emptyList(); total = -1; disabled = false; error = null; next = null
        sortTokens = emptyList(); newestFirst = false
        replies.clear(); repliesLoading.clear(); nestedOpen.clear(); repliesNext.clear(); replyingTo = null; draft = ""; postError = null
        searchOpen = false; searchQuery = ""
        loading = true
    }

    fun load(id: String) {
        videoId = id
        replyingTo = null
        draft = ""
        postError = null
        comments = emptyList()
        total = -1
        disabled = false
        error = null
        next = null
        sortTokens = emptyList()
        newestFirst = false
        replies.clear()
        repliesLoading.clear()
        nestedOpen.clear()
        repliesNext.clear()
        searchOpen = false
        searchQuery = ""
        liked.clear()
        loading = true
        job?.cancel()
        val startedAt = android.os.SystemClock.elapsedRealtime()
        job = scope.launch {
            runCatching { CommentFeed.first(id) }
                .onSuccess { page ->
                    Log.i("Skipii", "comments for $id in ${android.os.SystemClock.elapsedRealtime() - startedAt} ms")
                    if (videoId != id) return@onSuccess
                    comments = page.comments
                    total = page.total
                    disabled = page.disabled
                    next = page.next
                    sortTokens = page.sortTokens
                    arrived(page.comments)
                    if (page.chapters.isNotEmpty()) onChapters?.invoke(id, page.chapters)
                }
                .onFailure { if (videoId == id) error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    /** Top comments (first sort token) or Newest first (second); reloads the list in that order. */
    fun chooseSort(newest: Boolean) {
        val id = videoId ?: return
        if (newest == newestFirst) return
        val token = sortTokens.getOrNull(if (newest) 1 else 0) ?: return
        newestFirst = newest
        job?.cancel()
        comments = emptyList(); next = null; error = null
        replies.clear(); repliesLoading.clear()
        loading = true
        job = scope.launch {
            runCatching { CommentFeed.more(token) }
                .onSuccess { page ->
                    if (videoId != id) return@onSuccess
                    comments = page.comments
                    next = page.next
                    if (page.total >= 0) total = page.total
                    arrived(page.comments)
                }
                .onFailure { if (videoId == id) { error = YouTube.friendlyError(it); newestFirst = !newest } }
            loading = false
        }
    }

    fun loadMore() {
        val id = videoId ?: return
        val page = next ?: return
        if (loading) return
        loading = true
        job = scope.launch {
            runCatching { CommentFeed.more(page) }
                .onSuccess { more ->
                    if (videoId != id) return@onSuccess
                    // Pages can overlap at the boundary; keep ids unique for the list keys.
                    val seen = comments.mapTo(HashSet()) { it.id }
                    comments = comments + more.comments.filter { seen.add(it.id) }
                    next = more.next
                    arrived(more.comments)
                }
                .onFailure { if (videoId == id) error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    fun loadReplies(comment: Comment) {
        val id = videoId ?: return
        val page = comment.repliesToken ?: return
        if (repliesLoading[comment.id] == true || replies.containsKey(comment.id)) return
        repliesLoading[comment.id] = true
        scope.launch {
            runCatching { CommentFeed.more(page) }
                .onSuccess {
                    if (videoId == id) {
                        replies[comment.id] = it.comments
                        it.next?.let { next -> repliesNext[comment.id] = next }
                        arrived(it.comments)
                    }
                }
            repliesLoading.remove(comment.id)
        }
    }

    /** The next batch of replies under [comment], added below the ones showing. */
    fun loadMoreReplies(comment: Comment) {
        val id = videoId ?: return
        val token = repliesNext[comment.id] ?: return
        if (repliesLoading[comment.id] == true) return
        repliesLoading[comment.id] = true
        scope.launch {
            runCatching { CommentFeed.more(token) }
                .onSuccess { page ->
                    if (videoId == id) {
                        val have = replies[comment.id].orEmpty()
                        val seen = have.mapTo(HashSet()) { it.id }
                        replies[comment.id] = have + page.comments.filter { seen.add(it.id) }
                        val next = page.next
                        if (next != null) repliesNext[comment.id] = next else repliesNext.remove(comment.id)
                        arrived(page.comments)
                    }
                }
                .onFailure { repliesNext.remove(comment.id) }
            repliesLoading.remove(comment.id)
        }
    }

    /** New comments in: their liked state, and their authors' real names (looked up once each). */
    private fun arrived(page: List<Comment>) {
        page.forEach { if (it.liked) liked[it.id] = true }
        ChannelNames.resolve(scope, page.mapNotNullTo(LinkedHashSet()) { it.channelId })
    }

    fun hideReplies(comment: Comment) {
        replies.remove(comment.id)
        repliesNext.remove(comment.id)
    }

    // ── Posting (signed in) ──
    /** The comment being replied to, or null for a new top-level comment. */
    var replyingTo by mutableStateOf<Comment?>(null)
    var draft by mutableStateOf("")
    var posting by mutableStateOf(false)
        private set
    var postError by mutableStateOf<String?>(null)

    fun startReply(comment: Comment) {
        replyingTo = comment
        // Replies to a reply go to the same thread, addressed with an @mention (as on YouTube).
        if (comment.id.contains('.') && draft.isBlank()) draft = "${comment.author.trim()} "
    }

    fun post() {
        val id = videoId ?: return
        val text = draft.trim()
        if (text.isEmpty() || posting) return
        val target = replyingTo
        val threadId = target?.id?.substringBefore('.')
        posting = true
        postError = null
        scope.launch {
            runCatching {
                if (threadId == null) CommentPosting.comment(id, text) else CommentPosting.reply(id, threadId, text)
            }.onSuccess {
                val profile = Account.profile
                val mine = Comment(
                    id = "local-${System.nanoTime()}",
                    author = profile?.handle ?: profile?.name ?: str(R.string.comments_you),
                    channelId = null,
                    name = profile?.name,
                    avatar = profile?.avatar,
                    // English, as YouTube writes ages: shown through displayAge.
                    text = text, likes = 0, ago = "just now",
                    pinned = false, hearted = false, replyCount = 0, repliesToken = null,
                )
                if (threadId == null) {
                    comments = listOf(mine) + comments
                    if (total >= 0) total += 1
                } else {
                    replies[threadId] = replies[threadId].orEmpty() + mine
                }
                draft = ""
                replyingTo = null
            }.onFailure { postError = it.message ?: str(R.string.comments_error_post) }
            posting = false
        }
    }
}

/** The card under the video: count plus the top comment. Tap opens the full sheet. */
@Composable
fun CommentsCard(state: CommentsState, onOpen: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(24.dp)
    val first = state.comments.firstOrNull()
    Column(
        modifier = Modifier
            .padding(top = 16.dp)
            .fillMaxWidth()
            .pressable(enabled = state.comments.isNotEmpty() || (Account.signedIn && !state.disabled && !state.loading), pressedScale = 0.98f, onClick = onOpen)
            .clip(shape)
            .background(c.surfaceContainerLow)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.comments_title), fontWeight = FontWeight.ExtraBold, fontSize = 15.sp, modifier = Modifier.weight(1f))
            if (state.total > 0) {
                Text(formatCount(state.total.toLong()), color = c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 13.sp)
            }
        }
        Spacer(Modifier.height(10.dp))
        when {
            state.disabled -> Text(stringResource(R.string.comments_turned_off), color = c.onSurfaceVariant, fontSize = 13.5.sp)
            first != null -> Row {
                ChannelAvatar(first.avatar, first.author, size = 30)
                Spacer(Modifier.width(10.dp))
                Text(
                    text = first.text,
                    color = c.onSurfaceVariant,
                    fontSize = 13.5.sp,
                    lineHeight = 19.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            state.loading -> CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(22.dp))
            state.error != null -> Text(state.error.orEmpty(), color = c.onSurfaceVariant, fontSize = 13.5.sp)
            else -> Text(stringResource(if (Account.signedIn) R.string.comments_none_add_first else R.string.comments_none), color = c.onSurfaceVariant, fontSize = 13.5.sp)
        }
    }
}

/**
 * Comments as a panel over the player's details, opening with its top just under the video
 * (the video keeps playing above it). The comment box is pinned to the panel's bottom at
 * every size. Drag the top handle up to cover the screen, down to shrink or close; a fling
 * decides on its own. The list scrolls first: only once it's at the top does a further pull
 * down move the panel the same way (Derek, 2026-10-10).
 */
@Composable
fun CommentsPanel(state: CommentsState, partialTop: Dp, onClose: () -> Unit) {
    val c = MaterialTheme.colorScheme
    PlayerPanel(
        partialTop = partialTop,
        onClose = onClose,
        closeLabel = stringResource(R.string.comments_close),
        title = {
            Text(stringResource(R.string.comments_title), fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            if (state.total > 0) {
                Spacer(Modifier.width(8.dp))
                Text(formatCount(state.total.toLong()), color = c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
            if (state.sortTokens.size >= 2) {
                Spacer(Modifier.width(14.dp))
                SortChip(stringResource(R.string.comments_sort_top), selected = !state.newestFirst) { state.chooseSort(false) }
                Spacer(Modifier.width(6.dp))
                SortChip(stringResource(R.string.comments_sort_newest), selected = state.newestFirst) { state.chooseSort(true) }
            }
            if (CommentPrefs.search && state.comments.isNotEmpty()) {
                Spacer(Modifier.width(10.dp))
                Box(
                    Modifier.size(32.dp)
                        .pressable { state.searchOpen = !state.searchOpen; if (!state.searchOpen) state.searchQuery = "" }
                        .clip(CircleShape)
                        .background(if (state.searchOpen) c.primary else c.surfaceContainerHighest),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Rounded.Search, stringResource(R.string.comments_search_title),
                        tint = if (state.searchOpen) c.onPrimary else c.onSurfaceVariant, modifier = Modifier.size(18.dp),
                    )
                }
            }
        },
    ) { listModifier ->
        val list = rememberLazyListState()
        val nearEnd by remember {
            derivedStateOf { (list.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0) >= list.layoutInfo.totalItemsCount - 4 }
        }
        // Also when more arrived and the end is still in view: a search that few comments match keeps reading on.
        LaunchedEffect(nearEnd, state.comments.size) { if (nearEnd) state.loadMore() }
        LazyColumn(state = list, modifier = listModifier) {
            if (state.searchOpen && CommentPrefs.search) item(key = "commentSearch") {
                CommentSearchField(state.searchQuery) { state.searchQuery = it }
            }
            commentThreads(state, keyPrefix = "")
            if (state.loading || state.hasMore) item {
                Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) {
                    CircularProgressIndicator(color = c.primary, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
                }
            }
            if (state.searchOpen && CommentPrefs.search && state.searchQuery.isNotBlank() && !state.loading && !state.hasMore &&
                state.comments.isNotEmpty() && state.comments.none { state.matchesSearch(it) }
            ) item {
                Text(stringResource(R.string.comments_search_none), color = c.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(20.dp))
            }
            if (!state.loading && state.comments.isEmpty()) item {
                Text(
                    stringResource(if (state.disabled) R.string.comments_turned_off else R.string.comments_none),
                    color = c.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(20.dp),
                )
            }
            item { Spacer(Modifier.height(12.dp)) }
        }
        if (Account.signedIn) Composer(state) else Text(
            stringResource(R.string.comments_sign_in_to_comment),
            color = c.onSurfaceVariant, fontSize = 13.sp,
            modifier = Modifier.fillMaxWidth().background(c.surfaceContainer).padding(horizontal = 20.dp, vertical = 14.dp),
        )
        // Clear of the gesture bar when the keyboard isn't up.
        Spacer(Modifier.fillMaxWidth().background(c.surfaceContainer).windowInsetsBottomHeight(WindowInsets.navigationBars))
    }
}

/** One of the comment sort choices beside the panel's title. */
@Composable
private fun SortChip(label: String, selected: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    Text(
        label,
        color = if (selected) c.onPrimary else c.onSurfaceVariant,
        fontWeight = FontWeight.Bold,
        fontSize = 12.5.sp,
        maxLines = 1,
        modifier = Modifier
            .pressable(pressedScale = 0.94f, onClick = onClick)
            .clip(CircleShape)
            .background(if (selected) c.primary else c.surfaceContainerHighest)
            .padding(horizontal = 12.dp, vertical = 6.dp),
    )
}

/**
 * The panel the comments and the transcript open in, over the player's details with its top just under
 * the video. Drag the top handle (and [title]) up to cover the screen, down to shrink or close; a fling
 * decides on its own. [content] gets the modifier for its list (it fills the room left and, once at its
 * top, a further pull down moves the panel); anything after the list stays pinned to the bottom.
 */
@Composable
fun PlayerPanel(
    partialTop: Dp,
    onClose: () -> Unit,
    closeLabel: String,
    title: @Composable RowScope.() -> Unit,
    content: @Composable ColumnScope.(listModifier: Modifier) -> Unit,
) {
    val c = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()
    val reduced = rememberReducedMotion()
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val statusTop = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()
        val fullPx = with(density) { (maxHeight - statusTop - 8.dp).toPx() }
        val partialPx = with(density) { (maxHeight - partialTop).toPx() }.coerceIn(fullPx * 0.35f, fullPx)
        val height = remember { Animatable(partialPx) }
        // Slides up into place on open; slides back down to close.
        val slide = remember { Animatable(1f) }
        LaunchedEffect(Unit) { if (reduced) slide.snapTo(0f) else slide.animateTo(0f, spring(dampingRatio = 0.86f, stiffness = 550f)) }
        fun close() = scope.launch {
            if (!reduced) slide.animateTo(1f, tween(240, easing = Motion.Emphasized))
            onClose()
        }
        BackHandler { close() }
        fun dragBy(dy: Float): Float {
            val before = height.value
            val after = (before - dy).coerceIn(partialPx * 0.25f, fullPx)
            scope.launch { height.snapTo(after) }
            return before - after
        }
        fun settle(velocity: Float) {
            val h = height.value
            when {
                // Dragged down past 30%, flicked down once below the resting size, or flicked
                // hard from it: close.
                h < partialPx * 0.7f || velocity > 1200f && h < partialPx - 1f || velocity > 1800f && h <= partialPx + 1f -> close()
                velocity < -1200f -> scope.launch { height.animateTo(fullPx, spring(dampingRatio = 0.86f, stiffness = 550f)) }
                velocity > 1200f -> scope.launch { height.animateTo(partialPx, spring(dampingRatio = 0.86f, stiffness = 550f)) }
                else -> {
                    val target = if (h > (partialPx + fullPx) / 2f) fullPx else partialPx
                    scope.launch { height.animateTo(target, spring(dampingRatio = 0.86f, stiffness = 550f)) }
                }
            }
        }
        // The list's leftover downward drag (it's already at the top) pulls the panel down;
        // reversing gives the height back, up to where the pull began, before the list scrolls.
        // Flings never move the panel, so a fast scroll up stops at the top.
        val listPull = remember(partialPx, fullPx) {
            object : NestedScrollConnection {
                var pulling = false
                var pullFrom = 0f

                fun pullBy(dy: Float): Float {
                    val before = height.value
                    val after = (before - dy).coerceIn(partialPx * 0.25f, pullFrom)
                    scope.launch(start = CoroutineStart.UNDISPATCHED) { height.snapTo(after) }
                    return before - after
                }

                override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset {
                    if (pulling && source == NestedScrollSource.UserInput && available.y < 0f) return Offset(0f, pullBy(available.y))
                    return Offset.Zero
                }

                override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset {
                    if (source != NestedScrollSource.UserInput || available.y <= 0f) return Offset.Zero
                    if (!pulling) { pulling = true; pullFrom = height.value }
                    return Offset(0f, pullBy(available.y))
                }

                override suspend fun onPreFling(available: Velocity): Velocity {
                    if (!pulling) return Velocity.Zero
                    pulling = false
                    settle(available.y)
                    return available
                }
            }
        }
        val shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
        Column(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .height(with(density) { height.value.toDp() })
                .graphicsLayer { translationY = slide.value * size.height }
                .shadow(16.dp, shape, ambientColor = Color.Black.copy(alpha = 0.18f), spotColor = Color.Black.copy(alpha = 0.22f))
                .clip(shape)
                .background(c.surfaceContainerLow)
                // Swallow taps so nothing underneath reacts through the panel.
                .pointerInput(Unit) { awaitPointerEventScope { while (true) awaitPointerEvent() } }
                .imePadding(),
        ) {
            // Handle + title: the drag area.
            Column(
                Modifier
                    .fillMaxWidth()
                    .draggable(
                        state = rememberDraggableState { dragBy(it) },
                        orientation = Orientation.Vertical,
                        onDragStopped = { v -> settle(v) },
                    ),
            ) {
                Box(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp), Alignment.Center) {
                    Box(Modifier.size(width = 36.dp, height = 4.dp).clip(CircleShape).background(c.onSurfaceVariant.copy(alpha = 0.45f)))
                }
                Row(Modifier.padding(start = 20.dp, end = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    title()
                    Spacer(Modifier.weight(1f))
                    Box(Modifier.size(44.dp).pressable { close() }, contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, closeLabel, tint = c.onSurfaceVariant)
                    }
                }
            }
            content(Modifier.weight(1f).nestedScroll(listPull))
        }
    }
}

/**
 * Wide windows (Rail 3): the comments flow in the player's own list under the details, as they read
 * on a page, instead of a card that opens a panel. The heading with the count, the comment box when
 * signed in, then every comment with its replies; the next page loads as the end comes into view.
 */
fun LazyListScope.inlineComments(state: CommentsState) {
    item(key = "commentsHead") {
        val c = MaterialTheme.colorScheme
        Row(Modifier.padding(start = 20.dp, end = 20.dp, top = 22.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(stringResource(R.string.comments_title), fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp)
            if (state.total > 0) {
                Spacer(Modifier.width(8.dp))
                Text(formatCount(state.total.toLong()), color = c.onSurfaceVariant, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
    if (Account.signedIn && !state.disabled) item(key = "commentsComposer") {
        Box(Modifier.padding(horizontal = 12.dp, vertical = 6.dp).clip(RoundedCornerShape(26.dp))) { Composer(state) }
    }
    commentThreads(state, keyPrefix = "comment-")
    when {
        state.loading || state.hasMore -> item(key = "commentsMore") {
            // Composed as it nears the screen: that's the cue for the next page.
            LaunchedEffect(state.comments.size) { state.loadMore() }
            Box(Modifier.fillMaxWidth().padding(20.dp), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 3.dp, modifier = Modifier.size(26.dp))
            }
        }
        state.comments.isEmpty() -> item(key = "commentsNone") {
            Text(
                state.error ?: stringResource(if (state.disabled) R.string.comments_turned_off else R.string.comments_none),
                color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 14.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )
        }
    }
}

/** Text field pinned to the bottom of the sheet; Send (or Enter) posts and dismisses the keyboard. */
@Composable
private fun Composer(state: CommentsState) {
    val c = MaterialTheme.colorScheme
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    val view = LocalView.current
    val requester = remember { FocusRequester() }
    fun send() {
        focus.clearFocus(force = true)
        keyboard?.hide()
        view.postDelayed({
            view.context.getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(view.windowToken, 0)
        }, 120)
        state.post()
    }
    // Tapping Reply focuses the field.
    LaunchedEffect(state.replyingTo) { if (state.replyingTo != null) runCatching { requester.requestFocus() } }
    Column(Modifier.fillMaxWidth().background(c.surfaceContainer).padding(horizontal = 12.dp, vertical = 10.dp)) {
        state.replyingTo?.let { target ->
            Row(Modifier.padding(start = 8.dp, bottom = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.comments_replying_to, target.displayName()), color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                Box(Modifier.size(28.dp).pressable { state.replyingTo = null }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.comments_cancel_reply), tint = c.onSurfaceVariant, modifier = Modifier.size(16.dp))
                }
            }
        }
        state.postError?.let { Text(it, color = c.error, fontSize = 12.sp, modifier = Modifier.padding(start = 8.dp, bottom = 6.dp)) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.weight(1f).clip(RoundedCornerShape(24.dp)).background(c.surfaceContainerHighest)
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                if (state.draft.isEmpty()) {
                    Text(stringResource(if (state.replyingTo != null) R.string.comments_add_reply else R.string.comments_add_comment), color = c.onSurfaceVariant, fontSize = 15.sp)
                }
                BasicTextField(
                    value = state.draft,
                    onValueChange = { state.draft = it },
                    textStyle = OwnDirectionText.merge(TextStyle(color = c.onSurface, fontSize = 15.sp)),
                    cursorBrush = SolidColor(c.primary),
                    maxLines = 5,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send, capitalization = KeyboardCapitalization.Sentences),
                    keyboardActions = KeyboardActions(onSend = { send() }, onDone = { send() }),
                    modifier = Modifier.fillMaxWidth().focusRequester(requester).onPreviewKeyEvent { e ->
                        if (e.key == Key.Enter || e.key == Key.NumPadEnter) {
                            if (e.type == KeyEventType.KeyDown) send()
                            true
                        } else false
                    },
                )
            }
            Spacer(Modifier.width(8.dp))
            val canSend = state.draft.isNotBlank() && !state.posting
            Box(
                Modifier.size(46.dp).pressable(enabled = canSend) { send() }.clip(CircleShape)
                    .background(if (canSend) c.primary else c.surfaceContainerHighest),
                contentAlignment = Alignment.Center,
            ) {
                if (state.posting) CircularProgressIndicator(color = c.onPrimary, strokeWidth = 2.dp, modifier = Modifier.size(18.dp))
                else Icon(Icons.AutoMirrored.Rounded.Send, stringResource(R.string.comments_post), tint = if (canSend) c.onPrimary else c.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

/** Taps on one comment row, counted until the pause that ends them. */
private class TapCounter {
    var count = 0
    var job: Job? = null
}

private const val TapWindowMs = 300L

@Composable
private fun CommentRow(
    comment: Comment,
    state: CommentsState,
    /** 0 a comment, 1 a reply to it, 2 and on a reply to a reply (each a step further in, up to 3). */
    depth: Int = 0,
    /** Guide lines (x in dp) of the threads above that carry on past this row. */
    trail: List<Int> = emptyList(),
    /** The last reply of a thread: its guide line ends in the elbow instead of running on. */
    isLast: Boolean = false,
    /** The comment whose replies are open beneath it: a guide line runs down from its avatar. */
    threadOpen: Boolean = false,
    /** Pinned at the top while its replies scroll: the text is held to a few lines so it never fills the sheet. */
    compact: Boolean = false,
) {
    val c = MaterialTheme.colorScheme
    val isReply = depth > 0
    val haptic = LocalHapticFeedback.current
    val tapScope = rememberCoroutineScope()
    val taps = remember(comment.id) { TapCounter() }
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    val copiedText = stringResource(R.string.comments_copied)
    Row(
        Modifier
            .fillMaxWidth()
            // Double tap likes the comment, triple tap starts a reply (signed in only). A short wait after
            // each tap tells the two apart, so a triple tap never leaves a like behind.
            .pointerInput(comment.id) {
                detectTapGestures(onLongPress = {
                    // Hold a comment: its text goes to the clipboard (Settings can turn this off).
                    if (CommentPrefs.copyOnLongPress) {
                        clipboard.setText(AnnotatedString(comment.text))
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        // Android 13+ shows its own "copied" note.
                        if (android.os.Build.VERSION.SDK_INT < 33) android.widget.Toast.makeText(context, copiedText, android.widget.Toast.LENGTH_SHORT).show()
                    }
                }) {
                    val canAct = Account.signedIn && !comment.id.startsWith("local-")
                    taps.job?.cancel()
                    taps.count++
                    if (taps.count >= 3) {
                        taps.count = 0
                        if (canAct) {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            state.startReply(comment)
                        }
                    } else {
                        val seen = taps.count
                        taps.job = tapScope.launch {
                            delay(TapWindowMs)
                            taps.count = 0
                            if (seen == 2 && canAct && state.liked[comment.id] != true) {
                                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                state.toggleLike(comment) { state.postError = it }
                            }
                        }
                    }
                }
            }
            // Reddit-style guide: a line from the parent's avatar down its replies, with an elbow into each one.
            .then(
                if (isReply || threadOpen || trail.isNotEmpty()) {
                    val guide = c.outlineVariant
                    Modifier.drawBehind {
                        val width = 1.5.dp.toPx()
                        trail.forEach { x ->
                            drawLine(guide, Offset(x.dp.toPx(), 0f), Offset(x.dp.toPx(), size.height), strokeWidth = width, cap = StrokeCap.Round)
                        }
                        if (isReply) {
                            val x = threadCenter(depth - 1).dp.toPx()
                            val cy = 24.dp.toPx()
                            val r = 12.dp.toPx()
                            if (isLast) drawLine(guide, Offset(x, 0f), Offset(x, cy - r), strokeWidth = width, cap = StrokeCap.Round)
                            else drawLine(guide, Offset(x, 0f), Offset(x, size.height), strokeWidth = width, cap = StrokeCap.Round)
                            val elbow = Path().apply {
                                moveTo(x, cy - r)
                                quadraticBezierTo(x, cy, x + r, cy)
                                lineTo((threadStart(depth) - 4).dp.toPx(), cy)
                            }
                            drawPath(elbow, guide, style = Stroke(width = width, cap = StrokeCap.Round))
                        }
                        if (threadOpen) {
                            val x = threadCenter(depth).dp.toPx()
                            drawLine(guide, Offset(x, (if (isReply) 42 else 50).dp.toPx()), Offset(x, size.height), strokeWidth = width, cap = StrokeCap.Round)
                        }
                    }
                } else Modifier,
            )
            .padding(start = threadStart(depth).dp, end = 20.dp, top = 10.dp, bottom = 6.dp),
    ) {
        ChannelAvatar(comment.avatar, comment.author, size = if (isReply) 28 else 36)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (comment.pinned) Icon(Icons.Rounded.PushPin, stringResource(R.string.comments_pinned), tint = c.onSurfaceVariant, modifier = Modifier.size(14.dp))
                Text(comment.displayName(), fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                displayAge(comment.ago)?.let { Text(it, color = c.onSurfaceVariant, fontSize = 12.sp, maxLines = 1) }
            }
            Spacer(Modifier.height(3.dp))
            Text(
                comment.text, fontSize = 14.sp, lineHeight = 20.sp,
                maxLines = if (compact) 3 else Int.MAX_VALUE,
                overflow = if (compact) TextOverflow.Ellipsis else TextOverflow.Clip,
            )
            Spacer(Modifier.height(6.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                // Like (signed in): pops like the video's like, and the count follows.
                val isLiked = state.liked[comment.id] == true
                val canLike = Account.signedIn && !comment.id.startsWith("local-")
                val likeTint = if (isLiked) c.primary else c.onSurfaceVariant
                Row(
                    Modifier
                        .pressable(enabled = canLike) { state.toggleLike(comment) { state.postError = it } }
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FlourishIcon(
                        if (isLiked) Icons.Rounded.ThumbUp else Icons.Outlined.ThumbUp,
                        stringResource(if (isLiked) R.string.comments_unlike else R.string.comments_like),
                        active = isLiked, kind = Flourish.Pop, tint = likeTint, size = 16.dp,
                    )
                    val count = comment.likes + if (isLiked) 1 else 0
                    if (count > 0) {
                        Spacer(Modifier.width(5.dp))
                        Text(formatCount(count.toLong()), color = likeTint, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
                if (comment.hearted) {
                    Spacer(Modifier.width(12.dp))
                    Icon(Icons.Rounded.Favorite, stringResource(R.string.comments_hearted), tint = c.primary, modifier = Modifier.size(14.dp))
                }
                if (Account.signedIn && !comment.id.startsWith("local-")) {
                    Spacer(Modifier.width(16.dp))
                    Text(
                        stringResource(R.string.comments_reply),
                        color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.ExtraBold,
                        modifier = Modifier.pressable { state.startReply(comment) }.padding(vertical = 2.dp),
                    )
                }
            }
            if (!isReply && comment.replyCount > 0 && comment.repliesToken != null && !state.replies.containsKey(comment.id)) {
                val busy = state.repliesLoading[comment.id] == true
                Row(
                    modifier = Modifier
                        .padding(top = 6.dp)
                        .pressable { state.loadReplies(comment) }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        plural(R.plurals.comments_replies, comment.replyCount.toLong(), comment.replyCount.toString()),
                        color = c.primary,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 13.sp,
                    )
                    Icon(Icons.Rounded.ExpandMore, null, tint = c.primary, modifier = Modifier.size(18.dp))
                    if (busy) {
                        Spacer(Modifier.width(8.dp))
                        CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
                    }
                }
            }
        }
    }
}

/** Where a comment (depth 0) or a reply (1 and on) starts, and the middle of its avatar, in dp. */
private fun threadStart(depth: Int) = 20 + 42 * minOf(depth, 3)
private fun threadCenter(depth: Int) = threadStart(depth) + if (depth == 0) 18 else 14

/** A reply with the replies that answer it. */
private class ReplyNode(val reply: Comment) {
    val children = ArrayList<ReplyNode>()
    fun count(): Int = children.sumOf { 1 + it.count() }
}

/**
 * YouTube lists every reply flat under the comment, each starting with the @handle of who it answers. A reply
 * that answers an earlier reply goes under that one (a thread of its own); the rest answer the comment itself.
 */
private fun replyTree(replies: List<Comment>): List<ReplyNode> {
    val nodes = replies.map { ReplyNode(it) }
    val roots = ArrayList<ReplyNode>()
    nodes.forEachIndexed { i, node ->
        val mention = node.reply.text.trimStart { it.isWhitespace() || it == '\u200B' || it == '\u00A0' }.takeIf { it.startsWith("@") }
            ?.substring(1)?.takeWhile { !it.isWhitespace() }?.trimEnd(',', ':', ';', '!', '?', '.', '\u2026')
        val target = mention?.takeIf { it.isNotEmpty() }?.let { m ->
            (i - 1 downTo 0).firstOrNull { nodes[it].reply.author.removePrefix("@").equals(m, ignoreCase = true) }?.let { nodes[it] }
        }
        if (target != null) target.children += node else roots += node
    }
    return roots
}

/**
 * Every comment; once its replies are in they sit under it, tied to it by a guide line, and stay (they are not
 * folded away again). "More replies" at the end of the thread brings the next batch. A reply that others
 * answered has a "1 reply" button of its own that opens those answers under it.
 */
private fun LazyListScope.commentThreads(state: CommentsState, keyPrefix: String) {
    state.comments.forEach { comment ->
        if (!state.matchesSearch(comment)) return@forEach
        val replies = state.replies[comment.id]
        item(key = keyPrefix + comment.id) { CommentRow(comment, state, threadOpen = replies != null) }
        if (replies != null) {
            val more = state.repliesNext[comment.id] != null
            replyNodes(replyTree(replies), state, keyPrefix, depth = 1, trail = emptyList(), endsThread = !more)
            if (more) {
                item(key = keyPrefix + "more-" + comment.id) {
                    MoreRepliesRow(state.repliesLoading[comment.id] == true) { state.loadMoreReplies(comment) }
                }
            }
        }
    }
}

private fun LazyListScope.replyNodes(
    nodes: List<ReplyNode>, state: CommentsState, keyPrefix: String, depth: Int, trail: List<Int>, endsThread: Boolean,
) {
    nodes.forEachIndexed { index, node ->
        // The last reply's line ends in its elbow, unless "more replies" follows it.
        val last = index == nodes.lastIndex && endsThread
        val open = state.nestedOpen[node.reply.id] == true
        val hasChildren = node.children.isNotEmpty()
        // What carries on past this reply for the rows below it: the lines above, and its own parent's line when more replies follow.
        val below = if (last) trail else trail + threadCenter(depth - 1)
        item(key = keyPrefix + "reply-" + node.reply.id) {
            CommentRow(node.reply, state, depth = depth, trail = trail, isLast = last, threadOpen = hasChildren)
        }
        if (hasChildren) {
            if (open) {
                replyNodes(node.children, state, keyPrefix, minOf(depth + 1, 3), below, endsThread = true)
            } else {
                item(key = keyPrefix + "nest-" + node.reply.id) {
                    NestedRepliesToggle(node.count(), depth, below) { state.nestedOpen[node.reply.id] = true }
                }
            }
        }
    }
}

/** A guide line's elbow: down from the top at [x] (dp), curving right into a row whose text starts at [textStart] (dp). */
private fun androidx.compose.ui.graphics.drawscope.DrawScope.guideElbow(
    color: Color, x: Int, textStart: Int, centerY: Float, continues: Boolean,
) {
    val width = 1.5.dp.toPx()
    val xp = x.dp.toPx()
    val r = 12.dp.toPx()
    if (continues) drawLine(color, Offset(xp, 0f), Offset(xp, size.height), strokeWidth = width, cap = StrokeCap.Round)
    else drawLine(color, Offset(xp, 0f), Offset(xp, centerY - r), strokeWidth = width, cap = StrokeCap.Round)
    val elbow = Path().apply {
        moveTo(xp, centerY - r)
        quadraticBezierTo(xp, centerY, xp + r, centerY)
        lineTo(textStart.dp.toPx() - 4.dp.toPx(), centerY)
    }
    drawPath(elbow, color, style = Stroke(width = width, cap = StrokeCap.Round))
}

/** "1 reply" under a reply that others answered: opens those answers under it (and they stay). */
@Composable
private fun NestedRepliesToggle(count: Int, depth: Int, trail: List<Int>, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val guide = c.outlineVariant
    val textStart = threadStart(depth) + 40
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                val width = 1.5.dp.toPx()
                trail.forEach { x ->
                    drawLine(guide, Offset(x.dp.toPx(), 0f), Offset(x.dp.toPx(), size.height), strokeWidth = width, cap = StrokeCap.Round)
                }
                guideElbow(guide, threadCenter(depth), textStart, centerY = 16.dp.toPx(), continues = false)
            }
            .padding(start = textStart.dp, end = 20.dp)
            .pressable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            plural(R.plurals.comments_replies, count.toLong(), count.toString()),
            color = c.primary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp,
        )
        Icon(Icons.Rounded.ExpandMore, null, tint = c.primary, modifier = Modifier.size(18.dp))
    }
}

/** "More replies" at the end of a comment's thread: the next batch of replies. */
@Composable
private fun MoreRepliesRow(busy: Boolean, onClick: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val guide = c.outlineVariant
    val textStart = threadStart(0) + 48
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind { guideElbow(guide, threadCenter(0), textStart, centerY = 16.dp.toPx(), continues = false) }
            .padding(start = textStart.dp, end = 20.dp)
            .pressable(onClick = onClick)
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(stringResource(R.string.comments_more_replies), color = c.primary, fontWeight = FontWeight.ExtraBold, fontSize = 13.sp)
        Icon(Icons.Rounded.ExpandMore, null, tint = c.primary, modifier = Modifier.size(18.dp))
        if (busy) {
            Spacer(Modifier.width(8.dp))
            CircularProgressIndicator(color = c.primary, strokeWidth = 2.dp, modifier = Modifier.size(14.dp))
        }
    }
}

/** The search box under the comments title: filters the comments loaded so far. */
@Composable
private fun CommentSearchField(value: String, onChange: (String) -> Unit) {
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 6.dp)
            .fillMaxWidth()
            .height(44.dp)
            .clip(CircleShape)
            .background(c.surfaceContainerHigh)
            .padding(start = 14.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.Search, null, tint = c.onSurfaceVariant, modifier = Modifier.size(19.dp))
        Spacer(Modifier.width(10.dp))
        Box(Modifier.weight(1f)) {
            if (value.isEmpty()) {
                Text(stringResource(R.string.comments_search_hint), color = c.onSurfaceVariant, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
            }
            androidx.compose.foundation.text.BasicTextField(
                value = value,
                onValueChange = onChange,
                singleLine = true,
                textStyle = OwnDirectionText.merge(androidx.compose.ui.text.TextStyle(color = c.onSurface, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(c.primary),
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (value.isNotEmpty()) {
            Box(Modifier.size(36.dp).pressable { onChange("") }, contentAlignment = Alignment.Center) {
                Icon(Icons.Rounded.Close, stringResource(R.string.ui_clear_search), tint = c.onSurfaceVariant, modifier = Modifier.size(18.dp))
            }
        }
    }
}

/** Comment preferences (Settings › Playback): long press copies, and the search button; both on to begin with. */
object CommentPrefs {
    private var prefs: android.content.SharedPreferences? = null
    var copyOnLongPress by mutableStateOf(true)
        private set
    var search by mutableStateOf(true)
        private set

    fun init(context: android.content.Context) {
        val p = context.getSharedPreferences("appearance", android.content.Context.MODE_PRIVATE).also { prefs = it }
        copyOnLongPress = p.getBoolean("comment_copy", true)
        search = p.getBoolean("comment_search", true)
    }

    fun chooseCopy(on: Boolean) {
        copyOnLongPress = on
        prefs?.edit()?.putBoolean("comment_copy", on)?.apply()
    }

    fun chooseSearch(on: Boolean) {
        search = on
        prefs?.edit()?.putBoolean("comment_search", on)?.apply()
    }
}
