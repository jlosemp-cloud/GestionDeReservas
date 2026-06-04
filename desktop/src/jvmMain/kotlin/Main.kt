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
    val windowState = rememberWindowState(width = 1024.dp, height = 768.dp)
    
    Window(
        onCloseRequest = ::exitApplication,
        title = "Gestión de Reservas - Jesús Peluquería",
        state = windowState
    ) {
        val actions = DesktopPlatformActions()
        MainNavigationContainer(actions = actions)
    }
}

class DesktopPlatformActions : PlatformActions {
    override fun makeCall(phone: String?) {
        println("Llamando a: $phone (Simulado en Desktop)")
    }

    override fun shareLink(message: String) {
        println("Compartiendo: $message")
    }

    override fun sendWhatsApp(phone: String?, message: String) {
        if (phone.isNullOrEmpty()) return
        try {
            val encodedMsg = URLEncoder.encode(message, "UTF-8")
            val url = "https://wa.me/$phone?text=$encodedMsg"
            if (Desktop.isDesktopSupported()) {
                Desktop.getDesktop().browse(URI(url))
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    override fun showToast(message: String) {
        println("NOTIFICACIÓN: $message")
    }
}
