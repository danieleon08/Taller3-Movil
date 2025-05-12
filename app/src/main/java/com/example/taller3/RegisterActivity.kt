package com.example.taller3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.storage.FirebaseStorage
import java.io.File
import java.io.FileOutputStream

class RegisterActivity : AppCompatActivity() {

    private lateinit var etNombre: EditText
    private lateinit var etApellido: EditText
    private lateinit var etIdentificacion: EditText
    private lateinit var etEmail: EditText
    private lateinit var etPassword: EditText
    private lateinit var btnCrearCuenta: Button
    private lateinit var btnTomarFoto: Button
    private lateinit var imagePerfil: ImageView

    private var imageUri: Uri? = null

    private lateinit var auth: FirebaseAuth

    companion object {
        private const val TAG = "RegisterActivity"
        private const val REQUEST_IMAGE_CAPTURE = 1
        private const val REQUEST_CAMERA_PERMISSION = 100
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_register)

        auth = FirebaseAuth.getInstance()

        etNombre = findViewById(R.id.etNombre)
        etApellido = findViewById(R.id.etApellido)
        etIdentificacion = findViewById(R.id.etIdentificacion)
        etEmail = findViewById(R.id.etEmail)
        etPassword = findViewById(R.id.etPassword)
        btnCrearCuenta = findViewById(R.id.btnCrearCuenta)
        btnTomarFoto = findViewById(R.id.btnTomarFoto)
        imagePerfil = findViewById(R.id.imagePerfil)

        btnTomarFoto.setOnClickListener {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.CAMERA),
                    REQUEST_CAMERA_PERMISSION
                )
            } else {
                openCamera()
            }
        }

        btnCrearCuenta.setOnClickListener {
            val nombre = etNombre.text.toString()
            val apellido = etApellido.text.toString()
            val identificacion = etIdentificacion.text.toString()
            val email = etEmail.text.toString()
            val password = etPassword.text.toString()

            if (nombre.isEmpty() || apellido.isEmpty() || identificacion.isEmpty()
                || email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Por favor completa todos los campos.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (!isEmailValid(email)) {
                Toast.makeText(this, "Correo electrónico inválido.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            if (password.length < 6) {
                Toast.makeText(this, "La contraseña debe tener al menos 6 caracteres.", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            // Crear usuario en Firebase Auth
            auth.createUserWithEmailAndPassword(email, password)
                .addOnCompleteListener(this) { task ->
                    if (task.isSuccessful) {
                        val userId = auth.currentUser?.uid
                        if (userId != null) {
                            val userMap = mapOf(
                                "uid" to userId,
                                "nombre" to nombre,
                                "apellido" to apellido,
                                "email" to email,
                                "contraseña" to password,
                                "identificacion" to identificacion,
                                "latitud" to 0.0,
                                "longitud" to 0.0
                            )

                            FirebaseDatabase.getInstance().reference
                                .child("usuarios").child(userId).setValue(userMap)
                                .addOnSuccessListener {
                                    if (imageUri != null) {
                                        val storageRef = FirebaseStorage.getInstance().reference
                                            .child("imagenes_perfil/$userId.jpg")

                                        storageRef.putFile(imageUri!!)
                                            .addOnSuccessListener {
                                                Toast.makeText(
                                                    this,
                                                    "Cuenta e imagen creadas exitosamente.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                                startActivity(Intent(this, HomeActivity::class.java))
                                                finish()
                                            }
                                            .addOnFailureListener {
                                                Toast.makeText(
                                                    this,
                                                    "Usuario creado, pero error al subir imagen.",
                                                    Toast.LENGTH_SHORT
                                                ).show()
                                            }
                                    } else {
                                        Toast.makeText(this, "Cuenta creada sin imagen.", Toast.LENGTH_SHORT).show()
                                        startActivity(Intent(this, HomeActivity::class.java))
                                        finish()
                                    }
                                }
                                .addOnFailureListener {
                                    Toast.makeText(this, "Error al guardar datos.", Toast.LENGTH_SHORT).show()
                                }
                        }
                    } else {
                        Log.w(TAG, "createUserWithEmail:failure", task.exception)
                        Toast.makeText(this, "Error al crear cuenta: ${task.exception?.message}", Toast.LENGTH_SHORT).show()
                    }
                }
        }
    }

    private fun openCamera() {
        val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
        if (intent.resolveActivity(packageManager) != null) {
            startActivityForResult(intent, REQUEST_IMAGE_CAPTURE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CAMERA_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                openCamera()
            } else {
                Toast.makeText(this, "Permiso de cámara denegado.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_IMAGE_CAPTURE && resultCode == RESULT_OK) {
            val bitmap = data?.extras?.get("data") as? Bitmap
            bitmap?.let {
                imagePerfil.setImageBitmap(it)
                val file = File.createTempFile("profile_", ".jpg", cacheDir)
                val out = FileOutputStream(file)
                it.compress(Bitmap.CompressFormat.JPEG, 100, out)
                out.flush()
                out.close()
                imageUri = Uri.fromFile(file)
            }
        }
    }

    private fun isEmailValid(email: String): Boolean {
        return email.contains("@") && email.contains(".")
    }
}
