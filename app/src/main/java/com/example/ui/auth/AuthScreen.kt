package com.example.ui.auth

import android.util.Log
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.AdminPanelSettings
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Key
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.VerifiedUser
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.credentials.Credential
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
import com.example.ui.theme.StatusInfo
import com.example.ui.theme.StatusInfoContainer
import com.example.ui.theme.StatusSuccess
import com.example.ui.theme.StatusSuccessContainer
import com.example.viewmodel.AuthUiState
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GetSignInWithGoogleOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.android.libraries.identity.googleid.GoogleIdTokenParsingException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

/**
 * Entry gateway screen in the Auth Navigation Graph.
 * Lets users choose between the dedicated Admin, Teacher, and Student login routes.
 */
@Composable
fun PortalSelectorScreen(
    uiState: AuthUiState,
    onNavigateToLogin: (UserRole) -> Unit,
    onGoogleIdToken: (String) -> Unit,
    onClearError: () -> Unit
) {
    val context = LocalContext.current
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
                // Expected on first launch or after sign-out; interactive portal screens handle login
            }
        }
    }

    AuthGradientBackdrop {
        SuperSonicLogoBadge(size = 76.dp)
        Spacer(Modifier.height(14.dp))
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
        Spacer(Modifier.height(22.dp))

        InstituteCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(
                        text = stringResource(R.string.portal_selector_heading),
                        style = MaterialTheme.typography.titleLarge,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    Text(
                        text = stringResource(R.string.portal_selector_subheading),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                FeedbackBanner(
                    message = uiState.statusMessage,
                    error = uiState.errorMessage,
                    onDismiss = onClearError
                )

                PortalGatewayCard(
                    title = stringResource(R.string.admin_login_title),
                    subtitle = "Super Admin Console • Students, Teachers, 42 PDF Sets, AI Extraction & Analytics",
                    badgeText = "SUPER ADMIN",
                    icon = Icons.Outlined.AdminPanelSettings,
                    accentColor = BrandDarkBlue,
                    accentBg = BrandSurfaceTint,
                    testTag = "portal_card_admin",
                    onClick = { onNavigateToLogin(UserRole.ADMIN) }
                )

                PortalGatewayCard(
                    title = stringResource(R.string.teacher_login_title),
                    subtitle = "Faculty Workspace • Assigned Students, Exam Assignments, Grading & Reports",
                    badgeText = "FACULTY",
                    icon = Icons.Outlined.School,
                    accentColor = StatusInfo,
                    accentBg = StatusInfoContainer,
                    testTag = "portal_card_teacher",
                    onClick = { onNavigateToLogin(UserRole.TEACHER) }
                )

                PortalGatewayCard(
                    title = stringResource(R.string.student_login_title),
                    subtitle = "Learner Portal • Timed Online Exams, Auto-Evaluation & Progress Charts",
                    badgeText = "LEARNER",
                    icon = Icons.Outlined.Person,
                    accentColor = StatusSuccess,
                    accentBg = StatusSuccessContainer,
                    testTag = "portal_card_student",
                    onClick = { onNavigateToLogin(UserRole.STUDENT) }
                )
            }
        }

        Spacer(Modifier.height(18.dp))
        Text(
            text = "AI-Powered Online Examination & Student Performance Platform • 42 Pre-Loaded Computer MCQ Sets (1,237 Questions)",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )
    }
}

/**
 * Dedicated Super Admin Login Screen destination in the Auth Navigation Graph.
 */
@Composable
fun AdminLoginScreen(
    uiState: AuthUiState,
    onGoogleIdToken: (String) -> Unit,
    onAuthError: (String) -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
    onSwitchRoleLogin: (UserRole) -> Unit
) {
    RoleLoginTemplate(
        role = UserRole.ADMIN,
        title = stringResource(R.string.admin_login_title),
        badgeLabel = "SUPER ADMINISTRATOR PORTAL",
        subtitle = "Sign in with your authorized Super Sonic Computers Education administrator Google account.",
        icon = Icons.Outlined.AdminPanelSettings,
        accentColor = BrandDarkBlue,
        accentBg = BrandSurfaceTint,
        highlights = listOf(
            "Manage student & teacher accounts with auto-generated institute IDs",
            "Import all 42 pre-loaded PDF sets & run AI PDF question extraction",
            "Create, schedule & monitor examinations with full question-wise reports"
        ),
        showOptionalCodeSection = false,
        codeHintLabel = "",
        uiState = uiState,
        onUpdatePendingCredentials = { _, _ -> },
        onGoogleIdToken = onGoogleIdToken,
        onAuthError = onAuthError,
        onClearError = onClearError,
        onBack = onBack,
        onSwitchRoleLogin = onSwitchRoleLogin
    )
}

