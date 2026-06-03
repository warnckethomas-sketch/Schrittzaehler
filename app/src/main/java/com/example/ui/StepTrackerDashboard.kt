package com.example.ui

import android.app.DatePickerDialog
import android.content.Intent
import android.net.Uri
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import java.text.SimpleDateFormat
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.foundation.lazy.rememberLazyListState
import kotlinx.coroutines.launch
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupProperties
import androidx.compose.ui.unit.IntOffset
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.data.StepEntry
import android.app.Activity
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepTrackerDashboard(
    viewModel: StepViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val weeklyStats by viewModel.weeklyStats.collectAsStateWithLifecycle()
    val monthlyStats by viewModel.monthlyStats.collectAsStateWithLifecycle()
    val activePeriodType by viewModel.activePeriodType.collectAsStateWithLifecycle()
    val stepLengthCm by viewModel.stepLengthCm.collectAsStateWithLifecycle()
    val selectedWeekMonday by viewModel.selectedWeekMonday.collectAsStateWithLifecycle()
    val allEntries by viewModel.allEntries.collectAsStateWithLifecycle()

    // Dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var dialogInitialDate by remember { mutableStateOf("") }
    var dialogInitialSteps by remember { mutableStateOf("") }
    var dialogInitialRemark by remember { mutableStateOf("") }
    var showStepLengthConfig by remember { mutableStateOf(false) }
    var showExitConfirmationDialog by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0 = Dashboard, 1 = Historie & Backup
    val listState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    val isAutoBackupEnabled by viewModel.isAutoBackupEnabled.collectAsStateWithLifecycle()
    val lastBackupTime by viewModel.lastBackupTime.collectAsStateWithLifecycle()

    var isInitialLoad by remember { mutableStateOf(true) }

    LaunchedEffect(allEntries, stepLengthCm, isAutoBackupEnabled) {
        if (isInitialLoad) {
            isInitialLoad = false
            return@LaunchedEffect
        }
        if (isAutoBackupEnabled) {
            kotlinx.coroutines.delay(2000)
            viewModel.triggerAutoBackup(context)
        }
    }

    // Selected day state (for detailing the tapped bar)
    val todayStr = remember { DateUtils.getTodayString() }
    val currentPeriodDays = if (activePeriodType == PeriodType.WEEK) weeklyStats.daysData else monthlyStats.daysData

    var selectedDayDateStr by remember(activePeriodType, weeklyStats.mondayDateStr, monthlyStats.monthLabel) { 
        mutableStateOf(
            if (currentPeriodDays.any { it.dateStr == todayStr }) todayStr 
            else currentPeriodDays.firstOrNull()?.dateStr ?: ""
        )
    }

    var activelyClickedDateStr by remember(activePeriodType, weeklyStats.mondayDateStr, monthlyStats.monthLabel) {
        mutableStateOf<String?>(null)
    }

    val selectedDayData = currentPeriodDays.find { it.dateStr == selectedDayDateStr }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        uri?.let {
            viewModel.exportDataToUri(context, it)
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importDataFromUri(context, it)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        containerColor = Color(0xFFFEF7FF), // Exact High Density background
        topBar = {
            // High Density Premium Custom Header in place of a standard top bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFFEF7FF))
                    .statusBarsPadding()
                    .padding(horizontal = 24.dp, vertical = 12.dp)
            ) {
                // 1. Title & Description side-by-side in a Row with a background
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFECE6F0) // Sleek Material 3 Surface Color
                    ),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.CenterHorizontally)
                    ) {
                        Text(
                            text = if (showStepLengthConfig) "Einstellungen" else if (activeTab == 0) "Schrittzähler" else "Eintrag-Historie",
                            fontWeight = FontWeight.Bold,
                            fontSize = 24.sp,
                            letterSpacing = (-0.5).sp,
                            color = Color(0xFF1D1B20)
                        )
                        Icon(
                            imageVector = if (showStepLengthConfig) Icons.Default.Settings else if (activeTab == 0) Icons.Default.DirectionsRun else Icons.Default.History,
                            contentDescription = null,
                            tint = Color(0xFF6750A4),
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = if (showStepLengthConfig) "Konfiguration & Sicherung" else if (activeTab == 0) "Erfassung & Auswertung" else "Verlauf & Sicherung",
                            fontSize = 12.sp,
                            color = Color(0xFF49454F),
                            fontWeight = FontWeight.SemiBold,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // 2. Navigation & Actions Icon Row centered under the Title block
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Navigation tabs
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (!showStepLengthConfig) {
                            // Home / Dashboard Button
                            IconButton(
                                onClick = { activeTab = 0 },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("tab_dashboard")
                                    .clip(CircleShape)
                                    .background(if (activeTab == 0) Color(0xFFE8DEF8) else Color.Transparent)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Home,
                                    contentDescription = "Dashboard",
                                    tint = if (activeTab == 0) Color(0xFF21005D) else Color(0xFF49454F),
                                    modifier = Modifier.size(22.dp)
                                )
                            }

                            // Historie Button
                            IconButton(
                                onClick = { activeTab = 1 },
                                modifier = Modifier
                                    .size(40.dp)
                                    .testTag("tab_history")
                                    .clip(CircleShape)
                                    .background(if (activeTab == 1) Color(0xFFE8DEF8) else Color.Transparent)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.History,
                                    contentDescription = "Historie",
                                    tint = if (activeTab == 1) Color(0xFF21005D) else Color(0xFF49454F),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        } else {
                            // In Settings Mode: Show a clear Return Button
                            IconButton(
                                onClick = { showStepLengthConfig = false; activeTab = 0 },
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(Color.Transparent)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.ArrowBack,
                                    contentDescription = "Zurück",
                                    tint = Color(0xFF49454F),
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                    // Right: Settings Toggle & Custom Profile Initials
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = {
                                showStepLengthConfig = !showStepLengthConfig
                                coroutineScope.launch {
                                    listState.animateScrollToItem(0)
                                }
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("settings_gear_button")
                                .clip(CircleShape)
                                .background(if (showStepLengthConfig) Color(0xFFE8DEF8) else Color.Transparent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Settings,
                                contentDescription = "Einstellungen",
                                tint = if (showStepLengthConfig) Color(0xFF21005D) else Color(0xFF49454F),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                printMonthlyReport(context, monthlyStats.monthLabel, monthlyStats, stepLengthCm)
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("print_button")
                                .clip(CircleShape)
                                .background(Color.Transparent)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Print,
                                contentDescription = "Monatliche Zusammenfassung drucken",
                                tint = Color(0xFF49454F),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        IconButton(
                            onClick = {
                                showExitConfirmationDialog = true
                            },
                            modifier = Modifier
                                .size(40.dp)
                                .testTag("exit_button")
                                .clip(CircleShape)
                                .background(Color.Transparent)
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                contentDescription = "App beenden",
                                tint = Color(0xFF49454F),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.width(4.dp))

                        Surface(
                            modifier = Modifier.size(38.dp),
                            shape = CircleShape,
                            color = Color(0xFFEADDFF),
                            border = BorderStroke(1.dp, Color(0xFFCAC4D0))
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "TW", // Thomas Warncke
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF21005D),
                                    fontSize = 13.sp,
                                    letterSpacing = 0.sp
                                )
                            }
                        }
                    }
                }

                // 3. Backup Status Area (Underneath the Icons as a distinct notification line)
                if (activeTab == 0 && !showStepLengthConfig && lastBackupTime != "Nie") {
                    Spacer(modifier = Modifier.height(10.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = Color(0xFFF3EDF7)
                        ),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CloudDone,
                                contentDescription = "Sicherung erfolgreich",
                                tint = Color(0xFF6750A4),
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Letzte Sicherung: $lastBackupTime",
                                fontSize = 11.sp,
                                color = Color(0xFF6750A4),
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    ) { innerPadding ->
        LazyColumn(
            state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = 96.dp)
        ) {
            // SETTINGS & BACKUP AREA (shown when gear/settings is toggled)
            if (showStepLengthConfig) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        StepLengthConfigCard(
                            stepLengthCm = stepLengthCm,
                            onStepLengthChanged = { viewModel.updateStepLength(it) }
                        )

                        LocalBackupCard(
                            viewModel = viewModel
                        )
                    }
                }
            } else {
                if (activeTab == 0) {

            // TODAY LOGGING CTA CARD: Gorgeous banner styled in bg-[#EADDFF] (container) with light-purple button
            item {
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("today_quick_cta_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFFEADDFF)
                    ),
                    border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f))
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Header Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "HEUTE ERFASSEN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF21005D),
                                    letterSpacing = 1.sp
                                )
                                Text(
                                    text = "Schrittverlauf schnell ergänzen",
                                    fontSize = 13.sp,
                                    color = Color(0xFF49454F),
                                    fontWeight = FontWeight.Medium
                                )
                            }

                            // CTA Button with steps image as requested
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = Color(0xFF6750A4),
                                modifier = Modifier
                                    .height(38.dp)
                                    .clickable {
                                        dialogInitialDate = DateUtils.getTodayString()
                                        dialogInitialSteps = ""
                                        showAddDialog = true
                                    }
                                    .testTag("manual_add_steps_button")
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Image(
                                        painter = painterResource(id = R.drawable.img_steps_icon),
                                        contentDescription = "Schritte Icon",
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(Color.White),
                                        contentScale = ContentScale.Fit
                                    )
                                    Text(
                                        text = "+ Schritte",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }

                        // Quick action chips row to instantly add to today's steps
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Schnell:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFF49454F),
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            listOf(1000 to "+1.000", 3000 to "+3.000", 5000 to "+5.000").forEach { (amount, textStr) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = Color.White,
                                    border = BorderStroke(1.dp, Color(0xFF6750A4).copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(32.dp)
                                        .clickable {
                                            viewModel.quickAddTodaySteps(amount)
                                        }
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Text(
                                            text = textStr,
                                            color = Color(0xFF6750A4),
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                    }
                                }
                            }
                        }


                    }
                }
            }

            // Spacer wrapped in item block
            item {
                Spacer(modifier = Modifier.height(12.dp))
            }

            // PERIOD SELECTION SEGMENTED SWITCH
            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFF3EDF7))
                        .padding(4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf(PeriodType.WEEK to "Woche", PeriodType.MONTH to "Monat").forEach { (type, label) ->
                        val isSelected = type == activePeriodType
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) Color(0xFF6750A4) else Color.Transparent)
                                .clickable { viewModel.setPeriodType(type) }
                                .padding(vertical = 10.dp)
                                .testTag("period_tab_${type.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.White else Color(0xFF49454F)
                            )
                        }
                    }
                }
            }

            // PERIOD NAVIGATION HEADER
            item {
                PeriodSelectionHeader(
                    activePeriodType = activePeriodType,
                    mondayDateStr = selectedWeekMonday,
                    monthLabel = monthlyStats.monthLabel,
                    onPrev = {
                        if (activePeriodType == PeriodType.WEEK) {
                            viewModel.navigateToPreviousWeek()
                        } else {
                            viewModel.navigateToPreviousMonth()
                        }
                    },
                    onNext = {
                        if (activePeriodType == PeriodType.WEEK) {
                            viewModel.navigateToNextWeek()
                        } else {
                            viewModel.navigateToNextMonth()
                        }
                    },
                    onCurrent = {
                        viewModel.navigateToCurrentWeek()
                    }
                )
            }

            // GRAPHICAL PERIOD BAR CHART (Woche / Monat in custom card: bg-white border border-[#CAC4D0] rounded-3xl)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = Color.White
                    ),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, Color(0xFFCAC4D0)) // High Density specs
                ) {
                    Column(
                        modifier = Modifier.padding(20.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Bottom
                        ) {
                            Column {
                                Text(
                                    text = "Aktivität",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF1D1B20)
                                )
                                Text(
                                    text = if (activePeriodType == PeriodType.WEEK) "Wöchentliche Auswertung (Mo - So)" else "Monatliche Auswertung",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFF49454F)
                                )
                            }
                        }

                        if (activePeriodType == PeriodType.WEEK) {
                            WeeklyBarGraph(
                                daysData = weeklyStats.daysData,
                                selectedDateStr = selectedDayDateStr,
                                activelyClickedDateStr = activelyClickedDateStr,
                                onDaySelected = {
                                    selectedDayDateStr = it
                                    activelyClickedDateStr = if (activelyClickedDateStr == it) null else it
                                },
                                onSwipePrevWeek = { viewModel.navigateToPreviousWeek() },
                                onSwipeNextWeek = { viewModel.navigateToNextWeek() }
                            )
                        } else {
                            MonthlyBarGraph(
                                daysData = monthlyStats.daysData,
                                selectedDateStr = selectedDayDateStr,
                                activelyClickedDateStr = activelyClickedDateStr,
                                onDaySelected = {
                                    selectedDayDateStr = it
                                    activelyClickedDateStr = if (activelyClickedDateStr == it) null else it
                                },
                                onSwipePrevMonth = { viewModel.navigateToPreviousMonth() },
                                onSwipeNextMonth = { viewModel.navigateToNextMonth() }
                            )
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Selected Day Quick details & Quick edit action
                        selectedDayData?.let { day ->
                            val displayDate = DateUtils.formatGermanDate(day.dateStr)
                            val dayLabelLong = when (day.label) {
                                "Mo" -> "Montag"
                                "Di" -> "Dienstag"
                                "Mi" -> "Mittwoch"
                                "Do" -> "Donnerstag"
                                "Fr" -> "Freitag"
                                "Sa" -> "Samstag"
                                "So" -> "Sonntag"
                                else -> day.label
                            }

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(16.dp))
                                    .background(Color(0xFFEADDFF).copy(alpha = 0.4f))
                                    .border(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "$dayLabelLong, $displayDate",
                                        fontWeight = FontWeight.Bold,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF1D1B20)
                                    )
                                    Text(
                                        text = if (day.steps > 0) {
                                            "%, d Schritte • %.2f km".format(Locale.GERMANY, day.steps, day.distanceKm)
                                        } else {
                                            "Keine Schritte erfasst"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Medium,
                                        color = Color(0xFF49454F)
                                    )
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            dialogInitialDate = day.dateStr
                                            dialogInitialSteps = if (day.steps > 0) day.steps.toString() else ""
                                            showAddDialog = true
                                        },
                                        modifier = Modifier.size(36.dp).testTag("edit_day_button_${day.label}")
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Edit,
                                            contentDescription = "Schritte bearbeiten",
                                            tint = Color(0xFF6750A4),
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    if (day.steps > 0) {
                                        IconButton(
                                            onClick = { viewModel.deleteSteps(day.dateStr) },
                                            modifier = Modifier.size(36.dp).testTag("delete_day_button_${day.label}")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Delete,
                                                contentDescription = "Schritte löschen",
                                                tint = MaterialTheme.colorScheme.error,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }



            // STATS METRIC GRID - High Density styling in dual cards
            item {
                Text(
                    text = if (activePeriodType == PeriodType.WEEK) "Statistiken (Woche)" else "Statistiken (Monat)",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D1B20),
                    modifier = Modifier.padding(top = 4.dp, bottom = 4.dp)
                )
            }

            item {
                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val trackedDays = if (activePeriodType == PeriodType.WEEK) weeklyStats.trackedDaysCount else monthlyStats.trackedDaysCount
                    val totalDays = if (activePeriodType == PeriodType.WEEK) 7 else monthlyStats.daysData.size
                    val average = if (activePeriodType == PeriodType.WEEK) weeklyStats.averageSteps else monthlyStats.averageSteps
                    val totalDistance = if (activePeriodType == PeriodType.WEEK) weeklyStats.totalDistanceKm else monthlyStats.totalDistanceKm

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            title = "Erfasste Tage",
                            value = "$trackedDays / $totalDays",
                            icon = Icons.Default.CalendarToday,
                            color = Color(0xFF625B71),
                            modifier = Modifier.weight(1f).testTag("metric_logged_days")
                        )
                        MetricCard(
                            title = "Durchschnitt / Tag",
                            value = "%,.0f".format(Locale.GERMANY, average),
                            icon = Icons.Default.TrendingUp,
                            color = Color(0xFF7D5260),
                            modifier = Modifier.weight(1f).testTag("metric_average_steps")
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        MetricCard(
                            title = "Gesamtdistanz",
                            value = "%.2f km".format(Locale.GERMANY, totalDistance),
                            icon = Icons.Default.LocationOn,
                            color = Color(0xFF6750A4),
                            modifier = Modifier.weight(1f).testTag("metric_total_km")
                        )
                        // Simple info card
                        MetricCard(
                            title = "Schrittlänge",
                            value = "$stepLengthCm cm",
                            icon = Icons.Default.Settings,
                            color = Color(0xFF49454F),
                            modifier = Modifier.weight(1f).testTag("metric_step_len")
                        )
                    }
                }
            }

            } else {
                // RECORD LOGS LIST (Sorted ascending to assign chronological numbers)
                val sortedActiveEntries = allEntries
                    .filter { it.steps > 0 }
                    .sortedBy { it.date }

                val entryNumbers = sortedActiveEntries.mapIndexed { index, entry ->
                    entry.date to (index + 1)
                }.toMap()

                val historyDays = allEntries
                    .filter { it.steps > 0 }
                    .sortedByDescending { it.date }
                    .map { entry ->
                        val distanceKm = (entry.steps.toLong() * stepLengthCm) / 100000.0
                        DayStepData(
                            dateStr = entry.date,
                            label = DateUtils.getDayOfWeekLabel(entry.date),
                            steps = entry.steps,
                            distanceKm = distanceKm,
                            remark = entry.remark
                        )
                    }

                if (historyDays.isNotEmpty()) {
                    item {
                        Text(
                            text = "Eintrag-Historie",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF1D1B20),
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }

                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 520.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            historyDays.forEach { day ->
                                val num = entryNumbers[day.dateStr] ?: 0
                                LogItemRow(
                                    dayData = day,
                                    entryNumber = num,
                                    onEdit = {
                                        dialogInitialDate = day.dateStr
                                        dialogInitialSteps = day.steps.toString()
                                        dialogInitialRemark = day.remark
                                        showAddDialog = true
                                    },
                                    onDelete = {
                                        viewModel.deleteSteps(day.dateStr)
                                    }
                                )
                            }
                        }
                    }
                } else {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 40.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Keine Einträge vorhanden.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF49454F)
                            )
                        }
                    }
                }
            }
        }
    }
}

    // Step entry modal Dialog
    if (showAddDialog) {
        StepEntryDialog(
            initialDateStr = dialogInitialDate,
            initialStepsStr = dialogInitialSteps,
            initialRemarkStr = dialogInitialRemark,
            onDismiss = { showAddDialog = false },
            onSave = { date, steps, remark ->
                viewModel.saveSteps(date, steps, remark)
                showAddDialog = false
            }
        )
    }

    if (showExitConfirmationDialog) {
        AlertDialog(
            onDismissRequest = { showExitConfirmationDialog = false },
            title = {
                Text(
                    text = "App beenden",
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF21005D)
                )
            },
            text = {
                Text(
                    text = "Möchten Sie die App wirklich beenden?",
                    color = Color(0xFF49454F)
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmationDialog = false
                        (context as? Activity)?.finishAndRemoveTask()
                    }
                ) {
                    Text("Ja", color = Color(0xFF6750A4), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExitConfirmationDialog = false }
                ) {
                    Text("Nein", color = Color(0xFF49454F))
                }
            },
            containerColor = Color.White,
            shape = RoundedCornerShape(16.dp)
        )
    }
}

