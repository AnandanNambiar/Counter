package com.zenguard.counter

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.zenguard.counter.ui.theme.CounterTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt
import android.content.Context
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var openCounter by mutableStateOf<Counter?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.createChannel(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        val request = PeriodicWorkRequestBuilder<ReminderWorker>(2, TimeUnit.HOURS).build()
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "reminders",
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )

        readIntent(intent)
        enableEdgeToEdge()
        setContent {
            CounterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    App(
                        openCounter = openCounter,
                        onOpened = { openCounter = null },
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        readIntent(intent)
    }

    private fun readIntent(intent: Intent?) {
        val id = intent?.getIntExtra("counterId", -1) ?: -1
        val name = intent?.getStringExtra("counterName")
        if (id != -1 && name != null) openCounter = Counter(id = id, name = name)
    }
}

@Composable
fun Greeting(name: String, modifier: Modifier = Modifier) {
    Text(
        text = "Hello $name!",
        modifier = modifier
    )
}

@Preview(showBackground = true)
@Composable
fun GreetingPreview() {
    CounterTheme {
        Greeting("Android")
    }
}

@Composable
fun TapCounter(
    counterId: Int,
    name: String,
    onHistory: () -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).dailyCountDao() }
    val counterDao = remember { AppDatabase.get(context).counterDao() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val vibrator = context.getSystemService(Vibrator::class.java)

    val saved by dao.observeCount(counterId, LocalDate.now().toString())
        .collectAsState(initial = 0)
    val count = saved ?: 0
    val lifetime by dao.observeLifetime(counterId).collectAsState(initial = 0)
    val history by dao.observeHistory(counterId).collectAsState(initial = emptyList())
    var menuOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }
    var running by remember { mutableStateOf(false) }
    var session by remember { mutableStateOf(0) }
    var showFocus by remember { mutableStateOf(false) }
    var intervalText by remember { mutableStateOf("1") }
    var targetText by remember { mutableStateOf("1") }
    var dailyTarget by remember { mutableStateOf(108) }
    var targetDialog by remember { mutableStateOf(false) }
    var dailyTargetText by remember { mutableStateOf("108") }
    val targetMalas = dailyTarget / 108.0
    val targetLabel =
        if (targetMalas % 1.0 == 0.0) targetMalas.toInt().toString()
        else "%.2f".format(targetMalas)
    val doneDays = history.filter { it.count >= dailyTarget }
        .map { LocalDate.parse(it.date) }.toSet()
    val streak = computeStreak(doneDays, LocalDate.now())
    val prefs = remember { context.getSharedPreferences("milestones", Context.MODE_PRIVATE) }
    var celebrate by remember { mutableStateOf<Milestone?>(null) }

    LaunchedEffect(streak, lifetime) {
        val fresh = reachedMilestones(streak, lifetime)
            .firstOrNull { !prefs.getBoolean("$counterId:${it.key}", false) }
        if (fresh != null) {
            prefs.edit().putBoolean("$counterId:${fresh.key}", true).apply()
            celebrate = fresh
            vibrator?.vibrate(VibrationEffect.createOneShot(1000, VibrationEffect.DEFAULT_AMPLITUDE))
        }
    }


    // Load this counter's saved settings
    LaunchedEffect(counterId) {
        counterDao.get(counterId)?.let {
            val sec = it.focusIntervalSec
            intervalText = if (sec % 1.0 == 0.0) sec.toInt().toString() else sec.toString()
            targetText = it.focusTargetMalas.toString()
            dailyTarget = it.dailyTarget
        }
    }

    val focusTarget = (targetText.toIntOrNull()?.coerceAtLeast(1) ?: 1) * 108

    // Focus mode timer
    LaunchedEffect(running) {
        if (running) {
            session = 0
            while (session < focusTarget) {
                val seconds = intervalText.toDoubleOrNull()?.coerceAtLeast(0.2) ?: 1.0
                delay((seconds * 1000).toLong())
                val date = LocalDate.now().toString()
                dao.ensureRow(counterId, date)
                dao.increment(counterId, date)
                session++
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
            running = false
        }
    }

    Box(modifier = modifier.fillMaxSize()) {

        // Back arrow, top left
        IconButton(
            onClick = onBack,
            modifier = Modifier.align(Alignment.TopStart)
        ) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }

        // Menu, top right
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            IconButton(onClick = { menuOpen = true }) {
                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
            }
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(if (showFocus) "Hide Focus Mode" else "Focus mode") },
                    onClick = {
                        menuOpen = false
                        if (showFocus) running = false
                        showFocus = !showFocus
                    }
                )
                DropdownMenuItem(
                    text = { Text("Set daily target") },
                    onClick = {
                        menuOpen = false
                        dailyTargetText = targetLabel
                        targetDialog = true
                    }
                )
                DropdownMenuItem(
                    text = { Text("History") },
                    onClick = { menuOpen = false; onHistory() }
                )
                DropdownMenuItem(
                    text = { Text("Reset daily count") },
                    onClick = { menuOpen = false; confirm = "day" }
                )
                DropdownMenuItem(
                    text = { Text("Reset lifetime count") },
                    onClick = { menuOpen = false; confirm = "all" }
                )
            }
        }

        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(text = name, style = MaterialTheme.typography.headlineMedium)
            Spacer(modifier = Modifier.height(8.dp))
            Text("Today: $count (108 × ${count / 108})", style = MaterialTheme.typography.bodyLarge)
            Text("Lifetime: $lifetime (108 × ${lifetime / 108})", style = MaterialTheme.typography.bodyLarge)
            Text("Daily target: $targetLabel malas", style = MaterialTheme.typography.bodyLarge)
            Text("Streak: $streak days", style = MaterialTheme.typography.bodyLarge)
            Spacer(modifier = Modifier.height(24.dp))

            // Small undo circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
                    .clickable {
                        scope.launch { dao.decrement(counterId, LocalDate.now().toString()) }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Undo,
                    contentDescription = "Undo",
                    tint = MaterialTheme.colorScheme.onSecondary,
                    modifier = Modifier.size(28.dp)
                )
            }

            Spacer(modifier = Modifier.height(24.dp))

            if (showFocus) {
                OutlinedTextField(
                    value = intervalText,
                    onValueChange = { intervalText = it },
                    label = { Text("Seconds between counts") },
                    singleLine = true,
                    enabled = !running,
                    modifier = Modifier.width(220.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                OutlinedTextField(
                    value = targetText,
                    onValueChange = { targetText = it },
                    label = { Text("Target (malas of 108)") },
                    singleLine = true,
                    enabled = !running,
                    modifier = Modifier.width(220.dp)
                )
                Spacer(modifier = Modifier.height(8.dp))

                Button(onClick = {
                    if (!running) {
                        val sec = intervalText.toDoubleOrNull()?.coerceAtLeast(0.2) ?: 1.0
                        val malas = targetText.toIntOrNull()?.coerceAtLeast(1) ?: 1
                        scope.launch { counterDao.saveFocus(counterId, sec, malas) }
                    }
                    running = !running
                }) {
                    Text(if (running) "Stop ($session / $focusTarget)" else "Start focus mode")
                }
                Spacer(modifier = Modifier.height(24.dp))
            }

            // Big tap circle
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        scope.launch {
                            val d = LocalDate.now().toString()
                            dao.ensureRow(counterId, d)
                            dao.increment(counterId, d)
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "${count % 108}",
                    fontSize = 64.sp,
                    color = MaterialTheme.colorScheme.onPrimary
                )
            }
            Spacer(modifier = Modifier.height(16.dp))
            LinearProgressIndicator(
                progress = { (count % 108) / 108f },
                modifier = Modifier.width(240.dp)
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text("${count % 108} / 108", style = MaterialTheme.typography.bodyMedium)
        }
    }

    // Reset confirmation dialog
    val pending = confirm
    if (pending != null) {
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = {
                Text(if (pending == "day") "Reset today's count?" else "Reset lifetime count?")
            },
            text = {
                Text(
                    if (pending == "day") "Today's count for this counter will be cleared."
                    else "All saved counts for this counter will be deleted. This cannot be undone."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    scope.launch {
                        if (pending == "day") dao.resetDay(counterId, LocalDate.now().toString())
                        else dao.resetAll(counterId)
                    }
                    confirm = null
                }) { Text("Reset") }
            },
            dismissButton = {
                TextButton(onClick = { confirm = null }) { Text("Cancel") }
            }
        )
    }

    // Daily target dialog
    if (targetDialog) {
        AlertDialog(
            onDismissRequest = { targetDialog = false },
            title = { Text("Daily target") },
            text = {
                OutlinedTextField(
                    value = dailyTargetText,
                    onValueChange = { dailyTargetText = it },
                    label = { Text("Malas per day (108 counts each)") },
                    singleLine = true
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val m = dailyTargetText.toDoubleOrNull()
                    val t = if (m != null && m > 0) (m * 108).roundToInt().coerceAtLeast(1) else dailyTarget
                    dailyTarget = t
                    scope.launch { counterDao.saveTarget(counterId, t) }
                    targetDialog = false})
                { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { targetDialog = false }) { Text("Cancel") }
            }
        )
    }
    celebrate?.let { m ->
        AlertDialog(
            onDismissRequest = { celebrate = null },
            title = { Text("Milestone: ${m.title}") },
            text = { Text("You've done $lifetime counts on $name since you started. Keep going.") },
            confirmButton = {
                TextButton(onClick = { celebrate = null }) { Text("Continue") }
            }
        )
    }
}

