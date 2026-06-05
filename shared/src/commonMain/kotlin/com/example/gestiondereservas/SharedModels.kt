package com.example.gestiondereservas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class Appointment(
    @SerialName("id") val id: String? = null,
    @SerialName("date") val date: String? = "",
    @SerialName("time") val time: String? = "",
    @SerialName("client_name") val client_name: String? = "",
    @SerialName("service") val service: String? = "",
    @SerialName("phone") val phone: String? = "",
    @SerialName("reminder_sent") val reminder_sent: Boolean = false
)

@Serializable
data class Customer(
    @SerialName("id") val id: String? = null,
    @SerialName("name") var name: String? = "",
    @SerialName("phone") var phone: String? = "",
    @SerialName("last_visit") var last_visit: String? = "Hoy",
    @SerialName("technical_notes") var technical_notes: String? = "",
    @SerialName("habitual_treatment") var habitual_treatment: String? = "" // Nuevo campo
)

@Serializable
data class Visit(
    @SerialName("id") val id: String? = null,
    @SerialName("customer_id") val customer_id: String? = "",
    @SerialName("date") val date: String? = "",
    @SerialName("treatment") var treatment: String? = "", // El tratamiento aplicado ese día
    @SerialName("notes") var notes: String? = ""
)

@Serializable
data class Vacation(@SerialName("date") val date: String? = "")

const val MI_WEB_RESERVAS = "https://jlosemp-cloud.github.io/reservas/reserva.html"