@Composable
fun StepLengthConfigCard(
    stepLengthCm: Int,
    onStepLengthChanged: (Int) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("step_length_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3EDF7) // Tailwind layout gray-purple
        ),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Settings,
                        contentDescription = null,
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Voreingestellte Schrittlänge",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B20)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF6750A4),
                    contentColor = Color.White
                ) {
                    Text(
                        text = "$stepLengthCm cm",
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "Wird zum automatischen Berechnen deiner zurückgelegten Distanz in Kilometern verwendet.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF49454F)
            )

            Row(
                modifier = Modifier.fillMaxWidth().height(48.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(
                    onClick = { onStepLengthChanged((stepLengthCm - 1).coerceAtLeast(30)) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFEADDFF)
                    ),
                    modifier = Modifier.size(36.dp).testTag("step_length_decrease")
                ) {
                    Icon(
                        imageVector = Icons.Default.Remove,
                        contentDescription = "Verringern",
                        tint = Color(0xFF21005D),
                        modifier = Modifier.size(16.dp)
                    )
                }

                Slider(
                    value = stepLengthCm.toFloat(),
                    onValueChange = { onStepLengthChanged(it.toInt()) },
                    valueRange = 30f..150f,
                    steps = 120,
                    colors = SliderDefaults.colors(
                        thumbColor = Color(0xFF6750A4),
                        activeTrackColor = Color(0xFF6750A4),
                        inactiveTrackColor = Color(0xFF6750A4).copy(alpha = 0.2f)
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("step_length_slider")
                )

                IconButton(
                    onClick = { onStepLengthChanged((stepLengthCm + 1).coerceAtMost(150)) },
                    colors = IconButtonDefaults.filledIconButtonColors(
                        containerColor = Color(0xFFEADDFF)
                    ),
                    modifier = Modifier.size(36.dp).testTag("step_length_increase")
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = "Erhöhen",
                        tint = Color(0xFF21005D),
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun PeriodSelectionHeader(
    activePeriodType: PeriodType,
    mondayDateStr: String,
    monthLabel: String,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onCurrent: () -> Unit
) {
    val displayRange = remember(activePeriodType, mondayDateStr, monthLabel) {
        if (activePeriodType == PeriodType.WEEK) {
            val days = DateUtils.getDaysOfWeekList(mondayDateStr)
            val startFormatted = DateUtils.formatGermanDate(days.first())
            val endFormatted = DateUtils.formatGermanDate(days.last())
            "$startFormatted - $endFormatted"
        } else {
            monthLabel
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("period_selection_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3EDF7)
        ),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            IconButton(
                onClick = onPrev,
                modifier = Modifier.testTag("prev_period_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                    contentDescription = if (activePeriodType == PeriodType.WEEK) "Vorherige Woche" else "Vorheriger Monat",
                    tint = Color(0xFF6750A4)
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (activePeriodType == PeriodType.WEEK) "WÖCHENTLICHE AUSWAHL" else "MONATSAUSWAHL",
                    fontSize = 10.sp,
                    color = Color(0xFF49454F),
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = displayRange,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF1D1B20),
                    textAlign = TextAlign.Center
                )
            }

            IconButton(
                onClick = { onCurrent() },
                modifier = Modifier.testTag("current_period_shortcut_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Today,
                    contentDescription = if (activePeriodType == PeriodType.WEEK) "Aktuelle Woche" else "Aktueller Monat",
                    tint = Color(0xFF6750A4),
                    modifier = Modifier.size(20.dp)
                )
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.testTag("next_period_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (activePeriodType == PeriodType.WEEK) "Nächste Woche" else "Nächster Monat",
                    tint = Color(0xFF6750A4)
                )
            }
        }
    }
}

@Composable
fun MonthlyBarGraph(
    daysData: List<DayStepData>,
    selectedDateStr: String,
    activelyClickedDateStr: String?,
    onDaySelected: (String) -> Unit,
    onSwipePrevMonth: () -> Unit,
    onSwipeNextMonth: () -> Unit
) {
    val maxSteps = daysData.maxOfOrNull { it.steps } ?: 0
    val maxStepsTarget = 10000f
    val scaleMax = if (maxSteps > maxStepsTarget) maxSteps.toFloat() else maxStepsTarget

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .pointerInput(Unit) {
                var dragAccum = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onDragEnd = {
                        if (dragAccum > 60f) {
                            onSwipePrevMonth()
                        } else if (dragAccum < -60f) {
                            onSwipeNextMonth()
                        }
                    },
                    onDragCancel = { dragAccum = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccum += dragAmount
                    }
                )
            }
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = 16.dp, bottom = 4.dp, start = 4.dp, end = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            daysData.forEach { day ->
                val isSelected = day.dateStr == selectedDateStr
                val isClicked = day.dateStr == activelyClickedDateStr
                
                val targetFraction = day.steps.toFloat() / scaleMax
                val animatedFraction by animateFloatAsState(
                    targetValue = targetFraction.coerceIn(0.02f, 1f),
                    label = "bar_height"
                )

                val animatedBarColor by animateColorAsState(
                    targetValue = when {
                        isSelected -> Color(0xFF6750A4)
                        day.steps >= 10000 -> Color(0xFF6750A4)
                        day.steps > 0 -> Color(0xFFE8DEF8)
                        else -> Color(0xFFF4F3F7)
                    },
                    label = "bar_color"
                )

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable { onDaySelected(day.dateStr) }
                        .testTag("month_chart_column_${day.dateStr}"),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Bottom
                ) {
                    FactoredRemarkBubble(remark = day.remark, isSelected = isClicked)
                    // Bar itself
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.BottomCenter
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(0.75f)
                                .fillMaxHeight(animatedFraction.coerceIn(0.02f, 1f))
                                .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                .background(
                                    if (day.steps > 0) {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                animatedBarColor,
                                                animatedBarColor.copy(alpha = 0.85f)
                                            )
                                        )
                                    } else {
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                Color(0xFFE7E0EC).copy(alpha = 0.3f),
                                                Color(0xFFE7E0EC).copy(alpha = 0.1f)
                                            )
                                        )
                                    }
                                )
                                .then(
                                    if (isSelected) {
                                        Modifier.border(
                                            width = 1.dp,
                                            color = Color(0xFF6750A4),
                                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                        )
                                    } else Modifier
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(6.dp))

                    // Clean labels at bottom space
                    val dateNum = day.dateStr.split("-").lastOrNull()?.toIntOrNull() ?: 0
                    val isLabelDay = dateNum == 1 || dateNum == 5 || dateNum == 10 || dateNum == 15 || dateNum == 20 || dateNum == 25 || dateNum == 30

                    Column(
                        modifier = Modifier
                            .height(28.dp)
                            .fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Top
                    ) {
                        if (isSelected) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = dateNum.toString(),
                                    style = MaterialTheme.typography.bodySmall,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF6750A4)
                                )
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF6750A4))
                                )
                            }
                        } else if (isLabelDay) {
                            Text(
                                text = dateNum.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Normal,
                                color = Color(0xFF79747E)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(2.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFFCAC4D0).copy(alpha = 0.6f))
                            )
                        }

                        if (day.remark.isNotEmpty()) {
                            Spacer(modifier = Modifier.height(1.dp))
                            Icon(
                                imageVector = Icons.Default.ChatBubble,
                                contentDescription = "Notiz vorhanden",
                                tint = Color(0xFF6750A4),
                                modifier = Modifier.size(7.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun WeeklyBarGraph(
    daysData: List<DayStepData>,
    selectedDateStr: String,
    activelyClickedDateStr: String?,
    onDaySelected: (String) -> Unit,
    onSwipePrevWeek: () -> Unit,
    onSwipeNextWeek: () -> Unit
) {
    val maxSteps = daysData.maxOfOrNull { it.steps } ?: 0
    val maxStepsTarget = 10000f
    val scaleMax = if (maxSteps > maxStepsTarget) maxSteps.toFloat() else maxStepsTarget

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(210.dp)
            .padding(top = 16.dp, bottom = 4.dp)
            .pointerInput(Unit) {
                var dragAccum = 0f
                detectHorizontalDragGestures(
                    onDragStart = { dragAccum = 0f },
                    onDragEnd = {
                        if (dragAccum > 60f) {
                            onSwipePrevWeek()
                        } else if (dragAccum < -60f) {
                            onSwipeNextWeek()
                        }
                    },
                    onDragCancel = { dragAccum = 0f },
                    onHorizontalDrag = { change, dragAmount ->
                        change.consume()
                        dragAccum += dragAmount
                    }
                )
            },
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        daysData.forEach { day ->
            val isSelected = day.dateStr == selectedDateStr
            val isClicked = day.dateStr == activelyClickedDateStr
            
            // Animated height fraction
            val targetFraction = day.steps.toFloat() / scaleMax
            val animatedFraction by animateFloatAsState(
                targetValue = targetFraction.coerceIn(0.02f, 1f),
                label = "bar_height"
            )

            // High Density style colors
            val animatedBarColor by animateColorAsState(
                targetValue = when {
                    isSelected -> Color(0xFF6750A4) // Selected deep purple
                    day.steps >= 10000 -> Color(0xFF6750A4) // Matches PacerTrack primary purple
                    day.steps > 0 -> Color(0xFFE8DEF8) // Inactive purple level
                    else -> Color(0xFFFEF7FF) // Empty backdrop
                },
                label = "bar_color"
            )

            val displayStepText = if (day.steps > 0) {
                if (day.steps >= 1000) "${"%.1f".format(Locale.GERMANY, day.steps / 1000f).removeSuffix(",0")}k" else day.steps.toString()
            } else ""

            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clickable { onDaySelected(day.dateStr) }
                    .testTag("chart_column_${day.label}"),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Bottom
            ) {
                FactoredRemarkBubble(remark = day.remark, isSelected = isClicked)
                // Steps labels on top of charts with fixed container height
                Box(
                    modifier = Modifier.height(18.dp),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    if (displayStepText.isNotEmpty()) {
                        Text(
                            text = displayStepText,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                            color = if (isSelected) Color(0xFF6750A4) else Color(0xFF49454F),
                            fontSize = 9.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))

                // Bar Container that takes up the remaining weight-allocated height
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.BottomCenter
                ) {
                    // Bar
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(0.55f)
                            .fillMaxHeight(animatedFraction.coerceIn(0.02f, 1f))
                            .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                            .background(
                                if (day.steps > 0) {
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            animatedBarColor,
                                            animatedBarColor.copy(alpha = 0.82f)
                                        )
                                    )
                                } else {
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            Color(0xFFE7E0EC).copy(alpha = 0.4f),
                                            Color(0xFFE7E0EC).copy(alpha = 0.15f)
                                        )
                                    )
                                }
                            )
                            .then(
                                if (isSelected) {
                                    Modifier.border(
                                        width = 1.5.dp,
                                        color = Color(0xFF6750A4),
                                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                    )
                                } else Modifier
                            )
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Short day label: Mo, Di...
                Text(
                    text = day.label,
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                    color = if (isSelected) Color(0xFF6750A4) else Color(0xFF49454F)
                )

                // Calendar date label (dd.)
                val dateNum = day.dateStr.split("-").lastOrNull()?.removePrefix("0") ?: ""
                Text(
                    text = dateNum,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Light,
                    color = if (isSelected) Color(0xFF6750A4) else Color(0xFF49454F).copy(alpha = 0.7f)
                )

                Box(
                    modifier = Modifier.height(12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    if (day.remark.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "Notiz vorhanden",
                            tint = Color(0xFF6750A4),
                            modifier = Modifier.size(9.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FactoredRemarkBubble(
    remark: String,
    isSelected: Boolean
) {
    if (isSelected && remark.isNotEmpty()) {
        val density = androidx.compose.ui.platform.LocalDensity.current
        val offsetY = remember(density) { with(density) { -72.dp.roundToPx() } }
        
        Popup(
            alignment = Alignment.TopCenter,
            offset = IntOffset(0, offsetY),
            properties = PopupProperties(
                focusable = false,
                dismissOnBackPress = true,
                dismissOnClickOutside = true
            )
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .widthIn(max = 180.dp)
                    .padding(horizontal = 8.dp)
            ) {
                Surface(
                    color = Color(0xFF21005D),
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, Color(0xFFE8DEF8).copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notes,
                            contentDescription = null,
                            tint = Color(0xFFE8DEF8),
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = remark,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            style = MaterialTheme.typography.bodySmall,
                            textAlign = TextAlign.Center
                        )
                    }
                }
                
                // Small pointing arrow (downward arrow)
                Box(
                    modifier = Modifier
                        .offset(y = (-4).dp)
                        .size(8.dp)
                        .rotate(45f)
                        .background(Color(0xFF21005D))
                )
            }
        }
    }
}

@Composable
fun MetricCard(
    title: String,
    value: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(24.dp), // modern highly-rounded specs
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3EDF7) // Tailwind stats backgrounds
        ),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = title.uppercase(),
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF49454F),
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Column {
                Text(
                    text = value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF6750A4)
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = Color(0xFF49454F),
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "Aktive Auswertung",
                        fontSize = 9.sp,
                        color = Color(0xFF49454F)
                    )
                }
            }
        }
    }
}

