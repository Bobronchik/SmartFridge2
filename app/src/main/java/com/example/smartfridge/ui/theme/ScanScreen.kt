package com.example.smartfridge.ui

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun CameraSetupScreen(
    connected: Boolean,
    searching: Boolean,
    onSearch: () -> Unit,
    onDone: () -> Unit
) {

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .padding(24.dp),

        horizontalAlignment =
            Alignment.CenterHorizontally,

        verticalArrangement =
            Arrangement.Center
    ) {

        Text(
            text = "📷",
            style =
                MaterialTheme
                    .typography
                    .displayLarge
        )

        Spacer(
            Modifier.height(20.dp)
        )

        Text(
            text =
                if (connected)
                    "Камера готова к работе"
                else if (searching)
                    "Ищем камеру…"
                else
                    "Подключите камеру",

            style =
                MaterialTheme
                    .typography
                    .headlineSmall
        )

        Spacer(
            Modifier.height(12.dp)
        )

        Text(
            text =
                if (connected)
                    "Теперь приложение может проверять содержимое холодильника."
                else
                    "Убедитесь, что камера подключена к вашей Wi-Fi сети 2.4 ГГц.",

            style =
                MaterialTheme
                    .typography
                    .bodyLarge
        )

        Spacer(
            Modifier.height(28.dp)
        )

        if (!connected) {

            Button(
                onClick = onSearch,
                enabled = !searching
            ) {

                Text(
                    if (searching)
                        "Поиск…"
                    else
                        "Найти камеру"
                )
            }

        } else {

            Button(
                onClick = onDone
            ) {

                Text(
                    "Продолжить"
                )
            }
        }
    }
}