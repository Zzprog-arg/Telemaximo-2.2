package com.example.ui.admin

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.db.DatabaseService
import com.example.db.LoginResult
import com.example.db.UserAccount
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.launch
import java.sql.Timestamp
import java.text.SimpleDateFormat
import java.util.*

class AdminActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            MyApplicationTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = Color(0xFF0F111C)
                ) {
                    AdminAppRoot()
                }
            }
        }
    }
}

@Composable
fun AdminAppRoot() {
    val context = androidx.compose.ui.platform.LocalContext.current
    LaunchedEffect(Unit) {
        DatabaseService.loadSettings(context)
    }
    var loggedInAdmin by remember { mutableStateOf<UserAccount?>(null) }

    if (loggedInAdmin == null) {
        AdminLoginScreen(onSuccess = { loggedInAdmin = it })
    } else {
        AdminDashboardScreen(onLogout = { loggedInAdmin = null })
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminLoginScreen(onSuccess: (UserAccount) -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color(0xFF1E213A),
                        Color(0xFF0D0E15)
                    )
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = Color(0xFF161A29)),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
            shape = RoundedCornerShape(20.dp),
            modifier = Modifier
                .width(360.dp)
                .padding(16.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = "Admin icon",
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(48.dp)
                )
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    text = "IPTV Admin Panel",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Black,
                    color = Color.White
                )
                Text(
                    text = "Control e Inicio de Administrador",
                    fontSize = 12.sp,
                    color = Color.LightGray,
                    modifier = Modifier.padding(top = 4.dp)
                )
                Spacer(modifier = Modifier.height(24.dp))

                OutlinedTextField(
                    value = username,
                    onValueChange = { username = it },
                    label = { Text("Usuario Admin") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(16.dp))

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    visualTransformation = PasswordVisualTransformation(),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White,
                        focusedBorderColor = MaterialTheme.colorScheme.primary,
                        unfocusedBorderColor = Color.Gray
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(20.dp))

                if (errorMessage != null) {
                    Text(
                        text = errorMessage!!,
                        color = Color.Red,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 12.dp)
                    )
                }

                if (isLoading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                } else {
                    Button(
                        onClick = {
                            if (username.isBlank() || password.isBlank()) {
                                errorMessage = "Faltan registrar los campos"
                                return@Button
                            }
                            isLoading = true
                            errorMessage = null
                            scope.launch {
                                // Make sure DB exists
                                DatabaseService.initializeDb()
                                
                                val res = DatabaseService.login(username, password)
                                isLoading = false
                                when (res) {
                                    is LoginResult.Success -> {
                                        if (res.user.isAdmin) {
                                            onSuccess(res.user)
                                        } else {
                                            errorMessage = "Su cuenta no posee rango de Admin"
                                        }
                                    }
                                    is LoginResult.Disabled -> {
                                        errorMessage = "Cuenta desactivada"
                                    }
                                    is LoginResult.Expired -> {
                                        errorMessage = "Cuenta vencida"
                                    }
                                    is LoginResult.WrongCredentials -> {
                                        errorMessage = "Credenciales incorrectas"
                                    }
                                    is LoginResult.Error -> {
                                        errorMessage = res.message
                                    }
                                }
                            }
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(48.dp),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Text("Ingresar al Panel", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

enum class ListFilter {
    Todos, Activos, Vencidos, Desactivados
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdminDashboardScreen(onLogout: () -> Unit) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var allUsers by remember { mutableStateOf<List<UserAccount>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf(ListFilter.Todos) }
    var isLoadingList by remember { mutableStateOf(false) }

    // Dialog state controllers
    var activeActionUser by remember { mutableStateOf<UserAccount?>(null) }
    var showCreateDialog by remember { mutableStateOf(false) }
    var showM3uDialog by remember { mutableStateOf(false) }

    fun refreshUsers() {
        isLoadingList = true
        scope.launch {
            allUsers = DatabaseService.getAllUsers()
            isLoadingList = false
        }
    }

    LaunchedEffect(Unit) {
        refreshUsers()
    }

    // Client-side filtering logics
    val filteredUsers = remember(allUsers, searchQuery, selectedFilter) {
        allUsers.filter { user ->
            // Search query matches username (case-insensitive)
            val matchesSearch = user.username.contains(searchQuery, ignoreCase = true)
            
            // Check expiry state
            val now = Date()
            val isExpired = user.expiresAt != null && user.expiresAt.before(now)
            val isActive = user.estado == "active" && !isExpired
            val isDisabled = user.estado == "disabled"

            val matchesFilter = when (selectedFilter) {
                ListFilter.Todos -> true
                ListFilter.Activos -> isActive
                ListFilter.Vencidos -> isExpired
                ListFilter.Desactivados -> isDisabled
            }
            matchesSearch && matchesFilter
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Consola Railway Admin", fontWeight = FontWeight.Black) },
                actions = {
                    IconButton(onClick = { showM3uDialog = true }) {
                        Icon(imageVector = Icons.Default.Settings, contentDescription = "Configuración M3U", tint = MaterialTheme.colorScheme.primary)
                    }
                    IconButton(onClick = { refreshUsers() }) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = "Refresh list")
                    }
                    IconButton(onClick = onLogout) {
                        Icon(imageVector = Icons.Default.ExitToApp, contentDescription = "Log out")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF131622),
                    titleContentColor = Color.White,
                    actionIconContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showCreateDialog = true },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.Black
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = "Create account")
            }
        },
        containerColor = Color(0xFF0F111C)
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
        ) {
            // Metrics Header Row overview
            MetricsRow(allUsers)

            Spacer(modifier = Modifier.height(14.dp))

            // Search text field
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Buscar cuenta por nombre...", color = Color.Gray) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search", tint = Color.Gray) },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedTextColor = Color.White,
                    unfocusedTextColor = Color.White,
                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                    unfocusedBorderColor = Color.Gray
                ),
                modifier = Modifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Visual filter tabs Row (Todos, Activos, Vencidos, Desactivados)
            ScrollableTabRow(
                selectedTabIndex = selectedFilter.ordinal,
                containerColor = Color.Transparent,
                divider = {},
                indicator = {}
            ) {
                ListFilter.values().forEach { filter ->
                    val isSelected = selectedFilter == filter
                    Tab(
                        selected = isSelected,
                        onClick = { selectedFilter = filter },
                        modifier = Modifier
                            .padding(end = 8.dp)
                            .background(
                                color = if (isSelected) MaterialTheme.colorScheme.primary else Color(0xFF1A1D2E),
                                shape = RoundedCornerShape(18.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 6.dp)
                    ) {
                        Text(
                            text = filter.name,
                            color = if (isSelected) Color.Black else Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (isLoadingList) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                }
            } else if (filteredUsers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text("No se encontraron cuentas", color = Color.Gray, fontSize = 14.sp)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    items(filteredUsers) { user ->
                        UserCard(user = user, onClick = { activeActionUser = user })
                    }
                }
            }
        }
    }

    // Modal dialog for creation
    if (showCreateDialog) {
        CreateUserDialog(
            onDismiss = { showCreateDialog = false },
            onConfirm = { username, password, status, months, maxDevices, isAdmin ->
                scope.launch {
                    val cal = Calendar.getInstance()
                    cal.add(Calendar.MONTH, months)
                    val expiry = Timestamp(cal.timeInMillis)

                    val newUser = UserAccount(
                        id = 0,
                        username = username,
                        password = password,
                        estado = status,
                        expiresAt = expiry,
                        maxDevices = maxDevices,
                        lastLogin = null,
                        isAdmin = isAdmin,
                        createdAt = null,
                        updatedAt = null
                    )
                    val success = DatabaseService.createUser(newUser)
                    if (success) {
                        Toast.makeText(context, "Cuenta creada exitosamente", Toast.LENGTH_SHORT).show()
                        showCreateDialog = false
                        refreshUsers()
                    } else {
                        Toast.makeText(context, "El usuario ya existe en la base de datos", Toast.LENGTH_LONG).show()
                    }
                }
            }
        )
    }

    // Modal dialog for single user edits (toggle, months, devices, edit pass, delete)
    if (activeActionUser != null) {
        val user = activeActionUser!!
        ManageUserDialog(
            user = user,
            onDismiss = { activeActionUser = null },
            onRefresh = {
                activeActionUser = null
                refreshUsers()
            }
        )
    }

    if (showM3uDialog) {
        M3uConfigDialog(
            onDismiss = { showM3uDialog = false }
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun M3uConfigDialog(
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var m3uUrl by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var isSaving by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        m3uUrl = DatabaseService.getM3uUrl(context)
        isLoading = false
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("⚙️ Configuración M3U", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
            }
        },
        text = {
            Column {
                Text(
                    text = "Enlace de lista IPTV (.m3u) activo para todos los usuarios:",
                    color = Color.LightGray,
                    fontSize = 13.sp,
                    modifier = Modifier.padding(bottom = 12.dp)
                )

                if (isLoading) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                    }
                } else {
                    OutlinedTextField(
                        value = m3uUrl,
                        onValueChange = { m3uUrl = it },
                        label = { Text("Enlace M3U") },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = MaterialTheme.colorScheme.primary,
                            unfocusedBorderColor = Color.Gray
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            if (!isLoading) {
                Button(
                    onClick = {
                        if (m3uUrl.isBlank()) {
                            Toast.makeText(context, "El enlace M3U no puede estar vacío", Toast.LENGTH_SHORT).show()
                            return@Button
                        }
                        isSaving = true
                        scope.launch {
                            val success = DatabaseService.updateM3uUrl(context, m3uUrl.trim())
                            isSaving = false
                            if (success) {
                                Toast.makeText(context, "Enlace M3U actualizado con éxito", Toast.LENGTH_SHORT).show()
                                onDismiss()
                            } else {
                                Toast.makeText(context, "Error al actualizar. Verifique su base de datos", Toast.LENGTH_LONG).show()
                            }
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary),
                    enabled = !isSaving
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), color = Color.Black)
                    } else {
                        Text("Guardar Enlace", color = Color.Black, fontWeight = FontWeight.Bold)
                    }
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = !isSaving) {
                Text("Cancelar", color = Color.LightGray)
            }
        },
        containerColor = Color(0xFF161A29),
        shape = RoundedCornerShape(16.dp)
    )
}

