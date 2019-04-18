package com.genius.cgps

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.location.Address
import android.location.Location
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.util.Log
import android.view.Menu
import android.view.MenuItem
import com.github.florent37.runtimepermission.kotlin.PermissionException
import com.github.florent37.runtimepermission.kotlin.coroutines.experimental.askPermission

import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.io.IOException
import java.text.SimpleDateFormat
import java.util.*
import kotlin.coroutines.CoroutineContext

class MainActivity : AppCompatActivity(), CoroutineScope {

    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    private var updates: Job? = null
    private var currentStep = 0
    private val formatter = SimpleDateFormat("HH:mm:ss", Locale.getDefault())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(toolbar)

        fab.setOnClickListener { singleUpdate() }
    }

    override fun onCreateOptionsMenu(menu: Menu): Boolean {
        // Inflate the menu; this adds items to the action bar if it is present.
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode == Activity.RESULT_OK) {
            singleUpdate()
        }
    }

    @Suppress("DEPRECATION")
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        return when (item.itemId) {
            R.id.action_start -> {
                launch {
                    try {
                        askPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                    } catch (e: PermissionException) {

                    }
                    currentStep = 1
                    updates = locationUpdates()
                }
                return true
            }
            R.id.action_stop -> {
                updates?.cancel()
                tv_hello.setText(R.string.info_stopped)
                return true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    @Suppress("DEPRECATION")
    private fun singleUpdate() {
        launch {
            try {
                askPermission(Manifest.permission.ACCESS_FINE_LOCATION)
            } catch (e: PermissionException) {
                return@launch
            }

            val message = try {
                val location = CGGPS(this@MainActivity).actualLocationWithEnable()
                val address = location?.toAddress(this@MainActivity)
                location?.printInfo(address)
            } catch (e: Exception) {
                Log.e("GPS ERROR", e.message, e)
                e.message ?: "Empty error"
            }

            tv_hello.text = message
        }
    }

    private fun locationUpdates() = CGGPS(this).requestUpdates(actor {
        channel.consumeEach { result ->
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
    })

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
}

fun Location.printInfo(address: Address?) = """Provider: ${this.provider}
                    |Accuracy: ${this.accuracy}
                    |Coordinates: [${this.latitude}: ${this.longitude}]
                    |Speed: ${this.speed}
                    |Altitude: ${this.altitude}
                    |
                    |Address: ${address?.getAddressLine(0) ?: "n/a"}""".trimMargin()