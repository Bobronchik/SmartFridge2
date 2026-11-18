
package com.example.smartfridge

import android.os.Bundle
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
            SmartFridgeTheme {
                SmartFridgeRoot()
            }
        }
    }
}

@Composable
private fun SmartFridgeRoot() {

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

    SmartFridgeContent(fridgeViewModel)
}

@Composable
private fun SmartFridgeContent(
    viewModel: FridgeViewModel
) {

    val state by viewModel.uiState.collectAsState()
    val suggestions by viewModel.productSuggestions.collectAsState()

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var connected by remember {
        mutableStateOf(false)
    }

    var searching by remember {
        mutableStateOf(false)
    }

    var cameraAddress by remember {
        mutableStateOf<String?>(null)
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
            val address = cameraAddress
            if (address != null) {
                viewModel.scan(address)
            } else {
                viewModel.setMessage("Камера не подключена. Добавляй продукты вручную или настрой камеру позже.")
            }
        },

        onAdd = {
            showAddDialog = true
        }
        onDelete = viewModel::deleteProduct
    )

    if (showAddDialog) {
        AddProductDialog(
            suggestions = suggestions,

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
