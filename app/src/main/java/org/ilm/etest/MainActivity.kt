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
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat

private val Navy = Color(0xFF142D52)
private val Blue = Color(0xFF2864D7)
private val Gold = Color(0xFFD9AD55)
private val Ink = Color(0xFF1C2B43)
private val Muted = Color(0xFF718096)
private val Canvas = Color(0xFFF4F7FB)
private val Line = Color(0xFFE7ECF3)
private val Green = Color(0xFF18845B)

private enum class AppTab(val label: String) {
    HOME("Home"), TESTS("My Tests"), RESULTS("Results"), PROFILE("Profile")
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        setContent {
            var selectedTab by remember { mutableStateOf(AppTab.HOME) }
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
                    status = "Android permission granted. Screen content is not being sent to ILM."
                } else {
                    sharing = false
                    status = "Permission was not granted. You can continue exploring."
                }
            }

            MaterialTheme(
                colorScheme = lightColorScheme(
                    primary = Blue,
                    secondary = Gold,
                    background = Canvas,
                    surface = Color.White,
                    onSurface = Ink
                )
            ) {
                Scaffold(
                    containerColor = Canvas,
                    bottomBar = {
                        NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                            AppTab.values().forEach { tab ->
                                val icon = when (tab) {
                                    AppTab.HOME -> Icons.Default.Home
                                    AppTab.TESTS -> Icons.Default.Assignment
                                    AppTab.RESULTS -> Icons.Default.BarChart
                                    AppTab.PROFILE -> Icons.Default.Person
                                }
                                NavigationBarItem(
                                    selected = selectedTab == tab,
                                    onClick = { selectedTab = tab },
                                    icon = { Icon(icon, contentDescription = tab.label) },
                                    label = { Text(tab.label, fontSize = 10.sp, fontWeight = FontWeight.SemiBold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = Blue,
                                        selectedTextColor = Blue,
                                        indicatorColor = Color(0xFFEAF0FF),
                                        unselectedIconColor = Muted,
                                        unselectedTextColor = Muted
                                    )
                                )
                            }
                        }
                    }
                ) { padding ->
                    when (selectedTab) {
                        AppTab.HOME -> HomeScreen(
                            modifier = Modifier.padding(padding),
                            onOpenTests = { selectedTab = AppTab.TESTS },
                            onOpenProfile = { selectedTab = AppTab.PROFILE },
                            onScreenShare = {
                                if (!sharing) captureLauncher.launch(manager.createScreenCaptureIntent())
                                else {
                                    startService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
                                    sharing = false
                                    status = "Screen-sharing permission session stopped."
                                }
                            },
                            sharing = sharing,
                            status = status
                        )
                        AppTab.TESTS -> TestsScreen(Modifier.padding(padding))
                        AppTab.RESULTS -> ResultsScreen(Modifier.padding(padding))
                        AppTab.PROFILE -> ProfileScreen(
                            modifier = Modifier.padding(padding),
                            sharing = sharing,
                            status = status,
                            onScreenShare = {
                                if (!sharing) captureLauncher.launch(manager.createScreenCaptureIntent())
                                else {
                                    startService(Intent(this, ScreenCaptureService::class.java).setAction(ScreenCaptureService.ACTION_STOP))
                                    sharing = false
                                    status = "Screen-sharing permission session stopped."
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HomeScreen(
    modifier: Modifier = Modifier,
    onOpenTests: () -> Unit,
    onOpenProfile: () -> Unit,
    onScreenShare: () -> Unit,
    sharing: Boolean,
    status: String
) {
    Column(
        modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        Spacer(Modifier.height(8.dp))
        TopHeader(onProfile = onOpenProfile)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("STUDENT WORKSPACE", color = Blue, fontSize = 10.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.8.sp)
            Text("Welcome back!", color = Navy, fontSize = 27.sp, fontWeight = FontWeight.ExtraBold)
            Text("Your learning and assessment hub.", color = Muted, fontSize = 13.sp)
        }
        HeroCard(onOpenTests)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            MetricCard("Available tests", "—", Icons.Default.Assignment, Modifier.weight(1f))
            MetricCard("Completed", "—", Icons.Default.TaskAlt, Modifier.weight(1f))
            MetricCard("Avg. score", "—", Icons.Default.Insights, Modifier.weight(1f))
        }
        SectionHeading("Your next step", "VIEW ALL", onOpenTests)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line)
        ) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(Color(0xFFEAF0FF), RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.EventNote, contentDescription = null, tint = Blue)
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text("No scheduled test shown", color = Ink, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    Text("Published tests will appear in My Tests.", color = Muted, fontSize = 11.sp)
                }
                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Muted)
            }
        }
        SectionHeading("Secure assessment", "SET UP", onScreenShare)
        Card(
            colors = CardDefaults.cardColors(containerColor = Color.White),
            shape = RoundedCornerShape(18.dp),
            border = androidx.compose.foundation.BorderStroke(1.dp, Line)
        ) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.PresentToAll, contentDescription = null, tint = Blue)
                    Spacer(Modifier.width(8.dp))
                    Text("Screen-sharing permission", color = Ink, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.weight(1f))
                    StatusPill(if (sharing) "PERMISSION ON" else "OFF", sharing)
                }
                Text(status, color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                Text(
                    "This preview only starts Android's permission session. It does not transmit or record your screen.",
                    color = Muted, fontSize = 10.sp, lineHeight = 15.sp
                )
                OutlinedButton(onClick = onScreenShare, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
                    Icon(if (sharing) Icons.Default.StopCircle else Icons.Default.ScreenShare, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (sharing) "Stop permission session" else "Set up screen sharing")
                }
            }
        }
        Text("ILM THE CONTEST CENTER  •  ANDROID APP PREVIEW", color = Muted, fontSize = 9.sp, letterSpacing = 1.sp, modifier = Modifier.align(Alignment.CenterHorizontally))
        Spacer(Modifier.height(8.dp))
    }
}

