package com.decio.skipii

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Forum
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** One post from a channel's Posts tab (what YouTube used to call a community post). */
@Immutable
data class PostItem(
    val id: String,
    val channelId: String,
    val channelName: String,
    val avatar: String?,
    val text: String,
    /** YouTube's own words for its age ("3 days ago"). */
    val published: String?,
    val ageHours: Double,
    val likes: String?,
    val comments: String?,
    val image: String?,
    val video: VideoItem?,
    val poll: List<String>,
)

/** Whether the Posts tab shows (Settings › Appearance). On to begin with. */
object PostsSetting {
    private var prefs: SharedPreferences? = null
    var enabled by mutableStateOf(true)
        private set

    fun init(context: Context) {
        val p = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).also { prefs = it }
        enabled = p.getBoolean("posts_tab", true)
    }

    fun choose(on: Boolean) {
        enabled = on
        prefs?.edit()?.putBoolean("posts_tab", on)?.apply()
    }
}

enum class PostAge(val hours: Double, val label: String) {
    Any(Double.POSITIVE_INFINITY, "All"),
    Day(24.0, "24h"),
    Week(24.0 * 7, "7d"),
    Month(24.0 * 30, "30d"),
}

/**
 * The Posts tab's feed: the latest posts of the channels the account follows. There is no feed of posts to ask
 * YouTube for, so each followed channel's Posts tab is read (a few at a time, the first [MAX_CHANNELS] of them) and the
 * posts are put in order of age.
 */
object PostsFeed {
    private const val MAX_CHANNELS = 30
    private const val STALE_MS = 15 * 60_000L

    var items by mutableStateOf<List<PostItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    /** How recent the posts shown must be, and what they must mention (a channel's name, or words in the post). */
    var age by mutableStateOf(PostAge.Any)
    var query by mutableStateOf("")

    /** The posts that pass the age and the search. */
    fun visible(): List<PostItem> {
        val q = query.trim()
        return items.filter { post ->
            post.ageHours <= age.hours &&
                (q.isEmpty() || post.channelName.contains(q, ignoreCase = true) || post.text.contains(q, ignoreCase = true))
        }
    }

    private var loadedAt = 0L
    private var job: Job? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Reads the posts when there are none yet or they are a quarter of an hour old. */
    fun ensureLoaded() {
        if (!Account.signedIn || loading) return
        if (items.isEmpty() || System.currentTimeMillis() - loadedAt > STALE_MS) refresh()
    }

    fun refresh() {
        if (!Account.signedIn || loading) return
        loading = true
        error = null
        job?.cancel()
        job = scope.launch {
            val found = ArrayList<PostItem>()
            try {
                val channels = withContext(Dispatchers.IO) { InnerTube.subscriptions().orEmpty() }.take(MAX_CHANNELS)
                val gate = Semaphore(4)
                coroutineScope {
                    channels.map { channel ->
                        async {
                            val posts = gate.withPermit {
                                runCatching { withContext(Dispatchers.IO) { channelPosts(channel.ref) } }.getOrDefault(emptyList())
                            }
                            if (posts.isNotEmpty()) {
                                found += posts
                                items = found.sortedBy { it.ageHours }.take(80)
                            }
                        }
                    }.awaitAll()
                }
                loadedAt = System.currentTimeMillis()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                error = YouTube.friendlyError(e)
            }
            loading = false
        }
    }

    fun clear() {
        job?.cancel()
        items = emptyList()
        error = null
        loading = false
        loadedAt = 0L
    }

    /** The "params" of each channel's Posts tab (blank: it has none), found on the channel's page once. */
    private val tabParams = ConcurrentHashMap<String, String>()

    private fun postsTabParams(channelId: String): String? {
        tabParams[channelId]?.let { return it.ifBlank { null } }
        val root = InnerTube.postAny("browse", JSONObject().put("browseId", channelId))
        var found: String? = null
        root.walk { key, obj ->
            if (key == "tabRenderer" && found == null) {
                val title = obj.optString("title")
                if (title.equals("Posts", ignoreCase = true) || title.equals("Community", ignoreCase = true)) {
                    found = obj.optJSONObject("endpoint")?.optJSONObject("browseEndpoint")?.optString("params")?.ifBlank { null }
                }
            }
        }
        tabParams[channelId] = found.orEmpty()
        return found
    }

