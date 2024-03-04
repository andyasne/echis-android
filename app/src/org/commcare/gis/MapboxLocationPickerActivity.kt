package org.commcare.gis

import android.Manifest
import android.content.Intent
import android.content.IntentSender.SendIntentException
import android.location.Location
import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModelProvider
import androidx.viewbinding.ViewBinding
import com.google.android.gms.common.api.ResolvableApiException
import com.mapbox.android.core.location.LocationEngineCallback
import com.mapbox.android.core.location.LocationEngineResult
import com.mapbox.mapboxsdk.camera.CameraPosition
import com.mapbox.mapboxsdk.camera.CameraUpdateFactory
import com.mapbox.mapboxsdk.geometry.LatLng
import com.mapbox.mapboxsdk.location.LocationComponentActivationOptions
import com.mapbox.mapboxsdk.location.modes.CameraMode
import com.mapbox.mapboxsdk.location.modes.RenderMode
import com.mapbox.mapboxsdk.maps.MapboxMap
import com.mapbox.mapboxsdk.maps.Style
import com.mapbox.mapboxsdk.plugins.annotation.Symbol
import com.mapbox.mapboxsdk.plugins.annotation.SymbolManager
import com.mapbox.mapboxsdk.plugins.annotation.SymbolOptions
import io.ona.kujaku.views.KujakuMapView
import org.commcare.activities.components.FormEntryConstants
import org.commcare.activities.components.FormEntryDialogs
import org.commcare.dalvik.eCHIS.R
import org.commcare.dalvik.eCHIS.databinding.ActivityMapboxLocationPickerBinding
import org.commcare.location.CommCareLocationController
import org.commcare.location.CommCareLocationControllerFactory
import org.commcare.location.CommCareLocationListener
import org.commcare.utils.GeoUtils
import org.commcare.utils.Permissions
import org.commcare.views.widgets.GeoPointWidget
import org.javarosa.core.services.locale.Localization

class MapboxLocationPickerActivity : BaseMapboxActivity(), CommCareLocationListener {

    companion object {
        const val MARKER_ICON_IMAGE_ID = "marker_icon_image"
        const val LOCATION_SETTING_REQ = 101
        private val LOCATION_PERMISSIONS = arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    private lateinit var viewModel: MapboxLocationPickerViewModel
    private lateinit var locationController: CommCareLocationController
    private val mapStyles = arrayOf(
            Style.MAPBOX_STREETS,
            Style.SATELLITE,
            Style.SATELLITE_STREETS,
            Style.OUTDOORS
    )
    private var currentMapStyleIndex = 0
    private var cameraPosition: CameraPosition? = null
    private var symbolManager: SymbolManager? = null
    private var symbol: Symbol? = null
    private val mapClickListener = MapboxMap.OnMapClickListener { point ->
        if (!Permissions.missingAppPermission(this, LOCATION_PERMISSIONS)) {
            isManualSelectedLocation = true
            // Add marker.
            updateMarker(point)
            viewModel.reverseGeocode(point)
        } else {
            Toast.makeText(this,
                    Localization.get("permission.location.denial.message"),
                    Toast.LENGTH_LONG).show()
        }
        true
    }

    // don't reset marker to current GPS location if we manually selected a location
    private var isManualSelectedLocation = false
    private var currentLocation: Location? = null
    private lateinit var viewBinding: ActivityMapboxLocationPickerBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewModel = ViewModelProvider(this, ViewModelProvider.AndroidViewModelFactory.getInstance(application))
                .get(MapboxLocationPickerViewModel::class.java)
        attachListener()
        locationController = CommCareLocationControllerFactory.getLocationController(this, this)

        // Check extras
        if (intent.hasExtra(GeoPointWidget.LOCATION)) {
            val inputLocation = intent.getDoubleArrayExtra(GeoPointWidget.LOCATION)!!
            cameraPosition = CameraPosition.Builder()
                    .target(LatLng(inputLocation[0], inputLocation[1]))
                    .zoom(10.0)
                    .tilt(20.0)
                    .build()
            isManualSelectedLocation = true
        }
        getMapView().showCurrentLocationBtn(false)
    }

    override fun getMapView(): KujakuMapView {
        return viewBinding.mapView
    }

    override fun getViewBinding(): ViewBinding {
        if (!::viewBinding.isInitialized) {
            viewBinding = ActivityMapboxLocationPickerBinding.inflate(layoutInflater)
        }
        return viewBinding
    }

    private fun attachListener() {
        viewBinding.confirmLocationButton.visibility = if (inViewMode()) {
            View.GONE
        } else {
            View.VISIBLE
        }
        viewBinding.confirmLocationButton.setOnClickListener {
            // Use the map target's coordinates to make a reverse geocoding search
            Intent().apply {
                putExtra(
                    FormEntryConstants.LOCATION_RESULT,
                    GeoUtils.locationToString(viewModel.getLocation())
                )
            }.also {
                setResult(RESULT_OK, it)
                finish()
            }
        }
        viewBinding.cancelButton.setOnClickListener {
            finish()
        }
        viewBinding.switchMap.setOnClickListener {
            currentMapStyleIndex = (currentMapStyleIndex + 1) % mapStyles.size
            val style = Style.Builder()
                .fromUri(mapStyles[currentMapStyleIndex])
                .withImage(
                    MARKER_ICON_IMAGE_ID,
                    ContextCompat.getDrawable(this, R.drawable.marker)!!
                )
            map.setStyle(style) {
                val loc = viewModel.getLocation()
                updateMarker(LatLng(loc.latitude, loc.longitude, loc.altitude))
            }
        }
        viewBinding.currentLocation.setOnClickListener {
            isManualSelectedLocation = false
            currentLocation?.let {
                onLocationResult(it)
            }
        }
    }