@Composable
fun LogItemRow(
    dayData: DayStepData,
    entryNumber: Int,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    val displayDate = DateUtils.formatGermanDate(dayData.dateStr)
    val weekday = when (dayData.label) {
        "Mo" -> "Montag"
        "Di" -> "Dienstag"
        "Mi" -> "Mittwoch"
        "Do" -> "Donnerstag"
        "Fr" -> "Freitag"
        "Sa" -> "Samstag"
        "So" -> "Sonntag"
        else -> dayData.label
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("log_item_row_${dayData.dateStr}"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color.White
        ),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = Color(0xFFE8DEF8),
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = "$entryNumber.",
                        fontWeight = FontWeight.Black,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF6750A4)
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFCAC4D0)
                    )
                    Text(
                        text = "$weekday, $displayDate",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF1D1B20)
                    )
                }
                Text(
                    text = "%, d Schritte • %.2f km".format(Locale.GERMANY, dayData.steps, dayData.distanceKm),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF49454F)
                )
                if (dayData.remark.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = Color(0xFFF3EDF7)
                    ) {
                        Text(
                            text = dayData.remark,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = Color(0xFF6750A4),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier.size(36.dp).testTag("log_edit_${dayData.dateStr}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Edit,
                        contentDescription = "Eintrag bearbeiten",
                        tint = Color(0xFF49454F),
                        modifier = Modifier.size(18.dp)
                    )
                }

                IconButton(
                    onClick = onDelete,
                    modifier = Modifier.size(36.dp).testTag("log_delete_${dayData.dateStr}")
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = "Eintrag löschen",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun StepEntryDialog(
    initialDateStr: String,
    initialStepsStr: String,
    initialRemarkStr: String = "",
    onDismiss: () -> Unit,
    onSave: (String, Int, String) -> Unit
) {
    val context = LocalContext.current
    var dateStr by remember { mutableStateOf(initialDateStr) }
    var stepsField by remember { mutableStateOf(initialStepsStr) }
    var remarkField by remember { mutableStateOf(initialRemarkStr) }
    var showErrorMsg by remember { mutableStateOf("") }

    val formattedGermanDate = remember(dateStr) {
        DateUtils.formatGermanDate(dateStr)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = Color(0xFFFEF7FF),
        titleContentColor = Color(0xFF1D1B20),
        textContentColor = Color(0xFF49454F),
        title = {
            Text(
                text = if (initialStepsStr.isEmpty()) "Schritte erfassen" else "Eintrag bearbeiten",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = Color(0xFF1D1B20)
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // DATE PICKER FIELD
                OutlinedButton(
                    onClick = {
                        val cal = Calendar.getInstance()
                        try {
                            val parts = dateStr.split("-")
                            if (parts.size == 3) {
                                cal.set(Calendar.YEAR, parts[0].toInt())
                                cal.set(Calendar.MONTH, parts[1].toInt() - 1)
                                cal.set(Calendar.DAY_OF_MONTH, parts[2].toInt())
                            }
                        } catch (e: Exception) {
                            // fallback
                        }

                        DatePickerDialog(
                            context,
                            { _, y, m, d ->
                                val monthAdjusted = m + 1
                                val monthPadded = if (monthAdjusted < 10) "0$monthAdjusted" else "$monthAdjusted"
                                val dayPadded = if (d < 10) "0$d" else "$d"
                                dateStr = "$y-$monthPadded-$dayPadded"
                            },
                            cal.get(Calendar.YEAR),
                            cal.get(Calendar.MONTH),
                            cal.get(Calendar.DAY_OF_MONTH)
                        ).show()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .testTag("dialog_date_select"),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.CalendarToday, contentDescription = null, tint = Color(0xFF6750A4))
                            Text(
                                text = "Datum: $formattedGermanDate",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF1D1B20)
                            )
                        }
                        Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color(0xFF6750A4))
                    }
                }

                // STEPS COUNT FIELD
                OutlinedTextField(
                    value = stepsField,
                    onValueChange = { input ->
                        if (input.all { it.isDigit() }) {
                            stepsField = input
                        }
                    },
                    label = { Text("Schritte", color = Color(0xFF49454F)) },
                    placeholder = { Text("z.B. 10000", color = Color(0xFF79747E)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1D1B20),
                        unfocusedTextColor = Color(0xFF1D1B20),
                        focusedLabelColor = Color(0xFF6750A4),
                        unfocusedLabelColor = Color(0xFF49454F),
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCAC4D0),
                        cursorColor = Color(0xFF6750A4)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_steps_input"),
                    leadingIcon = {
                        Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = Color(0xFF6750A4))
                    },
                    trailingIcon = {
                        if (stepsField.isNotEmpty()) {
                            IconButton(onClick = { stepsField = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Eingabe löschen", tint = Color(0xFF49454F))
                            }
                        }
                    }
                )

                // REMARK FIELD
                OutlinedTextField(
                    value = remarkField,
                    onValueChange = { remarkField = it },
                    label = { Text("Bemerkung", color = Color(0xFF49454F)) },
                    placeholder = { Text("z.B. Abendspaziergang", color = Color(0xFF79747E)) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color(0xFF1D1B20),
                        unfocusedTextColor = Color(0xFF1D1B20),
                        focusedLabelColor = Color(0xFF6750A4),
                        unfocusedLabelColor = Color(0xFF49454F),
                        focusedBorderColor = Color(0xFF6750A4),
                        unfocusedBorderColor = Color(0xFFCAC4D0),
                        cursorColor = Color(0xFF6750A4)
                    ),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("dialog_remark_input"),
                    leadingIcon = {
                        Icon(Icons.Default.Notes, contentDescription = null, tint = Color(0xFF6750A4))
                    },
                    trailingIcon = {
                        if (remarkField.isNotEmpty()) {
                            IconButton(onClick = { remarkField = "" }) {
                                Icon(Icons.Default.Clear, contentDescription = "Eingabe löschen", tint = Color(0xFF49454F))
                            }
                        }
                    }
                )

                // Quick inputs
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val addAmount: (Int) -> Unit = { amount ->
                        val current = stepsField.toIntOrNull() ?: 0
                        stepsField = (current + amount).toString()
                    }
                    Button(
                        onClick = { addAmount(1000) },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = Color(0xFFE8DEF8)
                        ),
                        modifier = Modifier.weight(1f).testTag("quick_add_1k"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+1.000", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D192B))
                    }
                    Button(
                        onClick = { addAmount(5000) },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = Color(0xFFE8DEF8)
                        ),
                        modifier = Modifier.weight(1f).testTag("quick_add_5k"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+5.000", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D192B))
                    }
                    Button(
                        onClick = { addAmount(10000) },
                        colors = ButtonDefaults.textButtonColors(
                            containerColor = Color(0xFFE8DEF8)
                        ),
                        modifier = Modifier.weight(1f).testTag("quick_add_10k"),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text("+10.000", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFF1D192B))
                    }
                }

                AnimatedVisibility(visible = showErrorMsg.isNotEmpty()) {
                    Text(
                        text = showErrorMsg,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsed = stepsField.toIntOrNull()
                    if (parsed == null || parsed < 0) {
                        showErrorMsg = "Bitte gib eine gültige Anzahl an Schritten ein."
                    } else if (parsed > 1000000) {
                        showErrorMsg = "Das ist eine unglaubliche Zahl! Bitte erfasse Schritte unter 1.000.000."
                    } else {
                        onSave(dateStr, parsed, remarkField)
                    }
                },
                modifier = Modifier.testTag("dialog_save_button"),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6750A4)
                )
            ) {
                Text("Speichern")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.testTag("dialog_cancel_button")
            ) {
                Text("Abbrechen", color = Color(0xFF6750A4))
            }
        }
    )
}