@Composable
fun App(
    openCounter: Counter?,
    onOpened: () -> Unit,
    modifier: Modifier = Modifier
) {
    var screen by remember { mutableStateOf<Screen>(Screen.Home) }
    val s = screen

    // Jump straight to a counter when a notification was tapped
    LaunchedEffect(openCounter) {
        if (openCounter != null) {
            screen = Screen.Count(openCounter)
            onOpened()
        }
    }

    val goBack: () -> Unit = {
        screen = if (s is Screen.History && s.counter != null) Screen.Count(s.counter)
        else Screen.Home
    }

    BackHandler(enabled = s !is Screen.Home) { goBack() }

    when (s) {
        is Screen.Home -> CounterList(
            onOpen = { screen = Screen.Count(it) },
            onHistory = { screen = Screen.History(null) },
            modifier = modifier
        )
        is Screen.Count -> TapCounter(
            counterId = s.counter.id,
            name = s.counter.name,
            onHistory = { screen = Screen.History(s.counter) },
            onBack = goBack,
            modifier = modifier
        )
        is Screen.History -> HistoryScreen(s.counter, onBack = goBack, modifier = modifier)
    }
}

@Composable
fun CounterList(
    onOpen: (Counter) -> Unit,
    onHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).counterDao() }
    val dailyDao = remember { AppDatabase.get(context).dailyCountDao() }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now().toString()
    val counters by remember(today) { dao.observeSummaries(today) }
        .collectAsState(initial = emptyList())
    var newName by remember { mutableStateOf("") }
    var homeMenu by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val db = AppDatabase.get(context)
                val json = buildBackupJson(db.counterDao().getAll(), db.dailyCountDao().getAll())
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }
                Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                "Japa Counter",
                style = MaterialTheme.typography.headlineMedium,
                modifier = Modifier.weight(1f)
            )
            Box {
                IconButton(onClick = { homeMenu = true }) {
                    Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                }
                DropdownMenu(expanded = homeMenu, onDismissRequest = { homeMenu = false }) {
                    DropdownMenuItem(
                        text = { Text("Back up data") },
                        onClick = {
                            homeMenu = false
                            exportLauncher.launch("japa-backup-${LocalDate.now()}.json")
                        }
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(8.dp))
        TextButton(onClick = onHistory) { Text("View history") }
        TextButton(onClick = { Notifier.show(context, "Japa Counter", "Time for your japa") }) {
            Text("Test notification")
        }
        TextButton(onClick = {
            val request = OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(workDataOf("ignoreQuiet" to true))
                .build()
            WorkManager.getInstance(context).enqueue(request)
        }) { Text("Test reminder check") }

        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = newName,
                onValueChange = { newName = it },
                label = { Text("New counter") },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Button(onClick = {
                if (newName.isNotBlank()) {
                    val name = newName.trim()
                    scope.launch { dao.add(Counter(name = name)) }
                    newName = ""
                }
            }) { Text("Add") }
        }
        Spacer(modifier = Modifier.height(16.dp))

        LazyColumn {
            items(counters, key = { it.id }) { c ->
                var menuOpen by remember { mutableStateOf(false) }
                var renameOpen by remember { mutableStateOf(false) }
                var renameText by remember { mutableStateOf(c.name) }

                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { onOpen(Counter(id = c.id, name = c.name)) }
                                .padding(horizontal = 16.dp, vertical = 12.dp)
                        ) {
                            Text(text = c.name, style = MaterialTheme.typography.titleLarge)
                            Text("Today: ${c.today} (108 × ${c.today / 108})")
                            Text("Lifetime: ${c.lifetime}  (108 × ${c.lifetime / 108})")
                        }
                        Box {
                            IconButton(onClick = { menuOpen = true }) {
                                Icon(Icons.Default.MoreVert, contentDescription = "Menu")
                            }
                            DropdownMenu(
                                expanded = menuOpen,
                                onDismissRequest = { menuOpen = false }
                            ) {
                                DropdownMenuItem(
                                    text = { Text("Rename") },
                                    onClick = {
                                        menuOpen = false
                                        renameText = c.name
                                        renameOpen = true
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("Delete") },
                                    onClick = {
                                        menuOpen = false
                                        scope.launch {
                                            dailyDao.resetAll(c.id)
                                            dao.delete(c.id)
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                if (renameOpen) {
                    AlertDialog(
                        onDismissRequest = { renameOpen = false },
                        title = { Text("Rename counter") },
                        text = {
                            OutlinedTextField(
                                value = renameText,
                                onValueChange = { renameText = it },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            TextButton(onClick = {
                                val renamed = renameText.trim()
                                if (renamed.isNotBlank()) {
                                    scope.launch { dao.rename(c.id, renamed) }
                                }
                                renameOpen = false
                            }) { Text("Save") }
                        },
                        dismissButton = {
                            TextButton(onClick = { renameOpen = false }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}

sealed interface Screen {
    object Home : Screen
    data class Count(val counter: Counter) : Screen
    data class History(val counter: Counter?) : Screen
}

@Composable
fun HistoryScreen(
    counter: Counter?,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).dailyCountDao() }
    val rows by remember(counter?.id) {
        if (counter == null) dao.observeAllHistory()
        else dao.observeHistory(counter.id)
            .map { list -> list.map { HistoryRow(it.date, counter.name, it.count) } }
    }.collectAsState(initial = emptyList())

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text(
            text = if (counter == null) "All history" else "${counter.name} history",
            style = MaterialTheme.typography.headlineMedium
        )
        Spacer(modifier = Modifier.height(16.dp))

        if (rows.isEmpty()) Text("Nothing counted yet.")
        LazyColumn {
            items(rows) { r ->
                Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(r.date, modifier = Modifier.weight(1f))
                        if (counter == null) Text(r.name, modifier = Modifier.weight(1f))
                        Text("${r.count}  (108 × ${r.count / 108})")
                    }
                }
            }
        }
    }
}