package com.musicmr.player

import android.graphics.drawable.BitmapDrawable
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Equalizer
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Send
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material.icons.rounded.Speed
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.media3.common.Player
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NowPlaying(vm: PlayerViewModel, onClose: () -> Unit) {
    val song = vm.current
    val ctx = LocalContext.current
    val uriHandler = LocalUriHandler.current
    var accentTarget by remember { mutableStateOf(Violet) }
    var showSpeed by remember { mutableStateOf(false) }
    var showSleep by remember { mutableStateOf(false) }
    var showEq by remember { mutableStateOf(false) }
    var showQueue by remember { mutableStateOf(false) }
    var showTag by remember { mutableStateOf(false) }

    LaunchedEffect(song?.id) {
        val art = song?.artUri
        if (art == null) {
            accentTarget = Violet
        } else {
            val req = ImageRequest.Builder(ctx).data(art).allowHardware(false).size(128).build()
            val res = ctx.imageLoader.execute(req)
            val bmp = ((res as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap
            accentTarget = if (bmp != null) {
                withContext(Dispatchers.Default) {
                    val p = Palette.from(bmp).generate()
                    val fallback = 0xFF8B5CF6.toInt()
                    Color(p.getVibrantColor(p.getDarkVibrantColor(p.getMutedColor(fallback))))
                }
            } else Violet
        }
    }
    val accent by animateColorAsState(accentTarget, tween(600), label = "accent")
    val scale by animateFloatAsState(
        if (vm.isPlaying) 1f else 0.88f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "scale"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(Bg)
            .background(Brush.verticalGradient(listOf(accent.copy(alpha = 0.65f), Bg)))
    ) {
        Column(
            Modifier.fillMaxSize().systemBarsPadding().padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onClose) {
                    Icon(Icons.Rounded.KeyboardArrowDown, null, modifier = Modifier.size(32.dp))
                }
                Text(
                    "NOW PLAYING",
                    modifier = Modifier.weight(1f),
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    letterSpacing = 2.sp
                )
                IconButton(onClick = { uriHandler.openUri(TELEGRAM_URL) }) {
                    Icon(Icons.Rounded.Send, null, tint = TelegramBlue)
                }
            }
            Spacer(Modifier.height(12.dp))
            Art(
                song?.artUri,
                Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .graphicsLayer(scaleX = scale, scaleY = scale)
                    .shadow(24.dp, RoundedCornerShape(28.dp)),
                28.dp
            )
            Spacer(Modifier.height(24.dp))
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(
                        song?.title ?: "",
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        song?.artist ?: "",
                        color = Color.White.copy(alpha = 0.7f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                if (song != null) {
                    val fav = song.id in vm.favorites
                    IconButton(onClick = { vm.toggleFav(song) }) {
                        Icon(
                            if (fav) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                            null,
                            tint = if (fav) Color(0xFFFB7185) else Color.White
                        )
                    }
                }
            }

            ProgressSection(vm)

            Spacer(Modifier.height(12.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = { vm.toggleShuffle() }) {
                    Icon(
                        Icons.Rounded.Shuffle, null,
                        tint = if (vm.shuffle) Color.White else Color.White.copy(alpha = 0.4f)
                    )
                }
                IconButton(onClick = { vm.previous() }) {
                    Icon(Icons.Rounded.SkipPrevious, null, modifier = Modifier.size(38.dp))
                }
                Box(
                    Modifier
                        .size(72.dp)
                        .clip(CircleShape)
                        .background(Color.White)
                        .clickable { vm.togglePlay() },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (vm.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        null,
                        tint = Bg,
                        modifier = Modifier.size(42.dp)
                    )
                }
                IconButton(onClick = { vm.next() }) {
                    Icon(Icons.Rounded.SkipNext, null, modifier = Modifier.size(38.dp))
                }
                IconButton(onClick = { vm.cycleRepeat() }) {
                    Icon(
                        if (vm.repeatMode == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        null,
                        tint = if (vm.repeatMode == Player.REPEAT_MODE_OFF) Color.White.copy(alpha = 0.4f) else Color.White
                    )
                }
            }

            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
            ) {
                Pill(Icons.Rounded.Speed, vm.speed.toString().removeSuffix(".0") + "x") { showSpeed = true }
                val sl = vm.sleepLeft
                Pill(Icons.Rounded.Bedtime, if (sl > 0) "%d:%02d".format(sl / 60, sl % 60) else "Sleep") {
                    showSleep = true
                }
                Pill(Icons.Rounded.Equalizer, "EQ") { showEq = true }
                Pill(Icons.Rounded.Label, "Tag") { showTag = true }
                Pill(Icons.Rounded.QueueMusic, "Queue") { showQueue = true }
            }
            Spacer(Modifier.height(12.dp))
        }
    }

    if (showSpeed) {
        val options = listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f)
        ChoiceDialog(
            "Playback speed",
            options.map { it.toString().removeSuffix(".0") + "x" },
            options.indexOf(vm.speed),
            { vm.changeSpeed(options[it]) },
            { showSpeed = false }
        )
    }
    if (showSleep) {
        val options = listOf(0, 10, 15, 30, 45, 60)
        ChoiceDialog(
            "Sleep timer",
            options.map { if (it == 0) "Off" else "$it min" },
            -1,
            { vm.setSleep(options[it]) },
            { showSleep = false }
        )
    }
    if (showEq) EqualizerDialog(vm) { showEq = false }
    if (showTag && song != null) TagPickerDialog(vm, listOf(song), onDismiss = { showTag = false })
    if (showQueue) {
        ModalBottomSheet(onDismissRequest = { showQueue = false }, containerColor = Surface1) {
            Text(
                "Up next",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp)
            )
            LazyColumn(Modifier.heightIn(max = 520.dp)) {
                itemsIndexed(vm.queue) { i, s ->
                    val playing = s.id == song?.id
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { vm.playIndex(i) }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Art(s.artUri, Modifier.size(44.dp), 10.dp)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            Text(
                                s.title,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (playing) Violet else Color.White,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                s.artist,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ChoiceDialog(
    title: String,
    options: List<String>,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(title) },
        text = {
            Column {
                options.forEachIndexed { i, o ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clickable { onSelect(i); onDismiss() }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        RadioButton(selected = i == selected, onClick = null)
                        Spacer(Modifier.width(8.dp))
                        Text(o)
                    }
                }
            }
        }
    )
}

@Composable
fun EqualizerDialog(vm: PlayerViewModel, onDismiss: () -> Unit) {
    LaunchedEffect(Unit) { vm.setupEffects() }
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Done") } },
        title = { Text("Equalizer") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState())) {
                if (vm.eqLevels.isEmpty()) {
                    Text("Equalizer isn't available yet. Start playing a song first.")
                } else {
                    if (vm.presets.isNotEmpty()) {
                        Text("Presets", style = MaterialTheme.typography.labelLarge)
                        Row(Modifier.horizontalScroll(rememberScrollState())) {
                            vm.presets.forEachIndexed { i, p ->
                                TextButton(onClick = { vm.usePreset(i) }) { Text(p) }
                            }
                        }
                    }
                    vm.eqLevels.forEachIndexed { i, lvl ->
                        val f = vm.eqFreqs.getOrNull(i) ?: 0
                        val label = if (f >= 1000) "${f / 1000}kHz" else "${f}Hz"
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(label, modifier = Modifier.width(60.dp), fontSize = 12.sp)
                            Slider(
                                value = lvl.toFloat(),
                                onValueChange = { vm.setBand(i, it.toInt()) },
                                valueRange = vm.eqMin.toFloat()..vm.eqMax.toFloat()
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Bass boost", style = MaterialTheme.typography.labelLarge)
                    Slider(
                        value = vm.bassStrength.toFloat(),
                        onValueChange = { vm.setBass(it.toInt()) },
                        valueRange = 0f..1000f
                    )
                }
            }
        }
    )
}

@Composable
fun ProgressSection(vm: PlayerViewModel) {
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    val dur = vm.duration.coerceAtLeast(1L)
    val shown = if (dragging) dragValue else (vm.position.toFloat() / dur).coerceIn(0f, 1f)
    Column {
        Slider(
            value = shown,
            onValueChange = { dragging = true; dragValue = it },
            onValueChangeFinished = { vm.seekTo((dragValue * dur).toLong()); dragging = false },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f)
            )
        )
        Row(Modifier.fillMaxWidth()) {
            Text(
                fmt(if (dragging) (dragValue * dur).toLong() else vm.position),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
            Spacer(Modifier.weight(1f))
            Text(
                fmt(vm.duration),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.7f)
            )
        }
    }
}
