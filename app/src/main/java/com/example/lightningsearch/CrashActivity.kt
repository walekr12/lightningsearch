package com.example.lightningsearch

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class CrashActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val errorMessage = intent.getStringExtra("error_message") ?: "Unknown error"
        val errorClass = intent.getStringExtra("error_class") ?: "Exception"
        val stackTrace = intent.getStringExtra("stack_trace") ?: ""

        setContent {
            CrashScreen(
                errorClass = errorClass,
                errorMessage = errorMessage,
                stackTrace = stackTrace,
                onCopy = {
                    val fullError = """
                        |Error: $errorClass
                        |Message: $errorMessage
                        |
                        |Stack Trace:
                        |$stackTrace
                    """.trimMargin()

                    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                    val clip = ClipData.newPlainText("Crash Log", fullError)
                    clipboard.setPrimaryClip(clip)
                    Toast.makeText(this, "已复制到剪贴板", Toast.LENGTH_SHORT).show()
                },
                onRestart = {
                    val intent = packageManager.getLaunchIntentForPackage(packageName)
                    intent?.flags = android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK
                    startActivity(intent)
                    finish()
                }
            )
        }
    }
}

@Composable
fun CrashScreen(
    errorClass: String,
    errorMessage: String,
    stackTrace: String,
    onCopy: () -> Unit,
    onRestart: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1a1a2e))
            .padding(16.dp)
    ) {
        Text(
            text = "💥 应用崩溃了",
            fontSize = 24.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFe94560)
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = errorClass,
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFFffc107)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = errorMessage,
            fontSize = 14.sp,
            color = Color.White
        )

        Spacer(modifier = Modifier.height(16.dp))

        Text(
            text = "堆栈信息:",
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF00d9ff)
        )

        Spacer(modifier = Modifier.height(8.dp))

        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(Color(0xFF0f0f1a))
                .padding(8.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text(
                text = stackTrace,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
                color = Color(0xFFaaaaaa),
                lineHeight = 14.sp
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        Button(
            onClick = onCopy,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0f4c75))
        ) {
            Text("📋 复制错误信息", color = Color.White)
        }

        Spacer(modifier = Modifier.height(8.dp))

        Button(
            onClick = onRestart,
            modifier = Modifier.fillMaxWidth(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3282b8))
        ) {
            Text("🔄 重启应用", color = Color.White)
        }
    }
}
