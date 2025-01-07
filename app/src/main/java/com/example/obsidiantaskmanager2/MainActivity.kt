package com.example.obsidiantaskmanager2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.ActivityResultLauncher
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import android.os.Environment
import androidx.compose.foundation.ExperimentalFoundationApi
import com.example.obsidiantaskmanager2.ui.theme.ObsidianTaskManager2Theme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*
import java.util.regex.Pattern
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.combinedClickable

/**
 * This data class simulates the Python version of each 'line' stored in memory.
 */
data class TaskLine(
    var text: String,
    val filePath: String,
    val lineIndex: Int
)

/**
 * This class simulates the "MarkdownReviewer" from python (markdown_reviewer.py).
 * It loads lines, filters them, saves changes, etc.
 */
class MarkdownReviewer(
    private val context: Context,
    private var directoryPath: String
) {

    // In-memory list of lines that match certain criteria (📅 + [ ])
    var lines: MutableList<TaskLine> = mutableListOf()

    fun loadLines(onNoFilesFound: () -> Unit) {
        lines.clear()
        val directory = File(directoryPath)
        if (directory.exists() && directory.isDirectory) {
            var mdFiles = directory.listFiles { file ->
                !file.name.startsWith(".") && file.extension.equals(
                    "md",
                    ignoreCase = true
                )
            }
            if (mdFiles.isNullOrEmpty()) {
                onNoFilesFound()
                return
            }
            mdFiles.forEach { file ->
                val allLines = file.readLines()
                for ((i, line) in allLines.withIndex()) {
                    // The Python code looks for lines containing '📅 YYYY-MM-DD' and '[ ]'
                    if (line.contains("📅") &&
                        Pattern.compile("\\d{4}-\\d{2}-\\d{2}").matcher(line).find() &&
                        line.contains("[ ]")
                    ) {
                        lines.add(
                            TaskLine(
                                text = line.trimEnd(),
                                filePath = file.absolutePath,
                                lineIndex = i
                            )
                        )
                    }
                }
            }
        } else {
            // Handle the case where the directory does not exist or is not a directory
            onNoFilesFound()
        }
    }

    fun saveLine(taskLine: TaskLine, newText: String) {
        // Overwrite the line in the file
        val file = File(taskLine.filePath)
        if (!file.exists()) return

        val allLines = file.readLines().toMutableList()
        if (taskLine.lineIndex < allLines.size) {
            allLines[taskLine.lineIndex] = newText
            file.writeText(allLines.joinToString("\n"))
        }
    }

    fun deleteLine(taskLine: TaskLine) {
        val file = File(taskLine.filePath)
        if (!file.exists()) return
        val allLines = file.readLines().toMutableList()
        if (taskLine.lineIndex < allLines.size) {
            allLines.removeAt(taskLine.lineIndex)
            file.writeText(allLines.joinToString("\n"))
        }
    }

    /**
     * Snooze the line's date by [days].
     * Also removes any hourly snooze (snz:HH:MM) if found.
     */
    fun snoozeLine(taskLine: TaskLine, days: Int): String {
        val today = Date()
        val cal = Calendar.getInstance()
        cal.time = today
        cal.add(Calendar.DATE, days)
        val newDate = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(cal.time)
        // Replace date
        val newText = taskLine.text.replace(
            Regex("📅\\s*\\d{4}-\\d{2}-\\d{2}"),
            "📅 $newDate"
        )
        // Remove any hourly snz
        return newText.replace(Regex("\\ssnz:\\d{2}:\\d{2}"), "")
    }

    /**
     * Snooze hours: update `snz:HH:MM`, or insert it if missing
     * Also resets the date to "today"
     */
    fun snoozeHours(taskLine: TaskLine, hours: Int): String {
        val now = Calendar.getInstance()
        val dateRegex = Regex("📅\\s*\\d{4}-\\d{2}-\\d{2}")
        // Force today's date:
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now.time)

        // If a snz:HH:MM exists, parse it, otherwise start from "now"
        // For simplicity, we ignore the existing snz time and just set it to "now + hours"
        now.add(Calendar.HOUR_OF_DAY, hours)
        val snzStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)

        // Replace or add date => today's date
        var newText = if (dateRegex.containsMatchIn(taskLine.text)) {
            // Replace the date portion
            taskLine.text.replace(dateRegex, "📅 $todayStr")
        } else {
            // If no date, just append it
            "${taskLine.text} 📅 $todayStr"
        }

        // Replace or add snz
        val snzRegex = Regex("snz:\\d{2}:\\d{2}")
        newText = if (snzRegex.containsMatchIn(newText)) {
            newText.replace(snzRegex, "snz:$snzStr")
        } else {
            "$newText snz:$snzStr"
        }

        return newText
    }

    fun updateDirectoryPath(newPath: String) {
        directoryPath = newPath
    }
}

