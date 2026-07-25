package com.example.ui.screens

import android.content.Context
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.layout.ContentScale
import com.example.R
import com.example.db.DatabaseService
import com.example.db.LoginResult
import com.example.db.UserAccount
import kotlinx.coroutines.launch

@Composable
fun LoginScreen(
    onLoginSuccess: (UserAccount) -> Unit
) {
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRemembered by remember { mutableStateOf(true) }
    
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    // Focus state checks for TV borders
    var isUserFocused by remember { mutableStateOf(false) }
    var isPassFocused by remember { mutableStateOf(false) }
    var isBtnFocused by remember { mutableStateOf(false) }

    // External DB settings panel states
    var showConfigDialog by remember { mutableStateOf(false) }
    var tempMode by remember { mutableStateOf("supabase") }
    var tempHost by remember { mutableStateOf("") }
    var tempPort by remember { mutableStateOf("") }
    var tempUser by remember { mutableStateOf("") }
    var tempPass by remember { mutableStateOf("") }
    var tempDb by remember { mutableStateOf("") }
    var tempSupabaseUrl by remember { mutableStateOf("") }
    var tempSupabaseKey by remember { mutableStateOf("") }

    fun openConfigDialog() {
        val prefs = context.getSharedPreferences("iptv_db_prefs", Context.MODE_PRIVATE)
        tempMode = prefs.getString("db_mode", "supabase") ?: "supabase"
        tempHost = prefs.getString("db_host", "zephyr.proxy.rlwy.net") ?: "zephyr.proxy.rlwy.net"
        tempPort = prefs.getString("db_port", "45569") ?: "45569"
        tempUser = prefs.getString("db_user", "root") ?: "root"
        tempPass = prefs.getString("db_pass", "FLUnZYlYdxCwDBjmiJZyMnLywkTLgeJG") ?: "FLUnZYlYdxCwDBjmiJZyMnLywkTLgeJG"
        tempDb = prefs.getString("db_name", "railway") ?: "railway"
        tempSupabaseUrl = prefs.getString("supabase_url", "https://kcwkbobwcnifkejnamsa.supabase.co") ?: "https://kcwkbobwcnifkejnamsa.supabase.co"
        tempSupabaseKey = prefs.getString("supabase_key", "sb_publishable_TfMAybeB4n5TJFoUIKjRyQ_Fc0wI9ZL") ?: "sb_publishable_TfMAybeB4n5TJFoUIKjRyQ_Fc0wI9ZL"
        showConfigDialog = true
    }

    val sharedPrefs = remember {
        context.getSharedPreferences("iptv_session_prefs", Context.MODE_PRIVATE)
    }

    // Try automatic session restore or autofill from local cached credentials
    LaunchedEffect(Unit) {
        try {
            // Load custom MySQL credentials if saved
            DatabaseService.loadSettings(context)
            
            // Initiate DB create tables/seed asynchronously on separate thread
            DatabaseService.initializeDb()
            
            val savedUser = sharedPrefs.getString("saved_username", null)
            val savedPass = sharedPrefs.getString("saved_password", null)
            
            if (!savedUser.isNullOrEmpty() && !savedPass.isNullOrEmpty()) {
                username = savedUser
                password = savedPass
                
                // Dry login dynamically in background
                isLoading = true
                val res = DatabaseService.login(savedUser, savedPass)
                isLoading = false
                
                when (res) {
                    is LoginResult.Success -> {
                        onLoginSuccess(res.user)
                    }
                    is LoginResult.Disabled -> {
                        errorMessage = "Cuenta desactivada"
                    }
                    is LoginResult.Expired -> {
                        errorMessage = "Cuenta vencida, contacte al administrador"
                    }
                    is LoginResult.WrongCredentials -> {
                        errorMessage = "Credenciales incorrectas"
                    }
                    is LoginResult.Error -> {
                        errorMessage = res.message
                    }
                }
            }
        } catch (t: Throwable) {
            isLoading = false
            errorMessage = "Error al iniciar: ${t.localizedMessage ?: "Consulte su red"}"
        }
    }

    fun executeLogin() {
        if (username.isBlank() || password.isBlank()) {
            errorMessage = "Complete todos los campos"
            return
        }
        
        isLoading = true
        errorMessage = null
        
        scope.launch {
            try {
                val res = DatabaseService.login(username, password)
                isLoading = false
                
                when (res) {
                    is LoginResult.Success -> {
                        if (isRemembered) {
                            sharedPrefs.edit()
                                .putString("saved_username", username.trim())
                                .putString("saved_password", password)
                                .apply()
                        } else {
                            sharedPrefs.edit().clear().apply()
                        }
                        onLoginSuccess(res.user)
                    }
                    is LoginResult.Disabled -> {
                        errorMessage = "Cuenta desactivada"
                    }
                    is LoginResult.Expired -> {
                        errorMessage = "Cuenta vencida, contacte al administrador"
                    }
                    is LoginResult.WrongCredentials -> {
                        errorMessage = "Usuario o contraseña incorrectos"
                    }
                    is LoginResult.Error -> {
                        errorMessage = res.message
                    }
                }
            } catch (t: Throwable) {
                isLoading = false
                errorMessage = "Error de red/servidor: ${t.localizedMessage ?: "Verifique conexión"}"
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF0F111A),
                        Color(0xFF07080C)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        // High fidelity styled Android TV login card panel
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .width(420.dp)
                .background(Color(0xFF131622), shape = RoundedCornerShape(16.dp))
                .border(
                    width = 1.dp,
                    color = Color.White.copy(alpha = 0.1f),
                    shape = RoundedCornerShape(16.dp)
                )
                .padding(32.dp)
        ) {
            Image(
                painter = painterResource(id = R.drawable.img_telemaximo_banner),
                contentDescription = "TelemaXimo Logo",
                modifier = Modifier
                    .fillMaxWidth()
                    .height(90.dp)
                    .padding(bottom = 12.dp),
                contentScale = ContentScale.Fit
            )

            Text(
                text = "Inicie sesión con su cuenta para sintonizar canales",
                color = Color.Gray,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(bottom = 24.dp)
            )

            // Username input
            OutlinedTextField(
                value = username,
                onValueChange = { username = it },
                label = { Text("Usuario", color = if (isUserFocused) MaterialTheme.colorScheme.primary else Color.Gray) },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color(0xFF171B2B),
                    unfocusedContainerColor = Color(0xFF171B2B)
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Next
                ),
                keyboardActions = KeyboardActions(
                    onNext = { focusManager.moveFocus(FocusDirection.Down) }
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 16.dp)
                    .onFocusChanged { isUserFocused = it.isFocused }
            )

            // Password input
            OutlinedTextField(
                value = password,
                onValueChange = { password = it },
                label = { Text("Contraseña", color = if (isPassFocused) MaterialTheme.colorScheme.primary else Color.Gray) },
                visualTransformation = PasswordVisualTransformation(),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.White.copy(alpha = 0.15f),
                    focusedContainerColor = Color(0xFF171B2B),
                    unfocusedContainerColor = Color(0xFF171B2B)
                ),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { executeLogin() }
                ),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 20.dp)
                    .onFocusChanged { isPassFocused = it.isFocused }
            )

            // Error Message UI Feedback
            if (errorMessage != null) {
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = Color(0xFF381414)
                    ),
                    shape = RoundedCornerShape(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp)
                ) {
                    Text(
                        text = errorMessage!!,
                        color = Color(0xFFF78B8B),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp)
                    )
                }
            }

            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(32.dp),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Button(
                    onClick = { executeLogin() },
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isBtnFocused) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.primary.copy(alpha = 0.8f),
                        contentColor = Color.Black
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                        .onFocusChanged { isBtnFocused = it.isFocused }
                ) {
                    Text(
                        text = "Iniciar Sesión",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp
                    )
                }
            }
        }

        // External SQL settings configuration dialog
        if (showConfigDialog) {
            AlertDialog(
                onDismissRequest = { showConfigDialog = false },
                title = {
                    Text(
                        text = "Configurar Origen de Datos BD",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 18.sp
                    )
                },
                text = {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            text = "Seleccione cómo la aplicación gestionará las cuentas y accesos. El modo Supabase API es altamente recomendado como nube REST estable por puerto 443.",
                            color = Color.LightGray,
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )

                        Text(
                            text = "Orígenes de Datos:",
                            color = Color.White,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Bold
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Button(
                                onClick = { tempMode = "local" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (tempMode == "local") MaterialTheme.colorScheme.primary else Color(0xFF23283B),
                                    contentColor = if (tempMode == "local") Color.Black else Color.White
                                ),
                                modifier = Modifier.weight(1f),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                Text("📁 Local", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { tempMode = "supabase" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (tempMode == "supabase") MaterialTheme.colorScheme.primary else Color(0xFF23283B),
                                    contentColor = if (tempMode == "supabase") Color.Black else Color.White
                                ),
                                modifier = Modifier.weight(1.3f),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                Text("⚡ Supabase API", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                            Button(
                                onClick = { tempMode = "remote" },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (tempMode == "remote") MaterialTheme.colorScheme.primary else Color(0xFF23283B),
                                    contentColor = if (tempMode == "remote") Color.Black else Color.White
                                ),
                                modifier = Modifier.weight(1.1f),
                                contentPadding = PaddingValues(horizontal = 2.dp)
                            ) {
                                Text("🛢️ MySQL", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                            }
                        }

                        if (tempMode == "supabase") {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                            
                            Text(
                                text = "Credenciales de API Supabase:",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = tempSupabaseUrl,
                                onValueChange = { tempSupabaseUrl = it },
                                label = { Text("URL de Proyecto Supabase") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = tempSupabaseKey,
                                onValueChange = { tempSupabaseKey = it },
                                label = { Text("Supabase Key (Anon o Public)") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        if (tempMode == "remote") {
                            HorizontalDivider(color = Color.White.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 4.dp))
                            
                            Text(
                                text = "Credenciales MySQL Externas:",
                                color = Color.White,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Bold
                            )

                            OutlinedTextField(
                                value = tempHost,
                                onValueChange = { tempHost = it },
                                label = { Text("Host MySQL") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = tempPort,
                                onValueChange = { tempPort = it },
                                label = { Text("Puerto MySQL") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = tempUser,
                                onValueChange = { tempUser = it },
                                label = { Text("Usuario MySQL") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = tempPass,
                                onValueChange = { tempPass = it },
                                label = { Text("Contraseña MySQL") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = tempDb,
                                onValueChange = { tempDb = it },
                                label = { Text("Base de Datos") },
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedTextColor = Color.White,
                                    unfocusedTextColor = Color.White,
                                    focusedBorderColor = MaterialTheme.colorScheme.primary
                                ),
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            if (tempMode == "supabase") {
                                DatabaseService.saveSupabaseSettings(context, tempSupabaseUrl, tempSupabaseKey)
                            } else {
                                DatabaseService.saveSettings(context, tempHost, tempPort, tempUser, tempPass, tempDb)
                            }
                            DatabaseService.saveDbMode(context, tempMode)
                            showConfigDialog = false
                            
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                try {
                                    val ok = DatabaseService.initializeDb()
                                    isLoading = false
                                    if (ok) {
                                        errorMessage = when (tempMode) {
                                            "local" -> "📁 Base de datos local activa (SQLite)"
                                            "supabase" -> "⚡ Conectado con API Supabase con éxito y listo para usar"
                                            else -> "🌐 Conectado con MySQL en Railway con éxito"
                                        }
                                    } else {
                                        errorMessage = when (tempMode) {
                                            "local" -> "⚠️ Error al inicializar DB SQLite local."
                                            "supabase" -> "⚠️ No se pudo conectar a Supabase o la tabla 'users' no existe en el esquema. Verifique el SQL editor."
                                            else -> "⚠️ No se pudo conectar a MySQL. Verifique puerto expuesto en Railway."
                                        }
                                    }
                                } catch (t: Throwable) {
                                    isLoading = false
                                    errorMessage = "Error al conectar: ${t.localizedMessage}"
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Text("Guardar", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showConfigDialog = false }) {
                        Text("Cancelar", color = Color.Gray)
                    }
                },
                containerColor = Color(0xFF181B26),
                textContentColor = Color.White,
                titleContentColor = Color.White
            )
        }
    }
}
