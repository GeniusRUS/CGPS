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
- SecurityException - if permissions (API >= 23) is not granted (`ACCESS_COARSE_LOCATION` for `CGPS::lastLocation`, and `ACCESS_FINE_LOCATION` for otherwise)
- TimeoutException - if location is not received in time (by default 5000ms)
- LocationException - others exceptions
- ServicesAvailabilityException - if Google or Huawei Services not available on device
- ResolutionNeedException (for GoogleCGPS/HuaweiCGPS only) - if user permission is requested to enable GPS using the system dialog

To query the current location, the accuracy of the determination (Accuracy.*) and the maximum timeout of the operation

For Huawei variant:

> The library requires the `agconnect-services.json` file in the project, without it a `LocationException` error will be thrown

In the lib are three version:

HardwareCGPS - not required Google or Huawei Services. Uses only Android Location Manager

More battery friendly variants:

- GoogleCGPS - uses Google Services
- HuaweiCGPS - for HMS devices

## Usage
* Get last location

```kotlin
val location: Location = HardwareCGPS(context).lastLocation()
```

* Get actual location

```kotlin
val location: Location = HardwareCGPS(context).actualLocation()
```

* Get location updates

```kotlin
val flowLocation: Flow<Result<Location>> = HardwareCGPS(context).requestUpdates()
```

* Get actual location with enable GPS request (**Google/Huawei implementation only**)

```kotlin
val location: Location = GoogleCGPS(context).actualLocationWithEnable()
```

**NOTE**: Use [ActivityResultApi](https://developer.android.com/training/basics/intents/result) with `StartIntentSenderForResult` contract to handle `ResolutionNeedException.intentSender`

## Install
Artifact is publishing to Maven Central. You can add this repository to your project with:
```gradle
repositories {
    mavenCentral()
}
```

Add to your .gradle file:

- Core module (with hardware implementation)

```gradle
implementation "io.github.geniusrus:cgps-core:$last_version"
```

- Google module (include Core)

```gradle
implementation "io.github.geniusrus:cgps-google:$last_version"
```

- Huawei module (include Core)

```gradle
implementation "io.github.geniusrus:cgps-huawei:$last_version"
```

## Legacy

Old artifact `io.github.geniusrus:cgps` is still available at `2.1.0` version. But it will no longer be supported

## Sample
The sample is on `app` module

## Developed by 
* [Viktor Likhanov](mailto:Gen1usRUS@yandex.ru)
* [Umalt Isakhazhiev](mailto:UIsakxazhiev@unitbean.com)

## License
```
Apache v2.0 License

Copyright (c) 2018 Viktor Likhanov