/**
 * History item for undo
 */
data class HistoryItem(
    val indexInReviewer: Int,
    val oldTask: TaskLine
)

/**
 * MainActivity that demonstrates the UI and logic in one file.
 */
class MainActivity : ComponentActivity() {

    // A quick way to see if we're on Android or Windows (very naive check):
    private fun isAndroid(): Boolean = (Build.BRAND != null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MainScreen()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun MainScreen() {
    val context = LocalContext.current
    val prefs = remember { context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE) }
    val initialDirectory = prefs.getString("directory_path", null) ?: "/storage/emulated/0/JLM Obisidian"

    // Instantiate our "reviewer" that loads tasks from local directory
    val reviewer = remember {
        MarkdownReviewer(context, initialDirectory)
    }

    // We keep track of tasks displayed in a list
    var filteredTasks by remember { mutableStateOf<List<TaskLine>>(emptyList()) }

    // For undo stack
    val historyStack = remember { mutableStateListOf<HistoryItem>() }

    // Filter states
    var dateFilter by remember { mutableStateOf("today_or_before") }
    var charFilter by remember { mutableStateOf("") }
    var excludeHourlySnooze by remember { mutableStateOf(true) }

    // State to track the selected task using (filePath, lineIndex)
    var selectedFilePathAndLine by remember { mutableStateOf<Pair<String, Int>?>(null) }

    // Declare directoryLauncher as a lateinit variable
    lateinit var directoryLauncher: ActivityResultLauncher<Uri?>

    // Refresh / reload
    fun refreshTasks() {
        // Notify user of refresh
        println("Refreshing tasks...")
        reviewer.loadLines {
            // If no files are found, open the directory selector
            directoryLauncher.launch(null)
        }
        filteredTasks = applyFilters(
            reviewer.lines,
            dateFilter,
            charFilter,
            excludeHourlySnooze
        )

        // Notify user of re-selection
        println("Re-selecting the first item in the newly filtered list.")
        selectedFilePathAndLine = filteredTasks.firstOrNull()?.let { it.filePath to it.lineIndex }
    }

    // Initialize directoryLauncher after defining refreshTasks
    directoryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val directoryPath = it.path.toString() // Or handle the URI as needed
            prefs.edit().putString("directory_path", directoryPath).apply()
            reviewer.updateDirectoryPath(directoryPath)
            refreshTasks()
        }
    }

    // Ask for runtime permissions for external storage read/write
    // Permissions to request (updated based on manifest)
    val permissionsToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // On Android 11 (API 30) and higher, we need MANAGE_EXTERNAL_STORAGE
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE
        )
    } else {
        // On older versions, READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE are sufficient
        arrayOf(
            Manifest.permission.READ_EXTERNAL_STORAGE,
            Manifest.permission.WRITE_EXTERNAL_STORAGE
        )
    }

    // Launcher for permissions
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
        onResult = { results ->
            // Check if MANAGE_EXTERNAL_STORAGE is granted (for Android 11+)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (!Environment.isExternalStorageManager()) {
                    // MANAGE_EXTERNAL_STORAGE was denied
                    Toast.makeText(
                        context,
                        "Please grant 'All files access' for full functionality.",
                        Toast.LENGTH_SHORT
                    ).show()

                    // Intent to open the "All files access" settings screen
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                    val uri = Uri.fromParts("package", context.packageName, null)
                    intent.data = uri
                    context.startActivity(intent)
                }
            } else {
                // Check for READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE (for older Android versions)
                if (results.values.any { !it }) {
                    Toast.makeText(
                        context,
                        "Please grant storage permissions for full functionality.",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    )

    // Track whether permissions have been requested
    var permissionsRequested by remember { mutableStateOf(false) }

    // Check if permissions are already granted
    val permissionsGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        // On Android 11+, check for MANAGE_EXTERNAL_STORAGE
        Environment.isExternalStorageManager()
    } else {
        // On older versions, check for READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE
        permissionsToRequest.all {
            ContextCompat.checkSelfPermission(context, it) == PackageManager.PERMISSION_GRANTED
        }
    }

    // Request permissions if not granted and not already requested
    if (!permissionsGranted && !permissionsRequested) {
        SideEffect {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                // Request MANAGE_EXTERNAL_STORAGE on Android 11+
                // We need to direct the user to the settings screen
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                val uri = Uri.fromParts("package", context.packageName, null)
                intent.data = uri
                context.startActivity(intent)
            } else {
                // Request READ_EXTERNAL_STORAGE and WRITE_EXTERNAL_STORAGE on older versions
                permissionLauncher.launch(permissionsToRequest)
            }
            permissionsRequested = true
        }
    }

    // Only show the main content if permissions are granted or have been requested
    if (permissionsGranted || permissionsRequested) {
        // On first load
        LaunchedEffect(permissionsGranted) {
            if (permissionsGranted) {
                refreshTasks()
            }
        }

        // UI
        val dateFilterOptions = listOf(
            "all", "today", "past", "tomorrow", "next_week", "next_month", "today_or_before"
        )
        val charFilterOptions = listOf("", "✅", "⏫", "🔽", "🔽")

        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text("Obsidian Task Manager") },
                    actions = {}
                )
            },
            content = { padding ->
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .background(Color(0xFF2d2d2d))
                ) {
                    // Filters
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Date filter dropdown
                        FilterDropdown(
                            label = "Date Filter",
                            options = dateFilterOptions,
                            selected = dateFilter,
                            onSelectedChange = {
                                dateFilter = it
                                refreshTasks()
                            }
                        )
                        Spacer(Modifier.width(8.dp))
                        // Char filter dropdown
                        FilterDropdown(
                            label = "Char Filter",
                            options = charFilterOptions,
                            selected = charFilter,
                            onSelectedChange = {
                                charFilter = it
                                refreshTasks()
                            }
                        )
                    }

                    // Exclude Hourly Snooze Checkbox
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Checkbox(
                            checked = excludeHourlySnooze,
                            onCheckedChange = {
                                excludeHourlySnooze = it
                                refreshTasks()
                            }
                        )
                        Text(text = "Exclude Hourly Snooze", color = Color.White)
                    }

                    // Item count
                    Text(
                        text = "Items: ${filteredTasks.size}",
                        color = Color.White,
                        modifier = Modifier.padding(start = 16.dp, bottom = 4.dp)
                    )

                    // Task List
                    val scope = rememberCoroutineScope()
                    val focusManager = LocalFocusManager.current

                    // Ensure something is always selected
                    LaunchedEffect(filteredTasks) {
                        if (selectedFilePathAndLine == null && filteredTasks.isNotEmpty()) {
                            selectedFilePathAndLine = filteredTasks.first().filePath to filteredTasks.first().lineIndex
                            println("Auto-selected first item: ${selectedFilePathAndLine?.second}")
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredTasks) { taskLine ->
                            // Depending on priority mark, we color the background
                            val bgColor = when {
                                taskLine.text.contains("⏫") -> Color.Red.copy(alpha = 0.4f)
                                taskLine.text.contains("🔽") -> Color.Green.copy(alpha = 0.4f)
                                else -> Color.Transparent
                            }

                            // Determine if the current task is selected
                            val isSelected = (taskLine.filePath == selectedFilePathAndLine?.first
                                    && taskLine.lineIndex == selectedFilePathAndLine?.second)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) Color.White else bgColor)
                                    .combinedClickable(
                                        onClick = {
                                            selectedFilePathAndLine = filteredTasks.firstOrNull()?.let { it.filePath to it.lineIndex }
                                            println("Item selected: ${selectedFilePathAndLine?.second}")
                                        },
                                        onLongClick = {
                                            // Long press: open Obsidian
                                            openObsidianFileToLine(
                                                context,
                                                taskLine.filePath,
                                                taskLine.lineIndex
                                            )
                                        }
                                    )
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = taskLine.text,
                                    color = if (isSelected) Color.Black else Color.White
                                )
                            }
                        }
                    }

                    // Function to update the selected task
                    fun selectNextTask(nextTask: TaskLine?) {
                        selectedFilePathAndLine = nextTask?.let { it.filePath to it.lineIndex }
                    }

                    // Bottom Buttons
                    BottomButtons(
                        getSelectedTask = { selectedFilePathAndLine ->
                            getSelectedTask(reviewer, selectedFilePathAndLine)
                        },
                        onSnoozeHours = { h ->
                            doSnoozeHours(
                                h,
                                reviewer,
                                getSelectedTask(reviewer, selectedFilePathAndLine),
                                historyStack,
                                ::refreshTasks,
                                ::selectNextTask,
                                dateFilter,
                                charFilter,
                                excludeHourlySnooze
                            )
                        },
                        onSnoozeDays = { d ->
                            doSnoozeDays(
                                d,
                                reviewer,
                                getSelectedTask(reviewer, selectedFilePathAndLine),
                                historyStack,
                                ::refreshTasks,
                                ::selectNextTask,
                                dateFilter,
                                charFilter,
                                excludeHourlySnooze
                            )
                        },
                        onHighPri = {
                            doChangePriority(
                                "⏫",
                                reviewer,
                                getSelectedTask(reviewer, selectedFilePathAndLine),
                                historyStack,
                                ::refreshTasks
                            )
                        },
                        onRemovePri = {
                            doChangePriority(
                                "remove",
                                reviewer,
                                getSelectedTask(reviewer, selectedFilePathAndLine),
                                historyStack,
                                ::refreshTasks
                            )
                        },
                        onLowPri = {
                            doChangePriority(
                                "🔽",
                                reviewer,
                                getSelectedTask(reviewer, selectedFilePathAndLine),
                                historyStack,
                                ::refreshTasks
                            )
                        },
                        onCompleted = {
                            doMarkCompleted(
                                reviewer,
                                getSelectedTask(reviewer, selectedFilePathAndLine),
                                historyStack,
                                ::refreshTasks
                            )
                        },
                        onUndo = { doUndo(reviewer, historyStack, ::refreshTasks) },
                        onRefresh = { refreshTasks() },
                        onDelete = {
                            doDeleteTask(
                                reviewer,
                                getSelectedTask(reviewer, selectedFilePathAndLine),
                                historyStack,
                                ::refreshTasks
                            )
                        }
                    )
                }
            }
        )
    }
}

