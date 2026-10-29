package com.stackpilot.desktop.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stackpilot.desktop.auth.AuthClient
import com.stackpilot.desktop.auth.AuthConfig
import com.stackpilot.desktop.auth.AuthException
import com.stackpilot.desktop.auth.AuthResult
import com.stackpilot.desktop.auth.AuthScreen
import com.stackpilot.desktop.components.BrandMark
import com.stackpilot.desktop.components.loadLogoBytes
import com.stackpilot.desktop.theme.AppColors
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.sin

// ─── Dark colour palette ──────────────────────────────────────────────────────
private val BgScreen      = Color(0xFF070B12)   // overall background
private val BgCard        = Color(0xFF0D121F)   // form card background
private val BgCardAlt     = Color(0xFF131B2E)   // elevated surfaces
private val BorderLight   = Color(0x1AFFFFFF)   // white/10
private val BorderField   = Color(0x26FFFFFF)   // white/15
private val BorderFocus   = Color(0xFF2563EB)   // blue focus ring
private val TextMain      = Color(0xFFF8FAFC)   // primary text
private val TextMuted     = Color(0xFF94A3B8)   // secondary text
private val TextPlaceholder = Color(0xFF475569) // placeholder / muted labels
private val BtnBg         = Color(0xFF2563EB)   // primary button — blue
private val BtnBorder     = Color(0xFF3B82F6)

// Right panel
private val PanelBg       = Color(0xFF050505)
private val OrangeAccent  = Color(0xFFFC7819)
private val PanelTextMuted = Color(0xD9FFFFFF)

/** What the tool actually does — shown beside the "Think fast" headline. */
private val PanelFeatures = listOf(
    "Live health for every container, service and published port",
    "Log analysis that groups failures into ranked signatures",
    "Root causes backed by config, log, Docker and documentation evidence",
    "Fixes you approve first — backed up, rollback-able and verified"
)

// ─── Entry point ─────────────────────────────────────────────────────────────

