package com.example.data

import kotlinx.coroutines.flow.Flow

class StepRepository(
    private val stepDao: StepDao,
    private val preferencesManager: PreferencesManager
) {
    val allEntries: Flow<List<StepEntry>> = stepDao.getAllEntriesFlow()

    suspend fun insertOrUpdate(date: String, steps: Int, remark: String = "") {
        stepDao.insertOrUpdate(StepEntry(date = date, steps = steps, remark = remark))
    }

    suspend fun deleteByDate(date: String) {
        stepDao.deleteByDate(date)
    }

    suspend fun getEntryByDate(date: String): StepEntry? {
        return stepDao.getEntryByDate(date)
    }

    fun getStepLength(): Int {
        return preferencesManager.stepLengthCm
    }

    fun saveStepLength(cm: Int) {
        preferencesManager.stepLengthCm = cm
    }

    fun getStepLengthPerson2(): Int {
        return preferencesManager.stepLengthCmPerson2
    }

    fun saveStepLengthPerson2(cm: Int) {
        preferencesManager.stepLengthCmPerson2 = cm
    }

    fun getSelectedPerson(): String {
        return preferencesManager.selectedPerson
    }

    fun saveSelectedPerson(person: String) {
        preferencesManager.selectedPerson = person
    }

    fun getPerson1Name(): String {
        return preferencesManager.person1Name
    }

    fun savePerson1Name(name: String) {
        preferencesManager.person1Name = name
    }

    fun getPerson2Name(): String {
        return preferencesManager.person2Name
    }

    fun savePerson2Name(name: String) {
        preferencesManager.person2Name = name
    }

    fun isAutoBackupEnabled(): Boolean {
        return preferencesManager.isAutoBackupEnabled
    }

    fun setAutoBackupEnabled(enabled: Boolean) {
        preferencesManager.isAutoBackupEnabled = enabled
    }

    fun getLastBackupTime(): String {
        return preferencesManager.lastBackupTime
    }

    fun saveLastBackupTime(time: String) {
        preferencesManager.lastBackupTime = time
    }

    fun getCustomBackupDirUri(): String {
        return preferencesManager.customBackupDirUri
    }

    fun setCustomBackupDirUri(uri: String) {
        preferencesManager.customBackupDirUri = uri
    }

    fun getCustomBackupFileName(): String {
        return preferencesManager.customBackupFileName
    }

    fun setCustomBackupFileName(name: String) {
        preferencesManager.customBackupFileName = name
    }

    fun isAlarmEnabled(): Boolean {
        return preferencesManager.alarmEnabled
    }

    fun setAlarmEnabled(enabled: Boolean) {
        preferencesManager.alarmEnabled = enabled
    }

    fun getAlarmHour(): Int {
        return preferencesManager.alarmHour
    }

    fun setAlarmHour(hour: Int) {
        preferencesManager.alarmHour = hour
    }

    fun getAlarmMinute(): Int {
        return preferencesManager.alarmMinute
    }

    fun setAlarmMinute(minute: Int) {
        preferencesManager.alarmMinute = minute
    }

    suspend fun importBackupData(backupData: BackupData) {
        saveStepLength(backupData.stepLengthCm)
        backupData.entries.forEach { entry ->
            stepDao.insertOrUpdate(entry)
        }
    }
}
