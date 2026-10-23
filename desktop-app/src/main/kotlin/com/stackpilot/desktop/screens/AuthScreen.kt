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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.*
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.stackpilot.desktop.auth.AuthClient
import com.stackpilot.desktop.auth.AuthException
import com.stackpilot.desktop.auth.AuthResult
import com.stackpilot.desktop.auth.AuthScreen
import com.stackpilot.desktop.components.LogoImageBlack
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
                                        } catch (_: Exception)     { errorMessage = "Cannot reach http://localhost:3000" }
                                        finally { busy = false }
                                    }
                                },
                                onGoogle = {
                                    errorMessage = null; busy = true; googleWaiting = true
                                    openGoogleSignIn(scope,
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
                                        } catch (_: Exception)     { errorMessage = "Cannot reach http://localhost:3000" }
                                        finally { busy = false }
                                    }
                                },
                                onGoogle = {
                                    errorMessage = null; busy = true; googleWaiting = true
                                    openGoogleSignIn(scope,
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
        com.stackpilot.desktop.components.LogoImage(size = 40.dp)
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SocialBtn(
                modifier = Modifier.weight(1f),
                icon = { GoogleSvg() },
                label = "Sign in with Google",
                onClick = onGoogle,
                enabled = !busy
            )
            SocialBtn(
                modifier = Modifier.weight(1f),
                icon = { AppleSvg() },
                label = "Sign in with Apple",
                onClick = { /* Apple OAuth */ },
                enabled = !busy
            )
        }

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
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            SocialBtn(Modifier.weight(1f), { GoogleSvg() }, "Sign up with Google", onGoogle, !busy)
            SocialBtn(Modifier.weight(1f), { AppleSvg() }, "Sign up with Apple", {}, !busy)
        }

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
            Text(
                text = "Think fast,\nBuild faster",
                fontSize = 62.sp,
                fontWeight = FontWeight.Medium,
                color = Color.White,
                lineHeight = 60.sp,
                letterSpacing = (-2.5).sp
            )

            Row(
                modifier = Modifier
                    .clip(RoundedCornerShape(10.dp))
                    .border(1.dp, Color(0x40FFFFFF), RoundedCornerShape(10.dp))
                    .background(Color(0x18FFFFFF))
                    .clickable { }
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                WindowsSvg(modifier = Modifier.size(20.dp))
                Text(
                    text = "Download the Windows app",
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    color = PanelTextMuted
                )
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

@Composable
private fun GoogleSvg() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val s = size.width / 24f
        // Blue path
        drawPath(
            path = androidPath("M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09Z", s),
            color = Color(0xFF4285F4)
        )
        drawPath(
            path = androidPath("M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23Z", s),
            color = Color(0xFF34A853)
        )
        drawPath(
            path = androidPath("M5.84 14.1c-.22-.66-.35-1.36-.35-2.1s.13-1.44.35-2.1V7.06H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.94l3.66-2.84Z", s),
            color = Color(0xFFFBBC05)
        )
        drawPath(
            path = androidPath("M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.06l3.66 2.84C6.71 7.3 9.14 5.38 12 5.38Z", s),
            color = Color(0xFFEB4335)
        )
    }
}

@Composable
private fun AppleSvg() {
    Canvas(modifier = Modifier.size(18.dp)) {
        val s = size.width / 24f
        drawPath(
            path = androidPath("M17.05 12.54c-.03-3.02 2.47-4.47 2.58-4.54-1.41-2.06-3.6-2.34-4.38-2.37-1.86-.19-3.64 1.1-4.58 1.1-.95 0-2.42-1.07-3.98-1.04-2.05.03-3.94 1.19-4.99 3.02-2.13 3.69-.54 9.16 1.53 12.15 1.01 1.46 2.22 3.1 3.81 3.04 1.53-.06 2.11-.99 3.96-.99s2.37.99 3.99.96c1.65-.03 2.69-1.49 3.69-2.96 1.16-1.69 1.64-3.33 1.66-3.41-.04-.02-3.2-1.23-3.24-4.87ZM14.03 3.66c.84-1.02 1.41-2.43 1.25-3.84-1.21.05-2.68.81-3.55 1.83-.78.9-1.46 2.34-1.28 3.72 1.35.1 2.73-.69 3.58-1.71Z", s),
            color = TextMain   // white on dark background
        )
    }
}

