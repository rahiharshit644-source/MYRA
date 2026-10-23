package com.soltini.app.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.core.content.FileProvider
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.soltini.app.memory2.MemoryItem
import com.soltini.app.memory2.MemoryType
import com.soltini.app.security.MemoryBackupManager
import com.soltini.app.settings.AppSettings
import com.soltini.app.ui.components.*
import com.soltini.app.ui.theme.*

@Composable
fun MemorySection(
    memory2Engine: com.soltini.app.memory2.Memory2Engine? = null,
    appSettings: AppSettings? = null
) {
    val context = LocalContext.current
    var expanded by remember { mutableStateOf(true) }
    var showClearConfirm by remember { mutableStateOf(false) }
    var backupStatusMessage by remember { mutableStateOf<String?>(null) }
    var backupPassphrase by remember { mutableStateOf("") }
    var restorePendingUri by remember { mutableStateOf<Uri?>(null) }
    var showRestorePasswordDialog by remember { mutableStateOf(false) }
    var restorePasswordInput by remember { mutableStateOf("") }

    var unifiedMemories by remember { mutableStateOf<List<MemoryItem>>(emptyList()) }
    var sessionLogEntries by remember { mutableStateOf(memory2Engine?.db?.getRecentSessionLogEntries(50) ?: emptyList()) }

    var searchQuery by remember { mutableStateOf("") }
    var selectedTypeFilter by remember { mutableStateOf<MemoryType?>(null) }
    var editingItem by remember { mutableStateOf<MemoryItem?>(null) }
    var editTextValue by remember { mutableStateOf("") }
    var editTypeValue by remember { mutableStateOf(MemoryType.FACT) }
    var showAddDialog by remember { mutableStateOf(false) }
    var addTextValue by remember { mutableStateOf("") }
    var addTypeValue by remember { mutableStateOf(MemoryType.FACT) }

    fun refresh() {
        unifiedMemories = if (searchQuery.isNotBlank()) {
            memory2Engine?.searchMemoriesUnified(searchQuery) ?: emptyList()
        } else {
            memory2Engine?.getAllMemoriesUnified() ?: emptyList()
        }
        sessionLogEntries = memory2Engine?.db?.getRecentSessionLogEntries(50) ?: emptyList()
    }

    DisposableEffect(Unit) {
        refresh()
        onDispose { }
    }

    fun executeRestore(uri: Uri, pass: String?) {
        val m2Db = memory2Engine?.db
        if (m2Db != null) {
            try {
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    val stats = MemoryBackupManager.importFromStream(inputStream, m2Db, pass)
                    if (stats.isSuccess) {
                        backupStatusMessage = "Restored ${stats.totalCount} records (Facts: ${stats.longTermCount}, Notes: ${stats.knowledgeCount}, Legacy: ${stats.legacyMigratedCount})"
                        refresh()
                    } else {
                        backupStatusMessage = "Restore failed: ${stats.errorMessage}"
                    }
                }
            } catch (e: Exception) {
                backupStatusMessage = "Restore error: ${e.message}"
            }
        } else {
            backupStatusMessage = "Memory engine not initialized."
        }
    }

    val restoreLauncher = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            restorePendingUri = uri
            restorePasswordInput = ""
            showRestorePasswordDialog = true
        }
    }

    SectionLabel("Memory Engine & Knowledge")
    Spacer(Modifier.height(12.dp))

    // Unified Memory Engine stats card
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 18.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PastelIconBadge(
                icon = Icons.Default.Storage,
                badgeColor = PastelPurpleBadge,
                iconTint = PurpleAccentStart,
                size = 44.dp
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Memory Engine", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text("Facts + conversation history, deduplicated automatically", color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp)
            }
        }

        Spacer(Modifier.height(14.dp))

        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PastelPurpleBadge.copy(alpha = 0.65f))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${unifiedMemories.size}", color = PurpleAccentStart, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Saved Facts", color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp)
                }
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(PastelMintBadge.copy(alpha = 0.65f))
                    .padding(12.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text("${sessionLogEntries.size}", color = MintAccentDark, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Text("Past Conversations", color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp)
                }
            }
        }

        if (sessionLogEntries.isNotEmpty()) {
            Spacer(Modifier.height(14.dp))
            Text("Recently discussed:", color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
            Spacer(Modifier.height(6.dp))
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                sessionLogEntries.take(5).forEach { entry ->
                    Text(
                        "• ${entry.topic}",
                        color = LocalMyraTokens.current.textPrimary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }
    }

    Spacer(Modifier.height(14.dp))

    // Stored Memories Card & Interactive Memory Manager
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 18.dp
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                PastelIconBadge(
                    icon = Icons.Default.Storage,
                    badgeColor = PastelPinkBadge,
                    iconTint = AccentPurple,
                    size = 40.dp
                )
                Spacer(Modifier.width(10.dp))
                Column {
                    Text("Memory Manager", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    Text("${unifiedMemories.size} items stored", color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp)
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                GlassGradientButton(
                    text = "Add",
                    icon = Icons.Default.Add,
                    onClick = {
                        addTextValue = ""
                        addTypeValue = MemoryType.FACT
                        showAddDialog = true
                    }
                )

                IconButton(
                    onClick = {
                        expanded = !expanded
                        if (expanded) refresh()
                    },
                    modifier = Modifier.size(32.dp)
                ) {
                    Icon(
                        if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle memory list",
                        tint = TextMidGray
                    )
                }
            }
        }

        AnimatedVisibility(
            visible = expanded,
            enter = expandVertically(),
            exit = shrinkVertically()
        ) {
            Column(modifier = Modifier.padding(top = 14.dp)) {
                // Search Bar
                GlassPillInput(
                    value = searchQuery,
                    onValueChange = {
                        searchQuery = it
                        unifiedMemories = if (it.isNotBlank()) {
                            memory2Engine?.searchMemoriesUnified(it) ?: emptyList()
                        } else {
                            memory2Engine?.getAllMemoriesUnified() ?: emptyList()
                        }
                    },
                    placeholder = "Search memories, facts, preferences...",
                    leadingIcon = Icons.Default.Search,
                    trailingContent = {
                        if (searchQuery.isNotBlank()) {
                            IconButton(
                                onClick = {
                                    searchQuery = ""
                                    refresh()
                                },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Default.Close, contentDescription = "Clear", tint = TextMidGray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                )

                Spacer(Modifier.height(10.dp))

                val filterTypes = listOf(
                    null to "All",
                    MemoryType.PREFERENCE to "Preferences",
                    MemoryType.CORRECTION to "Corrections",
                    MemoryType.USER_PROFILE to "Profile",
                    MemoryType.FACT to "Facts",
                    MemoryType.PROJECT to "Projects",
                    MemoryType.ROUTINE to "Routines"
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    filterTypes.forEach { (type, label) ->
                        GlassSelectablePill(
                            label = label,
                            selected = selectedTypeFilter == type,
                            onClick = { selectedTypeFilter = type }
                        )
                    }
                }

                Spacer(Modifier.height(12.dp))

                val displayItems = unifiedMemories.filter {
                    selectedTypeFilter == null || it.type == selectedTypeFilter
                }

                if (displayItems.isEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 18.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            if (searchQuery.isNotBlank()) "No memories matching \"$searchQuery\""
                            else "No memories recorded yet in this category.",
                            color = LocalMyraTokens.current.textSecondary,
                            fontSize = 12.sp,
                            textAlign = TextAlign.Center
                        )
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        displayItems.take(50).forEach { item ->
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                color = Color(0xFFF6F3FC),
                                shape = RoundedCornerShape(16.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(12.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            GlassStatusBadge(text = item.type.name, style = BadgeStyle.PURPLE)

                                            if (item.sensitivityLevel.equals("SENSITIVE", ignoreCase = true)) {
                                                GlassStatusBadge(text = "Sensitive", style = BadgeStyle.ERROR)
                                            }

                                            if (item.userConfirmed) {
                                                GlassStatusBadge(text = "✓ Verified", style = BadgeStyle.ACTIVE)
                                            }
                                        }

                                        Spacer(Modifier.height(6.dp))
                                        Text(
                                            item.text,
                                            color = LocalMyraTokens.current.textPrimary,
                                            fontSize = 13.sp,
                                            lineHeight = 18.sp
                                        )
                                    }

                                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                        IconButton(
                                            onClick = {
                                                editingItem = item
                                                editTextValue = item.text
                                                editTypeValue = item.type
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Edit, contentDescription = "Edit Memory", tint = PurpleAccentStart, modifier = Modifier.size(16.dp))
                                        }

                                        IconButton(
                                            onClick = {
                                                memory2Engine?.deleteMemory(item.id)
                                                refresh()
                                            },
                                            modifier = Modifier.size(32.dp)
                                        ) {
                                            Icon(Icons.Default.Delete, contentDescription = "Delete Memory", tint = RedError, modifier = Modifier.size(16.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))

                if (displayItems.isNotEmpty()) {
                    if (showClearConfirm) {
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            GlassSecondaryButton(
                                text = "Yes, clear all",
                                onClick = {
                                    memory2Engine?.clearAllMemories()
                                    showClearConfirm = false
                                    refresh()
                                },
                                modifier = Modifier.weight(1f),
                                containerColor = Color(0xFFFFE5EA),
                                contentColor = RedError
                            )
                            GlassSecondaryButton(
                                text = "Cancel",
                                onClick = { showClearConfirm = false },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    } else {
                        GlassSecondaryButton(
                            text = "Clear All Memories",
                            icon = Icons.Default.Delete,
                            onClick = { showClearConfirm = true },
                            modifier = Modifier.fillMaxWidth(),
                            containerColor = Color(0xFFFFE5EA),
                            contentColor = RedError
                        )
                    }
                }
            }
        }
    }

    // Add Memory Dialog
    if (showAddDialog) {
        AlertDialog(
            onDismissRequest = { showAddDialog = false },
            containerColor = GlassCardWhiteSolid,
            shape = RoundedCornerShape(24.dp),
            confirmButton = {
                GlassGradientButton(
                    text = "Save Memory",
                    onClick = {
                        if (addTextValue.isNotBlank()) {
                            memory2Engine?.saveExplicitMemory(addTextValue.trim(), addTypeValue, confirmed = true)
                            refresh()
                        }
                        showAddDialog = false
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { showAddDialog = false }) {
                    Text("Cancel", color = LocalMyraTokens.current.textSecondary)
                }
            },
            title = { Text("Add Memory or Rule", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Record a personal fact, preference, user rule, or project detail for MYRA:", color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = addTextValue,
                        onValueChange = { addTextValue = it },
                        placeholder = { Text("e.g. User prefers concise answers", color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp) },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(16.dp)
                    )

                    Text("Memory Category:", color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(MemoryType.PREFERENCE, MemoryType.FACT, MemoryType.CORRECTION, MemoryType.USER_PROFILE, MemoryType.PROJECT).forEach { t ->
                            GlassSelectablePill(
                                label = t.name,
                                selected = addTypeValue == t,
                                onClick = { addTypeValue = t }
                            )
                        }
                    }
                }
            }
        )
    }

    // Edit Memory Dialog
    if (editingItem != null) {
        val item = editingItem!!
        AlertDialog(
            onDismissRequest = { editingItem = null },
            containerColor = GlassCardWhiteSolid,
            shape = RoundedCornerShape(24.dp),
            confirmButton = {
                GlassGradientButton(
                    text = "Update",
                    onClick = {
                        if (editTextValue.isNotBlank()) {
                            memory2Engine?.updateMemory(item.id, newContent = editTextValue.trim(), newType = editTypeValue)
                            refresh()
                        }
                        editingItem = null
                    }
                )
            },
            dismissButton = {
                TextButton(onClick = { editingItem = null }) {
                    Text("Cancel", color = LocalMyraTokens.current.textSecondary)
                }
            },
            title = { Text("Edit Memory", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Modify this stored memory or rule:", color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp)
                    OutlinedTextField(
                        value = editTextValue,
                        onValueChange = { editTextValue = it },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3,
                        shape = RoundedCornerShape(16.dp)
                    )

                    Text("Memory Category:", color = LocalMyraTokens.current.textSecondary, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(MemoryType.PREFERENCE, MemoryType.FACT, MemoryType.CORRECTION, MemoryType.USER_PROFILE, MemoryType.PROJECT).forEach { t ->
                            GlassSelectablePill(
                                label = t.name,
                                selected = editTypeValue == t,
                                onClick = { editTypeValue = t }
                            )
                        }
                    }
                }
            }
        )
    }

    Spacer(Modifier.height(14.dp))

    // Encrypted Memory Backup & Restore Card
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 24.dp,
        contentPadding = 18.dp
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            PastelIconBadge(
                icon = Icons.Default.Security,
                badgeColor = PastelMintBadge,
                iconTint = MintAccentDark,
                size = 42.dp
            )
            Spacer(Modifier.width(12.dp))
            Column {
                Text("Encrypted Memory Backup", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                Text("AES-256 Android Keystore Protected Backup", color = LocalMyraTokens.current.textSecondary, fontSize = 11.sp)
            }
        }

        Spacer(Modifier.height(10.dp))
        Text(
            "Export persistent memories (facts, personal context, knowledge, and experience) as an encrypted backup to restore across devices or sync with Google Drive.",
            color = LocalMyraTokens.current.textSecondary,
            fontSize = 12.sp,
            lineHeight = 17.sp
        )

        Spacer(Modifier.height(12.dp))
        GlassPillInput(
            value = backupPassphrase,
            onValueChange = { backupPassphrase = it },
            placeholder = "Optional passphrase for PBKDF2 AES-256",
            leadingIcon = Icons.Default.Key
        )

        val isKeystoreAvailable = remember { MemoryBackupManager.isKeyStoreAvailable() }
        if (backupPassphrase.isBlank() && !isKeystoreAvailable) {
            Spacer(Modifier.height(8.dp))
            Surface(
                color = Color(0xFFFFE5EA),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Warning, contentDescription = null, tint = RedError, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Warning: Hardware KeyStore unavailable on this device. Add a passphrase above for strong encryption.",
                        color = RedError,
                        fontSize = 11.sp,
                        lineHeight = 15.sp
                    )
                }
            }
        } else if (backupPassphrase.isNotBlank()) {
            Spacer(Modifier.height(6.dp))
            Text(
                "🛡️ Strong PBKDF2 (100,000 rounds) AES-256 encryption active.",
                color = MintAccentDark,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium
            )
        }

        if (backupStatusMessage != null) {
            Spacer(Modifier.height(10.dp))
            Surface(
                color = PastelPurpleBadge,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    backupStatusMessage!!,
                    color = PurpleAccentStart,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.padding(12.dp)
                )
            }
        }

        Spacer(Modifier.height(14.dp))
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassGradientButton(
                text = "Backup Memory",
                icon = Icons.Default.Storage,
                modifier = Modifier.weight(1f),
                onClick = {
                    val m2Db = memory2Engine?.db
                    if (m2Db != null) {
                        val passToUse = backupPassphrase.ifBlank { null }
                        val res = MemoryBackupManager.exportEncryptedBackup(context, m2Db, passToUse)
                        if (res.isSuccess) {
                            val file = res.getOrNull()!!
                            backupStatusMessage = "Backup saved: ${file.name} (${file.length()} bytes)"
                            try {
                                val uri = FileProvider.getUriForFile(
                                    context,
                                    "com.soltini.app.fileprovider",
                                    file
                                )
                                val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                    type = "application/octet-stream"
                                    putExtra(Intent.EXTRA_STREAM, uri)
                                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                }
                                context.startActivity(Intent.createChooser(shareIntent, "Save Backup to Google Drive / Files"))
                            } catch (_: Exception) {}
                        } else {
                            backupStatusMessage = "Backup failed: ${res.exceptionOrNull()?.message}"
                        }
                    } else {
                        backupStatusMessage = "Memory database not initialized."
                    }
                }
            )

            GlassSecondaryButton(
                text = "Restore",
                icon = Icons.Default.FolderOpen,
                modifier = Modifier.weight(1f),
                onClick = { restoreLauncher.launch("*/*") }
            )
        }

        if (showRestorePasswordDialog && restorePendingUri != null) {
            AlertDialog(
                onDismissRequest = {
                    showRestorePasswordDialog = false
                    restorePendingUri = null
                },
                containerColor = GlassCardWhiteSolid,
                shape = RoundedCornerShape(24.dp),
                title = { Text("Restore Memory Backup", color = LocalMyraTokens.current.textPrimary, fontWeight = FontWeight.Bold) },
                text = {
                    Column {
                        Text(
                            "Agar ye backup kisi password se encrypt kiya gaya tha, toh password daaliye. Agar bina password (KeyStore) backup tha, toh khali chhod kar Restore dabayein.",
                            color = LocalMyraTokens.current.textSecondary,
                            fontSize = 13.sp
                        )
                        Spacer(Modifier.height(12.dp))
                        OutlinedTextField(
                            value = restorePasswordInput,
                            onValueChange = { restorePasswordInput = it },
                            label = { Text("Backup Passphrase") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                },
                confirmButton = {
                    GlassGradientButton(
                        text = "Restore",
                        onClick = {
                            val uri = restorePendingUri
                            val pass = restorePasswordInput.ifBlank { null }
                            showRestorePasswordDialog = false
                            restorePendingUri = null
                            if (uri != null) {
                                executeRestore(uri, pass)
                            }
                        }
                    )
                },
                dismissButton = {
                    TextButton(onClick = {
                        showRestorePasswordDialog = false
                        restorePendingUri = null
                    }) {
                        Text("Cancel", color = LocalMyraTokens.current.textSecondary)
                    }
                }
            )
        }
    }
}
