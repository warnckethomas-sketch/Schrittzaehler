package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModelProvider
import android.os.Build
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import com.example.data.PreferencesManager
import com.example.data.StepDatabase
import com.example.data.StepRepository
import com.example.ui.StepTrackerDashboard
import com.example.ui.StepViewModel
import com.example.ui.StepViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted: Boolean ->
        if (isGranted) {
            Log.d("MainActivity", "POST_NOTIFICATIONS permission granted")
        } else {
            Log.w("MainActivity", "POST_NOTIFICATIONS permission denied")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Request POST_NOTIFICATIONS permission on Android 13+ (API 33+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                requestPermissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        
        // Initialize Core Dependencies
        val database = StepDatabase.getDatabase(applicationContext)
        val preferencesManager = PreferencesManager(applicationContext)
        val repository = StepRepository(database.stepDao(), preferencesManager, applicationContext)
        
        // Ensure alarm is scheduled if enabled
        if (preferencesManager.alarmEnabled) {
            com.example.data.AlarmHelper.scheduleAlarm(
                applicationContext,
                preferencesManager.alarmHour,
                preferencesManager.alarmMinute
            )
        }
        
        // Initialize ViewModel via factory
        val factory = StepViewModelFactory(repository)
        val viewModel = ViewModelProvider(this, factory)[StepViewModel::class.java]
        
        enableEdgeToEdge()
        setContent {
            val themeVibe by viewModel.themeVibe.collectAsState()
            val themeMode by viewModel.themeMode.collectAsState()

            MyApplicationTheme(
                themeVibe = themeVibe,
                themeMode = themeMode
            ) {
                StepTrackerDashboard(
                    viewModel = viewModel,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}