/**
 * Helper function to retrieve the selected TaskLine based on selectedFilePathAndLine
 */
fun getSelectedTask(reviewer: MarkdownReviewer, selectedFilePathAndLine: Pair<String, Int>?): TaskLine? {
    return selectedFilePathAndLine?.let { (filePath, lineIndex) ->
        reviewer.lines.find { it.filePath == filePath && it.lineIndex == lineIndex }
    }
}

/**
 * Filter logic that replicates the python approach (minus afternoon/evening tags).
 */
fun applyFilters(
    lines: List<TaskLine>,
    dateFilter: String,
    charFilter: String,
    excludeHourlySnooze: Boolean
): List<TaskLine> {
    val now = Calendar.getInstance()
    val today = now.time
    val dateFmt = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())

    // Filter by date
    val dateFiltered = lines.filter { line ->
        val match = Regex("📅\\s*(\\d{4}-\\d{2}-\\d{2})").find(line.text)
        if (dateFilter == "all" || match == null) {
            true
        } else {
            val dateStr = match.groupValues[1]
            val lineDate = dateFmt.parse(dateStr) ?: return@filter false
            when (dateFilter) {
                "today" -> sameDay(lineDate, today)
                "past" -> lineDate.before(stripTime(today))
                "tomorrow" -> sameDay(lineDate, addDays(stripTime(today), 1))
                "next_week" -> lineDate.after(today) && lineDate.before(addDays(today, 7))
                "next_month" -> lineDate.after(today) && lineDate.before(addDays(today, 30))
                "today_or_before" -> !lineDate.after(stripTime(today))
                else -> true
            }
        }
    }

    // Filter by character
    val charFiltered = if (charFilter.isNotEmpty()) {
        dateFiltered.filter { it.text.contains(charFilter) }
    } else {
        dateFiltered
    }

    // Exclude hourly snooze logic
    // If 'snz:HH:MM' is in the line, and the time is still in the future for "today," filter it out
    // or remove the snz if it's past
    if (!excludeHourlySnooze) return charFiltered

    val nowTimeStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)
    val nowTime = parseTime(nowTimeStr)
    val todayDate = stripTime(today)
    val newList = mutableListOf<TaskLine>()

    for (line in charFiltered) {
        val dateMatch = Regex("📅\\s*(\\d{4}-\\d{2}-\\d{2})").find(line.text)
        val snzMatch = Regex("snz:(\\d{2}:\\d{2})").find(line.text)

        if (snzMatch == null) {
            // No snz => always keep
            newList.add(line)
        } else {
            val snzTimeStr = snzMatch.groupValues[1]
            val snzTime = parseTime(snzTimeStr)
            // If date is in the future => keep
            if (dateMatch != null) {
                val lineDate = dateFmt.parse(dateMatch.groupValues[1]) ?: todayDate
                if (lineDate.after(todayDate)) {
                    newList.add(line)
                } else if (lineDate.before(todayDate)) {
                    // If it's in the past, we could remove the snz from the line if you want,
                    // but for simplicity we keep it out of the filtered list or remove snz.
                    // We'll mimic the python approach by removing snz and re-saving the line
                    newList.add(line) // We'll keep it for now; your logic may differ
                } else {
                    // It's today's date
                    // If the snz time has passed, remove snz
                    if (snzTime != null && nowTime != null && nowTime >= snzTime) {
                        newList.add(line) // and so on
                    }
                }
            } else {
                // No date found, check if snz time is past
                if (snzTime != null && nowTime != null && nowTime >= snzTime) {
                    newList.add(line)
                }
            }
        }
    }
    return newList
}

