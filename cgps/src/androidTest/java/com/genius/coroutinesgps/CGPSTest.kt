package com.genius.coroutinesgps

import android.location.Location
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.rule.GrantPermissionRule
import com.genius.cgps.CGPS
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CGPSTest {

    private lateinit var job: Job

    @Rule
    @JvmField
    val runtimePermissionRule = GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION)

    @Test
    fun actualLocation() {
        runBlocking {
            try {
                val location = CGPS(ApplicationProvider.getApplicationContext()).actualLocation()

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
                val location = CGPS(ApplicationProvider.getApplicationContext()).lastLocation()

                assertTrue(location.longitude > 0)
            } catch (e: Exception) {
                fail(e.message ?: "Undefined reason")
            }
        }
    }

    @Test
    fun requestUpdates() {
        val locationList = ArrayList<Result<Location>>()

        runBlocking {
            job = launch {
                CGPS(ApplicationProvider.getApplicationContext()).requestUpdates().collect {
                    locationList.add(it)

                    if (locationList.size == 3) {
                        job.cancel()
                        assertTrue(true)
                    }
                }
            }
        }
    }
}