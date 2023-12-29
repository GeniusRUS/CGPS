package com.genius.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Location
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.genius.cgps.CGPS
import com.genius.cgps.GoogleCGPS
import com.genius.cgps.HardwareCGPS
import com.genius.cgps.HuaweiCGPS
import com.genius.cgps.ResolutionNeedException
import com.genius.cgps.toAddress
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.system.measureTimeMillis

class MainFragment : Fragment(R.layout.fragment_main) {

    private var updates: Job? = null
    private var currentStep = 0
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private val hardwareCgps: CGPS by lazy { HardwareCGPS(requireContext()) }
    private val googleCgps: CGPS by lazy { GoogleCGPS(requireContext()) }
    private val huaweiCgps: CGPS by lazy { HuaweiCGPS(requireContext()) }
    private var currentCgps: CGPS? = null

    private var hardwareSingle: Button? = null
    private var googleSingle: Button? = null
    private var huaweiSingle: Button? = null
    private var btnService: Button? = null
    private var btnLocationUpdates: Button? = null
    private var tvHello: TextView? = null
    private val singleUpdateCaller = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            singleUpdate(currentCgps ?: return@registerForActivityResult)
        } else if (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            tvHello?.text = getString(R.string.location_type_not_supported)
        }
    }
    private val resolveSingleCaller = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            singleUpdate(currentCgps ?: return@registerForActivityResult)
        }
    }
    private val updatesCaller = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val isStorageGranted = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P || result[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true && isStorageGranted) {
            startUpdates(googleCgps)
        } else if (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            tvHello?.text = getString(R.string.location_type_not_supported)
        }
    }
    private val serviceCaller = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        val foregroundService = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            result[Manifest.permission.FOREGROUND_SERVICE_LOCATION]
        } else {
            true
        }
        val postNotifications = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            result[Manifest.permission.POST_NOTIFICATIONS]
        } else {
            true
        }
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true && foregroundService == true && postNotifications == true) {
            serviceAction()
        } else if (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            tvHello?.text = getString(R.string.location_type_not_supported)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        hardwareSingle = view.findViewById(R.id.hardware_single)
        googleSingle = view.findViewById(R.id.google_single)
        huaweiSingle = view.findViewById(R.id.huawei_single)
        btnService = view.findViewById(R.id.b_service)
        btnLocationUpdates = view.findViewById(R.id.b_location_updates)
        tvHello = view.findViewById(R.id.tv_hello)

        hardwareSingle?.setOnClickListener { singleUpdate(hardwareCgps) }
        googleSingle?.setOnClickListener { singleUpdate(googleCgps) }
        huaweiSingle?.setOnClickListener { singleUpdate(huaweiCgps) }
        btnService?.setOnClickListener { serviceAction() }
        btnLocationUpdates?.setOnClickListener {
            if (updates?.isActive != true) {
                btnLocationUpdates?.setText(R.string.action_stop)
                startUpdates(googleCgps)
            } else {
                updates?.cancel()
                btnLocationUpdates?.setText(R.string.action_start)
                tvHello?.setText(R.string.info_stopped)
            }
        }
    }

    private fun singleUpdate(cgps: CGPS) {
        lifecycleScope.launch {
            val permission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            if (permission != PackageManager.PERMISSION_GRANTED) {
                currentCgps = cgps
                singleUpdateCaller.launch(
                    arrayOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                )
                return@launch
            }

            val message = try {
                buildString {
                    val requestTime = measureTimeMillis {
                        val location: Location = when (cgps) {
                            is HardwareCGPS -> cgps.actualLocation()
                            else -> cgps.actualLocationWithEnable()
                        }
                        val address = location.toAddress(requireContext())
                        this.appendLine(location.printInfo(address))
                    }
                    this.append("Done in $requestTime milliseconds")
                }

            } catch (exceptionWithIntentSender: ResolutionNeedException) {
                currentCgps = cgps
                resolveSingleCaller.launch(IntentSenderRequest.Builder(exceptionWithIntentSender.intentSender).build())
                "Please enable GPS adapter"
            } catch (e: Exception) {
                Log.e("GPS ERROR", e.message, e)
                e.message ?: "Empty error"
            }

            tvHello?.text = message
        }
    }

    private fun startUpdates(cgps: CGPS) {
        val storagePermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            false
        } else {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE) != PackageManager.PERMISSION_GRANTED
        }
        val finePermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED
        if (finePermission || storagePermission) {
            currentCgps = cgps
            val permissions = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            )
            updatesCaller.launch(permissions)
            return
        }

        currentStep = 1
        updates = lifecycleScope.launch {
            cgps.requestUpdates().collect { result ->
                result.getOrNull()?.let { location ->
                    val message = """Step $currentStep in ${formatter.format(Date(location.time))}
            |
            |Provider: ${location.provider}
            |Accuracy: ${location.accuracy}
            |Coordinates: [${location.latitude}: ${location.longitude}]
            |Speed: ${location.speed}
            |Altitude: ${location.altitude}""".trimMargin()

                    tvHello?.text = message
                    logEvent(message)
                    currentStep++
                }
                result.exceptionOrNull() ?.let { error ->
                    Log.e("GPS ERROR", error.message, error)
                    val message = """Step $currentStep in ${formatter.format(Date())}
                |
                |${error.message ?: "Empty message"}""".trimMargin()
                    tvHello?.text = message
                    logEvent(message)
                    currentStep++
                }
            }
        }

        return
    }

    private fun logEvent(text: String) {
        try {
            //BufferedWriter for performance, true to set append to file flag
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            BufferedWriter(FileWriter(File(dir, "CGPS.txt"), true)).use {
                it.append(text)
                it.newLine()
                it.append("---------------------------------------")
                it.newLine()
            }
        } catch (e: IOException) {
            Log.e("File", e.message, e)
        }
    }

    private fun serviceAction() {
        val permission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val permissionForegroundLocation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.FOREGROUND_SERVICE_LOCATION)
        } else {
            PackageManager.PERMISSION_GRANTED
        }
        val notificationPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.POST_NOTIFICATIONS)
        } else {
            PackageManager.PERMISSION_GRANTED
        }
        if (permission != PackageManager.PERMISSION_GRANTED
            || permissionForegroundLocation != PackageManager.PERMISSION_GRANTED
            || notificationPermission != PackageManager.PERMISSION_GRANTED) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                serviceCaller.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS, Manifest.permission.FOREGROUND_SERVICE_LOCATION))
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                serviceCaller.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.POST_NOTIFICATIONS))
            } else {
                serviceCaller.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            }
            return
        }
        val serviceIntent = Intent(requireContext(), LocationService::class.java)
        requireContext().startService(serviceIntent)
    }
}

fun Location.printInfo(address: Address?) = """Provider: ${this.provider}
                    |Accuracy: ${this.accuracy}
                    |Coordinates: [${this.latitude}: ${this.longitude}]
                    |Speed: ${this.speed}
                    |Altitude: ${this.altitude}
                    |
                    |Address: ${address?.getAddressLine(0) ?: "n/a"}""".trimMargin()