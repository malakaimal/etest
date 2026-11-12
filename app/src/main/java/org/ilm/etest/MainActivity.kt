package org.ilm.etest

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.lifecycle.compose.LocalLifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

private const val PROJECT_URL = "https://qnqjwrmpralmmdvgjmno.supabase.co"
private const val PUBLIC_KEY = "sb_publishable_1ZEKiTnVrCJNrHphbfCDJA_WI5UANKd"
private val Navy = Color(0xFF142D52)
private val Blue = Color(0xFF2864D7)
private val Gold = Color(0xFFD9AD55)
private val Ink = Color(0xFF1C2B43)
private val Muted = Color(0xFF718096)
private val Canvas = Color(0xFFF4F7FB)
private val Line = Color(0xFFE7ECF3)

data class StudentProfile(val id: String, val name: String, val registration: String, val status: String)
data class ETest(val id: String, val title: String, val code: String, val duration: Int, val marks: String, val cameraRequired: Boolean)

private object ETestApi {
    private fun request(path: String, method: String = "GET", token: String? = null, body: JSONObject? = null): String {
        val connection = (URL(PROJECT_URL + path).openConnection() as HttpURLConnection)
        connection.requestMethod = method
        connection.connectTimeout = 15000
        connection.readTimeout = 15000
        connection.setRequestProperty("apikey", PUBLIC_KEY)
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Accept", "application/json")
        connection.setRequestProperty("Authorization", "Bearer ${token ?: PUBLIC_KEY}")
        if (body != null) {
            connection.doOutput = true
            connection.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
        }
        val code = connection.responseCode
        val stream = if (code in 200..299) connection.inputStream else connection.errorStream
        val response = stream?.bufferedReader()?.use { it.readText() } ?: ""
        connection.disconnect()
        if (code !in 200..299) {
            val message = try { JSONObject(response).optString("msg", JSONObject(response).optString("message", response)) } catch (_: Exception) { response }
            throw IllegalStateException(message.ifBlank { "Request failed ($code)" })
        }
        return response
    }

    suspend fun signIn(context: Context, email: String, password: String): StudentProfile = withContext(Dispatchers.IO) {
        val auth = JSONObject().put("email", email).put("password", password)
        val session = JSONObject(request("/auth/v1/token?grant_type=password", "POST", body = auth))
        val token = session.getString("access_token")
        val userId = session.getJSONObject("user").getString("id")
        val prefs = context.getSharedPreferences("ilm_etest_session", Context.MODE_PRIVATE)
        prefs.edit().putString("access_token", token).putString("refresh_token", session.optString("refresh_token")).apply()
        val rows = JSONArray(request("/rest/v1/profiles?id=eq.$userId&select=id,full_name,registration_id,status", token = token))
        if (rows.length() == 0) throw IllegalStateException("Student profile not found. Please contact ILM.")
        val p = rows.getJSONObject(0)
        StudentProfile(p.getString("id"), p.optString("full_name", email), p.optString("registration_id", ""), p.optString("status", "pending"))
    }

    suspend fun loadProfile(context: Context): StudentProfile? = withContext(Dispatchers.IO) {
        val prefs = context.getSharedPreferences("ilm_etest_session", Context.MODE_PRIVATE)
        val token = prefs.getString("access_token", null) ?: return@withContext null
        try {
            val user = JSONObject(request("/auth/v1/user", token = token))
            val id = user.getString("id")
            val rows = JSONArray(request("/rest/v1/profiles?id=eq.$id&select=id,full_name,registration_id,status", token = token))
            if (rows.length() == 0) null else rows.getJSONObject(0).let {
                StudentProfile(it.getString("id"), it.optString("full_name", user.optString("email")), it.optString("registration_id", ""), it.optString("status", "pending"))
            }
        } catch (_: Exception) { null }
    }

    suspend fun loadTests(context: Context): List<ETest> = withContext(Dispatchers.IO) {
        val token = context.getSharedPreferences("ilm_etest_session", Context.MODE_PRIVATE).getString("access_token", null)
            ?: throw IllegalStateException("Please sign in again.")
        val rows = JSONArray(request("/rest/v1/tests?status=eq.published&select=id,title,test_code,duration_minutes,total_marks,camera_required&order=created_at.desc", token = token))
        (0 until rows.length()).map { i ->
            val t = rows.getJSONObject(i)
            ETest(t.getString("id"), t.optString("title"), t.optString("test_code"), t.optInt("duration_minutes"), t.optString("total_marks"), t.optBoolean("camera_required", false))
        }
    }

