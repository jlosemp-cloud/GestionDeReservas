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

    val pendingRemindersCount by remember {
        derivedStateOf {
            appointments.count { it.date == tomorrow.toString() && !it.reminder_sent && it.client_name != "BLOQUEADO" }
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
            
            statusMsg = "Conectado ✅"
        } catch (e: Exception) { statusMsg = "Error de conexión" }
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

    // Configuración de pestañas: Avisos solo en móvil
    val tabs = if (actions.isDesktop) listOf("Hoy", "Gestión", "Clientes") else listOf("Hoy", "Avisos", "Gestión", "Clientes")

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(drawerContainerColor = Color.White, modifier = Modifier.width(320.dp)) {
                Column(Modifier.fillMaxSize().padding(24.dp).verticalScroll(rememberScrollState())) {
                    Text("Gestión Reservas", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E))
                    Spacer(Modifier.height(8.dp))
                    Text(if(actions.isDesktop) "Panel Profesional Desktop" else "Guía Móvil", color = Color.Gray, fontSize = 14.sp)
                    Spacer(Modifier.height(24.dp))
                    HorizontalDivider()
                    
                    HelpSection("📅 Mi Agenda", "Citas de hoy de un vistazo rápido.")
                    if (!actions.isDesktop) HelpSection("🔔 Avisos", "Gestión de recordatorios por WhatsApp.")
                    HelpSection("📆 Gestión Pro", "Calendario completo con panel de detalles lateral.")
                    HelpSection("👥 Clientes", "Fichas técnicas con tratamiento habitual y registros.")
                    
                    Spacer(Modifier.weight(1f))
                    Text("jlosemp(C) 2026", modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center, color = Color.Gray, fontSize = 12.sp)
                    Spacer(Modifier.height(16.dp))
                }
            }
        }
    ) {
        Scaffold(
            bottomBar = {
                NavigationBar(containerColor = Color.White, tonalElevation = 8.dp) {
                    tabs.forEachIndexed { index, label ->
                        NavigationBarItem(
                            selected = currentTab == index,
                            onClick = { currentTab = index },
                            icon = { 
                                val icon = when(label) {
                                    "Hoy" -> Icons.Default.Today
                                    "Avisos" -> Icons.Default.NotificationsActive
                                    "Gestión" -> Icons.Default.DateRange
                                    else -> Icons.Default.People
                                }
                                if (label == "Avisos" && pendingRemindersCount > 0) {
                                    BadgedBox(badge = { Badge { Text(pendingRemindersCount.toString()) } }) { Icon(icon, null) }
                                } else { Icon(icon, null) }
                            },
                            label = { Text(label) }
                        )
                    }
                }
            }
        ) { innerPadding ->
            Column(Modifier.padding(innerPadding)) {
                Surface(color = Color(0xFFE8EAF6), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(8.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                        Text(statusMsg, fontSize = 11.sp, color = Color.DarkGray)
                        Spacer(Modifier.width(8.dp))
                        Text("Reintentar", Modifier.clickable { scope.launch { loadData() } }, fontSize = 11.sp, color = Color(0xFF1A237E), fontWeight = FontWeight.Bold)
                    }
                }

                Box(Modifier.fillMaxSize()) {
                    if (isLoading) {
                        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator(color = Color(0xFF1A237E)) }
                    } else {
                        val currentLabel = tabs[currentTab]
                        when (currentLabel) {
                            "Hoy" -> AgendaScreen(appointments, actions, onOpenDrawer = { scope.launch { drawerState.open() } })
                            "Avisos" -> UpcomingAppointmentsScreen(appointments, actions, onRefresh = { scope.launch { loadData() } }, onOpenDrawer = { scope.launch { drawerState.open() } })
                            "Gestión" -> CalendarManagementScreen(vacations, appointments, actions, onRefresh = { scope.launch { loadData() } }, onOpenDrawer = { scope.launch { drawerState.open() } })
                            "Clientes" -> CustomerDatabaseScreen(customers, appointments, actions, onRefresh = { scope.launch { loadData() } }, onOpenDrawer = { scope.launch { drawerState.open() } })
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun HeaderPremium(title: String, subtitle: String, actions: PlatformActions, onMenuClick: (() -> Unit)? = null) {
    Box(Modifier.fillMaxWidth().height(120.dp).background(Brush.verticalGradient(listOf(Color(0xFF1A237E), Color(0xFF311B92)))).padding(24.dp)) { 
        Column(Modifier.align(Alignment.BottomStart)) { 
            Text(title, color = Color.White, fontSize = 24.sp, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Color.White.copy(0.7f)) 
        } 
        if (onMenuClick != null) {
            IconButton(onClick = onMenuClick, Modifier.align(Alignment.TopEnd).background(Color.White.copy(0.2f), CircleShape)) { Icon(Icons.Default.Menu, null, tint = Color.White) }
        }
    }
}

@Composable
fun AgendaScreen(appointments: SnapshotStateList<Appointment>, actions: PlatformActions, onOpenDrawer: () -> Unit) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
    val todayAppointments = appointments.filter { it.date == today && it.client_name != "BLOQUEADO" }.sortedBy { it.time ?: "" }

    Column(Modifier.fillMaxSize().background(Color(0xFFF8F9FB))) {
        HeaderPremium("Mi Agenda", "Citas de hoy", actions, onMenuClick = onOpenDrawer)
        LazyColumn(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item { Text("Agenda de trabajo", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold, color = Color(0xFF1A237E)) }
            if (todayAppointments.isEmpty()) {
                item { Text("Sin citas para hoy", Modifier.fillMaxWidth().padding(top = 40.dp), textAlign = TextAlign.Center, color = Color.Gray) }
            }
            items(todayAppointments) { appt ->
                Surface(shape = RoundedCornerShape(20.dp), border = BorderStroke(1.dp, Color(0xFFF0F0F0)), shadowElevation = 2.dp) {
                    Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(appt.time ?: "", fontWeight = FontWeight.Bold, color = Color(0xFF1A237E), fontSize = 20.sp, modifier = Modifier.width(75.dp))
                        Column(Modifier.weight(1f)) {
                            Text(appt.client_name ?: "", fontWeight = FontWeight.Bold, fontSize = 17.sp)
                            Text(appt.service ?: "", color = Color.Gray, fontSize = 14.sp)
                        }
                    }
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
        HeaderPremium("Gestión de Días", "Calendario y Detalles", actions, onMenuClick = onOpenDrawer)
        Row(Modifier.fillMaxSize().padding(16.dp)) {
            // Panel Izquierdo: Calendario
            Column(Modifier.weight(if(actions.isDesktop) 1.2f else 1f)) {
                CalendarGrid(currentYear, currentMonth, selectedDate, vacations.value, appointments, 
                    onMonthChange = { m, y -> currentMonth = m; currentYear = y }, onDateSelect = { selectedDate = it })
                
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(selectedDate.toString(), fontWeight = FontWeight.Bold, color = Color(0xFF1A237E), fontSize = 18.sp)
                    Row {
                        Button(onClick = { 
                            scope.launch { try { 
                                if (vacations.value.contains(selectedDate.toString())) supabase.from("vacations").delete { filter { eq("date", selectedDate.toString()) } } 
                                else supabase.from("vacations").insert(Vacation(selectedDate.toString()))
                                onRefresh() 
                            } catch (e: Exception) {} }
                        }, colors = ButtonDefaults.buttonColors(containerColor = if (vacations.value.contains(selectedDate.toString())) Color.Gray else Color(0xFFE91E63)), shape = RoundedCornerShape(8.dp)) { Text("Festivo", fontSize = 10.sp) }
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = { showAddApptDialog = true }, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFA5))) { Text("Cita", fontSize = 10.sp) }
                        Spacer(Modifier.width(4.dp))
                        Button(onClick = { showBlockDialog = true }, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E))) { Text("Bloq", fontSize = 10.sp) }
                    }
                }
            }
            // Panel Derecho: Detalles (Solo Desktop)
            if (actions.isDesktop) {
                Spacer(Modifier.width(24.dp))
                Column(Modifier.weight(0.8f)) {
                    Text("Citas para el día:", fontWeight = FontWeight.Bold, color = Color(0xFF1A237E), fontSize = 15.sp)
                    val dayData = appointments.filter { it.date == selectedDate.toString() }.sortedBy { it.time ?: "" }
                    LazyColumn(Modifier.fillMaxSize().padding(top = 8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        if (dayData.isEmpty()) item { Text("Día sin citas", color = Color.LightGray, modifier = Modifier.fillMaxWidth().padding(top = 20.dp), textAlign = TextAlign.Center) }
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
    if (showBlockDialog) BlockSlotsDialog(onDismiss = { showBlockDialog = false }, onConfirm = { t -> scope.launch { try { supabase.from("appointments").insert(Appointment(date = selectedDate.toString(), time = t, client_name = "BLOQUEADO", service = "BLOQUEO")); onRefresh() } catch(e: Exception){} } })
    if (showAddApptDialog) ManualAppointmentDialog(selectedDate.toString(), onDismiss = { showAddApptDialog = false }, onConfirm = { a -> scope.launch { try { supabase.from("appointments").insert(a); onRefresh() } catch(e: Exception){} } })
}

@Composable
fun CalendarGrid(year: Int, month: Month, selectedDate: LocalDate, vacations: Set<String>, appointments: List<Appointment>, onMonthChange: (Month, Int) -> Unit, onDateSelect: (LocalDate) -> Unit) {
    val monthName = when(month) { Month.JANUARY -> "Enero"; Month.FEBRUARY -> "Febrero"; Month.MARCH -> "Marzo"; Month.APRIL -> "Abril"; Month.MAY -> "Mayo"; Month.JUNE -> "Junio"; Month.JULY -> "Julio"; Month.AUGUST -> "Agosto"; Month.SEPTEMBER -> "Septiembre"; Month.OCTOBER -> "Octubre"; Month.NOVEMBER -> "Noviembre"; Month.DECEMBER -> "Diciembre"; else -> month.name }
    val firstDay = LocalDate(year, month, 1)
    val daysInMonth = when(month) { Month.FEBRUARY -> if((year%4==0 && year%100!=0)||year%400==0) 29 else 28; Month.APRIL, Month.JUNE, Month.SEPTEMBER, Month.NOVEMBER -> 30; else -> 31 }
    val padding = if (firstDay.dayOfWeek.isoDayNumber == 7) 6 else firstDay.dayOfWeek.isoDayNumber - 1

    Column(Modifier.background(Color.White, RoundedCornerShape(16.dp)).padding(16.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { val pM = if(month == Month.JANUARY) Month.DECEMBER else Month.entries[month.ordinal-1]; val pY = if(month == Month.JANUARY) year-1 else year; onMonthChange(pM, pY) }) { Icon(Icons.Default.ChevronLeft, null) }
            Text("$monthName $year", fontWeight = FontWeight.Bold, fontSize = 20.sp, color = Color(0xFF1A237E))
            IconButton(onClick = { val nM = if(month == Month.DECEMBER) Month.JANUARY else Month.entries[month.ordinal+1]; val nY = if(month == Month.DECEMBER) year+1 else year; onMonthChange(nM, nY) }) { Icon(Icons.Default.ChevronRight, null) }
        }
        Row(Modifier.fillMaxWidth()) {
            listOf("L","M","X","J","V","S","D").forEach { Text(it, Modifier.weight(1f), textAlign = TextAlign.Center, fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 12.sp) }
        }
        val rows = (daysInMonth + padding + 6) / 7
        for (r in 0 until rows) {
            Row(Modifier.fillMaxWidth()) {
                for (c in 0 until 7) {
                    val day = r * 7 + c - padding + 1
                    if (day in 1..daysInMonth) {
                        val date = LocalDate(year, month, day)
                        val isSel = date == selectedDate
                        val isVac = vacations.contains(date.toString()) || date.dayOfWeek == DayOfWeek.SUNDAY
                        val hasAppts = appointments.any { it.date == date.toString() && it.client_name != "BLOQUEADO" }
                        Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp).clip(RoundedCornerShape(8.dp)).background(if(isSel) Color(0xFF1A237E) else if(isVac) Color(0xFFFFEBEE) else if(hasAppts) Color(0xFFE0F2F1) else Color.Transparent).clickable { onDateSelect(date) }, contentAlignment = Alignment.Center) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(day.toString(), color = if(isSel) Color.White else if(isVac) Color.Red else Color.Black, fontWeight = if(isSel) FontWeight.Bold else FontWeight.Normal)
                                if (hasAppts && !isSel) Box(Modifier.size(4.dp).background(Color(0xFF00BFA5), CircleShape))
                            }
                        }
                    } else Spacer(Modifier.weight(1f).aspectRatio(1f))
                }
            }
        }
    }
}

@Composable
fun CustomerDatabaseScreen(customers: SnapshotStateList<Customer>, appointments: List<Appointment>, actions: PlatformActions, onRefresh: () -> Unit, onOpenDrawer: () -> Unit) {
    val scope = rememberCoroutineScope()
    var q by remember { mutableStateOf("") }
    var selectedC by remember { mutableStateOf<Customer?>(null) }
    var showEdit by remember { mutableStateOf(false) }

    Scaffold(floatingActionButton = { 
        FloatingActionButton(onClick = { selectedC = null; showEdit = true }, containerColor = Color(0xFF1A237E), contentColor = Color.White) { Icon(Icons.Default.Add, null) }
    }) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).background(Color(0xFFF8F9FB))) {
            HeaderPremium("Mis Clientes", "${customers.size} registrados", actions, onMenuClick = onOpenDrawer)
            OutlinedTextField(q, { q = it }, Modifier.fillMaxWidth().padding(16.dp), placeholder = { Text("Buscar por nombre...") }, shape = RoundedCornerShape(12.dp), leadingIcon = { Icon(Icons.Default.Search, null) })
            LazyColumn(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(customers.filter { (it.name ?: "").contains(q, true) }) { c -> 
                    Card(Modifier.fillMaxWidth().clickable { selectedC = c; showEdit = true }, colors = CardDefaults.cardColors(containerColor = Color.White), border = BorderStroke(1.dp, Color(0xFFEEEEEE))) {
                        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                            Box(Modifier.size(45.dp).background(Color(0xFF1A237E), CircleShape), contentAlignment = Alignment.Center) { Text((c.name ?: "S").take(1).uppercase(), color = Color.White, fontWeight = FontWeight.Bold) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(c.name ?: "Sin nombre", fontWeight = FontWeight.Bold, fontSize = 16.sp); Text(c.phone ?: "", fontSize = 13.sp, color = Color.Gray) }
                        }
                    }
                }
            }
        }
    }
    if (showEdit) {
        CustomerEditDialog(selectedC, actions, onDismiss = { showEdit = false }, 
            onConfirm = { updated -> scope.launch { try { if (updated.id == null) supabase.from("customers").insert(updated) else supabase.from("customers").update(updated) { filter { eq("id", updated.id ?: "") } }; onRefresh(); showEdit = false } catch (e: Exception) {} } }, 
            onDelete = { toDelete -> scope.launch { try { supabase.from("customers").delete { filter { eq("id", toDelete.id ?: "") } }; onRefresh(); showEdit = false } catch (e: Exception) {} } },
            onAddAppointment = { c, appt -> scope.launch { try { supabase.from("appointments").insert(appt); onRefresh() } catch(e: Exception){} } }
        )
    }
}