@Composable
private fun WindowsSvg(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val s = size.width / 24f
        drawPath(
            path = androidPath("M3 4.7 10.7 3.6v7.7H3V4.7Zm8.8-1.25L21 2.1v9.2h-9.2V3.45ZM3 12.7h7.7v7.7L3 19.3v-6.6Zm8.8 0H21v9.2l-9.2-1.3v-7.9Z", s),
            color = Color.White
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SVG path parsing — robust implementation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Parses a subset of SVG path `d` attribute into a Compose [Path].
 * Handles M, L, C, Z/z commands with absolute coordinates.
 * [scale] multiplies every coordinate so a 24×24 viewBox fits the canvas.
 */
private fun androidPath(d: String, scale: Float): Path {
    val path = Path()

    // Tokenise: split on commands OR whitespace/comma between numbers
    // Result is a flat list of strings, each either a single letter or a number.
    val tokens = mutableListOf<String>()
    val sb = StringBuilder()
    for (ch in d.trim()) {
        when {
            ch.isLetter() -> {
                if (sb.isNotEmpty()) { tokens.add(sb.toString()); sb.clear() }
                tokens.add(ch.toString())
            }
            ch == ',' || ch == ' ' || ch == '\t' || ch == '\n' -> {
                if (sb.isNotEmpty()) { tokens.add(sb.toString()); sb.clear() }
            }
            ch == '-' -> {
                // Negative sign starts a new number token
                if (sb.isNotEmpty()) { tokens.add(sb.toString()); sb.clear() }
                sb.append(ch)
            }
            else -> sb.append(ch)
        }
    }
    if (sb.isNotEmpty()) tokens.add(sb.toString())

    // Parse commands
    var i = 0
    fun nextFloat(): Float {
        while (i < tokens.size && tokens[i].length == 1 && tokens[i][0].isLetter()) i++
        return if (i < tokens.size) tokens[i++].toFloatOrNull() ?: 0f else 0f
    }

    while (i < tokens.size) {
        val cmd = tokens[i]
        if (cmd.length != 1 || !cmd[0].isLetter()) { i++; continue }
        i++ // consume command
        when (cmd) {
            "M" -> path.moveTo(nextFloat() * scale, nextFloat() * scale)
            "m" -> path.relativeMoveTo(nextFloat() * scale, nextFloat() * scale)
            "L" -> {
                while (i < tokens.size && (tokens[i].toFloatOrNull() != null || tokens[i].startsWith("-"))) {
                    path.lineTo(nextFloat() * scale, nextFloat() * scale)
                }
            }
            "l" -> {
                while (i < tokens.size && (tokens[i].toFloatOrNull() != null || tokens[i].startsWith("-"))) {
                    path.relativeLineTo(nextFloat() * scale, nextFloat() * scale)
                }
            }
            "H" -> {
                while (i < tokens.size && (tokens[i].toFloatOrNull() != null || tokens[i].startsWith("-"))) {
                    // Horizontal line — need current Y; approximate by using lineTo with same Y via moveTo trick
                    // For our icon paths H is not used, but handle gracefully
                    nextFloat() // consume X, skip (no currentY available here without tracking)
                }
            }
            "C" -> {
                while (i < tokens.size && (tokens[i].toFloatOrNull() != null || tokens[i].startsWith("-"))) {
                    val x1 = nextFloat() * scale; val y1 = nextFloat() * scale
                    val x2 = nextFloat() * scale; val y2 = nextFloat() * scale
                    val x  = nextFloat() * scale; val y  = nextFloat() * scale
                    path.cubicTo(x1, y1, x2, y2, x, y)
                }
            }
            "c" -> {
                while (i < tokens.size && (tokens[i].toFloatOrNull() != null || tokens[i].startsWith("-"))) {
                    val x1 = nextFloat() * scale; val y1 = nextFloat() * scale
                    val x2 = nextFloat() * scale; val y2 = nextFloat() * scale
                    val x  = nextFloat() * scale; val y  = nextFloat() * scale
                    path.relativeCubicTo(x1, y1, x2, y2, x, y)
                }
            }
            "Z", "z" -> path.close()
            else -> {} // skip unknown commands
        }
    }
    return path
}

// ─────────────────────────────────────────────────────────────────────────────
// Google OAuth local-server flow
// ─────────────────────────────────────────────────────────────────────────────

private fun openGoogleSignIn(
    scope: kotlinx.coroutines.CoroutineScope,
    onResult: (AuthResult) -> Unit,
    onError: (String) -> Unit
) {
    val clientId   = "248413993224-7tilgr2bqhia4unv6juq3g4b56f0mbdo.apps.googleusercontent.com"
    val redirectUri = "http://localhost:3000/auth/callback"
    val state = java.util.UUID.randomUUID().toString()

    val authUrl = buildString {
        append("https://accounts.google.com/o/oauth2/v2/auth?")
        append("client_id=${enc(clientId)}&redirect_uri=${enc(redirectUri)}")
        append("&response_type=code&scope=${enc("openid email profile")}")
        append("&state=$state&access_type=offline&prompt=select_account")
    }

    val server = try {
        com.sun.net.httpserver.HttpServer.create(
            java.net.InetSocketAddress("127.0.0.1", 9876), 0)
    } catch (e: Exception) { onError("Could not start local auth listener: ${e.message}"); return }

    server.createContext("/done") { exchange ->
        val params = (exchange.requestURI.query ?: "").split("&").associate {
            val kv = it.split("=", limit = 2)
            kv.getOrElse(0) { "" } to java.net.URLDecoder.decode(kv.getOrElse(1) { "" }, "UTF-8")
        }
        val html = "<html><head><script>window.close()</script></head><body style='background:#070b12;color:#fff;display:flex;align-items:center;justify-content:center;height:100vh;font-family:sans-serif;margin:0'><div style='text-align:center'><h2 style='color:#3b82f6'>✓ Signed in — close this tab</h2></div></body></html>".toByteArray()
        exchange.sendResponseHeaders(200, html.size.toLong())
        exchange.responseBody.use { it.write(html) }

        val token = params["token"]
        Thread {
            Thread.sleep(400)
            server.stop(0)
            scope.launch(Dispatchers.Main) {
                if (!token.isNullOrBlank()) {
                    onResult(AuthResult(token = token,
                        userId = params["userId"] ?: "",
                        name   = params["name"]   ?: "",
                        email  = params["email"]  ?: ""))
                } else {
                    onError(params["error"]?.ifBlank { null } ?: "Google sign-in was cancelled.")
                }
            }
        }.start()
    }
    server.executor = null; server.start()

    try { java.awt.Desktop.getDesktop().browse(java.net.URI(authUrl)) }
    catch (e: Exception) { server.stop(0); onError("Cannot open browser: ${e.message}") }
}

private fun enc(s: String) = java.net.URLEncoder.encode(s, "UTF-8")
