package com.example.gestiondereservas

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.example.gestiondereservas.ui.theme.GESTIONDERESERVASTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        
        val actions = AndroidPlatformActions(this)
        
        setContent {
            GESTIONDERESERVASTheme {
                MainNavigationContainer(actions = actions)
            }
        }
    }
}

class AndroidPlatformActions(private val context: Context) : PlatformActions {
    override fun makeCall(phone: String?) {
        if (phone.isNullOrEmpty()) return
        try {
            val intent = Intent(Intent.ACTION_DIAL).apply { data = Uri.parse("tel:$phone") }
            context.startActivity(intent)
        } catch (e: Exception) {
            showToast("Error al llamar")
        }
    }

    override fun shareLink(message: String) {
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, message)
        }
        context.startActivity(Intent.createChooser(intent, "Jesús, Peluquería de Autor"))
    }

    override fun sendWhatsApp(phone: String?, message: String) {
        if (phone.isNullOrEmpty()) return
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=${Uri.encode(message)}"))
            context.startActivity(intent)
        } catch (e: Exception) {
            showToast("Error al abrir WhatsApp")
        }
    }

    override fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
