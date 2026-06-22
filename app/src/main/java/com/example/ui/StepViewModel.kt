package com.example.ui

import android.content.Context
import android.net.Uri
import android.os.Environment
import android.widget.Toast
import androidx.core.content.FileProvider
import android.content.Intent
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.example.data.StepEntry
import com.example.data.StepRepository
import com.example.data.BackupData
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

// Data classes representing reactive UI structures
enum class PeriodType {
    WEEK, MONTH
}

data class WeeklyStats(
    val mondayDateStr: String,
    val daysData: List<DayStepData>, // 7 items (Monday to Sunday)
    val trackedDaysCount: Int,
    val totalSteps: Long,
    val averageSteps: Double,
    val totalDistanceKm: Double
)

data class MonthlyStats(
    val monthDateStr: String,
    val monthLabel: String,
    val daysData: List<DayStepData>, // All days of the month
    val trackedDaysCount: Int,
    val totalSteps: Long,
    val averageSteps: Double,
    val totalDistanceKm: Double
)

data class DayStepData(
    val dateStr: String,
    val label: String, // e.g. "Mo", "Di"
    val steps: Int,
    val distanceKm: Double,
    val remark: String = ""
)

class StepViewModel(private val repository: StepRepository) : ViewModel() {

    private val _isBackupRestoreLoading = MutableStateFlow(false)
    val isBackupRestoreLoading: StateFlow<Boolean> = _isBackupRestoreLoading.asStateFlow()

    private val _isAutoBackupEnabled = MutableStateFlow(repository.isAutoBackupEnabled())
    val isAutoBackupEnabled: StateFlow<Boolean> = _isAutoBackupEnabled.asStateFlow()

    private val _lastBackupTime = MutableStateFlow(repository.getLastBackupTime())
    val lastBackupTime: StateFlow<String> = _lastBackupTime.asStateFlow()

    private val _customBackupDirUri = MutableStateFlow(repository.getCustomBackupDirUri())
    val customBackupDirUri: StateFlow<String> = _customBackupDirUri.asStateFlow()

    private val _customBackupFileName = MutableStateFlow(repository.getCustomBackupFileName())
    val customBackupFileName: StateFlow<String> = _customBackupFileName.asStateFlow()

    fun setAutoBackupEnabled(enabled: Boolean) {
        _isAutoBackupEnabled.value = enabled
        repository.setAutoBackupEnabled(enabled)
    }

    fun setCustomBackupDirUri(uri: String) {
        _customBackupDirUri.value = uri
        repository.setCustomBackupDirUri(uri)
    }

    fun setCustomBackupFileName(name: String) {
        var cleanName = name.trim()
        if (cleanName.isEmpty()) {
            cleanName = "pacertrack_backup.json"
        } else if (!cleanName.endsWith(".json", ignoreCase = true)) {
            cleanName = "$cleanName.json"
        }
        _customBackupFileName.value = cleanName
        repository.setCustomBackupFileName(cleanName)
    }

