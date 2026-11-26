package com.example.reels

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.pager.VerticalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import kotlinx.coroutines.delay
import kotlin.math.abs

data class Reel(val uri: String, val user: String, val caption: String)

private const val BASE = "https://commondatastorage.googleapis.com/gtv-videos-bucket/sample/"
private val SAMPLES = listOf(
    Reel(BASE + "ForBiggerBlazes.mp4", "@blazes", "Big blazes #fun #fire"),
    Reel(BASE + "ForBiggerEscapes.mp4", "@escapes", "Great escapes #travel"),
    Reel(BASE + "ForBiggerFun.mp4", "@funtime", "Just for fun #vibes")
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { ReelsApp() }
    }
}

@Composable
fun ReelsApp() {
    val reels = remember { mutableStateListOf<Reel>().apply { addAll(SAMPLES) } }
    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickMultipleVisualMedia()
    ) { uris -> reels.addAll(0, uris.map { Reel(it.toString(), "@you", "My new video") }) }
    val pager = rememberPagerState { reels.size }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        VerticalPager(state = pager, modifier = Modifier.fillMaxSize()) { page ->
            ReelPage(reel = reels[page], active = pager.currentPage == page)
        }
        Row(
            Modifier.align(Alignment.TopCenter).padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Text("Following", color = Color(0xB3FFFFFF), fontSize = 17.sp)
            Text("For You", color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
        }
        Button(
            onClick = {
                picker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.VideoOnly)
                )
            },
            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp)
        ) { Text("+") }
    }
}

@Composable
fun ActionButton(icon: String, label: String, onClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .pointerInput(Unit) { detectTapGestures { onClick() } }
            .padding(vertical = 10.dp)
    ) {
        Text(icon, fontSize = 32.sp)
        Text(label, color = Color.White, fontSize = 12.sp)
    }
}

@Composable
fun ReelPage(reel: Reel, active: Boolean) {
    val context = LocalContext.current
    val exo = remember(reel.uri) {
        ExoPlayer.Builder(context).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE
            setMediaItem(MediaItem.fromUri(reel.uri))
            prepare()
        }
    }
    val isActive by rememberUpdatedState(active)
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(exo, lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_PAUSE) exo.pause()
            if (event == Lifecycle.Event.ON_RESUME && isActive) exo.play()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            exo.release()
        }
    }
    LaunchedEffect(active) { if (active) exo.play() else exo.pause() }

    var progress by remember { mutableStateOf(0f) }
    LaunchedEffect(exo) {
        while (true) {
            delay(100)
            val d = exo.duration
            progress = if (d > 0) (exo.currentPosition.toFloat() / d).coerceIn(0f, 1f) else 0f
        }
    }

    var liked by remember { mutableStateOf(false) }
    var likes by remember { mutableStateOf(abs(reel.uri.hashCode()) % 900 + 100) }
    var showHeart by remember { mutableStateOf(false) }
    LaunchedEffect(showHeart) { if (showHeart) { delay(600); showHeart = false } }

    val comments = remember { mutableStateListOf<String>() }
    var showComments by remember { mutableStateOf(false) }

    fun like() { liked = !liked; likes += if (liked) 1 else -1 }

    Box(
        Modifier.fillMaxSize().pointerInput(Unit) {
            detectTapGestures(
                onTap = { if (exo.isPlaying) exo.pause() else exo.play() },
                onDoubleTap = { if (!liked) like(); showHeart = true }
            )
        }
    ) {
        AndroidView(
            factory = { ctx -> PlayerView(ctx).apply { player = exo; useController = false } },
            modifier = Modifier.fillMaxSize()
        )
        if (showHeart) {
            Text("❤️", fontSize = 96.sp, modifier = Modifier.align(Alignment.Center))
        }
        Column(
            Modifier.align(Alignment.BottomEnd).padding(end = 12.dp, bottom = 40.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            ActionButton(if (liked) "❤️" else "🤍", likes.toString()) { like() }
            ActionButton("💬", comments.size.toString()) { showComments = true }
            ActionButton("↗️", "Share") {
                val send = Intent(Intent.ACTION_SEND).apply {
                    type = "text/plain"
                    putExtra(Intent.EXTRA_TEXT, reel.caption + " " + reel.uri)
                }
                context.startActivity(Intent.createChooser(send, null))
            }
        }
        Column(Modifier.align(Alignment.BottomStart).padding(start = 16.dp, bottom = 24.dp, end = 88.dp)) {
            Text(reel.user, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(reel.caption, color = Color.White, fontSize = 15.sp)
        }
        Box(
            Modifier.align(Alignment.BottomStart)
                .fillMaxWidth(progress).height(2.dp).background(Color.White)
        )
    }

    if (showComments) {
        var draft by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showComments = false },
            title = { Text("Comments (${comments.size})") },
            text = {
                Column {
                    comments.forEach { Text(it, modifier = Modifier.padding(vertical = 2.dp)) }
                    OutlinedTextField(
                        value = draft,
                        onValueChange = { draft = it },
                        placeholder = { Text("Add comment...") }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (draft.isNotBlank()) { comments.add("@you: " + draft.trim()); draft = "" }
                }) { Text("Post") }
            },
            dismissButton = { TextButton(onClick = { showComments = false }) { Text("Close") } }
        )
    }
}
