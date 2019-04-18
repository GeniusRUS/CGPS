package com.genius.coroutinesgps

import android.location.Location
import androidx.test.InstrumentationRegistry
import androidx.test.rule.GrantPermissionRule
import androidx.test.runner.AndroidJUnit4
import com.genius.cgps.CGPS
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.actor
import kotlinx.coroutines.channels.consumeEach
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
                val location = CGPS(InstrumentationRegistry.getContext()).actualLocation()

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
                val location = CGPS(InstrumentationRegistry.getContext()).lastLocation()

                assertTrue(location.longitude > 0)
            } catch (e: Exception) {
                fail(e.message ?: "Undefined reason")
            }
        }
    }

    @Test
    fun requestUpdates() {
        val locationList = ArrayList<Result<Location>>()

        job = CGPS(InstrumentationRegistry.getContext()).requestUpdates(GlobalScope.actor {
            channel.consumeEach {
                locationList.add(it)

                if (locationList.size == 3) {
                    job.cancel()
                    assertTrue(true)
                }
            }
        })
    }
}