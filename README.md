# CGPS
<a href='https://bintray.com/geniusrus/CGPS/com.geniusrus.cgps/_latestVersion'><img src='https://api.bintray.com/packages/geniusrus/CGPS/com.geniusrus.cgps/images/download.svg'></a>

## Short description
Simple obtaining of the current or the last location with the Kotlin Coroutines

## Details
By default, there are 5-seconds timeout for operation

Also, it is checked whether the GPS adapter is enabled on the device and whether there are valid data to receive

If one of the conditions is not valid, then the corresponding exception is thrown:

- LocationDisabledException - if GPS-adapter is disabled
- SecurityException - if permissions (API >= 23) is not granted
- TimeoutException - if location is not received in time (by default 5000ms)
- LocationException - others exceptions

To query the current location, the accuracy of the determination (Accuracy.*) and the maximum timeout of the operation

In the lib are two version:

CGPS - not required Google Services. Uses only Android Location Manager

CGGPS - uses Google Services and more battery friendly

## Usage
* Get last location
```kotlin
val location = CGGPS(context).lastLocation()
```

* Get actual location
```kotlin
val location = CGGPS(context).actualLocation()
```

* Get location updates
```kotlin
val location = CGGPS(context).requestUpdates(listener)
```
NOTE: This call is not suspend and just works like improved location update listener

## Install
Add to your .gradle file:
```gradle
implementation 'com.geniusrus.cgps:cgps:1.0.0'
```
## Sample
The sample is on `app` module

## Developed by 
* Viktor Likhanov

Yandex: [Gen1usRUS@yandex.ru](mailto:Gen1usRUS@yandex.ru)

## License
```
Apache v2.0 License

Copyright (c) 2018 Viktor Likhanov