@Composable
fun MetricsRow(users: List<UserAccount>) {
    val now = Date()
    val total = users.size
    var activeCount = 0
    var expiredCount = 0
    var disabledCount = 0

    users.forEach { u ->
        val isExpired = u.expiresAt != null && u.expiresAt.before(now)
        if (u.estado == "disabled") {
            disabledCount++
        } else if (isExpired) {
            expiredCount++
        } else {
            activeCount++
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        MetricItem("Total", total.toString(), Color.White, Modifier.weight(1f))
        MetricItem("Activos", activeCount.toString(), Color(0xFF4CAF50), Modifier.weight(1f))
        MetricItem("Vencidos", expiredCount.toString(), Color(0xFFE91E63), Modifier.weight(1f))
        MetricItem("Baja", disabledCount.toString(), Color(0xFF9E9E9E), Modifier.weight(1f))
    }
}

@Composable
fun MetricItem(label: String, value: String, color: Color, modifier: Modifier = Modifier) {
    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A29)),
        shape = RoundedCornerShape(12.dp),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(label, fontSize = 11.sp, color = Color.Gray, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, color = color, fontWeight = FontWeight.Black)
        }
    }
}

@Composable
fun UserCard(user: UserAccount, onClick: () -> Unit) {
    val now = Date()
    val isExpired = user.expiresAt != null && user.expiresAt.before(now)
    
    val badgeColor = when {
        user.estado == "disabled" -> Color.Gray
        isExpired -> Color(0xFFE91E63)
        else -> Color(0xFF4CAF50)
    }
    
    val badgeText = when {
        user.estado == "disabled" -> "Desactivado"
        isExpired -> "Vencido"
        else -> "Activo"
    }

    val format = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    val dateText = user.expiresAt?.let { format.format(it) } ?: "Eterno"
    val loginText = user.lastLogin?.let { format.format(it) } ?: "Nunca"

    Card(
        colors = CardDefaults.cardColors(containerColor = Color(0xFF161A29)),
        shape = RoundedCornerShape(14.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = user.username,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (user.isAdmin) {
                        Spacer(modifier = Modifier.width(6.dp))
                        Box(
                            modifier = Modifier
                                .background(Color(0xFFFF9800), shape = RoundedCornerShape(4.dp))
                                .padding(horizontal = 4.dp, vertical = 2.dp)
                        ) {
                            Text("ADMIN", fontSize = 9.sp, color = Color.Black, fontWeight = FontWeight.Bold)
                        }
                    }
                }
                
                Spacer(modifier = Modifier.height(6.dp))
                
                Text(
                    text = "Vence: $dateText",
                    fontSize = 12.sp,
                    color = Color.LightGray
                )
                
                Text(
                    text = "Último Acceso: $loginText",
                    fontSize = 11.sp,
                    color = Color.Gray
                )

                Text(
                    text = "Límite Dispositivos: ${user.maxDevices}",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    fontWeight = FontWeight.Bold
                )
            }

            Column(horizontalAlignment = Alignment.End) {
                Box(
                    modifier = Modifier
                        .background(badgeColor.copy(alpha = 0.15f), shape = RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp)
                ) {
                    Text(badgeText, color = badgeColor, fontSize = 11.sp, fontWeight = FontWeight.Black)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Icon(
                    imageVector = Icons.Default.Edit,
                    contentDescription = "Edit account",
                    tint = Color.Gray,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
fun CreateUserDialog(
    onDismiss: () -> Unit,
    onConfirm: (username: String, secret: String, status: String, months: Int, maxDev: Int, isAdm: Boolean) -> Unit
) {
    var uname by remember { mutableStateOf("") }
    var pass by remember { mutableStateOf("") }
    var isAdm by remember { mutableStateOf(false) }
    var selectedMonths by remember { mutableStateOf(1) }
    var maxDev by remember { mutableStateOf(1) }
    var isEnabled by remember { mutableStateOf(true) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva Cuenta IPTV", color = Color.White) },
        containerColor = Color(0xFF1A1D2E),
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = uname,
                    onValueChange = { uname = it },
                    label = { Text("Nombre de Usuario") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = pass,
                    onValueChange = { pass = it },
                    label = { Text("Contraseña") },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                    modifier = Modifier.fillMaxWidth()
                )

                // Months row quick selection
                Column {
                    Text("Período Inicial de Activación:", fontSize = 12.sp, color = Color.LightGray)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf(1, 3, 6, 12).forEach { m ->
                            val active = selectedMonths == m
                            Button(
                                onClick = { selectedMonths = m },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (active) MaterialTheme.colorScheme.primary else Color(0xFF262C44)
                                ),
                                contentPadding = PaddingValues(horizontal = 8.dp),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("$m Mes${if (m>1) "es" else ""}", fontSize = 11.sp, color = if (active) Color.Black else Color.White)
                            }
                        }
                    }
                }

                // Max devices row limit
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text("Límite Dispositivos:", fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (maxDev > 1) maxDev-- }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease", tint = Color.White)
                        }
                        Text(maxDev.toString(), color = Color.White, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 8.dp))
                        IconButton(onClick = { maxDev++ }) {
                            Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase", tint = Color.White)
                        }
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = isAdm,
                        onCheckedChange = { isAdm = it },
                        colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                    )
                    Text("Rango Administrador", color = Color.White, fontSize = 13.sp)
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (uname.isNotBlank() && pass.isNotBlank()) {
                        onConfirm(uname, pass, if (isEnabled) "active" else "disabled", selectedMonths, maxDev, isAdm)
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Crear", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancelar", color = Color.Gray)
            }
        }
    )
}

