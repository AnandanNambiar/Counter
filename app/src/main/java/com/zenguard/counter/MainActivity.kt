package com.zenguard.counter

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.zenguard.counter.ui.theme.CounterTheme
import kotlinx.coroutines.launch
import java.time.LocalDate

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CounterTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    App(modifier = Modifier.padding(innerPadding))
                }
            }
        }
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
fun TapCounter(counterId: Int, name: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).dailyCountDao() }
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current

    val saved by dao.observeCount(counterId, LocalDate.now().toString())
        .collectAsState(initial = 0)
    val count = saved ?: 0
    val lifetime by dao.observeLifetime(counterId).collectAsState(initial = 0)

    var menuOpen by remember { mutableStateOf(false) }
    var confirm by remember { mutableStateOf<String?>(null) }

    Box(modifier = modifier.fillMaxSize()) {

        // ⋮ menu, top right
        Box(modifier = Modifier.align(Alignment.TopEnd)) {
            Text(
                text = "⋮",
                fontSize = 28.sp,
                modifier = Modifier
                    .clickable { menuOpen = true }
                    .padding(16.dp)
            )
            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
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
            Text(text = name)
            Text("Today: $count  (108 × ${count / 108})")
            Text("Lifetime: $lifetime  (108 × ${lifetime / 108})")

            // Small undo circle
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.secondary)
                    .clickable {
                        if (count > 0) scope.launch {
                            dao.save(DailyCount(counterId, LocalDate.now().toString(), count - 1))
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "↶", fontSize = 28.sp)
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Big tap circle
            Box(
                modifier = Modifier
                    .size(240.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primary)
                    .clickable {
                        scope.launch {
                            dao.save(DailyCount(counterId, LocalDate.now().toString(), count + 1))
                        }
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    },
                contentAlignment = Alignment.Center
            ) {
                Text(text = "$count", fontSize = 56.sp)
            }
        }
    }

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
}

@Composable
fun App(modifier: Modifier = Modifier) {
    var selected by remember { mutableStateOf<Counter?>(null) }
    val current = selected

    BackHandler(enabled = current != null) { selected = null }

    if (current == null) {
        CounterList(onOpen = { selected = it }, modifier = modifier)
    } else {
        TapCounter(current.id, current.name, modifier)
    }
}

@Composable
fun CounterList(onOpen: (Counter) -> Unit, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val dao = remember { AppDatabase.get(context).counterDao() }
    val dailyDao = remember { AppDatabase.get(context).dailyCountDao() }
    val scope = rememberCoroutineScope()
    val today = LocalDate.now().toString()
    val counters by remember(today) { dao.observeSummaries(today) }
        .collectAsState(initial = emptyList())
    var newName by remember { mutableStateOf("") }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
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
        Text("Saved counters: ${counters.size}")

        LazyColumn {
            items(counters, key = { it.id }) { c ->
                var menuOpen by remember { mutableStateOf(false) }
                var renameOpen by remember { mutableStateOf(false) }
                var renameText by remember { mutableStateOf(c.name) }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(
                        modifier = Modifier
                            .weight(1f)
                            .clickable{ onOpen(Counter(id = c.id, name = c.name)) }
                            .padding(vertical = 12.dp)
                    ) {
                        Text(text = c.name, fontSize = 20.sp)
                        Text("Today: ${c.today} (108 × ${c.today / 108})")
                        Text("Lifetime: ${c.lifetime}  (108 × ${c.lifetime / 108})")
                    }
                    Box {
                        Text(
                            text = "⋮",
                            fontSize = 24.sp,
                            modifier = Modifier
                                .clickable { menuOpen = true }
                                .padding(12.dp)
                        )
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
                                        val newName = renameText.trim()
                                        if (newName.isNotBlank()) {
                                            scope.launch { dao.rename(c.id, newName) }
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
    }
}