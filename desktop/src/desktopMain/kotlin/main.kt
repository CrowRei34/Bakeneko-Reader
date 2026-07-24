import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items as gridItems
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.background
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koitharu.kotatsu.parsers.model.MangaParserSource
import org.koitharu.kotatsu.parsers.model.Manga
import io.github.landwarderer.futon.desktop.repo.MangaRepository
import io.github.landwarderer.futon.desktop.db.DatabaseManager
import DesktopMangaLoaderContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

enum class Screen {
    EXPLORE, LIBRARY, HISTORY, DOWNLOADS, DETAILS, READER
}

var currentScreen by mutableStateOf(Screen.EXPLORE)
var selectedManga by mutableStateOf<Manga?>(null)
var selectedChapterIndex by mutableStateOf(0)

val DarkColors = darkColorScheme(
    primary = Color(0xFF90CAF9),
    secondary = Color(0xFF81D4FA),
    background = Color(0xFF121212),
    surface = Color(0xFF1E1E1E),
    onPrimary = Color.Black,
    onSecondary = Color.Black,
    onBackground = Color.White,
    onSurface = Color.White
)

fun main() = application {
    DatabaseManager.init()
    
    Window(onCloseRequest = ::exitApplication, title = "Futon Linux - Premium Manga Reader") {
        App()
    }
}

@Composable
fun App() {
    MaterialTheme(colorScheme = DarkColors) {
        Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
            val context = remember { DesktopMangaLoaderContext() }
            val repo = remember { MangaRepository(context) }
            
            Scaffold(
                topBar = {
                    @OptIn(ExperimentalMaterial3Api::class)
                    TopAppBar(
                        title = { 
                            Text(
                                text = when (currentScreen) {
                                    Screen.EXPLORE -> "Explorar Catálogo"
                                    Screen.LIBRARY -> "Mi Biblioteca"
                                    Screen.HISTORY -> "Historial"
                                    else -> selectedManga?.title ?: ""
                                },
                                fontWeight = FontWeight.Bold
                            ) 
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        ),
                        navigationIcon = {
                            if (currentScreen == Screen.DETAILS || currentScreen == Screen.READER) {
                                TextButton(onClick = { 
                                    if (currentScreen == Screen.READER) currentScreen = Screen.DETAILS
                                    else currentScreen = Screen.EXPLORE // Or Library based on previous state
                                }) { 
                                    Text("← Atrás", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                }
                            }
                        },
                        actions = {
                            if (currentScreen == Screen.EXPLORE || currentScreen == Screen.LIBRARY || currentScreen == Screen.DOWNLOADS || currentScreen == Screen.HISTORY) {
                                TextButton(onClick = { currentScreen = Screen.EXPLORE }) {
                                    Text("Catálogo", color = if (currentScreen == Screen.EXPLORE) MaterialTheme.colorScheme.primary else Color.Gray)
                                }
                                TextButton(onClick = { currentScreen = Screen.LIBRARY }) {
                                    Text("Biblioteca", color = if (currentScreen == Screen.LIBRARY) MaterialTheme.colorScheme.primary else Color.Gray)
                                }
                                TextButton(onClick = { currentScreen = Screen.DOWNLOADS }) {
                                    Text("Descargas", color = if (currentScreen == Screen.DOWNLOADS) MaterialTheme.colorScheme.primary else Color.Gray)
                                }
                                TextButton(onClick = { currentScreen = Screen.HISTORY }) {
                                    Text("Historial", color = if (currentScreen == Screen.HISTORY) MaterialTheme.colorScheme.primary else Color.Gray)
                                }
                            }
                        }
                    )
                }
            ) { padding ->
                Box(Modifier.padding(padding).fillMaxSize()) {
                    when (currentScreen) {
                        Screen.EXPLORE -> ExploreScreen(repo) {
                            selectedManga = it
                            currentScreen = Screen.DETAILS
                        }
                        Screen.LIBRARY -> LibraryScreen(repo) {
                            selectedManga = it
                            currentScreen = Screen.DETAILS
                        }
                        Screen.DOWNLOADS -> DownloadsScreen(repo, onMangaClick = { manga ->
                            selectedManga = manga
                            currentScreen = Screen.DETAILS
                        })
                        Screen.HISTORY -> HistoryScreen(repo) {
                            selectedManga = it
                            currentScreen = Screen.DETAILS
                        }
                        Screen.DETAILS -> selectedManga?.let { DetailsScreen(repo, it) }
                        Screen.READER -> selectedManga?.let { ReaderScreen(repo, it) }
                    }
                }
            }
        }
    }
}

