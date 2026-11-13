package com.vknxworks.datectrl.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SheetState
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.material3.ripple
import androidx.compose.runtime.remember
import com.vknxworks.datectrl.ui.theme.CtrlColors
import com.vknxworks.datectrl.ui.theme.CtrlShapes
import com.vknxworks.datectrl.ui.theme.CtrlType

/*
 * Ctrl / Signal building blocks, shared in look with every Ctrl app. Screens use these instead of
 * styling Material components one by one, so the whole app changes from here.
 */

/**
 * Space taken at the bottom of the screen by the floating bottom bar. Scrolling screens add it to their
 * bottom content padding, so their last items can scroll clear of the bar.
 */
val LocalBottomClearance = compositionLocalOf { 0.dp }

// ---------- Text ----------

/** "SUM/CTRL": the app's wordmark, the slash in the accent. */
@Composable
fun CtrlWordmark(name: String, modifier: Modifier = Modifier) {
    Text(
        buildAnnotatedString {
            append(name.uppercase())
            withStyle(SpanStyle(color = CtrlColors.AccentText)) { append("/") }
            append("CTRL")
        },
        style = CtrlType.Wordmark,
        color = CtrlColors.Text,
        modifier = modifier,
    )
}

/** A mono section label: UPPERCASE, spaced out, muted. */
@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, color: Color = CtrlColors.Muted) {
    Text(text.uppercase(), style = CtrlType.Label, color = color, modifier = modifier, maxLines = 2, overflow = TextOverflow.Ellipsis)
}

/** An 8dp status square: accent when active, grey when not. */
@Composable
fun StatusDot(active: Boolean, modifier: Modifier = Modifier, color: Color = if (active) CtrlColors.Accent else CtrlColors.Inactive) {
    Box(modifier.size(8.dp).clip(CtrlShapes.Dot).background(color))
}

/** Mono status text with its square, for the end of a list row. */
@Composable
fun StatusTag(text: String, active: Boolean, modifier: Modifier = Modifier, color: Color = if (active) CtrlColors.AccentText else CtrlColors.Inactive) {
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(text.uppercase(), style = CtrlType.Status, color = color, maxLines = 1)
        Spacer(Modifier.width(8.dp))
        StatusDot(active)
    }
}

// ---------- Surfaces ----------

/** A secondary card: surface fill, 1dp border, 22dp corners. */
@Composable
fun CtrlCard(
    modifier: Modifier = Modifier,
    onClick: (() -> Unit)? = null,
    padding: PaddingValues = PaddingValues(16.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(CtrlShapes.Card)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.Outline, CtrlShapes.Card)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(padding),
        content = content,
    )
}

/**
 * The hero card, the one most important thing on a main screen: accent fill and dark content, a mono
 * label row on top ([label] left, [meta] right) and big content below.
 */
@Composable
fun CtrlHeroCard(
    label: String,
    modifier: Modifier = Modifier,
    meta: String? = null,
    onClick: (() -> Unit)? = null,
    onLongClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    val haptics = LocalHapticFeedback.current
    val click = when {
        onClick != null && onLongClick != null -> Modifier.combinedClickable(
            onClick = onClick,
            onLongClick = {
                haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                onLongClick()
            },
        )
        onClick != null -> Modifier.clickable(onClick = onClick)
        else -> Modifier
    }
    Column(
        modifier
            .clip(CtrlShapes.Hero)
            .background(CtrlColors.Accent)
            .then(click)
            .padding(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(label.uppercase(), style = CtrlType.LabelStrong, color = CtrlColors.OnAccent, modifier = Modifier.weight(1f), maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (meta != null) Text(meta.uppercase(), style = CtrlType.LabelStrong, color = CtrlColors.OnAccent, maxLines = 1)
        }
        androidx.compose.runtime.CompositionLocalProvider(androidx.compose.material3.LocalContentColor provides CtrlColors.OnAccent) {
            content()
        }
    }
}

/** A thin line drawn along the top of whatever it's on, between list rows. */
@Composable
fun Modifier.topDivider(color: Color = CtrlColors.Divider): Modifier = drawBehind {
    drawRect(color, topLeft = Offset.Zero, size = Size(size.width, 1.dp.toPx()))
}

// ---------- Controls ----------

/** A 44dp square button (back, close, actions in the top bar). */
@Composable
fun CtrlSquareButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    size: Dp = 44.dp,
    tint: Color = CtrlColors.Text,
    enabled: Boolean = true,
) {
    Box(
        modifier
            .size(size)
            .clip(CtrlShapes.Control)
            .background(CtrlColors.Surface)
            .border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Control)
            .clickable(enabled = enabled, role = Role.Button, onClick = onClick)
            .semantics { if (contentDescription != null) this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = if (enabled) tint else tint.copy(alpha = 0.38f))
    }
}

