package com.genius.example

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Address
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Build.VERSION.SDK_INT
import android.os.Bundle
import android.os.Environment
import android.provider.DocumentsContract
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import java.io.IOException
import java.io.OutputStreamWriter
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
            createFileToSave()
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
    private val storageCaller = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        it.data?.data?.let { uriFileToSave ->
            startUpdates(uriFileToSave)
            saveToFile = uriFileToSave
        }
    }
    private var saveToFile: Uri? = null

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
                startUpdates(saveToFile)
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

    private fun startUpdates(uriFile: Uri? = null) {
        val finePermission = ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
        if (finePermission != PackageManager.PERMISSION_GRANTED) {
            val locationPermission = arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
            updatesCaller.launch(locationPermission)
            return
        } else if (uriFile == null) {
            createFileToSave()
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
                    logEvent(uriFile, message)
                    currentStep++
                }
                result.exceptionOrNull() ?.let { error ->
                    Log.e("GPS ERROR", error.message, error)
                    val message = """Step $currentStep in ${formatter.format(Date())}
                |
                |${error.message ?: "Empty message"}""".trimMargin()
                    tvHello?.text = message
                    logEvent(uriFile, message)
                    currentStep++
                }
            }
        }

        return
    }

    private fun logEvent(uriFile: Uri, text: String) {
        try {
            //BufferedWriter for performance, true to set append to file flag
            BufferedWriter(
                OutputStreamWriter(
                    requireContext().contentResolver.openOutputStream(uriFile, "wa")
                )
            ).use {
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

    private fun createFileToSave() {
        val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
            addCategory(Intent.CATEGORY_OPENABLE)
            type = "text/plain"
            putExtra(Intent.EXTRA_TITLE, "CGPS.txt")

            if (SDK_INT >= Build.VERSION_CODES.O) {
                putExtra(DocumentsContract.EXTRA_INITIAL_URI, Environment.DIRECTORY_DOWNLOADS)
            }
        }

        storageCaller.launch(intent)
    }
}

fun Location.printInfo(address: Address?) = """Provider: ${this.provider}
                    |Accuracy: ${this.accuracy}
                    |Coordinates: [${this.latitude}: ${this.longitude}]
                    |Speed: ${this.speed}
                    |Altitude: ${this.altitude}
                    |
                    |Address: ${address?.getAddressLine(0) ?: "n/a"}""".trimMargin()