    /** The posts on a channel's Posts tab (its first page); none when it has no such tab. */
    private fun channelPosts(channel: ChannelRef): List<PostItem> {
        val params = postsTabParams(channel.id) ?: return emptyList()
        val root = InnerTube.postAny("browse", JSONObject().put("browseId", channel.id).put("params", params))
        val out = ArrayList<PostItem>()
        root.walk { key, obj ->
            if (key != "backstagePostRenderer") return@walk
            val id = obj.optString("postId").ifBlank { return@walk }
            val attachment = obj.optJSONObject("backstageAttachment")
            val single = attachment?.optJSONObject("backstageImageRenderer")?.optJSONObject("image")?.thumbnails()?.lastOrNull()
            val multi = attachment?.optJSONObject("postMultiImageRenderer")?.optJSONArray("images")?.optJSONObject(0)
                ?.optJSONObject("backstageImageRenderer")?.optJSONObject("image")?.thumbnails()?.lastOrNull()
            val choices = attachment?.optJSONObject("pollRenderer")?.optJSONArray("choices")
            val poll = if (choices == null) emptyList() else (0 until choices.length()).mapNotNull { choices.optJSONObject(it)?.text("text") }
            val published = obj.text("publishedTimeText")
            out += PostItem(
                id = id,
                channelId = channel.id,
                channelName = obj.text("authorText") ?: channel.name,
                avatar = obj.optJSONObject("authorThumbnail")?.thumbnails()?.lastOrNull() ?: channel.avatar,
                text = obj.text("contentText").orEmpty(),
                published = published,
                ageHours = ReplyPreview.ageHours(published),
                likes = obj.text("voteCount"),
                comments = obj.optJSONObject("actionButtons")?.optJSONObject("commentActionButtonsRenderer")
                    ?.optJSONObject("replyButton")?.optJSONObject("buttonRenderer")?.text("text"),
                image = single ?: multi,
                video = attachment?.let { InnerTube.parseFeed(it).videos.firstOrNull() },
                poll = poll,
            )
        }
        return out
    }
}

/** The Posts tab: what the channels you follow have posted lately, newest first. */
@Composable
fun PostsScreen(
    contentPadding: PaddingValues,
    onOpen: (VideoItem) -> Unit,
    onSignIn: () -> Unit,
    onAccount: () -> Unit,
    scrollToTop: Int = 0,
) {
    val signedIn = Account.signedIn
    val openChannel = LocalChannelOpener.current
    CollapsingHeaderLayout(
        resetKey = scrollToTop,
        header = { ScreenHeader(title = stringResource(R.string.app_tab_posts)) { AccountAction(onAccount) } },
    ) { top ->
        PullToRefresh(
            enabled = signedIn,
            refreshing = PostsFeed.loading,
            onRefresh = { PostsFeed.refresh() },
            topPadding = top,
        ) {
            LazyColumn(state = rememberScrollToTop(scrollToTop), contentPadding = contentPadding.withTop(top)) {
                if (!signedIn) {
                    item {
                        EmptyState(
                            icon = Icons.Rounded.Forum,
                            title = stringResource(R.string.posts_signed_out_title),
                            body = stringResource(R.string.posts_signed_out_body),
                            action = { PillButton(stringResource(R.string.ui_sign_in_with_google), onSignIn) },
                        )
                    }
                } else {
                    postsItems(onOpen, openChannel)
                }
            }
        }
    }
}

/** The posts as list items: the time chips and the search box, then each post (shared by the Posts tab and Subscriptions). */
fun androidx.compose.foundation.lazy.LazyListScope.postsItems(onOpen: (VideoItem) -> Unit, openChannel: (String) -> Unit) {
    item(key = "postsFilters") {
        // Read once the list is on screen (and again when it is old).
        LaunchedEffect(Unit) { PostsFeed.ensureLoaded() }
        PostsFilters()
    }
    val shown = PostsFeed.visible()
    items(shown, key = { it.id }) { post ->
        PostCard(post, onOpen) { openChannel("https://www.youtube.com/channel/${post.channelId}") }
    }
    if (PostsFeed.loading) {
        item(key = "loading") {
            Box(Modifier.fillMaxWidth().padding(24.dp), Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary, strokeWidth = 4.dp, modifier = Modifier.size(28.dp))
            }
        }
    } else if (shown.isEmpty()) {
        item(key = "empty") {
            val filtered = PostsFeed.items.isNotEmpty()
            EmptyState(
                icon = Icons.Rounded.Forum,
                title = stringResource(if (filtered) R.string.posts_none_match_title else R.string.posts_empty_title),
                body = if (filtered) stringResource(R.string.posts_none_match_body) else PostsFeed.error ?: stringResource(R.string.posts_empty_body),
            )
        }
    }
}