/**
 * The 44dp profile picture: surface square with an icon (DateCtrl has no accounts), and a small accent
 * badge bottom-right while [connected] (here: the app lock is on).
 */
@Composable
fun CtrlAvatar(
    icon: ImageVector,
    contentDescription: String,
    connected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(modifier.size(44.dp)) {
        Box(
            Modifier
                .size(44.dp)
                .clip(CtrlShapes.Control)
                .background(CtrlColors.Surface)
                .border(1.dp, CtrlColors.ControlOutline, CtrlShapes.Control)
                .clickable(role = Role.Button, onClick = onClick)
                .semantics { this.contentDescription = contentDescription },
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(22.dp), tint = CtrlColors.Text) }
        if (connected) {
            val badge = RoundedCornerShape(4.dp)
            Box(
                Modifier
                    .align(Alignment.BottomEnd)
                    .offset(3.dp, 3.dp)
                    .size(10.dp)
                    .clip(badge)
                    .background(CtrlColors.Accent)
                    .border(2.dp, CtrlColors.Background, badge),
            )
        }
    }
}

/** The Ctrl switch: a 52x32dp rounded rectangle with a 24dp square knob. */
@Composable
fun CtrlSwitch(checked: Boolean, onCheckedChange: ((Boolean) -> Unit)?, modifier: Modifier = Modifier, enabled: Boolean = true) {
    val track by animateColorAsState(if (checked) CtrlColors.Accent else CtrlColors.SwitchTrackOff, label = "switch-track")
    val knob by animateColorAsState(if (checked) CtrlColors.OnAccent else CtrlColors.SwitchKnobOff, label = "switch-knob")
    val x by animateDpAsState(if (checked) 20.dp else 0.dp, label = "switch-x")
    val shape = RoundedCornerShape(10.dp)
    Box(
        modifier
            .size(width = 52.dp, height = 32.dp)
            .clip(shape)
            .background(if (enabled) track else track.copy(alpha = 0.4f))
            .then(
                if (onCheckedChange != null) {
                    Modifier.toggleable(value = checked, enabled = enabled, role = Role.Switch, onValueChange = onCheckedChange)
                } else {
                    Modifier
                },
            )
            .padding(4.dp),
    ) {
        Box(Modifier.offset(x = x).size(24.dp).clip(RoundedCornerShape(7.dp)).background(knob))
    }
}

/** The main action on a screen: accent fill, dark text, 56dp tall. */
@Composable
fun CtrlButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    icon: ImageVector? = null,
    enabled: Boolean = true,
    container: Color = CtrlColors.Accent,
    content: Color = CtrlColors.OnAccent,
) {
    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 56.dp),
        shape = CtrlShapes.Button,
        colors = ButtonDefaults.buttonColors(
            containerColor = container,
            contentColor = content,
            disabledContainerColor = CtrlColors.Raised,
            disabledContentColor = CtrlColors.Inactive,
        ),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 12.dp),
    ) {
        if (icon != null) {
            Icon(icon, null, Modifier.size(20.dp))
            Spacer(Modifier.width(10.dp))
        }
        Text(text, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
    }
}

/** A secondary button: surface fill and a 1dp border. */
@Composable
fun CtrlSecondaryButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    content: @Composable RowScope.() -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.heightIn(min = 48.dp),
        shape = CtrlShapes.Field,
        border = BorderStroke(1.dp, CtrlColors.ControlOutline),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = CtrlColors.Surface,
            contentColor = CtrlColors.Text,
            disabledContainerColor = CtrlColors.Surface,
            disabledContentColor = CtrlColors.Inactive,
        ),
        content = content,
    )
}

@Composable
fun ctrlFieldColors(): TextFieldColors = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = CtrlColors.Surface,
    unfocusedContainerColor = CtrlColors.Surface,
    disabledContainerColor = CtrlColors.Surface,
    errorContainerColor = CtrlColors.Surface,
    focusedBorderColor = CtrlColors.AccentText,
    unfocusedBorderColor = CtrlColors.ControlOutline,
    cursorColor = CtrlColors.AccentText,
    focusedLabelColor = CtrlColors.AccentText,
    unfocusedLabelColor = CtrlColors.Muted,
    focusedLeadingIconColor = CtrlColors.Text,
    unfocusedLeadingIconColor = CtrlColors.Muted,
    focusedTrailingIconColor = CtrlColors.Muted,
    unfocusedTrailingIconColor = CtrlColors.Muted,
    focusedPlaceholderColor = CtrlColors.Muted,
    unfocusedPlaceholderColor = CtrlColors.Muted,
)

