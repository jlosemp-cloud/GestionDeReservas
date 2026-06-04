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
        title = "Gestión de Reservas - Desktop",
        state = windowState
    ) {
        val actions = object : PlatformActions {
            override fun makeCall(phone: String?) = println("Llamando a: $phone")
            override fun shareLink(message: String) = println("Compartiendo: $message")
            override fun sendWhatsApp(phone: String?, message: String) {
                if (phone.isNullOrEmpty()) return
                try {
                    val url = "https://wa.me/$phone?text=${URLEncoder.encode(message, "UTF-8")}"
                    if (Desktop.isDesktopSupported()) {
                        Desktop.getDesktop().browse(URI(url))
                    }
                } catch (e: Exception) { e.printStackTrace() }
            }
            override fun showToast(message: String) = println("Notificación: $message")
        }
        MainNavigationContainer(actions = actions)
    }
}
