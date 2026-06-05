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
import kotlinx.datetime.*

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
                    Text("Panel de Control", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E))
                    Spacer(Modifier.height(8.dp))
                    Text(if(actions.isDesktop) "Edición Escritorio Portable" else "Guía de Ayuda", color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider()
                    Spacer(Modifier.height(16.dp))
                    
                    HelpSection("📅 Mi Agenda", "Visualiza las citas del día actual de un vistazo.")
                    if (!actions.isDesktop) {
                        HelpSection("🔔 Avisos", "Recordatorios próximos y gestión de avisos por WhatsApp.")
                    }
                    HelpSection("📆 Gestión", "Calendario completo. Bloquea horas, marca festivos y gestiona el tiempo.")
                    HelpSection("👥 Clientes", "Fichas técnicas, tratamiento habitual e historial de visitas.")
                    
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
                    if (!actions.isDesktop) {
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
                    }
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
                        Text("Sincronizar", Modifier.clickable { scope.launch { loadData() } }, fontSize = 11.sp, color = Color(0xFF1A237E), fontWeight = FontWeight.Bold)
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    if (isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF1A237E)) }
                    } else {
                        when (currentTab) {
                            0 -> AgendaScreen(appointments, actions, onOpenDrawer = { scope.launch { drawerState.open() } })
                            1 -> if(!actions.isDesktop) UpcomingAppointmentsScreen(appointments, actions, onRefresh = { scope.launch { loadData() } }, onOpenDrawer = { scope.launch { drawerState.open() } }) else currentTab = 0
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
    Box(Modifier.fillMaxWidth().height(140.dp).background(Brush.verticalGradient(listOf(Color(0xFF1A237E), Color(0xFF311B92)))).padding(24.dp)) { 
        Column(Modifier.align(Alignment.BottomStart)) { 
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(0.7f)) 
        } 
        Row(Modifier.align(Alignment.TopEnd), verticalAlignment = Alignment.CenterVertically) {
            if (!actions.isDesktop) {
                IconButton(onClick = { actions.shareLink(MI_WEB_RESERVAS) }, Modifier.background(Color.White.copy(0.2f), CircleShape)) { Icon(Icons.Default.Share, null, tint = Color.White) }
                Spacer(Modifier.width(8.dp))
            }
            if (onMenuClick != null) {
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
                AppointmentCard(appt, actions)
            }
        }
    }
}

@Composable
fun AppointmentCard(appt: Appointment, actions: PlatformActions) {
    Surface(shape = RoundedCornerShape(20.dp), shadowElevation = 2.dp, border = BorderStroke(1.dp, Color(0xFFF0F0F0))) {
        Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(appt.time ?: "", fontWeight = FontWeight.Bold, color = Color(0xFF1A237E), fontSize = 18.sp, modifier = Modifier.width(65.dp))
            Column(Modifier.weight(1f)) {
                Text(appt.client_name ?: "", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                Text(appt.service ?: "", color = Color.Gray, fontSize = 13.sp)
            }
            if (!actions.isDesktop) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { actions.makeCall(appt.phone) }, Modifier.background(Color(0xFFE8F5E9), CircleShape).size(36.dp)) { Icon(Icons.Default.Call, null, tint = Color(0xFF4CAF50), modifier = Modifier.size(18.dp)) }
                    Spacer(Modifier.width(8.dp))
                    IconButton(onClick = { actions.sendWhatsApp(appt.phone, "Hola ${appt.client_name}, te recordamos tu cita...") }, Modifier.background(Color(0xFFDCF8C6), CircleShape).size(36.dp)) { Icon(Icons.AutoMirrored.Filled.Chat, null, tint = Color(0xFF25D366), modifier = Modifier.size(18.dp)) }
                }
            }
        }
    }
}

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
            groupedByDate.forEach { (date, appts) ->
                item {
                    val isTomorrow = (date ?: "") == tomorrow.toString()
                    val formattedDate = if ((date ?: "") == today.toString()) "Hoy" else if (isTomorrow) "MAÑANA (Avisar 🔔)" else (date ?: "")
                    Text((formattedDate ?: "").uppercase(), fontSize = 12.sp, fontWeight = FontWeight.Bold, color = if (isTomorrow) Color(0xFFE91E63) else Color.Gray, modifier = Modifier.padding(start = 4.dp, bottom = 4.dp))
                }
                items(appts) { appt ->
                    AppointmentCard(appt, actions)
                }
            }
        }
    }
}

