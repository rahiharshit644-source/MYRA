package com.soltini.app.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.util.Log
import androidx.core.content.ContextCompat
import com.soltini.app.agent.AgentStatusBus
import com.soltini.app.audio.JarvisSoundEngine
import com.soltini.app.graph.MyraKnowledgeGraphEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.Locale
import kotlin.math.*

data class LocationSnapshot(
    val isAvailable: Boolean = false,
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val accuracy: Float = 0f,
    val speedKmh: Float = 0f,
    val bearing: Float = 0f,
    val altitude: Double = 0.0,
    val timestamp: Long = 0L,
    val addressText: String = "",
    val subLocality: String = "",
    val locality: String = "",
    val adminArea: String = "",
    val country: String = "",
    val postalCode: String = "",
    val provider: String = "gps"
) {
    fun getReadableSummary(): String {
        if (!isAvailable) return "Location currently unavailable"
        val parts = mutableListOf<String>()
        if (subLocality.isNotBlank()) parts.add(subLocality)
        if (locality.isNotBlank()) parts.add(locality)
        if (adminArea.isNotBlank() && adminArea != locality) parts.add(adminArea)
        val locationDesc = if (parts.isNotEmpty()) parts.joinToString(", ") else "Lat: ${"%.4f".format(latitude)}, Lng: ${"%.4f".format(longitude)}"
        return locationDesc
    }
}

data class DistanceCalculationResult(
    val destination: String,
    val destLatitude: Double,
    val destLongitude: Double,
    val crowFliesDistanceKm: Double,
    val estimatedRoadDistanceKm: Double,
    val estimatedDrivingTimeMinutes: Int,
    val estimatedWalkingTimeMinutes: Int,
    val estimatedBikeTimeMinutes: Int,
    val formattedSpeechText: String,
    val directionCardinal: String
)

data class SavedFavoritePlace(
    val id: String,
    val name: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val category: String = "general",
    val addedTimestamp: Long = System.currentTimeMillis()
)

data class NearbyPlaceMatch(
    val name: String,
    val category: String,
    val address: String,
    val distanceKm: Double,
    val latitude: Double,
    val longitude: Double
)

/**
 * LocationIntelligenceEngine — Real-Time Background GPS & Spatial Intelligence
 *
 * Capabilities:
 *  1. Background Continuous Location Tracking:
 *     Tracks real-time GPS coordinates, speed, bearing, and reverse-geocodes locality & street.
 *  2. Exact Distance & Real-Time ETA Calculator:
 *     Calculates road distance, driving ETA, walking ETA, and Cardinal heading to any destination.
 *  3. Favorite Places & Spatial Memory:
 *     Saves and remembers personal places (Ghar, Office, Gym, Favorite Cafe, Friend's house)
 *     and integrates them seamlessly with the Personal Knowledge Graph.
 *  4. Nearby Amenities & Point of Interest Discovery:
 *     Discovers nearest Petrol Pumps, Hospitals, ATMs, Pharmacies, Metro Stations, and Restaurants.
 *  5. Hands-Free Turn-by-Turn Navigation Launcher:
 *     Deep-links directly into Google Maps with 0-touch navigation.
 */
class LocationIntelligenceEngine private constructor(private val appContext: Context) {

    companion object {
        private const val TAG = "LocationIntelligence"
        private const val PREFS_NAME = "myra_location_intelligence_prefs"
        private const val KEY_SAVED_FAVORITES = "saved_favorite_places"
        private const val UPDATE_MIN_TIME_MS = 10_000L // 10 seconds interval
        private const val UPDATE_MIN_DISTANCE_M = 15f   // 15 meters movement

        @Volatile
        private var instance: LocationIntelligenceEngine? = null

        fun getInstance(context: Context): LocationIntelligenceEngine =
            instance ?: synchronized(this) {
                instance ?: LocationIntelligenceEngine(context.applicationContext).also { instance = it }
            }
    }

    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs: SharedPreferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val locationManager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager

    private val _currentLocation = MutableStateFlow(LocationSnapshot())
    val currentLocation: StateFlow<LocationSnapshot> = _currentLocation.asStateFlow()

    private val _favoritePlaces = MutableStateFlow<List<SavedFavoritePlace>>(loadSavedFavorites())
    val favoritePlaces: StateFlow<List<SavedFavoritePlace>> = _favoritePlaces.asStateFlow()

    private var isTracking = false
    private var lastGeocodedLat = 0.0
    private var lastGeocodedLng = 0.0
    private var lastGeocodeTime = 0L

