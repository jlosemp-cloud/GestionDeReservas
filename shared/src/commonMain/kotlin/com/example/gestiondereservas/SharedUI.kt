package com.example.gestiondereservas

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.*
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.launch
import kotlinx.datetime.Clock
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.plus

@Composable
fun MainNavigationContainer(actions: PlatformActions) {
    var currentTab by remember { mutableIntStateOf(0) }
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)
    
    val customers = remember { mutableStateListOf<Customer>() }
    val appointments = remember { mutableStateListOf<Appointment>() }
    val vacations = remember { mutableStateOf(setOf<String>()) }
    var isLoading by remember { mutableStateOf(true) }
    var statusMsg by remember { mutableStateOf("Conectando...") }

    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val tomorrow = today.plus(1, DateTimeUnit.DAY)
    val tomorrowStr = tomorrow.toString()

    val pendingRemindersCount by remember {
        derivedStateOf {
            appointments.count { it.date == tomorrowStr && !it.reminder_sent && it.client_name != "BLOQUEADO" }
        }
    }

    suspend fun loadData() {
        try {
            statusMsg = "Sincronizando..."
            val aRes = try { supabase.from("appointments").select().decodeList<Appointment>() } catch(e: Exception) { emptyList() }
            val cRes = try { supabase.from("customers").select().decodeList<Customer>() } catch(e: Exception) { emptyList() }
            val vRes = try { supabase.from("vacations").select().decodeList<Vacation>() } catch(e: Exception) { emptyList() }
            
            customers.clear(); customers.addAll(cRes)
            appointments.clear(); appointments.addAll(aRes)
            vacations.value = vRes.mapNotNull { it.date }.toSet()
            
            statusMsg = if (appointments.isEmpty() && customers.isEmpty()) "Base de datos vacía 📭" else "Conectado ✅"
        } catch (e: Exception) {
            statusMsg = "Error de conexión"
        }
    }

    LaunchedEffect(Unit) {
        isLoading = true
        loadData()
        isLoading = false
        try {
            val channel = supabase.channel("changes")
            scope.launch { channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "appointments" }.collect { loadData() } }
            scope.launch { channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "customers" }.collect { loadData() } }
            scope.launch { channel.postgresChangeFlow<PostgresAction>(schema = "public") { table = "vacations" }.collect { loadData() } }
            channel.subscribe()
        } catch (e: Exception) { }
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = Color.White,
                modifier = Modifier.width(320.dp)
            ) {
                val scrollState = rememberScrollState()
                Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(scrollState)) {
                    Text("Guía de Ayuda", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E))
                    Spacer(Modifier.height(8.dp))
                    Text("Resumen práctico de uso", color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    
                    HelpSection("📅 Mi Agenda", "Citas de hoy. Toca el teléfono para llamar o el chat para WhatsApp.")
                    HelpSection("🔔 Avisos", "Recordatorios próximos. Mañana se resalta en rosa: pulsa la campana azul para avisar y cambiará a verde.")
                    HelpSection("📆 Gestión", "Control del tiempo. Toca un día para bloquear horas, anotar citas manuales o marcar festivos.")
                    HelpSection("👥 Fichas", "Tus clientes. Pulsa el botón verde para importar contactos directamente desde tu agenda.")
                    
                    Spacer(Modifier.height(40.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    Text("jlosemp(C) 2026", 
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center,
                        fontWeight = FontWeight.Bold,
                        color = Color(0xFF1A237E).copy(alpha = 0.6f),
                        fontSize = 14.sp
                    )
                }
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                    NavigationBarItem(selected = currentTab == 0, onClick = { currentTab = 0 }, icon = { Icon(Icons.Default.Today, null) }, label = { Text("Hoy") })
                    NavigationBarItem(
                        selected = currentTab == 1, 
                        onClick = { currentTab = 1 }, 
                        icon = { 
                            BadgedBox(badge = { if (pendingRemindersCount > 0) Badge { Text(pendingRemindersCount.toString()) } }) {
                                Icon(Icons.Default.NotificationsActive, null) 
                            }
                        }, 
                        label = { Text("Avisos") }
                    )
                    NavigationBarItem(selected = currentTab == 2, onClick = { currentTab = 2 }, icon = { Icon(Icons.Default.DateRange, null) }, label = { Text("Gestión") })
                    NavigationBarItem(selected = currentTab == 3, onClick = { currentTab = 3 }, icon = { Icon(Icons.Default.People, null) }, label = { Text("Fichas") })
                }
            }
        ) { innerPadding ->
            Column(Modifier.padding(innerPadding)) {
                Surface(color = if (statusMsg.contains("Error")) Color(0xFFFFEBEE) else Color(0xFFE8EAF6), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(statusMsg, fontSize = 11.sp, color = if (statusMsg.contains("Error")) Color.Red else Color.DarkGray)
                        Spacer(Modifier.width(8.dp))
                        Text("Reintentar", Modifier.clickable { scope.launch { loadData() } }, fontSize = 11.sp, color = Color(0xFF1A237E), fontWeight = FontWeight.Bold)
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    if (isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF1A237E)) }
                    } else {
                        when (currentTab) {
                            0 -> AgendaScreen(appointments, actions, onOpenDrawer = { scope.launch { drawerState.open() } })
                            1 -> UpcomingAppointmentsScreen(appointments, actions, onRefresh = { scope.launch { loadData() } }, onOpenDrawer = { scope.launch { drawerState.open() } })
                            2 -> CalendarManagementScreen(vacations, appointments, actions, onRefresh = { scope.launch { loadData() } }, onOpenDrawer = { scope.launch { drawerState.open() } })
                            3 -> CustomerDatabaseScreen(customers, appointments, actions, onRefresh = { scope.launch { loadData() } }, onOpenDrawer = { scope.launch { drawerState.open() } })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HelpSection(title: String, text: String) {
    Column(Modifier.padding(vertical = 10.dp)) {
        Text(title, fontWeight = FontWeight.Bold, color = Color(0xFF311B92), fontSize = 16.sp)
        Spacer(Modifier.height(4.dp))
        Text(text, color = Color.DarkGray, fontSize = 14.sp, lineHeight = 20.sp)
    }
}

@Composable
fun HeaderPremium(title: String, subtitle: String, actions: PlatformActions, onMenuClick: (() -> Unit)? = null) {
    val msgLink = "╔══════════════════════╗\n" +
            "  *Jesús, Peluquería de Autor*  \n" +
            "╚══════════════════════╝\n" +
            "\n" +
            "Reserva online pulsando en el botón visual de abajo:\n" +
            " \n" +
            "      [ 📲 PULSA AQUÍ ]      \n" +
            " \n" +
            MI_WEB_RESERVAS

    Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(Color(0xFF1A237E), Color(0xFF311B92)))).padding(24.dp)) { 
        Column(Modifier.align(Alignment.BottomStart)) { 
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(0.7f)) 
        } 
        Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { actions.shareLink(msgLink) }, Modifier.background(Color.White.copy(0.2f), CircleShape)) { Icon(Icons.Default.Share, null, tint = Color.White) }
            if (onMenuClick != null) {
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onMenuClick, Modifier.background(Color.White.copy(0.2f), CircleShape)) { Icon(Icons.Default.Menu, null, tint = Color.White) }
            }
        }
    }
}