fun getFolderDisplayNameSafe(context: android.content.Context, uriString: String): String {
    if (uriString.isEmpty()) return ""
    try {
        val uri = Uri.parse(uriString)
        if (DocumentsContract.isTreeUri(uri)) {
            val documentId = DocumentsContract.getTreeDocumentId(uri)
            val documentUri = DocumentsContract.buildDocumentUriUsingTree(uri, documentId)
            context.contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (index != -1) {
                        val name = cursor.getString(index)
                        if (!name.isNullOrEmpty()) {
                            return if (uriString.contains("com.google.android.apps.docs") || uriString.contains("google")) {
                                "Google Cloud ➤ $name"
                            } else {
                                name
                            }
                        }
                    }
                }
            }
        } else {
            context.contentResolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null
            )?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(DocumentsContract.Document.COLUMN_DISPLAY_NAME)
                    if (index != -1) {
                        val name = cursor.getString(index)
                        if (!name.isNullOrEmpty()) {
                            return if (uriString.contains("com.google.android.apps.docs") || uriString.contains("google")) {
                                "Google Cloud ➤ $name"
                            } else {
                                name
                            }
                        }
                    }
                }
            }
        }
    } catch (e: Throwable) {
        e.printStackTrace()
    }

    val decodedUri = Uri.decode(uriString)
    val treeMarker = "/tree/"
    val docMarker = "/document/"
    val pathPart = when {
        decodedUri.contains(treeMarker) -> decodedUri.substringAfter(treeMarker)
        decodedUri.contains(docMarker) -> decodedUri.substringAfter(docMarker)
        else -> decodedUri
    }

    return when {
        decodedUri.contains("com.google.android.apps.docs") || decodedUri.contains("google") -> {
            val cleanPath = when {
                pathPart.contains(":/") -> "/" + pathPart.substringAfter(":/")
                pathPart.contains(":") -> "/" + pathPart.substringAfter(":")
                else -> pathPart
            }
            "Google Cloud: $cleanPath"
        }
        decodedUri.contains("com.android.externalstorage.documents") -> {
            val cleanPath = when {
                pathPart.contains("primary:") -> "/" + pathPart.substringAfter("primary:")
                pathPart.contains(":") -> "/" + pathPart.substringAfter(":")
                else -> pathPart
            }
            "Hauptspeicher: $cleanPath"
        }
        else -> {
            val cleanPath = when {
                pathPart.contains(":/") -> "/" + pathPart.substringAfter(":/")
                pathPart.contains(":") -> "/" + pathPart.substringAfter(":")
                else -> pathPart
            }
            cleanPath
        }
    }
}