@Composable
fun AuthScreen(
    onAuthenticated: (AuthResult) -> Unit
) {
    val client = remember { AuthClient() }
    val scope  = rememberCoroutineScope()

    var currentScreen  by remember { mutableStateOf(AuthScreen.Login) }
    var pendingEmail   by remember { mutableStateOf("") }
    var busy           by remember { mutableStateOf(false) }
    var errorMessage   by remember { mutableStateOf<String?>(null) }
    var googleWaiting  by remember { mutableStateOf(false) }

    LaunchedEffect(currentScreen) { errorMessage = null }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BgScreen)
    ) {
        if (googleWaiting) {
            GoogleWaitingOverlay(
                onCancel = { googleWaiting = false; busy = false }
            )
        } else {
            // ── Two-column layout ─────────────────────────────────────────
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // ── Left: form panel ──────────────────────────────────────
                Box(
                    modifier = Modifier
                        .weight(0.94f)
                        .fillMaxHeight()
                        .clip(RoundedCornerShape(8.dp))
                        .border(1.dp, BorderLight, RoundedCornerShape(8.dp))
                        .background(BgCard)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 56.dp, vertical = 72.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    AnimatedContent(
                        targetState = currentScreen,
                        transitionSpec = {
                            val dir = if (targetState.ordinal > initialState.ordinal) 1 else -1
                            (fadeIn() + slideInHorizontally { dir * it }) togetherWith
                            (fadeOut() + slideOutHorizontally { -dir * it })
                        },
                        label = "authPanel"
                    ) { screen ->
                        when (screen) {
                            AuthScreen.Login   -> LoginPanel(
                                busy = busy, error = errorMessage,
                                onLogin = { email, pw ->
                                    errorMessage = null; busy = true
                                    scope.launch {
                                        try {
                                            val r = withContext(Dispatchers.IO) { client.login(email, pw) }
                                            onAuthenticated(r)
                                        } catch (e: AuthException) { errorMessage = e.message
                                        } catch (_: Exception)     { errorMessage = "Cannot reach ${AuthConfig.baseUrl}" }
                                        finally { busy = false }
                                    }
                                },
                                onGoogle = {
                                    errorMessage = null; busy = true; googleWaiting = true
                                    openGoogleSignIn(scope, intent = "signin",
                                        onResult = { r -> googleWaiting = false; busy = false; onAuthenticated(r) },
                                        onError  = { m -> googleWaiting = false; busy = false; errorMessage = m })
                                },
                                onCreateAccount = { currentScreen = AuthScreen.Signup }
                            )
                            AuthScreen.Signup  -> SignupPanel(
                                busy = busy, error = errorMessage,
                                onSignup = { name, email, pw ->
                                    errorMessage = null; busy = true
                                    scope.launch {
                                        try {
                                            withContext(Dispatchers.IO) { client.signup(name, email, pw) }
                                            pendingEmail = email; currentScreen = AuthScreen.VerifyOtp
                                        } catch (e: AuthException) { errorMessage = e.message
                                        } catch (_: Exception)     { errorMessage = "Cannot reach ${AuthConfig.baseUrl}" }
                                        finally { busy = false }
                                    }
                                },
                                onGoogle = {
                                    errorMessage = null; busy = true; googleWaiting = true
                                    openGoogleSignIn(scope, intent = "signup",
                                        onResult = { r -> googleWaiting = false; busy = false; onAuthenticated(r) },
                                        onError  = { m -> googleWaiting = false; busy = false; errorMessage = m })
                                },
                                onBack = { currentScreen = AuthScreen.Login }
                            )
                            AuthScreen.VerifyOtp -> OtpPanel(
                                email = pendingEmail, busy = busy, error = errorMessage,
                                onVerify = { otp ->
                                    errorMessage = null; busy = true
                                    scope.launch {
                                        try {
                                            val r = withContext(Dispatchers.IO) { client.verifyOtp(pendingEmail, otp) }
                                            onAuthenticated(r)
                                        } catch (e: AuthException) { errorMessage = e.message
                                        } catch (_: Exception)     { errorMessage = "Verification failed. Try again." }
                                        finally { busy = false }
                                    }
                                },
                                onBack = { currentScreen = AuthScreen.Signup }
                            )
                        }
                    }
                }

                // ── Right: gradient panel ─────────────────────────────────
                RightPanel(modifier = Modifier.weight(1.06f).fillMaxHeight())
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Login panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoginPanel(
    busy: Boolean,
    error: String?,
    onLogin: (String, String) -> Unit,
    onGoogle: () -> Unit,
    onCreateAccount: () -> Unit
) {
    var email    by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var showPw   by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        // Brand mark — white logo on dark background
        BrandHeader()
        Spacer(Modifier.height(28.dp))

        // Headline
        Text(
            text = "Sign in to StackPilot",
            fontSize = 38.sp,
            fontWeight = FontWeight.Medium,
            color = TextMain,
            letterSpacing = (-1.5).sp,
            lineHeight = 42.sp
        )
        Spacer(Modifier.height(8.dp))
        Text(
            text = "Diagnose, understand, and fix your stack",
            fontSize = 20.sp,
            color = TextMuted,
            fontWeight = FontWeight.Normal
        )

        Spacer(Modifier.height(40.dp))

        // Social buttons
        SocialBtn(
            modifier = Modifier.fillMaxWidth(),
            icon = { GoogleIcon() },
            label = "Sign in with Google",
            onClick = onGoogle,
            enabled = !busy
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Opens ${AuthConfig.baseUrl}/signin in your browser to continue with Google.",
            fontSize = 12.sp,
            color = TextPlaceholder
        )

        Spacer(Modifier.height(32.dp))

        OrDivider()

        Spacer(Modifier.height(32.dp))

        ErrorBanner(error)
        if (error != null) Spacer(Modifier.height(16.dp))

        // Email
        FieldBox(label = "Email", value = email, type = KeyboardType.Email,
            onValueChange = { email = it })
        Spacer(Modifier.height(14.dp))

        // Password
        FieldBox(
            label = "Password",
            value = password,
            type = KeyboardType.Password,
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            onValueChange = { password = it },
            trailingContent = {
                Icon(
                    imageVector = if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = "Toggle",
                    tint = TextPlaceholder,
                    modifier = Modifier.size(18.dp).clickable { showPw = !showPw }
                )
            }
        )

        Spacer(Modifier.height(32.dp))

        // Submit
        SubmitButton(
            text = "Sign In",
            busy = busy,
            enabled = email.isNotBlank() && password.isNotBlank(),
            onClick = { onLogin(email.trim(), password) }
        )

        Spacer(Modifier.height(18.dp))

        InfoLine("Diagnostics run on this machine — nothing is uploaded unless you export a report.")

        Spacer(Modifier.height(24.dp))

        // Create account link
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Don't have an account? ", fontSize = 14.sp, color = TextMuted)
            Text(
                text = "Create account",
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                color = AppColors.PrimaryBlue,
                textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(enabled = !busy) { onCreateAccount() }
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Signup panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SignupPanel(
    busy: Boolean,
    error: String?,
    onSignup: (String, String, String) -> Unit,
    onGoogle: () -> Unit,
    onBack: () -> Unit
) {
    var firstName  by remember { mutableStateOf("") }
    var lastName   by remember { mutableStateOf("") }
    var email      by remember { mutableStateOf("") }
    var password   by remember { mutableStateOf("") }
    var showPw     by remember { mutableStateOf(false) }
    var checkNews  by remember { mutableStateOf(false) }
    var checkTerms by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 560.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        BrandHeader()
        Spacer(Modifier.height(18.dp))

        // Back
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.clickable(enabled = !busy) { onBack() }
        ) {
            @Suppress("DEPRECATION")
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextMuted, modifier = Modifier.size(16.dp))
            Text("Back to sign in", fontSize = 13.sp, color = TextMuted)
        }
        Spacer(Modifier.height(24.dp))

        Text(
            "Create an account",
            fontSize = 38.sp,
            fontWeight = FontWeight.Medium,
            color = TextMain,
            letterSpacing = (-1.5).sp
        )
        Spacer(Modifier.height(8.dp))
        Text("We'll send a verification code to your email.", fontSize = 20.sp, color = TextMuted)

        Spacer(Modifier.height(36.dp))

        // Social
        SocialBtn(Modifier.fillMaxWidth(), { GoogleIcon() }, "Sign up with Google", onGoogle, !busy)
        Spacer(Modifier.height(10.dp))
        Text(
            "Opens ${AuthConfig.baseUrl}/signup in your browser to continue with Google.",
            fontSize = 12.sp,
            color = TextPlaceholder
        )

        Spacer(Modifier.height(28.dp))
        OrDivider()
        Spacer(Modifier.height(28.dp))

        ErrorBanner(error)
        if (error != null) Spacer(Modifier.height(16.dp))

        // First + Last name
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            FieldBox(modifier = Modifier.weight(1f), label = "First Name", value = firstName,
                onValueChange = { firstName = it })
            FieldBox(modifier = Modifier.weight(1f), label = "Last Name", value = lastName,
                onValueChange = { lastName = it })
        }
        Spacer(Modifier.height(14.dp))
        FieldBox(label = "Email", value = email, type = KeyboardType.Email, onValueChange = { email = it })
        Spacer(Modifier.height(14.dp))
        FieldBox(
            label = "Password", value = password, type = KeyboardType.Password,
            visualTransformation = if (showPw) VisualTransformation.None else PasswordVisualTransformation(),
            onValueChange = { password = it },
            trailingContent = {
                Icon(
                    imageVector = if (showPw) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                    contentDescription = "Toggle", tint = TextPlaceholder,
                    modifier = Modifier.size(18.dp).clickable { showPw = !showPw }
                )
            }
        )

        Spacer(Modifier.height(20.dp))

        // Checkboxes
        CheckboxLine(
            checked = checkNews,
            onCheckedChange = { checkNews = it },
            text = "I don't want to receive emails about StackPilot feature updates"
        )
        Spacer(Modifier.height(12.dp))
        CheckboxLine(
            checked = checkTerms,
            onCheckedChange = { checkTerms = it },
            richText = {
                Text("By creating an account, you agree to our ", fontSize = 13.sp, color = TextPlaceholder)
                Text("Terms and Services", fontSize = 13.sp, color = TextPlaceholder,
                    textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium)
                Text(" and ", fontSize = 13.sp, color = TextPlaceholder)
                Text("Privacy Policy", fontSize = 13.sp, color = TextPlaceholder,
                    textDecoration = TextDecoration.Underline, fontWeight = FontWeight.Medium)
            }
        )

        Spacer(Modifier.height(28.dp))

        SubmitButton(
            text = "Send Verification Code",
            busy = busy,
            enabled = firstName.isNotBlank() && email.isNotBlank() && password.length >= 8,
            onClick = { onSignup("$firstName $lastName".trim(), email.trim(), password) }
        )
        Spacer(Modifier.height(18.dp))
        InfoLine("Diagnostics run on this machine — nothing is uploaded unless you export a report.")
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OTP panel
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun OtpPanel(
    email: String,
    busy: Boolean,
    error: String?,
    onVerify: (String) -> Unit,
    onBack: () -> Unit
) {
    val digits = remember { mutableStateListOf("","","","","","") }
    val focuses = remember { List(6) { FocusRequester() } }
    val otp = digits.joinToString("")

    Column(
        modifier = Modifier.fillMaxWidth().widthIn(max = 480.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        BrandHeader(modifier = Modifier.align(Alignment.Start))
        Spacer(Modifier.height(18.dp))

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.align(Alignment.Start).clickable(enabled = !busy) { onBack() }
        ) {
            @Suppress("DEPRECATION")
            Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = TextMuted, modifier = Modifier.size(16.dp))
            Text("Back", fontSize = 13.sp, color = TextMuted)
        }
        Spacer(Modifier.height(32.dp))

        // Email icon
        Box(
            modifier = Modifier
                .size(64.dp)
                .clip(CircleShape)
                .background(Color(0xFF1E2B45)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MarkEmailRead, contentDescription = null,
                tint = AppColors.PrimaryBlueHover, modifier = Modifier.size(32.dp))
        }
        Spacer(Modifier.height(20.dp))

        Text("Check your email", fontSize = 32.sp, fontWeight = FontWeight.Medium,
            color = TextMain, letterSpacing = (-1.sp), textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text("We sent a 6-digit code to\n$email", fontSize = 16.sp, color = TextMuted,
            textAlign = TextAlign.Center, lineHeight = 22.sp)

        Spacer(Modifier.height(12.dp))
        ErrorBanner(error)
        if (error != null) Spacer(Modifier.height(8.dp))
        Spacer(Modifier.height(12.dp))

        // 6 digit boxes
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally)
        ) {
            digits.forEachIndexed { i, d ->
                OtpDigitBox(
                    value = d,
                    focusRequester = focuses[i],
                    onValueChange = { v ->
                        if (v.length <= 1 && (v.isEmpty() || v.all { it.isDigit() })) {
                            digits[i] = v
                            if (v.isNotEmpty() && i < 5) focuses[i+1].requestFocus()
                            if (v.isEmpty() && i > 0) focuses[i-1].requestFocus()
                        }
                    }
                )
            }
        }
        Spacer(Modifier.height(28.dp))

        SubmitButton(
            text = "Verify & Create Account",
            busy = busy,
            enabled = otp.length == 6,
            onClick = { onVerify(otp) }
        )
        Spacer(Modifier.height(20.dp))

        Row(
            horizontalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Didn't receive a code? ", fontSize = 14.sp, color = TextMuted)
            Text("Resend", fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
                color = AppColors.PrimaryBlue, textDecoration = TextDecoration.Underline,
                modifier = Modifier.clickable(enabled = !busy) { onBack() })
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Right gradient panel — "Think fast, Build faster"
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RightPanel(modifier: Modifier = Modifier) {
    // Continuous time counter — drives all animations
    val infiniteTransition = rememberInfiniteTransition(label = "ambient")
    val time by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue  = (2f * Math.PI).toFloat() * 4f,   // 4 full cycles before repeat
        animationSpec = infiniteRepeatable(
            tween(durationMillis = 18_000, easing = LinearEasing)
        ),
        label = "time"
    )

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF050505))
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawAmbientGradient(time)
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(48.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Brand — white StackPilot mark + wordmark on the dark panel
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    BrandMark(height = 30.dp)
                    Text(
                        text = "StackPilot",
                        fontSize = 19.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                        letterSpacing = (-0.3).sp
                    )
                }

                Spacer(Modifier.height(30.dp))

                Text(
                    text = "Think fast,\nBuild faster",
                    fontSize = 62.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                    lineHeight = 60.sp,
                    letterSpacing = (-2.5).sp
                )

                Spacer(Modifier.height(20.dp))

                Text(
                    text = "StackPilot is a desktop cockpit for local Docker stacks. " +
                        "It reads your containers, application logs, config and docs, " +
                        "then tells you why the stack broke — and fixes it only with " +
                        "your approval.",
                    fontSize = 15.sp,
                    color = PanelTextMuted,
                    lineHeight = 23.sp,
                    modifier = Modifier.widthIn(max = 470.dp)
                )

                Spacer(Modifier.height(26.dp))

                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    PanelFeatures.forEach { line ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.CheckCircle,
                                contentDescription = null,
                                tint = Color(0xCCFFFFFF),
                                modifier = Modifier.size(15.dp)
                            )
                            Text(
                                text = line,
                                fontSize = 13.sp,
                                color = PanelTextMuted,
                                lineHeight = 18.sp
                            )
                        }
                    }
                }
            }
        }
    }
}

