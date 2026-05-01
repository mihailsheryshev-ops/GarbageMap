package com.example.garbagemap

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.bottomnavigation.BottomNavigationView
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.FirebaseDatabase
import com.google.firebase.database.ValueEventListener
import com.yandex.mapkit.MapKitFactory
import com.yandex.mapkit.geometry.Point
import com.yandex.mapkit.map.CameraPosition
import com.yandex.mapkit.map.InputListener
import com.yandex.mapkit.map.Map
import com.yandex.mapkit.mapview.MapView
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

class MainActivity : AppCompatActivity() {

    private lateinit var mapView: MapView
    private lateinit var mapContainer: FrameLayout
    private lateinit var listContainer: LinearLayout
    private val database = FirebaseDatabase.getInstance().reference.child("markers")
    private val LOCATION_PERMISSION_REQUEST = 1
    private val markersList = mutableListOf<Triple<String, Double, Double>>()

    override fun onCreate(savedInstanceState: Bundle?) {
        MapKitFactory.setApiKey("208eacbd-7162-4e9c-8033-b5faea72ad9d")
        MapKitFactory.initialize(this)
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            layoutParams = android.view.ViewGroup.LayoutParams(-1, -1)
        }

        mapContainer = FrameLayout(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
        }

        mapView = MapView(this).apply {
            layoutParams = FrameLayout.LayoutParams(-1, -1)
        }
        mapContainer.addView(mapView)

        val fab = FloatingActionButton(this).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT
            ).also {
                it.gravity = android.view.Gravity.BOTTOM or android.view.Gravity.END
                it.setMargins(0, 0, 32, 32)
            }
            setImageResource(android.R.drawable.ic_menu_mylocation)
        }
        mapContainer.addView(fab)

        val scrollView = ScrollView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, 0, 1f)
            visibility = android.view.View.GONE
        }

        listContainer = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(16, 16, 16, 16)
        }
        scrollView.addView(listContainer)

        val bottomNav = BottomNavigationView(this).apply {
            layoutParams = LinearLayout.LayoutParams(-1, -2)
            inflateMenu(R.menu.bottom_nav_menu)
        }

        root.addView(mapContainer)
        root.addView(scrollView)
        root.addView(bottomNav)
        setContentView(root)

        mapView.mapWindow.map.move(
            CameraPosition(Point(55.75, 37.61), 10f, 0f, 0f)
        )

        loadMarkers()

        mapView.mapWindow.map.addInputListener(object : InputListener {
            override fun onMapTap(map: Map, point: Point) {
                AlertDialog.Builder(this@MainActivity)
                    .setTitle("Добавить метку?")
                    .setMessage("Отметить это место как место скопления мусора?")
                    .setPositiveButton("Добавить") { _, _ ->
                        addMarker(point.latitude, point.longitude)
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            }
            override fun onMapLongTap(map: Map, point: Point) {}
        })

        fab.setOnClickListener {
            checkLocationPermission()
        }

        bottomNav.setOnItemSelectedListener { item ->
            when (item.itemId) {
                R.id.nav_map -> {
                    mapContainer.visibility = android.view.View.VISIBLE
                    scrollView.visibility = android.view.View.GONE
                    true
                }
                R.id.nav_list -> {
                    mapContainer.visibility = android.view.View.GONE
                    scrollView.visibility = android.view.View.VISIBLE
                    updateList()
                    true
                }
                else -> false
            }
        }
    }

    private fun addMarker(lat: Double, lng: Double) {
        val key = database.push().key ?: return
        val marker = mapOf("lat" to lat, "lng" to lng)
        database.child(key).setValue(marker)
        Toast.makeText(this, "Метка добавлена!", Toast.LENGTH_SHORT).show()
    }

    private fun loadMarkers() {
        database.addValueEventListener(object : ValueEventListener {
            override fun onDataChange(snapshot: DataSnapshot) {
                markersList.clear()
                mapView.mapWindow.map.mapObjects.clear()
                for (child in snapshot.children) {
                    val key = child.key ?: continue
                    val lat = child.child("lat").getValue(Double::class.java) ?: continue
                    val lng = child.child("lng").getValue(Double::class.java) ?: continue
                    markersList.add(Triple(key, lat, lng))
                    mapView.mapWindow.map.mapObjects.addPlacemark(Point(lat, lng))
                }
            }
            override fun onCancelled(error: DatabaseError) {}
        })
    }

    private fun updateList() {
        listContainer.removeAllViews()
        markersList.forEachIndexed { idx, (key, lat, lng) ->
            val card = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                setPadding(24, 24, 24, 24)
                setBackgroundColor(Color.WHITE)
                val params = LinearLayout.LayoutParams(-1, LinearLayout.LayoutParams.WRAP_CONTENT)
                params.setMargins(0, 0, 0, 16)
                layoutParams = params
            }
            val text = TextView(this).apply {
                this.text = "Метка ${idx + 1}\n%.4f, %.4f".format(lat, lng)
                textSize = 14f
                layoutParams = LinearLayout.LayoutParams(0, -2, 1f)
            }
            val deleteBtn = android.widget.Button(this).apply {
                this.text = "Удалить"
                setOnClickListener {
                    database.child(key).removeValue()
                    Toast.makeText(this@MainActivity, "Метка удалена", Toast.LENGTH_SHORT).show()
                }
            }
            card.addView(text)
            card.addView(deleteBtn)
            listContainer.addView(card)
        }

        if (markersList.isEmpty()) {
            val empty = TextView(this).apply {
                this.text = "Меток пока нет"
                textSize = 16f
                gravity = android.view.Gravity.CENTER
                setPadding(0, 64, 0, 0)
            }
            listContainer.addView(empty)
        }
    }

    private fun checkLocationPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
            != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                LOCATION_PERMISSION_REQUEST)
        }
    }

    override fun onStart() {
        super.onStart()
        MapKitFactory.getInstance().onStart()
        mapView.onStart()
    }

    override fun onStop() {
        mapView.onStop()
        MapKitFactory.getInstance().onStop()
        super.onStop()
    }
}