@Composable
fun AsyncImage(url: String?, modifier: Modifier = Modifier, contentDescription: String? = null, contentScale: ContentScale = ContentScale.Crop, headers: Map<String, String> = emptyMap()) {
    var image by remember(url) { mutableStateOf<ImageBitmap?>(null) }
    var error by remember(url) { mutableStateOf(false) }
    var errorMsg by remember(url) { mutableStateOf<String?>(null) }
    var retryCount by remember(url) { mutableStateOf(0) }
    val clipboardManager = androidx.compose.ui.platform.LocalClipboardManager.current
    
    LaunchedEffect(url, retryCount) {
        if (url.isNullOrEmpty()) return@LaunchedEffect
        error = false
        errorMsg = null
        withContext(Dispatchers.IO) {
            try {
                if (url.startsWith("file://")) {
                    val path = url.removePrefix("file://")
                    val file = java.io.File(path)
                    file.inputStream().use { stream ->
                        val bmp = androidx.compose.ui.res.loadImageBitmap(stream)
                        image = bmp
                    }
                } else {
                    // Ensure spaces are properly URL encoded to avoid HTTP 400 errors
                    val encodedUrl = url.replace(" ", "%20")
                    val connection = URL(encodedUrl).openConnection() as HttpURLConnection
                    connection.setRequestProperty("User-Agent", "Mozilla/5.0")
                    // Adding generic referers to bypass basic hotlink protection on some servers
                    connection.setRequestProperty("Referer", encodedUrl)
                    headers.forEach { (key, value) ->
                        connection.setRequestProperty(key, value)
                    }
                    connection.connectTimeout = 5000
                    connection.readTimeout = 5000
                    connection.inputStream.use { stream ->
                        val bmp = androidx.compose.ui.res.loadImageBitmap(stream)
                        image = bmp
                    }
                }
            } catch (e: Exception) { 
                e.printStackTrace() 
                error = true
                errorMsg = e.stackTraceToString()
            }
        }
    }
    
    if (image != null) {
        Image(bitmap = image!!, contentDescription = contentDescription, modifier = modifier, contentScale = contentScale)
    } else if (error) {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("Error al cargar la imagen", color = Color.Gray, modifier = Modifier.clickable { retryCount++ })
                Text("Clic para reintentar", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall, modifier = Modifier.clickable { retryCount++ })
                if (errorMsg != null) {
                    Spacer(Modifier.height(8.dp))
                    Text(
                        "Copiar Log", 
                        color = MaterialTheme.colorScheme.secondary, 
                        style = MaterialTheme.typography.labelSmall, 
                        modifier = Modifier.clickable {
                            clipboardManager.setText(androidx.compose.ui.text.AnnotatedString(errorMsg!!))
                        }.padding(4.dp)
                    )
                }
            }
        }
    } else {
        Box(modifier = modifier.background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(modifier = Modifier.size(24.dp), color = MaterialTheme.colorScheme.primary, strokeWidth = 2.dp)
        }
    }
}