/** How recent (a chip each), and a search box: a channel's name finds that channel's posts; other words find them in the posts. */
@Composable
private fun PostsFilters() {
    val c = MaterialTheme.colorScheme
    Column(Modifier.fillMaxWidth().padding(bottom = 6.dp)) {
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            items(PostAge.entries.toList(), key = { it.name }) { age ->
                SearchFilterPill(age.label, PostsFeed.age == age) { PostsFeed.age = age }
            }
        }
        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 8.dp)
                .fillMaxWidth()
                .height(46.dp)
                .clip(CircleShape)
                .background(c.surfaceContainerHigh)
                .padding(start = 16.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
            Box(Modifier.weight(1f)) {
                if (PostsFeed.query.isEmpty()) {
                    Text(stringResource(R.string.posts_search_hint), color = c.onSurfaceVariant, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                }
                BasicTextField(
                    value = PostsFeed.query,
                    onValueChange = { PostsFeed.query = it },
                    singleLine = true,
                    textStyle = OwnDirectionText.merge(TextStyle(color = c.onSurface, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)),
                    cursorBrush = SolidColor(c.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (PostsFeed.query.isNotEmpty()) {
                Box(Modifier.size(38.dp).pressable { PostsFeed.query = "" }, contentAlignment = Alignment.Center) {
                    Icon(Icons.Rounded.Close, stringResource(R.string.ui_clear_search), tint = c.onSurfaceVariant, modifier = Modifier.size(20.dp))
                }
            }
        }
    }
}

/** One post: who and when, its words, then its picture, video or poll, and its counts. */
@Composable
private fun PostCard(post: PostItem, onOpen: (VideoItem) -> Unit, onChannel: () -> Unit) {
    val c = MaterialTheme.colorScheme
    var open by remember(post.id) { mutableStateOf(false) }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
        Row(Modifier.pressable(pressedScale = 0.98f, onClick = onChannel), verticalAlignment = Alignment.CenterVertically) {
            ChannelAvatar(post.avatar, post.channelName, size = 40)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(post.channelName, fontWeight = FontWeight.ExtraBold, fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                post.published?.let { Text(it, color = c.onSurfaceVariant, fontSize = 12.sp, fontWeight = FontWeight.SemiBold) }
            }
        }
        if (post.text.isNotBlank()) {
            Spacer(Modifier.height(10.dp))
            Text(
                post.text, fontSize = 15.sp, lineHeight = 21.sp,
                maxLines = if (open) Int.MAX_VALUE else 8, overflow = TextOverflow.Ellipsis,
                modifier = Modifier.pressable(pressedScale = 0.99f) { open = !open },
            )
        }
        post.image?.let { url ->
            Spacer(Modifier.height(10.dp))
            Thumbnail(url, Modifier.fillMaxWidth().aspectRatio(1.4f).clip(RoundedCornerShape(16.dp)), radius = 16)
        }
        post.video?.let { video ->
            Spacer(Modifier.height(6.dp))
            VideoRow(video, onClick = { onOpen(video) }, queueSwipe = false)
        }
        if (post.poll.isNotEmpty()) {
            Spacer(Modifier.height(10.dp))
            post.poll.forEach { choice ->
                Text(
                    choice, fontSize = 14.sp, fontWeight = FontWeight.SemiBold, maxLines = 2, overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp).clip(CircleShape)
                        .background(c.surfaceContainerHigh).padding(horizontal = 16.dp, vertical = 11.dp),
                )
            }
        }
        val counts = listOfNotNull(
            post.likes?.takeIf { it.isNotBlank() }?.let { "$it likes" },
            post.comments?.takeIf { it.isNotBlank() }?.let { "$it comments" },
        )
        if (counts.isNotEmpty()) {
            Spacer(Modifier.height(8.dp))
            Text(counts.joinToString(" · "), color = c.onSurfaceVariant, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}
