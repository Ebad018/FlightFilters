package com.ebad.flightfilters

import android.app.Application
import com.ebad.flightfilters.data.CachedHttp
import com.ebad.flightfilters.data.DealsFeed
import com.ebad.flightfilters.data.FareRepository
import com.ebad.flightfilters.data.FxRates
import com.ebad.flightfilters.data.GoogleFlights
import com.ebad.flightfilters.data.Ryanair
import com.ebad.flightfilters.data.SerpApi
import com.ebad.flightfilters.data.SettingsStore
import com.ebad.flightfilters.data.TravelpayoutsApi
import java.io.File

class FlightFiltersApp : Application() {
    lateinit var store: SettingsStore
        private set
    lateinit var repository: FareRepository
        private set
    lateinit var fx: FxRates
        private set
    lateinit var ai: com.ebad.flightfilters.data.AiClient
        private set
    lateinit var assistant: com.ebad.flightfilters.data.Assistant
        private set

    override fun onCreate() {
        super.onCreate()
        val http = CachedHttp(File(cacheDir, "fares"))
        store = SettingsStore(this)
        repository = FareRepository(TravelpayoutsApi(http), SerpApi(http), GoogleFlights(http), Ryanair(http), DealsFeed(http), http)
        fx = FxRates(http)
        val world = com.ebad.flightfilters.data.AirportDb.get(this)
        com.ebad.flightfilters.data.Airports.worldCity = { code -> world.get(code)?.city }
        ai = com.ebad.flightfilters.data.AiClient()
        assistant = com.ebad.flightfilters.data.Assistant(ai, world)
    }
}