@Composable
fun CalendarManagementScreen(vacations: MutableState<Set<String>>, appointments: SnapshotStateList<Appointment>, actions: PlatformActions, onRefresh: () -> Unit, onOpenDrawer: () -> Unit) {
    val scope = rememberCoroutineScope()
    var selectedDate by remember { mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date) }
    var currentMonth by remember { mutableStateOf(selectedDate.month) }
    var currentYear by remember { mutableIntStateOf(selectedDate.year) }
    
    var showBlockDialog by remember { mutableStateOf(false) }
    var showAddApptDialog by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Color(0xFFF8F9FB))) {
        HeaderPremium("Gestión de Días", "Calendario y bloqueos", actions, onMenuClick = onOpenDrawer)
        
        Row(Modifier.fillMaxSize().padding(16.dp)) {
            // Columna Izquierda: Calendario (Se potencia en Desktop)
            Column(Modifier.weight(if(actions.isDesktop) 1.2f else 1f)) {
                CalendarGrid(
                    year = currentYear,
                    month = currentMonth,
                    selectedDate = selectedDate,
                    vacations = vacations.value,
                    appointments = appointments,
                    onMonthChange = { m, y -> currentMonth = m; currentYear = y },
                    onDateSelect = { selectedDate = it }
                )
                
                Spacer(Modifier.height(16.dp))
                
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedDate.toString(), fontWeight = FontWeight.Bold, color = Color(0xFF1A237E), fontSize = if(actions.isDesktop) 20.sp else 16.sp)
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
                        }, colors = ButtonDefaults.buttonColors(containerColor = if (vacations.value.contains(selectedDate.toString())) Color.Gray else Color(0xFFE91E63)), shape = RoundedCornerShape(12.dp)) { Text(if (vacations.value.contains(selectedDate.toString())) "Quitar" else "Festivo", fontSize = 11.sp) }
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = { showAddApptDialog = true }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFA5))) { Text("Cita", fontSize = 11.sp) }
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = { showBlockDialog = true }, shape = RoundedCornerShape(12.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E))) { Text("Bloq.", fontSize = 11.sp) }
                    }
                }
            }

            if (actions.isDesktop) {
                Spacer(Modifier.width(24.dp))
                // Columna Derecha: Detalle del día (Solo Desktop)
                Column(Modifier.weight(0.8f)) {
                    Text("Citas para el día", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E))
                    Spacer(Modifier.height(8.dp))
                    val dayData = appointments.filter { (it.date ?: "") == selectedDate.toString() }.sortedBy { it.time ?: "" }
                    LazyColumn(Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (dayData.isEmpty()) item { Text("Sin citas ni bloqueos", color = Color.LightGray, modifier = Modifier.fillMaxWidth().padding(top = 20.dp), textAlign = TextAlign.Center) }
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
        }
    }
    
    // Para móvil, el detalle sale debajo si no es Desktop
    if (!actions.isDesktop) {
        // ... Lógica para mostrar la lista debajo ...
    }

    if (showBlockDialog) { BlockSlotsDialog(onDismiss = { showBlockDialog = false }, onConfirm = { time -> scope.launch { try { supabase.from("appointments").insert(Appointment(date = selectedDate.toString(), time = time, client_name = "BLOQUEADO", service = "BLOQUEO", phone = "000")); onRefresh() } catch (e: Exception) { } } }) }
    if (showAddApptDialog) { ManualAppointmentDialog(selectedDate.toString(), onDismiss = { showAddApptDialog = false }, onConfirm = { appt -> scope.launch { try { supabase.from("appointments").insert(appt); onRefresh() } catch (e: Exception) { } } }) }
}

