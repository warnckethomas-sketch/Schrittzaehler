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

    suspend fun importBackupData(backupData: BackupData) {
        saveStepLength(backupData.stepLengthCm)
        backupData.entries.forEach { entry ->
            stepDao.insertOrUpdate(entry)
        }
    }
}
