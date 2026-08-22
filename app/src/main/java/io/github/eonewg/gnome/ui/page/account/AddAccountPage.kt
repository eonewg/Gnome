package io.github.eonewg.gnome.ui.page.account

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.navigation.NavHostController
import com.skydoves.sandwich.suspendOnSuccess
import kotlinx.coroutines.launch
import io.github.eonewg.gnome.R
import io.github.eonewg.gnome.data.model.Account
import io.github.eonewg.gnome.ext.popBackStackIfLifecycleIsResumed
import io.github.eonewg.gnome.ext.string
import io.github.eonewg.gnome.ui.page.common.RouteName
import io.github.eonewg.gnome.ui.theme.GnomeDesign
import io.github.eonewg.gnome.viewmodel.LocalUserState

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddAccountPage(
    navController: NavHostController,
) {
    val userStateViewModel = LocalUserState.current
    val accounts by userStateViewModel.accounts.collectAsState()
    val lifecycleOwner = LocalLifecycleOwner.current
    val coroutineScope = rememberCoroutineScope()
    val colors = GnomeDesign.colors
    val hasLocalAccount = accounts.any { it is Account.Local }
    val isFirstRun = accounts.isEmpty()

    fun toMemos() {
        navController.navigate(RouteName.MEMOS) {
            popUpTo(navController.graph.id) {
                inclusive = true
            }
            launchSingleTop = true
        }
    }

    Scaffold(
        containerColor = colors.appBackground,
        topBar = {
            TopAppBar(
                title = {
                    if (!isFirstRun) {
                        Text(
                            text = R.string.add_account.string,
                            color = colors.textPrimary,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                },
                navigationIcon = {
                    if (!isFirstRun) {
                        IconButton(onClick = {
                            navController.popBackStackIfLifecycleIsResumed(lifecycleOwner)
                        }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
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
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            contentPadding = PaddingValues(vertical = 16.dp),
        ) {
            if (isFirstRun) {
                item {
                    Column {
                        Text(
                            text = R.string.gnome.string,
                            fontSize = 34.sp,
                            fontWeight = FontWeight.Bold,
                            color = colors.textPrimary,
                        )
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = R.string.choose_account_type.string,
                            fontSize = 15.sp,
                            lineHeight = 22.sp,
                            color = colors.textSecondary,
                        )
                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }
            }

            if (!hasLocalAccount) {
                item {
                    AccountOptionCard(
                        title = R.string.add_local_account.string,
                        description = R.string.local_account_description.string,
                        icon = Icons.Outlined.Home,
                        onClick = {
                            coroutineScope.launch {
                                userStateViewModel.addLocalAccount()
                                    .suspendOnSuccess { toMemos() }
                            }
                        },
                    )
                }
            }

            item {
                AccountOptionCard(
                    title = R.string.add_memos_account.string,
                    description = R.string.memos_account_description.string,
                    icon = Icons.Outlined.Cloud,
                    onClick = { navController.navigate(RouteName.LOGIN) },
                )
            }
        }
    }
}

@Composable
private fun AccountOptionCard(
    title: String,
    description: String,
    icon: ImageVector,
    onClick: () -> Unit,
) {
    val colors = GnomeDesign.colors
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(20.dp),
        color = colors.cardBackground,
        tonalElevation = 0.dp,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = colors.textPrimary,
                modifier = Modifier.size(26.dp),
            )
            Spacer(modifier = Modifier.width(18.dp))
            Column {
                Text(
                    text = title,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = colors.textPrimary,
                )
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = description,
                    fontSize = 14.sp,
                    color = colors.textSecondary,
                )
            }
        }
    }
}