@Composable
fun CalendarGrid(year: Int, month: Month, selectedDate: LocalDate, vacations: Set<String>, appointments: List<Appointment>, onMonthChange: (Month, Int) -> Unit, onDateSelect: (LocalDate) -> Unit) {
    val firstDayOfMonth = LocalDate(year, month, 1)
    val daysInMonth = getDaysInMonth(year, month)
    val paddingDays = if (firstDayOfMonth.dayOfWeek.isoDayNumber == 7) 6 else firstDayOfMonth.dayOfWeek.isoDayNumber - 1

    Column {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = {
                val prev = if (month == Month.JANUARY) Month.DECEMBER else Month.values()[month.ordinal - 1]
                val y = if (month == Month.JANUARY) year - 1 else year
                onMonthChange(prev, y)
            }) { Icon(Icons.Default.ChevronLeft, null) }
            Text("${month.name} $year", fontWeight = FontWeight.Bold, fontSize = 18.sp)
            IconButton(onClick = {
                val next = if (month == Month.DECEMBER) Month.JANUARY else Month.values()[month.ordinal + 1]
                val y = if (month == Month.DECEMBER) year + 1 else year
                onMonthChange(next, y)
            }) { Icon(Icons.Default.ChevronRight, null) }
        }
        
        Row(Modifier.fillMaxWidth()) {
            listOf("L", "M", "X", "J", "V", "S", "D").forEach { 
                Text(it, modifier = Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 12.sp)
            }
        }
        
        val rows = (daysInMonth + paddingDays + 6) / 7
        for (row in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (col in 0 until 7) {
                    val dayIndex = row * 7 + col - paddingDays + 1
                    if (dayIndex in 1..daysInMonth) {
                        val date = LocalDate(year, month, dayIndex)
                        val isSelected = date == selectedDate
                        val isVacation = vacations.contains(date.toString()) || date.dayOfWeek == DayOfWeek.SUNDAY
                        val hasAppts = appointments.any { it.date == date.toString() && it.client_name != "BLOQUEADO" }
                        
                        Box(
                            Modifier.weight(1f).aspectRatio(1f).padding(2.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isSelected) Color(0xFF1A237E) else if (isVacation) Color(0xFFFFEBEE) else if (hasAppts) Color(0xFFE0F2F1) else Color.Transparent)
                                .clickable { onDateSelect(date) },
                            contentAlignment = Alignment.Center
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(dayIndex.toString(), color = if (isSelected) Color.White else if (isVacation) Color.Red else Color.Black, fontWeight = if(isSelected) FontWeight.Bold else FontWeight.Normal)
                                if (hasAppts && !isSelected) Box(Modifier.size(4.dp).background(Color(0xFF00BFA5), CircleShape))
                            }
                        }
                    } else { Spacer(Modifier.weight(1f).aspectRatio(1f)) }
                }
            }
        }
    }
}

fun getDaysInMonth(year: Int, month: Month): Int {
    return when (month) {
        Month.FEBRUARY -> if ((year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)) 29 else 28
        Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> 30
        else -> 31
    }
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
                    CustomerCard(c, actions, onClick = { selectedCustomer = c; showEditDialog = true }) 
                }
            }
        }
    }
    if (showEditDialog) { 
        CustomerEditDialog(customer = selectedCustomer, actions = actions, onDismiss = { showEditDialog = false }, 
            onConfirm = { updated -> scope.launch { try { if (updated.id == null) supabase.from("customers").insert(updated) else supabase.from("customers").update(updated) { filter { eq("id", updated.id ?: "") } }; onRefresh(); showEditDialog = false } catch (e: Exception) { } } }, 
            onDelete = { toDelete -> scope.launch { try { supabase.from("customers").delete { filter { eq("id", toDelete.id ?: "") } }; onRefresh(); showEditDialog = false } catch (e: Exception) { } } },
            onAddAppointment = { c, date, appt -> scope.launch { try { supabase.from("appointments").insert(appt); onRefresh() } catch(e: Exception) {} } }
        ) 
    }
}

