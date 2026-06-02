package com.ebookreader

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.*
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.ebookreader.library.LibraryScreen
import com.ebookreader.reader.ReaderScreen
import com.ebookreader.ui.theme.EbookReaderTheme
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EbookReaderTheme {
                val navController = rememberNavController()

                NavHost(
                    navController = navController,
                    startDestination = "library",
                ) {
                    // 书架
                    composable("library") {
                        LibraryScreen(
                            onBookClick = { bookId ->
                                navController.navigate("reader/$bookId")
                            },
                        )
                    }

                    composable(
                        route = "reader/{bookId}",
                        arguments = listOf(
                            navArgument("bookId") { type = NavType.LongType }
                        ),
                    ) {
                        ReaderScreen(
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

