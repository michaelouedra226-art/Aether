package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.example.ui.components.NowPlayingMiniBar
import com.example.ui.components.SpotifyBottomNavigation
import com.example.ui.navigation.Screen
import com.example.ui.screens.details.AlbumDetailScreen
import com.example.ui.screens.details.ArtistDetailScreen
import com.example.ui.screens.details.FolderDetailScreen
import com.example.ui.screens.home.HomeScreen
import com.example.ui.screens.library.LibraryScreen
import com.example.ui.screens.player.PlayerScreen
import com.example.ui.screens.queue.QueueScreen
import com.example.ui.screens.search.SearchScreen
import com.example.ui.screens.settings.SettingsScreen
import com.example.ui.theme.AetherTheme
import com.example.ui.theme.SpotifyBlack
import com.example.ui.viewmodel.MainViewModel
import java.net.URLDecoder

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            AetherTheme {
                MainContent(viewModel = viewModel, onExitApp = { finish() })
            }
        }
    }
}

@Composable
fun MainContent(
    viewModel: MainViewModel,
    onExitApp: () -> Unit
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route

    val playbackState by viewModel.playbackState.collectAsStateWithLifecycle()
    val hasTrack = playbackState.currentTrack != null
    val isPlayerScreen = currentRoute == Screen.Player.route
    val showMiniPlayer = hasTrack && !isPlayerScreen

    // Initial silent permission scan on launch if permission already granted
    LaunchedEffect(Unit) {
        val permission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_AUDIO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        if (ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED) {
            viewModel.rescanLibrary(context)
        }
    }

    // Double Back Press on Home Route -> Closes app and kills notification
    val isHomeRoute = currentRoute == Screen.Home.route || currentRoute == null
    BackHandler(enabled = isHomeRoute) {
        viewModel.handleHomeBackPress(context, onExitApp)
    }

    Scaffold(
        modifier = Modifier
            .fillMaxSize()
            .background(SpotifyBlack),
        containerColor = SpotifyBlack,
        bottomBar = {
            if (!isPlayerScreen) {
                SpotifyBottomNavigation(
                    currentRoute = currentRoute,
                    onNavigate = { route ->
                        navController.navigate(route) {
                            popUpTo(navController.graph.findStartDestination().id) {
                                saveState = true
                            }
                            launchSingleTop = true
                            restoreState = true
                        }
                    }
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(bottom = innerPadding.calculateBottomPadding())
        ) {
            // Main Navigation Host
            NavHost(
                navController = navController,
                startDestination = Screen.Home.route,
                modifier = Modifier.fillMaxSize()
            ) {
                composable(Screen.Home.route) {
                    HomeScreen(
                        viewModel = viewModel,
                        onNavigateToLibrary = {
                            navController.navigate(Screen.Library.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                        onNavigateToSearch = {
                            navController.navigate(Screen.Search.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        onNavigateToPlayer = { navController.navigate(Screen.Player.route) }
                    )
                }

                composable(Screen.Search.route) {
                    SearchScreen(
                        viewModel = viewModel,
                        onNavigateToLibrary = {
                            navController.navigate(Screen.Library.route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        }
                    )
                }

                composable(Screen.Library.route) {
                    LibraryScreen(
                        viewModel = viewModel,
                        onNavigateToArtist = { artistName ->
                            navController.navigate(Screen.ArtistDetail.createRoute(artistName))
                        },
                        onNavigateToAlbum = { albumName ->
                            navController.navigate(Screen.AlbumDetail.createRoute(albumName))
                        },
                        onNavigateToFolder = { folderName ->
                            navController.navigate(Screen.FolderDetail.createRoute(folderName))
                        }
                    )
                }

                composable(Screen.Player.route) {
                    PlayerScreen(
                        viewModel = viewModel,
                        onBackClick = { navController.popBackStack() },
                        onNavigateToQueue = { navController.navigate(Screen.Queue.route) }
                    )
                }

                composable(Screen.Queue.route) {
                    QueueScreen(
                        viewModel = viewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable(Screen.Settings.route) {
                    SettingsScreen(
                        viewModel = viewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.ArtistDetail.route,
                    arguments = listOf(navArgument("artistName") { type = NavType.StringType })
                ) { backStackEntry ->
                    val encodedName = backStackEntry.arguments?.getString("artistName") ?: ""
                    val artistName = try {
                        URLDecoder.decode(encodedName, "UTF-8")
                    } catch (e: Exception) {
                        encodedName
                    }
                    ArtistDetailScreen(
                        artistName = artistName,
                        viewModel = viewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.AlbumDetail.route,
                    arguments = listOf(navArgument("albumName") { type = NavType.StringType })
                ) { backStackEntry ->
                    val encodedName = backStackEntry.arguments?.getString("albumName") ?: ""
                    val albumName = try {
                        URLDecoder.decode(encodedName, "UTF-8")
                    } catch (e: Exception) {
                        encodedName
                    }
                    AlbumDetailScreen(
                        albumName = albumName,
                        viewModel = viewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }

                composable(
                    route = Screen.FolderDetail.route,
                    arguments = listOf(navArgument("folderName") { type = NavType.StringType })
                ) { backStackEntry ->
                    val encodedName = backStackEntry.arguments?.getString("folderName") ?: ""
                    val folderName = try {
                        URLDecoder.decode(encodedName, "UTF-8")
                    } catch (e: Exception) {
                        encodedName
                    }
                    FolderDetailScreen(
                        folderName = folderName,
                        viewModel = viewModel,
                        onBackClick = { navController.popBackStack() }
                    )
                }
            }

            // Bottom Floating Controls Stack: Mini Player docked on top of Spotify Bottom Navigation
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
            ) {
                // Docked Mini Player
                AnimatedVisibility(
                    visible = showMiniPlayer,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    NowPlayingMiniBar(
                        track = playbackState.currentTrack,
                        isPlaying = playbackState.isPlaying,
                        currentPositionMs = playbackState.currentPositionMs,
                        durationMs = playbackState.durationMs,
                        onBarClick = { navController.navigate(Screen.Player.route) },
                        onPlayPauseClick = { viewModel.togglePlayPause() },
                        onNextClick = { viewModel.next() },
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                    )
                }
            }
        }
    }
}
