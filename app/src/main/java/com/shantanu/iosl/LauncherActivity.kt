package com.shantanu.iosl

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.graphics.drawable.toBitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

data class AppItem(val label: String, val pkg: String, val icon: ImageBitmap, val cat: Int)

class LauncherActivity : ComponentActivity() {
    private val tick = mutableIntStateOf(0)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { Home(tick.intValue) }
    }

    // reload app list whenever we come back (installs/uninstalls)
    override fun onResume() {
        super.onResume()
        tick.intValue++
    }
}

fun launch(ctx: Context, pkg: String) {
    ctx.packageManager.getLaunchIntentForPackage(pkg)?.let { ctx.startActivity(it) }
}

fun loadApps(ctx: Context): List<AppItem> {
    val pm = ctx.packageManager
    val q = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
    return pm.queryIntentActivities(q, 0)
        .filter { it.activityInfo.packageName != ctx.packageName }
        .map {
            AppItem(
                it.loadLabel(pm).toString(),
                it.activityInfo.packageName,
                it.loadIcon(pm).toBitmap(128, 128).asImageBitmap(),
                it.activityInfo.applicationInfo.category
            )
        }
        .distinctBy { it.pkg }
        .sortedBy { it.label.lowercase() }
}

fun dockPackages(ctx: Context): List<String> {
    val pm = ctx.packageManager
    return listOf(
        Intent(Intent.ACTION_DIAL, Uri.parse("tel:")),
        Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:")),
        Intent(Intent.ACTION_VIEW, Uri.parse("https://")),
        Intent(MediaStore.ACTION_IMAGE_CAPTURE)
    ).mapNotNull { pm.resolveActivity(it, 0)?.activityInfo?.packageName }.distinct()
}

fun catName(c: Int) = when (c) {
    ApplicationInfo.CATEGORY_GAME -> "Games"
    ApplicationInfo.CATEGORY_AUDIO -> "Music"
    ApplicationInfo.CATEGORY_VIDEO -> "Video"
    ApplicationInfo.CATEGORY_IMAGE -> "Photo"
    ApplicationInfo.CATEGORY_SOCIAL -> "Social"
    ApplicationInfo.CATEGORY_NEWS -> "News"
    ApplicationInfo.CATEGORY_MAPS -> "Travel"
    ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
    else -> "Other"
}

@Composable
fun Home(tick: Int) {
    val ctx = LocalContext.current
    var apps by remember { mutableStateOf<List<AppItem>>(emptyList()) }
    var dockPk by remember { mutableStateOf<List<String>>(emptyList()) }
    LaunchedEffect(tick) {
        withContext(Dispatchers.IO) { loadApps(ctx) to dockPackages(ctx) }.let { apps = it.first; dockPk = it.second }
    }
    var editing by remember { mutableStateOf(false) }
    var searching by remember { mutableStateOf(false) }
    BackHandler(editing || searching) { editing = false; searching = false }

    val dock = apps.filter { it.pkg in dockPk }.ifEmpty { apps.take(4) }.take(4)
    val pages = apps.filter { it !in dock }.chunked(24)
    val pager = rememberPagerState(pageCount = { pages.size + 1 }) // last page = App Library

    Box(
        Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .pointerInput(Unit) {
                var dy = 0f
                detectVerticalDragGestures(
                    onDragStart = { dy = 0f },
                    onDragEnd = { if (dy > 180f && !editing) searching = true }
                ) { _, d -> dy += d }
            }
    ) {
        Column(Modifier.fillMaxSize()) {
            if (editing) {
                Text(
                    "Done", color = Color.White, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.align(Alignment.End).clickable { editing = false }.padding(16.dp)
                )
            }
            HorizontalPager(pager, Modifier.weight(1f)) { p ->
                if (p < pages.size) GridPage(pages[p], editing) { editing = true }
                else Library(apps)
            }
            Row(Modifier.fillMaxWidth().padding(8.dp), Arrangement.Center) {
                repeat(pager.pageCount) {
                    Box(
                        Modifier.padding(4.dp).size(7.dp).clip(CircleShape)
                            .background(Color.White.copy(if (it == pager.currentPage) 0.95f else 0.4f))
                    )
                }
            }
            Row(
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp).fillMaxWidth()
                    .clip(RoundedCornerShape(32.dp)).background(Color.White.copy(0.25f)).padding(14.dp),
                Arrangement.SpaceEvenly
            ) { dock.forEach { AppIcon(it, editing, { editing = true }, showLabel = false) } }
        }
        if (searching) Search(apps) { searching = false }
    }
}

