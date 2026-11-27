package com.decio.skipii

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import okhttp3.Request

/**
 * Alerts for what happens to you on YouTube: likes, new followers, mentions and replies to your comments. YouTube's own
 * pushes only reach the YouTube app, so the app asks for itself: with the 15-minute background pass (the one new-video
 * notifications use) it reads the account's notifications and posts an Android notification for each new one of the kinds
 * switched on. The first pass only notes what is there, so nothing old is announced. A tap opens the video's comments
 * with the comment marked (see [NotificationCommentsScreen]).
 */
object ActivityAlerts {
    private const val TAG = "Skipii"
    private const val CHANNEL = "activity_alerts"
    private const val EXTRA_VIDEO = "com.skipii.alert.video"
    private const val EXTRA_COMMENT = "com.skipii.alert.comment"
    private const val EXTRA_TEXT = "com.skipii.alert.text"
    private const val EXTRA_THUMB = "com.skipii.alert.thumb"
    private const val MAX_PER_PASS = 5

    enum class Kind(val label: String) { Like("New like"), Follow("New follower"), Mention("You were mentioned"), Reply("New reply") }

    private var prefs: SharedPreferences? = null
    var enabled by mutableStateOf(true)
        private set
    var likes by mutableStateOf(true)
        private set
    var follows by mutableStateOf(true)
        private set
    var mentions by mutableStateOf(true)
        private set
    var replies by mutableStateOf(true)
        private set

    /** A comment notification's tap: the shell opens it and clears this. */
    var opening by mutableStateOf<CommentTarget?>(null)

    fun init(context: Context) {
        val p = context.getSharedPreferences("appearance", Context.MODE_PRIVATE).also { prefs = it }
        enabled = p.getBoolean("alerts_on", true)
        likes = p.getBoolean("alerts_likes", true)
        follows = p.getBoolean("alerts_follows", true)
        mentions = p.getBoolean("alerts_mentions", true)
        replies = p.getBoolean("alerts_replies", true)
    }

    /** Sets the master switch ([kind] null) or one kind. */
    fun choose(kind: Kind?, on: Boolean) {
        when (kind) {
            null -> { enabled = on; prefs?.edit()?.putBoolean("alerts_on", on)?.apply() }
            Kind.Like -> { likes = on; prefs?.edit()?.putBoolean("alerts_likes", on)?.apply() }
            Kind.Follow -> { follows = on; prefs?.edit()?.putBoolean("alerts_follows", on)?.apply() }
            Kind.Mention -> { mentions = on; prefs?.edit()?.putBoolean("alerts_mentions", on)?.apply() }
            Kind.Reply -> { replies = on; prefs?.edit()?.putBoolean("alerts_replies", on)?.apply() }
        }
    }

    private fun isOn(kind: Kind) = when (kind) {
        Kind.Like -> likes
        Kind.Follow -> follows
        Kind.Mention -> mentions
        Kind.Reply -> replies
    }

    /** What a notification is about, from its words (YouTube's English); null for anything else, such as a new upload. */
    internal fun classify(text: String): Kind? {
        val t = text.lowercase()
        return when {
            "replied" in t -> Kind.Reply
            "mentioned" in t -> Kind.Mention
            "liked your" in t || "likes your" in t -> Kind.Like
            "subscribed to you" in t || "started following" in t || "followed you" in t || "new subscriber" in t -> Kind.Follow
            else -> null
        }
    }

    private fun keyOf(item: InboxItem) = "${item.videoId}|${item.commentId}|${item.text}"

    /** One pass: reads the notifications and posts the new ones of the kinds switched on. */
    suspend fun check(context: Context) {
        if (!enabled || !Account.signedIn) return
        val items = Inbox.fetchNow()
        val store = context.getSharedPreferences("activity_alerts", Context.MODE_PRIVATE)
        val keys = items.map { keyOf(it) }
        val seen = store.getStringSet("seen", null)
        if (seen == null) {
            // The first pass only notes what's there.
            store.edit().putStringSet("seen", keys.toSet()).apply()
            Log.i(TAG, "alerts: first pass, ${keys.size} noted")
            return
        }
        var sent = 0
        // Oldest first, so the newest ends up on top.
        for (item in items.filter { keyOf(it) !in seen }.reversed()) {
            val kind = classify(item.text) ?: continue
            if (!item.unread || !isOn(kind)) continue
            if (sent >= MAX_PER_PASS) break
            if (notify(context, item, kind)) sent++
        }
        store.edit().putStringSet("seen", (keys + seen).distinct().take(300).toSet()).apply()
        Log.i(TAG, "alerts: ${keys.size} read, $sent posted")
    }

    private fun canNotify(context: Context): Boolean {
        if (!NotificationManagerCompat.from(context).areNotificationsEnabled()) return false
        val manager = context.getSystemService(NotificationManager::class.java) ?: return true
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL, "Activity on your account", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Likes, new followers, mentions and replies to your comments"
            },
        )
        return manager.getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE
    }

    private fun notify(context: Context, item: InboxItem, kind: Kind): Boolean {
        if (!canNotify(context)) return false
        val avatar = item.avatar?.let { runCatching { fetchBitmap(it) }.getOrNull() }
        val open = Intent(context, MainActivity::class.java)
            .setAction("com.skipii.ACTIVITY_ALERT")
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
            .apply {
                item.videoId?.let { putExtra(EXTRA_VIDEO, it) }
                item.commentId?.let { putExtra(EXTRA_COMMENT, it) }
                putExtra(EXTRA_TEXT, item.text)
                item.thumbnail?.let { putExtra(EXTRA_THUMB, it) }
            }
        val notification = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_skipii)
            .setContentTitle(kind.label)
            .setContentText(item.text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(item.text))
            .apply { if (avatar != null) setLargeIcon(avatar) }
            .setGroup(CHANNEL)
            .setAutoCancel(true)
            .setContentIntent(
                PendingIntent.getActivity(context, keyOf(item).hashCode(), open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT),
            )
            .build()
        return runCatching { NotificationManagerCompat.from(context).notify(keyOf(item).hashCode(), notification) }
            .onFailure { Log.w(TAG, "alerts: couldn't post (${it.message})") }
            .isSuccess
    }

    private fun fetchBitmap(url: String): Bitmap? {
        val request = Request.Builder().url(url).header("User-Agent", Net.USER_AGENT).build()
        Net.client.newCall(request).execute().use { r ->
            if (!r.isSuccessful) return null
            return r.body?.byteStream()?.use(BitmapFactory::decodeStream)
        }
    }

    /** The comment page an alert's tap carries (or the video it is about), or null. Clears it so a re-created window doesn't replay it. */
    fun takeTarget(intent: Intent?): CommentTarget? {
        val video = intent?.getStringExtra(EXTRA_VIDEO) ?: return null
        val target = CommentTarget(
            videoId = video,
            title = intent.getStringExtra(EXTRA_TEXT).orEmpty(),
            thumbnail = intent.getStringExtra(EXTRA_THUMB),
            commentId = intent.getStringExtra(EXTRA_COMMENT),
        )
        intent.removeExtra(EXTRA_VIDEO)
        return target
    }
}