private fun parseTime(timeStr: String): Int? {
    // Convert "HH:mm" to integer in minutes from midnight
    val parts = timeStr.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return hour * 60 + minute
}

private fun sameDay(d1: Date, d2: Date): Boolean {
    return stripTime(d1) == stripTime(d2)
}

private fun stripTime(d: Date): Date {
    val cal = Calendar.getInstance()
    cal.time = d
    cal.set(Calendar.HOUR_OF_DAY, 0)
    cal.set(Calendar.MINUTE, 0)
    cal.set(Calendar.SECOND, 0)
    cal.set(Calendar.MILLISECOND, 0)
    return cal.time
}

private fun addDays(d: Date, days: Int): Date {
    val cal = Calendar.getInstance()
    cal.time = d
    cal.add(Calendar.DATE, days)
    return cal.time
}

/**
 * Shortcuts for actions below
 */

// Snooze by days
fun doSnoozeDays(
    days: Int,
    reviewer: MarkdownReviewer,
    selectedTask: TaskLine?,
    history: MutableList<HistoryItem>,
    refresh: () -> Unit,
    selectNextTask: (TaskLine?) -> Unit,
    dateFilter: String,
    charFilter: String,
    excludeHourlySnooze: Boolean
) {
    if (selectedTask != null) {
        val oldCopy = selectedTask.copy()
        val newText = reviewer.snoozeLine(selectedTask, days)
        selectedTask.text = newText
        val indexInReviewer = reviewer.lines.indexOf(selectedTask)
        history.add(HistoryItem(indexInReviewer, oldCopy))
        reviewer.saveLine(selectedTask, newText)
    }
    refresh()

    // Select the first task in the filtered list if no task is selected
    val filteredTasks = applyFilters(
        reviewer.lines,
        dateFilter,
        charFilter,
        excludeHourlySnooze
    )
    selectNextTask(filteredTasks.firstOrNull())
}