    private fun getBackupFile(context: Context): File {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS)
            ?: context.filesDir
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return File(dir, "pacertrack_backup.json")
    }

    fun getBackupFilePath(context: Context): String {
        return getBackupFile(context).absolutePath
    }

    fun getBackupFileDirectory(context: Context): String {
        val dir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        return dir.absolutePath
    }

    fun backupToLocalFile(context: Context, showToast: Boolean = true) {
        viewModelScope.launch {
            _isBackupRestoreLoading.value = true
            try {
                val rootJson = buildAllUsersBackupJson()

                val customDir = _customBackupDirUri.value
                val customName = _customBackupFileName.value
                
                if (customDir.isNotEmpty()) {
                    val treeUri = Uri.parse(customDir)
                    val dirDoc = DocumentFile.fromTreeUri(context, treeUri)
                    if (dirDoc != null && dirDoc.exists()) {
                        var fileDoc = dirDoc.findFile(customName)
                        if (fileDoc == null) {
                            fileDoc = dirDoc.createFile("application/json", customName)
                        }
                        if (fileDoc != null) {
                            context.contentResolver.openOutputStream(fileDoc.uri)?.use { os ->
                                os.write(rootJson.toString(2).toByteArray(Charsets.UTF_8))
                            }
                        } else {
                            throw Exception("Konnte Datei '$customName' nicht anlegen.")
                        }
                    } else {
                        throw Exception("Gewählter Ordner ist nicht mehr erreichbar.")
                    }
                } else {
                    val fallbackFile = getBackupFile(context)
                    val backupFile = File(fallbackFile.parentFile, customName)
                    backupFile.writeText(rootJson.toString(2), Charsets.UTF_8)
                }
                
                val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                val timeStr = sdf.format(Date())
                repository.saveLastBackupTime(timeStr)
                _lastBackupTime.value = timeStr
                
                if (showToast) {
                    Toast.makeText(context, "Sicherung erfolgreich erstellt!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                if (showToast) {
                    Toast.makeText(context, "Sicherung fehlgeschlagen: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                }
                e.printStackTrace()
            } finally {
                _isBackupRestoreLoading.value = false
            }
        }
    }

    fun restoreFromLocalFile(context: Context) {
        viewModelScope.launch {
            _isBackupRestoreLoading.value = true
            try {
                val customDir = _customBackupDirUri.value
                val customName = _customBackupFileName.value
                val jsonStr: String

                if (customDir.isNotEmpty()) {
                    val treeUri = Uri.parse(customDir)
                    val dirDoc = DocumentFile.fromTreeUri(context, treeUri)
                    if (dirDoc != null && dirDoc.exists()) {
                        val fileDoc = dirDoc.findFile(customName)
                        if (fileDoc != null && fileDoc.exists()) {
                            jsonStr = context.contentResolver.openInputStream(fileDoc.uri)?.use { inputStream ->
                                inputStream.bufferedReader().use { it.readText() }
                            } ?: throw Exception("Konnte '$customName' nicht lesen.")
                        } else {
                            throw Exception("Datei '$customName' existiert nicht im gewählten Ordner.")
                        }
                    } else {
                        throw Exception("Gewählter Ordner ist nicht erreichbar.")
                    }
                } else {
                    val fallbackFile = getBackupFile(context)
                    val backupFile = File(fallbackFile.parentFile, customName)
                    if (!backupFile.exists()) {
                        Toast.makeText(context, "Keine Sicherungsdatei '$customName' im Standard-Ordner gefunden!", Toast.LENGTH_LONG).show()
                        return@launch
                    }
                    jsonStr = backupFile.readText(Charsets.UTF_8)
                }
                
                val success = importFromJsonString(context, jsonStr)
                if (success) {
                    Toast.makeText(context, "Daten erfolgreich geladen!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Wiederherstellung fehlgeschlagen: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            } finally {
                _isBackupRestoreLoading.value = false
            }
        }
    }

    fun shareBackupFile(context: Context) {
        viewModelScope.launch {
            try {
                val customDir = _customBackupDirUri.value
                val customName = _customBackupFileName.value
                val uri: Uri

                if (customDir.isNotEmpty()) {
                    val treeUri = Uri.parse(customDir)
                    val dirDoc = DocumentFile.fromTreeUri(context, treeUri)
                    val fileDoc = dirDoc?.findFile(customName)
                    if (fileDoc != null && fileDoc.exists()) {
                        uri = fileDoc.uri
                    } else {
                        throw Exception("Datei '$customName' existiert nicht. Bitte führe zuerst ein Backup durch.")
                    }
                } else {
                    val fallbackFile = getBackupFile(context)
                    val backupFile = File(fallbackFile.parentFile, customName)
                    
                    // Write current data first
                    val rootJson = buildAllUsersBackupJson()
                    backupFile.writeText(rootJson.toString(2), Charsets.UTF_8)

                    uri = FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        backupFile
                    )
                }

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/json"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                val chooser = Intent.createChooser(intent, "Sicherungsdatei teilen")
                chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(chooser)
            } catch (e: Exception) {
                Toast.makeText(context, "Teilen fehlgeschlagen: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
            }
        }
    }

    fun triggerAutoBackup(context: Context) {
        if (!_isAutoBackupEnabled.value) return

        viewModelScope.launch {
            try {
                val rootJson = buildAllUsersBackupJson()

                val customDir = _customBackupDirUri.value
                val customName = _customBackupFileName.value

                if (customDir.isNotEmpty()) {
                    val treeUri = Uri.parse(customDir)
                    val dirDoc = DocumentFile.fromTreeUri(context, treeUri)
                    if (dirDoc != null && dirDoc.exists()) {
                        var fileDoc = dirDoc.findFile(customName)
                        if (fileDoc == null) {
                            fileDoc = dirDoc.createFile("application/json", customName)
                        }
                        if (fileDoc != null) {
                            context.contentResolver.openOutputStream(fileDoc.uri)?.use { os ->
                                os.write(rootJson.toString(2).toByteArray(Charsets.UTF_8))
                            }
                        }
                    }
                } else {
                    val fallbackFile = getBackupFile(context)
                    val backupFile = File(fallbackFile.parentFile, customName)
                    backupFile.writeText(rootJson.toString(2), Charsets.UTF_8)
                }
                
                val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm:ss", Locale.getDefault())
                val timeStr = sdf.format(Date())
                repository.saveLastBackupTime(timeStr)
                _lastBackupTime.value = timeStr
                android.util.Log.d("Backup", "Automatisches Backup erfolgreich um $timeStr")
            } catch (e: Exception) {
                android.util.Log.e("Backup", "Automatisches Backup fehlerhaft", e)
            }
        }
    }

    private suspend fun importFromJsonString(context: Context, jsonStr: String): Boolean {
        if (jsonStr.trim().isEmpty()) {
            Toast.makeText(context, "Sicherungsdatei ist leer!", Toast.LENGTH_LONG).show()
            return false
        }
        
        var importedStepLength: Int? = null
        var importedStepLengthP1: Int? = null
        var importedStepLengthP2: Int? = null
        var importedPerson1Name: String? = null
        var importedPerson2Name: String? = null
        var importedSelectedPerson: String? = null
        val importedEntries = mutableListOf<StepEntry>()
        
        if (jsonStr.trim().startsWith("{")) {
            val rootJson = JSONObject(jsonStr)
            
            if (rootJson.has("stepLengthCmPerson1")) {
                importedStepLengthP1 = rootJson.getInt("stepLengthCmPerson1")
            }
            if (rootJson.has("stepLengthCmPerson2")) {
                importedStepLengthP2 = rootJson.getInt("stepLengthCmPerson2")
            }
            if (rootJson.has("person1Name")) {
                importedPerson1Name = rootJson.getString("person1Name")
            }
            if (rootJson.has("person2Name")) {
                importedPerson2Name = rootJson.getString("person2Name")
            }
            if (rootJson.has("selectedPerson")) {
                importedSelectedPerson = rootJson.getString("selectedPerson")
            }

            if (rootJson.has("stepLengthCm")) {
                importedStepLength = rootJson.getInt("stepLengthCm")
            }
            if (rootJson.has("entries")) {
                val array = rootJson.getJSONArray("entries")
                for (i in 0 until array.length()) {
                    val obj = array.getJSONObject(i)
                    val date = obj.getString("date")
                    val steps = obj.getInt("steps")
                    val remark = obj.optString("remark", "")
                    importedEntries.add(StepEntry(date, steps, remark))
                }
            } else {
                val keys = rootJson.keys()
                val excludes = setOf(
                    "stepLengthCm", "stepLengthCmPerson1", "stepLengthCmPerson2",
                    "person1Name", "person2Name", "selectedPerson", "version"
                )
                while (keys.hasNext()) {
                    val key = keys.next()
                    if (key !in excludes) {
                        val valObj = rootJson.optJSONObject(key)
                        if (valObj != null && valObj.has("steps")) {
                            val remark = valObj.optString("remark", "")
                            importedEntries.add(StepEntry(key, valObj.getInt("steps"), remark))
                        } else {
                            val stepsVal = rootJson.optInt(key, -1)
                            if (stepsVal >= 0) {
                                importedEntries.add(StepEntry(key, stepsVal, ""))
                            }
                        }
                    }
                }
            }
        } else if (jsonStr.trim().startsWith("[")) {
            val array = JSONArray(jsonStr)
            for (i in 0 until array.length()) {
                val obj = array.getJSONObject(i)
                val date = obj.getString("date")
                val steps = obj.getInt("steps")
                val remark = obj.optString("remark", "")
                importedEntries.add(StepEntry(date, steps, remark))
            }
        } else {
            Toast.makeText(context, "Ungültiges Dateiformat!", Toast.LENGTH_LONG).show()
            return false
        }
        
        if (importedEntries.isEmpty() && importedStepLength == null && importedStepLengthP1 == null) {
            Toast.makeText(context, "Keine lesbaren Daten in der Datei gefunden!", Toast.LENGTH_LONG).show()
            return false
        }
        
        importedPerson1Name?.let { updatePerson1Name(it) }
        importedPerson2Name?.let { updatePerson2Name(it) }
        importedStepLengthP1?.let { updateStepLengthPerson1(it) }
        importedStepLengthP2?.let { updateStepLengthPerson2(it) }
        importedSelectedPerson?.let { selectPerson(it) }

        if (importedStepLengthP1 == null && importedStepLength != null) {
            updateStepLength(importedStepLength)
        }
        
        val activePerson = _selectedPerson.value
        importedEntries.forEach { entry ->
            val dbKey = resolveDbKey(entry.date, activePerson)
            repository.insertOrUpdate(dbKey, entry.steps, entry.remark)
        }
        
        return true
    }

    private val _selectedPerson = MutableStateFlow(repository.getSelectedPerson())
    val selectedPerson: StateFlow<String> = _selectedPerson.asStateFlow()

    private val _person1Name = MutableStateFlow(repository.getPerson1Name())
    val person1Name: StateFlow<String> = _person1Name.asStateFlow()

    private val _person2Name = MutableStateFlow(repository.getPerson2Name())
    val person2Name: StateFlow<String> = _person2Name.asStateFlow()

    private val _stepLengthCmPerson1 = MutableStateFlow(repository.getStepLength())
    val stepLengthCmPerson1: StateFlow<Int> = _stepLengthCmPerson1.asStateFlow()

    private val _stepLengthCmPerson2 = MutableStateFlow(repository.getStepLengthPerson2())
    val stepLengthCmPerson2: StateFlow<Int> = _stepLengthCmPerson2.asStateFlow()

    // Derived step length of the currently selected person
    val stepLengthCm: StateFlow<Int> = combine(
        _selectedPerson,
        _stepLengthCmPerson1,
        _stepLengthCmPerson2
    ) { person, len1, len2 ->
        if (person == "person_2") len2 else len1
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = repository.getStepLength()
    )

    // Derived step length of the inactive person
    val inactiveStepLengthCm: StateFlow<Int> = combine(
        _selectedPerson,
        _stepLengthCmPerson1,
        _stepLengthCmPerson2
    ) { person, len1, len2 ->
        if (person == "person_2") len1 else len2
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = repository.getStepLengthPerson2()
    )

    private val _selectedWeekMonday = MutableStateFlow(DateUtils.getMondayOfWeek(DateUtils.getTodayString()))
    val selectedWeekMonday: StateFlow<String> = _selectedWeekMonday.asStateFlow()

    private val _activePeriodType = MutableStateFlow(PeriodType.WEEK)
    val activePeriodType: StateFlow<PeriodType> = _activePeriodType.asStateFlow()

    // Flow of all records, filtered by active person, with standard keys
    val allEntries: StateFlow<List<StepEntry>> = combine(
        repository.allEntries,
        _selectedPerson
    ) { entries, person ->
        if (person == "person_2") {
            entries.filter { it.date.startsWith("person_2|") }
                .map { it.copy(date = it.date.substringAfter("person_2|")) }
        } else {
            entries.filter { !it.date.startsWith("person_2|") && !it.date.startsWith("person_1|") }
                .map {
                    if (it.date.startsWith("person_1|")) {
                        it.copy(date = it.date.substringAfter("person_1|"))
                    } else {
                        it
                    }
                }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Flow of all records, filtered by inactive person, with standard keys
    val inactiveEntries: StateFlow<List<StepEntry>> = combine(
        repository.allEntries,
        _selectedPerson
    ) { entries, person ->
        val inactivePerson = if (person == "person_2") "person_1" else "person_2"
        if (inactivePerson == "person_2") {
            entries.filter { it.date.startsWith("person_2|") }
                .map { it.copy(date = it.date.substringAfter("person_2|")) }
        } else {
            entries.filter { !it.date.startsWith("person_2|") && !it.date.startsWith("person_1|") }
                .map {
                    if (it.date.startsWith("person_1|")) {
                        it.copy(date = it.date.substringAfter("person_1|"))
                    } else {
                        it
                    }
                }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Unfiltered raw entries for complete backup/restore across both persons
    val rawAllEntries: StateFlow<List<StepEntry>> = repository.allEntries
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private fun getDbKeyForDate(date: String, personId: String): String {
        return if (personId == "person_2") {
            "person_2|$date"
        } else {
            date
        }
    }

    fun selectPerson(personId: String) {
        if (personId == "person_1" || personId == "person_2") {
            _selectedPerson.value = personId
            repository.saveSelectedPerson(personId)
        }
    }

    fun updatePerson1Name(name: String) {
        val trimmed = name.trim()
        val displayName = if (trimmed.isEmpty()) "Person 1" else trimmed
        _person1Name.value = displayName
        repository.savePerson1Name(displayName)
    }

    fun updatePerson2Name(name: String) {
        val trimmed = name.trim()
        val displayName = if (trimmed.isEmpty()) "Person 2" else trimmed
        _person2Name.value = displayName
        repository.savePerson2Name(displayName)
    }

    // Reactive aggregate metrics computed automatically for week
    val weeklyStats: StateFlow<WeeklyStats> = combine(
        allEntries,
        _selectedWeekMonday,
        stepLengthCm
    ) { entries, monday, length ->
        val daysList = DateUtils.getDaysOfWeekList(monday)
        val entriesMap = entries.associateBy { it.date }

        val daysData = daysList.map { dateStr ->
            val entry = entriesMap[dateStr]
            val steps = entry?.steps ?: 0
            val distanceKm = (steps.toLong() * length) / 100000.0
            DayStepData(
                dateStr = dateStr,
                label = DateUtils.getDayOfWeekLabel(dateStr),
                steps = steps,
                distanceKm = distanceKm,
                remark = entry?.remark ?: ""
            )
        }

        val loggedDays = daysData.filter { it.steps > 0 }
        val trackedDaysCount = loggedDays.size
        val totalSteps = daysData.sumOf { it.steps.toLong() }
        val averageSteps = if (trackedDaysCount > 0) totalSteps.toDouble() / trackedDaysCount else 0.0
        val totalDistanceKm = (totalSteps * length) / 100000.0

        WeeklyStats(
            mondayDateStr = monday,
            daysData = daysData,
            trackedDaysCount = trackedDaysCount,
            totalSteps = totalSteps,
            averageSteps = averageSteps,
            totalDistanceKm = totalDistanceKm
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WeeklyStats(
            mondayDateStr = DateUtils.getMondayOfWeek(DateUtils.getTodayString()),
            daysData = emptyList(),
            trackedDaysCount = 0,
            totalSteps = 0,
            averageSteps = 0.0,
            totalDistanceKm = 0.0
        )
    )

    // Reactive aggregate metrics computed automatically for month
    val monthlyStats: StateFlow<MonthlyStats> = combine(
        allEntries,
        _selectedWeekMonday,
        stepLengthCm
    ) { entries, monday, length ->
        val daysList = DateUtils.getDaysOfMonthList(monday)
        val entriesMap = entries.associateBy { it.date }

        val daysData = daysList.map { dateStr ->
            val entry = entriesMap[dateStr]
            val steps = entry?.steps ?: 0
            val distanceKm = (steps.toLong() * length) / 100000.0
            DayStepData(
                dateStr = dateStr,
                label = DateUtils.getDayOfWeekLabel(dateStr),
                steps = steps,
                distanceKm = distanceKm,
                remark = entry?.remark ?: ""
            )
        }

        val loggedDays = daysData.filter { it.steps > 0 }
        val trackedDaysCount = loggedDays.size
        val totalSteps = daysData.sumOf { it.steps.toLong() }
        val averageSteps = if (trackedDaysCount > 0) totalSteps.toDouble() / trackedDaysCount else 0.0
        val totalDistanceKm = (totalSteps * length) / 100000.0
        val monthLabel = DateUtils.getMonthLabel(monday)

        MonthlyStats(
            monthDateStr = if (daysList.isNotEmpty()) daysList.first() else monday,
            monthLabel = monthLabel,
            daysData = daysData,
            trackedDaysCount = trackedDaysCount,
            totalSteps = totalSteps,
            averageSteps = averageSteps,
            totalDistanceKm = totalDistanceKm
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MonthlyStats(
            monthDateStr = "",
            monthLabel = "",
            daysData = emptyList(),
            trackedDaysCount = 0,
            totalSteps = 0,
            averageSteps = 0.0,
            totalDistanceKm = 0.0
        )
    )

    // Reactive aggregate metrics computed automatically for inactive person's week
    val inactiveWeeklyStats: StateFlow<WeeklyStats> = combine(
        inactiveEntries,
        _selectedWeekMonday,
        inactiveStepLengthCm
    ) { entries, monday, length ->
        val daysList = DateUtils.getDaysOfWeekList(monday)
        val entriesMap = entries.associateBy { it.date }

        val daysData = daysList.map { dateStr ->
            val entry = entriesMap[dateStr]
            val steps = entry?.steps ?: 0
            val distanceKm = (steps.toLong() * length) / 100000.0
            DayStepData(
                dateStr = dateStr,
                label = DateUtils.getDayOfWeekLabel(dateStr),
                steps = steps,
                distanceKm = distanceKm,
                remark = entry?.remark ?: ""
            )
        }

        val loggedDays = daysData.filter { it.steps > 0 }
        val trackedDaysCount = loggedDays.size
        val totalSteps = daysData.sumOf { it.steps.toLong() }
        val averageSteps = if (trackedDaysCount > 0) totalSteps.toDouble() / trackedDaysCount else 0.0
        val totalDistanceKm = (totalSteps * length) / 100000.0

        WeeklyStats(
            mondayDateStr = monday,
            daysData = daysData,
            trackedDaysCount = trackedDaysCount,
            totalSteps = totalSteps,
            averageSteps = averageSteps,
            totalDistanceKm = totalDistanceKm
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = WeeklyStats(
            mondayDateStr = DateUtils.getMondayOfWeek(DateUtils.getTodayString()),
            daysData = emptyList(),
            trackedDaysCount = 0,
            totalSteps = 0,
            averageSteps = 0.0,
            totalDistanceKm = 0.0
        )
    )

    // Reactive aggregate metrics computed automatically for inactive person's month
    val inactiveMonthlyStats: StateFlow<MonthlyStats> = combine(
        inactiveEntries,
        _selectedWeekMonday,
        inactiveStepLengthCm
    ) { entries, monday, length ->
        val daysList = DateUtils.getDaysOfMonthList(monday)
        val entriesMap = entries.associateBy { it.date }

        val daysData = daysList.map { dateStr ->
            val entry = entriesMap[dateStr]
            val steps = entry?.steps ?: 0
            val distanceKm = (steps.toLong() * length) / 100000.0
            DayStepData(
                dateStr = dateStr,
                label = DateUtils.getDayOfWeekLabel(dateStr),
                steps = steps,
                distanceKm = distanceKm,
                remark = entry?.remark ?: ""
            )
        }

        val loggedDays = daysData.filter { it.steps > 0 }
        val trackedDaysCount = loggedDays.size
        val totalSteps = daysData.sumOf { it.steps.toLong() }
        val averageSteps = if (trackedDaysCount > 0) totalSteps.toDouble() / trackedDaysCount else 0.0
        val totalDistanceKm = (totalSteps * length) / 100000.0
        val monthLabel = DateUtils.getMonthLabel(monday)

        MonthlyStats(
            monthDateStr = if (daysList.isNotEmpty()) daysList.first() else monday,
            monthLabel = monthLabel,
            daysData = daysData,
            trackedDaysCount = trackedDaysCount,
            totalSteps = totalSteps,
            averageSteps = averageSteps,
            totalDistanceKm = totalDistanceKm
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = MonthlyStats(
            monthDateStr = "",
            monthLabel = "",
            daysData = emptyList(),
            trackedDaysCount = 0,
            totalSteps = 0,
            averageSteps = 0.0,
            totalDistanceKm = 0.0
        )
    )

    fun setPeriodType(type: PeriodType) {
        _activePeriodType.value = type
    }

    fun navigateToPreviousWeek() {
        _selectedWeekMonday.value = DateUtils.getPreviousWeekMonday(_selectedWeekMonday.value)
    }

    fun navigateToNextWeek() {
        _selectedWeekMonday.value = DateUtils.getNextWeekMonday(_selectedWeekMonday.value)
    }

    fun navigateToPreviousMonth() {
        _selectedWeekMonday.value = DateUtils.getPreviousMonthMonday(_selectedWeekMonday.value)
    }

    fun navigateToNextMonth() {
        _selectedWeekMonday.value = DateUtils.getNextMonthMonday(_selectedWeekMonday.value)
    }

    fun navigateToCurrentWeek() {
        _selectedWeekMonday.value = DateUtils.getMondayOfWeek(DateUtils.getTodayString())
    }

    fun updateStepLength(cm: Int) {
        if (cm in 1..300) {
            if (_selectedPerson.value == "person_2") {
                _stepLengthCmPerson2.value = cm
                repository.saveStepLengthPerson2(cm)
            } else {
                _stepLengthCmPerson1.value = cm
                repository.saveStepLength(cm)
            }
        }
    }

    fun updateStepLengthPerson1(cm: Int) {
        if (cm in 1..300) {
            _stepLengthCmPerson1.value = cm
            repository.saveStepLength(cm)
        }
    }

    fun updateStepLengthPerson2(cm: Int) {
        if (cm in 1..300) {
            _stepLengthCmPerson2.value = cm
            repository.saveStepLengthPerson2(cm)
        }
    }

    fun saveSteps(date: String, steps: Int, remark: String = "", personId: String? = null) {
        viewModelScope.launch {
            val targetPerson = personId ?: _selectedPerson.value
            val dbKey = getDbKeyForDate(date, targetPerson)
            repository.insertOrUpdate(dbKey, steps, remark)
        }
    }

    fun deleteSteps(date: String) {
        viewModelScope.launch {
            val dbKey = getDbKeyForDate(date, _selectedPerson.value)
            repository.deleteByDate(dbKey)
        }
    }

    fun quickAddTodaySteps(amount: Int) {
        viewModelScope.launch {
            val today = DateUtils.getTodayString()
            val todayEntry = allEntries.value.find { it.date == today }
            val currentSteps = todayEntry?.steps ?: 0
            val currentRemark = todayEntry?.remark ?: ""
            val newSteps = currentSteps + amount
            val dbKey = getDbKeyForDate(today, _selectedPerson.value)
            repository.insertOrUpdate(dbKey, newSteps, currentRemark)
        }
    }

    fun generateDemoWeekData(context: Context) {
        viewModelScope.launch {
            try {
                val monday = _selectedWeekMonday.value
                val days = DateUtils.getDaysOfWeekList(monday)
                val randomSteps = listOf(6200, 8450, 7120, 10300, 5800, 9150, 11400)
                days.forEachIndexed { index, dateStr ->
                    val steps = randomSteps.getOrElse(index) { 7500 }
                    val dbKey = getDbKeyForDate(dateStr, _selectedPerson.value)
                    repository.insertOrUpdate(dbKey, steps)
                }
                Toast.makeText(context, "Muster-Woche erfolgreich eingetragen!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Fehler beim Erstellen der Musterdaten: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    fun exportDataToUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val rootJson = buildAllUsersBackupJson()
                
                context.contentResolver.openOutputStream(uri)?.use { os ->
                    os.write(rootJson.toString(2).toByteArray(Charsets.UTF_8))
                }
                
                Toast.makeText(context, "Sicherung erfolgreich erstellt!", Toast.LENGTH_LONG).show()
            } catch (e: Exception) {
                Toast.makeText(context, "Fehler beim Exportieren: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    fun importDataFromUri(context: Context, uri: Uri) {
        viewModelScope.launch {
            try {
                val content = StringBuilder()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            content.append(line)
                        }
                    }
                }
                
                val jsonStr = content.toString()
                if (jsonStr.trim().isEmpty()) {
                    Toast.makeText(context, "Sicherungsdatei ist leer!", Toast.LENGTH_LONG).show()
                    return@launch
                }
                
                val success = importFromJsonString(context, jsonStr)
                if (success) {
                    Toast.makeText(context, "Daten erfolgreich wiederhergestellt!", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                Toast.makeText(context, "Wiederherstellung fehlgeschlagen: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                e.printStackTrace()
            }
        }
    }

    private fun resolveDbKey(entryDate: String, activePerson: String): String {
        if (entryDate.startsWith("person_2|") || entryDate.startsWith("person_1|")) {
            return entryDate
        }
        return getDbKeyForDate(entryDate, activePerson)
    }

    private suspend fun buildAllUsersBackupJson(): JSONObject {
        val rawEntries = repository.allEntries.first()
        val stepLength = stepLengthCm.value
        val stepLengthP1 = _stepLengthCmPerson1.value
        val stepLengthP2 = _stepLengthCmPerson2.value
        val name1 = _person1Name.value
        val name2 = _person2Name.value
        val selPerson = _selectedPerson.value

        return JSONObject().apply {
            put("version", 2)
            put("stepLengthCm", stepLength)
            put("stepLengthCmPerson1", stepLengthP1)
            put("stepLengthCmPerson2", stepLengthP2)
            put("person1Name", name1)
            put("person2Name", name2)
            put("selectedPerson", selPerson)

            val entriesArray = JSONArray()
            rawEntries.forEach { entry ->
                val entryObj = JSONObject().apply {
                    put("date", entry.date)
                    put("steps", entry.steps)
                    put("remark", entry.remark)
                }
                entriesArray.put(entryObj)
            }
            put("entries", entriesArray)
        }
    }
}

// Simple date processing tools
object DateUtils {
    private fun getDateFormat(): SimpleDateFormat {
        return SimpleDateFormat("yyyy-MM-dd", Locale.GERMANY)
    }

    fun getTodayString(): String {
        return getDateFormat().format(Date())
    }

    fun getMondayOfWeek(dateStr: String): String {
        val sdf = getDateFormat()
        return try {
            val date = sdf.parse(dateStr) ?: Date()
            val cal = Calendar.getInstance(Locale.GERMANY)
            cal.time = date
            cal.firstDayOfWeek = Calendar.MONDAY
            
            // Loop backward to find Monday
            while (cal.get(Calendar.DAY_OF_WEEK) != Calendar.MONDAY) {
                cal.add(Calendar.DAY_OF_YEAR, -1)
            }
            sdf.format(cal.time)
        } catch (e: Exception) {
            dateStr
        }
    }

    fun getDaysOfWeekList(mondayStr: String): List<String> {
        val sdf = getDateFormat()
        val list = mutableListOf<String>()
        try {
            val monday = sdf.parse(mondayStr) ?: Date()
            val cal = Calendar.getInstance(Locale.GERMANY)
            cal.time = monday
            for (i in 0 until 7) {
                list.add(sdf.format(cal.time))
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        } catch (e: java.lang.Exception) {
            for (i in 0 until 7) {
                list.add(mondayStr)
            }
        }
        return list
    }

    fun getPreviousWeekMonday(mondayStr: String): String {
        val sdf = getDateFormat()
        return try {
            val date = sdf.parse(mondayStr) ?: Date()
            val cal = Calendar.getInstance(Locale.GERMANY)
            cal.time = date
            cal.add(Calendar.DAY_OF_YEAR, -7)
            sdf.format(cal.time)
        } catch (e: java.lang.Exception) {
            mondayStr
        }
    }

    fun getNextWeekMonday(mondayStr: String): String {
        val sdf = getDateFormat()
        return try {
            val date = sdf.parse(mondayStr) ?: Date()
            val cal = Calendar.getInstance(Locale.GERMANY)
            cal.time = date
            cal.add(Calendar.DAY_OF_YEAR, 7)
            sdf.format(cal.time)
        } catch (e: java.lang.Exception) {
            mondayStr
        }
    }

    fun formatGermanDate(dateStr: String): String {
        val sdf = getDateFormat()
        return try {
            val date = sdf.parse(dateStr) ?: Date()
            val outputFormat = SimpleDateFormat("dd.MM.yyyy", Locale.GERMANY)
            outputFormat.format(date)
        } catch (e: java.lang.Exception) {
            dateStr
        }
    }

    fun getDayOfWeekLabel(dateStr: String): String {
        val sdf = getDateFormat()
        return try {
            val date = sdf.parse(dateStr) ?: Date()
            val outputFormat = SimpleDateFormat("EE", Locale.GERMANY) // e.g. "Mo", "Di"
            outputFormat.format(date)
        } catch (e: java.lang.Exception) {
            ""
        }
    }

    fun getDaysOfMonthList(dateStr: String): List<String> {
        val sdf = getDateFormat()
        val list = mutableListOf<String>()
        try {
            val date = sdf.parse(dateStr) ?: Date()
            val cal = Calendar.getInstance(Locale.GERMANY)
            cal.time = date
            cal.set(Calendar.DAY_OF_MONTH, 1)
            val month = cal.get(Calendar.MONTH)
            while (cal.get(Calendar.MONTH) == month) {
                list.add(sdf.format(cal.time))
                cal.add(Calendar.DAY_OF_YEAR, 1)
            }
        } catch (e: java.lang.Exception) {
            // fallback
        }
        return list
    }

    fun getMonthLabel(dateStr: String): String {
        val sdf = getDateFormat()
        return try {
            val date = sdf.parse(dateStr) ?: Date()
            val outputFormat = SimpleDateFormat("MMMM yyyy", Locale.GERMANY)
            outputFormat.format(date)
        } catch (e: java.lang.Exception) {
            ""
        }
    }

    fun getPreviousMonthMonday(dateStr: String): String {
        val sdf = getDateFormat()
        return try {
            val date = sdf.parse(dateStr) ?: Date()
            val cal = Calendar.getInstance(Locale.GERMANY)
            cal.time = date
            cal.add(Calendar.MONTH, -1)
            getMondayOfWeek(sdf.format(cal.time))
        } catch (e: java.lang.Exception) {
            dateStr
        }
    }

    fun getNextMonthMonday(dateStr: String): String {
        val sdf = getDateFormat()
        return try {
            val date = sdf.parse(dateStr) ?: Date()
            val cal = Calendar.getInstance(Locale.GERMANY)
            cal.time = date
            cal.add(Calendar.MONTH, 1)
            getMondayOfWeek(sdf.format(cal.time))
        } catch (e: java.lang.Exception) {
            dateStr
        }
    }
}

// Manual ViewModel Factory
class StepViewModelFactory(private val repository: StepRepository) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(StepViewModel::class.java)) {
            @Suppress("UNCHECKED_CAST")
            return StepViewModel(repository) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