@Composable
fun CustomerCard(c: Customer, actions: PlatformActions, onClick: () -> Unit) {
    val name = c.name ?: "Sin nombre"
    Card(Modifier.fillMaxWidth().clickable { onClick() }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFEEEEEE))) { 
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { 
            Box(Modifier.size(40.dp).background(Color(0xFF1A237E), CircleShape), contentAlignment = Alignment.Center) { Text(name.take(1).uppercase(), color = Color.White) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) { 
                Text(name, fontWeight = FontWeight.Bold)
                Text(c.phone ?: "", fontSize = 12.sp, color = Color.Gray) 
            }
            if (!actions.isDesktop) {
                IconButton(onClick = { actions.sendWhatsApp(c.phone, "Hola...") }) { Icon(Icons.AutoMirrored.Filled.Send, null, tint = Color(0xFF2196F3)) }
                IconButton(onClick = { actions.makeCall(c.phone) }) { Icon(Icons.Default.Call, null, tint = Color(0xFF4CAF50)) }
            }
        } 
    }
}

@Composable
fun CustomerEditDialog(customer: Customer?, actions: PlatformActions, onDismiss: () -> Unit, onConfirm: (Customer) -> Unit, onDelete: (Customer) -> Unit, onAddAppointment: (Customer, String, Appointment) -> Unit) {
    var name by remember { mutableStateOf(customer?.name ?: "") }
    var phone by remember { mutableStateOf(customer?.phone ?: "") }
    var notes by remember { mutableStateOf(customer?.technical_notes ?: "") }
    var habitualTreatment by remember { mutableStateOf(customer?.habitual_treatment ?: "") }
    
    val visits = remember { mutableStateListOf<Visit>() }
    val scope = rememberCoroutineScope()
    var showAddApptDialog by remember { mutableStateOf(false) }

    LaunchedEffect(customer) {
        if (customer != null) {
            try {
                val res = supabase.from("visits").select { filter { eq("customer_id", customer.id ?: "") } }.decodeList<Visit>()
                visits.clear(); visits.addAll(res.sortedByDescending { it.date ?: "" })
            } catch (e: Exception) { }
        }
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (customer == null) "Nuevo Cliente" else "Ficha Técnica") }, text = { 
        Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { 
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre Completo") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Teléfono") }, modifier = Modifier.fillMaxWidth())
            
            // Tratamiento Habitual (Nueva sección potenciada)
            Surface(color = Color(0xFFE8EAF6), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Tratamiento Habitual", fontWeight = FontWeight.Bold, color = Color(0xFF1A237E), fontSize = 14.sp)
                    BasicTextField(value = habitualTreatment, onValueChange = { habitualTreatment = it }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }

            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Notas Técnicas / Historial") }, modifier = Modifier.fillMaxWidth().height(100.dp)) 
            
            if (customer != null) {
                Button(onClick = { showAddApptDialog = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E))) {
                    Icon(Icons.Default.Event, null); Spacer(Modifier.width(8.dp)); Text("Agendar Cita Directa")
                }
                
                HorizontalDivider(Modifier.padding(vertical = 8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Registros de Visitas", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(onClick = {
                        scope.launch {
                            val todayStr = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                            val newVisit = Visit(customer_id = customer.id, date = todayStr, treatment = "Tratamiento de hoy", notes = "")
                            try { 
                                supabase.from("visits").insert(newVisit)
                                val res = supabase.from("visits").select { filter { eq("customer_id", customer.id ?: "") } }.decodeList<Visit>()
                                visits.clear(); visits.addAll(res.sortedByDescending { it.date ?: "" })
                            } catch(e: Exception) {}
                        }
                    }, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFA5))) { Text("+ Registro Hoy", fontSize = 11.sp) }
                }
                
                visits.forEach { visit ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 4.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
                        Column(Modifier.padding(12.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(visit.date ?: "", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color.Gray)
                                Spacer(Modifier.weight(1f))
                                IconButton(onClick = {
                                    scope.launch {
                                        try {
                                            supabase.from("visits").delete { filter { eq("id", visit.id ?: "") } }
                                            visits.remove(visit)
                                        } catch(e: Exception) {}
                                    }
                                }, Modifier.size(24.dp)) { Icon(Icons.Default.Delete, null, Modifier.size(16.dp), tint = Color.Red.copy(0.4f)) }
                            }
                            var vTreatment by remember { mutableStateOf(visit.treatment ?: "") }
                            Text("Tratamiento aplicado:", fontSize = 11.sp, color = Color.Gray)
                            BasicTextField(value = vTreatment, onValueChange = { vTreatment = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp))
                            TextButton(onClick = {
                                scope.launch { try { supabase.from("visits").update(mapOf("treatment" to vTreatment)) { filter { eq("id", visit.id ?: "") } } } catch(e: Exception) {} }
                            }, Modifier.align(Alignment.End).height(30.dp)) { Text("Guardar Registro", fontSize = 10.sp) }
                        }
                    }
                }
                TextButton(onClick = { onDelete(customer) }, modifier = Modifier.fillMaxWidth()) { Text("Eliminar Cliente definitivamente", color = Color.Red, fontSize = 12.sp) }
            }
        } 
    }, confirmButton = { Button(onClick = { if(name.isNotEmpty()) { onConfirm(Customer(id = customer?.id, name = name, phone = phone, last_visit = "Hoy", technical_notes = notes, habitual_treatment = habitualTreatment)); onDismiss() } }) { Text("Guardar Cambios") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })

    if (showAddApptDialog && customer != null) {
        ManualAppointmentDialog(
            date = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString(),
            initialName = customer.name ?: "",
            initialPhone = customer.phone ?: "",
            onDismiss = { showAddApptDialog = false },
            onConfirm = { appt -> onAddAppointment(customer, appt.date ?: "", appt); showAddApptDialog = false }
        )
    }
}

