package com.example.obsidiantaskmanager2

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ShortcutManager
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
import androidx.compose.material.icons.filled.MoreVert
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
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.focusRequester
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.platform.LocalClipboardManager
import android.content.pm.ShortcutInfo
import android.graphics.drawable.Icon as ShortcutIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.material.icons.filled.LocalOffer

/**
 * ButtonPressCounter:
 * A helper object that uses SharedPreferences to persist counts, so that the number
 * of times each button is pressed is accumulated across app sessions.
 */
object ButtonPressCounter {
    private const val PREFS_NAME = "button_press_counter"

    fun increment(context: Context, key: String) {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val current = prefs.getInt(key, 0)
        prefs.edit().putInt(key, current + 1).apply()
    }

    fun getCount(context: Context, key: String): Int {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.getInt(key, 0)
    }

    fun getAllCounts(context: Context): Map<String, Int> {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        return prefs.all.filter { it.value is Int }.mapValues { it.value as Int }
    }
}

/**
 * This data class simulates the Python version of each 'line' stored in memory.
 */
data class TaskLine(
    var text: String,
    val filePath: String,
    val lineIndex: Int
)

// Add this new enum near the top of your file (for example, after the TaskLine data class)
enum class TaskPriority {
    High,
    Normal,
    Low
}

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
            // Check for any file containing "conflict" in the file name
            val conflictFile = mdFiles.firstOrNull { it.name.contains("conflict", ignoreCase = true) }
            if (conflictFile != null) {
                showConflictAlert(conflictFile)
            }
            
            // Process each markdown file as before...
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

    private fun showConflictAlert(conflictFile: File) {
        // Show a message box alerting the user about the conflict file and allow opening the file explorer.
        if (context is android.app.Activity) {
            context.runOnUiThread {
                android.app.AlertDialog.Builder(context)
                    .setTitle("Conflict File Detected")
                    .setMessage("The file \"${conflictFile.name}\" appears to be in conflict state. Would you like to open its containing folder?")
                    .setPositiveButton("Open") { _, _ ->
                        val folder = conflictFile.parentFile
                        val intent = Intent(Intent.ACTION_VIEW).apply {
                            // Attempt to open the folder in a file explorer.
                            setDataAndType(Uri.fromFile(folder), "resource/folder")
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK
                        }
                        try {
                            context.startActivity(intent)
                        } catch (e: Exception) {
                            Toast.makeText(context, "Unable to open file explorer", Toast.LENGTH_SHORT).show()
                        }
                    }
                    .setNegativeButton("Cancel", null)
                    .show()
            }
        } else {
            // Fallback: just show a toast message if context is not an Activity.
            Toast.makeText(context, "Conflict file detected: ${conflictFile.name}", Toast.LENGTH_LONG)
                .show()
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

    // Snooze by an arbitrary number of minutes
    fun snoozeMinutes(taskLine: TaskLine, minutes: Int): String {
        val now = Calendar.getInstance()

        // Force today's date (same as snoozeHours does)
        val dateRegex = Regex("📅\\s*\\d{4}-\\d{2}-\\d{2}")
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(now.time)

        // Move "now" by [minutes]
        now.add(Calendar.MINUTE, minutes)
        val snzStr = SimpleDateFormat("HH:mm", Locale.getDefault()).format(now.time)

        // Replace or add date => today's date
        var newText = if (dateRegex.containsMatchIn(taskLine.text)) {
            // Replace the existing date
            taskLine.text.replace(dateRegex, "📅 $todayStr")
        } else {
            // Append a new date
            "${taskLine.text} 📅 $todayStr"
        }

        // Replace or add "snz:HH:MM"
        val snzRegex = Regex("snz:\\d{2}:\\d{2}")
        newText = if (snzRegex.containsMatchIn(newText)) {
            newText.replace(snzRegex, "snz:$snzStr")
        } else {
            "$newText snz:$snzStr"
        }

        return newText
    }

    fun snoozeToSpecificTime(taskLine: TaskLine, targetHour: Int, targetMinute: Int): String {
        val now = Calendar.getInstance()
        val target = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, targetHour)
            set(Calendar.MINUTE, targetMinute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        // If the current time is after the target, schedule for the next day.
        if (now.timeInMillis > target.timeInMillis) {
            target.add(Calendar.DATE, 1)
        }
        val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(target.time)
        val snzStr = String.format("%02d:%02d", targetHour, targetMinute)
        
        // Update the date in the task line
        val dateRegex = Regex("📅\\s*\\d{4}-\\d{2}-\\d{2}")
        var newText = if (dateRegex.containsMatchIn(taskLine.text)) {
            taskLine.text.replace(dateRegex, "📅 $dateStr")
        } else {
            "${taskLine.text} 📅 $dateStr"
        }
        
        // Update or insert the snooze time (snz:HH:MM)
        val snzRegex = Regex("snz:\\d{2}:\\d{2}")
        newText = if (snzRegex.containsMatchIn(newText)) {
            newText.replace(snzRegex, "snz:$snzStr")
        } else {
            "$newText snz:$snzStr"
        }
        
        return newText.trim()
    }

    fun updateDirectoryPath(newPath: String) {
        directoryPath = newPath
    }

    fun getDirectoryPath(): String {
        return directoryPath
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

        // Check if this is the first run and create the shortcut if needed.
        val prefs = getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        if (!prefs.getBoolean("shortcut_created", false)) {
            createPinnedShortcut()
            prefs.edit().putBoolean("shortcut_created", true).apply()
        }

        // Pass the "open_new_task" flag (if any) from the intent to MainScreen.
        setContent {
            MainScreen(initialOpenNewTask = intent.getBooleanExtra("open_new_task", false))
        }
    }

    private fun createPinnedShortcut() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val shortcutManager = getSystemService(ShortcutManager::class.java)
            if (shortcutManager.isRequestPinShortcutSupported) {
                val shortcutIntent = Intent(this, MainActivity::class.java).apply {
                    action = Intent.ACTION_VIEW
                    putExtra("open_new_task", true)
                }
                val shortcutInfo = ShortcutInfo.Builder(this, "new_task_shortcut")
                    .setShortLabel("New Task")
                    .setLongLabel("Add a New Task")
                    // Use a pre-existing system icon instead of R.drawable.ic_new_task
                    .setIcon(ShortcutIcon.createWithResource(this, android.R.drawable.ic_menu_add))
                    .setIntent(shortcutIntent)
                    .build()
                shortcutManager.requestPinShortcut(shortcutInfo, null)
            }
        }
    }
}

