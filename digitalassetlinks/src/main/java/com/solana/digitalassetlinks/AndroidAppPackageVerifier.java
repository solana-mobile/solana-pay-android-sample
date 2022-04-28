/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.digitalassetlinks;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.net.Uri;
import android.os.Build;
import android.util.Log;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Verifier for Android app packages using <a href="http://digitalassetlinks.org/">
 * Digital Asset Links</a>
 */
public class AndroidAppPackageVerifier {
    private static final String TAG = AndroidAppPackageVerifier.class.getSimpleName();

    private static final int HTTP_TIMEOUT_MS = 500; // 0.5s
    private static final int MAX_DOCUMENT_LENGTH = 50 * 1024; // 100KB (2 bytes per char)

    private final PackageManager mPackageManager;

    private final AtomicBoolean mVerificationInProgress = new AtomicBoolean(false);
    private final AtomicReference<HttpURLConnection> mHttpURLConnection = new AtomicReference<>(null);

    /**
     * Construct a new {@link AndroidAppPackageVerifier}
     * @param pm the {@link PackageManager} from which to look up app package details
     */
    public AndroidAppPackageVerifier(@NonNull PackageManager pm) {
        mPackageManager = pm;
    }

    /**
     * Verify that the specified app package is covered by a reliable statement in the Asset Links
     * JSON for the provided {@link Uri}.
     * <p/><i>NOTE: this method performs blocking network activity; it should not be invoked on the
     * UI thread of an app. A running instance of this function can be cancelled (from another
     * thread using the {@link #cancel()} method.</i>
     * @param packageName the android app package name (e.g. com.solana.example)
     * @param uri the URI with which this app package should be verified. This should be a URI that
     *      establishes a trust link of interest to the app. For example, if the use case is a
     *      Solana Pay transaction request, the URI might refer to the site which is generating the
     *      transaction.
     * @return true if the package was verified using the specified URI, or false if it was not
     *      verified
     * @throws CouldNotVerifyPackageException if the package verification process was not
     *      successful. Note that this doesn't necessarily imply that the package was not verified,
     *      but rather that a relationship could not be established.
     */
    public boolean verify(@NonNull String packageName, @NonNull Uri uri)
            throws CouldNotVerifyPackageException {
        if (!mVerificationInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("verification already in progress");
        }

        // Step 1: Check that mPackageName is present and visible to this app
        final int flags;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            flags = PackageManager.GET_SIGNING_CERTIFICATES;
        } else {
            flags = PackageManager.GET_SIGNATURES;
        }
        final PackageInfo packageInfo;
        try {
            packageInfo = mPackageManager.getPackageInfo(packageName, flags);
        } catch (PackageManager.NameNotFoundException e) {
            throw new CouldNotVerifyPackageException("Failed reading signatures for package " + packageName, e);
        }