/**
 * Draws the ambient gradient animation.
 *
 * Four large radial orbs — white/orange — orbit slowly around the panel edges
 * using sine/cosine offsets driven by [time]. Each orb has a different phase
 * and radius so they never all align. A dense grain layer is regenerated each
 * frame with a seed derived from [time] so it visibly refreshes.
 */
private fun DrawScope.drawAmbientGradient(time: Float) {
    val w = size.width
    val h = size.height

    // ── Background fill ──────────────────────────────────────────────────────
    drawRect(Color(0xFF050505))

    // ── Four animated orbs ───────────────────────────────────────────────────
    //
    // Each orb has a centre that drifts along an elliptical path whose phase
    // is offset by ~90° from the others, giving a "breathing" quality.
    //
    // Orb A — top-left, white-orange, slow drift
    val ax = w * (0.0f + 0.22f * sin(time * 0.7f + 0.0f))
    val ay = h * (0.0f + 0.20f * sin(time * 0.5f + 1.0f))
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to OrangeAccent.copy(alpha = 0.75f),
                0.5f to OrangeAccent.copy(alpha = 0.35f),
                1.0f to Color.Transparent
            ),
            center = Offset(ax, ay),
            radius = w * 0.62f
        ),
        radius  = w * 0.62f,
        center  = Offset(ax, ay),
        blendMode = BlendMode.Screen
    )

    // Orb B — top-right, warm orange, medium drift
    val bx = w * (1.0f - 0.18f * sin(time * 0.6f + 2.1f))
    val by = h * (0.0f + 0.22f * sin(time * 0.8f + 0.8f))
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to OrangeAccent.copy(alpha = 0.70f),
                0.5f to OrangeAccent.copy(alpha = 0.28f),
                1.0f to Color.Transparent
            ),
            center = Offset(bx, by),
            radius = w * 0.55f
        ),
        radius  = w * 0.55f,
        center  = Offset(bx, by),
        blendMode = BlendMode.Screen
    )

    // Orb C — bottom-right, deep orange, slower
    val cx = w * (1.0f - 0.14f * sin(time * 0.4f + 4.2f))
    val cy = h * (1.0f - 0.18f * sin(time * 0.6f + 3.1f))
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to OrangeAccent.copy(alpha = 0.65f),
                0.45f to OrangeAccent.copy(alpha = 0.25f),
                1.0f to Color.Transparent
            ),
            center = Offset(cx, cy),
            radius = w * 0.58f
        ),
        radius  = w * 0.58f,
        center  = Offset(cx, cy),
        blendMode = BlendMode.Screen
    )

    // Orb D — bottom-left, white hint, fast-ish
    val dx = w * (0.0f + 0.20f * sin(time * 0.9f + 5.5f))
    val dy = h * (1.0f - 0.16f * sin(time * 0.5f + 2.4f))
    drawCircle(
        brush = Brush.radialGradient(
            colorStops = arrayOf(
                0.0f to Color.White.copy(alpha = 0.22f),
                0.4f to OrangeAccent.copy(alpha = 0.30f),
                1.0f to Color.Transparent
            ),
            center = Offset(dx, dy),
            radius = w * 0.52f
        ),
        radius  = w * 0.52f,
        center  = Offset(dx, dy),
        blendMode = BlendMode.Screen
    )

    // ── Central dark vignette — keeps the centre deep so orbs don't wash it ──
    drawRect(
        brush = Brush.radialGradient(
            colors = listOf(Color(0xCC000000), Color.Transparent),
            center = Offset(w * 0.5f, h * 0.5f),
            radius = w * 0.48f
        )
    )

    // ── Grain noise — seed changes each frame so it visibly refreshes ─────────
    val seed = (time * 30f).toInt()
    val rng  = kotlin.random.Random(seed)
    repeat(3200) {
        val gx    = rng.nextFloat() * w
        val gy    = rng.nextFloat() * h
        val alpha = rng.nextFloat() * 0.055f
        val r     = rng.nextFloat() * 0.55f + 0.2f
        drawCircle(Color.White.copy(alpha = alpha), radius = r, center = Offset(gx, gy))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Shared components
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// Shared brand + helper bits
// ─────────────────────────────────────────────────────────────────────────────

/** StackPilot mark (logo-white.png) with the wordmark beside it. */
@Composable
private fun BrandHeader(modifier: Modifier = Modifier) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        BrandMark(height = 34.dp)
        Text(
            text = "StackPilot",
            fontSize = 20.sp,
            fontWeight = FontWeight.SemiBold,
            color = TextMain,
            letterSpacing = (-0.3).sp
        )
    }
}