// Snooze by hours
fun doSnoozeHours(
    hours: Int,
    reviewer: MarkdownReviewer,
    selectedTask: TaskLine?,
    history: MutableList<HistoryItem>,
    refresh: () -> Unit,
    selectNextTask: (TaskLine?) -> Unit,
    dateFilter: String,
    charFilter: String,
    excludeHourlySnooze: Boolean
) {
    if (selectedTask != null) {
        val oldCopy = selectedTask.copy()
        val newText = reviewer.snoozeHours(selectedTask, hours)
        selectedTask.text = newText
        val indexInReviewer = reviewer.lines.indexOf(selectedTask)
        history.add(HistoryItem(indexInReviewer, oldCopy))
        reviewer.saveLine(selectedTask, newText)
    }
    refresh()

    // Ensure something is always selected
    val filteredTasks = applyFilters(
        reviewer.lines,
        dateFilter,
        charFilter,
        excludeHourlySnooze
    )
    if (filteredTasks.isNotEmpty()) {
        selectNextTask(filteredTasks.first())
    }
}

fun doChangePriority(
    priority: String,
    reviewer: MarkdownReviewer,
    selectedTask: TaskLine?,
    history: MutableList<HistoryItem>,
    refresh: () -> Unit
) {
    if (selectedTask != null) {
        val oldCopy = selectedTask.copy()
        val newText = when (priority) {
            "remove" -> {
                selectedTask.text.replace(Regex("[⏫🔽]"), "").trim()
            }
            "⏫" -> {
                if (selectedTask.text.contains("⏫") || selectedTask.text.contains("🔽")) {
                    selectedTask.text.replace(Regex("[⏫🔽]"), "⏫")
                } else {
                    selectedTask.text + " ⏫"
                }
            }
            "🔽" -> {
                if (selectedTask.text.contains("⏫") || selectedTask.text.contains("🔽")) {
                    selectedTask.text.replace(Regex("[⏫🔽]"), "🔽")
                } else {
                    selectedTask.text + " 🔽"
                }
            }
            else -> selectedTask.text
        }
        selectedTask.text = newText
        val indexInReviewer = reviewer.lines.indexOf(selectedTask)
        history.add(HistoryItem(indexInReviewer, oldCopy))
        reviewer.saveLine(selectedTask, newText)
        refresh()
    }
}

