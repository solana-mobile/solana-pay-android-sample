# Solana Pay - Android integration guide

## Summary
This document provides a recommended approach to exposing [Solana Pay](https://solanapay.com/) support by a native Android wallet app to the rest of the Android system. Nothing in this document should be considered normative for Solana Pay. Other integration approaches are also possible; this document is just a recommended starting point for Android.

## Prerequisites
a native Android wallet app that supports the [Solana Pay protocol](https://github.com/solana-labs/solana-pay)

## Security
"Security" in the context of Android integration of Solana Pay URIs is ensuring that the URIs are faithfully transmitted to the wallet, and are not tampered with in transit. Each type of [integration point](#integration-points) provides different guarantees about Solana Pay URI security.

The contents of the Solana Pay transfer or transaction request is not addressed by this security model; wallets retain ownership of verifying the contents of a Solana Pay transaction.

## Activity entrypoints
The primary entrypoint should be an `Activity`, which responds to an `Intent` with category=`android.intent.category.BROWSABLE` and action=`android.intent.action.VIEW`. For example,

```
<activity
    android:name=".SolanaPayActivity"
    android:exported="true">
    <intent-filter>
        <action android:name="android.intent.action.VIEW" />
        <category android:name="android.intent.category.DEFAULT" />
        <category android:name="android.intent.category.BROWSABLE" />
        <data android:scheme="solana" />
    </intent-filter>
</activity>
```

This provides a standard entrypoint for any source that can generate and launch URIs (native apps, browser, QR codes, etc). Note that this does not provide any assurances on the source of the Solana Pay link, nor does it convey any details on the mechanism (browser link, QR code, NFC, etc) by which it was received.

Specific integrations may use alternate entrypoints (either `<activity>` or `<activity-alias>`) with different `Intent` filters and/or permission requirements. If `<activity-alias>` is used, the wallet app can inspect [`getComponentName()`](https://developer.android.com/reference/android/app/Activity?hl=en#getComponentName()) of the `Activity` to identify from which `<activity>` or `<activity-alias>` element it was launched. If the `<intent-filter>` criteria for an alternate entrypoint is identical to that of the primary entrypoint, a higher `order` should be specified for the auxiliary entrypoint `<intent-filter>`, to ensure it is preferentially selected for handling the `Intent`.

## Integration points

### NFC (for tap-to-pay)

#### Entry
This integration uses a custom entrypoint.

```
<activity-alias
    android:name=".SolanaPayActivityViaNFC"
    android:targetActivity=".SolanaPayActivity"
    android:exported="true"
    android:permission="android.permission.DISPATCH_NFC_MESSAGE">
    <intent-filter>
        <action android:name="android.nfc.action.NDEF_DISCOVERED" />
        <category android:name="android.intent.category.DEFAULT" />
        <data android:scheme="solana" />
    </intent-filter>
</activity-alias>
```

To receive NFC `Intent`s, the wallet app should ensure it has the necessary [NFC permission request](https://developer.android.com/guide/topics/connectivity/nfc/nfc#manifest) in its manifest.

#### Return value
None possible; this integration is started by the system with [`startActivity`](https://developer.android.com/reference/android/app/Activity#startActivity(android.content.Intent)).

#### Added security
The permission filter applied to this entrypoint ensures that the NFC message originated with the system. While this permission is [marked with @hide](https://android.googlesource.com/platform/frameworks/base/+/25976ab7be91423c7f2e185f5f04236011df09c0/core/res/AndroidManifest.xml#5350), it is intended to be used for this purpose.

### Web browser with fallback URI

#### Entry
This integration uses the standard [Primary entrypoint](#activity-entrypoints), but with a modified URI. When the web document generating the Solana Pay URI detects it is running on an Android user agent, it may choose to generate an `intent://` URI to encode the Solana Pay URI:

```
intent://<solana_pay_uri_path_and_query_params>#Intent;scheme=solana;S.browser_fallback_url=<fallback_uri>;end
```

This URI format adds the `S.browser_fallback_url` parameter, which is loaded by the web browser if no app is installed to handle Solana Pay links. This can be used to display a page to the user explaining Solana Pay, and how to start using it.

#### Return value
None possible; this integration is started by web browsers with [`startActivity`](https://developer.android.com/reference/android/app/Activity#startActivity(android.content.Intent)).

#### Added security
None - this integration can be utilized by any app (including native apps masquerading as a web browser to the wallet)

### Native apps with result code

#### Entry
This integration uses the standard [Primary entrypoint](#activity-entrypoints), except that the `Intent` is dispatched to the wallet `Activity` with [`startActivityForResult`](https://developer.android.com/reference/android/app/Activity#startActivityForResult(android.content.Intent,%20int)), which allows the calling app identity to be determined.

#### Return value
Immediately upon creation, the result code of the `Activity` should be set to [`RESULT_CANCELED`](https://developer.android.com/reference/android/app/Activity#RESULT_CANCELED. This ensures that, if the `Activity` is dismissed by the user or killed by the system before the transaction is completed, the caller will receive an informative result.

After the Solana Pay URI has been fully processed (success or failure), the Activity should set its [result code](https://developer.android.com/reference/android/app/Activity#setResult(int,%20android.content.Intent)) to:

- [`RESULT_OK`](https://developer.android.com/reference/android/app/Activity#RESULT_OK) if the transaction was completed successfully
- `RESULT_NOT_VERIFIED (501)` if the wallet could not verify the app or the validity of the transaction
- `RESULT_FAILED (502)`  if the transaction did not complete successfully
- `RESULT_DECLINED (503)`  if the user declined to authorize the transaction

Success or failure, if a transaction was attempted to be submitted to the network, a result `Intent` should be set along with the result code. It should contain an extra, `com.solana.pay.SIGNATURE`, set to the base58-encoded transaction signature.

#### Added security
If the Solana Pay URI is a transaction request, the wallet should check the [Digital Asset Links](https://developers.google.com/digital-asset-links) file for the request URI’s site, and ensure that it contains an [`android_app` statement](https://developers.google.com/digital-asset-links/v1/statements) with a certificate fingerprint matching a signing certificate for the calling app identity. If not, the `Activity` result code should be set to `RESULT_NOT_VERIFIED`, followed by [`finish`](https://developer.android.com/reference/android/app/Activity#finish()).

The wallet app is free to additionally inspect the calling app identity and make its own decisions of whether to trust the Solana Pay URI sent by it, returning `RESULT_NOT_VERIFIED` if it decided to decline the request.

#### Commentary

The result codes and `com.solana.pay.SIGNATURE` extra represent extensions to the standard Solana Pay URI handling behavior. Apps invoking a Solana Pay URI with [`startActivityForResult`](https://developer.android.com/reference/android/app/Activity#startActivityForResult(android.content.Intent,%20int)) should not assume that every wallet will implement this behavior, and should gracefully handle unknown result codes and `RESULT_OK` without the `com.solana.pay.SIGNATURE` extra. 
