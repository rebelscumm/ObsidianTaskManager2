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
import androidx.compose.foundation.lazy.LazyRow
import android.graphics.drawable.Icon as ShortcutIcon
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.TextRange
import androidx.compose.material.icons.filled.LocalOffer
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.core.view.WindowCompat
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll

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

    fun addNewTask(taskText: String, priority: TaskPriority = TaskPriority.Normal) {
        val directory = File(directoryPath)
        if (!directory.exists() || !directory.isDirectory) {
            Toast.makeText(context, "Directory does not exist", Toast.LENGTH_SHORT).show()
            return
        }
        // Try to find an existing markdown file (skipping hidden files)
        val mdFiles = directory.listFiles { file ->
            !file.name.startsWith(".") && file.extension.equals("md", ignoreCase = true)
        }
        // If none is found, create a "tasks.md" file in the directory.
        val file = if (mdFiles.isNullOrEmpty()) {
            File(directory, "tasks.md")
        } else {
            mdFiles.sortedBy { it.name }[0]
        }
        // Prepare today's date string.
        val todayStr = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        
        // Start with the raw trimmed task text.
        var formattedTask = taskText.trim()
        
        // Ensure the task has the "[ ]" marker; if not, add it at the beginning.
        if (!formattedTask.contains("[ ]")) {
            formattedTask = "* [ ] " + formattedTask
        }
        
        // Build the priority marker string (if any).
        val priorityMarker = if (priority != TaskPriority.Normal) {
            when (priority) {
                TaskPriority.High -> "⏫"
                TaskPriority.Low -> "🔽"
                else -> ""
            }
        } else {
            ""
        }
        
        // Append the priority marker (if any) before the date marker at the end.
        if (!formattedTask.contains("📅")) {
            if (priorityMarker.isNotEmpty()) {
                formattedTask += " $priorityMarker"
            }
            formattedTask += " 📅 $todayStr"
        }
        
        // Read the current file content (if any) and prepend the new task.
        val existingContent = if (file.exists()) file.readText() else ""
        val newContent = formattedTask + "\n" + existingContent
        file.writeText(newContent)
        Toast.makeText(context, "New task added", Toast.LENGTH_SHORT).show()
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
        // Disable edge-to-edge mode by NOT calling enableEdgeToEdge().
        // Either remove the following line or replace it with:
        WindowCompat.setDecorFitsSystemWindows(window, true)
        
        window.decorView.setBackgroundColor(Color(0xFF2d2d2d).toArgb())

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
    availableTags: List<String> = listOf("drive", "dictate", "errand", "household", "Jean", "Clara", "Clyde", "Charlie", ),
    onTagSelected: (String) -> Unit
) {
    var expanded by remember { mutableStateOf(false) }
    Box {
        // Use a Button with the same color as snoozes/functions overflow to open the tag menu
        Button(
            onClick = { expanded = true },
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF121212))
        ) {
            Icon(
                imageVector = Icons.Default.LocalOffer,
                contentDescription = "Tag Menu",
                tint = Color.White
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
            modifier = Modifier.background(Color(0xFF121212))
        ) {
            availableTags.forEach { tag ->
                DropdownMenuItem(
                    text = { Text("#$tag", color = Color.White) },
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
    var editTaskText by remember { mutableStateOf(TextFieldValue("")) }

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
            // Create a FocusRequester for the edit dialog text field.
            val editFocusRequester = remember { FocusRequester() }

            // When the dialog is shown, request focus and select all text.
            LaunchedEffect(Unit) {
                editFocusRequester.requestFocus()
                editTaskText = editTaskText.copy(
                    selection = TextRange(0, editTaskText.text.length)
                )
            }

            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Edit Task") },
                text = {
                    Column {
                        Text("Edit the task text:")
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editTaskText,
                            onValueChange = { editTaskText = it },
                            modifier = Modifier.focusRequester(editFocusRequester)
                        )
                    }
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            editingTask?.let { task ->
                                // Save changes using edited text.
                                task.text = editTaskText.text
                                reviewer.saveLine(task, editTaskText.text)
                            }
                            showEditDialog = false
                            refreshTasks() // Refresh tasks to update the list.
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

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp)
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
                    options = listOf("", "drive", "dictate", "golf", "errand", "household", "Jean", "Clara", "Clyde", "Charlie", "obsidian", )
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
                                    editTaskText = TextFieldValue(taskLine.text)  // initialize with the existing text
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
@OptIn(
    ExperimentalMaterial3Api::class,
    ExperimentalFoundationApi::class,
    ExperimentalComposeUiApi::class,
    ExperimentalLayoutApi::class
)
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
    onAddTag: (String) -> Unit,
    reviewer: MarkdownReviewer,
    openNewTaskDialogInitial: Boolean = false
) {
    val context = LocalContext.current
    var showNewTaskDialog by remember { mutableStateOf(openNewTaskDialogInitial) }
    var showReportDialog by remember { mutableStateOf(false) }
    var newTaskText by remember { mutableStateOf(TextFieldValue("")) }
    val clipboardManager = LocalClipboardManager.current

    // --- New Dynamic Button Ordering Based on Frequency ---
    // Get the frequency counts for every button (using ButtonPressCounter)
    val counts = ButtonPressCounter.getAllCounts(context)
    // Change the limit from 16 to 18 to allow two extra dynamic buttons.
    val topButtons = dynamicButtons.sortedByDescending { counts[it.id] ?: 0 }.take(18)

    // Among the top buttons, filter the snooze buttons and sort them by ascending duration.
    val mainSnoozes = topButtons.filter { it.type == ButtonType.SNOOZE }
        .sortedWith(compareBy(
            { dynamicButtonSortKey(it).first },
            { dynamicButtonSortKey(it).second },
            { dynamicButtonSortKey(it).third }
        ))
    // The remaining top buttons (type OTHER—the functions) are sorted by id.
    val mainFunctions = topButtons.filter { it.type == ButtonType.OTHER }
        .sortedBy { it.id }
    // The buttons to show in the main row are the snoozes (first) followed by the functions.
    val displayButtons = mainSnoozes + mainFunctions

    // For the overflow menus, take any dynamic buttons not in the top group.
    val overflowSnoozes = dynamicButtons.filter { it.type == ButtonType.SNOOZE && it !in topButtons }
    val overflowFunctions = dynamicButtons.filter { it.type == ButtonType.OTHER && it !in topButtons }

    // State variables for the dropdown menus.
    var showSnoozeOverflow by remember { mutableStateOf(false) }
    var showOtherOverflow by remember { mutableStateOf(false) }

    // Local helper function to map a dynamic button to its onClick action.
    fun handleDynamicButtonClick(button: DynamicButton) {
        ButtonPressCounter.increment(context, button.id)
        when (button.id) {
            "10m", "30m" -> onSnoozeMinutes(button.durationInMinutes ?: 0)
            "1h", "2h", "3h", "4h", "7h" -> { // Add "7h" here.
                val hours = button.id.dropLast(1).toIntOrNull() ?: 0
                onSnoozeHours(hours)
            }
            "1d" -> onSnoozeDays(1)
            "2d" -> onSnoozeDays(2)
            "3d" -> onSnoozeDays(3)
            "4d" -> onSnoozeDays(4)
            "5d" -> onSnoozeDays(5)
            "6d" -> onSnoozeDays(6)
            "1w" -> onSnoozeDays(7) // Added case for "1w"
            "2w" -> onSnoozeDays(14)
            "3w" -> onSnoozeDays(21)
            "5w" -> onSnoozeDays(35)
            "8w" -> onSnoozeDays(56)
            "12w" -> onSnoozeDays(84)
            "26w" -> onSnoozeDays(182)
            "Mon" -> onSnoozeDays(nextDayOfWeek(Calendar.MONDAY))
            "Fri" -> onSnoozeDays(nextDayOfWeek(Calendar.FRIDAY))
            "1p" -> onSnoozeTo1p()
            "4p" -> onSnoozeTo4p()
            "7p" -> onSnoozeTo7p()
            "High Priority" -> onHighPriority()
            "Remove Priority" -> onRemovePriority()
            "Remove All Priorities" -> onRemoveAllPriorities()
            "Delete" -> onDelete()
            "Add New Task Shortcut" -> {
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
            "Report" -> { showReportDialog = true }
            else -> { /* no-op */ }
        }
    }

    Column {
        // --- Main Row of Dynamic Buttons ---
        // We show the extracted "displayButtons" in a FlowRow.
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            displayButtons.forEach { button ->
                // For buttons of type OTHER (functions), force a dark background and white text.
                val buttonColor = if (button.type == ButtonType.OTHER) {
                    Color(0xFF333333)
                } else {
                    // Snooze buttons keep their existing color coding.
                    backgroundColorForButton(button)
                }
                ElevatedButton(
                    onClick = { handleDynamicButtonClick(button) },
                    colors = ButtonDefaults.elevatedButtonColors(containerColor = buttonColor)
                ) {
                    Text(button.id, color = Color.White)
                }
            }
        }

        // --- Overflow Menus for Remaining Buttons ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            // Snooze Overflow Button labeled "Snoozes"
            Box {
                Button(
                    onClick = { showSnoozeOverflow = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF121212))
                ) {
                    Text("Snoozes", color = Color.White)
                }
                DropdownMenu(
                    expanded = showSnoozeOverflow,
                    onDismissRequest = { showSnoozeOverflow = false }
                ) {
                    overflowSnoozes.forEach { button ->
                        DropdownMenuItem(
                            modifier = Modifier.background(backgroundColorForButton(button)),
                            text = { 
                                Text(
                                    text = button.id,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = highContrastTextColor(backgroundColorForButton(button))
                                )
                            },
                            onClick = {
                                handleDynamicButtonClick(button)
                                showSnoozeOverflow = false
                            }
                        )
                    }
                }
            }
            // Tag Menu remains unchanged; it already uses the updated TagMenu() implementation.
            TagMenu(
                onTagSelected = onAddTag
            )
            // Overflow Functions Button labeled "Functions"
            Box {
                Button(
                    onClick = { showOtherOverflow = true },
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF121212))
                ) {
                    Text("Functions", color = Color.White)
                }
                DropdownMenu(
                    expanded = showOtherOverflow,
                    onDismissRequest = { showOtherOverflow = false }
                ) {
                    overflowFunctions.forEach { button ->
                        DropdownMenuItem(
                            modifier = Modifier.background(backgroundColorForButton(button)),
                            text = { 
                                Text(
                                    text = button.id,
                                    modifier = Modifier.fillMaxWidth(),
                                    color = highContrastTextColor(backgroundColorForButton(button))
                                )
                            },
                            onClick = {
                                handleDynamicButtonClick(button)
                                showOtherOverflow = false
                            }
                        )
                    }
                }
            }
        }

        // --- The Rest of the UI (New Task, Completed, Undo, Refresh, etc.) ---
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly
        ) {
            ElevatedButton(
                modifier = Modifier.height(96.dp),
                onClick = {
                    val clipboardContent = clipboardManager.getText()?.text.orEmpty()
                    newTaskText = if (clipboardContent.isNotEmpty()) {
                        TextFieldValue(
                            text = clipboardContent,
                            selection = TextRange(0, clipboardContent.length)
                        )
                    } else {
                        TextFieldValue("")
                    }
                    showNewTaskDialog = true
                },
                colors = ButtonDefaults.elevatedButtonColors(containerColor = Color(0xFFFFC107))
            ) {
                Text("New Task", color = Color.White)
            }
            Column(
                modifier = Modifier.weight(1f)
            ) {
                ElevatedButton(
                    modifier = Modifier.fillMaxWidth(),
                    onClick = {
                        ButtonPressCounter.increment(context, "Completed")
                        onCompleted()
                    },
                    colors = ButtonDefaults.elevatedButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text("Completed (m)", color = highContrastTextColor(MaterialTheme.colorScheme.primary))
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
                        },
                        colors = ButtonDefaults.elevatedButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Undo (u)", color = highContrastTextColor(MaterialTheme.colorScheme.primary))
                    }
                    ElevatedButton(
                        modifier = Modifier.weight(1f),
                        onClick = {
                            ButtonPressCounter.increment(context, "Refresh")
                            onRefresh()
                        },
                        colors = ButtonDefaults.elevatedButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Refresh (r)", color = highContrastTextColor(MaterialTheme.colorScheme.primary))
                    }
                }
            }
        }
    }

    // --- Report Dialog (unchanged) ---
    if (showReportDialog) {
        AlertDialog(
            onDismissRequest = { showReportDialog = false },
            title = { Text("Button Press Report") },
            text = {
                val countsMap = ButtonPressCounter.getAllCounts(context)
                val sortedCounts = countsMap.entries.sortedByDescending { it.value }
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 200.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = if (sortedCounts.isEmpty()) {
                            "No button presses recorded."
                        } else {
                            sortedCounts.joinToString("\n") { "${it.key}: ${it.value}" }
                        }
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showReportDialog = false }) {
                    Text("OK")
                }
            }
        )
    }

    // --- New Task Dialog with focus and selection ---
    if (showNewTaskDialog) {
        var newTaskPriority by remember { mutableStateOf(TaskPriority.Normal) }
        // Create a FocusRequester for the OutlinedTextField
        val focusRequester = remember { FocusRequester() }
        // When the dialog opens, request focus and update the text selection.
        LaunchedEffect(Unit) {
            focusRequester.requestFocus()
            newTaskText = newTaskText.copy(selection = TextRange(0, newTaskText.text.length))
        }
        AlertDialog(
            onDismissRequest = { showNewTaskDialog = false },
            title = { Text("New Task") },
            text = {
                Column {
                    OutlinedTextField(
                        value = newTaskText,
                        onValueChange = { newTaskText = it },
                        placeholder = { Text("Enter task details") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 100.dp)
                            .focusRequester(focusRequester),
                        singleLine = false,
                        maxLines = 5
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = (newTaskPriority == TaskPriority.High),
                                onClick = { newTaskPriority = TaskPriority.High }
                            )
                            Text("High", color = Color.Black)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = (newTaskPriority == TaskPriority.Normal),
                                onClick = { newTaskPriority = TaskPriority.Normal }
                            )
                            Text("Normal", color = Color.Black)
                        }
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RadioButton(
                                selected = (newTaskPriority == TaskPriority.Low),
                                onClick = { newTaskPriority = TaskPriority.Low }
                            )
                            Text("Low", color = Color.Black)
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        horizontalArrangement = Arrangement.End,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        TextButton(onClick = { newTaskText = TextFieldValue("") }) {
                            Text("Clear")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    reviewer.addNewTask(newTaskText.text, newTaskPriority)
                    onRefresh()
                    showNewTaskDialog = false
                    newTaskText = TextFieldValue("")
                    newTaskPriority = TaskPriority.Normal
                }) {
                    Text("Add")
                }
            },
            dismissButton = {
                TextButton(onClick = { showNewTaskDialog = false }) {
                    Text("Cancel")
                }
            }
        )
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
    DynamicButton("7h", ButtonType.SNOOZE, 420),
    DynamicButton("Mon", ButtonType.SNOOZE),
    DynamicButton("Fri", ButtonType.SNOOZE),
    DynamicButton("1p", ButtonType.SNOOZE),
    DynamicButton("4p", ButtonType.SNOOZE),
    DynamicButton("7p", ButtonType.SNOOZE),
    // New day snoozes:
    DynamicButton("1d", ButtonType.SNOOZE),
    DynamicButton("2d", ButtonType.SNOOZE),
    DynamicButton("3d", ButtonType.SNOOZE),
    DynamicButton("4d", ButtonType.SNOOZE), // 4d added
    DynamicButton("5d", ButtonType.SNOOZE),
    DynamicButton("6d", ButtonType.SNOOZE), // 6d added
    // Week snoozes:
    DynamicButton("1w", ButtonType.SNOOZE),
    DynamicButton("2w", ButtonType.SNOOZE),
    DynamicButton("3w", ButtonType.SNOOZE),
    DynamicButton("5w", ButtonType.SNOOZE),
    DynamicButton("8w", ButtonType.SNOOZE),  // <-- new button for 8 weeks
    DynamicButton("12w", ButtonType.SNOOZE), // <-- new button for 12 weeks
    DynamicButton("26w", ButtonType.SNOOZE),
    // Priority functions:
    DynamicButton("High Priority", ButtonType.OTHER),
    DynamicButton("Remove Priority", ButtonType.OTHER),
    // This one will always go in overflow:
    DynamicButton("Remove All Priorities", ButtonType.OTHER),
    // Other functions:
    DynamicButton("Delete", ButtonType.OTHER),
    DynamicButton("Add New Task Shortcut", ButtonType.OTHER),
    DynamicButton("Report", ButtonType.OTHER)
)

