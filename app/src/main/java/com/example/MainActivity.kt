package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.DownloadForOffline
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.LibraryMusic
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryMusic
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.toRoute
import com.example.ui.components.MiniPlayerBar
import com.example.ui.navigation.HistoryRoute
import com.example.ui.navigation.LibraryRoute
import com.example.ui.navigation.OfflineRoute
import com.example.ui.navigation.PlaylistDetailRoute
import com.example.ui.screens.DpPadding
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.LibraryScreen
import com.example.ui.screens.NowPlayingSheet
import com.example.ui.screens.OfflineScreen
import com.example.ui.screens.PlaylistDetailScreen
import com.example.ui.theme.PodDeckTheme
import com.example.ui.viewmodel.PodDeckViewModel

private data class TopLevelTab(
    val label: String,
    val route: Any,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val app = application as PodDeckApp

        setContent {
            PodDeckTheme {
                val viewModel: PodDeckViewModel = viewModel(
                    factory = PodDeckViewModel.Factory(app.repository, app.playerController)
                )
                PodDeckRoot(viewModel = viewModel)
            }
        }
    }
}

@Composable
private fun PodDeckRoot(viewModel: PodDeckViewModel) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val snackbarHostState = remember { SnackbarHostState() }

    val playlists by viewModel.playlists.collectAsStateWithLifecycle()
    val downloads by viewModel.downloads.collectAsStateWithLifecycle()
    val recentlyPlayed by viewModel.recentlyPlayed.collectAsStateWithLifecycle()
    val lastPlayed by viewModel.lastPlayedEpisode.collectAsStateWithLifecycle()
    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val isImporting by viewModel.isImporting.collectAsStateWithLifecycle()
    val isSyncing by viewModel.isSyncingPlaylist.collectAsStateWithLifecycle()
    val isAddingVideo by viewModel.isAddingVideo.collectAsStateWithLifecycle()

    var showNowPlaying by remember { mutableStateOf(false) }

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { _ -> }

    val requestNotificationPermissionIfNeeded = remember(context) {
        {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                val granted = ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.POST_NOTIFICATIONS
                ) == PackageManager.PERMISSION_GRANTED
                if (!granted) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
        }
    }

    LaunchedEffect(Unit) {
        viewModel.messages.collect { message ->
            snackbarHostState.showSnackbar(message)
        }
    }

    LaunchedEffect(playbackState.errorMessage) {
        val err = playbackState.errorMessage
        if (!err.isNullOrBlank()) {
            snackbarHostState.showSnackbar(err)
            viewModel.playerController.clearError()
        }
    }

    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    val isDetailScreen = currentDestination?.hasRoute<PlaylistDetailRoute>() == true

    val topLevelTabs = remember {
        listOf(
            TopLevelTab(
                label = "Library",
                route = LibraryRoute,
                selectedIcon = Icons.Rounded.LibraryMusic,
                unselectedIcon = Icons.Outlined.LibraryMusic,
                testTag = "nav_library"
            ),
            TopLevelTab(
                label = "Offline",
                route = OfflineRoute,
                selectedIcon = Icons.Rounded.DownloadForOffline,
                unselectedIcon = Icons.Outlined.DownloadForOffline,
                testTag = "nav_offline"
            ),
            TopLevelTab(
                label = "History",
                route = HistoryRoute,
                selectedIcon = Icons.Rounded.History,
                unselectedIcon = Icons.Outlined.History,
                testTag = "nav_history"
            )
        )
    }

    val hasActivePlayer = playbackState.currentEpisode != null
    val listBottomPadding = if (hasActivePlayer) 96.dp else 20.dp

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavigationRail = maxWidth >= 640.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            bottomBar = {
                Column(modifier = Modifier.fillMaxWidth()) {
                    AnimatedVisibility(visible = hasActivePlayer) {
                        MiniPlayerBar(
                            playbackState = playbackState,
                            onExpand = { showNowPlaying = true },
                            onTogglePlayPause = {
                                requestNotificationPermissionIfNeeded()
                                viewModel.playerController.togglePlayPause()
                            },
                            onSkipForward = { viewModel.playerController.skipForward() },
                            modifier = if (useNavigationRail || isDetailScreen) {
                                Modifier.navigationBarsPadding()
                            } else {
                                Modifier
                            }
                        )
                    }
                    if (!useNavigationRail && !isDetailScreen) {
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surface
                        ) {
                            topLevelTabs.forEach { tab ->
                                val selected = when (tab.route) {
                                    is LibraryRoute -> currentDestination?.hasRoute<LibraryRoute>() == true
                                    is OfflineRoute -> currentDestination?.hasRoute<OfflineRoute>() == true
                                    is HistoryRoute -> currentDestination?.hasRoute<HistoryRoute>() == true
                                    else -> false
                                }
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        navController.navigate(tab.route) {
                                            popUpTo(navController.graph.findStartDestination().id) {
                                                saveState = true
                                            }
                                            launchSingleTop = true
                                            restoreState = true
                                        }
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = { Text(tab.label) },
                                    modifier = Modifier.testTag(tab.testTag)
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(bottom = innerPadding.calculateBottomPadding())
            ) {
                if (useNavigationRail && !isDetailScreen) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surface,
                        modifier = Modifier
                            .fillMaxHeight()
                            .windowInsetsPadding(WindowInsets.statusBars)
                    ) {
                        topLevelTabs.forEach { tab ->
                            val selected = when (tab.route) {
                                is LibraryRoute -> currentDestination?.hasRoute<LibraryRoute>() == true
                                is OfflineRoute -> currentDestination?.hasRoute<OfflineRoute>() == true
                                is HistoryRoute -> currentDestination?.hasRoute<HistoryRoute>() == true
                                else -> false
                            }
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    navController.navigate(tab.route) {
                                        popUpTo(navController.graph.findStartDestination().id) {
                                            saveState = true
                                        }
                                        launchSingleTop = true
                                        restoreState = true
                                    }
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = tab.label
                                    )
                                },
                                label = { Text(tab.label) },
                                modifier = Modifier.testTag(tab.testTag)
                            )
                        }
                    }
                }

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = LibraryRoute,
                        modifier = Modifier.fillMaxSize()
                    ) {
                        composable<LibraryRoute> {
                            LibraryScreen(
                                playlists = playlists,
                                lastPlayed = lastPlayed,
                                isImporting = isImporting,
                                bottomContentPadding = DpPadding(bottom = listBottomPadding),
                                onPlaylistClick = { playlistId ->
                                    navController.navigate(PlaylistDetailRoute(playlistId))
                                },
                                onImportPlaylist = { url, onSuccess ->
                                    viewModel.importYouTubePlaylist(url, onSuccess)
                                },
                                onCreateCustomPlaylist = { title, desc, onSuccess ->
                                    viewModel.createCustomPlaylist(title, desc, onSuccess)
                                },
                                onSyncPlaylist = viewModel::syncPlaylist,
                                onDownloadAll = { item ->
                                    requestNotificationPermissionIfNeeded()
                                    viewModel.downloadAllInPlaylist(item.episodes)
                                },
                                onEditPlaylist = viewModel::updatePlaylistDetails,
                                onDeletePlaylist = { id -> viewModel.deletePlaylist(id) },
                                onResumeLastPlayed = {
                                    requestNotificationPermissionIfNeeded()
                                    viewModel.resumeLastPlayed()
                                },
                                onDismissLastPlayed = viewModel::removeFromHistory,
                                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                            )
                        }

                        composable<OfflineRoute> {
                            OfflineScreen(
                                downloads = downloads,
                                bottomContentPadding = listBottomPadding,
                                onPlayEpisode = { episode, playlist ->
                                    requestNotificationPermissionIfNeeded()
                                    viewModel.playEpisode(episode, playlist, listOf(episode))
                                },
                                onPauseDownload = viewModel::pauseDownload,
                                onPauseAllDownloads = viewModel::pauseAllActiveDownloads,
                                onResumePausedDownloads = {
                                    requestNotificationPermissionIfNeeded()
                                    viewModel.resumePausedDownloads()
                                },
                                onStopAllDownloads = viewModel::stopAllActiveDownloads,
                                onCancelDownload = viewModel::cancelDownload,
                                onRetryDownload = { ep ->
                                    requestNotificationPermissionIfNeeded()
                                    viewModel.downloadEpisode(ep)
                                },
                                onDismissFailedDownload = viewModel::removeDownloadedFile,
                                onExportOfflineFile = { ep ->
                                    viewModel.exportEpisodeToDownloads(ep) { shareIntent ->
                                        context.startActivity(shareIntent)
                                    }
                                },
                                onDeleteOfflineFile = viewModel::removeDownloadedFile,
                                onClearAllDownloads = viewModel::clearAllDownloads,
                                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                            )
                        }

                        composable<HistoryRoute> {
                            HistoryScreen(
                                playlists = playlists,
                                recentlyPlayed = recentlyPlayed,
                                bottomContentPadding = listBottomPadding,
                                onResumeEpisode = { episode, playlist ->
                                    requestNotificationPermissionIfNeeded()
                                    viewModel.playEpisode(episode, playlist, listOf(episode))
                                },
                                onOpenPlaylist = { playlistId ->
                                    navController.navigate(PlaylistDetailRoute(playlistId))
                                },
                                onRemoveHistoryItem = viewModel::removeFromHistory,
                                onClearAllHistory = viewModel::clearAllHistory,
                                modifier = Modifier.windowInsetsPadding(WindowInsets.statusBars)
                            )
                        }

                        composable<PlaylistDetailRoute> { backStackEntry ->
                            val route = backStackEntry.toRoute<PlaylistDetailRoute>()
                            val detailPlaylist by viewModel
                                .observePlaylist(route.playlistId)
                                .collectAsStateWithLifecycle(initialValue = null)

                            PlaylistDetailScreen(
                                playlistWithEpisodes = detailPlaylist,
                                playbackState = playbackState,
                                isSyncing = isSyncing,
                                isAddingVideo = isAddingVideo,
                                bottomContentPadding = listBottomPadding,
                                onBack = { navController.popBackStack() },
                                onSyncPlaylist = viewModel::syncPlaylist,
                                onAddVideo = viewModel::addVideoToPlaylist,
                                onEditPlaylist = viewModel::updatePlaylistDetails,
                                onDeletePlaylist = { id ->
                                    viewModel.deletePlaylist(id) {
                                        navController.popBackStack()
                                    }
                                },
                                onPlayEpisode = { episode, queue, restart ->
                                    requestNotificationPermissionIfNeeded()
                                    viewModel.playEpisode(
                                        episode = episode,
                                        playlist = detailPlaylist?.playlist,
                                        queue = queue,
                                        restartFromBeginning = restart
                                    )
                                },
                                onEnqueuePlayNext = viewModel::enqueuePlayNext,
                                onAddToQueue = viewModel::addToQueue,
                                onTogglePlayPause = {
                                    requestNotificationPermissionIfNeeded()
                                    viewModel.playerController.togglePlayPause()
                                },
                                onDownloadEpisode = { ep ->
                                    requestNotificationPermissionIfNeeded()
                                    viewModel.downloadEpisode(ep)
                                },
                                onDownloadAll = { eps ->
                                    requestNotificationPermissionIfNeeded()
                                    viewModel.downloadAllInPlaylist(eps)
                                },
                                onDownloadSelected = { eps ->
                                    requestNotificationPermissionIfNeeded()
                                    viewModel.downloadSelectedEpisodes(eps)
                                },
                                onDownloadNextUnplayed = { eps ->
                                    requestNotificationPermissionIfNeeded()
                                    viewModel.downloadNextUnplayed(eps)
                                },
                                onCleanCompletedOffline = viewModel::cleanCompletedOfflineAudio,
                                onPauseDownload = viewModel::pauseDownload,
                                onPausePlaylistDownloads = viewModel::pauseDownloadsForEpisodes,
                                onResumePlaylistDownloads = { eps ->
                                    requestNotificationPermissionIfNeeded()
                                    viewModel.resumePausedDownloads(eps)
                                },
                                onStopPlaylistDownloads = viewModel::stopDownloadsForEpisodes,
                                onCancelDownload = viewModel::cancelDownload,
                                onRemoveDownloadedFile = viewModel::removeDownloadedFile,
                                onExportOfflineFile = { ep ->
                                    viewModel.exportEpisodeToDownloads(ep) { shareIntent ->
                                        context.startActivity(shareIntent)
                                    }
                                },
                                onToggleEpisodeCompleted = viewModel::toggleEpisodeCompleted,
                                onResetEpisodeProgress = viewModel::resetEpisodeProgress,
                                onRenameEpisode = viewModel::renameEpisode,
                                onDeleteEpisode = viewModel::deleteEpisode
                            )
                        }
                    }
                }
            }
        }
    }

    val activeEpisodeId = playbackState.currentEpisode?.id
    if (showNowPlaying && activeEpisodeId != null) {
        val bookmarks by viewModel
            .observeBookmarksForEpisode(activeEpisodeId)
            .collectAsStateWithLifecycle(initialValue = emptyList())

        NowPlayingSheet(
            playbackState = playbackState,
            bookmarks = bookmarks,
            onDismiss = { showNowPlaying = false },
            onTogglePlayPause = {
                requestNotificationPermissionIfNeeded()
                viewModel.playerController.togglePlayPause()
            },
            onSeekTo = viewModel.playerController::seekTo,
            onSkipBackward = { viewModel.playerController.skipBackward() },
            onSkipForward = { viewModel.playerController.skipForward() },
            onSkipPrevious = viewModel.playerController::playPreviousInQueue,
            onSkipNext = viewModel.playerController::playNextInQueue,
            onSpeedChange = viewModel.playerController::setPlaybackSpeed,
            onToggleVolumeBoost = viewModel.playerController::toggleVolumeBoost,
            onSleepTimerMinutes = viewModel.playerController::setSleepTimerMinutes,
            onSleepAtEndOfEpisode = viewModel.playerController::toggleSleepAtEndOfEpisode,
            onDownloadEpisode = { ep ->
                requestNotificationPermissionIfNeeded()
                viewModel.downloadEpisode(ep)
            },
            onPlayQueueItem = { qEp ->
                requestNotificationPermissionIfNeeded()
                viewModel.playEpisode(
                    episode = qEp,
                    playlist = playbackState.currentPlaylist,
                    queue = playbackState.queue
                )
            },
            onAddBookmark = viewModel::addBookmark,
            onDeleteBookmark = viewModel::deleteBookmark
        )
    }
}