/** Small, quiet footnote used under the primary actions. */
@Composable
private fun InfoLine(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(7.dp)
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = TextPlaceholder,
            modifier = Modifier.size(13.dp)
        )
        Text(text, fontSize = 12.sp, color = TextPlaceholder, lineHeight = 16.sp)
    }
}

@Composable
private fun SocialBtn(
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
    label: String,
    onClick: () -> Unit,
    enabled: Boolean = true
) {
    Row(
        modifier = modifier
            .height(44.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(1.dp, BorderField, RoundedCornerShape(10.dp))
            .background(BgCardAlt)
            .clickable(enabled = enabled, onClick = onClick)
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally)
    ) {
        icon()
        Text(
            label,
            fontSize = 13.sp,
            color = TextMain,
            fontWeight = FontWeight.Medium,
            maxLines = 1
        )
    }
}

@Composable
private fun FieldBox(
    modifier: Modifier = Modifier,
    label: String,
    value: String,
    type: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    onValueChange: (String) -> Unit,
    trailingContent: (@Composable () -> Unit)? = null
) {
    var focused by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
                width = if (focused) 1.5.dp else 1.dp,
                color = if (focused) BorderFocus else BorderField,
                shape = RoundedCornerShape(10.dp)
            )
            .background(BgCardAlt)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .weight(1f)
                .onFocusChanged { focused = it.isFocused },
            singleLine = true,
            textStyle = TextStyle(
                color = TextMain,
                fontSize = 15.sp,
                fontFamily = FontFamily.SansSerif
            ),
            cursorBrush = SolidColor(BorderFocus),
            keyboardOptions = KeyboardOptions(keyboardType = type),
            visualTransformation = visualTransformation,
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(label, color = TextPlaceholder, fontSize = 15.sp)
                    }
                    inner()
                }
            }
        )
        // Label badge on the right when field has content and no trailing icon
        if (value.isNotEmpty() && trailingContent == null) {
            Text(
                label, fontSize = 11.sp, color = AppColors.PrimaryBlueHover,
                fontWeight = FontWeight.Medium
            )
        }
        trailingContent?.invoke()
    }
}