/** Text field: surface fill, 1dp border, 16dp corners; accent border and cursor when focused. */
@Composable
fun CtrlTextField(
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    label: (@Composable () -> Unit)? = null,
    placeholder: (@Composable () -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    singleLine: Boolean = false,
    minLines: Int = 1,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier,
        label = label,
        placeholder = placeholder,
        leadingIcon = leadingIcon,
        trailingIcon = trailingIcon,
        singleLine = singleLine,
        minLines = minLines,
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
        textStyle = MaterialTheme.typography.bodyLarge,
        shape = CtrlShapes.Field,
        colors = ctrlFieldColors(),
    )
}

/** Progress bar: 6dp tall, 3dp corners. Light fill, or the accent for the main figure. */
@Composable
fun CtrlProgressBar(progress: Float, modifier: Modifier = Modifier, color: Color = CtrlColors.ProgressFill) {
    val shape = RoundedCornerShape(3.dp)
    Box(modifier.fillMaxWidth().height(6.dp).clip(shape).background(CtrlColors.ProgressTrack)) {
        Box(Modifier.fillMaxHeight().fillMaxWidth(progress.coerceIn(0f, 1f)).clip(shape).background(color))
    }
}

// ---------- Dialogs and sheets ----------

/** Dialog on the surface colour, 26dp corners and a 1dp border. */
@Composable
fun CtrlAlertDialog(
    onDismissRequest: () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null,
    title: (@Composable () -> Unit)? = null,
    text: (@Composable () -> Unit)? = null,
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        title = title,
        text = text,
        modifier = Modifier.border(1.dp, CtrlColors.Outline, CtrlShapes.Dialog),
        shape = CtrlShapes.Dialog,
        containerColor = CtrlColors.Surface,
        titleContentColor = CtrlColors.Text,
        textContentColor = CtrlColors.Muted,
        tonalElevation = 0.dp,
    )
}

/**
 * Bottom sheet on the surface colour with 26dp top corners and no outline (the scrim separates it).
 * No border modifier here: ModalBottomSheet applies `modifier` before its own offset/drag modifiers,
 * so a border would be drawn in the wrong place.
 */
@Composable
fun CtrlBottomSheet(
    onDismissRequest: () -> Unit,
    sheetState: SheetState = rememberModalBottomSheetState(),
    content: @Composable ColumnScope.() -> Unit,
) {
    ModalBottomSheet(
        onDismissRequest = onDismissRequest,
        sheetState = sheetState,
        shape = CtrlShapes.Sheet,
        containerColor = CtrlColors.Surface,
        contentColor = CtrlColors.Text,
        tonalElevation = 0.dp,
        dragHandle = { BottomSheetDefaults.DragHandle(color = CtrlColors.ControlOutline) },
        content = content,
    )
}

// ---------- Bottom bar ----------

data class TabItem(val label: String, val icon: ImageVector, val selectedIcon: ImageVector)

/**
 * The floating bottom bar: 64dp tall, 22dp corners, surface at 92% with a 1dp border. The selected tab is
 * an accent block with icon and name; the others are just their icon.
 */
@Composable
fun CtrlBottomBar(
    items: List<TabItem>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val haptics = LocalHapticFeedback.current
    val shape = CtrlShapes.Card
    Row(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(shape)
            .background(CtrlColors.Surface.copy(alpha = 0.92f))
            .border(1.dp, CtrlColors.ControlOutline, shape)
            .padding(horizontal = 8.dp)
            .selectableGroup(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        items.forEachIndexed { i, item ->
            val selected = i == selectedIndex
            val bg by animateColorAsState(if (selected) CtrlColors.Accent else Color.Transparent, label = "tab-bg")
            val fg by animateColorAsState(if (selected) CtrlColors.OnAccent else CtrlColors.Muted, label = "tab-fg")
            Row(
                Modifier
                    .height(48.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(bg)
                    .selectable(
                        selected = selected,
                        onClick = {
                            if (!selected) haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            onSelect(i)
                        },
                        role = Role.Tab,
                        interactionSource = remember { MutableInteractionSource() },
                        indication = ripple(bounded = true),
                    )
                    .semantics { contentDescription = item.label }
                    .padding(horizontal = if (selected) 18.dp else 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (selected) item.selectedIcon else item.icon, null, Modifier.size(20.dp), tint = fg)
                if (selected) {
                    Spacer(Modifier.width(8.dp))
                    Text(item.label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
                }
            }
        }
    }
}

/** Keeps content from being hidden behind the floating bar. */
@Composable
fun Modifier.bottomClearance(extra: Dp = 24.dp): Modifier = padding(bottom = extra + LocalBottomClearance.current)
