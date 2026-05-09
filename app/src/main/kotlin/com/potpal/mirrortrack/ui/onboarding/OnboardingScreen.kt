package com.potpal.mirrortrack.ui.onboarding

import android.content.Intent
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel

private val ClickablePurple = Color(0xFFD2A8FF)
private val ClickablePurpleOpen = Color(0xFF8E5BFF)

@Composable
fun OnboardingScreen(
    onFinished: () -> Unit,
    startGroupId: String? = null,
    viewModel: OnboardingViewModel = hiltViewModel()
) {
    val groups = remember { OnboardingViewModel.GROUPS }

    val initialStep = remember(startGroupId) {
        if (startGroupId == null) -1
        else groups.indexOfFirst { it.id == startGroupId }.takeIf { it >= 0 } ?: -1
    }
    var step by rememberSaveable { mutableIntStateOf(initialStep) }

    var pendingGrantGroup by remember { mutableStateOf<PermissionGroup?>(null) }
    var pendingSpecialIndex by remember { mutableIntStateOf(0) }
    var nextSpecialIntent by remember { mutableStateOf<Intent?>(null) }

    fun gotoNext() {
        val next = step + 1
        step = if (next > groups.size) groups.size else next
    }

    val runtimeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { _ ->
        val group = pendingGrantGroup ?: return@rememberLauncherForActivityResult
        if (group.specialAccess.isEmpty()) {
            viewModel.markDecided(group, granted = true)
            pendingGrantGroup = null
            gotoNext()
        } else {
            pendingSpecialIndex = 0
            nextSpecialIntent = specialAccessIntent(group.specialAccess.first())
        }
    }

    val specialLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        val group = pendingGrantGroup ?: return@rememberLauncherForActivityResult
        val nextIndex = pendingSpecialIndex + 1
        if (nextIndex >= group.specialAccess.size) {
            viewModel.markDecided(group, granted = true)
            pendingGrantGroup = null
            gotoNext()
        } else {
            pendingSpecialIndex = nextIndex
            nextSpecialIntent = specialAccessIntent(group.specialAccess[nextIndex])
        }
    }

    LaunchedEffect(nextSpecialIntent) {
        val intent = nextSpecialIntent ?: return@LaunchedEffect
        nextSpecialIntent = null
        specialLauncher.launch(intent)
    }

    fun startGrant(group: PermissionGroup) {
        pendingGrantGroup = group
        pendingSpecialIndex = 0
        when {
            group.requiresAdb -> {
                // ADB perms cannot be granted in-app. The CTA simply marks the
                // step decided; markDecided will check each collector's
                // isAvailable() and only enable the ones whose perms the user
                // actually granted via adb.
                viewModel.markDecided(group, granted = true)
                pendingGrantGroup = null
                gotoNext()
            }
            group.runtimePermissions.isNotEmpty() ->
                runtimeLauncher.launch(group.runtimePermissions.toTypedArray())
            group.specialAccess.isNotEmpty() ->
                nextSpecialIntent = specialAccessIntent(group.specialAccess.first())
            else -> {
                viewModel.markDecided(group, granted = true)
                pendingGrantGroup = null
                gotoNext()
            }
        }
    }

    fun skip(group: PermissionGroup) {
        viewModel.markDecided(group, granted = false)
        gotoNext()
    }

    Scaffold { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 20.dp)
        ) {
            if (step in 0 until groups.size) {
                StepProgressBar(currentStep = step, totalSteps = groups.size)
            }

            when {
                step == -1 -> WelcomeStep(
                    alwaysOn = remember { viewModel.alwaysOnCollectorNames() },
                    onContinue = { step = 0 },
                    onSkipAll = {
                        viewModel.skipAll()
                        onFinished()
                    }
                )
                step >= groups.size -> FinishStep(
                    onDone = {
                        viewModel.markEntrySeen()
                        onFinished()
                    }
                )
                else -> GroupStep(
                    group = groups[step],
                    onGrant = { startGrant(groups[step]) },
                    onSkip = { skip(groups[step]) },
                    onBack = {
                        step = if (step > 0) step - 1 else -1
                    }
                )
            }
        }
    }
}

private fun specialAccessIntent(kind: SpecialAccessKind): Intent {
    val action = when (kind) {
        SpecialAccessKind.NOTIFICATION_LISTENER -> Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS
        SpecialAccessKind.USAGE_ACCESS -> Settings.ACTION_USAGE_ACCESS_SETTINGS
    }
    return Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
}