@Composable
fun rememberFolderDisplayName(context: android.content.Context, uriString: String): String {
    if (uriString.isEmpty()) return ""
    val displayNameState = produceState(initialValue = "Lade...", key1 = uriString) {
        value = kotlinx.coroutines.withContext(kotlinx.coroutines.Dispatchers.IO) {
            getFolderDisplayNameSafe(context, uriString)
        }
    }
    return displayNameState.value
}

fun printMonthlyReport(context: android.content.Context, monthLabel: String, stats: MonthlyStats, stepLengthCm: Int) {
    val activity = context as? Activity ?: return
    activity.runOnUiThread {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as? PrintManager
                if (printManager != null) {
                    val printAdapter = webView.createPrintDocumentAdapter("Schrittzähler_Monatsbericht_${monthLabel.replace(" ", "_")}")
                    val jobName = "Schrittzähler - $monthLabel"
                    printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
                }
            }
        }

        val htmlContent = generateMonthlyReportHtml(monthLabel, stats, stepLengthCm)
        webView.loadDataWithBaseURL(null, htmlContent, "text/html", "UTF-8", null)
    }
}

fun generateMonthlyChartSvg(daysData: List<DayStepData>): String {
    val maxSteps = daysData.maxOfOrNull { it.steps }?.coerceAtLeast(1000) ?: 10000
    val height = 135
    val width = 740
    val paddingLeft = 55
    val paddingRight = 15
    val paddingTop = 12
    val paddingBottom = 28
    
    val graphWidth = width - paddingLeft - paddingRight
    val graphHeight = height - paddingTop - paddingBottom
    
    val sb = StringBuilder()
    sb.append("<svg width=\"100%\" height=\"$height\" viewBox=\"0 0 $width $height\" xmlns=\"http://www.w3.org/2000/svg\">\n")
    sb.append("  <rect width=\"100%\" height=\"100%\" fill=\"#FAFAFA\" rx=\"8\"/>\n")
    
    val stepsGrid = listOf(0, maxSteps / 2, maxSteps)
    for (stepVal in stepsGrid) {
        val y = paddingTop + graphHeight - (stepVal.toDouble() / maxSteps * graphHeight).toInt()
        sb.append("  <line x1=\"$paddingLeft\" y1=\"$y\" x2=\"${width - paddingRight}\" y2=\"$y\" stroke=\"#EAEAEA\" stroke-width=\"1\" stroke-dasharray=\"3\"/>\n")
        sb.append("  <text x=\"${paddingLeft - 8}\" y=\"${y + 4}\" font-family=\"sans-serif\" font-size=\"9\" fill=\"#666\" text-anchor=\"end\">${String.format("%,d", stepVal)}</text>\n")
    }
    
    val barCount = daysData.size
    if (barCount > 0) {
        val stepX = graphWidth.toDouble() / barCount
        val barWidth = (stepX * 0.70).coerceAtLeast(2.0)
        
        daysData.forEachIndexed { index, day ->
            val barHeight = (day.steps.toDouble() / maxSteps * graphHeight).coerceAtLeast(0.0)
            val x = paddingLeft + index * stepX + (stepX - barWidth) / 2
            val y = paddingTop + graphHeight - barHeight
            
            val barColor = if (day.steps >= 10000) "#00796B" else "#6750A4"
            if (barHeight > 0) {
                sb.append("  <rect x=\"$x\" y=\"$y\" width=\"$barWidth\" height=\"$barHeight\" fill=\"$barColor\" rx=\"1.5\"/>\n")
            }
            
            val dayNumPart = day.dateStr.substringAfterLast("-").toIntOrNull() ?: (index + 1)
            if (barCount <= 15 || dayNumPart == 1 || dayNumPart % 5 == 0 || dayNumPart == barCount) {
                val labelX = x + barWidth / 2
                val labelY = paddingTop + graphHeight + 12
                sb.append("  <text x=\"$labelX\" y=\"$labelY\" font-family=\"sans-serif\" font-size=\"8\" fill=\"#555\" text-anchor=\"middle\">$dayNumPart</text>\n")
            }
        }
    }
    
    val xAxisY = paddingTop + graphHeight
    sb.append("  <line x1=\"$paddingLeft\" y1=\"$xAxisY\" x2=\"${width - paddingRight}\" y2=\"$xAxisY\" stroke=\"#999\" stroke-width=\"1\"/>\n")
    sb.append("  <line x1=\"$paddingLeft\" y1=\"$paddingTop\" x2=\"$paddingLeft\" y2=\"$xAxisY\" stroke=\"#999\" stroke-width=\"1\"/>\n")
    
    sb.append("  <circle cx=\"${width - 150}\" cy=\"${height - 10}\" r=\"4\" fill=\"#6750A4\"/>\n")
    sb.append("  <text x=\"${width - 142}\" y=\"${height - 7}\" font-family=\"sans-serif\" font-size=\"8\" fill=\"#555\">Schritte</text>\n")
    
    sb.append("  <circle cx=\"${width - 90}\" cy=\"${height - 10}\" r=\"4\" fill=\"#00796B\"/>\n")
    sb.append("  <text x=\"${width - 82}\" y=\"${height - 7}\" font-family=\"sans-serif\" font-size=\"8\" fill=\"#555\">Aktiv (&gt;= 10k)</text>\n")
    
    sb.append("</svg>")
    return sb.toString()
}