/**
 * Dedicated Teacher Login Screen destination in the Auth Navigation Graph.
 */
@Composable
fun TeacherLoginScreen(
    uiState: AuthUiState,
    onUpdatePendingCredentials: (String, String) -> Unit,
    onGoogleIdToken: (String) -> Unit,
    onAuthError: (String) -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
    onSwitchRoleLogin: (UserRole) -> Unit
) {
    RoleLoginTemplate(
        role = UserRole.TEACHER,
        title = stringResource(R.string.teacher_login_title),
        badgeLabel = "FACULTY & INSTRUCTOR PORTAL",
        subtitle = "Sign in with Google to access your assigned students, publish test assignments, and grade answers.",
        icon = Icons.Outlined.School,
        accentColor = StatusInfo,
        accentBg = StatusInfoContainer,
        highlights = listOf(
            "View & mentor only the students assigned to your classes",
            "Assign published examinations with custom deadlines",
            "Review question-wise accuracy, grade short answers & export PDF/CSV reports"
        ),
        showOptionalCodeSection = true,
        codeHintLabel = "Teacher ID (e.g. TCH-4821)",
        uiState = uiState,
        onUpdatePendingCredentials = onUpdatePendingCredentials,
        onGoogleIdToken = onGoogleIdToken,
        onAuthError = onAuthError,
        onClearError = onClearError,
        onBack = onBack,
        onSwitchRoleLogin = onSwitchRoleLogin
    )
}

/**
 * Dedicated Student Login Screen destination in the Auth Navigation Graph.
 */
@Composable
fun StudentLoginScreen(
    uiState: AuthUiState,
    onUpdatePendingCredentials: (String, String) -> Unit,
    onGoogleIdToken: (String) -> Unit,
    onAuthError: (String) -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
    onSwitchRoleLogin: (UserRole) -> Unit
) {
    RoleLoginTemplate(
        role = UserRole.STUDENT,
        title = stringResource(R.string.student_login_title),
        badgeLabel = "STUDENT EXAMINATION PORTAL",
        subtitle = "Sign in with Google to take timed online computer exams and track your score growth.",
        icon = Icons.Outlined.Person,
        accentColor = StatusSuccess,
        accentBg = StatusSuccessContainer,
        highlights = listOf(
            "Distraction-free timed exam interface with question palette & review flags",
            "Instant automatic evaluation for MCQ, Multi-Select, True/False & Fill-in-Blank",
            "Personal score improvement trends, subject mastery charts & answer reviews"
        ),
        showOptionalCodeSection = true,
        codeHintLabel = "Student ID (e.g. STU-1042)",
        uiState = uiState,
        onUpdatePendingCredentials = onUpdatePendingCredentials,
        onGoogleIdToken = onGoogleIdToken,
        onAuthError = onAuthError,
        onClearError = onClearError,
        onBack = onBack,
        onSwitchRoleLogin = onSwitchRoleLogin
    )
}

