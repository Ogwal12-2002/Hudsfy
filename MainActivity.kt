package com.hudsify.app

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.MimeTypes
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.hudsify.app.data.AudioRepository
import com.hudsify.app.data.HudsifyStore
import com.hudsify.app.lyrics.LrcParser
import com.hudsify.app.model.LrcLine
import com.hudsify.app.model.Playlist
import com.hudsify.app.model.Track
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val BG = Color(0xFF08090B)
private val SURFACE = Color(0xFF111318)
private val SURFACE2 = Color(0xFF181B21)
private val ACCENT = Color(0xFFE7FF4F)
private val MUTED = Color(0xFF9A9EA8)

class MainActivity : ComponentActivity() {
    private lateinit var player: ExoPlayer

    private val permissions = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        player = ExoPlayer.Builder(this).build()
        requestMediaPermissions()
        setContent { HudsifyTheme { App(player) } }
    }

    private fun requestMediaPermissions() {
        val p = if (Build.VERSION.SDK_INT >= 33) arrayOf(
            Manifest.permission.READ_MEDIA_AUDIO,
            Manifest.permission.READ_MEDIA_VIDEO,
            Manifest.permission.POST_NOTIFICATIONS
        ) else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
        val needed = p.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }
        if (needed.isNotEmpty()) permissions.launch(needed.toTypedArray())
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}

@Composable
fun HudsifyTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = darkColorScheme(
            background = BG, surface = SURFACE, primary = ACCENT,
            onPrimary = Color.Black, onBackground = Color.White, onSurface = Color.White
        ),
        content = content
    )
}

@Composable
fun App(player: ExoPlayer) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val store = remember { HudsifyStore(context) }
    var tracks by remember { mutableStateOf(emptyList<Track>()) }
    var favorites by remember { mutableStateOf(store.favorites()) }
    var playlists by remember { mutableStateOf(store.playlists()) }
    var tab by remember { mutableStateOf("Home") }
    var query by remember { mutableStateOf("") }
    var current by remember { mutableStateOf<Track?>(null) }
    var fullPlayer by remember { mutableStateOf(false) }
    var video by remember { mutableStateOf(false) }
    var queue by remember { mutableStateOf(emptyList<Track>()) }
    var showPlaylistDialog by remember { mutableStateOf(false) }

    fun refresh() {
        scope.launch {
            tracks = withContext(Dispatchers.IO) {
                AudioRepository(context.contentResolver).loadTracks()
            }
        }
    }
    LaunchedEffect(Unit) { refresh() }

    fun playList(list: List<Track>, index: Int) {
        if (list.isEmpty()) return
        player.setMediaItems(list.map {
            MediaItem.Builder().setMediaId(it.id.toString()).setUri(it.uri).build()
        }, index, 0L)
        player.prepare()
        player.play()
        queue = list
        current = list[index]
        fullPlayer = true
    }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onMediaItemTransition(item: MediaItem?, reason: Int) {
                val id = item?.mediaId?.toLongOrNull()
                current = tracks.firstOrNull { it.id == id }
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Scaffold(
        containerColor = BG,
        bottomBar = {
            if (!fullPlayer) NavigationBar(containerColor = Color(0xFF0D0F12)) {
                listOf(
                    "Home" to Icons.Default.Home,
                    "Search" to Icons.Default.Search,
                    "Library" to Icons.Default.LibraryMusic,
                    "Settings" to Icons.Default.Settings
                ).forEach { (name, icon) ->
                    NavigationBarItem(
                        selected = tab == name,
                        onClick = { tab = name },
                        icon = { Icon(icon, null) },
                        label = { Text(name) }
                    )
                }
            }
        }
    ) { pad ->
        Box(Modifier.fillMaxSize().padding(pad)) {
            if (fullPlayer && current != null) {
                NowPlaying(
                    track = current!!, player = player,
                    favorite = current!!.id in favorites,
                    onFavorite = {
                        favorites = if (current!!.id in favorites)
                            favorites - current!!.id else favorites + current!!.id
                        store.setFavorites(favorites)
                    },
                    onClose = { fullPlayer = false },
                    queue = queue,
                    onQueue = { }
                )
            } else {
                when (tab) {
                    "Home" -> Home(
                        tracks, favorites,
                        onPlay = { i -> playList(tracks, i) },
                        onRefresh = ::refresh,
                        onVideo = { video = true }
                    )
                    "Search" -> Search(
                        query, { query = it }, tracks,
                        onPlay = { t -> playList(tracks, tracks.indexOfFirst { it.id == t.id }) }
                    )
                    "Library" -> Library(
                        tracks.filter { it.id in favorites },
                        playlists,
                        onPlay = { t -> playList(tracks, tracks.indexOfFirst { it.id == t.id }) },
                        onCreate = { showPlaylistDialog = true }
                    )
                    "Settings" -> Settings(store)
                }
            }

            if (!fullPlayer && current != null) {
                MiniPlayer(current!!, player) { fullPlayer = true }
            }

            if (video) VideoScreen(player) { video = false }
        }
    }

    if (showPlaylistDialog) {
        var name by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showPlaylistDialog = false },
            title = { Text("New playlist") },
            text = {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") })
            },
            confirmButton = {
                TextButton(onClick = {
                    if (name.isNotBlank()) {
                        playlists = playlists + Playlist(System.currentTimeMillis(), name.trim(), emptyList())
                        store.savePlaylists(playlists)
                    }
                    showPlaylistDialog = false
                }) { Text("Create") }
            },
            dismissButton = { TextButton(onClick = { showPlaylistDialog = false }) { Text("Cancel") } }
        )
    }
}

