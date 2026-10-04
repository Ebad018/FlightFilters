# FlightFilters

A personal Android fare radar for travel from Basel. It scans several departure airports at once, each with its own date and time window, across many airlines. Results appear as a metro-style route network. The app also flags a nearby day, airport or faster ticket when it would save money or time.

## Screens

- **Loop**: circle trips like Looping: home → 2–4 cities → home, a few nights in each, with every leg the cheapest direct flight in its window. Pick **Cheapest cities** (the app chooses the chain) or **My cities** (your order, and the reverse is tried too). Set start dates, nights per city and a total budget. Each leg links to its booking page.
- **Deals date range**: 2 weeks, 1 month, 3 months, 6 months, or **Pick dates…** for any range up to a year ahead. Last-minute **Departures** moved to the board icon in Deals' top bar.
- **Ask** (AI): type a request in plain words ("cheap long weekend in the sun late October", "Tokyo in April, flexible"). The app runs the real scan and the AI explains the results; prices always come from the scan. It works free with no key (LLM7, with Pollinations as backup). A built-in parser understands places, dates, budgets, direct-only, trip length and deal-hunting even when the AI is down. Optional keys: Gemini, Groq and OpenRouter (free tiers); OpenAI and Claude (paid); or any OpenAI-compatible endpoint. Set these in Settings → AI assistant.
- **Destinations**: Plan's search covers every scheduled airport in the world (OurAirports, public domain, bundled). You can search by city, airport name, country or code, and add departure airports anywhere the same way.
- **Deals** (opens first): scans your home airport (Basel by default; switch on "+ nearby airports" for ZRH, SXB, BRN, FKB, STR, GVA, FRA and FMM) for 2 weeks, 1 month or 3 months ahead, one way or return. When the Plan has destinations, Deals, promos and Departures show only fares to them ("Only to …" chip) until you switch to all destinations. It shows fares that are **unusually cheap for their route** (for example "−64%, usually CHF 89"), the **cheapest countries** to reach, and **promo and error fares**. Tap a deal to check it live, book it, or "compare all airlines around this date".
- **Network**: results for the current plan. Each departure airport is a coloured line, each change is an interchange circle, and each destination is a terminus. Tap a line to trace it. Below the diagram are the cheapest fare per day, "Worth a look" suggestions and the full itinerary list (sort by Best / Cheapest / Fastest).
- **Departures**: last-minute offers, meaning the cheapest departures anywhere in the next few days from your lines.
- **Plan**: departure airports with per-airport date and hour windows, destination (or Anywhere), one way or return, ± flex days, and filters for price cap, changes, max flight length, airlines (only/avoid) and cabin.
- **Lines**: saved plans (presets), each run with one tap.
- **Settings** (gear icon): home airport, ground time to each airport, what an hour of your time is worth, EUR display, theme (Midnight / Porcelain / System), API keys, and sample data.

"Best" ranks by **trip cost**: ticket price plus door-to-door time (flight + getting to the airport) multiplied by your CHF-per-hour value. That's how a cheaper Zürich flight can lose to a pricier Basel one.

## Fare data (free, no keys needed)

| Source | How | Used for |
|---|---|---|
| Google Flights | reads the public results page (each result's accessibility label) | live prices on every airline for a named route and date, "Check live price", and Google's "prices are currently low/typical/high" |
| Ryanair fare finder | the public JSON endpoint behind ryanair.com's cheap-flights pages | the cheapest fare to everywhere (Deals, Departures, Anywhere scans) and per-day calendars, used to spot real price drops |
| Fly4free (Europe) | public RSS feed | hand-picked promo and error fares, flagged when they leave from near Basel |
| Travelpayouts / Aviasales | optional free token | extra cached fares from more airlines |
| SerpApi | optional free key | fallback for live checks if Google can't be read |

**How "price drop" works:** for each cheap destination, the app reads that route's Ryanair price for every day of the month. It compares the fare against the median day. "−64%" means 64% below a normal day on that route.

**Limits:**
- easyJet and Wizz Air block automated requests, so the Deals scan doesn't include them. easyJet fares still appear in Google Flights results for a named route.
- These sources aren't official APIs. If a site changes its page, that source quietly returns nothing, and the app keeps working with the others.
- Responses are cached on the phone (Google 30 min, Ryanair 3–6 h, feed 1 h), so repeated taps don't hammer the sites.

Parser tests that run on real captured responses are in `app/src/test`; run them with `./gradlew testDebugUnitTest`.

(The Amadeus Self-Service API, the usual free option, was shut down on 17 July 2026.)

## Build and install

This project builds with the JDK and Android SDK bundled with Unity 6000.3, so Android Studio isn't required.

```bash
JAVA_HOME="/c/Program Files/Unity/Hub/Editor/6000.3.8f1/Editor/Data/PlaybackEngines/AndroidPlayer/OpenJDK" ./gradlew assembleDebug
```

The APK is written to `app/build/outputs/apk/debug/app-debug.apk`. Copy it to your phone and open it, allowing "install unknown apps" when asked. Alternatively, with USB debugging on:

```bash
"/c/Program Files/Unity/Hub/Editor/6000.3.8f1/Editor/Data/PlaybackEngines/AndroidPlayer/SDK/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

`local.properties` points `sdk.dir` at Unity's SDK. The "SDK is read-only" warnings during the build are harmless.

## Stack

Kotlin 2.2, Jetpack Compose (Material 3), Navigation Compose, DataStore, OkHttp, kotlinx.serialization. minSdk 26, targetSdk 36. It has no backend, and keys stay on the device.