fun dynamicButtonSortKey(button: DynamicButton): Triple<Int, Int, String> {
    val id = button.id.trim()
    // Group 1: Minutes (e.g. "10m", "30m")
    if (Regex("^\\d+m$", RegexOption.IGNORE_CASE).matches(id)) {
        val num = id.dropLast(1).toIntOrNull() ?: Int.MAX_VALUE
        return Triple(1, num, id)
    }
    // Group 2: Hours (e.g. "1h", "2h")
    if (Regex("^\\d+h$", RegexOption.IGNORE_CASE).matches(id)) {
        val num = id.dropLast(1).toIntOrNull() ?: Int.MAX_VALUE
        return Triple(2, num, id)
    }
    // Group 3: Specific times (e.g. "1p", "4p", "7p")
    if (Regex("^\\d+p$", RegexOption.IGNORE_CASE).matches(id)) {
        val num = id.dropLast(1).toIntOrNull() ?: Int.MAX_VALUE
        return Triple(3, num, id)
    }
    // Group 4: Specific days (e.g. "Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")
    val dayMap = mapOf("Mon" to 1, "Tue" to 2, "Wed" to 3, "Thu" to 4, "Fri" to 5, "Sat" to 6, "Sun" to 7)
    if (dayMap.containsKey(id)) {
        return Triple(4, dayMap[id] ?: Int.MAX_VALUE, id)
    }
    // Group 5: Days (e.g. "1d", "2d", "3d", "4d", etc. or "1 Days")
    if (Regex("^\\d+(?:\\s*Days|d)$", RegexOption.IGNORE_CASE).matches(id)) {
        val num = Regex("\\d+").find(id)?.value?.toIntOrNull() ?: Int.MAX_VALUE
        return Triple(5, num, id)
    }
    // Group 6: Weeks (e.g. "3w", "5w", or "3 Weeks")
    if (Regex("^\\d+w$", RegexOption.IGNORE_CASE).matches(id) ||
        Regex("^\\d+\\s*Weeks$", RegexOption.IGNORE_CASE).matches(id)
    ) {
        val num = Regex("\\d+").find(id)?.value?.toIntOrNull() ?: Int.MAX_VALUE
        return Triple(6, num, id)
    }
    // Group 7: All other buttons / functions
    return Triple(7, 0, id)
}