fun generateMonthlyReportHtml(monthLabel: String, stats: MonthlyStats, stepLengthCm: Int): String {
    val totalStepsFormatted = String.format("%,d", stats.totalSteps)
    val avgStepsFormatted = String.format("%,d", stats.averageSteps.toInt())
    val distanceFormatted = String.format("%.2f", stats.totalDistanceKm)
    val currentDate = SimpleDateFormat("dd.MM.yyyy HH:mm", java.util.Locale.getDefault()).format(java.util.Date())
    
    val chartSvg = generateMonthlyChartSvg(stats.daysData)
    
    val sb = StringBuilder()
    sb.append("""
        <!DOCTYPE html>
        <html>
        <head>
        <meta charset="utf-8">
        <style>
            @page {
                size: A4 portrait;
                margin-top: 26mm; /* Größerer oberer Rand zum vertikalen Zentrieren */
                margin-bottom: 26mm; /* Größerer unterer Rand zur Ausbalancierung */
                margin-left: 24mm; /* Großer linker Rand zum Abheften / Lochen */
                margin-right: 16mm; /* Passender rechter Rand */
            }
            body {
                font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
                color: #1D1B20;
                margin: 0;
                padding: 0;
                background-color: #ffffff;
                line-height: 1.25;
            }
            .header-container {
                border-bottom: 2px solid #6750A4;
                padding-bottom: 6px;
                margin-bottom: 10px;
                display: flex;
                justify-content: space-between;
                align-items: flex-end;
            }
            .title-main {
                font-size: 18px;
                margin: 0;
                color: #21005D;
                font-weight: bold;
            }
            .subtitle {
                font-size: 10px;
                color: #49454F;
                margin: 0;
                text-align: right;
            }
            .card-wrapper {
                display: flex;
                justify-content: space-between;
                margin-bottom: 10px;
                gap: 8px;
            }
            .metric-card {
                flex: 1;
                background-color: #F3EDF7;
                border: 1px solid #E8DEF8;
                border-radius: 6px;
                padding: 6px;
                text-align: center;
                box-sizing: border-box;
            }
            .metric-title {
                font-size: 8px;
                text-transform: uppercase;
                letter-spacing: 0.3px;
                color: #49454F;
                margin-bottom: 2px;
                font-weight: bold;
            }
            .metric-value {
                font-size: 13px;
                font-weight: bold;
                color: #21005D;
            }
            .chart-box {
                border: 1px solid #CAC4D0;
                border-radius: 8px;
                padding: 8px 12px;
                margin-bottom: 10px;
                background-color: #FAFAFA;
            }
            .chart-box-title {
                font-size: 11px;
                font-weight: bold;
                margin-bottom: 4px;
                color: #1D1B20;
                border-bottom: 1px solid #E8DEF8;
                padding-bottom: 2px;
            }
            .section-title {
                font-size: 11px;
                font-weight: bold;
                margin: 10px 0 4px 0;
                color: #6750A4;
                text-transform: uppercase;
                letter-spacing: 0.5px;
            }
            table {
                width: 100%;
                border-collapse: collapse;
                margin-top: 4px;
                font-size: 8.5px; /* Kleinere Schriftgröße für die tägliche Erfassung */
            }
            th {
                background-color: #6750A4;
                color: white;
                text-align: left;
                padding: 4px 6px;
                font-weight: bold;
                border: 1px solid #6750A4;
            }
            td {
                padding: 3px 6px;
                border: 1px solid #E1E1E1;
            }
            tr:nth-child(even) {
                background-color: #F8F7FA;
            }
            .goal-reached {
                color: #00796B;
                font-weight: bold;
            }
            .footer {
                margin-top: 12px;
                font-size: 8px;
                text-align: center;
                color: #79747E;
                border-top: 1px solid #E8DEF8;
                padding-top: 4px;
            }
            @media print {
                body {
                    padding: 0;
                }
                tr {
                    page-break-inside: avoid;
                }
            }
        </style>
        </head>
        <body>
            <div class="header-container">
                <h1 class="title-main">📊 Schrittzähler &amp; Aktivität</h1>
                <p class="subtitle">Bericht für <strong>$monthLabel</strong> &bull; Schrittlänge: $stepLengthCm cm &bull; Gedruckt: $currentDate</p>
            </div>
            
            <div class="card-wrapper">
                <div class="metric-card">
                    <div class="metric-title">Gesamtschritte</div>
                    <div class="metric-value">$totalStepsFormatted</div>
                </div>
                <div class="metric-card">
                    <div class="metric-title">Tagesdurchschnitt</div>
                    <div class="metric-value">$avgStepsFormatted</div>
                </div>
                <div class="metric-card">
                    <div class="metric-title">Gesamtdistanz</div>
                    <div class="metric-value">$distanceFormatted km</div>
                    <div style="font-size: 7.5px; color: #49454F; margin-top: 2px;">(Schrittlänge: $stepLengthCm cm)</div>
                </div>
                <div class="metric-card">
                    <div class="metric-title">Erfasste Tage</div>
                    <div class="metric-value">${stats.trackedDaysCount} Tage</div>
                </div>
            </div>
            
            <div class="chart-box">
                <div class="chart-box-title">Aktivitäts-Diagramm (Monatsverlauf)</div>
                <div>
                    $chartSvg
                </div>
            </div>
            
            <div class="section-title">Tägliche Erfassung:</div>
            <table>
                <thead>
                    <tr>
                        <th style="width: 12%;">Datum</th>
                        <th style="width: 15%;">Wochentag</th>
                        <th style="width: 18%;">Schritte</th>
                        <th style="width: 15%;">Distanz</th>
                        <th>Notiz / Bemerkungen</th>
                    </tr>
                </thead>
                <tbody>
    """.trimIndent())
    
    stats.daysData.forEach { day ->
        val dateDisplay = try {
            val parts = day.dateStr.split("-")
            if (parts.size == 3) "${parts[2]}.${parts[1]}.${parts[0]}" else day.dateStr
        } catch (e: Exception) {
            day.dateStr
        }
        
        val stepsFormatted = String.format("%,d", day.steps)
        val kmFormatted = String.format("%.2f km", day.distanceKm)
        val isGoalMetClass = if (day.steps >= 10000) "class=\"goal-reached\"" else ""
        
        sb.append("""
            <tr>
                <td>$dateDisplay</td>
                <td>${day.label}</td>
                <td ${"$isGoalMetClass"}>$stepsFormatted ${if (day.steps >= 10000) "🏆" else ""}</td>
                <td>$kmFormatted</td>
                <td>${day.remark.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")}</td>
            </tr>
        """.trimIndent())
    }
    
    sb.append("""
                </tbody>
            </table>
            
            <div class="footer">
                Gesundheitsbericht &bull; Schrittzähler App (Thomas Warncke)
            </div>
        </body>
        </html>
    """.trimIndent())
    
    return sb.toString()
}

