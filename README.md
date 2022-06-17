# Solana Pay on Android sample

_Part of the [Solana Mobile Stack](https://github.com/solana-mobile/solana-mobile-stack-sdk)_

Join us on [Discord](TODO)

## Summary

An integration guide and sample for Android wallets on handling [Solana Pay](https://www.solanapay.com) URIs generated externally (e.g. via NFC taps, QR codes, web links, etc). 

## Target audience

This repository is primarily intended for consumption by developers of Android wallet apps.

## What's included

- An [integration guide](docs/integration_guide.md) for Android wallets
  - Android apps and web sites utilizing Solana Pay, and developers interesting in creating Solana Pay URIs that are accessible via QR codes or NFC taps, may also find this guide useful
- A set of [Solana Pay API and support classes](pay) for parsing Solana Pay URIs
- An [Android-compatible implementation](digitalassetlinks) of [Digital Asset Links](http://digitalassetlinks.org/)
  - This can be used by Android apps to verify that an installed package is associated with a specified web domain
- A [sample app](app) demonstrating the techniques described in the [integration guide](docs/integration_guide.md)

## How to build

All Android projects within this repository can be built using [Android Studio](https://developer.android.com/studio)

### How to reference these libraries in your project

_Check back soon! We plan to publish the [pay](pay) and [digitalassetlinks](digitalassetlinks) libraries on Maven Central._

## Developer documentation

`pay`: [JavaDoc](TODO)

`digitalassetlinks`: [JavaDoc](TODO)

## Get involved

Contributions are welcome! Go ahead and file Issues, open Pull Requests, or join us on our [Discord](TODO) to discuss this SDK.
