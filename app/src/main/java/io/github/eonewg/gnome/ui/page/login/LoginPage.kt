package io.github.eonewg.gnome.ui.page.login

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.PermIdentity
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.skydoves.sandwich.suspendOnSuccess
import kotlinx.coroutines.launch
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.ext.suspendOnErrorMessage
import io.github.eonewg.gnome.feature.account.AccountSessionViewModel
import io.github.eonewg.gnome.feature.account.LoginCompatibility
import io.github.eonewg.gnome.nav.GnomeNavigator
import io.github.eonewg.gnome.nav.TimelineKey
import io.github.eonewg.gnome.ui.theme.GnomeDesign

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginPage(
    navigator: GnomeNavigator
) {
    val coroutineScope = rememberCoroutineScope()
    val accountSessionViewModel: AccountSessionViewModel = hiltViewModel()
    val currentAccount by accountSessionViewModel.currentAccount.collectAsStateWithLifecycle()
    val snackbarState = remember { SnackbarHostState() }
    val colors = GnomeDesign.colors
    val isAddAccount = currentAccount != null

    var accountLabel by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    var host by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    var accessToken by rememberSaveable(stateSaver = TextFieldValue.Saver) {
        mutableStateOf(TextFieldValue())
    }

    var loginCompatibilityWarning by remember { mutableStateOf<String?>(null) }

    fun normalizedHost(): String {
        val trimmed = host.text.trim()
        return if (trimmed.contains("//")) trimmed else "https://$trimmed"
    }

    fun login(allowHigherV1Version: Boolean = false) = coroutineScope.launch {
        if (host.text.isBlank() || accessToken.text.isBlank()) {
            snackbarState.showSnackbar(R.string.fill_login_form.string)
            return@launch
        }

        val sanitizedHost = normalizedHost()
        host = TextFieldValue(sanitizedHost)

        if (!allowHigherV1Version) {
            when (val compatibility = accountSessionViewModel.checkLoginCompatibility(sanitizedHost)) {
                LoginCompatibility.Supported -> Unit
                is LoginCompatibility.Unsupported -> {
                    snackbarState.showSnackbar(compatibility.message)
                    return@launch
                }
                is LoginCompatibility.RequiresConfirmation -> {
                    loginCompatibilityWarning = compatibility.message
                    return@launch
                }
            }
        }

        val resp = accountSessionViewModel.loginMemosWithAccessToken(
            host = sanitizedHost,
            accessToken = accessToken.text.trim(),
            accountLabel = accountLabel.text,
            allowHigherV1Version = allowHigherV1Version,
        )
        resp.suspendOnSuccess {
            navigator.resetTo(TimelineKey)
        }
        .suspendOnErrorMessage {
            snackbarState.showSnackbar(it)
        }
    }

    if (loginCompatibilityWarning != null) {
        AlertDialog(
            onDismissRequest = { loginCompatibilityWarning = null },
            title = { Text(text = R.string.unsupported_memos_version_title.string) },
            text = { Text(text = loginCompatibilityWarning ?: "") },
            confirmButton = {
                TextButton(
                    onClick = {
                        loginCompatibilityWarning = null
                        login(allowHigherV1Version = true)
                    }
                ) {
                    Text(R.string.still_login.string)
                }
            },
            dismissButton = {
                TextButton(onClick = { loginCompatibilityWarning = null }) {
                    Text(R.string.cancel.string)
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.imePadding(),
        containerColor = colors.appBackground,
        snackbarHost = {
            SnackbarHost(hostState = snackbarState)
        },
        topBar = {
            TopAppBar(
                title = {
                    if (isAddAccount) {
                        Text(
                            text = R.string.add_account.string,
                            color = colors.textPrimary,
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                navigationIcon = {
                    if (isAddAccount) {
                        IconButton(onClick = {
                            navigator.goBack()
                        }) {
                            Icon(
                                Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = R.string.back.string,
                                tint = colors.textPrimary,
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = colors.appBackground,
                    scrolledContainerColor = colors.appBackground,
                ),
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 28.dp),
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            if (!isAddAccount) {
                Text(
                    text = R.string.gnome.string,
                    fontSize = 34.sp,
                    fontWeight = FontWeight.Bold,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(10.dp))
            }

            Text(
                text = R.string.login_subtitle.string,
                fontSize = 15.sp,
                lineHeight = 22.sp,
                color = colors.textSecondary,
            )

            Spacer(modifier = Modifier.height(36.dp))

            LoginTextField(
                value = accountLabel,
                onValueChange = { accountLabel = it },
                label = R.string.account_name.string,
                leadingIcon = Icons.Outlined.Badge,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Words,
                    autoCorrectEnabled = true,
                    keyboardType = KeyboardType.Text,
                    imeAction = ImeAction.Next
                ),
            )

            Spacer(modifier = Modifier.height(14.dp))

            LoginTextField(
                value = host,
                onValueChange = { host = it },
                label = R.string.host.string,
                leadingIcon = Icons.Outlined.Computer,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Uri,
                    imeAction = ImeAction.Next
                ),
            )

            Spacer(modifier = Modifier.height(14.dp))

            LoginTextField(
                value = accessToken,
                onValueChange = { accessToken = it },
                label = R.string.access_token.string,
                leadingIcon = Icons.Outlined.PermIdentity,
                visualTransformation = PasswordVisualTransformation(),
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.None,
                    autoCorrectEnabled = false,
                    keyboardType = KeyboardType.Password,
                    imeAction = ImeAction.Go
                ),
                keyboardActions = KeyboardActions(onGo = { login() }),
            )

            Spacer(modifier = Modifier.height(32.dp))

            Button(
                onClick = { login() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = colors.accent,
                    contentColor = colors.cardBackground,
                ),
            ) {
                Text(
                    text = R.string.sign_in.string,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }

            Spacer(modifier = Modifier.height(32.dp))
        }
    }
}

@Composable
private fun LoginTextField(
    value: TextFieldValue,
    onValueChange: (TextFieldValue) -> Unit,
    label: String,
    leadingIcon: ImageVector,
    keyboardOptions: KeyboardOptions,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
) {
    val colors = GnomeDesign.colors
    OutlinedTextField(
        modifier = Modifier.fillMaxWidth(),
        value = value,
        onValueChange = onValueChange,
        singleLine = true,
        shape = RoundedCornerShape(14.dp),
        visualTransformation = visualTransformation,
        leadingIcon = {
            Icon(
                imageVector = leadingIcon,
                contentDescription = label,
                tint = colors.textSecondary,
            )
        },
        label = { Text(label) },
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = colors.textPrimary,
            unfocusedTextColor = colors.textPrimary,
            focusedBorderColor = colors.accent,
            unfocusedBorderColor = colors.divider,
            focusedLabelColor = colors.accent,
            unfocusedLabelColor = colors.textSecondary,
            focusedLeadingIconColor = colors.accent,
            unfocusedLeadingIconColor = colors.textSecondary,
            cursorColor = colors.accent,
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
        keyboardOptions = keyboardOptions,
        keyboardActions = keyboardActions,
    )
}