@Composable
fun ExploreScreen(repo: MangaRepository, onMangaClick: (Manga) -> Unit) {
    var mangas by remember { mutableStateOf<List<Manga>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    var searchQuery by remember { mutableStateOf("") }
    
    val allSources = MangaParserSource.values()
    var selectedSource by remember { mutableStateOf(allSources.firstOrNull { it.name.contains("MANGADEX", true) } ?: allSources.first()) }
    var showSourceDropdown by remember { mutableStateOf(false) }
    
    var hasMore by remember { mutableStateOf(true) }
    var loadingMore by remember { mutableStateOf(false) }
    
    val scope = rememberCoroutineScope()
    
    fun performSearch(query: String = "") {
        scope.launch {
            try {
                loading = true
                mangas = repo.fetchCatalog(selectedSource, offset = 0, query = query)
                hasMore = mangas.isNotEmpty()
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loading = false
            }
        }
    }

    fun loadMore() {
        if (loadingMore || !hasMore) return
        scope.launch {
            try {
                loadingMore = true
                val nextMangas = repo.fetchCatalog(selectedSource, offset = mangas.size, query = searchQuery)
                if (nextMangas.isEmpty()) {
                    hasMore = false
                } else {
                    mangas = mangas + nextMangas
                }
            } catch (e: Exception) {
                e.printStackTrace()
                hasMore = false
            } finally {
                loadingMore = false
            }
        }
    }
    
    LaunchedEffect(Unit) {
        performSearch()
    }
    
    Column(Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box {
                Button(onClick = { showSourceDropdown = true }, modifier = Modifier.height(56.dp)) {
                    Text(selectedSource.name)
                }
                androidx.compose.material3.DropdownMenu(
                    expanded = showSourceDropdown,
                    onDismissRequest = { showSourceDropdown = false }
                ) {
                    allSources.forEach { source ->
                        androidx.compose.material3.DropdownMenuItem(
                            text = { Text(source.name) },
                            onClick = {
                                selectedSource = source
                                showSourceDropdown = false
                                performSearch(searchQuery)
                            }
                        )
                    }
                }
            }
            Spacer(Modifier.width(16.dp))
            androidx.compose.material3.OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier.weight(1f),
                label = { Text("Buscar manga...") },
                singleLine = true
            )
            Spacer(Modifier.width(16.dp))
            Button(
                onClick = { performSearch(searchQuery) },
                modifier = Modifier.height(56.dp)
            ) {
                Text("Buscar")
            }
        }
        
        if (loading) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
            }
        } else {
            MangaGrid(mangas, onMangaClick, onLoadMore = { loadMore() }, hasMore = hasMore)
        }
    }
}

@Composable
fun LibraryScreen(repo: MangaRepository, onMangaClick: (Manga) -> Unit) {
    var mangas by remember { mutableStateOf<List<Manga>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                loading = true
                val favorites = repo.getFavorites()
                mangas = favorites.map { it.first }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loading = false
            }
        }
    }
    
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else if (mangas.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Tu biblioteca está vacía.", color = Color.Gray)
        }
    } else {
        MangaGrid(mangas, onMangaClick)
    }
}

@Composable
fun DownloadsScreen(repo: MangaRepository, onMangaClick: (Manga) -> Unit) {
    var mangas by remember { mutableStateOf<List<Manga>>(emptyList()) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                loading = true
                val all = repo.getAllMangas()
                mangas = all.filter { manga -> 
                    io.github.landwarderer.futon.desktop.repo.DownloadManager.hasAnyDownloads(
                        manga.source as? MangaParserSource ?: MangaParserSource.MANGADEX, 
                        manga.url
                    )
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loading = false
            }
        }
    }
    
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else if (mangas.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No tienes mangas descargados.", color = Color.Gray)
        }
    } else {
        MangaGrid(mangas, onMangaClick)
    }
}

