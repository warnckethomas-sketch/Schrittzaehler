package com.example.ui

import android.app.DatePickerDialog
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import android.os.Build
import android.content.Intent
import android.net.Uri
import android.app.AlarmManager
import android.os.PowerManager
import android.provider.Settings
import android.print.PrintAttributes
import android.print.PrintManager
import android.webkit.WebView
import android.webkit.WebViewClient
import java.text.SimpleDateFormat
import java.util.Locale
import android.provider.DocumentsContract
import android.widget.Toast
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.foundation.Image
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
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
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
    val inactiveWeeklyStats by viewModel.inactiveWeeklyStats.collectAsStateWithLifecycle()
    val inactiveMonthlyStats by viewModel.inactiveMonthlyStats.collectAsStateWithLifecycle()
    val activePeriodType by viewModel.activePeriodType.collectAsStateWithLifecycle()
    val stepLengthCm by viewModel.stepLengthCm.collectAsStateWithLifecycle()
    val selectedWeekMonday by viewModel.selectedWeekMonday.collectAsStateWithLifecycle()
    val allEntries by viewModel.allEntries.collectAsStateWithLifecycle()
    val inactiveEntries by viewModel.inactiveEntries.collectAsStateWithLifecycle()
    val rawAllEntries by viewModel.rawAllEntries.collectAsStateWithLifecycle()
    val selectedPerson by viewModel.selectedPerson.collectAsStateWithLifecycle()
    val person1Name by viewModel.person1Name.collectAsStateWithLifecycle()
    val person2Name by viewModel.person2Name.collectAsStateWithLifecycle()
    val initials = remember(selectedPerson, person1Name, person2Name) {
        val name = if (selectedPerson == "person_2") person2Name else person1Name
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (parts.isEmpty()) {
            if (selectedPerson == "person_2") "P2" else "TW"
        } else {
            parts.map { it.first().uppercase() }.joinToString("").take(3)
        }
    }
    val stepLengthCmPerson1 by viewModel.stepLengthCmPerson1.collectAsStateWithLifecycle()
    val stepLengthCmPerson2 by viewModel.stepLengthCmPerson2.collectAsStateWithLifecycle()

    // Dialog state
    var showAddDialog by remember { mutableStateOf(false) }
    var dialogInitialDate by remember { mutableStateOf("") }
    var dialogInitialSteps by remember { mutableStateOf("") }
    var dialogInitialRemark by remember { mutableStateOf("") }
    var showStepLengthConfig by remember { mutableStateOf(false) }
    var isPersonConfigExpanded by remember { mutableStateOf(false) }
    var isAlarmConfigExpanded by remember { mutableStateOf(false) }
    var isBackupConfigExpanded by remember { mutableStateOf(false) }
    var isThemeConfigExpanded by remember { mutableStateOf(false) }
    var showExitConfirmationDialog by remember { mutableStateOf(false) }
    var showTopMenu by remember { mutableStateOf(false) }
    var showPrintPersonDialog by remember { mutableStateOf(false) }
    var activeTab by remember { mutableStateOf(0) } // 0 = Dashboard, 1 = Historie & Backup
    var visibleMonthsLimit by remember { mutableStateOf(1) }
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

    LaunchedEffect(activeTab, showStepLengthConfig) {
        if (activeTab == 0 && !showStepLengthConfig) {
            visibleMonthsLimit = 1
            if (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0) {
                try {
                    listState.animateScrollToItem(0)
                } catch (e: Exception) {
                    // ignore
                }
            }
        }
    }

    // Selected day state (for detailing the tapped bar)
    val todayStr = remember { DateUtils.getTodayString() }
    val hasTodayEntry = allEntries.any { it.date == todayStr }
    val activePersonLabel = if (selectedPerson == "person_2") person2Name else person1Name
    val currentPeriodDays = if (activePeriodType == PeriodType.WEEK) weeklyStats.daysData else monthlyStats.daysData

    var selectedDayDateStr by remember(activePeriodType, weeklyStats.mondayDateStr, monthlyStats.monthLabel) { 
        mutableStateOf(
            if (currentPeriodDays.any { it.dateStr == todayStr }) todayStr 
            else currentPeriodDays.firstOrNull()?.dateStr ?: ""
        )
    }

    var activelyClickedDateStr by remember(activeTab, activePeriodType, weeklyStats.mondayDateStr, monthlyStats.monthLabel) {
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
        containerColor = MaterialTheme.colorScheme.background, // Dynamic High Density background
        topBar = {
            // High Density Premium Custom Header in place of a standard top bar
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.85f))
                    .statusBarsPadding()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp)
                ) {
                    // 1. Title & Description side-by-side in a Row with a background
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.70f)
                        ),
                        shape = RoundedCornerShape(14.dp)
                    ) {
                        Box(modifier = Modifier.fillMaxWidth()) {
                            Image(
                                painter = painterResource(id = com.example.R.drawable.header_bg),
                                contentDescription = null,
                                contentScale = ContentScale.FillBounds,
                                modifier = Modifier.matchParentSize(),
                                alpha = 0.22f
                            )
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 16.dp, vertical = 12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Text(
                                    text = "Schrittzähler",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 24.sp,
                                    letterSpacing = (-0.5).sp,
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                                Text(
                                    text = if (showStepLengthConfig) "Konfiguration & Sicherung" else if (activeTab == 0) "Erfassung & Auswertung" else "Verlauf & Sicherung",
                                    fontSize = 14.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    fontWeight = FontWeight.SemiBold,
                                    textAlign = TextAlign.Center
                                )
                                Spacer(modifier = Modifier.height(2.dp))
                                Icon(
                                    imageVector = if (showStepLengthConfig) Icons.Default.Settings else if (activeTab == 0) Icons.Default.DirectionsRun else Icons.Default.History,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                    }

                Spacer(modifier = Modifier.height(10.dp))
                // 2. Upper action row with Backup status on left and Menu dropdown on right
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Left: Backup Status message with beautiful cloud indicators (weighted so it never overflows and pushes Menu)
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier
                            .weight(1f, fill = false)
                            .clip(RoundedCornerShape(12.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                            .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)), RoundedCornerShape(12.dp))
                            .padding(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = if (lastBackupTime == "Nie") Icons.Default.CloudQueue else Icons.Default.CloudDone,
                            contentDescription = if (lastBackupTime == "Nie") "Keine Sicherung vorhanden" else "Sicherung erfolgreich",
                            tint = if (lastBackupTime == "Nie") MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f) else MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = if (lastBackupTime == "Nie") "Sicherung: Keine" else "Gesichert: ${if (lastBackupTime.contains(":")) lastBackupTime.substringBeforeLast(":") else lastBackupTime}",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (lastBackupTime == "Nie") MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.primary,
                            fontSize = 11.sp,
                            maxLines = 1,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                        )
                    }

                    // Right: Elegant Menu Button and Dropdown Menu
                    Box {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.15f)), RoundedCornerShape(12.dp))
                                .clickable { showTopMenu = true }
                                .padding(horizontal = 8.dp, vertical = 6.dp)
                                .testTag("upper_menu_trigger_button")
                        ) {
                            // User initials / Profile avatar
                            Surface(
                                modifier = Modifier.size(24.dp),
                                shape = CircleShape,
                                color = MaterialTheme.colorScheme.primaryContainer,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                            ) {
                                Box(contentAlignment = Alignment.Center) {
                                    Text(
                                        text = initials,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                                        fontSize = 10.sp,
                                        letterSpacing = 0.sp
                                    )
                                }
                            }

                            Icon(
                                imageVector = Icons.Default.Menu,
                                contentDescription = "Menü öffnen",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp)
                            )
                        }

                        DropdownMenu(
                            expanded = showTopMenu,
                            onDismissRequest = { showTopMenu = false },
                            modifier = Modifier
                                .background(MaterialTheme.colorScheme.surface)
                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)), RoundedCornerShape(12.dp))
                        ) {
                            // Current Active Person label or switch
                            val isP1 = selectedPerson == "person_1"
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isP1) Icons.Default.CheckCircle else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isP1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "Aktiv: $person1Name",
                                        fontWeight = if (isP1) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isP1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    viewModel.selectPerson("person_1")
                                    showTopMenu = false
                                },
                                modifier = Modifier.testTag("dropdown_menu_select_p1")
                            )

                            val isP2 = selectedPerson == "person_2"
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = if (isP2) Icons.Default.CheckCircle else Icons.Default.Person,
                                        contentDescription = null,
                                        tint = if (isP2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "Aktiv: $person2Name",
                                        fontWeight = if (isP2) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isP2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    viewModel.selectPerson("person_2")
                                    showTopMenu = false
                                },
                                modifier = Modifier.testTag("dropdown_menu_select_p2")
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )

                            val isDashboardActive = activeTab == 0 && !showStepLengthConfig
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Home,
                                        contentDescription = null,
                                        tint = if (isDashboardActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "Dashboard",
                                        fontWeight = if (isDashboardActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isDashboardActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    activeTab = 0
                                    showStepLengthConfig = false
                                    showTopMenu = false
                                    coroutineScope.launch {
                                        try {
                                            listState.animateScrollToItem(0)
                                        } catch (e: Exception) {}
                                    }
                                },
                                modifier = Modifier.testTag("dropdown_menu_dashboard")
                            )

                            val isHistoryActive = activeTab == 1 && !showStepLengthConfig
                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = if (isHistoryActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "Historie",
                                        fontWeight = if (isHistoryActive) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isHistoryActive) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    activeTab = 1
                                    showStepLengthConfig = false
                                    showTopMenu = false
                                    coroutineScope.launch {
                                        try {
                                            listState.animateScrollToItem(0)
                                        } catch (e: Exception) {}
                                    }
                                },
                                modifier = Modifier.testTag("dropdown_menu_history")
                            )

                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Settings,
                                        contentDescription = null,
                                        tint = if (showStepLengthConfig) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "Einstellungen",
                                        fontWeight = if (showStepLengthConfig) FontWeight.Bold else FontWeight.Normal,
                                        color = if (showStepLengthConfig) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    showStepLengthConfig = !showStepLengthConfig
                                    showTopMenu = false
                                    if (showStepLengthConfig) {
                                        coroutineScope.launch {
                                            listState.animateScrollToItem(0)
                                        }
                                    }
                                },
                                modifier = Modifier.testTag("dropdown_menu_settings")
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )

                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.Print,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "Zusammenfassung drucken",
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                },
                                onClick = {
                                    showPrintPersonDialog = true
                                    showTopMenu = false
                                },
                                modifier = Modifier.testTag("dropdown_menu_print")
                            )

                            HorizontalDivider(
                                modifier = Modifier.padding(vertical = 4.dp),
                                color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )

                            DropdownMenuItem(
                                leadingIcon = {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.size(18.dp)
                                    )
                                },
                                text = {
                                    Text(
                                        text = "App beenden",
                                        color = MaterialTheme.colorScheme.error,
                                        fontWeight = FontWeight.Medium
                                    )
                                },
                                onClick = {
                                    showExitConfirmationDialog = true
                                    showTopMenu = false
                                },
                                modifier = Modifier.testTag("dropdown_menu_exit")
                            )
                        }
                    }
                } // Closes Row
                } // Closes inner Column
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                )
            }
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
            ) {
                HorizontalDivider(
                    thickness = 1.dp,
                    color = MaterialTheme.colorScheme.secondary.copy(alpha = 0.45f)
                )
            }
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    dialogInitialDate = DateUtils.getTodayString()
                    dialogInitialSteps = ""
                    showAddDialog = true
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = MaterialTheme.colorScheme.onPrimary,
                shape = CircleShape,
                modifier = Modifier
                    .size(56.dp)
                    .testTag("bottom_manual_add_steps_button")
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Schritte erfassen",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null
                ) {
                    activelyClickedDateStr = null
                }
        ) {
            LazyColumn(
                state = listState,
            modifier = Modifier
                .fillMaxSize()
                .padding(top = innerPadding.calculateTopPadding())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(bottom = innerPadding.calculateBottomPadding() + 16.dp)
        ) {
            // SETTINGS & BACKUP AREA (shown when gear/settings is toggled)
            if (showStepLengthConfig) {
                item {
                    Column(
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = "Einstellungen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedPerson == "person_2") person2Name else person1Name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ThemeSettingsCard(
                            viewModel = viewModel,
                            isExpanded = isThemeConfigExpanded,
                            onToggle = {
                                isThemeConfigExpanded = !isThemeConfigExpanded
                                if (isThemeConfigExpanded) {
                                    isPersonConfigExpanded = false
                                    isAlarmConfigExpanded = false
                                    isBackupConfigExpanded = false
                                }
                            }
                        )

                        PersonSettingsCard(
                            person1Name = person1Name,
                            onPerson1NameChanged = { viewModel.updatePerson1Name(it) },
                            stepLengthCmPerson1 = stepLengthCmPerson1,
                            onStepLengthCmPerson1Changed = { viewModel.updateStepLengthPerson1(it) },
                            person2Name = person2Name,
                            onPerson2NameChanged = { viewModel.updatePerson2Name(it) },
                            stepLengthCmPerson2 = stepLengthCmPerson2,
                            onStepLengthCmPerson2Changed = { viewModel.updateStepLengthPerson2(it) },
                            isExpanded = isPersonConfigExpanded,
                            onToggle = {
                                isPersonConfigExpanded = !isPersonConfigExpanded
                                if (isPersonConfigExpanded) {
                                    isThemeConfigExpanded = false
                                    isAlarmConfigExpanded = false
                                    isBackupConfigExpanded = false
                                }
                            }
                        )

                        AlarmSettingsCard(
                            viewModel = viewModel,
                            isExpanded = isAlarmConfigExpanded,
                            onToggle = {
                                isAlarmConfigExpanded = !isAlarmConfigExpanded
                                if (isAlarmConfigExpanded) {
                                    isThemeConfigExpanded = false
                                    isPersonConfigExpanded = false
                                    isBackupConfigExpanded = false
                                }
                            }
                        )

                        LocalBackupCard(
                            viewModel = viewModel,
                            isExpanded = isBackupConfigExpanded,
                            onToggle = {
                                isBackupConfigExpanded = !isBackupConfigExpanded
                                if (isBackupConfigExpanded) {
                                    isThemeConfigExpanded = false
                                    isPersonConfigExpanded = false
                                    isAlarmConfigExpanded = false
                                }
                            }
                        )
                    }
                }
            } else {
                if (activeTab == 0) {
                    item {
                        Column(
                            modifier = Modifier.padding(top = 8.dp)
                        ) {
                            Text(
                                text = "Erfassung",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = if (selectedPerson == "person_2") person2Name else person1Name,
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

            // TODAY LOGGING CTA CARD: Gorgeous banner styled dynamically based on active person's today entry
            item {
                val cardBg = if (hasTodayEntry) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                val cardBorderColor = if (hasTodayEntry) MaterialTheme.colorScheme.outline.copy(alpha = 0.5f) else MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
                val cardBorderWidth = if (hasTodayEntry) 1.dp else 1.5.dp

                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("today_quick_cta_card"),
                    shape = RoundedCornerShape(24.dp),
                    colors = CardDefaults.cardColors(
                        containerColor = cardBg
                    ),
                    border = BorderStroke(cardBorderWidth, cardBorderColor)
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
                                    text = if (hasTodayEntry) "SCHRITTVERLAUF HEUTE AKTUELL" else "SCHRITTVERLAUF SCHNELL ERFASSEN",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    letterSpacing = 1.sp
                                )
                                if (hasTodayEntry) {
                                    val todaySteps = allEntries.find { it.date == todayStr }?.steps ?: 0
                                    Column(
                                        horizontalAlignment = Alignment.Start,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp))
                                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)), shape = RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = activePersonLabel,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = "hat heute bereits $todaySteps Schritte!",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                } else {
                                    Column(
                                        horizontalAlignment = Alignment.Start,
                                        verticalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier.padding(top = 4.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(MaterialTheme.colorScheme.surface, shape = RoundedCornerShape(8.dp))
                                                .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)), shape = RoundedCornerShape(8.dp))
                                                .padding(horizontal = 8.dp, vertical = 2.dp)
                                        ) {
                                            Text(
                                                text = activePersonLabel,
                                                fontSize = 11.sp,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        Text(
                                            text = "Heute fehlen noch Einträge.",
                                            fontSize = 13.sp,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }
                                }
                            }

                            // CTA Button with steps image as requested
                            Surface(
                                shape = RoundedCornerShape(16.dp),
                                color = MaterialTheme.colorScheme.primary,
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
                                text = "Eintragen:",
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(end = 4.dp)
                            )
                            val presets = if (hasTodayEntry) {
                                listOf(1000 to "+1.000", 3000 to "+3.000", 5000 to "+5.000")
                            } else {
                                listOf(3000 to "3.000", 6000 to "6.000", 10000 to "10.000")
                            }
                            presets.forEach { (amount, textStr) ->
                                Surface(
                                    shape = RoundedCornerShape(8.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
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
                                            color = MaterialTheme.colorScheme.primary,
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
                        if (activePeriodType == PeriodType.WEEK) {
                            viewModel.navigateToCurrentWeek()
                        } else {
                            viewModel.navigateToCurrentMonth()
                        }
                    },
                    onPrint = {
                        showPrintPersonDialog = true
                    }
                )
            }

            // GRAPHICAL PERIOD BAR CHART (Woche / Monat in custom card: adaptive theme colors)
            item {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.surface
                    ),
                    shape = RoundedCornerShape(28.dp),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
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
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (activePeriodType == PeriodType.WEEK) "Wöchentliche Auswertung (Mo - So)" else "Monatliche Auswertung",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        // Unified Switcher Deck: Side-by-side selectors for Person and Period with custom theme mapping
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // 1. Person Selector with custom distinct colors (Person 1 -> Primary, Person 2 -> Secondary)
                            Row(
                                modifier = Modifier
                                    .weight(1.2f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("person_1" to person1Name, "person_2" to person2Name).forEach { (personId, name) ->
                                    val isSelected = personId == selectedPerson
                                    val activeColor = if (personId == "person_1") MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary
                                    val onActiveColor = if (personId == "person_1") MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSecondary
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) activeColor else Color.Transparent)
                                            .clickable { viewModel.selectPerson(personId) }
                                            .padding(vertical = 8.dp)
                                            .testTag("activity_chart_person_tab_$personId"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isSelected) Icons.Default.CheckCircle else Icons.Default.Person,
                                                contentDescription = null,
                                                tint = if (isSelected) onActiveColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(14.dp)
                                            )
                                            Text(
                                                text = name,
                                                style = MaterialTheme.typography.bodySmall,
                                                fontWeight = FontWeight.Bold,
                                                color = if (isSelected) onActiveColor else MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1
                                            )
                                        }
                                    }
                                }
                            }

                            // 2. Period Selector (Woche vs Monat)
                            Row(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f))
                                    .border(BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)), RoundedCornerShape(12.dp))
                                    .padding(4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf(PeriodType.WEEK to "Woche", PeriodType.MONTH to "Monat").forEach { (type, label) ->
                                    val isSelected = type == activePeriodType
                                    Box(
                                        modifier = Modifier
                                            .weight(1f)
                                            .clip(RoundedCornerShape(8.dp))
                                            .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                                            .clickable { viewModel.setPeriodType(type) }
                                            .padding(vertical = 8.dp)
                                            .testTag("activity_chart_period_tab_${type.name.lowercase()}"),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Bold,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        val hasAnyEntries = allEntries.isNotEmpty() || inactiveEntries.isNotEmpty()

                        if (hasAnyEntries) {
                            if (activePeriodType == PeriodType.WEEK) {
                                WeeklyBarGraph(
                                    daysData = weeklyStats.daysData,
                                    inactiveDaysData = inactiveWeeklyStats.daysData,
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
                                    inactiveDaysData = inactiveMonthlyStats.daysData,
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
                                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f))
                                        .border(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "$dayLabelLong, $displayDate",
                                            fontWeight = FontWeight.Bold,
                                            style = MaterialTheme.typography.bodyMedium,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = if (day.steps > 0) {
                                                "%, d Schritte • %.2f km".format(Locale.GERMANY, day.steps, day.distanceKm)
                                            } else {
                                                "Keine Schritte erfasst"
                                            },
                                            style = MaterialTheme.typography.bodySmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
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
                                                tint = MaterialTheme.colorScheme.primary,
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
                        } else {
                            // Onboarding / Empty state representation when there are no entries for this person
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 24.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.padding(12.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.DirectionsWalk,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(48.dp)
                                    )
                                    Text(
                                        text = "Kein Schrittverlauf vorhanden",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        textAlign = TextAlign.Center
                                    )
                                    Text(
                                        text = "Für $activePersonLabel sind bisher noch keine Schritte dokumentiert. Klicke oben auf '+ Schritte', um deinen Verlauf zu starten, oder lade testweise eine Muster-Woche!",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        lineHeight = 16.sp
                                    )

                                    Button(
                                        onClick = { viewModel.generateDemoWeekData(context) },
                                        colors = ButtonDefaults.buttonColors(
                                            containerColor = MaterialTheme.colorScheme.primary,
                                            contentColor = MaterialTheme.colorScheme.onPrimary
                                        ),
                                        shape = RoundedCornerShape(12.dp),
                                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.AutoAwesome,
                                            contentDescription = null,
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Muster-Woche eintragen", fontWeight = FontWeight.Bold, fontSize = 11.sp)
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
                    color = MaterialTheme.colorScheme.onSurface,
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

                val visibleMonthPrefixes = getVisibleMonthPrefixes(todayStr, visibleMonthsLimit)
                val visibleHistoryDays = historyDays.filter { day ->
                    visibleMonthPrefixes.contains(day.dateStr.take(7))
                }

                val hasOlderEntries = historyDays.any { day ->
                    !visibleMonthPrefixes.contains(day.dateStr.take(7))
                }

                item {
                    Column(
                        modifier = Modifier.padding(top = 8.dp)
                    ) {
                        Text(
                            text = "Historie",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (selectedPerson == "person_2") person2Name else person1Name,
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                if (visibleHistoryDays.isNotEmpty()) {
                    item {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 520.dp)
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            visibleHistoryDays.forEach { day ->
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
                } else if (historyDays.isNotEmpty()) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Keine Einträge für ${if (selectedPerson == "person_2") person2Name else person1Name} im aktuellen Zeitraum geladen.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFF49454F)
                            )
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
                                text = "Keine Einträge für ${if (selectedPerson == "person_2") person2Name else person1Name} vorhanden.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                if (hasOlderEntries) {
                    item {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Button(
                                onClick = { visibleMonthsLimit++ },
                                shape = RoundedCornerShape(14.dp),
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("load_more_entries_btn")
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = null,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Text(
                                        text = "Weitere Einträge laden?",
                                        fontWeight = FontWeight.SemiBold,
                                        fontSize = 14.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
            } // end of Box wrapping LazyColumn
        }
    }
}

    // Step entry modal Dialog
    if (showAddDialog) {
        StepEntryDialog(
            initialDateStr = dialogInitialDate,
            initialStepsStr = dialogInitialSteps,
            initialRemarkStr = dialogInitialRemark,
            selectedPerson = selectedPerson,
            person1Name = person1Name,
            person2Name = person2Name,
            onDismiss = { showAddDialog = false },
            onSave = { date, steps, remark, personId ->
                viewModel.saveSteps(date, steps, remark, personId)
                if (personId != selectedPerson) {
                    viewModel.selectPerson(personId)
                }
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
                    color = MaterialTheme.colorScheme.onSurface
                )
            },
            text = {
                Text(
                    text = "Möchten Sie die App wirklich beenden?",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitConfirmationDialog = false
                        (context as? Activity)?.finishAndRemoveTask()
                    }
                ) {
                    Text("Ja", color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExitConfirmationDialog = false }
                ) {
                    Text("Nein", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(16.dp)
        )
    }

    if (showPrintPersonDialog) {
        var chosenPrintPerson by remember(showPrintPersonDialog) { mutableStateOf(selectedPerson) }
        var chosenPrintMonthDateStr by remember(showPrintPersonDialog, monthlyStats.monthDateStr) {
            mutableStateOf(
                if (monthlyStats.monthDateStr.isNotEmpty()) monthlyStats.monthDateStr
                else DateUtils.getCurrentMonthFirstDay()
            )
        }
        var isMonthDropdownExpanded by remember { mutableStateOf(false) }

        val availableMonths = remember(rawAllEntries) {
            DateUtils.getAvailableMonthsList(rawAllEntries)
        }

        val previewStats = remember(chosenPrintPerson, chosenPrintMonthDateStr, rawAllEntries, stepLengthCmPerson1, stepLengthCmPerson2) {
            viewModel.calculateMonthlyStats(chosenPrintPerson, chosenPrintMonthDateStr)
        }

        val chosenPersonName = if (chosenPrintPerson == "person_2") person2Name else person1Name
        val chosenStepLength = if (chosenPrintPerson == "person_2") stepLengthCmPerson2 else stepLengthCmPerson1

        AlertDialog(
            onDismissRequest = { showPrintPersonDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(24.dp)
                    )
                    Text(
                        text = "Zusammenfassung drucken",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        style = MaterialTheme.typography.titleLarge
                    )
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Text(
                        text = "Wählen Sie Person und Monat für die Druckzusammenfassung aus:",
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        style = MaterialTheme.typography.bodyMedium
                    )

                    // 1. Person Selection
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(
                            text = "Person:",
                            color = MaterialTheme.colorScheme.onSurface,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            val isP1 = chosenPrintPerson == "person_1"
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { chosenPrintPerson = "person_1" }
                                    .testTag("print_select_person_1"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isP1) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    width = if (isP1) 2.dp else 1.dp,
                                    color = if (isP1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = if (isP1) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = person1Name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isP1) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isP1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isP1) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Ausgewählt",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }

                            val isP2 = chosenPrintPerson == "person_2"
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { chosenPrintPerson = "person_2" }
                                    .testTag("print_select_person_2"),
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = if (isP2) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface
                                ),
                                border = BorderStroke(
                                    width = if (isP2) 2.dp else 1.dp,
                                    color = if (isP2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)
                                )
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 10.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        modifier = Modifier.weight(1f, fill = false)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Person,
                                            contentDescription = null,
                                            tint = if (isP2) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Text(
                                            text = person2Name,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isP2) FontWeight.Bold else FontWeight.Normal,
                                            color = if (isP2) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                    if (isP2) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Ausgewählt",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // 2. Month Selection
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Monat:",
                                color = MaterialTheme.colorScheme.onSurface,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold
                            )
                            val currentMonthFirstDay = DateUtils.getCurrentMonthFirstDay()
                            if (DateUtils.getMonthLabel(chosenPrintMonthDateStr) != DateUtils.getMonthLabel(currentMonthFirstDay)) {
                                TextButton(
                                    onClick = { chosenPrintMonthDateStr = currentMonthFirstDay },
                                    contentPadding = PaddingValues(horizontal = 6.dp, vertical = 0.dp),
                                    modifier = Modifier.testTag("print_reset_to_current_month")
                                ) {
                                    Text(
                                        text = "Aktueller Monat",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                            ),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                IconButton(
                                    onClick = {
                                        chosenPrintMonthDateStr = DateUtils.getPreviousMonthFirstDay(chosenPrintMonthDateStr)
                                    },
                                    modifier = Modifier.testTag("print_prev_month_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                                        contentDescription = "Vorheriger Monat",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }

                                Box {
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
                                        modifier = Modifier
                                            .clickable { isMonthDropdownExpanded = true }
                                            .testTag("print_month_dropdown_trigger")
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.CalendarToday,
                                                contentDescription = null,
                                                tint = MaterialTheme.colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = DateUtils.getMonthLabel(chosenPrintMonthDateStr),
                                                style = MaterialTheme.typography.bodyMedium,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurface
                                            )
                                            Icon(
                                                imageVector = Icons.Default.ArrowDropDown,
                                                contentDescription = "Monat wählen",
                                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.size(20.dp)
                                            )
                                        }
                                    }

                                    DropdownMenu(
                                        expanded = isMonthDropdownExpanded,
                                        onDismissRequest = { isMonthDropdownExpanded = false },
                                        modifier = Modifier.heightIn(max = 280.dp)
                                    ) {
                                        availableMonths.forEach { (firstDayStr, label) ->
                                            val isSelected = DateUtils.getMonthLabel(firstDayStr) == DateUtils.getMonthLabel(chosenPrintMonthDateStr)
                                            DropdownMenuItem(
                                                text = {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = label,
                                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                                                        )
                                                        if (isSelected) {
                                                            Icon(
                                                                imageVector = Icons.Default.Check,
                                                                contentDescription = null,
                                                                tint = MaterialTheme.colorScheme.primary,
                                                                modifier = Modifier.size(16.dp)
                                                            )
                                                        }
                                                    }
                                                },
                                                onClick = {
                                                    chosenPrintMonthDateStr = firstDayStr
                                                    isMonthDropdownExpanded = false
                                                }
                                            )
                                        }
                                    }
                                }

                                IconButton(
                                    onClick = {
                                        chosenPrintMonthDateStr = DateUtils.getNextMonthFirstDay(chosenPrintMonthDateStr)
                                    },
                                    modifier = Modifier.testTag("print_next_month_button")
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                                        contentDescription = "Nächster Monat",
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                        }
                    }

                    // 3. Live Preview Card
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.25f))
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Vorschau (${chosenPersonName})",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = "${previewStats.trackedDaysCount} von ${previewStats.daysData.size} Tagen",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "${String.format(Locale.GERMANY, "%,d", previewStats.totalSteps)} Schritte",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Text(
                                        text = "Distanz: ${String.format(Locale.GERMANY, "%.2f", previewStats.totalDistanceKm)} km",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }

                                if (previewStats.trackedDaysCount > 0) {
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(
                                            text = "Ø ${String.format(Locale.GERMANY, "%,d", previewStats.averageSteps.toInt())}",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = "Schritte / Tag",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            if (previewStats.trackedDaysCount == 0) {
                                Text(
                                    text = "(Für diesen Monat liegen noch keine Einträge vor)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                                    fontStyle = androidx.compose.ui.text.font.FontStyle.Italic
                                )
                            }
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val statsToPrint = viewModel.calculateMonthlyStats(chosenPrintPerson, chosenPrintMonthDateStr)
                        printMonthlyReport(
                            context = context,
                            monthLabel = statsToPrint.monthLabel,
                            stats = statsToPrint,
                            stepLengthCm = chosenStepLength,
                            personName = chosenPersonName
                        )
                        showPrintPersonDialog = false
                    },
                    modifier = Modifier.testTag("print_confirm_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Drucken", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { showPrintPersonDialog = false },
                    modifier = Modifier.testTag("print_cancel_button")
                ) {
                    Text("Abbrechen", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            },
            containerColor = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(20.dp)
        )
    }
}

@Composable
fun PersonSelectorRow(
    selectedPerson: String,
    person1Name: String,
    person2Name: String,
    onPersonSelected: (String) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("person_selector_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "AKTIVER BENUTZER",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary,
                letterSpacing = 1.sp
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Person 1 Segment
                val isP1 = selectedPerson == "person_1"
                Button(
                    onClick = { onPersonSelected("person_1") },
                    modifier = Modifier.weight(1f).height(44.dp).testTag("select_person_1"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isP1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (isP1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = if (isP1) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isP1) Icons.Default.CheckCircle else Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = person1Name,
                            fontWeight = if (isP1) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                // Person 2 Segment
                val isP2 = selectedPerson == "person_2"
                Button(
                    onClick = { onPersonSelected("person_2") },
                    modifier = Modifier.weight(1f).height(44.dp).testTag("select_person_2"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isP2) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        contentColor = if (isP2) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                    ),
                    border = if (isP2) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f)),
                    contentPadding = PaddingValues(horizontal = 8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = if (isP2) Icons.Default.CheckCircle else Icons.Default.Person,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = person2Name,
                            fontWeight = if (isP2) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 13.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PersonSettingsCard(
    person1Name: String,
    onPerson1NameChanged: (String) -> Unit,
    stepLengthCmPerson1: Int,
    onStepLengthCmPerson1Changed: (Int) -> Unit,
    person2Name: String,
    onPerson2NameChanged: (String) -> Unit,
    stepLengthCmPerson2: Int,
    onStepLengthCmPerson2Changed: (Int) -> Unit,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("person_settings_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Clickable Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.People,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Profile & Schrittlängen",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isExpanded) "Namen & Schrittlängen anpassen" else "Personennamen & Schrittlängen einstellen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Zuklappen" else "Aufklappen",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // -- PERSON 1 SECTION --
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Person 1 (Standard)",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "$stepLengthCmPerson1 cm",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    OutlinedTextField(
                        value = person1Name,
                        onValueChange = onPerson1NameChanged,
                        label = { Text("Name Person 1") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_p1_name"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { onStepLengthCmPerson1Changed((stepLengthCmPerson1 - 1).coerceAtLeast(30)) },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = "Verringern", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        }

                        Slider(
                            value = stepLengthCmPerson1.toFloat(),
                            onValueChange = { onStepLengthCmPerson1Changed(it.toInt()) },
                            valueRange = 30f..150f,
                            steps = 120,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = { onStepLengthCmPerson1Changed((stepLengthCmPerson1 + 1).coerceAtMost(150)) },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Erhöhen", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                // -- PERSON 2 SECTION --
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Person 2",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "$stepLengthCmPerson2 cm",
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    OutlinedTextField(
                        value = person2Name,
                        onValueChange = onPerson2NameChanged,
                        label = { Text("Name Person 2") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().testTag("edit_p2_name"),
                        shape = RoundedCornerShape(12.dp),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = MaterialTheme.colorScheme.onSurface,
                            unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            focusedLabelColor = MaterialTheme.colorScheme.primary,
                            unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unfocusedBorderColor = MaterialTheme.colorScheme.outline
                        )
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        IconButton(
                            onClick = { onStepLengthCmPerson2Changed((stepLengthCmPerson2 - 1).coerceAtLeast(30)) },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Remove, contentDescription = "Verringern", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        }

                        Slider(
                            value = stepLengthCmPerson2.toFloat(),
                            onValueChange = { onStepLengthCmPerson2Changed(it.toInt()) },
                            valueRange = 30f..150f,
                            steps = 120,
                            colors = SliderDefaults.colors(
                                thumbColor = MaterialTheme.colorScheme.primary,
                                activeTrackColor = MaterialTheme.colorScheme.primary,
                                inactiveTrackColor = MaterialTheme.colorScheme.outlineVariant
                            ),
                            modifier = Modifier.weight(1f)
                        )

                        IconButton(
                            onClick = { onStepLengthCmPerson2Changed((stepLengthCmPerson2 + 1).coerceAtMost(150)) },
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                            modifier = Modifier.size(36.dp)
                        ) {
                            Icon(imageVector = Icons.Default.Add, contentDescription = "Erhöhen", tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(16.dp))
                        }
                    }
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
    onCurrent: () -> Unit,
    onPrint: (() -> Unit)? = null
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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
                    tint = MaterialTheme.colorScheme.primary
                )
            }

            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = if (activePeriodType == PeriodType.WEEK) "WÖCHENTLICHE AUSWAHL" else "MONATSAUSWAHL",
                    fontSize = 10.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 0.5.sp
                )
                Text(
                    text = displayRange,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
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
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
            }

            if (activePeriodType == PeriodType.MONTH && onPrint != null) {
                IconButton(
                    onClick = onPrint,
                    modifier = Modifier.testTag("month_print_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Print,
                        contentDescription = "Monatsbericht drucken",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            IconButton(
                onClick = onNext,
                modifier = Modifier.testTag("next_period_button")
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                    contentDescription = if (activePeriodType == PeriodType.WEEK) "Nächste Woche" else "Nächster Monat",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Composable
fun MonthlyBarGraph(
    daysData: List<DayStepData>,
    inactiveDaysData: List<DayStepData> = emptyList(),
    selectedDateStr: String,
    activelyClickedDateStr: String?,
    onDaySelected: (String) -> Unit,
    onSwipePrevMonth: () -> Unit,
    onSwipeNextMonth: () -> Unit
) {
    val maxActive = daysData.maxOfOrNull { it.steps } ?: 0
    val maxInactive = inactiveDaysData.maxOfOrNull { it.steps } ?: 0
    val maxSteps = maxOf(maxActive, maxInactive)
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

                val inactiveDay = inactiveDaysData.find { it.dateStr == day.dateStr }
                val inactiveSteps = inactiveDay?.steps ?: 0
                
                val targetFraction = day.steps.toFloat() / scaleMax
                val animatedFraction by animateFloatAsState(
                    targetValue = targetFraction.coerceIn(0.02f, 1f),
                    label = "bar_height"
                )

                val inactiveTargetFraction = inactiveSteps.toFloat() / scaleMax
                val animatedInactiveFraction by animateFloatAsState(
                    targetValue = inactiveTargetFraction.coerceIn(0.02f, 1f),
                    label = "inactive_bar_height"
                )

                val animatedBarColor by animateColorAsState(
                    targetValue = when {
                        isSelected -> MaterialTheme.colorScheme.primary
                        day.steps >= 10000 -> MaterialTheme.colorScheme.primary
                        day.steps > 0 -> MaterialTheme.colorScheme.primaryContainer
                        else -> MaterialTheme.colorScheme.surfaceVariant
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
                        val hasDualBars = inactiveSteps > 0
                        val barWidth = if (hasDualBars) 0.45f else 0.75f
                        val activeOffset = if (hasDualBars) 2.5.dp else 0.dp
                        val inactiveOffset = if (hasDualBars) (-2.5).dp else 0.dp

                        // Inactive Bar (drawn behind and offset)
                        if (inactiveSteps > 0) {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth(barWidth)
                                    .fillMaxHeight(animatedInactiveFraction.coerceIn(0.02f, 1f))
                                    .offset(x = inactiveOffset)
                                    .clip(RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp))
                                    .background(
                                        Brush.verticalGradient(
                                            colors = listOf(
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                                MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                                            )
                                        )
                                    )
                            )
                        }

                        // Active Bar
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(barWidth)
                                .fillMaxHeight(animatedFraction.coerceIn(0.02f, 1f))
                                .offset(x = activeOffset)
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
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.1f)
                                            )
                                        )
                                    }
                                )
                                .then(
                                    if (day.steps > 0) {
                                        Modifier.border(
                                            width = if (isSelected) 1.8.dp else 0.8.dp,
                                            color = MaterialTheme.colorScheme.primary,
                                            shape = RoundedCornerShape(topStart = 4.dp, topEnd = 4.dp)
                                        )
                                    } else if (isSelected) {
                                        Modifier.border(
                                            width = 1.1.dp,
                                            color = MaterialTheme.colorScheme.primary,
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
                            .height(38.dp)
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
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Box(
                                    modifier = Modifier
                                        .size(3.dp)
                                        .clip(CircleShape)
                                        .background(MaterialTheme.colorScheme.primary)
                                )
                            }
                        } else if (isLabelDay) {
                            Text(
                                text = dateNum.toString(),
                                style = MaterialTheme.typography.bodySmall,
                                fontSize = 8.5.sp,
                                fontWeight = FontWeight.Normal,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                              )
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(top = 4.dp)
                                    .size(2.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.outline.copy(alpha = 0.6f))
                            )
                        }

                        Row(
                            modifier = Modifier.padding(top = 1.dp).height(10.dp),
                            horizontalArrangement = Arrangement.spacedBy(1.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (day.remark.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubble,
                                    contentDescription = "Notiz von aktiver Person vorhanden",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(7.dp)
                                )
                            }
                            if (inactiveDay != null && inactiveDay.remark.isNotEmpty()) {
                                Icon(
                                    imageVector = Icons.Default.ChatBubble,
                                    contentDescription = "Notiz von inaktiver Person vorhanden",
                                    tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                    modifier = Modifier.size(7.dp)
                                )
                            }
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
    inactiveDaysData: List<DayStepData> = emptyList(),
    selectedDateStr: String,
    activelyClickedDateStr: String?,
    onDaySelected: (String) -> Unit,
    onSwipePrevWeek: () -> Unit,
    onSwipeNextWeek: () -> Unit
) {
    val maxActive = daysData.maxOfOrNull { it.steps } ?: 0
    val maxInactive = inactiveDaysData.maxOfOrNull { it.steps } ?: 0
    val maxSteps = maxOf(maxActive, maxInactive)
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

            val inactiveDay = inactiveDaysData.find { it.dateStr == day.dateStr }
            val inactiveSteps = inactiveDay?.steps ?: 0
            
            // Animated height fractions
            val targetFraction = day.steps.toFloat() / scaleMax
            val animatedFraction by animateFloatAsState(
                targetValue = targetFraction.coerceIn(0.02f, 1f),
                label = "bar_height"
            )

            val inactiveTargetFraction = inactiveSteps.toFloat() / scaleMax
            val animatedInactiveFraction by animateFloatAsState(
                targetValue = inactiveTargetFraction.coerceIn(0.02f, 1f),
                label = "inactive_bar_height"
            )

            // High Density style colors
            val animatedBarColor by animateColorAsState(
                targetValue = when {
                    isSelected -> MaterialTheme.colorScheme.primary // Selected
                    day.steps >= 10000 -> MaterialTheme.colorScheme.primary // Target met
                    day.steps > 0 -> MaterialTheme.colorScheme.primaryContainer // Active steps
                    else -> MaterialTheme.colorScheme.surfaceVariant // Backdrop empty
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
                            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
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
                    val hasDualBars = inactiveSteps > 0
                    val barWidth = if (hasDualBars) 0.36f else 0.55f
                    val activeOffset = if (hasDualBars) 4.5.dp else 0.dp
                    val inactiveOffset = if (hasDualBars) (-4.5).dp else 0.dp

                    // Inactive Bar (drawn behind and offset)
                    if (inactiveSteps > 0) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(barWidth)
                                .fillMaxHeight(animatedInactiveFraction.coerceIn(0.02f, 1f))
                                .offset(x = inactiveOffset)
                                .clip(RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp))
                                .background(
                                    Brush.verticalGradient(
                                        colors = listOf(
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
                                            MaterialTheme.colorScheme.secondary.copy(alpha = 0.25f)
                                        )
                                    )
                                )
                        )
                    }

                    // Bar (Active person)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(barWidth)
                            .fillMaxHeight(animatedFraction.coerceIn(0.02f, 1f))
                            .offset(x = activeOffset)
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
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.15f)
                                        )
                                    )
                                }
                            )
                            .then(
                                if (day.steps > 0) {
                                    Modifier.border(
                                        width = if (isSelected) 2.5.dp else 1.2.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
                                    )
                                } else if (isSelected) {
                                    Modifier.border(
                                        width = 1.5.dp,
                                        color = MaterialTheme.colorScheme.primary,
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
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Calendar date label (dd.)
                val dateNum = day.dateStr.split("-").lastOrNull()?.removePrefix("0") ?: ""
                Text(
                    text = dateNum,
                    style = MaterialTheme.typography.labelSmall,
                    fontSize = 9.sp,
                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Light,
                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )

                Row(
                    modifier = Modifier.height(14.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (day.remark.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "Notiz von aktiver Person vorhanden",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(9.dp)
                        )
                    }
                    if (inactiveDay != null && inactiveDay.remark.isNotEmpty()) {
                        Icon(
                            imageVector = Icons.Default.ChatBubble,
                            contentDescription = "Notiz von inaktiver Person vorhanden",
                            tint = MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f),
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
                    color = MaterialTheme.colorScheme.inverseSurface,
                    shape = RoundedCornerShape(12.dp),
                    shadowElevation = 6.dp,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.inverseOnSurface.copy(alpha = 0.4f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notes,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = remark,
                            color = MaterialTheme.colorScheme.inverseOnSurface,
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
                        .background(MaterialTheme.colorScheme.inverseSurface)
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
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
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(18.dp))

            Column {
                Text(
                    text = value,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "Aktive Auswertung",
                        fontSize = 9.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
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
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.5f))
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.primaryContainer,
                modifier = Modifier.size(40.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.DirectionsRun,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
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
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = "•",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.outline
                    )
                    Text(
                        text = "$weekday, $displayDate",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                Text(
                    text = "%, d Schritte • %.2f km".format(Locale.GERMANY, dayData.steps, dayData.distanceKm),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                if (dayData.remark.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant
                    ) {
                        Text(
                            text = dayData.remark,
                            style = MaterialTheme.typography.bodySmall,
                            fontStyle = androidx.compose.ui.text.font.FontStyle.Italic,
                            color = MaterialTheme.colorScheme.primary,
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
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StepEntryDialog(
    initialDateStr: String,
    initialStepsStr: String,
    initialRemarkStr: String = "",
    selectedPerson: String,
    person1Name: String,
    person2Name: String,
    onDismiss: () -> Unit,
    onSave: (String, Int, String, String) -> Unit
) {
    val context = LocalContext.current
    var dateStr by remember { mutableStateOf(initialDateStr) }
    var stepsField by remember { mutableStateOf(initialStepsStr) }
    var remarkField by remember { mutableStateOf(initialRemarkStr) }
    var chosenPerson by remember { mutableStateOf(selectedPerson) }
    var showErrorMsg by remember { mutableStateOf("") }
    var showComposeDatePicker by remember { mutableStateOf(false) }

    val formattedGermanDate = remember(dateStr) {
        DateUtils.formatGermanDate(dateStr)
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 24.dp)
                .clickable(
                    onClick = onDismiss,
                    indication = null,
                    interactionSource = remember { MutableInteractionSource() }
                ),
            contentAlignment = Alignment.TopCenter
        ) {
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        enabled = true,
                        onClick = {},
                        indication = null,
                        interactionSource = remember { MutableInteractionSource() }
                    ),
                shape = RoundedCornerShape(28.dp),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 6.dp
            ) {
                Column(
                    modifier = Modifier.padding(24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = if (initialStepsStr.isEmpty()) "Schritte erfassen" else "Eintrag bearbeiten",
                        fontWeight = FontWeight.Bold,
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // PERSON SELECTION
                        Column(
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Text(
                                text = "Person auswählen",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary,
                                letterSpacing = 0.5.sp
                            )
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val isP1 = chosenPerson == "person_1"
                                val isP2 = chosenPerson == "person_2"
                                
                                Button(
                                    onClick = { chosenPerson = "person_1" },
                                    modifier = Modifier.weight(1f).height(44.dp).testTag("dialog_select_person_1"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isP1) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        contentColor = if (isP1) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = if (isP1) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isP1) Icons.Default.CheckCircle else Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = person1Name,
                                            fontWeight = if (isP1) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }

                                Button(
                                    onClick = { chosenPerson = "person_2" },
                                    modifier = Modifier.weight(1f).height(44.dp).testTag("dialog_select_person_2"),
                                    shape = RoundedCornerShape(12.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isP2) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                                        contentColor = if (isP2) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
                                    ),
                                    border = if (isP2) null else BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                    contentPadding = PaddingValues(horizontal = 8.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (isP2) Icons.Default.CheckCircle else Icons.Default.Person,
                                            contentDescription = null,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = person2Name,
                                            fontWeight = if (isP2) FontWeight.Bold else FontWeight.Medium,
                                            fontSize = 12.sp,
                                            textAlign = TextAlign.Center,
                                            maxLines = 1,
                                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }

                        // DATE PICKER FIELD
                        OutlinedButton(
                            onClick = {
                                showComposeDatePicker = true
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(56.dp)
                                .testTag("dialog_date_select"),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Icon(Icons.Default.CalendarToday, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                    Text(
                                        text = "Datum: $formattedGermanDate",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                                Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
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
                            label = { Text("Schritte") },
                            placeholder = { Text("z.B. 10000") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_steps_input"),
                            leadingIcon = {
                                Icon(Icons.Default.DirectionsRun, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                if (stepsField.isNotEmpty()) {
                                    IconButton(onClick = { stepsField = "" }) {
                                          Icon(Icons.Default.Clear, contentDescription = "Eingabe löschen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        )

                        // REMARK FIELD
                        OutlinedTextField(
                            value = remarkField,
                            onValueChange = { remarkField = it },
                            label = { Text("Bemerkung") },
                            placeholder = { Text("z.B. Abendspaziergang") },
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedLabelColor = MaterialTheme.colorScheme.primary,
                                unfocusedLabelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                cursorColor = MaterialTheme.colorScheme.primary
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("dialog_remark_input"),
                            leadingIcon = {
                                Icon(Icons.Default.Notes, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                            },
                            trailingIcon = {
                                if (remarkField.isNotEmpty()) {
                                    IconButton(onClick = { remarkField = "" }) {
                                        Icon(Icons.Default.Clear, contentDescription = "Eingabe löschen", tint = MaterialTheme.colorScheme.onSurfaceVariant)
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
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.weight(1f).testTag("quick_add_1k"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("+1.000", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { addAmount(5000) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.weight(1f).testTag("quick_add_5k"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("+5.000", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { addAmount(10000) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer
                                ),
                                modifier = Modifier.weight(1f).testTag("quick_add_10k"),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text("+10.000", fontSize = 11.sp, fontWeight = FontWeight.Bold)
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

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        TextButton(
                            onClick = onDismiss,
                            modifier = Modifier.testTag("dialog_cancel_button")
                        ) {
                            Text("Abbrechen", color = MaterialTheme.colorScheme.primary)
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Button(
                            onClick = {
                                val parsed = stepsField.toIntOrNull()
                                if (parsed == null || parsed < 0) {
                                    showErrorMsg = "Bitte gib eine gültige Anzahl an Schritten ein."
                                } else if (parsed > 1000000) {
                                    showErrorMsg = "Das ist eine unglaubliche Zahl! Bitte erfasse Schritte unter 1.000.000."
                                } else {
                                    onSave(dateStr, parsed, remarkField, chosenPerson)
                                }
                            },
                            modifier = Modifier.testTag("dialog_save_button"),
                            shape = RoundedCornerShape(12.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = MaterialTheme.colorScheme.primary,
                                contentColor = MaterialTheme.colorScheme.onPrimary
                            )
                        ) {
                            Text("Speichern")
                        }
                    }
                }
            }
        }
    }

    if (showComposeDatePicker) {
        val initialMs = remember(dateStr) {
            try {
                val parts = dateStr.split("-")
                if (parts.size == 3) {
                    val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                    cal.clear()
                    cal.set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
                    cal.timeInMillis
                } else {
                    System.currentTimeMillis()
                }
            } catch (e: Exception) {
                System.currentTimeMillis()
            }
        }
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMs
        )
        
        DatePickerDialog(
            onDismissRequest = { showComposeDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        datePickerState.selectedDateMillis?.let { ms ->
                            val cal = Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                            cal.timeInMillis = ms
                            val y = cal.get(Calendar.YEAR)
                            val m = cal.get(Calendar.MONTH) + 1
                            val d = cal.get(Calendar.DAY_OF_MONTH)
                            val monthPadded = if (m < 10) "0$m" else "$m"
                            val dayPadded = if (d < 10) "0$d" else "$d"
                            dateStr = "$y-$monthPadded-$dayPadded"
                        }
                        showComposeDatePicker = false
                    }
                ) {
                    Text("OK", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showComposeDatePicker = false }) {
                    Text("Abbrechen")
                }
            },
            colors = DatePickerDefaults.colors(
                containerColor = MaterialTheme.colorScheme.surface
            )
        ) {
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    titleContentColor = MaterialTheme.colorScheme.primary,
                    headlineContentColor = MaterialTheme.colorScheme.onSurface,
                    weekdayContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    subheadContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    navigationContentColor = MaterialTheme.colorScheme.primary,
                    yearContentColor = MaterialTheme.colorScheme.onSurface,
                    selectedYearContentColor = MaterialTheme.colorScheme.onPrimary,
                    selectedYearContainerColor = MaterialTheme.colorScheme.primary,
                    dayContentColor = MaterialTheme.colorScheme.onSurface,
                    selectedDayContentColor = MaterialTheme.colorScheme.onPrimary,
                    selectedDayContainerColor = MaterialTheme.colorScheme.primary,
                    todayContentColor = MaterialTheme.colorScheme.primary,
                    todayDateBorderColor = MaterialTheme.colorScheme.primary
                )
            )
        }
    }
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

fun printMonthlyReport(context: android.content.Context, monthLabel: String, stats: MonthlyStats, stepLengthCm: Int, personName: String) {
    val activity = context as? Activity ?: return
    activity.runOnUiThread {
        val webView = WebView(context)
        webView.webViewClient = object : WebViewClient() {
            override fun onPageFinished(view: WebView?, url: String?) {
                val printManager = context.getSystemService(android.content.Context.PRINT_SERVICE) as? PrintManager
                if (printManager != null) {
                    val sanitizedPerson = personName.replace(Regex("[^a-zA-Z0-9_-]"), "_")
                    val sanitizedMonth = monthLabel.replace(" ", "_")
                    val printAdapter = webView.createPrintDocumentAdapter("Schrittzähler_${sanitizedPerson}_${sanitizedMonth}")
                    val jobName = "Schrittzähler - $personName - $monthLabel"
                    printManager.print(jobName, printAdapter, PrintAttributes.Builder().build())
                }
            }
        }

        val htmlContent = generateMonthlyReportHtml(monthLabel, stats, stepLengthCm, personName)
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

fun generateMonthlyReportHtml(monthLabel: String, stats: MonthlyStats, stepLengthCm: Int, personName: String): String {
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
            .person-sub-header {
                font-size: 9px;
                color: #49454F;
                margin-top: 4px;
                margin-bottom: 8px;
                font-weight: normal;
                text-align: left;
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
            <div class="person-sub-header">
                Person: <strong>$personName</strong>
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
    viewModel: StepViewModel,
    isExpanded: Boolean,
    onToggle: () -> Unit
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Clickable Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Save,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Backup & Wiederherstellung",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isExpanded) "Daten sichern und einlesen" else "Daten sichern & wiederherstellen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Zuklappen" else "Aufklappen",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                Text(
                    text = "Die Sicherung wird immer in die konfigurierte Datei geschrieben und überschreibt diese, damit keine unübersichtlichen Dateidubletten entstehen.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                // Dynamic storage path configuration
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f), RoundedCornerShape(16.dp))
                        .padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Konfigurierter Speicherort:",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
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
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
                                containerColor = MaterialTheme.colorScheme.primary
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
                                    containerColor = MaterialTheme.colorScheme.primaryContainer
                                ),
                                modifier = Modifier.weight(1.2f),
                                shape = RoundedCornerShape(8.dp),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("Standard", color = MaterialTheme.colorScheme.onPrimaryContainer, fontSize = 11.sp, maxLines = 1)
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
                                focusedTextColor = MaterialTheme.colorScheme.onSurface,
                                unfocusedTextColor = MaterialTheme.colorScheme.onSurface,
                                focusedBorderColor = MaterialTheme.colorScheme.primary,
                                unfocusedBorderColor = MaterialTheme.colorScheme.outline,
                                focusedContainerColor = MaterialTheme.colorScheme.surface,
                                unfocusedContainerColor = MaterialTheme.colorScheme.surface
                            )
                        )
                        Button(
                            onClick = {
                                viewModel.setCustomBackupFileName(tempFileName)
                                Toast.makeText(context, "Dateiname gespeichert!", Toast.LENGTH_SHORT).show()
                            },
                            shape = RoundedCornerShape(8.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                            contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
                        ) {
                            Text("Ok", fontSize = 11.sp)
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), thickness = 1.dp)

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
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Sichert Änderungen im Schrittverlauf automatisch im Hintergrund in der Standarddatei.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Spacer(Modifier.width(16.dp))
                    Switch(
                        checked = isAutoBackupEnabled,
                        onCheckedChange = { viewModel.setAutoBackupEnabled(it) },
                        modifier = Modifier.testTag("auto_backup_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                if (lastBackupTime != "Nie") {
                    Text(
                        text = "Letzte Sicherung: $lastBackupTime",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.4f), thickness = 1.dp)

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
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
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
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        if (isLoading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                color = MaterialTheme.colorScheme.onPrimary,
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
                        containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.1f),
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Icon(Icons.Default.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Sicherungsdatei teilen / senden", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
fun AlarmSettingsCard(
    viewModel: StepViewModel,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val context = LocalContext.current
    val alarmEnabled by viewModel.alarmEnabled.collectAsStateWithLifecycle()
    val alarmHour by viewModel.alarmHour.collectAsStateWithLifecycle()
    val alarmMinute by viewModel.alarmMinute.collectAsStateWithLifecycle()

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            viewModel.setAlarmEnabled(true)
            com.example.data.AlarmHelper.scheduleAlarm(context, alarmHour, alarmMinute)
            Toast.makeText(context, "Erinnerung aktiviert!", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(
                context,
                "Benachrichtigungsberechtigung abgelehnt. Erinnerungen können nicht gesendet werden.",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("alarm_settings_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Clickable Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.NotificationsActive,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Tägliche Erinnerung",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isExpanded) "Erinnerungen anpassen" else "Tägliche Schritterinnerung verwalten",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Zuklappen" else "Aufklappen",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                Text(
                    text = "Erhalte eine tägliche Benachrichtigung, falls am aktuellen Tag noch keine Schritte für eine der Personen eingetragen wurden.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                // Toggle Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Erinnerung aktivieren",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (alarmEnabled) "Aktiviert für ${String.format("%02d:%02d", alarmHour, alarmMinute)} Uhr" else "Deaktiviert",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = alarmEnabled,
                        onCheckedChange = { checked ->
                            if (checked) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    val permissionCheck = ContextCompat.checkSelfPermission(
                                        context,
                                        android.Manifest.permission.POST_NOTIFICATIONS
                                    )
                                    if (permissionCheck == PackageManager.PERMISSION_GRANTED) {
                                        viewModel.setAlarmEnabled(true)
                                        com.example.data.AlarmHelper.scheduleAlarm(context, alarmHour, alarmMinute)
                                        Toast.makeText(context, "Erinnerung aktiviert!", Toast.LENGTH_SHORT).show()
                                    } else {
                                        permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                    }
                                } else {
                                    viewModel.setAlarmEnabled(true)
                                    com.example.data.AlarmHelper.scheduleAlarm(context, alarmHour, alarmMinute)
                                    Toast.makeText(context, "Erinnerung aktiviert!", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                viewModel.setAlarmEnabled(false)
                                com.example.data.AlarmHelper.cancelAlarm(context)
                                Toast.makeText(context, "Erinnerung deaktiviert.", Toast.LENGTH_SHORT).show()
                            }
                        },
                        modifier = Modifier.testTag("alarm_switch"),
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.onPrimary,
                            checkedTrackColor = MaterialTheme.colorScheme.primary,
                            uncheckedThumbColor = MaterialTheme.colorScheme.outline,
                            uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    )
                }

                if (alarmEnabled) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                    // Time Selection Row
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                android.app.TimePickerDialog(
                                    context,
                                    { _, selectedHour, selectedMinute ->
                                        viewModel.setAlarmTime(selectedHour, selectedMinute)
                                        com.example.data.AlarmHelper.scheduleAlarm(context, selectedHour, selectedMinute)
                                        Toast.makeText(context, "Erinnerung auf ${String.format("%02d:%02d", selectedHour, selectedMinute)} Uhr aktualisiert.", Toast.LENGTH_SHORT).show()
                                    },
                                    alarmHour,
                                    alarmMinute,
                                    true
                                ).show()
                            }
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccessTime,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Column {
                                Text(
                                    text = "Uhrzeit einstellen",
                                    style = MaterialTheme.typography.bodyLarge,
                                    fontWeight = FontWeight.Medium,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = "Tippen zum Ändern",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Text(
                            text = "${String.format("%02d:%02d", alarmHour, alarmMinute)} Uhr",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                // --- Samsung & Android 13+ Diagnostics Section ---
                var refreshTrigger by remember { mutableStateOf(0) }

                val lifecycleOwner = androidx.compose.ui.platform.LocalLifecycleOwner.current
                DisposableEffect(lifecycleOwner) {
                    val observer = androidx.lifecycle.LifecycleEventObserver { _, event ->
                        if (event == androidx.lifecycle.Lifecycle.Event.ON_RESUME) {
                            refreshTrigger++
                        }
                    }
                    lifecycleOwner.lifecycle.addObserver(observer)
                    onDispose {
                        lifecycleOwner.lifecycle.removeObserver(observer)
                    }
                }

                val hasNotificationPermission = remember(refreshTrigger) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        ContextCompat.checkSelfPermission(context, android.Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
                    } else {
                        true
                    }
                }

                val hasExactAlarmPermission = remember(refreshTrigger) {
                    val am = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        am?.canScheduleExactAlarms() ?: false
                    } else {
                        true
                    }
                }

                val isIgnoringBattery = remember(refreshTrigger) {
                    val pm = context.getSystemService(Context.POWER_SERVICE) as? PowerManager
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        pm?.isIgnoringBatteryOptimizations(context.packageName) ?: true
                    } else {
                        true
                    }
                }

                Spacer(modifier = Modifier.height(4.dp))
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .border(
                            width = 1.dp,
                            color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .padding(12.dp)
                ) {
                    Column(
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Info,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Alarm-Diagnose (Samsung / Android 13+)",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            IconButton(
                                onClick = { refreshTrigger++ },
                                modifier = Modifier.size(24.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Refresh,
                                    contentDescription = "Status aktualisieren",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }

                        Text(
                            text = "Damit der Alarm pünktlich ertönt und nicht im Hintergrund stumm geschaltet oder verzögert wird, müssen folgende Berechtigungen aktiv sein:",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        // 1. Notification Permission Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "1. Benachrichtigungen",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (hasNotificationPermission) "Erlaubt (Akustische Signale aktiv)" else "Nicht erlaubt (Stummgeschaltet)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasNotificationPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            if (!hasNotificationPermission) {
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                            permissionLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Erlauben", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Erlaubt",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 2. Exact Alarm Permission Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "2. Exakte Weckzeiten",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (hasExactAlarmPermission) "Erlaubt (Wecker & Erinnerungen aktiv)" else "Eingeschränkt (Alarme können verzögert werden)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (hasExactAlarmPermission) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            if (!hasExactAlarmPermission) {
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                            try {
                                                val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                                context.startActivity(intent)
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Zulassen", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Erlaubt",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }

                        // 3. Battery Optimization Status
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "3. Akku-Optimierung",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                                Text(
                                    text = if (isIgnoringBattery) "Ausgenommen (Zuverlässiger Alarm)" else "Optimiert (Kann im Hintergrund einschlafen)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = if (isIgnoringBattery) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error
                                )
                            }
                            if (!isIgnoringBattery) {
                                Button(
                                    onClick = {
                                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                                            try {
                                                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
                                                    data = Uri.parse("package:${context.packageName}")
                                                }
                                                context.startActivity(intent)
                                            } catch (e: Exception) {
                                                val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
                                                context.startActivity(intent)
                                            }
                                        }
                                    },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    shape = RoundedCornerShape(8.dp),
                                    modifier = Modifier.height(32.dp)
                                ) {
                                    Text("Ausschalten", style = MaterialTheme.typography.labelMedium)
                                }
                            } else {
                                Icon(
                                    imageVector = Icons.Default.CheckCircle,
                                    contentDescription = "Erlaubt",
                                    tint = Color(0xFF4CAF50),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ThemeSettingsCard(
    viewModel: StepViewModel,
    isExpanded: Boolean,
    onToggle: () -> Unit
) {
    val currentVibe by viewModel.themeVibe.collectAsStateWithLifecycle()
    val currentMode by viewModel.themeMode.collectAsStateWithLifecycle()

    val vibesList = listOf(
        Triple("standard", "Klassisch", "Klassischer Lavendelton"),
        Triple("minimalist", "Minimal-Modern", "Stilvolles Schiefergrau"),
        Triple("professional", "Business Pro", "Tiefblau & Vertrauenswürdig"),
        Triple("playful", "Verspielt", "Sonniges Orange & Koralle"),
        Triple("calm", "Naturruhe", "Beruhigendes Waldgrün & Salbei")
    )

    val modesList = listOf(
        Triple("auto", "System", Icons.Default.Settings),
        Triple("light", "Hell", Icons.Default.LightMode),
        Triple("half_dark", "Halbdark", Icons.Default.DarkMode),
        Triple("dark", "Tiefschwarz", Icons.Default.Contrast)
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("theme_settings_card"),
        shape = RoundedCornerShape(24.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.4f))
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Clickable Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(36.dp)
                            .background(
                                color = MaterialTheme.colorScheme.primaryContainer,
                                shape = RoundedCornerShape(10.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Palette,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "Design & Farbthema",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isExpanded) "Aura, Farben & Hintergrund wählen" else "Vibe & Hintergrund einstellen",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Icon(
                    imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = if (isExpanded) "Schließen" else "Öffnen",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(24.dp)
                )
            }

            if (isExpanded) {
                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                // Vibe Section
                Text(
                    text = "AURA & VIBE (AUSSTRAHLUNG)",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp)
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    vibesList.forEach { (vibeKey, name, desc) ->
                        val isSelected = currentVibe == vibeKey
                        
                        val previewPrimary = when(vibeKey) {
                            "minimalist" -> Color(0xFF0F172A)
                            "professional" -> Color(0xFF1A365D)
                            "playful" -> Color(0xFFE76F51)
                            "calm" -> Color(0xFF2D6A4F)
                            else -> Color(0xFF6750A4)
                        }
                        val previewSecondary = when(vibeKey) {
                            "minimalist" -> Color(0xFF475569)
                            "professional" -> Color(0xFF319795)
                            "playful" -> Color(0xFFF4A261)
                            "calm" -> Color(0xFF74C69D)
                            else -> Color(0xFF625B71)
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.setThemeVibe(vibeKey) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    // Color preview circle
                                    Row(
                                        modifier = Modifier
                                            .width(42.dp)
                                            .height(24.dp)
                                            .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(12.dp))
                                            .padding(2.dp),
                                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box(modifier = Modifier.size(16.dp).background(previewPrimary, CircleShape))
                                        Box(modifier = Modifier.size(16.dp).background(previewSecondary, CircleShape))
                                    }
                                    
                                    Column {
                                        Text(
                                            text = name,
                                            style = MaterialTheme.typography.bodyLarge,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                        )
                                        Text(
                                            text = desc,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }

                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Ausgewählt",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(20.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.2f), modifier = Modifier.padding(vertical = 4.dp))

                // Background Section
                Text(
                    text = "HINTERGRUND & HALBDARKMODUS",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    modesList.forEach { (modeKey, name, icon) ->
                        val isSelected = currentMode == modeKey

                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable { viewModel.setThemeMode(modeKey) },
                            shape = RoundedCornerShape(12.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)
                            ),
                            border = BorderStroke(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline.copy(alpha = 0.2f)
                            )
                        ) {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 12.dp, horizontal = 4.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(32.dp)
                                        .background(
                                            color = if (isSelected) MaterialTheme.colorScheme.primary.copy(alpha = 0.15f) else Color.Transparent,
                                            shape = CircleShape
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = icon,
                                        contentDescription = name,
                                        tint = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Text(
                                    text = name,
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                    color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun getVisibleMonthPrefixes(todayStr: String, limit: Int): List<String> {
    val prefixes = mutableListOf<String>()
    val sdf = java.text.SimpleDateFormat("yyyy-MM", java.util.Locale.GERMANY)
    try {
        val date = java.text.SimpleDateFormat("yyyy-MM-dd", java.util.Locale.GERMANY).parse(todayStr) ?: java.util.Date()
        val cal = java.util.Calendar.getInstance(java.util.Locale.GERMANY)
        cal.time = date
        for (i in 0 until limit) {
            prefixes.add(sdf.format(cal.time))
            cal.add(java.util.Calendar.MONTH, -1)
        }
    } catch (e: Exception) {
        prefixes.add(todayStr.take(7))
    }
    return prefixes
}