    fun signOut(context: Context) {
        val prefs = context.getSharedPreferences("ilm_etest_session", Context.MODE_PRIVATE)
        val token = prefs.getString("access_token", null)
        if (token != null) Thread { try { request("/auth/v1/logout", "POST", token) } catch (_: Exception) {} }.start()
        prefs.edit().clear().apply()
    }
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = lightColorScheme(primary = Blue, secondary = Gold, background = Canvas, surface = Color.White, onSurface = Ink)) {
                ETestApp(this)
            }
        }
    }
}

@Composable
private fun ETestApp(activity: MainActivity) {
    val scope = rememberCoroutineScope()
    var profile by remember { mutableStateOf<StudentProfile?>(null) }
    var loading by remember { mutableStateOf(true) }
    var error by remember { mutableStateOf("") }
    var selectedTest by remember { mutableStateOf<ETest?>(null) }
    var tests by remember { mutableStateOf<List<ETest>>(emptyList()) }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        profile = ETestApi.loadProfile(activity)
        if (profile != null && profile?.status == "approved") {
            try { tests = ETestApi.loadTests(activity) } catch (e: Exception) { error = e.message ?: "Could not load tests." }
        }
        loading = false
    }

    if (loading) {
        Box(Modifier.fillMaxSize().background(Canvas), contentAlignment = Alignment.Center) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text("ILM", color = Gold, fontSize = 34.sp, fontWeight = FontWeight.Black)
                Text("Loading your secure student workspace…", color = Muted)
                CircularProgressIndicator(color = Blue)
            }
        }
    } else if (profile == null) {
        LoginScreen(email, { email = it }, password, { password = it }, busy, error, {
            busy = true; error = ""
            scope.launch {
                try { profile = ETestApi.signIn(activity, email.trim(), password); password = ""; if (profile?.status == "approved") tests = ETestApi.loadTests(activity) }
                catch (e: Exception) { error = e.message ?: "Sign in failed." }
                busy = false
            }
        })
    } else if (selectedTest != null) {
        TestPermissionScreen(activity, selectedTest!!, onBack = { selectedTest = null }, onError = { error = it })
    } else {
        StudentHome(profile!!, tests, error, onRefresh = {
            scope.launch { busy = true; try { tests = ETestApi.loadTests(activity); error = "" } catch (e: Exception) { error = e.message ?: "Could not refresh." }; busy = false }
        }, busy = busy, onSelect = { selectedTest = it }, onLogout = {
            ETestApi.signOut(activity); profile = null; tests = emptyList(); error = ""
        })
    }
}

@Composable
private fun LoginScreen(email: String, onEmail: (String) -> Unit, password: String, onPassword: (String) -> Unit, busy: Boolean, error: String, onLogin: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Canvas).verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(64.dp).background(Navy, RoundedCornerShape(18.dp)), contentAlignment = Alignment.Center) {
            Text("ILM", color = Gold, fontSize = 22.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.height(24.dp))
        Text("Welcome to ILM ETest", color = Navy, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
        Text("Sign in with the student account created by ILM.", color = Muted, fontSize = 14.sp)
        Spacer(Modifier.height(24.dp))
        Card(shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(email, onEmail, modifier = Modifier.fillMaxWidth(), label = { Text("Student email") }, singleLine = true)
                OutlinedTextField(password, onPassword, modifier = Modifier.fillMaxWidth(), label = { Text("Password") }, singleLine = true, visualTransformation = PasswordVisualTransformation())
                if (error.isNotBlank()) Text(error, color = Color(0xFFB42318), fontSize = 12.sp)
                Button(onClick = onLogin, enabled = !busy && email.isNotBlank() && password.isNotBlank(), modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(12.dp)) {
                    if (busy) CircularProgressIndicator(Modifier.size(18.dp), color = Color.White) else Text("Sign in to student portal", fontWeight = FontWeight.Bold)
                }
            }
        }
        Spacer(Modifier.height(18.dp))
        Text("Use the same email and password supplied by ILM. New accounts require administrator approval.", color = Muted, fontSize = 12.sp)
        Text("ILM THE CONTEST CENTER", color = Navy, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp, modifier = Modifier.padding(top = 28.dp))
    }
}