@Composable
fun ManageUserDialog(
    user: UserAccount,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var editUsername by remember { mutableStateOf(user.username) }
    var editPassword by remember { mutableStateOf(user.password) }
    var editMaxDevices by remember { mutableStateOf(user.maxDevices) }
    var editEstado by remember { mutableStateOf(user.estado) }
    var editIsAdmin by remember { mutableStateOf(user.isAdmin) }

    var isDeleting by remember { mutableStateOf(false) }

    val formatter = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Administrar: ${user.username}",
                color = Color.White,
                fontWeight = FontWeight.Black
            )
        },
        containerColor = Color(0xFF161A29),
        text = {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                item {
                    OutlinedTextField(
                        value = editUsername,
                        onValueChange = { editUsername = it },
                        label = { Text("Nombre de Usuario") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                item {
                    OutlinedTextField(
                        value = editPassword,
                        onValueChange = { editPassword = it },
                        label = { Text("Contraseña") },
                        singleLine = true,
                        colors = OutlinedTextFieldDefaults.colors(focusedTextColor = Color.White, unfocusedTextColor = Color.White),
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                // Subscription renewal section with standard rule calculation displayed live
                item {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF22263B)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(
                                text = "Línea de Vencimiento de Suscripción:",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = user.expiresAt?.let { formatter.format(it) } ?: "Ilimitado",
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Black
                            )
                            
                            Spacer(modifier = Modifier.height(10.dp))
                            
                            Text("Añadir / Restar Meses de Licencia:", fontSize = 11.sp, color = Color.LightGray)
                            Spacer(modifier = Modifier.height(6.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(1, 3, 6).forEach { months ->
                                    Button(
                                        onClick = {
                                            scope.launch {
                                                val success = DatabaseService.changeSubscriptionDuration(user.id, months)
                                                if (success) {
                                                    Toast.makeText(context, "Sumados $months meses", Toast.LENGTH_SHORT).show()
                                                    onRefresh()
                                                }
                                            }
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                        contentPadding = PaddingValues(horizontal = 4.dp),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("+$months Mes", fontSize = 11.sp, color = Color.White)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Button(
                                    onClick = {
                                        scope.launch {
                                            val success = DatabaseService.changeSubscriptionDuration(user.id, -1)
                                            if (success) {
                                                Toast.makeText(context, "Descontado 1 mes", Toast.LENGTH_SHORT).show()
                                                onRefresh()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("-1 Mes", fontSize = 11.sp, color = Color.White)
                                }

                                Button(
                                    onClick = {
                                        scope.launch {
                                            val success = DatabaseService.changeSubscriptionDuration(user.id, -3)
                                            if (success) {
                                                Toast.makeText(context, "Descontados 3 meses", Toast.LENGTH_SHORT).show()
                                                onRefresh()
                                            }
                                        }
                                    },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                                    contentPadding = PaddingValues(horizontal = 4.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("-3 Meses", fontSize = 11.sp, color = Color.White)
                                }
                            }
                        }
                    }
                }

                // Max devices edit
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Permitir Dispositivos:", fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { if (editMaxDevices > 1) editMaxDevices-- }) {
                                Icon(Icons.Default.KeyboardArrowDown, contentDescription = "Decrease", tint = Color.White)
                            }
                            Text(editMaxDevices.toString(), color = Color.White, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { editMaxDevices++ }) {
                                Icon(Icons.Default.KeyboardArrowUp, contentDescription = "Increase", tint = Color.White)
                            }
                        }
                    }
                }

                // Account enabled/disabled checkbox
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Estado Cuenta Activa:", fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
                        Switch(
                            checked = editEstado == "active",
                            onCheckedChange = { editEstado = if (it) "active" else "disabled" },
                            colors = SwitchDefaults.colors(checkedThumbColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                // Admin privileges checkbox
                item {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                        Text("Rango Administrador:", fontSize = 13.sp, color = Color.White, modifier = Modifier.weight(1f))
                        Checkbox(
                            checked = editIsAdmin,
                            onCheckedChange = { editIsAdmin = it },
                            colors = CheckboxDefaults.colors(checkedColor = MaterialTheme.colorScheme.primary)
                        )
                    }
                }

                // Confirm Action and delete buttons
                item {
                    Spacer(modifier = Modifier.height(14.dp))
                    
                    if (isDeleting) {
                        Text("¿Confirmar eliminación absoluta?", color = Color.Red, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Button(
                                onClick = {
                                    scope.launch {
                                        val ok = DatabaseService.deleteUser(user.id)
                                        if (ok) {
                                            Toast.makeText(context, "Cuenta eliminada", Toast.LENGTH_SHORT).show()
                                            onRefresh()
                                        }
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Sí, borrar", color = Color.White)
                            }
                            Button(
                                onClick = { isDeleting = false },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Gray),
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Cancelar", color = Color.White)
                            }
                        }
                    } else {
                        Button(
                            onClick = { isDeleting = true },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0x33FF0000)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Eliminar Cuenta Definitivamente", color = Color.Red, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    scope.launch {
                        val updated = user.copy(
                            username = editUsername,
                            password = editPassword,
                            maxDevices = editMaxDevices,
                            estado = editEstado,
                            isAdmin = editIsAdmin
                        )
                        val ok = DatabaseService.updateUser(updated)
                        if (ok) {
                            Toast.makeText(context, "Cuenta editada exitosamente", Toast.LENGTH_SHORT).show()
                            onRefresh()
                        }
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("Guardar Cambios", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cerrar", color = Color.Gray)
            }
        }
    )
}
