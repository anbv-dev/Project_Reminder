package com.olaf.rereminder

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.olaf.rereminder.data.ReminderRepository
import com.olaf.rereminder.model.Reminder
import com.olaf.rereminder.scheduler.ReminderScheduler
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {

    private lateinit var repository: ReminderRepository
    private lateinit var scheduler: ReminderScheduler

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        repository = ReminderRepository(applicationContext)
        scheduler = ReminderScheduler(applicationContext)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    val reminders by repository.remindersFlow.collectAsState()
                    val coroutineScope = rememberCoroutineScope()
                    var showDialog by remember { mutableStateOf(false) }

                    Scaffold(
                        topBar = {
                            TopAppBar(
                                title = { Text("Reminder") },
                                colors = TopAppBarDefaults.topAppBarColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    titleContentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                )
                            )
                        },
                        floatingActionButton = {
                            FloatingActionButton(onClick = { showDialog = true }) {
                                Icon(Icons.Default.Add, contentDescription = "Add Reminder")
                            }
                        }
                    ) { innerPadding ->
                        ReminderList(
                            reminders = reminders,
                            modifier = Modifier.padding(innerPadding),
                            onToggle = { reminder, enabled ->
                                coroutineScope.launch {
                                    val updated = reminder.copy(isEnabled = enabled)
                                    repository.saveReminder(updated)
                                    if (enabled) {
                                        scheduler.schedule(updated)
                                    } else {
                                        scheduler.cancel(updated.id)
                                    }
                                }
                            },
                            onDelete = { reminder ->
                                coroutineScope.launch {
                                    scheduler.cancel(reminder.id)
                                    repository.deleteReminder(reminder.id)
                                }
                            }
                        )

                        if (showDialog) {
                            CreateReminderDialog(
                                onDismiss = { showDialog = false },
                                onConfirm = { title, minutesOffset ->
                                    coroutineScope.launch {
                                        val triggerTime = System.currentTimeMillis() + (minutesOffset * 60 * 1000L)
                                        val newReminder = Reminder(
                                            title = title,
                                            scheduledTimeMillis = triggerTime,
                                            isEnabled = true,
                                            isCompleted = false
                                        )
                                        repository.saveReminder(newReminder)
                                        scheduler.schedule(newReminder)
                                        showDialog = false
                                    }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ReminderList(
    reminders: List<Reminder>,
    modifier: Modifier = Modifier,
    onToggle: (Reminder, Boolean) -> Unit,
    onDelete: (Reminder) -> Unit
) {
    if (reminders.isEmpty()) {
        Box(modifier = modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("No one-time reminders. Tap + to set one.")
        }
    } else {
        LazyColumn(
            modifier = modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(reminders, key = { it.id }) { reminder ->
                ReminderItemCard(reminder = reminder, onToggle = onToggle, onDelete = onDelete)
            }
        }
    }
}

@Composable
fun ReminderItemCard(
    reminder: Reminder,
    onToggle: (Reminder, Boolean) -> Unit,
    onDelete: (Reminder) -> Unit
) {
    val dateFormat = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val formattedTime = remember(reminder.scheduledTimeMillis) {
        dateFormat.format(Date(reminder.scheduledTimeMillis))
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = reminder.title,
                    style = MaterialTheme.typography.titleMedium,
                    textDecoration = if (reminder.isCompleted) TextDecoration.LineThrough else TextDecoration.None
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = if (reminder.isCompleted) "Completed" else "Due: $formattedTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (!reminder.isCompleted) {
                Switch(
                    checked = reminder.isEnabled,
                    onCheckedChange = { checked -> onToggle(reminder, checked) }
                )
            }
            IconButton(onClick = { onDelete(reminder) }) {
                Icon(Icons.Default.Delete, contentDescription = "Delete Reminder")
            }
        }
    }
}

@Composable
fun CreateReminderDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, Int) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var minutesText by remember { mutableStateOf("10") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set One-Time Reminder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Reminder Title") },
                    singleLine = true
                )
                OutlinedTextField(
                    value = minutesText,
                    onValueChange = { minutesText = it.filter { char -> char.isDigit() } },
                    label = { Text("Minutes from now") },
                    singleLine = true
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val minutes = minutesText.toIntOrNull() ?: 10
                    if (title.isNotBlank()) {
                        onConfirm(title, minutes)
                    }
                }
            ) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