    private fun inViewMode() = intent.getBooleanExtra(GeoPointWidget.EXTRA_VIEW_ONLY, false)

    override fun onResume() {
        super.onResume()
        observeViewModel()
        requestLocation()
    }

    override fun onPause() {
        super.onPause()
        locationController.stop()
    }

    private fun observeViewModel() {
        viewModel.placeName.observe(this, androidx.lifecycle.Observer {
            viewBinding.confirmLocationButton.isEnabled = true
            viewBinding.location.text = it
        })
    }

    override fun onDestroy() {
        map.removeOnMapClickListener(mapClickListener)
        locationController.destroy()
        super.onDestroy()
    }

    override fun shouldFocusUserLocationOnLoad(): Boolean {
        return false
    }

    override fun onMapLoaded() {
        if (!inViewMode()) {
            map.addOnMapClickListener(mapClickListener)
        }
        setMapStyle()
    }

    private fun setMapStyle() {
        val style = Style.Builder()
                .fromUri(mapStyles[currentMapStyleIndex])
                .withImage(MARKER_ICON_IMAGE_ID, ContextCompat.getDrawable(this, R.drawable.marker)!!)
        map.setStyle(style) {
            enableLocationComponent(it)
            addMarker(it, map.cameraPosition.target)
            initialMarkerPosition()
        }
    }

    private fun enableLocationComponent(style: Style) {
        val component = map.locationComponent
        component.activateLocationComponent(LocationComponentActivationOptions.builder(
                this, style
        ).build())
        component.isLocationComponentEnabled = true
        component.cameraMode = CameraMode.TRACKING
        component.renderMode = RenderMode.NORMAL
    }

    private fun addMarker(style: Style, point: LatLng) {
        symbolManager = SymbolManager(getMapView(), map, style)
        symbolManager?.let { symbolManager ->
            symbolManager.iconAllowOverlap = true
            val symbolOptions = SymbolOptions()
                    .withLatLng(point)
                    .withIconImage(MARKER_ICON_IMAGE_ID)
            symbol = symbolManager.create(symbolOptions)
        }
    }

    private fun initialMarkerPosition() {
        if (cameraPosition != null) {
            cameraPosition?.let { pos ->
                viewModel.reverseGeocode(pos.target)
                updateMarker(pos.target)
            }
        } else {
            map.locationComponent.locationEngine
                    ?.getLastLocation(object : LocationEngineCallback<LocationEngineResult> {
                        override fun onSuccess(result: LocationEngineResult?) {
                            result ?: return
                            result.lastLocation?.let { location ->
                                val point = LatLng(location.latitude, location.longitude, location.altitude)
                                viewModel.reverseGeocode(point)
                                updateMarker(point)
                            }
                        }

                        override fun onFailure(exception: Exception) {}
                    })
        }
    }

    private fun updateMarker(point: LatLng) {
        symbol?.let { symbol ->
            symbol.latLng = point
            symbolManager?.update(symbol)
            val pos = CameraPosition.Builder()
                    .target(point)
                    .zoom(15.0)
                    .build()
            map.animateCamera(CameraUpdateFactory.newCameraPosition(pos), 10)
        }
    }

    override fun onLocationRequestStart() {
        /// Do nothing
    }

    override fun onLocationResult(result: Location) {
        if (isManualSelectedLocation || inViewMode()) {
            return
        }
        if (currentLocation == null || currentLocation!!.accuracy == 0f || result.accuracy <= currentLocation!!.accuracy) {
            currentLocation = result
            val point = LatLng(result.latitude, result.longitude, result.altitude)
            viewModel.reverseGeocode(point)
            updateMarker(point)
            getMapView().focusOnUserLocation(true)
        }
    }

    override fun missingPermissions() {
        // Do nothing. Kujaku handles requesting permissions internally.
        // https://github.com/onaio/kujaku/blob/4d737b89b23fb9eafe0850e83671034121c10e1d/library/src/main/java/io/ona/kujaku/helpers/PermissionsHelper.java#L17-L24
    }

    override fun onLocationRequestFailure(failure: CommCareLocationListener.Failure) {
        if (failure is CommCareLocationListener.Failure.ApiException) {
            val exception = failure.exception
            if (exception is ResolvableApiException) {
                try {
                    exception.startResolutionForResult(this, LOCATION_SETTING_REQ)
                } catch (e: SendIntentException) {
                    e.printStackTrace()
                }
            }
        } else {
            FormEntryDialogs.handleNoGpsProvider(this)
        }
    }

    private fun requestLocation() {
        if (!Permissions.missingAppPermission(this, LOCATION_PERMISSIONS)) {
            locationController.start()
        }
    }
}
