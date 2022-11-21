# Solana Pay on Android sample

[![Release (latest by date)](https://img.shields.io/github/v/release/solana-mobile/solana-pay-android-sample)](https://github.com/solana-mobile/solana-pay-android-sample/releases/latest)
[![Android CI](https://github.com/solana-mobile/solana-pay-android-sample/actions/workflows/android.yml/badge.svg)](https://github.com/solana-mobile/solana-pay-android-sample/actions/workflows/android.yml)

_Part of the [Solana Mobile Stack](https://github.com/solana-mobile/solana-mobile-stack-sdk)_

Join us on [Discord](https://discord.gg/solanamobile)

## Summary

An integration guide and sample for Android wallets on handling [Solana Pay](https://www.solanapay.com) URIs generated externally (e.g. via NFC taps, QR codes, web links, etc). 

## Target audience

This repository is primarily intended for consumption by developers of Android wallet apps.

## What's included

- An [integration guide](docs/integration_guide.md) for Android wallets
  - Android apps and web sites utilizing Solana Pay, and developers interesting in creating Solana Pay URIs that are accessible via QR codes or NFC taps, may also find this guide useful
- A set of [Solana Pay API and support classes](pay) for parsing Solana Pay URIs
- A [sample app](app) demonstrating the techniques described in the [integration guide](docs/integration_guide.md)

## How to build

All Android projects within this repository can be built using [Android Studio](https://developer.android.com/studio)

### How to reference these libraries in your project

#### Gradle

```
dependencies {
    implementation 'com.solanamobile:solana-pay-android:1.0.0'
}
```

## Developer documentation

`pay`: [JavaDoc](https://solana-mobile.github.io/solana-pay-android-sample/pay/javadoc/index.html)

## Get involved

Contributions are welcome! Go ahead and file Issues, open Pull Requests, or join us on our [Discord](https://discord.gg/solanamobile) to discuss this SDK.
