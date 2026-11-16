package com.decio.skipii

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

/** Opens a video from anywhere (a notification, say). Provided by the shell. */
val LocalVideoOpener = staticCompositionLocalOf<(VideoItem) -> Unit> { {} }

/** One entry of the account's YouTube notifications. */
@Immutable
data class InboxItem(
    val text: String,
    val time: String?,
    val avatar: String?,
    val thumbnail: String?,
    val videoId: String?,
    val unread: Boolean,
)

/** The signed-in account's YouTube notifications (the bell by the profile picture). */
object Inbox {
    var items by mutableStateOf<List<InboxItem>>(emptyList())
        private set
    var loading by mutableStateOf(false)
        private set
    var error by mutableStateOf<String?>(null)
        private set
    val unread get() = items.count { it.unread }

    private var lastMs = 0L
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    /** Reads the list, at most once a minute unless [force]d. */
    fun refresh(force: Boolean = false) {
        if (!Account.signedIn || loading) return
        if (!force && System.currentTimeMillis() - lastMs < 60_000L) return
        loading = true
        scope.launch {
            runCatching { fetch() }
                .onSuccess { items = it; error = null; lastMs = System.currentTimeMillis() }
                .onFailure { error = YouTube.friendlyError(it) }
            loading = false
        }
    }

    fun clear() {
        items = emptyList()
        error = null
        lastMs = 0L
    }

    private suspend fun fetch(): List<InboxItem> = withContext(Dispatchers.IO) {
        val response = InnerTube.post(
            "notification/get_notification_menu",
            JSONObject().put("notificationsMenuRequestType", "NOTIFICATIONS_MENU_REQUEST_TYPE_INBOX"),
        )
        val out = ArrayList<InboxItem>()
        response.walk { key, obj ->
            if (key == "notificationRenderer") {
                val text = obj.text("shortMessage") ?: return@walk
                val videoId = obj.optJSONObject("navigationEndpoint")?.optJSONObject("watchEndpoint")
                    ?.optString("videoId")?.ifBlank { null }
                out += InboxItem(
                    text = text,
                    time = obj.text("sentTimeText"),
                    avatar = obj.thumbnails()?.lastOrNull(),
                    thumbnail = obj.optJSONObject("videoThumbnail")?.thumbnails()?.lastOrNull(),
                    videoId = videoId,
                    unread = !obj.optBoolean("read"),
                )
            }
        }
        out
    }
}

/** The bell in the headers, with a count of what's new; opens the notifications. Signed-in accounts only. */
@Composable
fun InboxBell() {
    val c = MaterialTheme.colorScheme
    var open by remember { mutableStateOf(false) }
    LaunchedEffect(Account.signedIn) { Inbox.refresh() }
    Box(Modifier.size(44.dp)) {
        CircleAction(Icons.Rounded.Notifications, stringResource(R.string.inbox_title)) {
            open = true
            Inbox.refresh(force = true)
        }
        val unread = Inbox.unread
        if (unread > 0) {
            Box(
                Modifier
                    .align(Alignment.TopEnd)
                    .defaultMinSize(minWidth = 17.dp, minHeight = 17.dp)
                    .clip(CircleShape)
                    .background(c.error)
                    .padding(horizontal = 4.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(if (unread > 9) "9+" else unread.toString(), color = c.onError, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold)
            }
        }
    }
    if (open) InboxSheet(onDismiss = { open = false })
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun InboxSheet(onDismiss: () -> Unit) {
    val c = MaterialTheme.colorScheme
    val openVideo = LocalVideoOpener.current
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = c.surfaceContainerLow,
    ) {
        Text(
            stringResource(R.string.inbox_title),
            fontFamily = RoundedHeavy, fontWeight = FontWeight.ExtraBold, fontSize = 21.sp,
            modifier = Modifier.padding(start = 20.dp, end = 20.dp, bottom = 8.dp),
        )
        val items = Inbox.items
        when {
            items.isNotEmpty() -> LazyColumn(Modifier.fillMaxWidth()) {
                itemsIndexed(items, key = { index, item -> "$index-${item.videoId}" }) { _, item ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .pressable(enabled = item.videoId != null) {
                                item.videoId?.let { id ->
                                    openVideo(
                                        VideoItem(
                                            id = id, title = item.text, channel = "", channelUrl = null,
                                            thumbnail = item.thumbnail, avatar = item.avatar,
                                            durationSec = 0, views = 0, uploaded = item.time,
                                        ),
                                    )
                                    onDismiss()
                                }
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.Top,
                    ) {
                        if (item.unread) {
                            Box(Modifier.padding(top = 18.dp, end = 8.dp).size(8.dp).clip(CircleShape).background(c.primary))
                        }
                        ChannelAvatar(item.avatar, item.text, size = 40)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(item.text, fontSize = 14.sp, lineHeight = 19.sp, fontWeight = FontWeight.SemiBold, maxLines = 3, overflow = TextOverflow.Ellipsis)
                            item.time?.let { Text(it, color = c.onSurfaceVariant, fontSize = 12.sp) }
                        }
                        item.thumbnail?.let {
                            Spacer(Modifier.width(12.dp))
                            Thumbnail(it, Modifier.width(96.dp).aspectRatio(16f / 9f), radius = 10)
                        }
                    }
                }
                item { Spacer(Modifier.height(28.dp)) }
            }
            Inbox.loading -> Box(Modifier.fillMaxWidth().padding(32.dp), Alignment.Center) {
                CircularProgressIndicator(color = c.primary, strokeWidth = 4.dp, modifier = Modifier.size(30.dp))
            }
            else -> Text(
                Inbox.error ?: stringResource(R.string.inbox_empty),
                color = c.onSurfaceVariant, fontSize = 14.sp,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            )
        }
    }
}
