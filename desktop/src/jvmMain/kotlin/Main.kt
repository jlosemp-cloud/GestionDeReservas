import androidx.compose.ui.window.Window
import androidx.compose.ui.window.application
import androidx.compose.ui.window.rememberWindowState
import androidx.compose.ui.unit.dp
import com.example.gestiondereservas.MainNavigationContainer
import com.example.gestiondereservas.PlatformActions
import java.awt.Desktop
import java.net.URI
import java.net.URLEncoder

fun main() = application {
    val windowState = rememberWindowState(width = 1100.dp, height = 800.dp)
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Gestión de Reservas - Edición Profesional",
        state = windowState
    ) {
        val actions = object : PlatformActions {
            override val isDesktop: Boolean = true // ACTIVA EL MODO ESCRITORIO
            override fun makeCall(phone: String?) = println("Simulando llamada a: $phone")
            override fun shareLink(message: String) = println("Enlace copiado")
            override fun sendWhatsApp(phone: String?, message: String) {
                if (phone.isNullOrEmpty()) return
                try {
                    val url = "https://wa.me/$phone?text=${URLEncoder.encode(message, "UTF-8")}"
                    Desktop.getDesktop().browse(URI(url))
                } catch (e: Exception) { e.printStackTrace() }
            }
            override fun showToast(message: String) = println("AVISO: $message")
        }
        MainNavigationContainer(actions = actions)
    }
}