@Composable
fun AgendaScreen(appointments: SnapshotStateList<Appointment>, actions: PlatformActions, onOpenDrawer: () -> Unit) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    val todayAppointments = appointments.filter { (it.date ?: "") == today && it.client_name != "BLOQUEADO" }.sortedBy { it.time ?: "" }

    Column(Modifier.fillMaxSize().background(Color(0xFFF8F9FB))) {
        HeaderPremium("Mi Agenda", "Citas de hoy", actions, onMenuClick = onOpenDrawer)
        LazyColumn(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Agenda para hoy", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E)) }
            if (todayAppointments.isEmpty()) {
                item { Text("Sin citas para hoy", Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center, color = Color.Gray) }
            }
            items(todayAppointments) { appt ->
                AppointmentCard(appt, 
                    onChat = { actions.sendWhatsApp(appt.phone, getWhatsAppMsg(appt.client_name ?: "")) }, 
                    onCall = { actions.makeCall(appt.phone) }
                )
            }
        }
    }
}

fun getWhatsAppMsg(name: String) = "╭──────────────────────╮\n" +
            "   *Jesús, Peluquería de Autor*   \n" +
            "╰──────────────────────╯\n" +
            "Hola $name, toca el botón visual de abajo para elegir tu hora favorita:\n" +
            "\n" +
            "      [ 📲 PULSA AQUÍ ]      \n" +
            "\n" +
            MI_WEB_RESERVAS