fun doMarkCompleted(
    reviewer: MarkdownReviewer,
    selectedTask: TaskLine?,
    history: MutableList<HistoryItem>,
    refresh: () -> Unit
) {
    if (selectedTask != null) {
        val oldCopy = selectedTask.copy()
        val newText = selectedTask.text.replace("[ ]", "[x]")
        selectedTask.text = newText
        val indexInReviewer = reviewer.lines.indexOf(selectedTask)
        history.add(HistoryItem(indexInReviewer, oldCopy))
        reviewer.saveLine(selectedTask, newText)
        refresh()
    }
}

fun doDeleteTask(
    reviewer: MarkdownReviewer,
    selectedTask: TaskLine?,
    history: MutableList<HistoryItem>,
    refresh: () -> Unit
) {
    if (selectedTask != null) {
        val indexInReviewer = reviewer.lines.indexOf(selectedTask)
        val oldCopy = selectedTask.copy()
        history.add(HistoryItem(indexInReviewer, oldCopy))

        reviewer.lines.remove(selectedTask)
        reviewer.deleteLine(selectedTask)
        refresh()
    }
}

fun doUndo(
    reviewer: MarkdownReviewer,
    history: MutableList<HistoryItem>,
    refresh: () -> Unit
) {
    if (history.isNotEmpty()) {
        val (indexInReviewer, oldTask) = history.removeAt(history.lastIndex)
        // We'll restore it in the lines
        // Possibly the lineIndex changed if lines got reloaded,
        // so we rely on rewriting the text only.
        if (indexInReviewer < 0 || indexInReviewer > reviewer.lines.size) {
            // If the index is out of range, just re-append
            reviewer.lines.add(oldTask)
        } else {
            // Insert or replace
            reviewer.lines.add(indexInReviewer, oldTask)
        }
        // We re-save its line
        reviewer.saveLine(oldTask, oldTask.text)
        refresh()
    }
}