@Composable
private fun TopHeader(onProfile: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(42.dp).background(Navy, RoundedCornerShape(13.dp)), contentAlignment = Alignment.Center) {
            Text("ILM", color = Gold, fontSize = 14.sp, fontWeight = FontWeight.Black)
        }
        Spacer(Modifier.width(10.dp))
        Column {
            Text("ILM ETEST", color = Navy, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, letterSpacing = 1.sp)
            Text("THE CONTEST CENTER", color = Muted, fontSize = 8.sp, letterSpacing = 1.1.sp)
        }
        Spacer(Modifier.weight(1f))
        IconButton(onClick = onProfile) {
            Icon(Icons.Default.AccountCircle, contentDescription = "Profile", tint = Navy, modifier = Modifier.size(28.dp))
        }
    }
}

@Composable
private fun HeroCard(onOpenTests: () -> Unit) {
    Card(
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(containerColor = Navy)
    ) {
        Box(Modifier.fillMaxWidth().padding(20.dp)) {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("ILM ASSESSMENTS", color = Gold, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.5.sp)
                    Spacer(Modifier.weight(1f))
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Gold)
                }
                Text("Think clearly.\nShow what you know.", color = Color.White, fontSize = 25.sp, lineHeight = 31.sp, fontWeight = FontWeight.ExtraBold)
                Text("Your next challenge starts with preparation.", color = Color(0xFFD6DFED), fontSize = 12.sp)
                Button(
                    onClick = onOpenTests,
                    colors = ButtonDefaults.buttonColors(containerColor = Gold, contentColor = Navy),
                    shape = RoundedCornerShape(11.dp)
                ) {
                    Text("Explore my tests", fontWeight = FontWeight.Bold)
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Default.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun MetricCard(title: String, value: String, icon: androidx.compose.ui.graphics.vector.ImageVector, modifier: Modifier = Modifier) {
    Card(modifier, colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Icon(icon, contentDescription = null, tint = Blue, modifier = Modifier.size(18.dp))
            Text(value, color = Navy, fontSize = 21.sp, fontWeight = FontWeight.ExtraBold)
            Text(title, color = Muted, fontSize = 10.sp, lineHeight = 13.sp)
        }
    }
}

@Composable
private fun SectionHeading(title: String, action: String, onClick: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(title, color = Navy, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.weight(1f))
        Text(action, color = Blue, fontSize = 10.sp, fontWeight = FontWeight.Bold, modifier = Modifier.clickable(onClick = onClick))
    }
}

