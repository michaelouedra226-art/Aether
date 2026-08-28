package com.example.ui.navigation

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object Library : Screen("library")
    data object Player : Screen("player")
    data object Queue : Screen("queue")
    data object Settings : Screen("settings")
    data object Search : Screen("search")
    data object ArtistDetail : Screen("artist_detail/{artistName}") {
        fun createRoute(artistName: String): String = "artist_detail/${java.net.URLEncoder.encode(artistName, "UTF-8")}"
    }
    data object AlbumDetail : Screen("album_detail/{albumName}") {
        fun createRoute(albumName: String): String = "album_detail/${java.net.URLEncoder.encode(albumName, "UTF-8")}"
    }
    data object FolderDetail : Screen("folder_detail/{folderName}") {
        fun createRoute(folderName: String): String = "folder_detail/${java.net.URLEncoder.encode(folderName, "UTF-8")}"
    }
}
