package com.example.taller3

import android.content.Intent
import android.graphics.BitmapFactory
import android.os.Bundle
import android.widget.Button
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.taller3.databinding.ActivityDisponiblesBinding
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.google.firebase.storage.FirebaseStorage
import org.json.JSONObject

class Disponibles : AppCompatActivity() {

    private lateinit var binding: ActivityDisponiblesBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityDisponiblesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Cargar los usuarios disponibles desde Firebase
        cargarUsuariosDisponibles()
    }

    // Cargar los usuarios disponibles (estado == "Disponible") desde Firebase Realtime Database
    private fun cargarUsuariosDisponibles() {
        val usuariosRef = FirebaseDatabase.getInstance().getReference("usuarios")

        // Realizamos la consulta para obtener usuarios con estado "Disponible"
        usuariosRef.orderByChild("estado").equalTo("Disponible").addValueEventListener(object :
            ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                // Limpiamos la vista de usuarios previos
                binding.listadoDisponibles.removeAllViews()

                for (usuarioSnapshot in snapshot.children) {
                    val nombre = usuarioSnapshot.child("nombre").value.toString()
                    val apellido = usuarioSnapshot.child("apellido").value.toString()
                    val fotoPerfilUrl = usuarioSnapshot.child("fotoPerfilUrl").value.toString() // Asumimos que está en "fotoPerfilUrl"
                    val latitud = usuarioSnapshot.child("latitud").value.toString().toDouble()
                    val longitud = usuarioSnapshot.child("longitud").value.toString().toDouble()
                    val usuarioId = usuarioSnapshot.child("uid").value.toString()  // Asumimos que tienes un campo "uid"

                    // Crear y mostrar el bloque del usuario
                    crearBloqueUsuario(nombre, apellido, usuarioId, latitud, longitud)
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@Disponibles, "Error al cargar usuarios", Toast.LENGTH_SHORT).show()
            }
        })
    }

    // Crear un bloque de usuario en la interfaz
    private fun crearBloqueUsuario(nombre: String, apellido: String, usuarioId: String, latitud: Double, longitud: Double) {
        val inflater = layoutInflater
        val bloqueUsuarioView = inflater.inflate(R.layout.bloque_usuario, null)

        // Configurar el nombre
        val nombreTextView = bloqueUsuarioView.findViewById<TextView>(R.id.us_nombre)
        nombreTextView.text = "$nombre $apellido"

        // Cargar la foto de perfil desde Firebase Storage
        val fotoPerfilImageView = bloqueUsuarioView.findViewById<ImageView>(R.id.us_foto)
        cargarImagenUsuario(usuarioId, fotoPerfilImageView)

        // Configurar el botón "Ver ubicación"
        val btnVerUbicacion = bloqueUsuarioView.findViewById<Button>(R.id.btn_ubicacion)
        btnVerUbicacion.setOnClickListener {
            val intent = Intent(this, PosicionActual::class.java)

            val bundle = Bundle().apply {
                putString("nombre", nombre)
                putString("apellido", apellido)
                putString("usuarioId", usuarioId)
                putDouble("latitud", latitud)
                putDouble("longitud", longitud)
            }

            intent.putExtras(bundle)
            startActivity(intent)
        }


        // Agregar el bloque a un contenedor en la actividad
        val listadoDisponibles = findViewById<LinearLayout>(R.id.listado_disponibles)  // Asegúrate de tener un LinearLayout en tu layout
        listadoDisponibles.addView(bloqueUsuarioView)
    }

    // Función para cargar la imagen del usuario desde Firebase Storage
    private fun cargarImagenUsuario(usuarioId: String, imgProfile: ImageView) {
        // Aquí cargamos la imagen del usuario desde Firebase Storage
        val storageRef = FirebaseStorage.getInstance().reference.child("imagenes_perfil/$usuarioId.jpg")

        val ONE_MEGABYTE: Long = 1024 * 1024 // (1MB máximo para la imagen)

        storageRef.getBytes(ONE_MEGABYTE).addOnSuccessListener { bytes ->
            val bitmap = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            imgProfile.setImageBitmap(bitmap)  // Asignamos la imagen descargada al ImageView
        }.addOnFailureListener {
            imgProfile.setImageResource(R.drawable.ic_disponibles) // Imagen por defecto si no hay imagen en Storage
        }
    }
}