@Composable
private fun SubmitButton(
    text: String,
    busy: Boolean,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        enabled = enabled && !busy,
        modifier = Modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = BtnBg,
            disabledContainerColor = Color(0xFF1E2B45)
        ),
        border = BorderStroke(1.dp, if (enabled && !busy) BtnBorder else Color.Transparent)
    ) {
        if (busy) {
            CircularProgressIndicator(
                modifier = Modifier.size(20.dp),
                strokeWidth = 2.dp,
                color = Color.White
            )
        } else {
            Text(
                text,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = if (enabled) Color.White else TextPlaceholder
            )
        }
    }
}

@Composable
private fun OrDivider() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderLight)
        Text("  or  ", fontSize = 13.sp, fontWeight = FontWeight.Medium, color = TextPlaceholder)
        HorizontalDivider(modifier = Modifier.weight(1f), color = BorderLight)
    }
}

@Composable
private fun ErrorBanner(message: String?) {
    if (message.isNullOrBlank()) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(8.dp))
            .background(Color(0xFF2D1515))
            .border(1.dp, Color(0xFF7F1D1D), RoundedCornerShape(8.dp))
            .padding(10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Icon(Icons.Filled.Error, contentDescription = null,
            tint = Color(0xFFEF4444), modifier = Modifier.size(16.dp))
        Text(message, fontSize = 13.sp, color = Color(0xFFFCA5A5), lineHeight = 17.sp)
    }
}

