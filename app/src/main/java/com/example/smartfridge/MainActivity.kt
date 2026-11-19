
package com.example.smartfridge

import android.os.Bundle
import android.content.SharedPreferences
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import com.example.smartfridge.notifications.ExpiryNotificationScheduler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.example.smartfridge.camera.CameraRepository
import com.example.smartfridge.camera.discoverCamera
import com.example.smartfridge.data.AppDatabase
import com.example.smartfridge.data.DatabaseInitializer
import com.example.smartfridge.BuildConfig
import com.example.smartfridge.ml.FridgeVisionPipeline
import com.example.smartfridge.ml.GeminiProductVerifier
import com.example.smartfridge.ml.YOLODetector
import com.example.smartfridge.repository.SmartFridgeRepository
import com.example.smartfridge.ui.*
import com.example.smartfridge.ui.theme.SmartFridgeTheme
import com.paddle.ocr.PaddleOCR
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        OpenCVLoader.initLocal()
        ExpiryNotificationScheduler.createNotificationChannel(applicationContext)
        ExpiryNotificationScheduler.schedule(applicationContext)

        val notificationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { /* Разрешение можно изменить позже в системных настройках. */ }

        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        setContent {
            val preferences = getSharedPreferences("smartfridge_settings", MODE_PRIVATE)
            var darkTheme by remember { mutableStateOf(preferences.getBoolean("dark_theme", true)) }
            SmartFridgeTheme(darkTheme = darkTheme) {
                SmartFridgeRoot(
                    preferences = preferences,
                    darkTheme = darkTheme,
                    onThemeChange = { enabled ->
                        darkTheme = enabled
                        preferences.edit().putBoolean("dark_theme", enabled).apply()
                    }
                )
            }
        }
    }
}

@Composable
private fun SmartFridgeRoot(
    preferences: SharedPreferences,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {

    val context = LocalContext.current

    var repository by remember {
        mutableStateOf<SmartFridgeRepository?>(null)
    }

    var loading by remember {
        mutableStateOf(true)
    }

    var error by remember {
        mutableStateOf(false)
    }

    LaunchedEffect(Unit) {
        try {
            val database = AppDatabase.getInstance(context)

            withContext(Dispatchers.IO) {
                DatabaseInitializer(database).initialize()
            }

            val detector = YOLODetector(
                context.applicationContext
            )

            val ocr = withContext(Dispatchers.Default) {
                PaddleOCR.create(context.applicationContext)
            }

            val pipeline = FridgeVisionPipeline(
                yoloDetector = detector,
                ocr = ocr,
                productDao = database.productDao(),
                geminiVerifier = BuildConfig.GEMINI_API_KEY.takeIf { it.isNotBlank() }
                    ?.let { GeminiProductVerifier(it) }
            )

            repository = SmartFridgeRepository(
                fridgeDao = database.fridgeDao(),
                productDao = database.productDao(),
                pipeline = pipeline,
                cameraRepository = CameraRepository()
            )

        } catch (e: Exception) {
            error = true
            e.printStackTrace()
        } finally {
            loading = false
        }
    }

    if (loading) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = androidx.compose.ui.Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    if (error) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp)
        ) {
            Column {
                Text(
                    text = "Не удалось подготовить SmartFridge",
                    style = MaterialTheme.typography.headlineSmall
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Перезапустите приложение и попробуйте снова."
                )
            }
        }
        return
    }

    val repo = repository ?: return

    val fridgeViewModel = remember(repo) {
        FridgeViewModel(repo)
    }

    SmartFridgeContent(fridgeViewModel, preferences, darkTheme, onThemeChange)
}

