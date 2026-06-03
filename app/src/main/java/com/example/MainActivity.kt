package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.ui.Modifier
import androidx.lifecycle.ViewModelProvider
import com.example.data.PreferencesManager
import com.example.data.StepDatabase
import com.example.data.StepRepository
import com.example.ui.StepTrackerDashboard
import com.example.ui.StepViewModel
import com.example.ui.StepViewModelFactory
import com.example.ui.theme.MyApplicationTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize Core Dependencies
        val database = StepDatabase.getDatabase(applicationContext)
        val preferencesManager = PreferencesManager(applicationContext)
        val repository = StepRepository(database.stepDao(), preferencesManager)
        
        // Initialize ViewModel via factory
        val factory = StepViewModelFactory(repository)
        val viewModel = ViewModelProvider(this, factory)[StepViewModel::class.java]
        
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    StepTrackerDashboard(
                        viewModel = viewModel,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}
