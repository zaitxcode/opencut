package com.zaitxcode.opencut.ui.account

import androidx.compose.foundation.background
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
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.zaitxcode.opencut.OpenCutApp
import com.zaitxcode.opencut.R
import com.zaitxcode.opencut.core.account.UserProfile
import com.zaitxcode.opencut.core.entitlements.Entitlements
import com.zaitxcode.opencut.core.entitlements.Feature
import com.zaitxcode.opencut.core.entitlements.FeatureGroup
import com.zaitxcode.opencut.core.i18n.AppLanguage
import com.zaitxcode.opencut.core.i18n.publishSystemLocale
import com.zaitxcode.opencut.ui.theme.BrandCentered
import com.zaitxcode.opencut.ui.theme.BrandLogo
import com.zaitxcode.opencut.ui.theme.BrandMark

/**
 * First launch gate.
 *
 * Two paths are offered and they are equal in capability: a guest profile that
 * starts editing immediately, or a local account that only stores a display name
 * on this device. Neither path talks to a server.
 */
@Composable
fun OnboardingScreen(onDone: () -> Unit) {
    val context = LocalContext.current
    val store = (context.applicationContext as OpenCutApp).accountStore
    var showNameField by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 24.dp, vertical = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(28.dp))
        BrandCentered(markSize = 68.dp, fontSize = 32)
        Spacer(Modifier.height(10.dp))
        Text(
            text = stringResource(R.string.onboarding_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Spacer(Modifier.height(32.dp))

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Promise(
                stringResource(R.string.onboarding_free_title),
                stringResource(R.string.onboarding_free_body),
            )
            Promise(
                stringResource(R.string.onboarding_account_title),
                stringResource(R.string.onboarding_account_body),
            )
            Promise(
                stringResource(R.string.onboarding_open_title),
                stringResource(R.string.onboarding_open_body),
            )
        }

        Spacer(Modifier.height(36.dp))

        Button(
            onClick = {
                store.continueAsGuest()
                onDone()
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(stringResource(R.string.onboarding_guest), fontWeight = FontWeight.SemiBold)
        }

        Spacer(Modifier.height(10.dp))

        if (!showNameField) {
            OutlinedButton(
                onClick = { showNameField = true },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Icon(Icons.Default.Person, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.onboarding_account))
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(14.dp),
            ) {
                Text(
                    text = stringResource(R.string.account_name_label),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.account_guest_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(32) },
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.account_name_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(10.dp))
                Button(
                    onClick = {
                        store.createLocalAccount(name)
                        onDone()
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp),
                    shape = RoundedCornerShape(12.dp),
                ) {
                    Text(stringResource(R.string.onboarding_account))
                }
            }
        }

        Spacer(Modifier.height(24.dp))
        Text(
            text = stringResource(R.string.account_licence),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun Promise(title: String, detail: String) {
    Row(verticalAlignment = Alignment.Top) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier
                .padding(top = 2.dp)
                .size(20.dp),
        )
        Spacer(Modifier.width(12.dp))
        Column {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
            )
            Text(
                text = detail,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * The account and plan screen.
 *
 * It doubles as the "what you get for free" page: the whole feature catalogue is
 * listed with a free badge, because there is no paid tier to compare against.
 */
@Composable
fun AccountScreen(
    onBack: () -> Unit,
    onSignedOut: () -> Unit,
) {
    val context = LocalContext.current
    val store = (context.applicationContext as OpenCutApp).accountStore
    var profile by remember { mutableStateOf(store.current()) }
    var renaming by remember { mutableStateOf(false) }
    var draftName by remember { mutableStateOf(profile?.displayName ?: "") }
    var language by remember { mutableStateOf(AppLanguage.current(context)) }

    // Switching the language rebuilds the activity, so every string and the text
    // direction follow the new choice right away.
    val onLanguage: (AppLanguage) -> Unit = { picked ->
        if (picked != language) {
            AppLanguage.set(context, picked)
            publishSystemLocale(context, picked)
            (context as? android.app.Activity)?.recreate()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.editor_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
            Text(
                text = stringResource(R.string.account_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp),
        ) {
            // ---------------------------------------------------------- profile --
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(
                    modifier = Modifier
                        .size(52.dp)
                        .clip(CircleShape)
                        .background(Color(profile?.avatarColor ?: 0xFF3A7AFE.toInt())),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = profile?.initial ?: "?",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 22.sp,
                    )
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = profile?.displayName ?: stringResource(R.string.account_guest),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = when {
                            profile == null -> stringResource(R.string.account_guest_body)
                            profile!!.isGuest -> stringResource(R.string.account_guest_body)
                            else -> "${profile!!.handle} · " + stringResource(R.string.account_licence)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (profile != null) {
                    TextButton(onClick = {
                        draftName = profile?.displayName ?: ""
                        renaming = !renaming
                    }) {
                        Text(
                            if (renaming) stringResource(R.string.common_cancel)
                            else stringResource(R.string.account_name_label),
                        )
                    }
                }
            }

            if (renaming) {
                Spacer(Modifier.height(10.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it.take(32) },
                        singleLine = true,
                        modifier = Modifier.weight(1f),
                    )
                    Button(onClick = {
                        profile = store.rename(draftName)
                        renaming = false
                    }) {
                        Text(stringResource(R.string.common_save))
                    }
                }
            }


            // --------------------------------------------------------- language --
            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.account_language),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                LanguageChip(
                    label = stringResource(R.string.account_language_system),
                    selected = language == AppLanguage.SYSTEM,
                    onClick = { onLanguage(AppLanguage.SYSTEM) },
                    modifier = Modifier.weight(1f),
                )
                LanguageChip(
                    label = stringResource(R.string.account_language_en),
                    selected = language == AppLanguage.ENGLISH,
                    onClick = { onLanguage(AppLanguage.ENGLISH) },
                    modifier = Modifier.weight(1f),
                )
                LanguageChip(
                    label = stringResource(R.string.account_language_ar),
                    selected = language == AppLanguage.ARABIC,
                    onClick = { onLanguage(AppLanguage.ARABIC) },
                    modifier = Modifier.weight(1f),
                )
            }

            // ------------------------------------------------------------- plan --
            Spacer(Modifier.height(18.dp))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    BrandMark(size = 28.dp)
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = Entitlements.PLAN_NAME,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = Entitlements.PLAN_DETAIL,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Spacer(Modifier.height(18.dp))
            Text(
                text = stringResource(R.string.account_about),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))

            Entitlements.grouped().forEach { (group, features) ->
                FeatureGroupBlock(group, features)
                Spacer(Modifier.height(12.dp))
            }

            // ---------------------------------------------------------- actions --
            Spacer(Modifier.height(10.dp))
            HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
            Spacer(Modifier.height(14.dp))

            if (profile == null) {
                Button(
                    onClick = {
                        profile = store.continueAsGuest()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.onboarding_guest))
                }
            } else {
                OutlinedButton(
                    onClick = {
                        store.signOut()
                        profile = null
                        onSignedOut()
                    },
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(stringResource(R.string.account_sign_out))
                }
            }

            Spacer(Modifier.height(28.dp))
            Text(
                text = stringResource(R.string.account_licence),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 24.dp),
                textAlign = TextAlign.Center,
            )
        }
    }
}

@Composable
private fun LanguageChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(
                if (selected) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.surface,
            )
            .clickable { onClick() }
            .padding(vertical = 10.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun FeatureGroupBlock(group: FeatureGroup, features: List<Feature>) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(MaterialTheme.colorScheme.surface)
            .padding(vertical = 8.dp, horizontal = 14.dp),
    ) {
        Text(
            text = group.title,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(vertical = 6.dp),
        )
        features.forEach { feature ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = feature.title,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                    )
                    Text(
                        text = feature.description,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Text(
                    text = if (Entitlements.isUnlocked(feature)) "FREE" else "—",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }
}

/** Small header row used by screens that want the wordmark with a back action. */
@Composable
fun BrandedTopBar(
    title: String?,
    onBack: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.editor_back),
                    tint = MaterialTheme.colorScheme.onBackground,
                )
            }
        } else {
            Spacer(Modifier.width(12.dp))
        }
        if (title == null) {
            BrandLogo(markSize = 30.dp, fontSize = 20)
        } else {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onBackground,
            )
        }
    }
}