@Composable
private fun StatusPill(label: String, active: Boolean) {
    Surface(color = if (active) Color(0xFFE7F6EF) else Color(0xFFF0F2F6), shape = CircleShape) {
        Text(label, color = if (active) Green else Muted, fontSize = 8.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp))
    }
}

@Composable
private fun TestsScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TopHeader(onProfile = {})
        PageTitle("My Tests", "Your published assessments will appear here.")
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Box(Modifier.size(62.dp).background(Color(0xFFEAF0FF), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Assignment, contentDescription = null, tint = Blue, modifier = Modifier.size(29.dp))
                }
                Text("No tests available yet", color = Navy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("When ILM publishes a test for your account, it will show up here with its schedule and duration.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                StatusPill("WAITING FOR PUBLISHED TESTS", false)
            }
        }
        InfoTile(Icons.Default.Lightbulb, "Before your test", "Make sure your phone is charged and your internet connection is stable.")
        InfoTile(Icons.Default.Security, "Assessment privacy", "Android will ask before any screen-sharing permission session starts.")
    }
}

@Composable
private fun ResultsScreen(modifier: Modifier = Modifier) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TopHeader(onProfile = {})
        PageTitle("Results & Progress", "Review your assessment history.")
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
            Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(Icons.Default.BarChart, contentDescription = null, tint = Blue, modifier = Modifier.size(42.dp))
                Text("Your results will appear here", color = Navy, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("After you complete an assessment and ILM publishes its result, your marks and progress can be displayed here.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        InfoTile(Icons.Default.EmojiEvents, "Keep learning", "Every assessment is a chance to discover what you know and what to practise next.")
    }
}

@Composable
private fun ProfileScreen(modifier: Modifier = Modifier, sharing: Boolean, status: String, onScreenShare: () -> Unit) {
    Column(modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        TopHeader(onProfile = {})
        PageTitle("My Profile", "Your student account and app settings.")
        Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(18.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
            Column(Modifier.fillMaxWidth().padding(18.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.size(66.dp).background(Color(0xFFEAF0FF), CircleShape), contentAlignment = Alignment.Center) {
                    Icon(Icons.Default.Person, contentDescription = null, tint = Blue, modifier = Modifier.size(34.dp))
                }
                Text("Student account", color = Navy, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text("Account sign-in will be connected to your ILM ETest account in a later update.", color = Muted, fontSize = 12.sp, lineHeight = 18.sp)
                StatusPill("PROFILE PREVIEW", false)
            }
        }
        Text("APP SETTINGS", color = Muted, fontSize = 10.sp, fontWeight = FontWeight.Bold, letterSpacing = 1.3.sp)
        InfoTile(Icons.Default.Security, "Screen-sharing permission", status)
        OutlinedButton(onClick = onScreenShare, modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(12.dp)) {
            Icon(if (sharing) Icons.Default.StopCircle else Icons.Default.ScreenShare, contentDescription = null)
            Spacer(Modifier.width(8.dp))
            Text(if (sharing) "Stop permission session" else "Request screen-sharing permission")
        }
        InfoTile(Icons.Default.Info, "About ILM ETest", "Version 1.0.0 • ILM The Contest Center")
        Text("For help, contact your ILM administrator.", color = Muted, fontSize = 11.sp)
    }
}

@Composable
private fun PageTitle(title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
        Text(title, color = Navy, fontSize = 26.sp, fontWeight = FontWeight.ExtraBold)
        Text(subtitle, color = Muted, fontSize = 12.sp)
    }
}

@Composable
private fun InfoTile(icon: androidx.compose.ui.graphics.vector.ImageVector, title: String, body: String) {
    Card(colors = CardDefaults.cardColors(containerColor = Color.White), shape = RoundedCornerShape(16.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Line)) {
        Row(Modifier.fillMaxWidth().padding(15.dp), verticalAlignment = Alignment.Top) {
            Icon(icon, contentDescription = null, tint = Blue, modifier = Modifier.size(21.dp))
            Spacer(Modifier.width(12.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                Text(body, color = Muted, fontSize = 11.sp, lineHeight = 16.sp)
            }
        }
    }
}
