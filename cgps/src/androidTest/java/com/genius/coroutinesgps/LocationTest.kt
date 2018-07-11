package com.genius.coroutinesgps

import android.location.Location
import android.support.test.InstrumentationRegistry
import android.support.test.rule.GrantPermissionRule
import android.support.test.runner.AndroidJUnit4
import com.genius.cgps.CGGPS
import kotlinx.coroutines.experimental.Job
import kotlinx.coroutines.experimental.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class LocationTest {

    private lateinit var job: Job

    @get:Rule
    private val runtimePermissionRule = GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION)

    @Test
    fun actualLocation() {
        runBlocking {
            try {
                val location = CGGPS(InstrumentationRegistry.getContext()).actualLocation()

                assertTrue(location.longitude > 0)
            } catch (e: Exception) {
                fail(e.message ?: "Undefined reason")
            }
        }
    }

    @Test
    fun lastLocation() {
        runBlocking {
            try {
                val location = CGGPS(InstrumentationRegistry.getContext()).lastLocation()

                assertTrue(location.longitude > 0)
            } catch (e: Exception) {
                fail(e.message ?: "Undefined reason")
            }
        }
    }

    @Test
    fun requestUpdates() {
        val locationList = ArrayList<Location>()

        job = CGGPS(InstrumentationRegistry.getContext()).requestUpdates(object : CGGPS.CoroutineLocationListener {
            override fun onLocationReceive(location: Location) {
                locationList.add(location)

                if (locationList.size == 3) {
                    job.cancel()
                    assertTrue(true)
                }
            }

            override fun onErrorReceive(error: Exception) {
                fail(error.message ?: "Undefined reason")
            }
        })
    }
}