@Composable
fun UpcomingAppointmentsScreen(appointments: List<Appointment>, actions: PlatformActions, onRefresh: () -> Unit, onOpenDrawer: () -> Unit) {
    val scope = rememberCoroutineScope()
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val tomorrow = today.plus(1, DateTimeUnit.DAY)
    
    val futureAppointments = appointments.filter { 
        val dateStr = it.date ?: ""
        val date = try { LocalDate.parse(dateStr) } catch(e:Exception) { null }
        date != null && (date >= today) && it.client_name != "BLOQUEADO"
    }.sortedWith(compareBy({ it.date ?: "" }, { it.time ?: "" }))

    val groupedByDate = futureAppointments.groupBy { it.date }

    Column(Modifier.fillMaxSize().background(Color(0xFFF8F9FB))) {
        HeaderPremium("Próximos Avisos", "Recordatorios de citas", actions, onMenuClick = onOpenDrawer)
        LazyColumn(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            if (futureAppointments.isEmpty()) {
                item { Text("No hay citas futuras registradas", Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center, color = Color.Gray) }
            }
            groupedByDate.forEach { (date, appts) ->
                item {
                    val isTomorrow = (date ?: "") == tomorrow.toString()
                    val formattedDate = if ((date ?: "") == today.toString()) "Hoy" else if (isTomorrow) "MAÑANA (Avisar 🔔)" else (date ?: "")
                    
                    Text(formattedDate.uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isTomorrow) Color(0xFFE91E63) else Color.Gray, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                }
                items(appts) { appt ->
                    AppointmentCard(appt, 
                        onChat = { actions.sendWhatsApp(appt.phone, getWhatsAppMsg(appt.client_name ?: "")) },
                        onCall = { actions.makeCall(appt.phone) },
                        onReminder = { 
                            val reminderMsg = "Te recordamos tu cita para el día ${appt.date} a las ${appt.time} en:\n*Jesús, Peluquería de Autor*\n\n$MI_WEB_RESERVAS"
                            actions.sendWhatsApp(appt.phone, reminderMsg)
                            scope.launch {
                                try {
                                    supabase.from("appointments").update(mapOf("reminder_sent" to true)) { filter { eq("id", appt.id ?: "") } }
                                    onRefresh()
                                } catch (e: Exception) { }
                            }
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun AppointmentCard(appt: Appointment, onChat: () -> Unit, onCall: () -> Unit, onReminder: (() -> Unit)? = null) {
    Surface(shape = RoundedCornerShape(20.dp), shadowElevation = 2.dp, border = BorderStroke(1.dp, Color(0xFFF0F0F0))) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(appt.time ?: "", fontWeight = FontWeight.Bold, color = Color(0xFF1A237E), fontSize = 18.sp, modifier = Modifier.width(65.dp))
            Column(Modifier.weight(1f)) {
                Text(appt.client_name ?: "", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(appt.service ?: "", color = Color.Gray, fontSize = 13.sp)
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (onReminder != null) {
                    IconButton(
                        onClick = onReminder, 
                        Modifier.background(if (appt.reminder_sent) Color(0xFFE8F5E9) else Color(0xFFE3F2FD), CircleShape).size(36.dp)
                    ) {
                        Icon(
                            if (appt.reminder_sent) Icons.Default.CheckCircle else Icons.Default.NotificationsActive, 
                            null, 
                            tint = if (appt.reminder_sent) Color(0xFF4CAF50) else Color(0xFF1976D2), 
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                }
                IconButton(onClick = onCall, Modifier.background(Color(0xFFE8F5E9), CircleShape).size(36.dp)) { Icon(Icons.Default.Call, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp)) }
                Spacer(Modifier.width(8.dp))
                IconButton(onClick = onChat, Modifier.background(Color(0xFFDCF8C6), CircleShape).size(36.dp)) { Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color(0xFF25D366), modifier = Modifier.size(18.dp)) }
            }
        }
    }
}

@Composable
fun CalendarManagementScreen(vacations: MutableState<Set<String>>, appointments: SnapshotStateList<Appointment>, actions: PlatformActions, onRefresh: () -> Unit, onOpenDrawer: () -> Unit) {
    val scope = rememberCoroutineScope()
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    var selectedDate by remember { mutableStateOf(today) }
    var showBlockDialog by remember { mutableStateOf(false) }
    var showAddApptDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color(0xFFF8F9FB))) {
        HeaderPremium("Gestión de Días", "Calendario y bloqueos", actions, onMenuClick = onOpenDrawer)
        Column(Modifier.padding(16.dp)) {
            Text("Fecha seleccionada: $selectedDate", fontWeight = FontWeight.Bold, color = Color(0xFF1A237E))
            Spacer(Modifier.height(16.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Row {
                    Button(onClick = {
                        val dateStr = selectedDate.toString()
                        scope.launch {
                            try {
                                if (vacations.value.contains(dateStr)) { supabase.from("vacations").delete { filter { eq("date", dateStr) } } }
                                else { supabase.from("vacations").insert(Vacation(dateStr)) }
                                onRefresh()
                            } catch (e: Exception) { }
                        }
                    }, colors = ButtonDefaults.buttonColors(containerColor = if (vacations.value.contains(selectedDate.toString())) Color.Gray else Color(0xFFE91E63)), shape = RoundedCornerShape(12.dp)) { Text(if (vacations.value.contains(selectedDate.toString())) "Quitar Festivo" else "Marcar Festivo", fontSize = 10.sp) }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { showAddApptDialog = true }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFA5))) { Text("Cita", fontSize = 10.sp) }
                    Spacer(Modifier.width(4.dp))
                    Button(onClick = { showBlockDialog = true }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E))) { Text("Bloq.", fontSize = 10.sp) }
                }
            }
            val dayData = appointments.filter { (it.date ?: "") == selectedDate.toString() }.sortedBy { it.time ?: "" }
            LazyColumn(Modifier.height(300.dp).padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                if (dayData.isEmpty()) item { Text("Sin citas ni bloqueos", color = Color.LightGray, modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center) }
                items(dayData) { item ->
                    val isBlock = item.client_name == "BLOQUEADO"
                    Surface(color = if (isBlock) Color(0xFFFFEBEE) else Color.White, shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFFEEEEEE))) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(item.time ?: "", fontWeight = FontWeight.Bold, color = if (isBlock) Color.Red else Color(0xFF1A237E), modifier = Modifier.width(55.dp))
                            Text(if (isBlock) "BLOQUEADO" else (item.client_name ?: ""), modifier = Modifier.weight(1f))
                            IconButton(onClick = { scope.launch { try { supabase.from("appointments").delete { filter { eq("id", item.id ?: "") } }; onRefresh() } catch (e: Exception) { } } }) { Icon(Icons.Default.Delete, null, tint = Color.LightGray, modifier = Modifier.size(20.dp)) }
                        }
                    }
                }
            }
        }
    }
    if (showBlockDialog) { BlockSlotsDialog(onDismiss = { showBlockDialog = false }, onConfirm = { time -> scope.launch { try { supabase.from("appointments").insert(Appointment(date = selectedDate.toString(), time = time, client_name = "BLOQUEADO", service = "BLOQUEO", phone = "000")); onRefresh() } catch (e: Exception) { } } }) }
    if (showAddApptDialog) { ManualAppointmentDialog(selectedDate.toString(), onDismiss = { showAddApptDialog = false }, onConfirm = { appt -> scope.launch { try { supabase.from("appointments").insert(appt); onRefresh() } catch (e: Exception) { } } }) }
}