fun backgroundColorForButton(button: DynamicButton): Color {
    // For "days" snooze buttons, include "1d", "2d", "3d", "4d", "5d", and "6d"
    if (button.id in listOf("1d", "2d", "3d", "4d", "5d", "6d")) {
        return Color(0xFF1B5E20) // Dark green for day snoozes
    }
    // Minutes group
    if (button.id.matches(Regex("^\\d+m$", RegexOption.IGNORE_CASE))) {
        return Color(0xFF1A237E)
    }
    // Hours group
    if (button.id.matches(Regex("^\\d+h$", RegexOption.IGNORE_CASE))) {
        return Color(0xFFB71C1C)
    }
    // Specific times group (e.g. "1p", "4p", "7p")
    if (button.id.matches(Regex("^\\d+p$", RegexOption.IGNORE_CASE))) {
        return Color(0xFFF57C00)
    }
    // Weeks group
    if (button.id.matches(Regex("^\\d+w$", RegexOption.IGNORE_CASE)) ||
        button.id.matches(Regex("^\\d+\\s*Weeks$", RegexOption.IGNORE_CASE))
    ) {
        return Color(0xFF8E24AA)
    }
    // Specific days group (e.g. "Mon", "Tue", etc.)
    if (button.id in listOf("Mon", "Tue", "Wed", "Thu", "Fri", "Sat", "Sun")) {
        return Color(0xFF1976D2) // Blue for day-of-week snoozes
    }
    // Default: for functions and any other category
    return Color(0xFF424242)
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

        // Perform the snooze update using the snoozeMinutes function from MarkdownReviewer.
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

fun nextDayOfWeek(targetDay: Int): Int {
    val calendar = Calendar.getInstance()
    val today = calendar.get(Calendar.DAY_OF_WEEK)
    var diff = targetDay - today
    if (diff <= 0) {
        diff += 7
    }
    return diff
}

// Helper function to compute high-contrast text color based on background luminance.
fun highContrastTextColor(background: Color): Color {
    return if (background.luminance() > 0.5f) Color.Black else Color.White
}
