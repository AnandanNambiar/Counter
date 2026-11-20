package com.zenguard.counter

import android.media.MediaPlayer
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.compose.foundation.layout.ColumnScope
import java.io.File
import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.zenguard.counter.ui.theme.CounterTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.time.LocalDate
import java.util.concurrent.TimeUnit
import kotlin.math.roundToInt

class MainActivity : ComponentActivity() {
    private val askNotifications =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    private var openCounter by mutableStateOf<Counter?>(null)
    private var openMeditate by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Notifier.createChannel(this)
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            askNotifications.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        WorkManager.getInstance(this).enqueue(
            OneTimeWorkRequestBuilder<ReminderWorker>()
                .setInputData(workDataOf("ignoreQuiet" to true)).build()
        )

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
                        openMeditate = openMeditate,
                        onMeditateOpened = { openMeditate = false },
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
        if (intent?.getBooleanExtra("openMeditate", false) == true) openMeditate = true
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
    val slotDao = remember { AppDatabase.get(context).slotStateDao() }
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
                val newCount = dao.getCount(counterId, date) ?: 0
                if (newCount > 0 && newCount % 108 == 0) {
                    slotDao.ensureRow()
                    slotDao.addToken()
                }
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
            Text(
                "Daily target: $targetLabel ${if (targetLabel == "1") "mala" else "malas"}",
                style = MaterialTheme.typography.bodyLarge
            )
            Text(
                "Streak: $streak ${if (streak == 1) "day" else "days"}",
                style = MaterialTheme.typography.bodyLarge
            )
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
                            val newCount = dao.getCount(counterId, d) ?: 0
                            if (newCount > 0 && newCount % 108 == 0) {
                                vibrator?.vibrate(
                                    VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE)
                                )
                                slotDao.ensureRow()
                                slotDao.addToken()
                            }
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
                    targetDialog = false
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { targetDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Milestone dialog
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
    openMeditate: Boolean,
    onMeditateOpened: () -> Unit,
    modifier: Modifier = Modifier
) {
    var screen by rememberSaveable(stateSaver = screenSaver) {
        mutableStateOf<Screen>(Screen.Home)
    }
    val s = screen

    // Jump straight to a counter when a notification was tapped
    LaunchedEffect(openCounter) {
        if (openCounter != null) {
            screen = Screen.Count(openCounter)
            onOpened()
        }
    }

    LaunchedEffect(openMeditate) {
        if (openMeditate) {
            screen = Screen.Meditation
            onMeditateOpened()
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
            onRewards = { screen = Screen.Rewards },
            onMeditate = { screen = Screen.Meditation },
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
        is Screen.Rewards -> RewardsScreen(onBack = goBack, modifier = modifier)
        is Screen.Meditation -> MeditationScreen(onBack = goBack, modifier = modifier)
    }
}

@Composable
fun CounterList(
    onOpen: (Counter) -> Unit,
    onHistory: () -> Unit,
    onRewards: () -> Unit,
    onMeditate: () -> Unit,
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
                val json = buildBackupJson(db)
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(json.toByteArray()) }
                }
                Toast.makeText(context, "Backup saved", Toast.LENGTH_SHORT).show()
            }
        }
    }

    var pendingRestore by remember { mutableStateOf<BackupData?>(null) }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            scope.launch {
                try {
                    val text = withContext(Dispatchers.IO) {
                        context.contentResolver.openInputStream(uri)?.use { it.readBytes().decodeToString() }
                    } ?: throw IllegalStateException("empty file")
                    pendingRestore = parseBackupJson(text)
                } catch (e: Exception) {
                    Toast.makeText(context, "That file is not a valid backup", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    pendingRestore?.let { b ->
        AlertDialog(
            onDismissRequest = { pendingRestore = null },
            title = { Text("Restore backup?") },
            text = {
                Text("This replaces ALL current data with the backup: ${b.counters.size} counters, ${b.days.size} days of history, ${b.rewards.size} rewards and ${b.meditationDays.size} meditation days. This cannot be undone.")
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRestore = null
                    scope.launch {
                        restoreBackup(AppDatabase.get(context), b)
                        Toast.makeText(context, "Backup restored", Toast.LENGTH_SHORT).show()
                    }
                }) { Text("Restore") }
            },
            dismissButton = {
                TextButton(onClick = { pendingRestore = null }) { Text("Cancel") }
            }
        )
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
                    DropdownMenuItem(
                        text = { Text("Restore from backup") },
                        onClick = {
                            homeMenu = false
                            importLauncher.launch(arrayOf("*/*"))
                        }
                    )
                }
            }
        }

        Row {
            TextButton(onClick = onHistory) { Text("View history") }
            TextButton(onClick = onRewards) { Text("Rewards") }
            TextButton(onClick = onMeditate) { Text("Meditate") }
        }

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
                var deleteOpen by remember { mutableStateOf(false) }
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
                                        deleteOpen = true
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
                if (deleteOpen) {
                    AlertDialog(
                        onDismissRequest = { deleteOpen = false },
                        title = { Text("Delete ${c.name}?") },
                        text = { Text("All counts and history for this counter will be deleted. This cannot be undone.") },
                        confirmButton = {
                            TextButton(onClick = {
                                deleteOpen = false
                                scope.launch {
                                    dailyDao.resetAll(c.id)
                                    dao.delete(c.id)
                                }
                            }) { Text("Delete") }
                        },
                        dismissButton = {
                            TextButton(onClick = { deleteOpen = false }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }
}

sealed interface Screen {
    object Home : Screen
    object Rewards : Screen
    object Meditation : Screen
    data class Count(val counter: Counter) : Screen
    data class History(val counter: Counter?) : Screen
}

val screenSaver = Saver<Screen, List<Any?>>(
    save = { s ->
        when (s) {
            is Screen.Home -> listOf("home")
            is Screen.Rewards -> listOf("rewards")
            is Screen.Meditation -> listOf("meditation")
            is Screen.Count -> listOf("count", s.counter.id, s.counter.name)
            is Screen.History -> listOf("history", s.counter?.id, s.counter?.name)
        }
    },
    restore = { l ->
        when (l[0]) {
            "count" -> Screen.Count(Counter(id = l[1] as Int, name = l[2] as String))
            "history" -> Screen.History(
                if (l[1] != null) Counter(id = l[1] as Int, name = l[2] as String) else null
            )
            "rewards" -> Screen.Rewards
            "meditation" -> Screen.Meditation
            else -> Screen.Home
        }
    }
)

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

@Composable
fun RewardsScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).rewardDao() }
    val scope = rememberCoroutineScope()
    val rewards by dao.observeAll().collectAsState(initial = emptyList())
    var newName by remember { mutableStateOf("") }
    var tier by remember { mutableStateOf(0) }
    val tierNames = listOf("Small", "Medium", "Jackpot")

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text("Rewards", style = MaterialTheme.typography.headlineMedium)
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedTextField(
            value = newName,
            onValueChange = { newName = it },
            label = { Text("New reward") },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            tierNames.forEachIndexed { i, label ->
                FilterChip(
                    selected = tier == i,
                    onClick = { tier = i },
                    label = { Text(label) }
                )
            }
        }
        Button(onClick = {
            if (newName.isNotBlank()) {
                val name = newName.trim()
                val t = tier
                scope.launch { dao.add(Reward(name = name, tier = t)) }
                newName = ""
            }
        }) { Text("Add reward") }

        LazyColumn {
            tierNames.forEachIndexed { i, label ->
                val inTier = rewards.filter { it.tier == i }
                item {
                    Text(
                        label,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)
                    )
                }
                if (inTier.isEmpty()) item { Text("None yet") }
                items(inTier, key = { it.id }) { r ->
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(r.name, modifier = Modifier.weight(1f))
                            IconButton(onClick = { scope.launch { dao.delete(r.id) } }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MeditationScreen(onBack: () -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val view = LocalView.current
    val vibrator = context.getSystemService(Vibrator::class.java)
    val prefs = remember { context.getSharedPreferences("meditation", Context.MODE_PRIVATE) }
    val dayDao = remember { AppDatabase.get(context).meditationDayDao() }
    val dates by dayDao.observeDates().collectAsState(initial = emptyList())
    val streak = computeStreak(dates.map { LocalDate.parse(it) }.toSet(), LocalDate.now())
    val earned = when {
        streak >= 40 -> 3
        streak >= 21 -> 2
        streak >= 7 -> 1
        else -> 0
    }
    var level by remember { mutableStateOf(prefs.getInt("level", 0)) }
    LaunchedEffect(earned) {
        if (earned > level) {
            level = earned
            prefs.edit().putInt("level", earned).apply()
        }
    }
    var minutesText by remember { mutableStateOf(prefs.getString("minutes", "10") ?: "10") }
    var running by remember { mutableStateOf(false) }
    var remaining by remember { mutableStateOf(0) }
    var finished by remember { mutableStateOf(false) }

    // Keep the screen on only while the timer runs
    DisposableEffect(running) {
        view.keepScreenOn = running
        onDispose { view.keepScreenOn = false }
    }

    LaunchedEffect(running) {
        if (running) {
            val total = ((minutesText.toDoubleOrNull() ?: 10.0) * 60).roundToInt().coerceAtLeast(1)
            val endAt = System.currentTimeMillis() + total * 1000L
            remaining = total
            while (remaining > 0) {
                delay(250)
                remaining = ((endAt - System.currentTimeMillis() + 999) / 1000).toInt().coerceAtLeast(0)
            }
            vibrator?.vibrate(VibrationEffect.createOneShot(800, VibrationEffect.DEFAULT_AMPLITUDE))
            dayDao.markDone(MeditationDay(LocalDate.now().toString()))
            finished = true
            running = false
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        IconButton(onClick = onBack) {
            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
        }
        Text("Meditate", style = MaterialTheme.typography.headlineMedium)
        Text("Streak: $streak ${if (streak == 1) "day" else "days"}  ·  Days done: ${dates.size}")
        Text("Level: $level of 3")
        Spacer(modifier = Modifier.height(16.dp))

        OutlinedTextField(
            value = minutesText,
            onValueChange = { minutesText = it },
            label = { Text("Minutes") },
            singleLine = true,
            enabled = !running,
            modifier = Modifier.width(160.dp)
        )
        Spacer(modifier = Modifier.height(24.dp))

        if (running) {
            Text(
                "%d:%02d".format(remaining / 60, remaining % 60),
                fontSize = 64.sp
            )
        } else if (finished) {
            Text("Session complete", style = MaterialTheme.typography.titleLarge)
        }
        Spacer(modifier = Modifier.height(16.dp))

        Button(onClick = {
            if (!running) {
                prefs.edit().putString("minutes", minutesText).apply()
                finished = false
            }
            running = !running

        }) {
        Text(if (running) "Stop" else "Start")
        }

        Spacer(modifier = Modifier.height(16.dp))
        SessionsSection(unlocked = level)
    }
}

@Composable
fun ColumnScope.SessionsSection(unlocked: Int) {
    val context = LocalContext.current
    val dayDao = remember { AppDatabase.get(context).meditationDayDao() }
    val dao = remember { AppDatabase.get(context).trackDao() }
    val scope = rememberCoroutineScope()
    val tracks by dao.observeAll().collectAsState(initial = emptyList())
    var playingId by remember { mutableStateOf<Int?>(null) }
    var player by remember { mutableStateOf<MediaPlayer?>(null) }
    val stop: () -> Unit = {
        player?.release()
        player = null
        playingId = null
    }
    DisposableEffect(Unit) { onDispose { player?.release() } }
    var pickedUri by remember { mutableStateOf<Uri?>(null) }
    var trackName by remember { mutableStateOf("") }
    var level by remember { mutableStateOf(0) }
    val chipLabels = listOf("Start", "7d", "21d", "40d")
    val levelNames = listOf("Starter", "7 days", "21 days", "40 days")

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            trackName = ""
            level = 0
            pickedUri = uri
        }
    }

    pickedUri?.let { uri ->
        AlertDialog(
            onDismissRequest = { pickedUri = null },
            title = { Text("Add session") },
            text = {
                Column {
                    OutlinedTextField(
                        value = trackName,
                        onValueChange = { trackName = it },
                        label = { Text("Session name") },
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("Unlocks at")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        chipLabels.forEachIndexed { i, label ->
                            FilterChip(
                                selected = level == i,
                                onClick = { level = i },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val name = trackName.trim()
                    val lvl = level
                    pickedUri = null
                    if (name.isNotBlank()) {
                        scope.launch {
                            val ok = importTrack(context, uri, name, lvl)
                            if (!ok) {
                                Toast.makeText(context, "Could not import that file", Toast.LENGTH_LONG).show()
                            }
                        }
                    }
                }) { Text("Save") }
            },
            dismissButton = {
                TextButton(onClick = { pickedUri = null }) { Text("Cancel") }
            }
        )
    }

    Text("Sessions", style = MaterialTheme.typography.titleMedium)
    Button(onClick = { picker.launch(arrayOf("audio/*")) }) { Text("Add session") }
    if (tracks.isEmpty()) Text("No sessions yet.")
    LazyColumn(modifier = Modifier.weight(1f)) {
        items(tracks, key = { it.id }) { t ->
            Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f).padding(vertical = 12.dp)) {
                        Text(t.name, style = MaterialTheme.typography.titleMedium)
                        Text(
                            (if (t.requiredLevel > unlocked) "Locked  ·  " else "") +
                                    "%d:%02d  ·  Unlocks at: %s".format(
                                        t.durationSec / 60,
                                        t.durationSec % 60,
                                        levelNames[t.requiredLevel.coerceIn(0, 3)]
                                    )
                        )
                    }

                    IconButton(enabled = t.requiredLevel <= unlocked, onClick = {
                        if (playingId == t.id) {
                            stop()
                        } else {
                            stop()
                            val mp = MediaPlayer()
                            try {
                                mp.setDataSource(t.filePath)
                                mp.prepare()
                                mp.setOnCompletionListener {
                                    stop()
                                    scope.launch { dayDao.markDone(MeditationDay(LocalDate.now().toString())) }
                                    Toast.makeText(context, "Session finished", Toast.LENGTH_SHORT).show()
                                }
                                mp.start()
                                player = mp
                                playingId = t.id
                            } catch (e: Exception) {
                                mp.release()
                                Toast.makeText(context, "Could not play that file", Toast.LENGTH_LONG).show()
                            }
                        }
                    }) {
                        Icon(
                            if (playingId == t.id) Icons.Default.Stop else Icons.Default.PlayArrow,
                            contentDescription = "Play"
                        )
                    }

                    IconButton(onClick = {
                        scope.launch {
                            if (playingId == t.id) stop()
                            withContext(Dispatchers.IO) { File(t.filePath).delete() }
                            dao.delete(t.id)
                        }
                    }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }
    }
}

suspend fun importTrack(context: Context, uri: Uri, name: String, level: Int): Boolean =
    withContext(Dispatchers.IO) {
        val dir = File(context.filesDir, "meditation").apply { mkdirs() }
        val file = File(dir, "track_${System.currentTimeMillis()}")
        try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                file.outputStream().use { output -> input.copyTo(output) }
            } ?: return@withContext false

            val retriever = MediaMetadataRetriever()
            val ms = try {
                retriever.setDataSource(file.path)
                retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull()
            } finally {
                retriever.release()
            }
            if (ms == null || ms <= 0) {
                file.delete()
                return@withContext false
            }

            AppDatabase.get(context).trackDao().add(
                Track(
                    name = name,
                    filePath = file.path,
                    durationSec = (ms / 1000).toInt(),
                    requiredLevel = level
                )
            )
            true
        } catch (e: Exception) {
            file.delete()
            false
        }
    }