@Composable
fun BlockSlotsDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val hours = listOf("09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "12:00", "16:00", "16:30", "17:00", "17:30", "18:00", "18:30", "19:00")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Bloquear Hora") }, text = { LazyColumn(Modifier.height(300.dp)) { items(hours) { h -> TextButton(onClick = { onConfirm(h); onDismiss() }, Modifier.fillMaxWidth()) { Text("Bloquear $h", color = Color.Red) } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

@Composable
fun ManualAppointmentDialog(date: String, onDismiss: () -> Unit, onConfirm: (Appointment) -> Unit) {
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var service by remember { mutableStateOf("Corte de Pelo") }; var time by remember { mutableStateOf("09:00") }
    val hours = listOf("09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "12:00", "16:00", "16:30", "17:00", "17:30", "18:00", "18:30", "19:00")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nueva Cita Manual") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") }, shape = RoundedCornerShape(12.dp))
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Teléfono") }, shape = RoundedCornerShape(12.dp))
            OutlinedTextField(value = service, onValueChange = { service = it }, label = { Text("Servicio") }, shape = RoundedCornerShape(12.dp))
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(hours) { h -> FilterChip(selected = time == h, onClick = { time = h }, label = { Text(h) }) } }
        }
    }, confirmButton = { Button(onClick = { if(name.isNotEmpty()) { onConfirm(Appointment(date = date, time = time, client_name = name, service = service, phone = phone)); onDismiss() } }) { Text("Anotar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun CustomerDatabaseScreen(customers: SnapshotStateList<Customer>, appointments: List<Appointment>, actions: PlatformActions, onRefresh: () -> Unit, onOpenDrawer: () -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var showEditDialog by remember { mutableStateOf(false) }

    Scaffold(floatingActionButton = { 
        FloatingActionButton(onClick = { selectedCustomer = null; showEditDialog = true }, containerColor = Color(0xFF1A237E), contentColor = Color.White) { Icon(Icons.Default.Add, null) }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(Color(0xFFF8F9FB))) {
            HeaderPremium("Mis Clientes", "${customers.size} registrados", actions, onMenuClick = onOpenDrawer)
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text("Buscar por nombre...") }, shape = RoundedCornerShape(16.dp), leadingIcon = { Icon(Icons.Default.Search, null) })
            LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(customers.filter { (it.name ?: "").contains(q, true) }) { c -> 
                    CustomerCard(c, onClick = { selectedCustomer = c; showEditDialog = true }, onCall = { actions.makeCall(c.phone ?: "") }, onSendLink = { actions.sendWhatsApp(c.phone, getWhatsAppMsg(c.name ?: "")) }) 
                }
            }
        }
    }
    if (showEditDialog) { 
        CustomerEditDialog(customer = selectedCustomer, onDismiss = { showEditDialog = false }, onConfirm = { updated -> scope.launch { try { if (updated.id == null) supabase.from("customers").insert(updated) else supabase.from("customers").update(updated) { filter { eq("id", updated.id ?: "") } }; onRefresh(); showEditDialog = false } catch (e: Exception) { } } }, onDelete = { toDelete -> scope.launch { try { supabase.from("customers").delete { filter { eq("id", toDelete.id ?: "") } }; onRefresh(); showEditDialog = false } catch (e: Exception) { } } }) 
    }
}