@Composable
fun Home(
    tracks: List<Track>, favorites: Set<Long>,
    onPlay: (Int) -> Unit, onRefresh: () -> Unit, onVideo: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("HUDSIFY", color = ACCENT, fontSize = 14.sp, fontWeight = FontWeight.Bold)
            Text("FEEL EVERY BEAT.", fontSize = 32.sp, fontWeight = FontWeight.Black)
            Spacer(Modifier.height(20.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Hero("MUSIC", Icons.Default.MusicNote, Modifier.weight(1f))
                Hero("VIDEO", Icons.Default.Movie, Modifier.weight(1f), onVideo)
            }
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("On your device", fontSize = 21.sp, fontWeight = FontWeight.Bold, Modifier.weight(1f))
                IconButton(onClick = onRefresh) { Icon(Icons.Default.Refresh, null) }
            }
        }
        if (tracks.isEmpty()) item {
            Text("No music found. Add music to your device and refresh.", color = MUTED)
        } else itemsIndexed(tracks.take(40)) { i, t ->
            TrackRow(t, t.id in favorites) { onPlay(i) }
        }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
fun Hero(
    text: String, icon: androidx.compose.ui.graphics.vector.ImageVector,
    modifier: Modifier, onClick: () -> Unit = {}
) {
    Surface(
        modifier.height(112.dp).clickable { onClick() },
        shape = RoundedCornerShape(24.dp), color = SURFACE2
    ) {
        Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.SpaceBetween) {
            Icon(icon, null, tint = ACCENT, modifier = Modifier.size(30.dp))
            Text(text, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun TrackRow(track: Track, favorite: Boolean, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable { onClick() }.padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            Modifier.size(58.dp).clip(RoundedCornerShape(12.dp)).background(SURFACE2),
            contentAlignment = Alignment.Center
        ) { Icon(Icons.Default.MusicNote, null, tint = ACCENT) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(track.title, fontWeight = FontWeight.SemiBold, maxLines = 1)
            Text("${track.artist} • ${track.album}", color = MUTED, fontSize = 12.sp, maxLines = 1)
        }
        if (favorite) Icon(Icons.Default.Favorite, null, tint = ACCENT)
        else Icon(Icons.Default.PlayCircle, null)
    }
}

@Composable
fun Search(
    query: String, onQuery: (String) -> Unit, tracks: List<Track>,
    onPlay: (Track) -> Unit
) {
    val results = tracks.filter {
        query.isBlank() || it.title.contains(query, true) ||
            it.artist.contains(query, true) || it.album.contains(query, true)
    }
    Column(Modifier.fillMaxSize().padding(20.dp)) {
        Spacer(Modifier.height(8.dp))
        Text("Search", fontSize = 30.sp, fontWeight = FontWeight.Black)
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = query, onValueChange = onQuery, modifier = Modifier.fillMaxWidth(),
            singleLine = true, placeholder = { Text("Songs, artists, albums…") },
            leadingIcon = { Icon(Icons.Default.Search, null) }
        )
        Spacer(Modifier.height(14.dp))
        LazyColumn(verticalArrangement = Arrangement.spacedBy(7.dp)) {
            items(results) { t -> TrackRow(t, false) { onPlay(t) } }
        }
    }
}