@Composable
fun MangaGrid(mangas: List<Manga>, onMangaClick: (Manga) -> Unit, onLoadMore: (() -> Unit)? = null, hasMore: Boolean = false) {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(minSize = 160.dp),
        contentPadding = PaddingValues(16.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        gridItems(mangas) { manga ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onMangaClick(manga) },
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                shape = RoundedCornerShape(12.dp)
            ) {
                Column {
                    Box {
                        AsyncImage(
                            url = manga.coverUrl, 
                            modifier = Modifier
                                .fillMaxWidth()
                                .aspectRatio(0.7f)
                                .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                        )
                        if (io.github.landwarderer.futon.desktop.repo.DownloadManager.hasAnyDownloads(manga.source as? MangaParserSource ?: MangaParserSource.MANGADEX, manga.url)) {
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.9f), shape = RoundedCornerShape(6.dp))
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                                    .align(Alignment.TopEnd)
                            ) {
                                Text("⬇️", style = MaterialTheme.typography.labelMedium)
                            }
                        }
                    }
                    Column(Modifier.padding(12.dp)) {
                        Text(
                            text = manga.title, 
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = manga.authors?.joinToString() ?: "Desconocido", 
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.7f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        
        if (hasMore && onLoadMore != null) {
            item(span = { androidx.compose.foundation.lazy.grid.GridItemSpan(maxLineSpan) }) {
                LaunchedEffect(Unit) {
                    onLoadMore()
                }
                Box(Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            }
        }
    }
}

@Composable
fun HistoryScreen(repo: MangaRepository, onMangaSelected: (Manga) -> Unit) {
    var history by remember { mutableStateOf<List<Pair<Manga, Pair<Int, Int>>>>(emptyList()) }
    val scope = rememberCoroutineScope()
    
    LaunchedEffect(Unit) {
        scope.launch {
            try {
                history = repo.getHistoryList()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
    
    Column(Modifier.fillMaxSize().padding(16.dp)) {
        Text("Historial de Lectura", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
        Spacer(Modifier.height(16.dp))
        
        if (history.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("No has leído nada aún.", color = Color.Gray)
            }
        } else {
            androidx.compose.foundation.lazy.grid.LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 160.dp),
                modifier = Modifier.fillMaxSize(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                gridItems(history) { pair ->
                    val manga = pair.first
                    val chapterIndex = pair.second.first
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .aspectRatio(0.7f)
                            .clickable { onMangaSelected(manga) },
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Box(Modifier.fillMaxSize()) {
                            AsyncImage(
                                url = manga.coverUrl,
                                modifier = Modifier.fillMaxSize(),
                                contentDescription = manga.title
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .fillMaxWidth()
                                    .background(Color.Black.copy(alpha = 0.7f))
                                    .padding(8.dp)
                            ) {
                                Column {
                                    Text(
                                        text = manga.title,
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        maxLines = 1
                                    )
                                    Text(
                                        text = "Capítulo ${chapterIndex + 1}",
                                        color = MaterialTheme.colorScheme.primary,
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DetailsScreen(repo: MangaRepository, manga: Manga) {
    var fullManga by remember { mutableStateOf(manga) }
    var isFavorite by remember { mutableStateOf(false) }
    var historyIndex by remember { mutableStateOf<Int?>(null) }
    var loading by remember { mutableStateOf(true) }
    val scope = rememberCoroutineScope()
    val sourceEnum = MangaParserSource.values().firstOrNull { it.name.contains("MANGADEX", true) } ?: MangaParserSource.values().first()
    val activeDownloads by io.github.landwarderer.futon.desktop.repo.DownloadManager.activeDownloads.collectAsState()
    
    LaunchedEffect(manga.url) {
        scope.launch {
            try {
                loading = true
                fullManga = repo.getMangaDetails(sourceEnum, manga)
                isFavorite = repo.isFavorite(manga.url, sourceEnum)
                val hList = repo.getHistoryList()
                val hItem = hList.find { it.first.url == manga.url }
                if (hItem != null) {
                    historyIndex = hItem.second.first
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loading = false
            }
        }
    }
    
    if (loading) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else {
        Row(Modifier.padding(24.dp).fillMaxSize()) {
            // Left Column: Cover & Actions
            Column(Modifier.width(240.dp)) {
                Card(
                    shape = RoundedCornerShape(12.dp),
                    elevation = CardDefaults.cardElevation(8.dp)
                ) {
                    AsyncImage(
                        url = fullManga.coverUrl, 
                        modifier = Modifier.fillMaxWidth().aspectRatio(0.7f)
                    )
                }
                Spacer(Modifier.height(24.dp))
                Button(
                    onClick = { 
                        selectedChapterIndex = historyIndex ?: 0
                        currentScreen = Screen.READER 
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(if (historyIndex != null) "Continuar Cap. ${historyIndex!! + 1}" else "Leer Ahora", fontWeight = FontWeight.Bold)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(
                    onClick = { 
                        scope.launch { 
                            repo.toggleFavorite(fullManga, sourceEnum, !isFavorite)
                            isFavorite = !isFavorite
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                    shape = RoundedCornerShape(8.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = if (isFavorite) Color.Red else MaterialTheme.colorScheme.primary)
                ) {
                    Text(if (isFavorite) "♥ Favorito" else "♡ Añadir a Biblioteca", fontWeight = FontWeight.Bold)
                }
            }
            
            Spacer(Modifier.width(32.dp))
            
            // Right Column: Info & Chapters
            androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxWidth()) {
                item {
                    Text(
                        text = fullManga.title, 
                        style = MaterialTheme.typography.headlineLarge,
                        fontWeight = FontWeight.ExtraBold,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Por ${fullManga.authors?.joinToString() ?: "Autor Desconocido"}", 
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(24.dp))
                    
                    Text("Sinopsis", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = fullManga.description ?: "Sin descripción disponible.", 
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                        lineHeight = MaterialTheme.typography.bodyLarge.lineHeight * 1.5f
                    )
                    
                    Spacer(Modifier.height(32.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                        Text("Capítulos", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                        Button(
                            onClick = {
                                scope.launch {
                                    repo.saveMangaToDb(fullManga, sourceEnum)
                                    fullManga.chapters?.forEach { chapter ->
                                        io.github.landwarderer.futon.desktop.repo.DownloadManager.downloadChapter(repo, sourceEnum, manga.url, chapter)
                                    }
                                }
                            }
                        ) {
                            Text("Descargar Todo")
                        }
                    }
                    Spacer(Modifier.height(16.dp))
                }
                
                val chaptersList = fullManga.chapters?.reversed() ?: emptyList()
                if (chaptersList.isEmpty()) {
                    item {
                        Text("No hay capítulos disponibles", color = Color.Gray)
                    }
                } else {
                    itemsIndexed(chaptersList) { index, chapter ->
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp).clickable { 
                                    selectedChapterIndex = index
                                    currentScreen = Screen.READER 
                                },
                                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(chapter.name, fontWeight = FontWeight.Medium, modifier = Modifier.weight(1f))
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                                        val key = "${manga.url}|${chapter.url}"
                                        val progress = activeDownloads[key]
                                        if (progress != null && !progress.isComplete && !progress.isError) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                                        } else if (io.github.landwarderer.futon.desktop.repo.DownloadManager.isChapterDownloaded(sourceEnum, manga.url, chapter.url)) {
                                            Text("✓", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        } else {
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        repo.saveMangaToDb(fullManga, sourceEnum)
                                                        io.github.landwarderer.futon.desktop.repo.DownloadManager.downloadChapter(repo, sourceEnum, manga.url, chapter)
                                                    }
                                                },
                                                modifier = Modifier.height(32.dp),
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                            ) {
                                                Text("Descargar", style = MaterialTheme.typography.bodySmall)
                                            }
                                        }
                                        
                                        Button(
                                            onClick = {
                                                selectedChapterIndex = index
                                                currentScreen = Screen.READER
                                            },
                                            modifier = Modifier.height(32.dp),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp)
                                        ) {
                                            Text("Ver", style = MaterialTheme.typography.bodySmall)
                                        }
                                    }
                                }
                            }
                        }
                }
            }
        }
    }
}

enum class ReadMode { WEBTOON, PAGINATED }

@OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun ReaderScreen(repo: MangaRepository, manga: Manga) {
    var loadingChapters by remember { mutableStateOf(true) }
    var chapters by remember { mutableStateOf<List<org.koitharu.kotatsu.parsers.model.MangaChapter>>(emptyList()) }
    var currentChapterIndex by remember { mutableStateOf(selectedChapterIndex) }
    var pageUrls by remember { mutableStateOf<List<String>>(emptyList()) }
    var loadingPages by remember { mutableStateOf(false) }
    var parserHeaders by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var readMode by remember { mutableStateOf(ReadMode.WEBTOON) }
    val scope = rememberCoroutineScope()
    val sourceEnum = MangaParserSource.values().firstOrNull { it.name.contains("MANGADEX", true) } ?: MangaParserSource.values().first()
    
    LaunchedEffect(manga.url) {
        scope.launch {
            try {
                parserHeaders = repo.getHeaders(sourceEnum)
                loadingChapters = true
                val detailedManga = repo.getMangaDetails(sourceEnum, manga)
                chapters = detailedManga.chapters?.reversed() ?: emptyList()
                if (chapters.isNotEmpty()) {
                    currentChapterIndex = selectedChapterIndex
                }
            } catch (e: Exception) {
                e.printStackTrace()
            } finally {
                loadingChapters = false
            }
        }
    }
    
    val listState = rememberLazyListState()
    
    LaunchedEffect(currentChapterIndex, chapters) {
        if (chapters.isNotEmpty() && currentChapterIndex in chapters.indices) {
            scope.launch {
                try {
                    loadingPages = true
                    pageUrls = emptyList() // clear current pages
                    
                    val chapter = chapters[currentChapterIndex]
                    if (io.github.landwarderer.futon.desktop.repo.DownloadManager.isChapterDownloaded(sourceEnum, manga.url, chapter.url)) {
                        val downloadedFiles = io.github.landwarderer.futon.desktop.repo.DownloadManager.getDownloadedPages(sourceEnum, manga.url, chapter.url)
                        pageUrls = downloadedFiles.map { "file://${it.absolutePath}" }
                    } else {
                        val chapterPages = repo.getPages(chapter, sourceEnum)
                        pageUrls = chapterPages.map { repo.getPageUrl(it, sourceEnum) }
                    }
                    
                    // Save History
                    repo.upsertHistory(manga, sourceEnum, currentChapterIndex, 0)
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    loadingPages = false
                    // Scroll to top after pages are loaded and LazyColumn is recomposed
                    listState.scrollToItem(0)
                }
            }
        }
    }
    
    if (loadingChapters) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
        }
    } else if (chapters.isEmpty()) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No se encontraron capítulos para este manga.", color = Color.Gray)
        }
    } else {
        Box(Modifier.fillMaxSize()) {
            val focusRequester = remember { androidx.compose.ui.focus.FocusRequester() }
            
            LaunchedEffect(Unit) { focusRequester.requestFocus() }
            if (loadingPages) {
                Box(Modifier.fillMaxSize().background(Color.Black), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else {
                if (readMode == ReadMode.WEBTOON) {
                    androidx.compose.foundation.lazy.LazyColumn(
                        state = listState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .focusRequester(focusRequester)
                            .focusable(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        items(pageUrls) { url ->
                            AsyncImage(
                                url = url,
                                headers = parserHeaders,
                                modifier = Modifier.widthIn(max = 900.dp).fillMaxWidth().aspectRatio(0.7f),
                                contentDescription = "Página del manga",
                                contentScale = ContentScale.FillWidth
                            )
                        }
                        item {
                            Spacer(Modifier.height(80.dp)) // padding for bottom bar
                        }
                    }
                } else {
                    val pagerState = rememberPagerState(pageCount = { pageUrls.size })
                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                            .focusRequester(focusRequester)
                            .focusable()
                    ) { page ->
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            AsyncImage(
                                url = pageUrls[page],
                                headers = parserHeaders,
                                modifier = Modifier.fillMaxSize(),
                                contentDescription = "Página del manga",
                                contentScale = ContentScale.Fit
                            )
                        }
                    }
                }
            }
            
            // Bottom navigation overlay
            Surface(
                modifier = Modifier.align(Alignment.BottomCenter).fillMaxWidth().padding(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(16.dp).fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Button(
                        onClick = { if (currentChapterIndex > 0) currentChapterIndex-- },
                        enabled = currentChapterIndex > 0,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                    ) {
                        Text("← Cap Anterior")
                    }
                    
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f).padding(horizontal = 16.dp)) {
                        Text(
                            text = chapters[currentChapterIndex].name,
                            color = MaterialTheme.colorScheme.onSurface,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1
                        )
                        Button(onClick = { readMode = if (readMode == ReadMode.WEBTOON) ReadMode.PAGINATED else ReadMode.WEBTOON }) {
                            Text(if (readMode == ReadMode.WEBTOON) "Modo: Webtoon" else "Modo: Paginado")
                        }
                    }
                    
                    Button(
                        onClick = { if (currentChapterIndex < chapters.size - 1) currentChapterIndex++ },
                        enabled = currentChapterIndex < chapters.size - 1,
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Sig Cap →")
                    }
                }
            }
        }
    }
}
