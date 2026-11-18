
package com.example.smartfridge

import android.os.Bundle
import android.content.SharedPreferences
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
import com.example.smartfridge.ml.FridgeVisionPipeline
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

        setContent {
            val preferences = getSharedPreferences("smartfridge_settings", MODE_PRIVATE)
            var darkTheme by remember { mutableStateOf(preferences.getBoolean("dark_theme", false)) }
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
                productDao = database.productDao()
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

    var cameraAddress by remember {
        mutableStateOf(preferences.getString("camera_address", "") ?: "")
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
                viewModel.scan(address)
            } else {
                viewModel.setMessage("Укажи адрес камеры в настройках.")
            }
        },

        onAdd = {
            showAddDialog = true
        },
        onDelete = viewModel::deleteProduct,
        darkTheme = darkTheme,
        onThemeChange = onThemeChange,
        cameraAddress = cameraAddress,
        onCameraAddressChange = { cameraAddress = it },
        onSaveSettings = {
            preferences.edit().putString("camera_address", cameraAddress.trim()).apply()
            viewModel.setMessage("Настройки камеры сохранены")
        },
        onFindCamera = {
            scope.launch {
                viewModel.setMessage("Ищем камеру в локальной сети…")
                val found = withContext(Dispatchers.IO) { discoverCamera(context) }
                if (found != null) {
                    cameraAddress = found
                    preferences.edit().putString("camera_address", found).apply()
                    viewModel.setMessage("Камера найдена: $found")
                } else {
                    viewModel.setMessage("Камера не найдена. Проверь, что телефон и камера в одной сети.")
                }
            }
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

                viewModel.addProduct(
                    name,
                    category,
                    icon,
                    quantity,
                    expiresAt,
                    exact
                )

                showAddDialog = false
            }
        )
    }
}
