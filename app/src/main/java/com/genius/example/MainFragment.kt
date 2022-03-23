package com.genius.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Location
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.genius.cgps.CGGPS
import com.genius.cgps.ResolutionNeedException
import com.genius.cgps.toAddress
import com.google.android.material.floatingactionbutton.FloatingActionButton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
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

    private var fab: FloatingActionButton? = null
    private var btnService: Button? = null
    private var btnLocationUpdates: Button? = null
    private var tvHello: TextView? = null
    private val singleUpdateCaller = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            singleUpdate()
        } else if (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            tvHello?.text = getString(R.string.location_type_not_supported)
        }
    }
    private val resolveSingleCaller = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            singleUpdate()
        }
    }
    private val updatesCaller = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            if (result[Manifest.permission.WRITE_EXTERNAL_STORAGE] == null && SDK_INT >= Build.VERSION_CODES.R) {
                storageCaller.launch(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
            } else if (result[Manifest.permission.WRITE_EXTERNAL_STORAGE] == true) {
                startUpdates()
            }
        } else if (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            tvHello?.text = getString(R.string.location_type_not_supported)
        }
    }
    private val serviceCaller = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { result ->
        if (result[Manifest.permission.ACCESS_FINE_LOCATION] == true) {
            serviceAction()
        } else if (result[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            tvHello?.text = getString(R.string.location_type_not_supported)
        }
    }
    @RequiresApi(Build.VERSION_CODES.R)
    private val storageCaller = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        if (Environment.isExternalStorageManager()) {
            startUpdates()
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        fab = view.findViewById(R.id.fab)
        btnService = view.findViewById(R.id.b_service)
        btnLocationUpdates = view.findViewById(R.id.b_location_updates)
        tvHello = view.findViewById(R.id.tv_hello)

        fab?.setOnClickListener { singleUpdate() }
        btnService?.setOnClickListener { serviceAction() }
        btnLocationUpdates?.setOnClickListener {
            if (updates?.isActive != true) {
                btnLocationUpdates?.setText(R.string.action_stop)
                startUpdates()
            } else {
                updates?.cancel()
                btnLocationUpdates?.setText(R.string.action_start)
                tvHello?.setText(R.string.info_stopped)
            }
        }
    }

    @Suppress("BlockingMethodInNonBlockingContext")
    private fun singleUpdate() {
        lifecycleScope.launch {
            val permission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
            if (permission != PackageManager.PERMISSION_GRANTED) {
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
                        val location: Location = CGGPS(requireContext()).actualLocationWithEnable()
                        val address = withContext(Dispatchers.IO) {
                            location.toAddress(requireContext())
                        }
                        this.appendLine(location.printInfo(address))
                    }
                    this.append("Done in $requestTime milliseconds")
                }

            } catch (exceptionWithIntentSender: ResolutionNeedException) {
                resolveSingleCaller.launch(IntentSenderRequest.Builder(exceptionWithIntentSender.intentSender).build())
                "Please enable GPS adapter"
            } catch (e: Exception) {
                Log.e("GPS ERROR", e.message, e)
                e.message ?: "Empty error"
            }

            tvHello?.text = message
        }
    }

    private fun startUpdates() {
        val finePermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        val writePermission = checkStoragePermission()
        if (finePermission != PackageManager.PERMISSION_GRANTED || !writePermission) {
            val locationPermission = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            val storagePermission = if (SDK_INT >= Build.VERSION_CODES.R) {
                arrayOf()
            } else {
                arrayOf(
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                )
            }
            updatesCaller.launch(
                locationPermission + storagePermission
            )
            return
        }

        currentStep = 1
        updates = lifecycleScope.launch {
            CGGPS(requireContext()).requestUpdates().collect { result ->
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
        val downloadFolder = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
        val logFile = File(downloadFolder, "CGPS.txt")
        if (!logFile.exists()) {
            try {
                logFile.createNewFile()
            } catch (e: IOException) {
                Log.e("File", e.message, e)
            }
        }

        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter(FileWriter(logFile, true)).use {
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
        if (permission != PackageManager.PERMISSION_GRANTED) {
            serviceCaller.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION))
            return
        }
        val serviceIntent = Intent(requireContext(), LocationService::class.java)
        requireContext().startService(serviceIntent)
    }

    private fun checkStoragePermission(): Boolean {
        return if (SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            val result = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
            result == PackageManager.PERMISSION_GRANTED
        }
    }
}

fun Location.printInfo(address: Address?) = """Provider: ${this.provider}
                    |Accuracy: ${this.accuracy}
                    |Coordinates: [${this.latitude}: ${this.longitude}]
                    |Speed: ${this.speed}
                    |Altitude: ${this.altitude}
                    |
                    |Address: ${address?.getAddressLine(0) ?: "n/a"}""".trimMargin()