        // Step 2: Query the set of signatures we're looking for
        final byte[][] packageCertSha256Fingerprints;
        final boolean[] signatureMask;
        final boolean requireAllSignatures;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (packageInfo.signingInfo.hasMultipleSigners()) {
                packageCertSha256Fingerprints = convertDEREncodedCertificatesToSHA256Fingerprints(
                        packageInfo.signingInfo.getApkContentsSigners());
                signatureMask = new boolean[packageCertSha256Fingerprints.length];
                requireAllSignatures = true;
            } else {
                packageCertSha256Fingerprints = convertDEREncodedCertificatesToSHA256Fingerprints(
                        packageInfo.signingInfo.getSigningCertificateHistory());
                signatureMask = new boolean[1];
                requireAllSignatures = false;
            }
        } else {
            packageCertSha256Fingerprints = convertDEREncodedCertificatesToSHA256Fingerprints(
                    packageInfo.signatures);
            signatureMask = new boolean[packageCertSha256Fingerprints.length];
            requireAllSignatures = true;
        }

        // Step 3: Create and configure an AssetLinksJSONParser object
        final AssetLinksJSONParser parser = new AssetLinksJSONParser();
        final AndroidAppTargetStatementMatcher androidAppMatcher =
                new AndroidAppTargetStatementMatcher(packageName);
        parser.addStatementMatcher(androidAppMatcher, (matcher, o) -> {
            final JSONObject target = o.getJSONObject(AssetLinksGrammar.GRAMMAR_TARGET); // mandatory field
            final JSONArray certFingerprints = target.getJSONArray(AssetLinksGrammar.GRAMMAR_SHA256_CERT_FINGERPRINTS); // mandatory field
            final int numCertFingerprints = certFingerprints.length();
            for (int i = 0; i < numCertFingerprints; i++) {
                final String certFingerprint = certFingerprints.getString(i);
                final byte[] fp = convertSHA256CertFingerprintStringToByteArray(certFingerprint);
                for (int j = 0; j < packageCertSha256Fingerprints.length; j++) {
                    final byte[] packageFp = packageCertSha256Fingerprints[j];
                    if (Arrays.equals(fp, packageFp)) {
                        signatureMask[requireAllSignatures ? j : 0] = true;
                    }
                }
            }
        });

        // Step 4: Derive the initial Asset Links JSON Uri
        final Uri assetLinksUri;
        try {
            assetLinksUri = AssetLinksJSONParser.getWellKnownAssetLinksUri(uri);
        } catch (IllegalArgumentException e) {
            throw new CouldNotVerifyPackageException("Could not create well-known Asset Links URI for " + uri, e);
        }

        // Step 5: Start the AssetLinksJSONParser, and respond to document requests
        Uri loadDocumentUri = parser.start(assetLinksUri);
        try {
            while (loadDocumentUri != null) {
                if (!mVerificationInProgress.get()) {
                    throw new CouldNotVerifyPackageException("Asset Links android app package verification cancelled");
                }

                final String document;
                try {
                    document = loadDocument(loadDocumentUri);
                } catch (Exception e) {
                    throw new CouldNotVerifyPackageException("Failed loading an Asset Links document " + loadDocumentUri, e);
                }

                if (!mVerificationInProgress.get()) {
                    throw new CouldNotVerifyPackageException("Asset Links android app package verification cancelled");
                }

                loadDocumentUri = parser.onDocumentLoaded(loadDocumentUri, document);
            }
        } catch (AssetLinksJSONParser.IllFormattedStatementException |
                AssetLinksJSONParser.TooManyIncludesException e) {
            Log.w(TAG, "Verification failed for package " + packageName, e);
            return false;
        }

        // No need to check parser.isComplete() or parser.isError() here; the first is handled by
        // waiting for parser.onDocumentLoaded(...) to return null, and the second by terminating on
        // a caught exception.

        // Step 6: Check the verification state produced by the Android App statement matcher
        boolean result = true;
        for (boolean b : signatureMask) {
            if (!b) {
                result = false;
                break;
            }
        }

        mVerificationInProgress.set(false);
        return result;
    }

    /**
     * Attempt to cancel a running {@link #verify(String, Uri)} for this
     * {@link AndroidAppPackageVerifier}. This will return immediately, however the verification may
     * continue for a short amount of time (until the next cancellation point).
     */
    public void cancel() {
        mVerificationInProgress.set(false);
        final HttpURLConnection urlConnection = mHttpURLConnection.get();
        if (urlConnection != null) {
            urlConnection.disconnect();
        }
    }

    @NonNull
    private String loadDocument(@NonNull Uri uri) throws IOException {
        final URL url = new URL(uri.toString());
        final HttpURLConnection urlConnection = (HttpURLConnection)url.openConnection();
        mHttpURLConnection.set(urlConnection);

        try {
            urlConnection.setConnectTimeout(HTTP_TIMEOUT_MS);
            urlConnection.setReadTimeout(HTTP_TIMEOUT_MS);
            urlConnection.setInstanceFollowRedirects(false);
            urlConnection.connect();
            final int responseCode = urlConnection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new UnsupportedOperationException("Asset Links document fetch failed; actual=" + responseCode + ", expected=" + HttpURLConnection.HTTP_OK);
            }
            final String mimeType = urlConnection.getContentType();
            if (!"application/json".equals(mimeType)) {
                // Spec deviation - don't fail on an incorrect content type
                Log.w(TAG, "Asset Links document type incorrect; actual=" + mimeType + ", expected=application/json");
            }
            StringBuilder documentBuilder = new StringBuilder();
            final InputStream rawInputStream = urlConnection.getInputStream();
            final InputStreamReader charInputStream = new InputStreamReader(rawInputStream, StandardCharsets.UTF_8);
            final char[] buf = new char[2048];
            while (true) {
                final int count = charInputStream.read(buf);
                if (count == -1) break;
                documentBuilder.append(buf, 0, count);
                final int documentLength = documentBuilder.length();
                if (documentLength > MAX_DOCUMENT_LENGTH) {
                    throw new UnsupportedOperationException("Asset Links document content too long; actual=" + documentLength + ", max=" + MAX_DOCUMENT_LENGTH);
                }
            }
            return documentBuilder.toString();
        } finally {
            urlConnection.disconnect();
            mHttpURLConnection.set(null);
        }
    }

    @NonNull
    private static byte[][] convertDEREncodedCertificatesToSHA256Fingerprints(
            @NonNull Signature[] derEncodedCerts) {
        final MessageDigest digest;
        try {
            digest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException e) {
            throw new UnsupportedOperationException("SHA-256 message digest algorithm not found", e);
        }
        final byte[][] sha256CertFingerprints = new byte[derEncodedCerts.length][];
        for (int i = 0; i < derEncodedCerts.length; i++) {
            sha256CertFingerprints[i] = digest.digest(derEncodedCerts[i].toByteArray());
        }
        return sha256CertFingerprints;
    }

    @NonNull
    private static byte[] convertSHA256CertFingerprintStringToByteArray(
            @NonNull String sha256CertFingerprint) {
        if (sha256CertFingerprint.length() != 95) {
            throw new IllegalArgumentException("sha256CertFingerprint string should consist of 32 2-character uppercase hex strings, separated by ':'");
        }
        final byte[] fp = new byte[32];
        for (int i = 0; i < 32; i++) {
            final int j = i * 3;
            String s = sha256CertFingerprint.substring(j, j + 2);
            if (Character.isLowerCase(s.charAt(0)) || Character.isLowerCase(s.charAt(1))) {
                throw new IllegalArgumentException("All alphabetic characters in sha256CertFingerprint should be upper-case");
            }
            try {
                fp[i] = (byte)Short.parseShort(s, 16);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Error while parsing hex substring", nfe);
            }

            if (i < 31 && sha256CertFingerprint.charAt(j + 2) != ':') {
                throw new IllegalArgumentException("Expected a ':' separator");
            }
        }
        return fp;
    }

    /**
     * This exception indicates that the verification process could not be completed (for e.g., due
     * to a network error). It indicates that no trust relationship between the app package and the
     * {@link Uri} was established, but does not necessarily  imply that the app package is not
     * trusted.
     */
    public static class CouldNotVerifyPackageException extends Exception {
        public CouldNotVerifyPackageException(String message) { super(message); }
        public CouldNotVerifyPackageException(String message, Throwable t) { super(message, t); }
    }
}