@Composable
private fun RoleLoginTemplate(
    role: UserRole,
    title: String,
    badgeLabel: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    accentBg: Color,
    highlights: List<String>,
    showOptionalCodeSection: Boolean,
    codeHintLabel: String,
    uiState: AuthUiState,
    onUpdatePendingCredentials: (String, String) -> Unit,
    onGoogleIdToken: (String) -> Unit,
    onAuthError: (String) -> Unit,
    onClearError: () -> Unit,
    onBack: () -> Unit,
    onSwitchRoleLogin: (UserRole) -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val credentialManager = remember(context) { CredentialManager.create(context) }
    val webClientId = remember(context) { context.getString(R.string.default_web_client_id) }

    var showCodeFields by remember { mutableStateOf(false) }

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

    val roleKey = role.name.lowercase()
    val otherRoles = remember(role) { UserRole.entries.filterNot { it == role } }

    AuthGradientBackdrop {
        // Top navigation bar with Back to Portal Selector
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                onClick = onBack,
                shape = RoundedCornerShape(12.dp),
                color = Color.White.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.24f)),
                modifier = Modifier.testTag("${roleKey}_login_back_button")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = stringResource(R.string.back_to_portals),
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = stringResource(R.string.back_to_portals),
                        style = MaterialTheme.typography.labelLarge,
                        color = Color.White
                    )
                }
            }

            SuperSonicLogoBadge(size = 44.dp)
        }

        Spacer(Modifier.height(18.dp))

        // Role Header Banner
        Surface(
            shape = CircleShape,
            color = accentBg,
            border = BorderStroke(2.dp, Color.White.copy(alpha = 0.6f)),
            modifier = Modifier.size(64.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = icon,
                    contentDescription = title,
                    tint = accentColor,
                    modifier = Modifier.size(34.dp)
                )
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            text = badgeLabel,
            style = MaterialTheme.typography.labelSmall,
            color = BrandLightBlue,
            fontWeight = FontWeight.Bold
        )
        Spacer(Modifier.height(4.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.headlineMedium,
            color = Color.White,
            fontWeight = FontWeight.ExtraBold,
            textAlign = TextAlign.Center
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = subtitle,
            style = MaterialTheme.typography.bodyMedium,
            color = Color(0xFFD6F0FD),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(20.dp))

        InstituteCard(modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                // Role permissions summary
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = accentBg,
                    border = BorderStroke(1.dp, accentColor.copy(alpha = 0.24f))
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Outlined.VerifiedUser,
                                contentDescription = null,
                                tint = accentColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Portal Capabilities",
                                style = MaterialTheme.typography.labelLarge,
                                color = BrandDeepNavy,
                                fontWeight = FontWeight.Bold
                            )
                        }
                        highlights.forEach { bullet ->
                            Row(
                                verticalAlignment = Alignment.Top,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.CheckCircle,
                                    contentDescription = null,
                                    tint = accentColor,
                                    modifier = Modifier
                                        .padding(top = 2.dp)
                                        .size(15.dp)
                                )
                                Text(
                                    text = bullet,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = BrandDeepNavy
                                )
                            }
                        }
                    }
                }

                // Optional Institute ID & Passcode pre-link section for Teacher / Student
                if (showOptionalCodeSection) {
                    Surface(
                        onClick = { showCodeFields = !showCodeFields },
                        shape = RoundedCornerShape(12.dp),
                        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, BrandBorderBlue),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("${roleKey}_toggle_code_section")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Icon(
                                    imageVector = Icons.Outlined.Key,
                                    contentDescription = null,
                                    tint = BrandDarkBlue,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "Have an Admin-issued Institute ID & Passcode?",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = BrandDeepNavy
                                )
                            }
                            Text(
                                text = if (showCodeFields) "Hide" else "Link Now",
                                style = MaterialTheme.typography.labelMedium,
                                color = BrandDarkBlue,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    AnimatedVisibility(visible = showCodeFields) {
                        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedTextField(
                                value = uiState.pendingPortalCode,
                                onValueChange = { onUpdatePendingCredentials(it, uiState.pendingPortalPasscode) },
                                label = { Text(codeHintLabel) },
                                singleLine = true,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("${roleKey}_code_input")
                            )
                            OutlinedTextField(
                                value = uiState.pendingPortalPasscode,
                                onValueChange = { onUpdatePendingCredentials(uiState.pendingPortalCode, it) },
                                label = { Text("Passcode (Optional)") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("${roleKey}_passcode_input")
                            )
                            Text(
                                text = "When entered, your institute ID will be verified automatically right after Google Sign-In.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
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
                        .testTag("${roleKey}_google_sign_in_button"),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = accentColor,
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
                            text = "Sign in with Google • $title",
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                }

                // Direct quick switcher between Admin / Teacher / Student login routes
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = stringResource(R.string.switch_portal_prompt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        otherRoles.forEach { target ->
                            val targetLabel = when (target) {
                                UserRole.ADMIN -> stringResource(R.string.admin_login_title)
                                UserRole.TEACHER -> stringResource(R.string.teacher_login_title)
                                UserRole.STUDENT -> stringResource(R.string.student_login_title)
                            }
                            TextButton(
                                onClick = { onSwitchRoleLogin(target) },
                                modifier = Modifier.testTag("switch_to_${target.name.lowercase()}_login")
                            ) {
                                Text(targetLabel, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PortalGatewayCard(
    title: String,
    subtitle: String,
    badgeText: String,
    icon: ImageVector,
    accentColor: Color,
    accentBg: Color,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.25.dp, accentColor.copy(alpha = 0.28f)),
        shadowElevation = 2.dp,
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = accentBg,
                modifier = Modifier.size(52.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = icon,
                        contentDescription = title,
                        tint = accentColor,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = BrandDeepNavy,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        shape = RoundedCornerShape(6.dp),
                        color = accentBg
                    ) {
                        Text(
                            text = badgeText,
                            style = MaterialTheme.typography.labelSmall,
                            color = accentColor,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = CircleShape,
                color = accentBg,
                modifier = Modifier.size(36.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                        contentDescription = "Open $title",
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun AuthGradientBackdrop(
    content: @Composable androidx.compose.foundation.layout.ColumnScope.() -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(BrandDeepNavy, BrandDarkBlue, BrandSkyBackground, BrandSkyBackground),
                    startY = 0f,
                    endY = 920f
                )
            )
            .systemBarsPadding(),
        contentAlignment = Alignment.TopCenter
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 560.dp)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            content = content
        )
    }
}

private fun extractIdToken(credential: Credential): String? {
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