@Composable
private fun CheckboxLine(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    text: String? = null,
    richText: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        // Custom checkbox — dark theme
        Box(
            modifier = Modifier
                .padding(top = 2.dp)
                .size(15.dp)
                .clip(RoundedCornerShape(3.dp))
                .border(
                    1.dp,
                    if (checked) BorderFocus else BorderField,
                    RoundedCornerShape(3.dp)
                )
                .background(if (checked) BtnBg else BgCardAlt)
                .clickable { onCheckedChange(!checked) },
            contentAlignment = Alignment.Center
        ) {
            if (checked) {
                Icon(
                    Icons.Filled.Check, contentDescription = null,
                    tint = Color.White, modifier = Modifier.size(10.dp)
                )
            }
        }
        if (richText != null) {
            Row(modifier = Modifier.weight(1f)) { richText() }
        } else if (text != null) {
            Text(text, fontSize = 13.sp, color = TextPlaceholder, lineHeight = 18.sp,
                modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun OtpDigitBox(
    value: String,
    focusRequester: FocusRequester,
    onValueChange: (String) -> Unit
) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = Modifier
            .size(52.dp)
            .clip(RoundedCornerShape(10.dp))
            .border(
                1.5.dp,
                if (value.isNotEmpty()) BorderFocus else BorderField,
                RoundedCornerShape(10.dp)
            )
            .background(if (value.isNotEmpty()) Color(0xFF1E3A5F) else BgCardAlt)
            .focusRequester(focusRequester),
        singleLine = true,
        textStyle = TextStyle(
            color = TextMain,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            fontFamily = FontFamily.Monospace
        ),
        cursorBrush = SolidColor(BorderFocus),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
        decorationBox = { inner ->
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { inner() }
        }
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// Google waiting overlay
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GoogleWaitingOverlay(onCancel: () -> Unit) {
    val infiniteTransition = rememberInfiniteTransition(label = "spin")
    val rotation by infiniteTransition.animateFloat(
        initialValue = 0f, targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1200, easing = LinearEasing)),
        label = "rotation"
    )
    Box(
        modifier = Modifier.fillMaxSize().background(BgScreen),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(0.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .background(Color(0xFF1E3A5F)),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(44.dp).rotate(rotation),
                    color = Color(0xFF4285F4),
                    strokeWidth = 3.dp
                )
            }
            Spacer(Modifier.height(28.dp))
            Text("Waiting for Google sign-in…", fontSize = 22.sp,
                fontWeight = FontWeight.SemiBold, color = TextMain)
            Spacer(Modifier.height(10.dp))
            Text(
                text = "Complete sign-in in your browser.\nThis window will update automatically.",
                fontSize = 15.sp, color = TextMuted, textAlign = TextAlign.Center, lineHeight = 22.sp
            )
            Spacer(Modifier.height(32.dp))
            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(8.dp))
                    .border(1.dp, BorderField, RoundedCornerShape(8.dp))
                    .background(BgCardAlt)
                    .clickable(onClick = onCancel)
                    .padding(horizontal = 24.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = null, tint = TextMuted,
                    modifier = Modifier.size(14.dp))
                Text("Cancel", fontSize = 14.sp, color = TextMain, fontWeight = FontWeight.Medium)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// Inline SVG icons (matching the React component exactly)
// ─────────────────────────────────────────────────────────────────────────────

// ─────────────────────────────────────────────────────────────────────────────
// Google icon — the real four-colour brand "G"
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun GoogleIcon() = GoogleGlyph()

@Composable
private fun GoogleGlyph() {
    // The SVG viewBox is "-3 0 262 262" → width=262, height=262
    // Scale to fit 18dp canvas
    Canvas(modifier = Modifier.size(18.dp)) {
        val s = size.width / 262f
        val ox = 3f * s  // compensate for the -3 viewBox x offset
        drawPath(
            path = androidPath("M255.878 133.451c0-10.734-.871-18.567-2.756-26.69H130.55v48.448h71.947c-1.45 12.04-9.283 30.172-26.69 42.356l-.244 1.622 38.755 30.023 2.685.268c24.659-22.774 38.875-56.282 38.875-96.027", s, ox),
            color = Color(0xFF4285F4)
        )
        drawPath(
            path = androidPath("M130.55 261.1c35.248 0 64.839-11.605 86.453-31.622l-41.196-31.913c-11.024 7.688-25.82 13.055-45.257 13.055-34.523 0-63.824-22.773-74.269-54.25l-1.531.13-40.298 31.187-.527 1.465C35.393 231.798 79.49 261.1 130.55 261.1", s, ox),
            color = Color(0xFF34A853)
        )
        drawPath(
            path = androidPath("M56.281 156.37c-2.756-8.123-4.351-16.827-4.351-25.82 0-8.994 1.595-17.697 4.206-25.82l-.073-1.73L15.26 71.312l-1.335.635C5.077 89.644 0 109.517 0 130.55s5.077 40.905 13.925 58.602l42.356-32.782", s, ox),
            color = Color(0xFFFBBC05)
        )
        drawPath(
            path = androidPath("M130.55 50.479c24.514 0 41.05 10.589 50.479 19.438l36.844-35.974C195.245 12.91 165.798 0 130.55 0 79.49 0 35.393 29.301 13.925 71.947l42.211 32.783c10.59-31.477 39.891-54.251 74.414-54.251", s, ox),
            color = Color(0xFFEB4335)
        )
    }
}

/** Version of androidPath that also applies an X offset for non-zero viewBox origins. */
private fun androidPath(d: String, scale: Float, offsetX: Float): Path {
    val path = androidPath(d, scale)
    path.translate(Offset(offsetX, 0f))
    return path
}

// ─────────────────────────────────────────────────────────────────────────────
// SVG path parsing
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parses an SVG path `d` attribute into a Compose [Path] scaled by [scale].
 *
 * This delegates to Compose's [PathParser], which implements the full SVG path
 * grammar. A hand-rolled tokenizer used to live here and it silently dropped the
 * H/V shorthand (`h`, `H`, `v`, `V`) — the two brand glyphs on this screen use
 * exactly those commands, so they rendered with whole chunks missing.
 */
private fun androidPath(d: String, scale: Float): Path {
    val path = PathParser().parsePathString(d).toPath()
    if (scale != 1f) {
        path.transform(Matrix().apply { scale(scale, scale, 1f) })
    }
    return path
}

// ─────────────────────────────────────────────────────────────────────────────
// Google sign-in — the flow starts on the StackPilot site, not at Google
// ─────────────────────────────────────────────────────────────────────────────

/** Port of the tiny listener that receives the finished session from the site. */
private const val CALLBACK_PORT = 9876

/**
 * Opens the StackPilot site's Google sign-in / sign-up page in the system browser.
 *
 * The browser starts at [AuthConfig.baseUrl] (`https://stackpilotconfig.vercel.app/signin` or
 * `/signup`), which shows the Google button. The site owns the Google client
 * id/secret: it sends the browser to Google, handles the callback at
 * [AuthConfig.redirectUri], and only then posts the finished session to the
 * listener below - so the tool opens already signed in.
 *
 * The `state` value generated here travels through the page, Google and the site
 * back to the listener, so a callback this window did not start is rejected.
 */
private fun openGoogleSignIn(
    scope: kotlinx.coroutines.CoroutineScope,
    intent: String,
    onResult: (AuthResult) -> Unit,
    onError: (String) -> Unit
) {
    val state = java.util.UUID.randomUUID().toString()
    val page = if (intent == "signup") "signup" else "signin"
    val startUrl = "${AuthConfig.baseUrl}/$page?desktop=1&state=$state"

    val server = try {
        com.sun.net.httpserver.HttpServer.create(
            java.net.InetSocketAddress("127.0.0.1", CALLBACK_PORT), 0)
    } catch (e: Exception) { onError("Could not start local auth listener: ${e.message}"); return }

    server.createContext("/done") { exchange ->
        val params = (exchange.requestURI.query ?: "").split("&").associate {
            val kv = it.split("=", limit = 2)
            kv.getOrElse(0) { "" } to java.net.URLDecoder.decode(kv.getOrElse(1) { "" }, "UTF-8")
        }
        val html = callbackPage(ok = !params["token"].isNullOrBlank()).toByteArray(Charsets.UTF_8)
        exchange.responseHeaders.add("Content-Type", "text/html; charset=utf-8")
        exchange.responseHeaders.add("Cache-Control", "no-store")
        exchange.sendResponseHeaders(200, html.size.toLong())
        exchange.responseBody.use { it.write(html) }

        val token    = params["token"]
        val returned = params["state"].orEmpty()
        Thread {
            Thread.sleep(400)
            server.stop(0)
            scope.launch(Dispatchers.Main) {
                when {
                    returned.isNotEmpty() && returned != state ->
                        onError("That sign-in was not started by StackPilot. Please try again.")
                    !token.isNullOrBlank() ->
                        onResult(AuthResult(token = token,
                            userId = params["userId"] ?: "",
                            name   = params["name"]   ?: "",
                            email  = params["email"]  ?: ""))
                    else ->
                        onError(params["error"]?.ifBlank { null } ?: "Google sign-in was cancelled.")
                }
            }
        }.start()
    }
    server.executor = null; server.start()

    // The site has to be up, otherwise the browser only shows a dead tab and this
    // overlay would wait forever, so check before opening anything.
    scope.launch {
        val reachable = withContext(Dispatchers.IO) {
            try {
                java.net.http.HttpClient.newBuilder()
                    .version(java.net.http.HttpClient.Version.HTTP_1_1)
                    .connectTimeout(java.time.Duration.ofSeconds(3))
                    .build()
                    .send(
                        java.net.http.HttpRequest.newBuilder(java.net.URI.create("${AuthConfig.baseUrl}/$page"))
                            .timeout(java.time.Duration.ofSeconds(3))
                            .GET()
                            .build(),
                        java.net.http.HttpResponse.BodyHandlers.discarding()
                    )
                true
            } catch (_: Exception) { false }
        }

        if (!reachable) {
            server.stop(0)
            onError("Cannot reach ${AuthConfig.baseUrl} - start the site (npm run dev) and try again.")
            return@launch
        }

        try { java.awt.Desktop.getDesktop().browse(java.net.URI(startUrl)) }
        catch (e: Exception) { server.stop(0); onError("Cannot open browser: ${e.message}") }
    }
}

/**
 * The page the browser finishes on once the site has handed the session back.
 *
 * Served as UTF-8 with an explicit charset header and a `<meta charset>` tag:
 * the original version wrote raw UTF-8 bytes with neither, so the check mark and
 * the em dash in the copy appeared as "âœ“" and "â€"" on Windows browsers.
 * The app logo is inlined as a data URI so the page needs no network access.
 */
internal fun callbackPage(ok: Boolean): String {
    val logo = loadLogoBytes()
        ?.let { "data:image/png;base64," + java.util.Base64.getEncoder().encodeToString(it) }
        .orEmpty()
    val logoTag = if (logo.isEmpty()) "" else "<img src=\"$logo\" alt=\"StackPilot\">"

    val accent  = if (ok) "#22C55E" else "#F59E0B"
    val icon    = if (ok) "<path d=\"M20 6 9 17l-5-5\"/>"
                  else "<circle cx=\"12\" cy=\"12\" r=\"9\"/><path d=\"M12 8v4\"/><path d=\"M12 16h.01\"/>"
    val title   = if (ok) "You're signed in" else "Sign-in wasn't completed"
    val detail  = if (ok) "Returning to the StackPilot desktop app…"
                  else "Close this tab and try again from the app."

    return """
<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>StackPilot - $title</title>
<style>
  :root { color-scheme: dark; }
  * { box-sizing: border-box; }
  body {
    margin: 0; min-height: 100vh; display: flex; align-items: center; justify-content: center;
    background: #070b12; color: #e2e8f0;
    font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", Roboto, Helvetica, Arial, sans-serif;
  }
  .card {
    width: 100%; max-width: 420px; margin: 24px; padding: 40px 32px; text-align: center;
    background: #0d131f; border: 1px solid rgba(255,255,255,0.08); border-radius: 16px;
    box-shadow: 0 24px 80px -24px rgba(0,0,0,0.9);
  }
  .brand { display: flex; align-items: center; justify-content: center; gap: 10px; margin-bottom: 28px; }
  .brand img { height: 26px; width: auto; display: block; }
  .brand span { font-size: 15px; font-weight: 700; letter-spacing: -0.2px; color: #fff; }
  .badge {
    width: 64px; height: 64px; margin: 0 auto 20px; border-radius: 50%;
    display: flex; align-items: center; justify-content: center;
    background: ${accent}1f; border: 1px solid ${accent}40;
  }
  .badge svg { width: 30px; height: 30px; stroke: $accent; fill: none;
               stroke-width: 2.2; stroke-linecap: round; stroke-linejoin: round; }
  h1 { margin: 0 0 8px; font-size: 21px; font-weight: 600; letter-spacing: -0.4px; color: #fff; }
  p { margin: 0; font-size: 14px; line-height: 1.6; color: #94a3b8; }
  .hint { margin-top: 22px; font-size: 12px; color: #64748b; }
</style>
</head>
<body>
  <div class="card">
    <div class="brand">$logoTag<span>StackPilot</span></div>
    <div class="badge"><svg viewBox="0 0 24 24">$icon</svg></div>
    <h1>$title</h1>
    <p>$detail</p>
    <p class="hint">You can close this tab - the desktop app is already open.</p>
  </div>
  <script>setTimeout(function () { try { window.close(); } catch (e) {} }, 1500);</script>
</body>
</html>
""".trimIndent()
}