// Add doAddTag helper function (to modify a task by appending a new tag)
fun doAddTag(
    reviewer: MarkdownReviewer,
    selectedTask: TaskLine?,
    history: MutableList<HistoryItem>,
    tag: String,
    refresh: () -> Unit
) {
    if (selectedTask != null) {
        val oldCopy = selectedTask.copy()
        val tagWithHash = "#$tag"
        // Only add the tag if not already present
        val newText = if (selectedTask.text.contains(tagWithHash)) {
            selectedTask.text
        } else {
            "${selectedTask.text} $tagWithHash"
        }
        selectedTask.text = newText
        val indexInReviewer = reviewer.lines.indexOf(selectedTask)
        history.add(HistoryItem(indexInReviewer, oldCopy))
        reviewer.saveLine(selectedTask, newText)
        refresh()
    }
}

// Create a TagMenu composable that shows a dropdown with tag options.
// (Make sure also to import the icon for tags: androidx.compose.material.icons.filled.LocalOffer)
@Composable
fun TagMenu(
    availableTags: List<String> = listOf("drive", "errand", "household", "Jean", "Clara", "Clyde", "Charlie"),
    onTagSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        IconButton(onClick = { expanded = true }) {
            Icon(
                imageVector = Icons.Default.LocalOffer,
                contentDescription = "Tag Menu",
                tint = Color.Yellow // Use yellow for high contrast against dark backgrounds
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier
                .background(Color.Black) // Use a black background for higher contrast
        ) {
            availableTags.forEach { tag ->
                DropdownMenuItem(
                    text = { Text("#$tag", color = Color.White) }, // White text for better readability
                    onClick = {
                        expanded = false
                        onTagSelected(tag)
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, 
       androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun MainScreen(initialOpenNewTask: Boolean = false) {
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

    // --- New state variables for editing ---
    var showEditDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<TaskLine?>(null) }
    var editTaskText by remember { mutableStateOf("") }

    // Declare directoryLauncher as a lateinit variable
    lateinit var directoryLauncher: ActivityResultLauncher<Uri?>

    // Refresh / reload
    fun refreshTasks() {
        println("Refreshing tasks...")
        reviewer.loadLines {
            directoryLauncher.launch(null)
        }
        
        // Remove expired snooze times and update filters...
        val now = Calendar.getInstance()
        val today = now.time
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
        val currentTimeStr = timeFormat.format(now.time)
        val currentTimeMins = parseTime(currentTimeStr)
        
        reviewer.lines.forEach { taskLine ->
            val text = taskLine.text
            val snzRegex = Regex("snz:(\\d{2}:\\d{2})").find(text)
            
            snzRegex?.let { match ->
                val snzTimeStr = match.groupValues[1]
                val snzTimeMins = parseTime(snzTimeStr)
                val dateMatch = Regex("📅\\s*(\\d{4}-\\d{2}-\\d{2})").find(text)
                val dateStr = dateMatch?.groupValues?.get(1)
                val taskDate = dateStr?.let { 
                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).parse(it) 
                } ?: today
                
                if (stripTime(taskDate) <= stripTime(today) && 
                    snzTimeMins != null && 
                    currentTimeMins != null && 
                    snzTimeMins < currentTimeMins
                ) {
                    val newText = text.replace(match.value, "").trim()
                    taskLine.text = newText
                    reviewer.saveLine(taskLine, newText)
                }
            }
        }

        filteredTasks = applyFilters(
            reviewer.lines,
            dateFilter,
            charFilter,
            excludeHourlySnooze
        )

        println("Re-selecting the first item in the newly filtered list.")
        selectedFilePathAndLine = filteredTasks.firstOrNull()?.let { it.filePath to it.lineIndex }
    }

    directoryLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri?.let {
            val directoryPath = it.path.toString()
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
        LaunchedEffect(Unit) {
            refreshTasks()
        }

        // --- Display the edit dialog if needed ---
        if (showEditDialog) {
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Edit Task") },
                text = {
                    Column {
                        Text("Edit the task text:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editTaskText,
                            onValueChange = { editTaskText = it }
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            editingTask?.let { task ->
                                // Save changes if the dialog's text was modified.
                                task.text = editTaskText
                                reviewer.saveLine(task, editTaskText)
                            }
                            showEditDialog = false
                            refreshTasks() // Refresh tasks to update the list
                        }
                    ) {
                        Text("OK")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
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
                        // Replaced "Char Filter" dropdown with a searchable/dropdown input.
                        SearchableDropdown(
                            label = "[Search]",
                            value = charFilter,
                            onValueChange = { newValue ->
                                charFilter = newValue
                                // If search box is not empty, force date filter to all;
                                // if cleared, reset to "today_or_before"
                                dateFilter = if (newValue.isNotEmpty()) "all" else "today_or_before"
                                refreshTasks()
                            },
                            options = listOf("", "drive", "golf", "errand", "household", "Jean", "Clara", "Clyde", "Charlie", "obsidian")
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

                    LaunchedEffect(filteredTasks) {
                        if (selectedFilePathAndLine == null && filteredTasks.isNotEmpty()) {
                            selectedFilePathAndLine =
                                filteredTasks.first().filePath to filteredTasks.first().lineIndex
                            println("Auto-selected first item: ${selectedFilePathAndLine?.second}")
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(filteredTasks) { taskLine ->
                            val bgColor = when {
                                taskLine.text.contains("⏫") -> Color.Red.copy(alpha = 0.4f)
                                taskLine.text.contains("🔽") -> Color.Green.copy(alpha = 0.4f)
                                else -> Color.Transparent
                            }

                            val isSelected = (taskLine.filePath == selectedFilePathAndLine?.first &&
                                    taskLine.lineIndex == selectedFilePathAndLine?.second)

                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(if (isSelected) Color.White else bgColor)
                                    .combinedClickable(
                                        onClick = {
                                            selectedFilePathAndLine = taskLine.filePath to taskLine.lineIndex
                                            println("Item selected: ${selectedFilePathAndLine?.second}")
                                        },
                                        onDoubleClick = {
                                            val urlRegex = Regex("https?://\\S+")
                                            val match = urlRegex.find(taskLine.text)
                                            match?.let {
                                                val url = it.value
                                                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                    addCategory(Intent.CATEGORY_BROWSABLE)
                                                }
                                                context.startActivity(Intent.createChooser(intent, "Open with"))
                                            }
                                        },
                                        onLongClick = {
                                            editingTask = taskLine
                                            editTaskText = taskLine.text
                                            showEditDialog = true
                                        }
                                    )
                                    .padding(8.dp)
                            ) {
                                Text(
                                    text = taskLine.text,
                                    color = if (isSelected) Color.Black else Color.White,
                                    style = MaterialTheme.typography.bodyLarge.copy(
                                        fontFamily = FontFamily.Default,
                                        letterSpacing = (-0.2).sp
                                    )
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
                        onSnoozeMinutes = { m ->
                            doSnoozeMinutes(
                                m,
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
                        onSnoozeTo1p = {
                            doSnoozeToSpecificTime(
                                13, 0,
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
                        onSnoozeTo4p = {
                            doSnoozeToSpecificTime(
                                16, 0,
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
                        onSnoozeTo7p = {
                            doSnoozeToSpecificTime(
                                19, 0,
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
                        },
                        onHighPriority = {
                            doChangePriority(
                                "⏫",
                                reviewer,
                                getSelectedTask(reviewer, selectedFilePathAndLine),
                                historyStack,
                                ::refreshTasks
                            )
                        },
                        onRemovePriority = {
                            doChangePriority(
                                "remove",
                                reviewer,
                                getSelectedTask(reviewer, selectedFilePathAndLine),
                                historyStack,
                                ::refreshTasks
                            )
                        },
                        onRemoveAllPriorities = {
                            doRemoveAllPriorities(reviewer, historyStack, ::refreshTasks)
                        },
                        // NEW: Provide the onAddTag lambda – note that we retrieve the selectedTask as before.
                        onAddTag = { tag ->
                            doAddTag(
                                reviewer,
                                getSelectedTask(reviewer, selectedFilePathAndLine),
                                historyStack,
                                tag,
                                ::refreshTasks
                            )
                        },
                        reviewer = reviewer,
                        openNewTaskDialogInitial = initialOpenNewTask
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

    // Filter by character (case-insensitive)
    val charFiltered = if (charFilter.isNotEmpty()) {
        dateFiltered.filter { it.text.contains(charFilter, ignoreCase = true) }
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
            newList.add(line)
        } else {
            // Declare snzTime in the current scope.
            val snzTimeStr = snzMatch.groupValues[1]
            val snzTime = parseTime(snzTimeStr)
            if (dateMatch != null) {
                val lineDate = dateFmt.parse(dateMatch.groupValues[1]) ?: todayDate
                if (lineDate.after(todayDate)) {
                    newList.add(line)
                } else if (lineDate.before(todayDate)) {
                    newList.add(line)
                } else {
                    // It's today's date
                    // If the snz time has passed, remove snz
                    if (snzTime != null && nowTime != null && nowTime >= snzTime) {
                        newList.add(line)
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

    // Sort the tasks so that high priority items appear first
    return newList.sortedByDescending { taskLine ->
        when {
            taskLine.text.contains("⏫") -> 2 // High priority
            taskLine.text.contains("🔽") -> 0 // Low priority
            else -> 1 // Normal priority
        }
    }
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
        // Capture the filtered list BEFORE changing the task so we know its position.
        val filteredBefore = applyFilters(
            reviewer.lines,
            dateFilter,
            charFilter,
            excludeHourlySnooze
        )
        val currentIndex = filteredBefore.indexOfFirst { 
            it.filePath == selectedTask.filePath && it.lineIndex == selectedTask.lineIndex 
        }
    
        // Do the snooze update
        val oldCopy = selectedTask.copy()
        val newText = reviewer.snoozeLine(selectedTask, days)
        selectedTask.text = newText
        val indexInReviewer = reviewer.lines.indexOf(selectedTask)
        history.add(HistoryItem(indexInReviewer, oldCopy))
        reviewer.saveLine(selectedTask, newText)
    
        // Refresh tasks (this will update the filtered list)
        refresh()
    
        // After refresh, compute the new filtered list
        val filteredAfter = applyFilters(
            reviewer.lines,
            dateFilter,
            charFilter,
            excludeHourlySnooze
        )
    
        // Select the same index as before if available; otherwise, the last item.
        val newSelection = when {
            filteredAfter.isEmpty() -> null
            currentIndex in filteredAfter.indices -> filteredAfter[currentIndex]
            currentIndex >= filteredAfter.size -> filteredAfter.last()
            else -> filteredAfter.first()
        }
        selectNextTask(newSelection)
    }
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
        // Capture the filtered list BEFORE changing the task so we know its position.
        val filteredBefore = applyFilters(
            reviewer.lines,
            dateFilter,
            charFilter,
            excludeHourlySnooze
        )
        val currentIndex = filteredBefore.indexOfFirst { 
            it.filePath == selectedTask.filePath && it.lineIndex == selectedTask.lineIndex 
        }
    
        // Do the snooze update
        val oldCopy = selectedTask.copy()
        val newText = reviewer.snoozeHours(selectedTask, hours)
        selectedTask.text = newText
        val indexInReviewer = reviewer.lines.indexOf(selectedTask)
        history.add(HistoryItem(indexInReviewer, oldCopy))
        reviewer.saveLine(selectedTask, newText)
    
        // Refresh tasks (this will update the filtered list)
        refresh()
    
        // After refresh, compute the new filtered list
        val filteredAfter = applyFilters(
            reviewer.lines,
            dateFilter,
            charFilter,
            excludeHourlySnooze
        )
    
        // Select the same index as before if available; otherwise, the last item.
        val newSelection = when {
            filteredAfter.isEmpty() -> null
            currentIndex in filteredAfter.indices -> filteredAfter[currentIndex]
            currentIndex >= filteredAfter.size -> filteredAfter.last()
            else -> filteredAfter.first()
        }
        selectNextTask(newSelection)
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
            textStyle = TextStyle(color = Color.White),
            label = { Text(label, color = Color.White) },
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
@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class, 
       androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
fun BottomButtons(
    getSelectedTask: (selectedFilePathAndLine: Pair<String, Int>?) -> TaskLine?,
    onSnoozeHours: (Int) -> Unit,
    onSnoozeDays: (Int) -> Unit,
    onSnoozeMinutes: (Int) -> Unit,
    onSnoozeTo1p: () -> Unit,
    onSnoozeTo4p: () -> Unit,
    onSnoozeTo7p: () -> Unit,
    onCompleted: () -> Unit,
    onUndo: () -> Unit,
    onRefresh: () -> Unit,
    onDelete: () -> Unit,
    onHighPriority: () -> Unit,
    onRemovePriority: () -> Unit,
    onRemoveAllPriorities: () -> Unit,
    // New callback for adding a tag:
    onAddTag: (String) -> Unit,
    reviewer: MarkdownReviewer,
    openNewTaskDialogInitial: Boolean = false
) {
    val context = LocalContext.current
    var showNewTaskDialog by remember { mutableStateOf(openNewTaskDialogInitial) }
    var showReportDialog by remember { mutableStateOf(false) }
    var newTaskText by remember { mutableStateOf(TextFieldValue("")) }
    var selectedPriority by remember { mutableStateOf(TaskPriority.Normal) }
    val clipboardManager = LocalClipboardManager.current

    val allCounts = ButtonPressCounter.getAllCounts(context)

    // Sort dynamic buttons by usage (highest count first)
    val buttonsByFrequency = dynamicButtons.sortedByDescending { allCounts[it.id] ?: 0 }

    // Select the 20 most common buttons
    val prioritizedButtons = buttonsByFrequency.take(20)

    // Separate prioritized buttons into snooze and other types
    val prioritizedSnoozeButtons = prioritizedButtons.filter { it.type == ButtonType.SNOOZE }
        // For snooze buttons with a duration, sort them in ascending order of duration;
        // if duration is null, push those to the end.
        .sortedWith(compareBy({ it.durationInMinutes ?: Int.MAX_VALUE }))

    val prioritizedOtherButtons = prioritizedButtons.filter { it.type == ButtonType.OTHER }

    // Combine back if you wish to display them in one row:
    val displayButtons = prioritizedSnoozeButtons + prioritizedOtherButtons

    // Prepare overflow lists (those not in the top 20)
    val overflowButtons = dynamicButtons.filter { it !in prioritizedButtons }
    val overflowSnooze = overflowButtons.filter { it.type == ButtonType.SNOOZE }
    val overflowOther = overflowButtons.filter { it.type == ButtonType.OTHER }

    if (showNewTaskDialog) {
        val focusRequester = remember { FocusRequester() }
        val keyboardController = LocalSoftwareKeyboardController.current

        AlertDialog(
            onDismissRequest = { showNewTaskDialog = false },
            title = { Text("New Task") },
            text = {
                Column(
                    modifier = Modifier.focusRequester(focusRequester)
                ) {
                    Text("Enter the text for your new task:")
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = newTaskText,
                        onValueChange = { newTaskText = it },
                        trailingIcon = {
                            if (newTaskText.text.isNotEmpty()) {
                                IconButton(onClick = { newTaskText = TextFieldValue("") }) {
                                    Icon(
                                        imageVector = Icons.Default.Clear,
                                        contentDescription = "Clear new task text"
                                    )
                                }
                            }
                        }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    // Priority selection with three options: High, Normal, Low
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Priority:", color = Color.Black)
                        Spacer(modifier = Modifier.width(8.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { selectedPriority = TaskPriority.High }
                        ) {
                            RadioButton(
                                selected = selectedPriority == TaskPriority.High,
                                onClick = { selectedPriority = TaskPriority.High }
                            )
                            Text("High", color = Color.Black)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { selectedPriority = TaskPriority.Normal }
                        ) {
                            RadioButton(
                                selected = selectedPriority == TaskPriority.Normal,
                                onClick = { selectedPriority = TaskPriority.Normal }
                            )
                            Text("Normal", color = Color.Black)
                        }
                        Spacer(modifier = Modifier.width(16.dp))
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.clickable { selectedPriority = TaskPriority.Low }
                        ) {
                            RadioButton(
                                selected = selectedPriority == TaskPriority.Low,
                                onClick = { selectedPriority = TaskPriority.Low }
                            )
                            Text("Low", color = Color.Black)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showNewTaskDialog = false
                        if (newTaskText.text.isNotBlank()) {
                            doAddNewTask(
                                reviewer = reviewer,
                                taskText = newTaskText.text,
                                priority = selectedPriority
                            )
                            onRefresh()
                        }
                    }
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewTaskDialog = false }) {
                    Text("Cancel")
                }
            }
        )

        LaunchedEffect(showNewTaskDialog) {
            if (newTaskText.text.isNotEmpty()) {
                newTaskText = newTaskText.copy(
                    selection = TextRange(0, newTaskText.text.length)
                )
            }
            delay(100) // slight delay to help request focus
            focusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Button Press Report") },
            text = {
                val counts = ButtonPressCounter.getAllCounts(context)
                val sortedCounts = counts.entries.sortedByDescending { it.value }
                Text(
                    text = if (sortedCounts.isEmpty()) {
                        "No button presses recorded."
                    } else {
                        sortedCounts.joinToString("\n") { "${it.key}: ${it.value}" }
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    Column {
        // First row: Added snooze options "2h" and "4h"
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val firstRowOptions = listOf("10m", "30m", "1h", "2h", "3h", "4h")
            firstRowOptions.forEach { option ->
                ElevatedButton(onClick = {
                    ButtonPressCounter.increment(context, option)
                    when {
                        option.endsWith("m") -> {
                            val minutes = option.dropLast(1).toInt()
                            onSnoozeMinutes(minutes)
                        }
                        option.endsWith("h") -> {
                            onSnoozeHours(option.dropLast(1).toInt())
                        }
                    }
                }) {
                    Text(option)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Second row: "1d", "2d", "4d", "7d"
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val secondRowOptions = listOf("1d", "2d", "4d", "7d")
            secondRowOptions.forEach { option ->
                ElevatedButton(
                    onClick = {
                        ButtonPressCounter.increment(context, option)
                        onSnoozeDays(option.dropLast(1).toInt())
                    },
                    colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFF8BC34A))
                ) {
                    Text(option, color = Color.Black)
                }
            }
        }

        Spacer(Modifier.height(4.dp))

        // Third row: "Mon", "Fri", "1p", "4p", and the Overflow menu.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ElevatedButton(
                onClick = {
                    ButtonPressCounter.increment(context, "Mon")
                    val daysToAdd = nextDayOfWeek(Calendar.MONDAY)
                    onSnoozeDays(daysToAdd)
                },
                colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFF67CFFF))
            ) {
                Text("Mon", color = Color.Black)
            }
            ElevatedButton(
                onClick = {
                    ButtonPressCounter.increment(context, "Fri")
                    val daysToAdd = nextDayOfWeek(Calendar.FRIDAY)
                    onSnoozeDays(daysToAdd)
                },
                colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFF67CFFF))
            ) {
                Text("Fri", color = Color.Black)
            }
            ElevatedButton(
                onClick = {
                    ButtonPressCounter.increment(context, "1p")
                    onSnoozeTo1p()
                },
            ) {
                Text("1p")
            }
            ElevatedButton(
                onClick = {
                    ButtonPressCounter.increment(context, "4p")
                    onSnoozeTo4p()
                },
            ) {
                Text("4p")
            }
            Box {
                var expanded by remember { mutableStateOf(false) }
                IconButton(onClick = { expanded = true }) {
                    Icon(
                        imageVector = Icons.Default.MoreVert,
                        contentDescription = "Overflow menu",
                        tint = Color.White
                    )
                }
                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("7 Hours") },
                        onClick = {
                            ButtonPressCounter.increment(context, "7 Hours")
                            expanded = false
                            onSnoozeHours(7)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("7p") },
                        onClick = {
                            ButtonPressCounter.increment(context, "7p")
                            expanded = false
                            onSnoozeTo7p()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("3 Days") },
                        onClick = {
                            ButtonPressCounter.increment(context, "3 Days")
                            expanded = false
                            onSnoozeDays(3)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("5 Days") },
                        onClick = {
                            ButtonPressCounter.increment(context, "5 Days")
                            expanded = false
                            onSnoozeDays(5)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("3 Weeks") },
                        onClick = {
                            ButtonPressCounter.increment(context, "3 Weeks")
                            expanded = false
                            onSnoozeDays(21)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("5 Weeks") },
                        onClick = {
                            ButtonPressCounter.increment(context, "5 Weeks")
                            expanded = false
                            onSnoozeDays(35)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("26 Weeks") },
                        onClick = {
                            ButtonPressCounter.increment(context, "26 Weeks")
                            expanded = false
                            onSnoozeDays(182)
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("High Priority") },
                        onClick = {
                            ButtonPressCounter.increment(context, "High Priority")
                            expanded = false
                            onHighPriority()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove Priority") },
                        onClick = {
                            ButtonPressCounter.increment(context, "Remove Priority")
                            expanded = false
                            onRemovePriority()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Remove All Priorities") },
                        onClick = {
                            ButtonPressCounter.increment(context, "Remove All Priorities")
                            expanded = false
                            onRemoveAllPriorities()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete") },
                        onClick = {
                            ButtonPressCounter.increment(context, "Delete")
                            expanded = false
                            onDelete()
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Add New Task Shortcut") },
                        onClick = {
                            ButtonPressCounter.increment(context, "Add New Task Shortcut")
                            expanded = false
                            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                                val shortcutManager = context.getSystemService(ShortcutManager::class.java)
                                if (shortcutManager.isRequestPinShortcutSupported) {
                                    val shortcutIntent = Intent(context, MainActivity::class.java).apply {
                                        action = Intent.ACTION_VIEW
                                        putExtra("open_new_task", true)
                                    }
                                    val shortcutInfo = ShortcutInfo.Builder(context, "new_task_shortcut")
                                        .setShortLabel("New Task")
                                        .setLongLabel("Add a New Task")
                                        .setIcon(ShortcutIcon.createWithResource(context, android.R.drawable.ic_menu_add))
                                        .setIntent(shortcutIntent)
                                        .build()
                                    shortcutManager.requestPinShortcut(shortcutInfo, null)
                                } else {
                                    Toast.makeText(context, "Pinned shortcuts are not supported", Toast.LENGTH_SHORT).show()
                                }
                            } else {
                                Toast.makeText(context, "Requires Android O or newer", Toast.LENGTH_SHORT).show()
                            }
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("Report") },
                        onClick = {
                            ButtonPressCounter.increment(context, "Report")
                            expanded = false
                            showReportDialog = true
                        }
                    )
                }
            }
        }
        
        Spacer(Modifier.height(4.dp))

        // Weekly snooze row
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val weekSnoozeOptions = listOf(2, 4, 8, 12, 16)
            weekSnoozeOptions.forEach { w ->
                ElevatedButton(onClick = {
                    ButtonPressCounter.increment(context, "${w}w")
                    onSnoozeDays(w * 7)
                }) {
                    Text("${w}w")
                }
            }
        }
        
        Spacer(Modifier.height(4.dp))
        
        // Row containing "New Task" and "Completed"
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ElevatedButton(
                modifier = Modifier.height(96.dp),
                onClick = {
                    // Use clipboard content as default if not empty.
                    newTaskText = TextFieldValue(clipboardManager.getText()?.text ?: "")
                    showNewTaskDialog = true
                },
                colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFFFFC107))
            ) {
                Text("New Task", color = Color.Black)
            }

            Column(
                modifier = Modifier.weight(1f)
            ) {
                ElevatedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        ButtonPressCounter.increment(context, "Completed")
                        onCompleted()
                    }
                ) {
                    Text("Completed (m)")
                }
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    ElevatedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ButtonPressCounter.increment(context, "Undo")
                            onUndo()
                        }
                    ) {
                        Text("Undo (u)")
                    }
                    ElevatedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ButtonPressCounter.increment(context, "Refresh")
                            onRefresh()
                        }
                    ) {
                        Text("Refresh (r)")
                    }
                }
            }
        }
        
        Spacer(Modifier.height(4.dp))
        
        // (Optional) Second weekly snooze row (if still needed)
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            val weekSnoozeOptions = listOf(2, 4, 8, 12, 16)
            weekSnoozeOptions.forEach { w ->
                ElevatedButton(onClick = {
                    ButtonPressCounter.increment(context, "${w}w")
                    onSnoozeDays(w * 7)
                }) {
                    Text("${w}w")
                }
            }
        }
        
        Spacer(Modifier.height(4.dp))
        
        // NEW: Row for the Tag Menu – this will display an icon that expands to list the tags.
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center
        ) {
            TagMenu(onTagSelected = onAddTag)
        }
        
        Spacer(Modifier.height(4.dp))
    }
}

fun doSnoozeMinutes(
    minutes: Int,
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
        // Capture the filtered list BEFORE changing the task so we know its position.
        val filteredBefore = applyFilters(
            reviewer.lines,
            dateFilter,
            charFilter,
            excludeHourlySnooze
        )
        val currentIndex = filteredBefore.indexOfFirst { 
            it.filePath == selectedTask.filePath && it.lineIndex == selectedTask.lineIndex 
        }

        val oldCopy = selectedTask.copy()
        val newText = reviewer.snoozeMinutes(selectedTask, minutes)
        selectedTask.text = newText
        val indexInReviewer = reviewer.lines.indexOf(selectedTask)
        history.add(HistoryItem(indexInReviewer, oldCopy))
        reviewer.saveLine(selectedTask, newText)

        // Refresh tasks (this will update the filtered list)
        refresh()

        // After refresh, compute the new filtered list
        val filteredAfter = applyFilters(
            reviewer.lines,
            dateFilter,
            charFilter,
            excludeHourlySnooze
        )

        // Select the same index as before if available; otherwise, the last item.
        val newSelection = when {
            filteredAfter.isEmpty() -> null
            currentIndex in filteredAfter.indices -> filteredAfter[currentIndex]
            currentIndex >= filteredAfter.size -> filteredAfter.last()
            else -> filteredAfter.first()
        }
        selectNextTask(newSelection)
    }
}

fun nextDayOfWeek(dayOfWeek: Int): Int {
    val cal = Calendar.getInstance()
    val currentDay = cal.get(Calendar.DAY_OF_WEEK)
    var daysToAdd = dayOfWeek - currentDay
    if (daysToAdd <= 0) {
        daysToAdd += 7
    }
    return daysToAdd
}

/**
 * Adds a new task to "New Tasks.md" at the top of the file with today's date.
 * The task text now includes a priority marker based on the isHighPriority value.
 */
fun doAddNewTask(
    reviewer: MarkdownReviewer,
    taskText: String,
    priority: TaskPriority // changed parameter type
) {
    val dateStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
    val priorityMarker = when (priority) {
        TaskPriority.High -> "⏫"
        TaskPriority.Low -> "🔽"
        TaskPriority.Normal -> ""  // No marker for normal priority
    }
    val lineToInsert = if (priorityMarker.isBlank()) {
        "* [ ] $taskText 📅 $dateStr"
    } else {
        "* [ ] $taskText $priorityMarker 📅 $dateStr"
    }

    // The file "New Tasks.md" in the reviewer's directory path
    val newTasksFile = File(reviewer.getDirectoryPath(), "New Tasks.md")
    val allLines = if (newTasksFile.exists()) {
        newTasksFile.readLines().toMutableList()
    } else {
        mutableListOf()
    }

    // Insert at the top of the file.
    allLines.add(0, lineToInsert)
    newTasksFile.writeText(allLines.joinToString("\n"))
}

/**
 * Updates selection by index, given the filteredTasks list
 */
private fun setSelectedItemPosition(
    selectedPos: Int,
    filteredTasks: List<TaskLine>,
    currentSelection: Pair<String, Int>?
): Pair<String, Int>? {
    val items = filteredTasks
    // If no items, or the position is out of range, reset to -1
    val newPosition = if (items.isEmpty()) {
        -1
    } else {
        if (selectedPos < 0 || selectedPos >= items.size) 0 else selectedPos
    }

    // If we have a valid newPosition, pick the item at that position; otherwise null
    return if (newPosition == -1) {
        null
    } else {
        items[newPosition].filePath to items[newPosition].lineIndex
    }
}

fun onItemRemoved(
    position: Int,
    filteredTasks: List<TaskLine>,
    selectedFilePathAndLine: Pair<String, Int>?
): Pair<String, Int>? {
    return if (position == selectedFilePathAndLine?.second) {
        setSelectedItemPosition(
            selectedPos = position,
            filteredTasks = filteredTasks,
            currentSelection = selectedFilePathAndLine
        )
    } else {
        selectedFilePathAndLine
    }
}

@Composable
fun SearchableDropdown(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    options: List<String>,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    // Filter the options based on the current search value
    val filteredOptions = if (value.isEmpty()) options else options.filter {
        it.contains(value, ignoreCase = true)
    }
    
    Box(modifier = modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {
                onValueChange(it)
                expanded = true // Re-open the dropdown when the value changes
            },
            textStyle = TextStyle(color = Color.White),
            label = { Text(label, color = Color.White) },
            trailingIcon = {
                Row {
                    if (value.isNotEmpty()) {
                        IconButton(onClick = {
                            onValueChange("")
                            expanded = false
                        }) {
                            Icon(
                                imageVector = Icons.Default.Clear,
                                contentDescription = "Clear search"
                            )
                        }
                    }
                    IconButton(onClick = { expanded = !expanded }) {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = "Toggle Dropdown"
                        )
                    }
                }
            },
            modifier = Modifier.fillMaxWidth()
        )
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.fillMaxWidth()
        ) {
            filteredOptions.forEach { option ->
                DropdownMenuItem(
                    text = { Text(text = option) },
                    onClick = {
                        onValueChange(option)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
fun customOutlinedTextFieldColors(
    cursorColor: Color = Color.White,
    focusedBorderColor: Color = Color.White,
    errorBorderColor: Color = Color.Red,
    focusedLabelColor: Color = Color.White,
    unfocusedLabelColor: Color = Color.White,
    disabledLabelColor: Color = Color.White.copy(alpha = 0.38f),
    errorLabelColor: Color = Color.Red,
    containerColor: Color = Color.Transparent
): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
    )
}

fun doSnoozeToSpecificTime(
    targetHour: Int,
    targetMinute: Int,
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
        // Capture the filtered list BEFORE changing the task so we know its position.
        val filteredBefore = applyFilters(
            reviewer.lines,
            dateFilter,
            charFilter,
            excludeHourlySnooze
        )
        val currentIndex = filteredBefore.indexOfFirst { 
            it.filePath == selectedTask.filePath && it.lineIndex == selectedTask.lineIndex 
        }

        val oldCopy = selectedTask.copy()
        val newText = reviewer.snoozeToSpecificTime(selectedTask, targetHour, targetMinute)
        selectedTask.text = newText
        val indexInReviewer = reviewer.lines.indexOf(selectedTask)
        history.add(HistoryItem(indexInReviewer, oldCopy))
        reviewer.saveLine(selectedTask, newText)

        // Refresh tasks (this will update the filtered list)
        refresh()

        // After refresh, compute the new filtered list
        val filteredAfter = applyFilters(
            reviewer.lines,
            dateFilter,
            charFilter,
            excludeHourlySnooze
        )

        // Select the same index as before if available; otherwise, the last item.
        val newSelection = when {
            filteredAfter.isEmpty() -> null
            currentIndex in filteredAfter.indices -> filteredAfter[currentIndex]
            currentIndex >= filteredAfter.size -> filteredAfter.last()
            else -> filteredAfter.first()
        }
        selectNextTask(newSelection)
    }
}

fun doRemoveAllPriorities(
    reviewer: MarkdownReviewer,
    history: MutableList<HistoryItem>,
    refresh: () -> Unit
) {
    // Iterate over all task lines and remove high priority (⏫) and low priority (🔽) markers.
    reviewer.lines.forEachIndexed { index, task ->
        if (task.text.contains("⏫") || task.text.contains("🔽")) {
            val oldCopy = task.copy()
            val newText = task.text.replace(Regex("[⏫🔽]"), "").trim()
            task.text = newText
            history.add(HistoryItem(index, oldCopy))
            reviewer.saveLine(task, newText)
        }
    }
    refresh()
}

// Define an enum to represent the type of button.
enum class ButtonType {
    SNOOZE,
    OTHER
}

// Data class for dynamic buttons.
data class DynamicButton(
    val id: String,
    val type: ButtonType,
    val durationInMinutes: Int? = null // For snooze buttons, etc.
)

// Define your list of dynamic buttons.
val dynamicButtons = listOf(
    DynamicButton("10m", ButtonType.SNOOZE, 10),
    DynamicButton("30m", ButtonType.SNOOZE, 30),
    DynamicButton("1h", ButtonType.SNOOZE, 60),
    DynamicButton("2h", ButtonType.SNOOZE, 120),
    DynamicButton("3h", ButtonType.SNOOZE, 180),
    DynamicButton("4h", ButtonType.SNOOZE, 240),
    // Other buttons might be declared as "OTHER"
    DynamicButton("Mon", ButtonType.OTHER),
    DynamicButton("Fri", ButtonType.OTHER),
    DynamicButton("1p", ButtonType.OTHER),
    DynamicButton("4p", ButtonType.OTHER),
    DynamicButton("7 Hours", ButtonType.OTHER),
    DynamicButton("7p", ButtonType.OTHER),
    DynamicButton("3 Days", ButtonType.OTHER),
    DynamicButton("5 Days", ButtonType.OTHER),
    DynamicButton("3 Weeks", ButtonType.OTHER),
    DynamicButton("5 Weeks", ButtonType.OTHER),
    DynamicButton("26 Weeks", ButtonType.OTHER),
    DynamicButton("High Priority", ButtonType.OTHER),
    DynamicButton("Remove Priority", ButtonType.OTHER),
    DynamicButton("Remove All Priorities", ButtonType.OTHER),
    DynamicButton("Delete", ButtonType.OTHER),
    DynamicButton("Add New Task Shortcut", ButtonType.OTHER),
    DynamicButton("Report", ButtonType.OTHER)
)

fun backgroundColorForButton(button: DynamicButton): Color {
    return when {
        button.type != ButtonType.SNOOZE -> Color.LightGray
        // For snooze buttons, try to deduce the unit based on duration in minutes.
        button.durationInMinutes == null -> Color.Gray
        button.durationInMinutes < 60 -> Color(0xFFFFD54F) // minutes (yellowish)
        button.durationInMinutes in 60 until 1440 -> Color(0xFFEF5350) // hours (redish)
        button.durationInMinutes in 1440 until 10080 -> Color(0xFF66BB6A) // days (greenish)
        else -> Color(0xFF42A5F5) // weeks or more (blueish)
    }
}