@Composable
fun Library(
    favorites: List<Track>, playlists: List<Playlist>,
    onPlay: (Track) -> Unit, onCreate: () -> Unit
) {
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Your Library", fontSize = 30.sp, fontWeight = FontWeight.Black)
        }
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Playlists", fontSize = 20.sp, fontWeight = FontWeight.Bold, Modifier.weight(1f))
                FilledTonalButton(onClick = onCreate) {
                    Icon(Icons.Default.Add, null); Spacer(Modifier.width(5.dp)); Text("New")
                }
            }
        }
        items(playlists) { p ->
            Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), color = SURFACE) {
                Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QueueMusic, null, tint = ACCENT)
                    Spacer(Modifier.width(14.dp))
                    Column(Modifier.weight(1f)) {
                        Text(p.name, fontWeight = FontWeight.SemiBold)
                        Text("${p.trackIds.size} songs", color = MUTED, fontSize = 12.sp)
                    }
                }
            }
        }
        item { Text("Liked songs", fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        if (favorites.isEmpty()) item { Text("Your liked songs will appear here.", color = MUTED) }
        else items(favorites) { t -> TrackRow(t, true) { onPlay(t) } }
        item { Spacer(Modifier.height(90.dp)) }
    }
}

@Composable
fun MiniPlayer(track: Track, player: ExoPlayer, onOpen: () -> Unit) {
    Surface(
        Modifier.fillMaxWidth().padding(10.dp).clickable { onOpen() },
        RoundedCornerShape(18.dp), color = Color(0xFF1B1E24), tonalElevation = 8.dp
    ) {
        Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier.size(48.dp).clip(RoundedCornerShape(10.dp)).background(SURFACE2),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.MusicNote, null, tint = ACCENT) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(track.title, fontWeight = FontWeight.Bold, maxLines = 1)
                Text(track.artist, color = MUTED, fontSize = 12.sp)
            }
            IconButton(onClick = {
                if (player.isPlaying) player.pause() else player.play()
            }) {
                Icon(if (player.isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow, null)
            }
        }
    }
}