@Composable
private fun StepProgressBar(currentStep: Int, totalSteps: Int) {
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 8.dp)) {
        LinearProgressIndicator(
            progress = { (currentStep + 1f) / totalSteps.toFloat() },
            modifier = Modifier.fillMaxWidth(),
            color = MaterialTheme.colorScheme.primary,
            trackColor = MaterialTheme.colorScheme.surfaceVariant
        )
        Spacer(Modifier.height(4.dp))
        Text(
            "Step ${currentStep + 1} of $totalSteps",
            fontSize = 11.sp,
            fontFamily = FontFamily.Monospace,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

// ── Reusable info bubble + clickable item ───────────────────────────

@Composable
private fun InfoBubble(
    title: String,
    body: String,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Text(
                body,
                style = MaterialTheme.typography.bodyMedium
            )
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Got it") }
        }
    )
}

/**
 * A bullet-row that opens an info bubble describing [infoKey].
 * Shows light purple normally, darker purple while its bubble is open,
 * and reverts when the bubble closes. If no description exists for the
 * key, falls back to a non-clickable plain row.
 */
@Composable
private fun ClickableInfoItem(
    label: String,
    infoKey: String = label,
    monospace: Boolean = false,
    indent: Boolean = true
) {
    val description = remember(infoKey) { OnboardingContent.descriptionFor(infoKey) }
    if (description == null) {
        Text(
            (if (indent) "• " else "") + label,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.padding(vertical = 2.dp)
        )
        return
    }
    var open by remember { mutableStateOf(false) }
    val color = if (open) ClickablePurpleOpen else ClickablePurple
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = true }
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            (if (indent) "• " else "") + label,
            style = MaterialTheme.typography.bodySmall,
            fontFamily = if (monospace) FontFamily.Monospace else null,
            color = color,
            modifier = Modifier.weight(1f)
        )
        Icon(
            Icons.Default.Info,
            contentDescription = "More info",
            tint = color.copy(alpha = 0.7f),
            modifier = Modifier.size(14.dp)
        )
    }
    if (open) {
        InfoBubble(
            title = description.first,
            body = description.second,
            onDismiss = { open = false }
        )
    }
}

// ── Welcome step ─────────────────────────────────────────────────────