@Composable
fun ManualAppointmentDialog(date: String, initialName: String = "", initialPhone: String = "", onDismiss: () -> Unit, onConfirm: (Appointment) -> Unit) {
    var name by remember { mutableStateOf(initialName) }; var phone by remember { mutableStateOf(initialPhone) }; var service by remember { mutableStateOf("Servicio") }; var time by remember { mutableStateOf("09:00") }
    val hours = listOf("09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "12:00", "16:00", "16:30", "17:00", "17:30", "18:00", "18:30", "19:00")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nueva Cita") }, text = {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre") })
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Teléfono") })
            OutlinedTextField(value = service, onValueChange = { service = it }, label = { Text("Servicio") })
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(hours) { h -> FilterChip(selected = time == h, onClick = { time = h }, label = { Text(h) }) } }
        }
    }, confirmButton = { Button(onClick = { if(name.isNotEmpty()) { onConfirm(Appointment(date = date, time = time, client_name = name, service = service, phone = phone)); onDismiss() } }) { Text("Anotar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun BlockSlotsDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val hours = listOf("09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "12:00", "16:00", "16:30", "17:00", "17:30", "18:00", "18:30", "19:00")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Bloquear Hora") }, text = { LazyColumn(Modifier.height(300.dp)) { items(hours) { h -> TextButton(onClick = { onConfirm(h); onDismiss() }, Modifier.fillMaxWidth()) { Text("Bloquear $h", color = Color.Red) } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}
