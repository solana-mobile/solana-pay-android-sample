/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.digitalassetlinks;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;

import androidx.annotation.NonNull;

import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URI;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;

/**
 * Verifier for Android app packages using <a href="http://digitalassetlinks.org/">
 * Digital Asset Links</a>
 */
public class AndroidAppPackageVerifier extends URISourceVerifier {
    private final PackageManager mPackageManager;

    /**
     * Construct a new {@link AndroidAppPackageVerifier}
     * @param pm the {@link PackageManager} from which to look up app package details
     */
    public AndroidAppPackageVerifier(@NonNull PackageManager pm) {
        mPackageManager = pm;
    }

    /**
     * Verify that the specified app package is covered by a reliable statement in the Asset Links
     * JSON for the provided {@link URI}.
     * <p><i>NOTE: this method performs blocking network activity; it should not be invoked on the
     * UI thread of an app. A running instance of this function can be cancelled (from another
     * thread using the {@link #cancel()} method.</i></p>
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
    public boolean verify(@NonNull String packageName, @NonNull URI uri)
            throws CouldNotVerifyPackageException {
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            throw new CouldNotVerifyPackageException(
                    "Android app packages can only be verified with secure source URIs");
        }

        // Check that mPackageName is present and visible to this app
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

        // Query the set of signatures we're looking for
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

        // Create and configure an AssetLinksJSONParser object
        final StatementMatcher androidAppMatcher = StatementMatcher
                .createAndroidAppStatementMatcher(
                        AssetLinksGrammar.GRAMMAR_RELATION_HANDLE_ALL_URLS,
                        packageName,
                        null);
        final AssetLinksJSONParser.StatementMatcherCallback androidAppMatcherCallback =
            (matcher, o) -> {
                final JSONObject target = o.getJSONObject(AssetLinksGrammar.GRAMMAR_TARGET); // mandatory field
                final JSONArray certFingerprints = target.getJSONArray(AssetLinksGrammar.GRAMMAR_ANDROID_APP_SHA256_CERT_FINGERPRINTS); // mandatory field
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
            };

        // Do URI source verification with the Android app package target matcher
        try {
            // NOTE: ignore the return value of verify; it conveys whether there were acceptable
            // parsing errors that do not affect the validity of the verification.
            verify(uri, new StatementMatcherWithCallback(androidAppMatcher, androidAppMatcherCallback));
        } catch (CouldNotVerifyException e) {
            throw new CouldNotVerifyPackageException("Could not verify package " + packageName, e);
        } catch (IllegalArgumentException e) {
            throw new CouldNotVerifyPackageException("Source URI not a valid HTTP or HTTPS URL: " + uri, e);
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

        return result;
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
        // NOTE: the format of the certificate fingerprint string is already checked against
        // AssetLinksGrammer.SHA256_CERT_FINGERPRINT_PATTERN by AssetLinksJSONParser
        final byte[] fp = new byte[32];
        for (int i = 0; i < 32; i++) {
            final int j = i * 3;
            String s = sha256CertFingerprint.substring(j, j + 2);
            try {
                fp[i] = (byte)Short.parseShort(s, 16);
            } catch (NumberFormatException nfe) {
                throw new IllegalArgumentException("Error while parsing hex substring", nfe);
            }
        }
        return fp;
    }

    /**
     * This exception indicates that the verification process could not be completed (for e.g., due
     * to a network error). It indicates that no trust relationship between the app package and the
     * {@link URI} was established, but does not necessarily  imply that the app package is not
     * trusted.
     */
    public static class CouldNotVerifyPackageException extends CouldNotVerifyException {
        public CouldNotVerifyPackageException(String message) { super(message); }
        public CouldNotVerifyPackageException(String message, Throwable t) { super(message, t); }
    }
}