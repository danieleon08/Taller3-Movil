package com.example.taller3

import android.app.IntentService
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener

class UsuarioDisponibleService : Service() {

    private lateinit var database: FirebaseDatabase
    private lateinit var referenciaUsuarios: DatabaseReference
    private var listener: ValueEventListener? = null

    // Guarda el último estado conocido de cada usuario
    private val estadoAnteriorUsuarios = mutableMapOf<String, String>()

    override fun onCreate() {
        super.onCreate()
        Log.d("Servicio", "Servicio iniciado")

        database = FirebaseDatabase.getInstance()
        referenciaUsuarios = database.getReference("usuarios")

        listener = object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                for (usuarioSnapshot in snapshot.children) {
                    val usuarioId = usuarioSnapshot.key ?: continue
                    val estadoActual = usuarioSnapshot.child("estado").getValue(String::class.java) ?: continue

                    val estadoAnterior = estadoAnteriorUsuarios[usuarioId]

                    // Detectar cambio de "Desconectado" a "Disponible"
                    if (estadoAnterior == "Desconectado" && estadoActual == "Disponible") {
                        val nombre = usuarioSnapshot.child("nombre").getValue(String::class.java) ?: continue
                        val apellido = usuarioSnapshot.child("apellido").getValue(String::class.java) ?: ""
                        val latitud = usuarioSnapshot.child("latitud").getValue(Double::class.java) ?: continue
                        val longitud = usuarioSnapshot.child("longitud").getValue(Double::class.java) ?: continue

                        val mensaje = "$nombre $apellido está ahora disponible"
                        mostrarNotificacion(mensaje, nombre, apellido, usuarioId, latitud, longitud)

                        Log.d("Servicio", "Notificación enviada para $nombre $apellido")
                    }

                    // Actualizar el estado guardado
                    estadoAnteriorUsuarios[usuarioId] = estadoActual
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Log.e("Servicio", "Error en Firebase: ${error.message}")
            }
        }

        referenciaUsuarios.addValueEventListener(listener!!)
    }

    private fun mostrarNotificacion(
        mensaje: String,
        nombre: String,
        apellido: String,
        usuarioId: String,
        latitud: Double,
        longitud: Double
    ) {
        // Crear el Bundle
        val bundle = Bundle().apply {
            putString("nombre", nombre)
            putString("apellido", apellido)
            putString("usuarioId", usuarioId)
            putDouble("latitud", latitud)
            putDouble("longitud", longitud)
        }

        // Crear el Intent y añadir el Bundle
        val intent = Intent(this, PosicionActual::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
            putExtras(bundle) // Agregar el Bundle al Intent
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, "usuarios_channel")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle("Nuevo usuario disponible")
            .setContentText(mensaje)
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify((0..10000).random(), notification)
    }


    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        listener?.let {
            referenciaUsuarios.removeEventListener(it)
        }
        super.onDestroy()
    }
}

