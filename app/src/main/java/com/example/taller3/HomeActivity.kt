package com.example.taller3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import org.json.JSONObject
import org.osmdroid.api.IMapController
import org.osmdroid.config.Configuration
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class HomeActivity : AppCompatActivity() {

    private lateinit var map: MapView
    private lateinit var controller: IMapController
    private val REQUEST_PERMISSIONS_REQUEST_CODE = 1
    private lateinit var btnMenu: ImageButton
    private lateinit var auth: FirebaseAuth
    private lateinit var database: DatabaseReference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))
        setContentView(R.layout.activity_home)

        map = findViewById(R.id.map)
        map.setMultiTouchControls(true)
        controller = map.controller
        controller.setZoom(14.0)

        requestPermissionsIfNecessary()

        // Cargar puntos desde el archivo JSON y poner los marcadores
        agregarPuntosDeInteres()

        auth = FirebaseAuth.getInstance()
        database = FirebaseDatabase.getInstance().reference

        btnMenu = findViewById(R.id.btnMenu)
        setupPopupMenu()
    }

    private fun setupPopupMenu() {
        btnMenu.setOnClickListener { view ->
            showPopupMenu(view)
        }
    }

    private fun showPopupMenu(view: View) {
        val popupMenu = PopupMenu(this, view)
        popupMenu.menuInflater.inflate(R.menu.menu_opciones, popupMenu.menu)

        // Forzar íconos visibles
        try {
            val fields = popupMenu.javaClass.declaredFields
            for (field in fields) {
                if ("mPopup" == field.name) {
                    field.isAccessible = true
                    val menuPopupHelper = field.get(popupMenu)
                    val classPopupHelper = Class.forName(menuPopupHelper.javaClass.name)
                    val setForceIcons = classPopupHelper.getMethod("setForceShowIcon", Boolean::class.javaPrimitiveType)
                    setForceIcons.invoke(menuPopupHelper, true)
                    break
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }

        popupMenu.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.action_logout -> {
                    logoutUser()
                    true
                }
                R.id.action_status -> {
                    toggleUserStatus()
                    true
                }
                else -> false
            }
        }
        popupMenu.show()
    }

    private fun logoutUser() {
        auth.signOut()
        val intent = Intent(this, LoginActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        finish()
    }

    private fun toggleUserStatus() {
        val userId = auth.currentUser?.uid ?: return
        val statusRef = database.child("usuarios").child(userId).child("estado")

        statusRef.get().addOnSuccessListener { snapshot ->
            val estadoActual = snapshot.getValue(String::class.java)
            val nuevoEstado = if (estadoActual == "Disponible") "Desconectado" else "Disponible"

            statusRef.setValue(nuevoEstado).addOnSuccessListener {
                Toast.makeText(this, "Estado actualizado: $nuevoEstado", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun requestPermissionsIfNecessary() {
        val permissions = arrayOf(
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION
        )

        val permissionsToRequest = permissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), REQUEST_PERMISSIONS_REQUEST_CODE)
        } else {
            mostrarUbicacionActual()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSIONS_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                mostrarUbicacionActual()
            } else {
                Toast.makeText(this, "Permisos de ubicación denegados", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun mostrarUbicacionActual() {
        val locationOverlay = MyLocationNewOverlay(map)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        map.overlays.add(locationOverlay)

        locationOverlay.runOnFirstFix {
            val location = locationOverlay.myLocation
            if (location != null) {
                val geoPoint = GeoPoint(location.latitude, location.longitude)
                runOnUiThread {
                    controller.setCenter(geoPoint)
                    val marker = Marker(map)
                    marker.position = geoPoint
                    marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                    marker.title = "Tu ubicación actual"
                    map.overlays.add(marker)
                    map.invalidate()
                }
            }
        }
    }

    private fun agregarPuntosDeInteres() {
        try {
            val inputStream = assets.open("locations.json")
            val jsonString = inputStream.bufferedReader().use { it.readText() }

            val jsonObject = JSONObject(jsonString)
            val locationsArray = jsonObject.getJSONArray("locationsArray")

            for (i in 0 until locationsArray.length()) {
                val location = locationsArray.getJSONObject(i)
                val lat = location.getDouble("latitude")
                val lon = location.getDouble("longitude")
                val name = location.getString("name")

                val point = GeoPoint(lat, lon)
                val marker = Marker(map)
                marker.position = point
                marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
                marker.title = name
                map.overlays.add(marker)
            }

            map.invalidate()
        } catch (e: Exception) {
            Toast.makeText(this, "Error al cargar el JSON: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