@Composable
fun GridPage(items: List<AppItem>, editing: Boolean, onEdit: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(horizontal = 12.dp, vertical = 8.dp)) {
        items.chunked(4).forEach { row ->
            Row(Modifier.fillMaxWidth().height(94.dp)) {
                row.forEach { Box(Modifier.weight(1f), Alignment.TopCenter) { AppIcon(it, editing, onEdit) } }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppIcon(
    a: AppItem, editing: Boolean, onEdit: () -> Unit,
    size: Dp = 60.dp, showLabel: Boolean = true
) {
    val ctx = LocalContext.current
    val jiggle = rememberInfiniteTransition(label = "jiggle")
    val rot by jiggle.animateFloat(
        -1.6f, 1.6f, infiniteRepeatable(tween(120, easing = LinearEasing), RepeatMode.Reverse), label = "rot"
    )
    Column(
        Modifier.rotate(if (editing) rot else 0f)
            .combinedClickable(onClick = { if (!editing) launch(ctx, a.pkg) }, onLongClick = onEdit),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box {
            Image(a.icon, a.label, Modifier.size(size).clip(RoundedCornerShape(size * 0.225f)))
            if (editing) {
                Box(
                    Modifier.size(22.dp).background(Color(0xFF8E8E93), CircleShape).clickable {
                        ctx.startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:${a.pkg}")))
                    },
                    Alignment.Center
                ) { Text("–", color = Color.White, fontSize = 14.sp) }
            }
        }
        if (showLabel) {
            Text(
                a.label, color = Color.White, fontSize = 11.sp, maxLines = 1,
                overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                style = TextStyle(shadow = Shadow(Color.Black.copy(0.5f), Offset(0f, 1f), 3f)),
                modifier = Modifier.width(size + 10.dp).padding(top = 4.dp)
            )
        }
    }
}

@Composable
fun Library(apps: List<AppItem>) {
    val groups = apps.groupBy { catName(it.cat) }.toList().sortedBy { it.first }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("App Library", color = Color.White, fontSize = 26.sp, fontWeight = FontWeight.Bold)
        groups.chunked(2).forEach { row ->
            Row(Modifier.fillMaxWidth(), Arrangement.spacedBy(16.dp)) {
                row.forEach { (name, list) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Column(
                            Modifier.fillMaxWidth().clip(RoundedCornerShape(24.dp))
                                .background(Color.White.copy(0.22f)).padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            list.take(4).chunked(2).forEach { r ->
                                Row(Modifier.fillMaxWidth(), Arrangement.SpaceEvenly) {
                                    r.forEach { AppIcon(it, false, {}, 44.dp, false) }
                                }
                            }
                        }
                        Text(name, color = Color.White, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
fun Search(apps: List<AppItem>, close: () -> Unit) {
    val ctx = LocalContext.current
    var q by remember { mutableStateOf("") }
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Column(Modifier.fillMaxSize().background(Color.Black.copy(0.7f)).padding(16.dp)) {
        Box(
            Modifier.fillMaxWidth().clip(RoundedCornerShape(14.dp))
                .background(Color.White.copy(0.18f)).padding(12.dp)
        ) {
            if (q.isEmpty()) Text("Search", color = Color.White.copy(0.5f), fontSize = 17.sp)
            BasicTextField(
                q, { q = it }, Modifier.fillMaxWidth().focusRequester(focus),
                textStyle = TextStyle(color = Color.White, fontSize = 17.sp),
                cursorBrush = SolidColor(Color.White), singleLine = true
            )
        }
        Spacer(Modifier.height(12.dp))
        val res = if (q.isBlank()) emptyList() else apps.filter { it.label.contains(q, ignoreCase = true) }
        LazyColumn {
            items(res) { a ->
                Row(
                    Modifier.fillMaxWidth().clickable { launch(ctx, a.pkg); close() }.padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Image(a.icon, a.label, Modifier.size(44.dp).clip(RoundedCornerShape(10.dp)))
                    Spacer(Modifier.width(12.dp))
                    Text(a.label, color = Color.White, fontSize = 16.sp)
                }
            }
        }
    }
}
