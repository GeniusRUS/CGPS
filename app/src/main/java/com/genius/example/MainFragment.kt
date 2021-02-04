package com.genius.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Location
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.genius.cgps.CGGPS
import com.genius.cgps.toAddress
import com.google.android.material.floatingactionbutton.FloatingActionButton
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.system.measureTimeMillis

class MainFragment : Fragment(R.layout.fragment_main) {

    private var updates: Job? = null
    private var currentStep = 0
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    private var fab: FloatingActionButton? = null
    private var btnService: Button? = null
    private var btnLocationUpdates: Button? = null
    private var tvHello: TextView? = null

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

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            singleUpdate()
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_WRITE_STORAGE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startUpdates()
            }
        } else if (requestCode == REQUEST_CODE_FINE_LOCATION) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                singleUpdate()
            }
        } else if (requestCode == REQUEST_CODE_FINE_LOCATION_TO_SERVICE) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                serviceAction()
            }
        }
    }

    private fun singleUpdate() {
        lifecycleScope.launch {
            if (!checkPermissionIsNeedToRequest(REQUEST_CODE_FINE_LOCATION)) return@launch

            val message = try {
                buildString {
                    val requestTime = measureTimeMillis {
                        val location: Location = CGGPS(this@MainFragment).actualLocationWithEnable()
                        val address = withContext(Dispatchers.IO) { location.toAddress(requireContext()) }
                        this.appendLine(location.printInfo(address))
                    }
                    this.append("Done in $requestTime milliseconds")
                }

            } catch (e: Exception) {
                Log.e("GPS ERROR", e.message, e)
                e.message ?: "Empty error"
            }

            tvHello?.text = message
        }
    }

    private fun startUpdates(): Boolean {
        val permission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE_WRITE_STORAGE)
            return false
        }

        currentStep = 1
        updates = lifecycleScope.launch {
            CGGPS(this@MainFragment).requestUpdates().collect { result ->
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

        return true
    }

    private fun logEvent(text: String) {
        val logFile = File("sdcard/CGPS.txt")
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
        if (!checkPermissionIsNeedToRequest(REQUEST_CODE_FINE_LOCATION_TO_SERVICE)) return
        val serviceIntent = Intent(requireContext(), LocationService::class.java)
        requireContext().startService(serviceIntent)
    }

    private fun checkPermissionIsNeedToRequest(requestCode: Int): Boolean {
        val permission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        return if (permission != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), requestCode)
            false
        } else true
    }

    companion object {
        private const val REQUEST_CODE_WRITE_STORAGE = 1014
        private const val REQUEST_CODE_FINE_LOCATION = 1015
        private const val REQUEST_CODE_FINE_LOCATION_TO_SERVICE = 1016
    }
}

fun Location.printInfo(address: Address?) = """Provider: ${this.provider}
                    |Accuracy: ${this.accuracy}
                    |Coordinates: [${this.latitude}: ${this.longitude}]
                    |Speed: ${this.speed}
                    |Altitude: ${this.altitude}
                    |
                    |Address: ${address?.getAddressLine(0) ?: "n/a"}""".trimMargin()