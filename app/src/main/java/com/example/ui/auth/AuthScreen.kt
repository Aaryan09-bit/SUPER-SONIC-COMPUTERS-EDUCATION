package com.example.ui.auth

import android.util.Log
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.CredentialManager
import androidx.credentials.CustomCredential
import androidx.credentials.GetCredentialRequest
import androidx.credentials.exceptions.GetCredentialCancellationException
import androidx.credentials.exceptions.GetCredentialException
import androidx.credentials.exceptions.NoCredentialException
import com.example.R
import com.example.data.model.UserRole
import com.example.ui.components.FeedbackBanner
import com.example.ui.components.InstituteCard
import com.example.ui.components.SuperSonicLogoBadge
import com.example.ui.theme.BrandBorderBlue
import com.example.ui.theme.BrandDarkBlue
import com.example.ui.theme.BrandDeepNavy
import com.example.ui.theme.BrandLightBlue
import com.example.ui.theme.BrandSkyBackground
import com.example.ui.theme.BrandSurfaceTint
import com.example.viewmodel.AuthUiState
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    uiState: AuthUiState,
    onSelectPortalTab: (UserRole) -> Unit,
    onGoogleIdToken: (String) -> Unit,
    onAuthError: (String) -> Unit,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val webClientId = remember(context) { context.getString(R.string.default_web_client_id) }

    // Silent auto sign-in for returning users using GetGoogleIdOption
    LaunchedEffect(Unit) {
        if (uiState.firebaseUser == null) {
            try {
                val silentOption = GetGoogleIdOption.Builder()
                    .setFilterByAuthorizedAccounts(true)
                    .setServerClientId(webClientId)
                    .setAutoSelectEnabled(true)
                    .build()
                val req = GetCredentialRequest.Builder()
                    .addCredentialOption(silentOption)
                    .build()
                val res = credentialManager.getCredential(context, req)
                extractIdToken(res.credential)?.let(onGoogleIdToken)
            } catch (e: CancellationException) {
                throw e
            } catch (_: Exception) {
                // Silent check failing is expected on first launch; interactive button handles login
            }
        }
    }

    fun launchInteractiveGoogleSignIn() {
        scope.launch {
            try {
                val signInOption = GetSignInWithGoogleOption.Builder(webClientId).build()
                val request = GetCredentialRequest.Builder()
                    .addCredentialOption(signInOption)
                    .build()
                val result = credentialManager.getCredential(context, request)
                val idToken = extractIdToken(result.credential)
                if (idToken != null) {
                    onGoogleIdToken(idToken)
                } else {
                    onAuthError("Received an unexpected credential type. Please select a Google account.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (_: GetCredentialCancellationException) {
                onAuthError(context.getString(R.string.error_auth_cancelled))
            } catch (_: NoCredentialException) {
                onAuthError(context.getString(R.string.error_no_google_account))
            } catch (e: GetCredentialException) {
                Log.e("AuthScreen", "CredentialManager sign-in error", e)
                onAuthError(e.localizedMessage ?: "Google Sign-In could not be completed.")
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BrandDeepNavy, BrandDarkBlue, BrandSkyBackground, BrandSkyBackground),
                    startY = 0f,
                    endY = 900f
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            SuperSonicLogoBadge(size = 76.dp)
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.institute_full_name),
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
                fontWeight = FontWeight.ExtraBold
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(R.string.institute_tagline),
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFFD6F0FD),
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            InstituteCard(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Select Your Institute Portal",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        PortalRoleTile(
                            role = UserRole.ADMIN,
                            label = "Admin",
                            icon = Icons.Outlined.AdminPanelSettings,
                            selected = uiState.selectedPortalTab == UserRole.ADMIN,
                            onClick = { onSelectPortalTab(UserRole.ADMIN) },
                            modifier = Modifier.weight(1f)
                        )
                        PortalRoleTile(
                            role = UserRole.TEACHER,
                            label = "Teacher",
                            icon = Icons.Outlined.School,
                            selected = uiState.selectedPortalTab == UserRole.TEACHER,
                            onClick = { onSelectPortalTab(UserRole.TEACHER) },
                            modifier = Modifier.weight(1f)
                        )
                        PortalRoleTile(
                            role = UserRole.STUDENT,
                            label = "Student",
                            icon = Icons.Outlined.Person,
                            selected = uiState.selectedPortalTab == UserRole.STUDENT,
                            onClick = { onSelectPortalTab(UserRole.STUDENT) },
                            modifier = Modifier.weight(1f)
                        )
                    }

                    val roleDesc = when (uiState.selectedPortalTab) {
                        UserRole.ADMIN ->
                            "Super Admin control over students, teachers, 42 bundled PDF sets, AI PDF extraction, examinations, results, and analytics."
                        UserRole.TEACHER ->
                            "Manage assigned students, assign published examinations, monitor performance, grade short answers, and generate reports."
                        UserRole.STUDENT ->
                            "Take timed online examinations, receive automatic evaluation, review question-wise answers, and track score improvement."
                    }
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = BrandSurfaceTint,
                        border = BorderStroke(1.dp, BrandBorderBlue)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Outlined.VerifiedUser, contentDescription = null, tint = BrandDarkBlue, modifier = Modifier.size(20.dp))
                            Text(
                                text = roleDesc,
                                style = MaterialTheme.typography.bodySmall,
                                color = BrandDeepNavy
                            )
                        }
                    }

                    FeedbackBanner(
                        message = uiState.statusMessage,
                        error = uiState.errorMessage,
                        onDismiss = onClearError
                    )

                    Button(
                        onClick = { launchInteractiveGoogleSignIn() },
                        enabled = !uiState.loading,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .testTag("google_sign_in_button"),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = BrandDarkBlue,
                            contentColor = Color.White
                        )
                    ) {
                        if (uiState.loading) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                color = Color.White,
                                strokeWidth = 2.dp
                            )
                            Spacer(Modifier.width(10.dp))
                            Text("Authenticating...")
                        } else {
                            Icon(Icons.Outlined.Lock, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(
                                text = "Sign in with Google • ${uiState.selectedPortalTab.name.lowercase().replaceFirstChar(Char::uppercase)} Portal",
                                style = MaterialTheme.typography.labelLarge
                            )
                        }
                    }

                    Text(
                        text = "Teachers & Students with an Admin-issued ID & Passcode can link their portal immediately after Google verification.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }

            Spacer(Modifier.height(20.dp))
            Text(
                text = "AI-Powered Online Examination & Student Performance Platform • 42 Pre-Loaded Computer MCQ Sets (1,237 Questions)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
        }
    }
}

@Composable
private fun PortalRoleTile(
    role: UserRole,
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .height(74.dp)
            .testTag("portal_tab_${role.name.lowercase()}"),
        shape = RoundedCornerShape(14.dp),
        color = if (selected) BrandDarkBlue else MaterialTheme.colorScheme.surfaceVariant,
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) BrandLightBlue else BrandBorderBlue
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) BrandLightBlue else BrandDarkBlue,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = if (selected) Color.White else MaterialTheme.colorScheme.onSurface,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
            )
        }
    }
}

private fun extractIdToken(credential: androidx.credentials.Credential): String? {
    if (credential is CustomCredential &&
        credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
    ) {
        return try {
            GoogleIdTokenCredential.createFrom(credential.data).idToken
        } catch (e: GoogleIdTokenParsingException) {
            Log.e("AuthScreen", "Invalid Google ID token response", e)
            null
        }
    }
    return null
}
