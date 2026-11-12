package org.ilm.etest

import android.app.Activity
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Navy = Color(0xFF102B55)
private val Gold = Color(0xFFD7AD58)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        setContent {
            var sharing by remember { mutableStateOf(false) }
            var status by remember { mutableStateOf("Screen sharing is off") }
            val captureLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.StartActivityForResult()
            ) { result ->
                if (result.resultCode == Activity.RESULT_OK && result.data != null) {
                    val service = Intent(this, ScreenCaptureService::class.java).apply {
                        action = ScreenCaptureService.ACTION_START
                        putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, result.resultCode)
                        putExtra(ScreenCaptureService.EXTRA_DATA, result.data)
                    }
                    ContextCompat.startForegroundService(this, service)
                    sharing = true
                    status = "Screen capture permission granted. No screen is being sent to ILM yet."
                } else {
                    sharing = false
                    status = "Permission was not granted. You can still explore the app."
                }
            }

            MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Gold)) {
                Column(
                    Modifier.fillMaxSize().background(Color(0xFFF4F7FB)).padding(22.dp),
                    verticalArrangement = Arrangement.spacedBy(18.dp)
                ) {
                    Text("ILM", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("ETest", color = Navy, fontSize = 34.sp, fontWeight = FontWeight.ExtraBold)
                    Text("Native Android examination app", color = Color(0xFF5D6B80))
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Student Portal", color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                            Text("Your existing ILM ETest account will be used after Supabase sign-in is connected.")
                            OutlinedTextField(value = "", onValueChange = {}, enabled = false, label = { Text("Email login (backend connection next)") }, modifier = Modifier.fillMaxWidth())
                            OutlinedTextField(value = "", onValueChange = {}, enabled = false, label = { Text("Password") }, modifier = Modifier.fillMaxWidth())
                            Text("Login and test loading are not connected in this first Android build.", color = Color(0xFF8A5A00), fontSize = 12.sp)
                        }
                    }
                    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            Text("Screen-sharing permission", color = Navy, fontSize = 19.sp, fontWeight = FontWeight.Bold)
                            Text("When you choose Start permission, Android displays its own system consent screen. You can cancel or stop sharing at any time.")
                            Text(status, color = if (sharing) Color(0xFF176B45) else Color(0xFF5D6B80), fontSize = 13.sp)
                            Button(onClick = { if (!sharing) captureLauncher.launch(manager.createScreenCaptureIntent()) else {
                                startService(Intent(this@MainActivity, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
                                sharing = false
                                status = "Screen sharing permission session stopped."
                            } }, modifier = Modifier.fillMaxWidth()) {
                                Text(if (sharing) "Stop screen sharing session" else "Request Android screen-sharing permission")
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    Text("ILM The Contest Center • Version 1.0.0 • Demo build", color = Color(0xFF718096), fontSize = 11.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
                }
            }
        }
    }
}
