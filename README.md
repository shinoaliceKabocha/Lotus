# Lotus

In Android application and firmware development, information from Logcat is extremely useful.
This library provides a function to keep Logcat in the device.
This library is still under development, so the supported OS versions are limited.

## Warning
- Please be aware that this library is intended for developers, and that some user information may be included in the Log, so please handle it carefully.
- The OS version that we have tested is Android 8.1.
- We have confirmed that it does not behave as expected on Android 12.

# Usage

Just create a `LogCollect` instance and run `start()`.
Don't forget to `stop()` to stop the logging.

```.kt
private var logcollect: LogCollect? = null

override fun onResume() {
    super.onResume()
    logcollect = LogCollect(this, object: LogCollect.ILogCollect {
        override fun onReceivedZipFile(zipFile: File) {}
    }).apply {
        start()
    }
}

override fun onPause() {
    super.onPause()
    logcollect?.stop()
}
````

The easiest way to check that Logcat is accumulating is to run `run-as`.

````
$ adb shell
$ run-as com.android.your.application.name
$ ls app_log/
1_2022-02-08-11-11.log index
````

# build

```
$ git clone <this repo>
$ cd <this repo dir>

$ . /gradlew assembleRelease
# If wsl2, powershell.exe . /gradlew assembleRelease

$ ls . /app/lotus/build/outputs/aar/
lotus-release.aar
```

# Install

- Add the built `aar` to your repository.

```
# copy your repository

cp lotus-release.aar <your project>/lotus/
```

```
# create lotus build.gradle

+ configurations.maybeCreate("default")
+ artifacts.add("default", file('lotus-release.aar'))
```

```
# settings.gradle

+ include `:lotus`
```

```
# app build.gradle

+ implementation project(':lotus')
```

