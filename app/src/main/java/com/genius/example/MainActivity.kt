package com.genius.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Location
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import androidx.core.app.ActivityCompat
import com.genius.cgps.CGGPS
import com.genius.cgps.toAddress

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.collect
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.CoroutineContext
import kotlin.system.measureTimeMillis

class MainActivity : AppCompatActivity(), CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    private var updates: Job? = null
    private var currentStep = 0
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fab.setOnClickListener { singleUpdate() }
        b_service.setOnClickListener { serviceAction() }
        b_location_updates.setOnClickListener {
            if (updates?.isActive != true) {
                b_location_updates.setText(R.string.action_stop)
                startUpdates()
            } else {
                updates?.cancel()
                b_location_updates.setText(R.string.action_start)
                tv_hello.setText(R.string.info_stopped)
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
        launch {
            if (!checkPermissionIsNeedToRequest(REQUEST_CODE_FINE_LOCATION)) return@launch

            val message = try {
                buildString {
                    val requestTime = measureTimeMillis {
                        val location: Location? = CGGPS(this@MainActivity).actualLocationWithEnable()
                        val address = withContext(Dispatchers.IO) { location?.toAddress(this@MainActivity) }
                        this.appendln(location?.printInfo(address))
                    }
                    this.append("Done in $requestTime milliseconds")
                }

            } catch (e: Exception) {
                Log.e("GPS ERROR", e.message, e)
                e.message ?: "Empty error"
            }

            tv_hello.text = message
        }
    }

    private fun startUpdates(): Boolean {
        val permission = ActivityCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE)
        if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), REQUEST_CODE_WRITE_STORAGE)
            return false
        }

        currentStep = 1
        updates = launch {
            CGGPS(this@MainActivity).requestUpdates().collect { result ->
                result.getOrNull()?.let { location ->
                    val message = """Step $currentStep in ${formatter.format(Date(location.time))}
            |
            |Provider: ${location.provider}
            |Accuracy: ${location.accuracy}
            |Coordinates: [${location.latitude}: ${location.longitude}]
            |Speed: ${location.speed}
            |Altitude: ${location.altitude}""".trimMargin()

                    tv_hello.text = message
                    logEvent(message)
                    currentStep++
                }
                result.exceptionOrNull() ?.let { error ->
                    Log.e("GPS ERROR", error.message, error)
                    val message = """Step $currentStep in ${formatter.format(Date())}
                |
                |${error.message ?: "Empty message"}""".trimMargin()
                    tv_hello.text = message
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
        val serviceIntent = Intent(this, LocationService::class.java)
        startService(serviceIntent)
    }

    private fun checkPermissionIsNeedToRequest(requestCode: Int): Boolean {
        val permission = ActivityCompat.checkSelfPermission(this@MainActivity, Manifest.permission.ACCESS_FINE_LOCATION)
        return if (permission != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this@MainActivity, arrayOf(Manifest.permission.ACCESS_FINE_LOCATION), requestCode)
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