# Product

## Platform

android

## Stack

Delegated: native Kotlin + Jetpack Compose (Material 3), single-activity, Gradle Kotlin DSL. Chosen because the machine already has JDK 17 and Android SDK 34–36 + build-tools 36 + adb bundled with Unity 6000.3 (`C:\Program Files\Unity\Hub\Editor\6000.3.8f1\Editor\Data\PlaybackEngines\AndroidPlayer`), so it builds from the command line without installing Android Studio. Node is also present but not used for the app.

## Users

One user: the owner, based in Basel, Switzerland. They want to fly cheaply and flexibly, and they check fares opportunistically rather than to book one fixed trip. This is a personal tool, so it has no accounts, Play Store listing or growth funnel.

## Product Purpose

The app scans many airlines and fares across several departure airports and date windows at once. It surfaces the best options and tells the owner when a nearby date, airport or ticket would save money or time compared with what they asked for. It succeeds when the owner opens it and immediately sees whether a trip is worth taking and which variant to book.

## Positioning

The app is a personal fare radar centred on Basel's unusual position. EuroAirport (BSL/MLH) sits on the Swiss/French/German border, and Zürich, Bern, Geneva, Strasbourg, Stuttgart and Frankfurt are within ground range. One search fans out across all of the departure airports the owner chooses, each with its own departure time window. Results are ranked with travel time and money on the same scale, rather than by one airline or one airport.

## Operating Context

- The owner uses it on their Android phone, often in short sessions ("anything cheap this weekend?").
- Home is Basel, CH. Prices are shown in CHF with EUR alongside, because EuroAirport fares are frequently quoted in EUR.
- Booking happens outside the app. The app hands off to the provider or airline link.

## Capabilities and Constraints

- **Home location:** a settable home city and airport (default Basel / BSL). The owner can mark which airports count as home alternatives and give each one a ground-travel time cost.
- **Departure cities:** several origin airports per search, each with its own departure date window and time-of-day window.
- **Last-minute offers:** a feed of cheap departures in the next few days from the owner's selected origins.
- **Multi-airline scan:** fares from many carriers, sorted and filtered by price, stops, airlines, duration, cabin and time windows.
- **Recommendations:** flags alternatives such as ±N days, a different origin, or a different ticket when they save money or time, and states the saving explicitly.
- **Filter presets:** saveable, named and reusable combinations of filters.
- **Data budget is free tier only.** The design assumes Travelpayouts Data API (Aviasales; free affiliate signup, cached prices, generous limits) for broad scanning, last-minute offers and date calendars. SerpApi Google Flights (free monthly quota) is used only for on-demand live verification of a shortlisted fare. The Amadeus Self-Service API was shut down on 17 July 2026 and must not be used. The app must cache aggressively and batch requests.
- **Prices from the cached source are indicative.** The UI must say when a price was last seen and must never present it as guaranteed.
- API keys are kept on the device. There is no backend.

## Brand Commitments

None yet. Working name: "FlightFilters" (folder name; undecided).

## Evidence on Hand

No real fare data is on hand yet. Data appears only once the owner adds API keys. Do not fabricate fares, savings or airline claims in shipped UI. Use clearly labelled sample data only for empty-state and offline development.

## Product Principles

1. **Answer first.** Every screen leads with the best option and how much it saves. Filters support that answer and come after it.
2. **Money and time are one decision.** A cheaper fare that costs three extra hours, or a drive to Zürich, is shown as the trade it is.
3. **Honest data.** Always show price freshness and the source, and show live verification separately from a cached estimate.
4. **Configure once, glance often.** Home, origins and presets are set once. Daily use takes one tap.