@Composable
fun CustomerEditDialog(customer: Customer?, actions: PlatformActions, onDismiss: () -> Unit, onConfirm: (Customer) -> Unit, onDelete: (Customer) -> Unit, onAddAppointment: (Customer, Appointment) -> Unit) {
    var name by remember { mutableStateOf(customer?.name ?: "") }
    var phone by remember { mutableStateOf(customer?.phone ?: "") }
    var notes by remember { mutableStateOf(customer?.technical_notes ?: "") }
    var habitualT by remember { mutableStateOf(customer?.habitual_treatment ?: "") }
    val visits = remember { mutableStateListOf<Visit>() }
    val scope = rememberCoroutineScope()
    var showAddA by remember { mutableStateOf(false) }

    LaunchedEffect(customer) {
        if (customer != null) {
            try {
                val res = supabase.from("visits").select { filter { eq("customer_id", customer.id ?: "") } }.decodeList<Visit>()
                visits.clear(); visits.addAll(res.sortedByDescending { it.date ?: "" })
            } catch (e: Exception) {}
        }
    }

    AlertDialog(onDismissRequest = onDismiss, title = { Text(if (customer == null) "Nuevo Cliente" else "Ficha Técnica") }, text = { 
        Column(Modifier.verticalScroll(rememberScrollState()).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(12.dp)) { 
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Nombre Completo") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Teléfono") }, modifier = Modifier.fillMaxWidth())
            Surface(color = Color(0xFFE8EAF6), shape = RoundedCornerShape(8.dp), modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.padding(12.dp)) {
                    Text("Tratamiento Habitual", fontWeight = FontWeight.Bold, color = Color(0xFF1A237E), fontSize = 13.sp)
                    BasicTextField(value = habitualT, onValueChange = { habitualT = it }, modifier = Modifier.fillMaxWidth().padding(top = 4.dp))
                }
            }
            OutlinedTextField(value = notes, onValueChange = { notes = it }, label = { Text("Historial / Notas Técnicas") }, modifier = Modifier.fillMaxWidth().height(80.dp)) 
            if (customer != null) {
                Button(onClick = { showAddA = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1A237E))) { Icon(Icons.Default.Event, null); Spacer(Modifier.width(8.dp)); Text("Agendar Cita Directa") }
                HorizontalDivider(Modifier.padding(vertical = 4.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Registros Diarios", fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                    Button(onClick = { scope.launch { val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()
                        val newV = Visit(customer_id = customer.id, date = today, treatment = "Tratamiento hoy")
                        try { supabase.from("visits").insert(newV); val res = supabase.from("visits").select { filter { eq("customer_id", customer.id ?: "") } }.decodeList<Visit>(); visits.clear(); visits.addAll(res.sortedByDescending { it.date ?: "" }) } catch(e: Exception){} } }, shape = RoundedCornerShape(8.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00BFA5))) { Text("+ Registro", fontSize = 10.sp) }
                }
                visits.forEach { v ->
                    Card(Modifier.fillMaxWidth().padding(vertical = 2.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF5F5F5))) {
                        Column(Modifier.padding(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) { Text(v.date ?: "", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Gray); Spacer(Modifier.weight(1f))
                                IconButton(onClick = { scope.launch { try { supabase.from("visits").delete { filter { eq("id", v.id ?: "") } }; visits.remove(v) } catch(e: Exception){} } }, Modifier.size(20.dp)) { Icon(Icons.Default.Delete, null, tint = Color.Red.copy(0.3f)) } }
                            var vT by remember { mutableStateOf(v.treatment ?: "") }
                            BasicTextField(value = vT, onValueChange = { vT = it }, modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp))
                            TextButton(onClick = { scope.launch { try { supabase.from("visits").update(mapOf("treatment" to vT)) { filter { eq("id", v.id ?: "") } } } catch(e: Exception){} } }, Modifier.align(Alignment.End).height(24.dp)) { Text("Guardar Registro", fontSize = 9.sp) }
                        }
                    }
                }
                TextButton(onClick = { onDelete(customer) }, modifier = Modifier.fillMaxWidth()) { Text("Eliminar Cliente", color = Color.Red, fontSize = 11.sp) }
            }
        } 
    }, confirmButton = { Button(onClick = { if(name.isNotEmpty()) { onConfirm(Customer(id = customer?.id, name = name, phone = phone, last_visit = "Hoy", technical_notes = notes, habitual_treatment = habitualT)); onDismiss() } }) { Text("Guardar Todo") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
    if (showAddA && customer != null) {
        var datePick by remember { mutableStateOf(Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date.toString()) }
        AlertDialog(onDismissRequest = { showAddA = false }, title = { Text("Agendar Cita") }, text = {
            Column {
                OutlinedTextField(value = datePick, onValueChange = { datePick = it }, label = { Text("Fecha (YYYY-MM-DD)") })
                Spacer(Modifier.height(8.dp))
                ManualAppointmentDialog(datePick, initialName = customer.name ?: "", initialPhone = customer.phone ?: "", onDismiss = { showAddA = false }, onConfirm = { a -> onAddAppointment(customer, a); showAddA = false }, isEmbedded = true)
            }
        }, confirmButton = {})
    }
}

@Composable
fun ManualAppointmentDialog(date: String, initialName: String = "", initialPhone: String = "", onDismiss: () -> Unit, onConfirm: (Appointment) -> Unit, isEmbedded: Boolean = false) {
    var n by remember { mutableStateOf(initialName) }; var p by remember { mutableStateOf(initialPhone) }; var s by remember { mutableStateOf("Servicio") }; var t by remember { mutableStateOf("09:00") }
    val hours = listOf("09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "12:00", "16:00", "16:30", "17:00", "17:30", "18:00", "18:30", "19:00")
    val content = @Composable {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            if(!isEmbedded) {
                OutlinedTextField(value = n, onValueChange = { n = it }, label = { Text("Nombre") })
                OutlinedTextField(value = p, onValueChange = { p = it }, label = { Text("Teléfono") })
            }
            OutlinedTextField(value = s, onValueChange = { s = it }, label = { Text("Servicio") })
            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) { items(hours) { h -> FilterChip(selected = t == h, onClick = { t = h }, label = { Text(h) }) } }
            Button(onClick = { if(n.isNotEmpty()) { onConfirm(Appointment(date = date, time = t, client_name = n, service = s, phone = p)); onDismiss() } }, Modifier.fillMaxWidth()) { Text("Anotar Cita") }
        }
    }
    if (isEmbedded) content()
    else AlertDialog(onDismissRequest = onDismiss, title = { Text("Nueva Cita") }, text = { content() }, confirmButton = {}, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun BlockSlotsDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    val hours = listOf("09:00", "09:30", "10:00", "10:30", "11:00", "11:30", "12:00", "16:00", "16:30", "17:00", "17:30", "18:00", "18:30", "19:00")
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Bloquear Hora") }, text = { LazyColumn(Modifier.height(300.dp)) { items(hours) { h -> TextButton(onClick = { onConfirm(h); onDismiss() }, Modifier.fillMaxWidth()) { Text("Bloquear $h", color = Color.Red) } } } }, confirmButton = { TextButton(onClick = onDismiss) { Text("Cerrar") } })
}

@Composable
fun UpcomingAppointmentsScreen(appointments: List<Appointment>, actions: PlatformActions, onRefresh: () -> Unit, onOpenDrawer: () -> Unit) {
    val today = Clock.System.now().toLocalDateTime(TimeZone.currentSystemDefault()).date
    val futureAppointments = appointments.filter { 
        val d = try { LocalDate.parse(it.date ?: "") } catch(e:Exception) { null }
        d != null && d >= today && it.client_name != "BLOQUEADO"
    }.sortedWith(compareBy({ it.date ?: "" }, { it.time ?: "" }))
    val grouped = futureAppointments.groupBy { it.date }
    Column(Modifier.fillMaxSize().background(Color(0xFFF8F9FB))) {
        HeaderPremium("Avisos", "Recordatorios", actions, onMenuClick = onOpenDrawer)
        LazyColumn(Modifier.padding(16.dp)) {
            grouped.forEach { (date, appts) ->
                item { Text(date ?: "", fontWeight = FontWeight.Bold, color = Color.Gray, fontSize = 12.sp, modifier = Modifier.padding(8.dp)) }
                items(appts) { appt ->
                    Surface(shape = RoundedCornerShape(12.dp), border = BorderStroke(1.dp, Color(0xFFF0F0F0)), modifier = Modifier.padding(vertical = 4.dp)) {
                        Row(Modifier.padding(12.dp).fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text(appt.time ?: "", fontWeight = FontWeight.Bold, modifier = Modifier.width(60.dp))
                            Column(Modifier.weight(1f)) { Text(appt.client_name ?: "", fontWeight = FontWeight.Bold); Text(appt.service ?: "", fontSize = 12.sp) }
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
        Text(text, color = Color.DarkGray, fontSize = 14.sp, lineHeight = 20.sp)
    }
}
