package com.example.taller3

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.StrictMode
import android.util.Log
import android.view.View
import android.widget.ImageButton
import android.widget.PopupMenu
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.example.taller3.databinding.ActivityPosicionActualBinding
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationCallback
import com.google.android.gms.location.LocationRequest
import com.google.android.gms.location.LocationResult
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.DatabaseReference
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.osmdroid.api.IMapController
import org.osmdroid.bonuspack.routing.OSRMRoadManager
import org.osmdroid.bonuspack.routing.RoadManager
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import org.osmdroid.views.overlay.Polyline
import org.osmdroid.views.overlay.mylocation.GpsMyLocationProvider
import org.osmdroid.views.overlay.mylocation.MyLocationNewOverlay

class PosicionActual : AppCompatActivity() {

    lateinit var binding: ActivityPosicionActualBinding

    private lateinit var database: FirebaseDatabase
    private lateinit var referenciaUsuario: DatabaseReference
    private lateinit var map: MapView
    private lateinit var controller: IMapController
    private lateinit var marker: Marker
    private lateinit var locationOverlay: MyLocationNewOverlay
    lateinit var roadManager: RoadManager
    private var roadOverlay: Polyline? = null
    private lateinit var btnMenu: ImageButton

    private var lastLocation: GeoPoint? = null

    //Crear proveedor de localizacion
    lateinit var mFusedLocationProviderClient: FusedLocationProviderClient

    //Suscribirno a cambios
    lateinit var mLocationRequest: LocationRequest

    //callback a localizacion
    private lateinit var mLocationCallback: LocationCallback

    private var userGeopoint: GeoPoint? = null


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Configuration.getInstance().load(applicationContext, getSharedPreferences("osmdroid", MODE_PRIVATE))

        //Recibimo el usuario del intent
        val user = intent.extras
        val nombre = user!!.getString("nombre")
        val apellido = user.getString("apellido")
        val usuarioId = user.getString("usuarioId")
        val latitud = user.getDouble("latitud")
        val longitud = user.getDouble("longitud")


        binding = ActivityPosicionActualBinding.inflate(layoutInflater)

        //Inicializar el proveedor
        mFusedLocationProviderClient = LocationServices.getFusedLocationProviderClient(this)

        //Metodo propio
        mLocationRequest = createLocationRequest()
        setContentView(binding.root)

        roadManager = OSRMRoadManager(this, "ANDROID")

        map = findViewById(R.id.map)
        map.setTileSource(TileSourceFactory.MAPNIK)
        map.setMultiTouchControls(true)

        controller = map.controller
        controller.setZoom(18.0)

        // Inicializar overlay de ubicación
        locationOverlay = MyLocationNewOverlay(GpsMyLocationProvider(this), map)
        locationOverlay.enableMyLocation()
        locationOverlay.enableFollowLocation()
        map.overlays.add(locationOverlay)

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), 1001)
            return
        }

        locationOverlay.runOnFirstFix {
            val location = locationOverlay.myLocation
            runOnUiThread {
                location?.let {
                    val point = GeoPoint(it.latitude, it.longitude)
                    controller.animateTo(point)
                    lastLocation = it
                }
            }
        }

        database = FirebaseDatabase.getInstance()
        referenciaUsuario = database.getReference("usuarios").child(usuarioId!!)

        // Escuchar cambios en latitud y longitud en tiempo real
        referenciaUsuario.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                val lat = snapshot.child("latitud").getValue(Double::class.java)
                val lon = snapshot.child("longitud").getValue(Double::class.java)

                if (lat != null && lon != null) {
                    val updatedPoint = GeoPoint(lat, lon)
                    userGeopoint = updatedPoint

                    // Si ya tienes una ubicación actual válida, dibuja la ruta
                    locationOverlay.myLocation?.let { myLoc ->
                        drawLine(GeoPoint(myLoc.latitude, myLoc.longitude), updatedPoint)
                    }
                    colocarMarcador(updatedPoint, "$nombre $apellido")
                }
            }

            override fun onCancelled(error: DatabaseError) {
                Toast.makeText(this@PosicionActual, "Error cargando ubicación: ${error.message}", Toast.LENGTH_SHORT).show()
            }
        })


        mLocationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                val location = locationResult.lastLocation
                Log.i("LOCATION", "Location update in the callback: $location")
                if (location != null) {
                    val point = GeoPoint(location.latitude,location.longitude)
                    if (lastLocation == null || point.distanceToAsDouble(lastLocation) > 30) {
                        lastLocation = point
                        userGeopoint?.let {
                            drawLine(point, it)
                        }
                        controller.animateTo(point)
                    }
                }
            }
        }
        startLocationUpdates()
    }

    private fun colocarMarcador(geoPoint: GeoPoint, titulo: String) {
        if (::marker.isInitialized) {
            map.overlays.remove(marker)
        }

        marker = Marker(map)
        marker.position = geoPoint
        marker.setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM)
        marker.title = titulo
        map.overlays.add(marker)
    }

    private fun drawRoute(start: GeoPoint, finish: GeoPoint) {
        lifecycleScope.launch(Dispatchers.IO) {
            val routePoints = arrayListOf(start, finish)
            try {
                val road = roadManager.getRoad(routePoints)

                withContext(Dispatchers.Main) {
                    Log.i("OSM_acticity", "Route length: ${road.mLength} km")
                    Log.i("OSM_acticity", "Duration: ${road.mDuration / 60} min")

                    roadOverlay?.let { map.overlays.remove(it) }
                    roadOverlay = RoadManager.buildRoadOverlay(road)
                    map.overlays.add(roadOverlay)
                    map.invalidate()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(this@PosicionActual, "Error obteniendo la ruta: ${e.message}", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun drawLine(start: GeoPoint, finish: GeoPoint) {
        roadOverlay?.let { map.overlays.remove(it) }

        val line = Polyline()
        line.setPoints(listOf(start, finish))
        line.outlinePaint.color = android.graphics.Color.BLUE
        line.outlinePaint.strokeWidth = 5f

        roadOverlay = line
        map.overlays.add(roadOverlay)
        map.invalidate()
    }



    private fun startLocationUpdates() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION)
            == PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(
                this,
                android.Manifest.permission.ACCESS_COARSE_LOCATION
            ) == PackageManager.PERMISSION_GRANTED)
        {
            mFusedLocationProviderClient.requestLocationUpdates(mLocationRequest,mLocationCallback,null)
        }
    }


    //Constructor de peticiones para cambios en localizacion
    private fun createLocationRequest(): LocationRequest =
        LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY,10000).apply { setMinUpdateIntervalMillis(5000) }.build()

    override fun onResume() {
        super.onResume()
        map.onResume()
    }

    override fun onPause() {
        super.onPause()
        map.onPause()
    }
}