@Composable
private fun WelcomeStep(
    alwaysOn: List<String>,
    onContinue: () -> Unit,
    onSkipAll: () -> Unit
) {
    var expanded by rememberSaveable { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 40.dp)
    ) {
        Text(
            "Your phone is already telling on you.",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "MirrorTrack collects the same kinds of signals that ad and analytics SDKs use — but only on this device, only for you to inspect, and only with permissions you grant one at a time.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.LockOpen,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "Available without any permission",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    "These run as soon as the app opens. Build details, hardware identifiers, screen activity, battery state — the kind of fingerprint your phone leaks before any prompt.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(8.dp))
                val visible = if (expanded) alwaysOn else alwaysOn.take(8)
                visible.forEach {
                    Text(
                        "• $it",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
                if (alwaysOn.size > 8) {
                    Spacer(Modifier.height(4.dp))
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { expanded = !expanded }
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            if (expanded) "Show fewer" else "… and ${alwaysOn.size - 8} more",
                            style = MaterialTheme.typography.bodySmall,
                            color = ClickablePurple
                        )
                        Spacer(Modifier.weight(1f))
                        Icon(
                            if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = ClickablePurple,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            "The next few screens ask one permission at a time. Each shows what the permission unlocks — and what it does not see. You can skip any of them.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(32.dp))
        Button(
            onClick = onContinue,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Walk me through it")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onSkipAll,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Skip — I'll set things up later")
        }
        Spacer(Modifier.height(24.dp))
    }
}

// ── Group step ───────────────────────────────────────────────────────

@Composable
private fun GroupStep(
    group: PermissionGroup,
    onGrant: () -> Unit,
    onSkip: () -> Unit,
    onBack: () -> Unit
) {
    var showDetails by rememberSaveable(group.id) { mutableStateOf(false) }
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = 24.dp)
    ) {
        Text(
            group.title,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(8.dp))
        Text(
            group.hook,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        Spacer(Modifier.height(24.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.08f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.CheckCircle,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "What this turns on",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
                Spacer(Modifier.height(8.dp))
                val cards = if (group.primaryUnlocks.isNotEmpty()) {
                    group.primaryUnlocks
                } else {
                    group.derivedCardNames()
                }
                cards.forEach { name ->
                    ClickableInfoItem(
                        label = name,
                        infoKey = name
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "What this does not see",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    group.notSeen,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        // Tracker comparison: how industry SDKs use these permissions and
        // how MirrorTrack differs.
        Card(
            shape = RoundedCornerShape(12.dp),
            colors = CardDefaults.cardColors(
                containerColor = Color(0xFFF85149).copy(alpha = 0.08f)
            )
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Default.Public,
                        contentDescription = null,
                        tint = Color(0xFFF85149),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        "How trackers use this",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        color = Color(0xFFF85149)
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    group.trackerComparison,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (group.requiresAdb) {
            Spacer(Modifier.height(12.dp))
            AdbInstructionsCard(group)
        }

        Spacer(Modifier.height(8.dp))
        TextButton(onClick = { showDetails = !showDetails }) {
            Text(if (showDetails) "Hide details" else "Tell me more")
        }
        if (showDetails) {
            Column(modifier = Modifier.padding(start = 8.dp, end = 8.dp, bottom = 4.dp)) {
                if (group.runtimePermissions.isNotEmpty()) {
                    Text(
                        "Android permissions:",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    group.runtimePermissions.forEach { perm ->
                        ClickableInfoItem(
                            label = perm.substringAfterLast('.'),
                            infoKey = perm,
                            monospace = true
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (group.specialAccess.isNotEmpty()) {
                    Text(
                        "Special access (opens system settings):",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    group.specialAccess.forEach { kind ->
                        val label = when (kind) {
                            SpecialAccessKind.NOTIFICATION_LISTENER -> "Notification listener access"
                            SpecialAccessKind.USAGE_ACCESS -> "Usage access"
                        }
                        val infoKey = when (kind) {
                            SpecialAccessKind.NOTIFICATION_LISTENER -> "BIND_NOTIFICATION_LISTENER_SERVICE"
                            SpecialAccessKind.USAGE_ACCESS -> "PACKAGE_USAGE_STATS"
                        }
                        ClickableInfoItem(
                            label = label,
                            infoKey = infoKey
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Text(
                    "Collectors enabled when granted:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
                group.collectorIds.forEach { id ->
                    ClickableInfoItem(
                        label = id,
                        infoKey = id,
                        monospace = true
                    )
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Button(
            onClick = onGrant,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                when {
                    group.requiresAdb -> "Done — I've run the commands"
                    group.specialAccess.isNotEmpty() && group.runtimePermissions.isEmpty() ->
                        "Open system settings"
                    else -> "Grant"
                }
            )
        }
        Spacer(Modifier.height(4.dp))
        OutlinedButton(
            onClick = onSkip,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(if (group.requiresAdb) "Not interested in ADB-tier" else "Skip this one")
        }
        Spacer(Modifier.height(8.dp))
        TextButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Back")
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun AdbInstructionsCard(group: PermissionGroup) {
    val clipboard = LocalClipboardManager.current
    var copied by remember { mutableStateOf(false) }
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = ClickablePurple.copy(alpha = 0.10f)
        )
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Default.Terminal,
                    contentDescription = null,
                    tint = ClickablePurple,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "How to enable",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = ClickablePurple
                )
            }
            Spacer(Modifier.height(6.dp))
            Text(
                OnboardingContent.adbInstructions,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(Modifier.height(12.dp))
            // Command block — monospace, scrollable horizontally if needed.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF0D1117))
                    .padding(12.dp)
            ) {
                Text(
                    group.adbCommands.orEmpty(),
                    style = MaterialTheme.typography.bodySmall,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    color = Color(0xFF7EE787)
                )
            }
            Spacer(Modifier.height(8.dp))
            TextButton(
                onClick = {
                    clipboard.setText(AnnotatedString(group.adbCommands.orEmpty()))
                    copied = true
                }
            ) {
                Icon(
                    Icons.Default.ContentCopy,
                    contentDescription = null,
                    modifier = Modifier.size(14.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(if (copied) "Copied" else "Copy commands")
            }
            Spacer(Modifier.height(4.dp))
            Text(
                OnboardingContent.adbConsequences,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun FinishStep(onDone: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 64.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Box(
            modifier = Modifier
                .size(72.dp)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Default.CheckCircle,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
        }
        Spacer(Modifier.height(24.dp))
        Text(
            "All set",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(12.dp))
        Text(
            "Cards will fill in as data accumulates. Some need only seconds, some take a day or two.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(8.dp))
        Text(
            "You can revisit any skipped step from Settings.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(40.dp))
        Button(
            onClick = onDone,
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp)
        ) {
            Text("Show my insights")
        }
    }
}