@Composable
fun LocalBackupCard(
    viewModel: StepViewModel
) {
    val context = LocalContext.current
    val isLoading by viewModel.isBackupRestoreLoading.collectAsStateWithLifecycle()
    val isAutoBackupEnabled by viewModel.isAutoBackupEnabled.collectAsStateWithLifecycle()
    val lastBackupTime by viewModel.lastBackupTime.collectAsStateWithLifecycle()
    val customBackupDirUri by viewModel.customBackupDirUri.collectAsStateWithLifecycle()
    val customBackupFileName by viewModel.customBackupFileName.collectAsStateWithLifecycle()

    val directoryPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocumentTree()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val takeFlags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
                context.contentResolver.takePersistableUriPermission(uri, takeFlags)
                viewModel.setCustomBackupDirUri(uri.toString())
                Toast.makeText(context, "Speicherort erfolgreich festgelegt!", Toast.LENGTH_SHORT).show()
            } catch (e: Exception) {
                viewModel.setCustomBackupDirUri(uri.toString())
                Toast.makeText(context, "Speicherort festgelegt (ggf. Berechtigung temporär)", Toast.LENGTH_SHORT).show()
                e.printStackTrace()
            }
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("local_backup_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = Color(0xFFF3EDF7)
        ),
        border = BorderStroke(1.dp, Color(0xFFCAC4D0).copy(alpha = 0.6f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Save,
                        contentDescription = null,
                        tint = Color(0xFF6750A4),
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = "Lokale Datensicherung",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B20)
                    )
                }

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = Color(0xFF6750A4).copy(alpha = 0.1f),
                    contentColor = Color(0xFF6750A4)
                ) {
                    Text(
                        text = if (customBackupDirUri.isNotEmpty()) "Eigener Pfad" else "Standarddatei",
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "Die Sicherung wird immer in die konfigurierte Datei geschrieben und überschreibt diese, damit keine unübersichtlichen Dateidubletten entstehen.",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF49454F)
            )

            // Dynamic storage path configuration
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFFEADDFF).copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "Konfigurierter Speicherort:",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF21005D)
                )
                
                val currentPathText = if (customBackupDirUri.isNotEmpty()) {
                    val readablePath = rememberFolderDisplayName(context, customBackupDirUri)
                    "Ordner: $readablePath\nDatei: $customBackupFileName"
                } else {
                    "Ordner: App-Speicher (.../files/Documents/)\nDatei: $customBackupFileName"
                }

                Text(
                    text = currentPathText,
                    style = MaterialTheme.typography.bodySmall,
                    color = Color(0xFF49454F),
                    lineHeight = 16.sp
                )
                
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            try {
                                directoryPicker.launch(null)
                            } catch (e: Exception) {
                                Toast.makeText(context, "Ordnerauswahl fehlgeschlagen: ${e.localizedMessage}", Toast.LENGTH_LONG).show()
                            }
                        },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = Color(0xFF6750A4)
                        ),
                        modifier = Modifier.weight(1.5f),
                        shape = RoundedCornerShape(8.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                    ) {
                        Text("Ordner wählen", fontSize = 11.sp, maxLines = 1)
                    }

                    if (customBackupDirUri.isNotEmpty()) {
                        Button(
                            onClick = {
                                viewModel.setCustomBackupDirUri("")
                                Toast.makeText(context, "Auf Standard-Speicherort zurückgesetzt!", Toast.LENGTH_LONG).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Color(0xFFEADDFF)
                            ),
                            modifier = Modifier.weight(1.2f),
                            shape = RoundedCornerShape(8.dp),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text("Standard", color = Color(0xFF21005D), fontSize = 11.sp, maxLines = 1)
                        }
                    }
                }

                // Dateiname ändern
                var tempFileName by remember(customBackupFileName) { mutableStateOf(customBackupFileName) }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = tempFileName,
                        onValueChange = { tempFileName = it },
                        placeholder = { Text("pacertrack_backup.json") },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                        textStyle = MaterialTheme.typography.bodySmall,
                        shape = RoundedCornerShape(8.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color(0xFF6750A4),
                            unfocusedTextColor = Color(0xFF6750A4),
                            focusedBorderColor = Color(0xFF6750A4),
                            unfocusedBorderColor = Color(0xFFCAC4D0),
                            focusedContainerColor = Color.White,
                            unfocusedContainerColor = Color.White
                        )
                    )
                    Button(
                        onClick = {
                            viewModel.setCustomBackupFileName(tempFileName)
                            Toast.makeText(context, "Dateiname gespeichert!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(8.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF6750A4)),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                    ) {
                        Text("Ok", fontSize = 11.sp)
                    }
                }
            }

            HorizontalDivider(color = Color(0xFFCAC4D0).copy(alpha = 0.4f), thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Automatische Sicherung",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1D1B20)
                    )
                    Text(
                        text = "Sichert Änderungen im Schrittverlauf automatisch im Hintergrund in der Standarddatei.",
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFF49454F)
                    )
                }
                Spacer(Modifier.width(16.dp))
                Switch(
                    checked = isAutoBackupEnabled,
                    onCheckedChange = { viewModel.setAutoBackupEnabled(it) },
                    modifier = Modifier.testTag("auto_backup_switch")
                )
            }

            if (lastBackupTime != "Nie") {
                Text(
                    text = "Letzte Sicherung: $lastBackupTime",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFF6750A4)
                )
            }

            HorizontalDivider(color = Color(0xFFCAC4D0).copy(alpha = 0.4f), thickness = 1.dp)

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = { viewModel.backupToLocalFile(context) },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6750A4)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Save, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Sichern", fontSize = 13.sp)
                    }
                }

                Button(
                    onClick = { viewModel.restoreFromLocalFile(context) },
                    modifier = Modifier.weight(1f),
                    enabled = !isLoading,
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF6750A4)
                    )
                ) {
                    if (isLoading) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            color = Color.White,
                            strokeWidth = 2.dp
                        )
                    } else {
                        Icon(Icons.Default.Download, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Einspielen", fontSize = 13.sp)
                    }
                }
            }

            Button(
                onClick = { viewModel.shareBackupFile(context) },
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF6750A4).copy(alpha = 0.1f),
                    contentColor = Color(0xFF6750A4)
                )
            ) {
                Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                Spacer(Modifier.width(8.dp))
                Text("Sicherungsdatei teilen / senden", fontSize = 13.sp)
            }
        }
    }
}