@Composable
fun CustomerCard(c: Customer, onClick: () -> Unit, onCall: () -> Unit, onSendLink: () -> Unit) {
    val name = c.name ?: "Sin nombre"
    Card(Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFEEEEEE))) { Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Box(Modifier.size(40.dp).background(Color(0xFF1A237E), CircleShape), contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), color = Color.White) }; Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(name, fontWeight = FontWeight.Bold); Text(c.phone ?: "", fontSize = 12.sp, color = Color.Gray) }; IconButton(onClick = onSendLink) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color(0xFF2196F3)) }; IconButton(onClick = onCall) { Icon(Icons.Default.Call, null, tint = Color(0xFF4CAF50)) } } }
}

@Composable
fun CustomerEditDialog(customer: Customer?, onDismiss: () -> Unit, onConfirm: (Customer) -> Unit, onDelete: (Customer) -> Unit) {
    var name by remember { mutableStateOf(customer?.name ?: "") }; var phone by remember { mutableStateOf(customer?.phone ?: "") }; var notes by remember { mutableStateOf(customer?.technical_notes ?: "") }
    val visits = remember { mutableStateListOf<Visit>() }
    val scope = rememberCoroutineScope()

    LaunchedEffect(customer) {
        if (customer != null) {
            try {
                val res = supabase.from("visits").select { filter { eq("customer_id", customer.id ?: "") } }.decodeList<Visit>()
                visits.clear(); visits.addAll(res.sortedByDescending { it.date ?: "" })
            } catch (e: Exception) { }
        }
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (customer == null) "Nuevo Cliente" else "Ficha de Cliente") }, text = { 
        Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(8.dp)) { 
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre Completo") }, shape = RoundedCornerShape(12.dp))
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Teléfono") }, shape = RoundedCornerShape(12.dp))
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notas Técnicas") }, modifier = Modifier.height(100.dp), shape = RoundedCornerShape(12.dp)) 
            
            if (customer != null) {
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Historial de Visitas", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        scope.launch {
                            val todayStr = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                            val newVisit = Visit(customer_id = customer.id, date = todayStr, treatment = "Nuevo Tratamiento", notes = "")
                            try { 
                                supabase.from("visits").insert(newVisit)
                                val res = supabase.from("visits").select { filter { eq("customer_id", customer.id ?: "") } }.decodeList<Visit>()
                                visits.clear(); visits.addAll(res.sortedByDescending { it.date ?: "" })
                            } catch(e: Exception) {}
                        }
                    }, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFA5))) { Text("+ Visita", fontSize = 11.sp) }
                }
                
                visits.forEach { visit ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
                        Column(Modifier.padding(8.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(visit.date ?: "", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = {
                                    scope.launch {
                                        try {
                                            supabase.from("visits").delete { filter { eq("id", visit.id ?: "") } }
                                            visits.remove(visit)
                                        } catch(e: Exception) {}
                                    }
                                }, Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = Color.LightGray) }
                            }
                            var vTreatment by remember { mutableStateOf(visit.treatment ?: "") }
                            BasicTextField(value = vTreatment, onValueChange = { vTreatment = it }, textStyle = androidx.compose.ui.text.TextStyle(fontWeight = FontWeight.Bold, fontSize = 13.sp))
                            TextButton(onClick = {
                                scope.launch { try { supabase.from("visits").update(mapOf("treatment" to vTreatment)) { filter { eq("id", visit.id ?: "") } } } catch(e: Exception) {} }
                            }, Modifier.align(Alignment.End).height(30.dp)) { Text("Guardar", fontSize = 10.sp) }
                        }
                    }
                }
            }

            if (customer != null) { TextButton(onClick = { onDelete(customer) }, modifier = Modifier.fillMaxWidth()) { Text("Eliminar Cliente definitivamente", color = Color.Red, fontSize = 12.sp) } }
        } 
    }, confirmButton = { Button(onClick = { if(name.isNotEmpty()) { onConfirm(Customer(id = customer?.id, name = name, phone = phone, last_visit = "Hoy", technical_notes = notes)); onDismiss() } }) { Text("Guardar Ficha") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}
