package dev.alexis.mediagallery

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import dev.alexis.mediagallery.data.SavedProfileManager
import dev.alexis.mediagallery.ui.gallery.GalleryScreen
import dev.alexis.mediagallery.ui.gallery.GalleryViewModel
import dev.alexis.mediagallery.ui.login.LoginScreen
import dev.alexis.mediagallery.ui.login.LoginViewModel
import dev.alexis.mediagallery.ui.theme.MediaGalleryTheme
import dev.alexis.mediagallery.ui.upload.UploadScreen
import dev.alexis.mediagallery.ui.upload.UploadViewModel
import dev.alexis.mediagallery.ui.viewer.MediaViewerScreen
import dev.alexis.mediagallery.ui.viewer.MediaViewerViewModel
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private lateinit var insetsController: WindowInsetsControllerCompat

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)

        insetsController = WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE

            hide(WindowInsetsCompat.Type.systemBars())
        }

        setContent {
            MediaGalleryTheme(darkTheme = true) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MediaGalleryApp()
                }
            }
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)

        if (hasFocus) {
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}

@Composable
fun MediaGalleryApp() {
    val application = LocalContext.current.applicationContext as MediaGalleryApplication
    val container = application.container
    val navController: NavHostController = rememberNavController()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    val startDestination =
        if (container.tokenManager.isLoggedIn()) "gallery" else "login"

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectTapGestures {
                    val activity = context as? ComponentActivity
                    activity?.window?.let { window ->
                        WindowCompat.getInsetsController(window, window.decorView)
                            .hide(WindowInsetsCompat.Type.systemBars())
                    }
                }
            }
    ) {
        NavHost(
            navController = navController,
            startDestination = startDestination
        ) {
            composable("login") {
                val viewModel: LoginViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            LoginViewModel(
                                container.apiService,
                                container.tokenManager,
                                SavedProfileManager(context.applicationContext)
                            )
                        }
                    }
                )

                LoginScreen(
                    viewModel = viewModel,
                    onLoginSuccess = {
                        navController.navigate("gallery") {
                            popUpTo("login") { inclusive = true }
                        }
                    }
                )
            }

            composable("gallery") {
                val viewModel: GalleryViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            GalleryViewModel(
                                container.apiService,
                                container.tokenManager,
                                application
                            )
                        }
                    }
                )

                GalleryScreen(
                    viewModel = viewModel,
                    onMediaClick = { media ->
                        if (media.type == "other" || media.type == "others") {
                            return@GalleryScreen
                        }
                        navController.navigate("viewer/${media.id}")
                    },
                    onUploadClick = {
                        navController.navigate("upload")
                    },
                    onLogoutClick = {
                        scope.launch {
                            container.tokenManager.clearToken()
                        }

                        navController.navigate("login") {
                            popUpTo("gallery") { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                )
            }

            composable("upload") {
                val viewModel: UploadViewModel = viewModel(
                    factory = viewModelFactory {
                        initializer {
                            UploadViewModel(
                                container.apiService,
                                context.contentResolver
                            )
                        }
                    }
                )

                UploadScreen(
                    viewModel = viewModel,
                    onUploadFinished = {
                        navController.popBackStack()
                    },
                    onBackClick = {
                        navController.popBackStack()
                    }
                )
            }

            composable(
                route = "viewer/{mediaId}",
                arguments = listOf(
                    navArgument("mediaId") {
                        type = NavType.IntType
                    }
                )
            ) { backStackEntry ->

                val mediaId = backStackEntry.arguments?.getInt("mediaId")

                if (mediaId != null) {
                    val viewModel: MediaViewerViewModel = viewModel(
                        factory = viewModelFactory {
                            initializer {
                                MediaViewerViewModel(
                                    container.apiService,
                                    container.tokenManager,
                                    context.cacheDir,
                                    mediaId
                                )
                            }
                        }
                    )

                    MediaViewerScreen(
                        viewModel = viewModel,
                        onBackClick = {
                            navController.popBackStack()
                        }
                    )
                }
            }
        }
    }
}