@Composable
private fun StudentHome(profile: StudentProfile, tests: List<ETest>, error: String, onRefresh: () -> Unit, busy: Boolean, onSelect: (ETest) -> Unit, onLogout: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Canvas).verticalScroll(rememberScrollState()).padding(18.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text("ILM ETEST", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.ExtraBold)
                Text("THE CONTEST CENTER", color = Muted, fontSize = 9.sp, letterSpacing = 1.2.sp)
            }
            TextButton(onClick = onLogout) { Text("Sign out") }
        }
        Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(22.dp)) {
            Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("STUDENT WORKSPACE", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
                Text("Welcome, ${profile.name}", color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.ExtraBold)
                Text("Registration ID: ${profile.registration}", color = Color(0xFFD6DFED), fontSize = 12.sp)
                Text("Account status: ${profile.status.uppercase()}", color = Gold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
        if (profile.status != "approved") {
            NoticeCard("Account approval pending", "Your account is not approved yet. Tests will become available after an ILM administrator approves your profile.")
        } else {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Available tests", color = Navy, fontSize = 20.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                TextButton(onClick = onRefresh, enabled = !busy) { Text("Refresh") }
            }
            if (error.isNotBlank()) NoticeCard("Connection notice", error)
            if (tests.isEmpty()) NoticeCard("No tests published", "When ILM publishes an assessment for students, it will appear here.")
            tests.forEach { test ->
                Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
                    Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(test.title, color = Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                            if (test.cameraRequired) AssistChip(onClick = {}, label = { Text("Camera required") }, leadingIcon = { Icon(Icons.Default.Videocam, null) })
                        }
                        Text("${test.duration} minutes  •  ${test.marks} marks  •  ID: ${test.code}", color = Muted, fontSize = 11.sp)
                        Button(onClick = { onSelect(test) }, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(11.dp)) { Text("Open assessment") }
                    }
                }
            }
        }
        Text("Camera permission is requested only for tests marked as invigilated. It is not accessed in the background.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun NoticeCard(title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(title, color = Navy, fontWeight = FontWeight.Bold)
            Text(body, color = Muted, fontSize = 12.sp)
        }
    }
}

@Composable
private fun TestPermissionScreen(activity: MainActivity, test: ETest, onBack: () -> Unit, onError: (String) -> Unit) {
    var cameraGranted by remember { mutableStateOf(ContextCompat.checkSelfPermission(activity, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var screenGranted by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf("") }
    var captureIntent by remember { mutableStateOf<Intent?>(null) }
    val projectionManager = activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
    val cameraPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { cameraGranted = it; if (!it) message = "Camera permission is needed for this invigilated test." }
    val screenPermission = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        screenGranted = result.resultCode == Activity.RESULT_OK && result.data != null
        if (screenGranted) { captureIntent = result.data; message = "Screen-sharing permission granted for this session." }
        else message = "Screen sharing was not enabled. You cannot enter this invigilated assessment yet."
    }
    val ready = if (test.cameraRequired) cameraGranted && screenGranted else true
    Column(Modifier.fillMaxSize().background(Canvas).verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        TextButton(onClick = onBack) { Text("← Back to tests") }
        Text("ASSESSMENT CHECK-IN", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.4.sp)
        Text(test.title, color = Navy, fontSize = 25.sp, fontWeight = FontWeight.ExtraBold)
        Text("${test.duration} minutes • ${test.marks} marks", color = Muted, fontSize = 13.sp)
        if (test.cameraRequired) {
            NoticeCard("Invigilated test", "This assessment requires camera and screen-sharing permission. Android will show separate permission prompts. You can stop either permission at any time.")
            if (cameraGranted) CameraPreview(Modifier.fillMaxWidth().height(220.dp))
            Button(onClick = { cameraPermission.launch(Manifest.permission.CAMERA) }, enabled = !cameraGranted, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.Videocam, null); Spacer(Modifier.width(8.dp)); Text(if (cameraGranted) "Camera permission granted" else "Allow camera for this test")
            }
            Button(onClick = { screenPermission.launch(projectionManager.createScreenCaptureIntent()) }, enabled = !screenGranted, modifier = Modifier.fillMaxWidth()) {
                Icon(Icons.Default.ScreenShare, null); Spacer(Modifier.width(8.dp)); Text(if (screenGranted) "Screen sharing approved" else "Allow screen sharing")
            }
        } else {
            NoticeCard("Standard assessment", "This test is not marked as invigilated. Camera permission will not be requested.")
            Button(onClick = onBack, modifier = Modifier.fillMaxWidth()) { Text("Return to tests") }
        }
        if (message.isNotBlank()) Text(message, color = Muted, fontSize = 12.sp)
        if (test.cameraRequired) {
            Button(onClick = { message = "Permissions are ready. The native question and answer exam flow is the next integration step." }, enabled = ready, modifier = Modifier.fillMaxWidth()) {
                Text("Continue to assessment")
            }
        }
        Text("Camera access is limited to this invigilated test session. This app version does not upload or store camera footage.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun CameraPreview(modifier: Modifier = Modifier) {
    val lifecycleOwner = LocalLifecycleOwner.current
    AndroidView(modifier = modifier, factory = { context ->
        val previewView = PreviewView(context)
        val providerFuture = ProcessCameraProvider.getInstance(context)
        providerFuture.addListener({
            val provider = providerFuture.get()
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }
            try { provider.unbindAll(); provider.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_FRONT_CAMERA, preview) } catch (_: Exception) {}
        }, ContextCompat.getMainExecutor(context))
        previewView
    })
}
