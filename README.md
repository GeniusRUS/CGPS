# CGPS
[![Maven Central](https://maven-badges.herokuapp.com/maven-central/io.github.geniusrus/cgps/badge.svg)](https://maven-badges.herokuapp.com/maven-central/io.github.geniusrus/cgps)
[![CircleCI](https://circleci.com/gh/GeniusRUS/CGPS/tree/master.svg?style=svg)](https://circleci.com/gh/GeniusRUS/CGPS/tree/master)
[![codebeat badge](https://codebeat.co/badges/fb32140e-dcac-4214-9dbf-5b70aaa8592b)](https://codebeat.co/projects/github-com-geniusrus-cgps-master)

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
- ServicesAvailabilityException - if Google Services not available on device
- ResolutionNeedException (for CGGPS only) - if user permission is requested to enable GPS using the system dialog

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
val location = CGGPS(context).requestUpdates(): Flow<Result<Location>>
```

* Get actual location with enable GPS request
```kotlin
val location = CGGPS(context).actualLocationWithEnable()
```
**NOTE**: In onActivityResult you must handle the call in `itActivityResult` with the passed `requestCode`

## Install
Artifact is publishing to Maven Central. You can add this repository to your project with:
```gradle
repositories {
    mavenCentral()
}
```

Add to your .gradle file:
```gradle
implementation "io.github.geniusrus:cgps:$last_version"
```
## Sample
The sample is on `app` module

## Developed by 
* [Viktor Likhanov](mailto:Gen1usRUS@yandex.ru)
* [Umalt Isakhazhiev](mailto:UIsakxazhiev@unitbean.com)

## License
```
Apache v2.0 License

Copyright (c) 2018 Viktor Likhanov