@Composable
private fun SmartFridgeContent(
    viewModel: FridgeViewModel,
    preferences: SharedPreferences,
    darkTheme: Boolean,
    onThemeChange: (Boolean) -> Unit
) {

    val state by viewModel.uiState.collectAsState()
    val suggestions by viewModel.productSuggestions.collectAsState()
    val catalog by viewModel.productCatalog.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val shoppingItems = remember {
        mutableStateListOf<String>().apply {
            addAll(preferences.getStringSet("smartfridge_shopping_list", emptySet()).orEmpty().sorted())
        }
    }

    DisposableEffect(viewModel) {
        onDispose { viewModel.dispose() }
    }

    var cameraAddress by remember {
        mutableStateOf(preferences.getString("camera_address", "") ?: "")
    }
    var cameraScanIntervalMinutes by remember {
        mutableStateOf(preferences.getInt("camera_scan_interval_minutes", 1).coerceIn(1, 1440))
    }

    var showAddDialog by remember {
        mutableStateOf(false)
    }

    FridgeScreen(
        state = state,

        onSearch = {
            viewModel.setSearch(it)
        },

        onCategory = {
            viewModel.setCategory(it)
        },

        onScan = {
            val address = cameraAddress.trim()
            if (address.isNotBlank()) {
                viewModel.scan(address, cameraScanIntervalMinutes)
            } else {
                viewModel.setMessage("Укажи адрес камеры в настройках.")
            }
        },
        onTakePhoto = {
            val address = cameraAddress.trim()
            if (address.isNotBlank()) {
                viewModel.capturePhotoForGemini(address, BuildConfig.GEMINI_API_KEY)
            } else {
                viewModel.setMessage("Сначала укажи адрес камеры в Настройки.")
            }
        },

        onAdd = {
            showAddDialog = true
        },
        onDelete = viewModel::deleteProduct,
        onUpdateProduct = viewModel::updateProduct,
        darkTheme = darkTheme,
        onThemeChange = onThemeChange,
        cameraAddress = cameraAddress,
        onCameraAddressChange = { cameraAddress = it },
        onSaveSettings = {
            preferences.edit()
                .putString("camera_address", cameraAddress.trim())
                .putInt("camera_scan_interval_minutes", cameraScanIntervalMinutes)
                .apply()
            viewModel.setMessage("Настройки камеры сохранены")
        },
        onFindCamera = {
            if (!state.cameraSearching) scope.launch {
                viewModel.setCameraSearching(true)
                try {
                    viewModel.setMessage("Ищем камеру в локальной сети…")
                    val found = withContext(Dispatchers.IO) { discoverCamera(context) }
                    if (found != null) {
                        cameraAddress = found
                        preferences.edit().putString("camera_address", found).apply()
                        viewModel.setMessage("Камера найдена: $found")
                    } else {
                        viewModel.setMessage("Камера не найдена. Проверь, что телефон и камера в одной сети.")
                    }
                } finally {
                    viewModel.setCameraSearching(false)
                }
            }
        },
        onConfirmScan = viewModel::confirmScanReview,
        onDismissScan = viewModel::dismissScanReview,
        onUpdatePendingProduct = viewModel::updatePendingProduct,
        onRemovePendingProduct = viewModel::removePendingProduct,
        cameraScanIntervalMinutes = cameraScanIntervalMinutes,
        onCameraScanIntervalChange = {
            cameraScanIntervalMinutes = it.coerceIn(1, 1440)
            preferences.edit().putInt("camera_scan_interval_minutes", cameraScanIntervalMinutes).apply()
        },
        onSaveCameraInterval = {
            preferences.edit().putInt("camera_scan_interval_minutes", cameraScanIntervalMinutes).apply()
            viewModel.setMessage("Интервал между снимками сохранён")
        },
        shoppingItems = shoppingItems,
        onAddShoppingItem = { name ->
            if (shoppingItems.none { it.equals(name, ignoreCase = true) }) shoppingItems.add(name)
            preferences.edit().putStringSet("smartfridge_shopping_list", shoppingItems.toSet()).apply()
        },
        onRemoveShoppingItem = { name ->
            shoppingItems.remove(name)
            preferences.edit().putStringSet("smartfridge_shopping_list", shoppingItems.toSet()).apply()
        }
    )

    if (showAddDialog) {
        AddProductDialog(
            suggestions = catalog,

            onSearch = {
                viewModel.searchCatalog(it)
            },

            onDismiss = {
                showAddDialog = false
            },

            onAdd = {
                    name,
                    category,
                    icon,
                    quantity,
                    expiresAt,
                    exact ->

                scope.launch {
                    val saved = viewModel.addProduct(
                        name,
                        category,
                        icon,
                        quantity,
                        expiresAt,
                        exact
                    )
                    if (saved) showAddDialog = false
                }
            }
        )
    }
}