/**
 * Opens Obsidian to a specific line number
 */
fun openObsidianFileToLine(context: Context, filePath: String, lineNumber: Int) {
    val encodedPath = Uri.encode(filePath)
    val url = "obsidian://open?path=$encodedPath&line=$lineNumber"

    // Attempt to open
    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    // If no Obsidian found, catch it
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "Obsidian not found", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Opens Obsidian search for the text
 */
fun openObsidianFileToTextSearch(context: Context, filePath: String, lineText: String) {
    val encodedSearch = Uri.encode("\"$lineText\"")
    val url = "obsidian://search?query=$encodedSearch"

    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
    if (intent.resolveActivity(context.packageManager) != null) {
        context.startActivity(intent)
    } else {
        Toast.makeText(context, "Obsidian not found", Toast.LENGTH_SHORT).show()
    }
}

/**
 * Basic composable for a Filter Dropdown
 */
@Composable
fun FilterDropdown(
    label: String,
    options: List<String>,
    selected: String,
    onSelectedChange: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }

    Box {
        OutlinedTextField(
            value = selected,
            onValueChange = {},
            label = { Text(label) },
            readOnly = true,
            modifier = Modifier
                .width(150.dp)
                .height(56.dp),
            trailingIcon = {
                IconButton(onClick = { expanded = !expanded }) {
                    Icon(
                        imageVector = androidx.compose.material.icons.Icons.Default.ArrowDropDown,
                        contentDescription = null
                    )
                }
            },
            keyboardOptions = KeyboardOptions.Default.copy(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = {})
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            options.forEach { option ->
                DropdownMenuItem(
                    text = { Text(option) },
                    onClick = {
                        onSelectedChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Bottom row of buttons for snooze, priority, etc.
 */
@Composable
fun BottomButtons(
    getSelectedTask: (selectedFilePathAndLine: Pair<String, Int>?) -> TaskLine?,
    onSnoozeHours: (Int) -> Unit,
    onSnoozeDays: (Int) -> Unit,
    onHighPri: () -> Unit,
    onRemovePri: () -> Unit,
    onLowPri: () -> Unit,
    onCompleted: () -> Unit,
    onUndo: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit
) {
    Column {
        // Hourly and daily snooze
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val snoozeOptions = listOf("1h", "4h", "7h", "1d", "2d", "4d", "6d")
            snoozeOptions.forEach { option ->
                ElevatedButton(onClick = {
                    when {
                        option.endsWith("h") -> onSnoozeHours(option.dropLast(1).toInt())
                        option.endsWith("d") -> onSnoozeDays(option.dropLast(1).toInt())
                    }
                }) {
                    Text(option)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Weekly snooze
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            val weekSnoozeOptions = listOf(2, 4, 8, 12, 16)
            weekSnoozeOptions.forEach { w ->
                ElevatedButton(onClick = { onSnoozeDays(w * 7) }) {
                    Text("${w}w")
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Priority, Completed, etc.
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ElevatedButton(onClick = { onHighPri() }) {
                Text("High Pri. (h)")
            }
            ElevatedButton(onClick = { onRemovePri() }) {
                Text("Remove Pri. (r)")
            }
            ElevatedButton(onClick = { onLowPri() }) {
                Text("Low Pri. (l)")
            }
        }

        Spacer(Modifier.height(4.dp))

        // Completed
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ElevatedButton(onClick = { onCompleted() }) {
                Text("Completed (m)")
            }
        }

        Spacer(Modifier.height(4.dp))

        // Undo, Refresh, Delete
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
            ElevatedButton(onClick = { onUndo() }) {
                Text("Undo (u)")
            }
            ElevatedButton(onClick = { onRefresh() }) {
                Text("Refresh (r)")
            }
            ElevatedButton(onClick = { onDelete() }) {
                Text("Delete (d)")
            }
        }
    }
}