@Composable
fun NowPlaying(
    track: Track, player: ExoPlayer, favorite: Boolean, onFavorite: () -> Unit,
    onClose: () -> Unit, queue: List<Track>, onQueue: () -> Unit
) {
    var playing by remember { mutableStateOf(player.isPlaying) }
    var shuffle by remember { mutableStateOf(player.shuffleModeEnabled) }
    var repeat by remember { mutableStateOf(player.repeatMode != Player.REPEAT_MODE_OFF) }
    var progress by remember { mutableFloatStateOf(0f) }
    var showLyrics by remember { mutableStateOf(false) }
    var lyrics by remember { mutableStateOf<List<LrcLine>>(emptyList()) }

    LaunchedEffect(player) {
        while (true) {
            val d = player.duration
            progress = if (d > 0) player.currentPosition.toFloat() / d else 0f
            playing = player.isPlaying
            delay(400)
        }
    }

    if (showLyrics) {
        LyricsSheet(player, lyrics, onClose = { showLyrics = false })
    } else {
        Column(
            Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, null) }
                Text("NOW PLAYING", fontSize = 13.sp, fontWeight = FontWeight.Bold, Modifier.weight(1f))
                IconButton(onClick = onQueue) { Icon(Icons.Default.QueueMusic, null) }
            }
            Spacer(Modifier.height(24.dp))
            Box(
                Modifier.fillMaxWidth().aspectRatio(1f)
                    .clip(RoundedCornerShape(28.dp)).background(SURFACE2),
                contentAlignment = Alignment.Center
            ) { Icon(Icons.Default.MusicNote, null, tint = ACCENT, modifier = Modifier.size(100.dp)) }
            Spacer(Modifier.height(25.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(track.title, fontSize = 25.sp, fontWeight = FontWeight.Black, maxLines = 1)
                    Text(track.artist, color = MUTED)
                    Text(track.album, color = MUTED, fontSize = 12.sp)
                }
                IconButton(onClick = onFavorite) {
                    Icon(if (favorite) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                        null, tint = if (favorite) ACCENT else Color.White)
                }
            }
            Slider(
                value = progress.coerceIn(0f, 1f),
                onValueChange = {
                    progress = it
                    if (player.duration > 0) player.seekTo((player.duration * it).toLong())
                }
            )
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text(fmt(player.currentPosition), color = MUTED, fontSize = 12.sp)
                Text(fmt(player.duration), color = MUTED, fontSize = 12.sp)
            }
            Spacer(Modifier.height(10.dp))
            Row(
                Modifier.fillMaxWidth(),
                Arrangement.SpaceEvenly,
                Alignment.CenterVertically
            ) {
                IconButton(onClick = {
                    shuffle = !shuffle; player.shuffleModeEnabled = shuffle
                }) { Icon(Icons.Default.Shuffle, null, tint = if (shuffle) ACCENT else Color.White) }
                IconButton(onClick = {
                    if (player.hasPreviousMediaItem) player.seekToPreviousMediaItem() else player.seekTo(0)
                }) { Icon(Icons.Default.SkipPrevious, null, modifier = Modifier.size(38.dp)) }
                FilledIconButton(
                    onClick = {
                        if (player.isPlaying) player.pause() else player.play()
                        playing = player.isPlaying
                    },
                    modifier = Modifier.size(72.dp)
                ) { Icon(if (playing) Icons.Default.Pause else Icons.Default.PlayArrow, null) }
                IconButton(onClick = { if (player.hasNextMediaItem) player.seekToNextMediaItem() }) {
                    Icon(Icons.Default.SkipNext, null, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = {
                    repeat = !repeat
                    player.repeatMode = if (repeat) Player.REPEAT_MODE_ALL else Player.REPEAT_MODE_OFF
                }) { Icon(Icons.Default.Repeat, null, tint = if (repeat) ACCENT else Color.White) }
            }
            Spacer(Modifier.height(12.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedButton(onClick = { showLyrics = true }) {
                    Icon(Icons.Default.Subtitles, null); Spacer(Modifier.width(5.dp)); Text("Lyrics")
                }
                OutlinedButton(onClick = { }) {
                    Icon(Icons.Default.Download, null); Spacer(Modifier.width(5.dp)); Text("Offline")
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Queue", fontSize = 20.sp, fontWeight = FontWeight.Bold, Modifier.fillMaxWidth())
            queue.take(8).forEach { q ->
                Row(Modifier.fillMaxWidth().padding(vertical = 7.dp)) {
                    Icon(Icons.Default.MusicNote, null, tint = ACCENT)
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text(q.title, fontWeight = FontWeight.SemiBold)
                        Text(q.artist, color = MUTED, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun LyricsSheet(player: ExoPlayer, lines: List<LrcLine>, onClose: () -> Unit) {
    var offset by remember { mutableLongStateOf(0L) }
    var pos by remember { mutableLongStateOf(0L) }
    LaunchedEffect(player) {
        while (true) { pos = player.currentPosition; delay(200) }
    }
    Column(Modifier.fillMaxSize().background(BG).padding(20.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.KeyboardArrowDown, null) }
            Text("SYNCED LYRICS", fontWeight = FontWeight.Bold, Modifier.weight(1f))
            TextButton(onClick = { offset = 0 }) { Text("Reset") }
        }
        if (lines.isEmpty()) {
            Text("No synced lyrics loaded yet.", color = MUTED)
            Text("The LRC parser is included; connect it to your authorized lyrics source.", color = MUTED)
        } else {
            val active = lines.indexOfLast { it.timeMs + offset <= pos }
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(vertical = 30.dp)
            ) {
                itemsIndexed(lines) { i, line ->
                    Text(
                        line.text,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                        fontSize = if (i == active) 25.sp else 19.sp,
                        fontWeight = if (i == active) FontWeight.Black else FontWeight.Normal,
                        color = if (i == active) Color.White else MUTED
                    )
                }
            }
        }
    }
}

@OptIn(UnstableApi::class)
@Composable
fun VideoScreen(player: ExoPlayer, onClose: () -> Unit) {
    val context = LocalContext.current
    var uriText by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().background(Color.Black)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onClose) { Icon(Icons.Default.Close, null, tint = Color.White) }
            Text("VIDEO", color = Color.White, fontWeight = FontWeight.Bold, Modifier.weight(1f))
        }
        AndroidView(
            factory = {
                PlayerView(context).apply {
                    this.player = player
                    useController = true
                }
            },
            modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f)
        )
        Column(Modifier.padding(16.dp)) {
            Text("Play an authorized video URL", color = Color.White, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = uriText, onValueChange = { uriText = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("https://…") },
                singleLine = true
            )
            Spacer(Modifier.height(8.dp))
            Button(onClick = {
                if (uriText.startsWith("http")) {
                    player.setMediaItem(MediaItem.fromUri(Uri.parse(uriText)))
                    player.prepare(); player.play()
                }
            }) { Text("Play") }
        }
    }
}