    private val locationListener = object : LocationListener {
        override fun onLocationChanged(location: Location) {
            handleNewLocation(location)
        }
        @Deprecated("Deprecated in Java")
        override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {}
        override fun onProviderEnabled(provider: String) {
            Log.i(TAG, "Location provider enabled: $provider")
        }
        override fun onProviderDisabled(provider: String) {
            Log.w(TAG, "Location provider disabled: $provider")
        }
    }

    init {
        startLocationTracking()
    }

    /**
     * Checks if location permissions are granted.
     */
    fun hasLocationPermission(): Boolean {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
        return fine || coarse
    }

    /**
     * Starts continuous low-power background location updates.
     */
    @SuppressLint("MissingPermission")
    fun startLocationTracking() {
        if (!hasLocationPermission() || locationManager == null || isTracking) return

        try {
            // First, pick up last known location immediately
            val lastGps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val lastNetwork = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val bestLast = when {
                lastGps != null && lastNetwork != null -> if (lastGps.time >= lastNetwork.time) lastGps else lastNetwork
                lastGps != null -> lastGps
                else -> lastNetwork
            }
            if (bestLast != null) {
                handleNewLocation(bestLast)
            }

            // Register continuous updates for both providers
            if (locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.GPS_PROVIDER,
                    UPDATE_MIN_TIME_MS,
                    UPDATE_MIN_DISTANCE_M,
                    locationListener,
                    Looper.getMainLooper()
                )
            }
            if (locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locationManager.requestLocationUpdates(
                    LocationManager.NETWORK_PROVIDER,
                    UPDATE_MIN_TIME_MS,
                    UPDATE_MIN_DISTANCE_M,
                    locationListener,
                    Looper.getMainLooper()
                )
            }

            isTracking = true
            Log.i(TAG, "LocationIntelligenceEngine background tracking active")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start location tracking: ${e.message}", e)
        }
    }

    fun stopLocationTracking() {
        if (!isTracking) return
        try {
            locationManager?.removeUpdates(locationListener)
            isTracking = false
        } catch (_: Exception) {}
    }

    /**
     * Processes raw Android Location and triggers reverse geocoding asynchronously.
     */
    private fun handleNewLocation(location: Location) {
        val speedKmh = if (location.hasSpeed()) (location.speed * 3.6f) else 0f
        val currentSnap = _currentLocation.value

        val newSnap = currentSnap.copy(
            isAvailable = true,
            latitude = location.latitude,
            longitude = location.longitude,
            accuracy = location.accuracy,
            speedKmh = speedKmh,
            bearing = if (location.hasBearing()) location.bearing else 0f,
            altitude = if (location.hasAltitude()) location.altitude else 0.0,
            timestamp = location.time,
            provider = location.provider ?: "gps"
        )
        _currentLocation.value = newSnap

        // Trigger Geocoding if moved > 50 meters or > 2 minutes since last geocode
        val distMoved = calculateHaversineDistanceKm(lastGeocodedLat, lastGeocodedLng, location.latitude, location.longitude) * 1000.0
        val now = System.currentTimeMillis()
        if (distMoved > 50.0 || (now - lastGeocodeTime) > 120_000L) {
            lastGeocodedLat = location.latitude
            lastGeocodedLng = location.longitude
            lastGeocodeTime = now
            reverseGeocode(location.latitude, location.longitude)
        }
    }

    /**
     * Reverse geocodes coordinates into an Indian/International friendly street address.
     */
    private fun reverseGeocode(latitude: Double, longitude: Double) {
        scope.launch {
            try {
                if (!Geocoder.isPresent()) return@launch
                val geocoder = Geocoder(appContext, Locale.getDefault())

                val addresses: List<Address>? = withContext(Dispatchers.IO) {
                    try {
                        @Suppress("DEPRECATION")
                        geocoder.getFromLocation(latitude, longitude, 1)
                    } catch (e: IOException) {
                        Log.w(TAG, "Geocoder IO failure: ${e.message}")
                        null
                    }
                }

                val address = addresses?.firstOrNull() ?: return@launch
                val subLocality = address.subLocality ?: address.thoroughfare ?: ""
                val locality = address.locality ?: address.subAdminArea ?: ""
                val adminArea = address.adminArea ?: ""
                val country = address.countryName ?: ""
                val postal = address.postalCode ?: ""

                val addressLines = (0..address.maxAddressLineIndex).mapNotNull { address.getAddressLine(it) }
                val fullText = if (addressLines.isNotEmpty()) addressLines.joinToString(", ") else listOfNotNull(subLocality, locality, adminArea).joinToString(", ")

                _currentLocation.value = _currentLocation.value.copy(
                    addressText = fullText,
                    subLocality = subLocality,
                    locality = locality,
                    adminArea = adminArea,
                    country = country,
                    postalCode = postal
                )

                Log.d(TAG, "Reverse geocoded: $fullText")
            } catch (e: Exception) {
                Log.w(TAG, "Reverse geocoding error: ${e.message}")
            }
        }
    }

    /**
     * Calculates realistic road distance, travel ETA, and heading to any destination string or landmark.
     */
    suspend fun calculateDistanceAndEta(destinationQuery: String): DistanceCalculationResult? = withContext(Dispatchers.IO) {
        val current = _currentLocation.value
        if (!current.isAvailable) {
            // Try last attempt to fetch quick location
            startLocationTracking()
        }

        val startLat = if (current.isAvailable) current.latitude else 28.6139 // Default New Delhi fallback if no GPS
        val startLng = if (current.isAvailable) current.longitude else 77.2090

        // 1. Check if destination is a saved favorite place (e.g. "Ghar", "Office", "Gym")
        val saved = findMatchingFavoritePlace(destinationQuery)
        val targetLat: Double
        val targetLng: Double
        val finalDestName: String

        if (saved != null) {
            targetLat = saved.latitude
            targetLng = saved.longitude
            finalDestName = saved.name
        } else {
            // Geocode destination query
            val geocoded = geocodeLocationName(destinationQuery)
            if (geocoded != null) {
                targetLat = geocoded.latitude
                targetLng = geocoded.longitude
                finalDestName = destinationQuery
            } else {
                Log.w(TAG, "Could not geocode destination: $destinationQuery")
                return@withContext null
            }
        }

        val crowFliesKm = calculateHaversineDistanceKm(startLat, startLng, targetLat, targetLng)
        // Road tortuosity factor: real driving routes in cities/highways are ~1.25x to 1.3x straight-line
        val roadDistanceKm = (crowFliesKm * 1.28).let { (it * 10).roundToInt() / 10.0 }

        // ETA calculations
        // Average city/suburban driving speed: ~32 km/h
        val drivingTimeMin = max(1, (roadDistanceKm / 32.0 * 60).roundToInt())
        // Average walking speed: 5 km/h
        val walkingTimeMin = max(1, (roadDistanceKm / 5.0 * 60).roundToInt())
        // Average bike/two-wheeler speed: ~38 km/h
        val bikeTimeMin = max(1, (roadDistanceKm / 38.0 * 60).roundToInt())

        val heading = calculateBearingCardinal(startLat, startLng, targetLat, targetLng)

        val speechText = if (roadDistanceKm < 1.0) {
            val meters = (roadDistanceKm * 1000).roundToInt()
            "Boss, $finalDestName yahan se lagbhag $meters meter dur hai ($heading). Paidal sirf $walkingTimeMin minute lagenge."
        } else {
            "Boss, $finalDestName yahan se lagbhag $roadDistanceKm km dur hai ($heading). Car se lagbhag $drivingTimeMin minute aur bike se $bikeTimeMin minute lagenge."
        }

        DistanceCalculationResult(
            destination = finalDestName,
            destLatitude = targetLat,
            destLongitude = targetLng,
            crowFliesDistanceKm = crowFliesKm,
            estimatedRoadDistanceKm = roadDistanceKm,
            estimatedDrivingTimeMinutes = drivingTimeMin,
            estimatedWalkingTimeMinutes = walkingTimeMin,
            estimatedBikeTimeMinutes = bikeTimeMin,
            formattedSpeechText = speechText,
            directionCardinal = heading
        )
    }

    /**
     * Forward geocodes a location query string into latitude & longitude.
     */
    private suspend fun geocodeLocationName(query: String): Location? = withContext(Dispatchers.IO) {
        try {
            if (!Geocoder.isPresent()) return@withContext null
            val geocoder = Geocoder(appContext, Locale.getDefault())
            @Suppress("DEPRECATION")
            val matches = geocoder.getFromLocationName(query, 1)
            val match = matches?.firstOrNull() ?: return@withContext null
            Location("geocoder").apply {
                latitude = match.latitude
                longitude = match.longitude
            }
        } catch (e: Exception) {
            Log.w(TAG, "Geocode error for '$query': ${e.message}")
            null
        }
    }

    /**
     * Discovers nearest places of interest (e.g. Petrol Pump, Hospital, ATM, Restaurant).
     */
    suspend fun findNearby(category: String): List<NearbyPlaceMatch> = withContext(Dispatchers.IO) {
        val current = _currentLocation.value
        val locality = current.locality.ifBlank { "nearby" }
        val searchQuery = "$category in $locality"

        val results = mutableListOf<NearbyPlaceMatch>()
        try {
            if (Geocoder.isPresent()) {
                val geocoder = Geocoder(appContext, Locale.getDefault())
                @Suppress("DEPRECATION")
                val matches = geocoder.getFromLocationName(searchQuery, 4)
                matches?.forEach { addr ->
                    val name = addr.featureName ?: category.replaceFirstChar { it.uppercase() }
                    val addressText = (0..addr.maxAddressLineIndex).mapNotNull { addr.getAddressLine(it) }.joinToString(", ")
                    val distKm = if (current.isAvailable) {
                        calculateHaversineDistanceKm(current.latitude, current.longitude, addr.latitude, addr.longitude)
                    } else 0.0
                    results.add(
                        NearbyPlaceMatch(
                            name = name,
                            category = category,
                            address = addressText,
                            distanceKm = (distKm * 10).roundToInt() / 10.0,
                            latitude = addr.latitude,
                            longitude = addr.longitude
                        )
                    )
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "findNearby error: ${e.message}")
        }

        results.sortedBy { it.distanceKm }
    }

    /**
     * Launches Google Maps turn-by-turn navigation directly.
     */
    fun startTurnByTurnNavigation(destination: String, mode: String = "driving"): Boolean {
        return try {
            val travelMode = when (mode.lowercase()) {
                "walking", "walk" -> "w"
                "bicycling", "bike" -> "b"
                "transit", "train", "bus" -> "r"
                else -> "d"
            }
            val uri = Uri.parse("google.navigation:q=${Uri.encode(destination)}&mode=$travelMode")
            val intent = Intent(Intent.ACTION_VIEW, uri).apply {
                setPackage("com.google.android.apps.maps")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (intent.resolveActivity(appContext.packageManager) != null) {
                appContext.startActivity(intent)
            } else {
                val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${Uri.encode(destination)}")).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                appContext.startActivity(browserIntent)
            }
            JarvisSoundEngine.playTacticalBlip()
            AgentStatusBus.emit("🗺️ Turn-by-Turn Navigation started: $destination")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Failed to launch navigation: ${e.message}", e)
            false
        }
    }

    // ─── FAVORITE PLACES MANAGEMENT ──────────────────────────────────────────

    fun saveFavoritePlace(name: String, customAddress: String? = null, category: String = "general"): SavedFavoritePlace {
        val current = _currentLocation.value
        val lat = current.latitude
        val lng = current.longitude
        val address = customAddress?.ifBlank { current.addressText } ?: current.addressText.ifBlank { "Current Coordinates: $lat, $lng" }

        val place = SavedFavoritePlace(
            id = name.lowercase().replace(" ", "_"),
            name = name,
            address = address,
            latitude = lat,
            longitude = lng,
            category = category
        )

        val updatedList = _favoritePlaces.value.filter { it.name.lowercase() != name.lowercase() } + place
        _favoritePlaces.value = updatedList
        persistFavorites(updatedList)

        // Sync with Knowledge Graph
        try {
            val kg = MyraKnowledgeGraphEngine.getInstance(appContext)
            val entityId = "place_${name.lowercase().replace(" ", "_")}"
            kg.addEntity(
                com.soltini.app.graph.GraphEntity(
                    id = entityId,
                    name = name,
                    type = "PLACE",
                    aliases = listOf(name.lowercase()),
                    attributes = mapOf("address" to address, "lat" to lat.toString(), "lng" to lng.toString(), "category" to category)
                )
            )
            kg.addRelation("user_boss", entityId, "FAVORITE_PLACE")
        } catch (_: Exception) {}

        JarvisSoundEngine.playTaskSuccessChime()
        AgentStatusBus.emit("📍 Saved Favorite Place: $name ($address)")
        return place
    }

    fun findMatchingFavoritePlace(query: String): SavedFavoritePlace? {
        val q = query.trim().lowercase(Locale.ROOT)
        return _favoritePlaces.value.firstOrNull {
            it.name.lowercase() == q ||
            q.contains(it.name.lowercase()) ||
            (it.name.lowercase() == "ghar" && (q.contains("home") || q.contains("ghar"))) ||
            (it.name.lowercase() == "home" && (q.contains("home") || q.contains("ghar"))) ||
            (it.name.lowercase() == "office" && (q.contains("office") || q.contains("kaam") || q.contains("work")))
        }
    }

    private fun loadSavedFavorites(): List<SavedFavoritePlace> {
        val raw = prefs.getString(KEY_SAVED_FAVORITES, null) ?: return defaultFavoritePlaces()
        return try {
            val arr = JSONArray(raw)
            val list = mutableListOf<SavedFavoritePlace>()
            for (i in 0 until arr.length()) {
                val obj = arr.getJSONObject(i)
                list.add(
                    SavedFavoritePlace(
                        id = obj.getString("id"),
                        name = obj.getString("name"),
                        address = obj.optString("address", ""),
                        latitude = obj.optDouble("latitude", 0.0),
                        longitude = obj.optDouble("longitude", 0.0),
                        category = obj.optString("category", "general"),
                        addedTimestamp = obj.optLong("addedTimestamp", System.currentTimeMillis())
                    )
                )
            }
            if (list.isEmpty()) defaultFavoritePlaces() else list
        } catch (_: Exception) {
            defaultFavoritePlaces()
        }
    }

    private fun persistFavorites(places: List<SavedFavoritePlace>) {
        val arr = JSONArray()
        places.forEach { p ->
            arr.put(
                JSONObject().apply {
                    put("id", p.id)
                    put("name", p.name)
                    put("address", p.address)
                    put("latitude", p.latitude)
                    put("longitude", p.longitude)
                    put("category", p.category)
                    put("addedTimestamp", p.addedTimestamp)
                }
            )
        }
        prefs.edit().putString(KEY_SAVED_FAVORITES, arr.toString()).apply()
    }

    private fun defaultFavoritePlaces(): List<SavedFavoritePlace> {
        return listOf(
            SavedFavoritePlace(id = "home", name = "Ghar", address = "Home Location", latitude = 28.6139, longitude = 77.2090, category = "residence"),
            SavedFavoritePlace(id = "gym", name = "Gym", address = "Fitness Hub", latitude = 28.6200, longitude = 77.2150, category = "fitness")
        )
    }

    // ─── MATHEMATICAL GEOGRAPHY HELPERS ───────────────────────────────────────

    /**
     * Calculates the great-circle Haversine distance between two coordinates in kilometers.
     */
    private fun calculateHaversineDistanceKm(lat1: Double, lon1: Double, lat2: Double, lon2: Double): Double {
        val r = 6371.0 // Radius of the Earth in km
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) + cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) * sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return (r * c * 100).roundToInt() / 100.0
    }

    /**
     * Calculates Cardinal direction (North, North-East, South, etc.) from point 1 to point 2.
     */
    private fun calculateBearingCardinal(lat1: Double, lon1: Double, lat2: Double, lon2: Double): String {
        val phi1 = Math.toRadians(lat1)
        val phi2 = Math.toRadians(lat2)
        val deltaLambda = Math.toRadians(lon2 - lon1)

        val y = sin(deltaLambda) * cos(phi2)
        val x = cos(phi1) * sin(phi2) - sin(phi1) * cos(phi2) * cos(deltaLambda)
        var degrees = Math.toDegrees(atan2(y, x))
        degrees = (degrees + 360) % 360

        val directions = arrayOf("North", "North-East", "East", "South-East", "South", "South-West", "West", "North-West")
        val index = ((degrees + 22.5) / 45.0).toInt() % 8
        return directions[index]
    }

    /**
     * Formats real-time prompt injection for Gemini Live WebSocket so Myra is always spatial-aware.
     */
    fun buildLocationPromptBlock(): String {
        val loc = _currentLocation.value
        val favorites = _favoritePlaces.value.joinToString(", ") { "${it.name} (${it.address})" }
        return if (loc.isAvailable) {
            """
[LIVE BACKGROUND GPS TELEMETRY:
Current Location: ${loc.getReadableSummary()} (${loc.addressText})
Coordinates: ${"%.4f".format(loc.latitude)}° N, ${"%.4f".format(loc.longitude)}° E | Speed: ${loc.speedKmh.roundToInt()} km/h | Provider: ${loc.provider}
Saved Favorite Places: $favorites]
"""
        } else {
            """
[LIVE GPS TELEMETRY: Standby. Saved Favorite Places: $favorites]
"""
        }
    }
}
