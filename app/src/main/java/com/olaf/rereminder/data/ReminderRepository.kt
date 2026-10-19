package com.olaf.rereminder.data

import android.content.Context
import com.olaf.rereminder.model.Reminder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ReminderRepository(private val context: Context) {

    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = false
    }

    private val file: File
        get() = File(context.filesDir, "one_time_reminders.json")

    private val _remindersFlow = MutableStateFlow<List<Reminder>>(emptyList())
    val remindersFlow: StateFlow<List<Reminder>> = _remindersFlow.asStateFlow()

    init {
        loadSync()
    }

    @Synchronized
    private fun loadSync() {
        if (!file.exists()) {
            _remindersFlow.value = emptyList()
            return
        }
        try {
            val content = file.readText()
            _remindersFlow.value = json.decodeFromString<List<Reminder>>(content)
        } catch (e: Exception) {
            e.printStackTrace()
            _remindersFlow.value = emptyList()
        }
    }

    suspend fun getAllReminders(): List<Reminder> = withContext(Dispatchers.IO) {
        loadSync()
        _remindersFlow.value
    }

    suspend fun getReminderById(id: String): Reminder? = withContext(Dispatchers.IO) {
        getAllReminders().find { it.id == id }
    }

    suspend fun saveReminder(reminder: Reminder) = withContext(Dispatchers.IO) {
        val current = getAllReminders().toMutableList()
        val index = current.indexOfFirst { it.id == reminder.id }
        val updated = reminder.copy(modifiedAt = System.currentTimeMillis())
        if (index != -1) {
            current[index] = updated
        } else {
            current.add(updated)
        }
        persist(current)
    }

    suspend fun deleteReminder(id: String) = withContext(Dispatchers.IO) {
        val current = getAllReminders().filter { it.id != id }
        persist(current)
    }

    private fun persist(list: List<Reminder>) {
        try {
            file.writeText(json.encodeToString(list))
            _remindersFlow.value = list
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }
}