@Composable
fun Settings(store: HudsifyStore) {
    var gapless by remember { mutableStateOf(store.settings("gapless", true)) }
    var crossfade by remember { mutableStateOf(store.settings("crossfade", false)) }
    LazyColumn(
        Modifier.fillMaxSize(), contentPadding = PaddingValues(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Spacer(Modifier.height(8.dp))
            Text("Settings", fontSize = 30.sp, fontWeight = FontWeight.Black)
            Text("Hudsify 2.0.0", color = MUTED)
            Spacer(Modifier.height(18.dp))
        }
        item { ToggleRow("Gapless playback", "Reduce pauses between tracks", gapless) {
            gapless = it; store.setSetting("gapless", it)
        }}
        item { ToggleRow("Crossfade", "Smooth transition between tracks", crossfade) {
            crossfade = it; store.setSetting("crossfade", it)
        }}
        item { SettingRow("Audio quality", "High / lossless-ready architecture") }
        item { SettingRow("Downloads", "Offline media manager foundation") }
        item { SettingRow("Lyrics", "Synced LRC parser included") }
        item { SettingRow("Subtitles", "Media3 video subtitle-ready") }
        item { SettingRow("Music sources", "Authorized local/remote sources") }
        item { SettingRow("Storage", "Manage local media") }
    }
}

@Composable
fun ToggleRow(title: String, subtitle: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), color = SURFACE) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(subtitle, color = MUTED, fontSize = 12.sp)
            }
            Switch(checked = checked, onCheckedChange = onChange)
        }
    }
}

@Composable
fun SettingRow(title: String, value: String) {
    Surface(Modifier.fillMaxWidth(), RoundedCornerShape(18.dp), color = SURFACE) {
        Row(Modifier.padding(17.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.SemiBold)
                Text(value, color = MUTED, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = MUTED)
        }
    }
}

fun fmt(ms: Long): String {
    if (ms < 0 || ms == androidx.media3.common.C.TIME_UNSET) return "0:00"
    val sec = ms.coerceAtLeast(0) / 1000
    return "${sec / 60}:${(sec % 60).toString().padStart(2, '0')}"
}
