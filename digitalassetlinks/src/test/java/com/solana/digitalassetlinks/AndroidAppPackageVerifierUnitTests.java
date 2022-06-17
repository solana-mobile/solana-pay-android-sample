/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.digitalassetlinks;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.content.pm.SigningInfo;
import android.os.Build;

import androidx.annotation.NonNull;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={ RobolectricConfig.MIN_SDK, Build.VERSION_CODES.P, RobolectricConfig.CUR_SDK }) // SigningInfo introduced in P
public class AndroidAppPackageVerifierUnitTests {
    // SHA256(0x01) == "4B:F5:12:2F:34:45:54:C5:3B:DE:2E:BB:8C:D2:B7:E3:D1:60:0A:D6:31:C3:85:A5:D7:CC:E2:3C:77:85:45:9A"
    private static final byte[] CERT_1 = new byte[] { 0x01 };

    // SHA256(0x02) == "DB:C1:B4:C9:00:FF:E4:8D:57:5B:5D:A5:C6:38:04:01:25:F6:5D:B0:FE:3E:24:49:4B:76:EA:98:64:57:D9:86"
    private static final byte[] CERT_2 = new byte[] { 0x02 };

    // SHA256(0x03) == "08:4F:ED:08:B9:78:AF:4D:7D:19:6A:74:46:A8:6B:58:00:9E:63:6B:61:1D:B1:62:11:B6:5A:9A:AD:FF:29:C5"
    private static final byte[] CERT_3 = new byte[] { 0x03 };

    private static final String ANDROID_APP_STATEMENT_LIST_CERTS_2_3 =
            "[{\"relation\": [\"delegate_permission/common.handle_all_urls\"], " +
                    "\"target\": {" +
                    "\"namespace\": \"android_app\", " +
                    "\"package_name\": \"com.test.sample\", " +
                    "\"sha256_cert_fingerprints\": [" +
                    "\"DB:C1:B4:C9:00:FF:E4:8D:57:5B:5D:A5:C6:38:04:01:25:F6:5D:B0:FE:3E:24:49:4B:76:EA:98:64:57:D9:86\", " + // SHA256(CERT_2)
                    "\"08:4F:ED:08:B9:78:AF:4D:7D:19:6A:74:46:A8:6B:58:00:9E:63:6B:61:1D:B1:62:11:B6:5A:9A:AD:FF:29:C5\"" +  // SHA256(CERT_3)
                    "]}}]";

    @Test
    public void testAppPackageVerificationSuccess()
            throws AndroidAppPackageVerifier.CouldNotVerifyPackageException {
        ArrayList<MockWebContentServer.Content> mockWebContent = new ArrayList<>();
        mockWebContent.add(new MockWebContentServer.Content(
                URI.create("https://www.test.com/.well-known/assetlinks.json"),
                HttpURLConnection.HTTP_OK,
                "application/json",
                ANDROID_APP_STATEMENT_LIST_CERTS_2_3));

        final PackageManager pm = mockPackageManagerFactory(
                "com.test.sample", new byte[][] { CERT_2, CERT_1 }, false);

        final AndroidAppPackageVerifierHarness verifier = 
                new AndroidAppPackageVerifierHarness(pm, mockWebContent);
        boolean verified = verifier.verify("com.test.sample", URI.create("https://www.test.com"));
        assertTrue(verified);
    }

    @Test
    public void testAppPackageVerificationNoAssetLinks() {
        ArrayList<MockWebContentServer.Content> mockWebContent = new ArrayList<>();
        mockWebContent.add(new MockWebContentServer.Content(
                URI.create("https://www.test.com/.well-known/assetlinks.json"),
                HttpURLConnection.HTTP_OK,
                "application/json",
                ANDROID_APP_STATEMENT_LIST_CERTS_2_3));

        final PackageManager pm = mockPackageManagerFactory(
                "com.test.sample", new byte[][] { CERT_2, CERT_1 }, false);

        final AndroidAppPackageVerifierHarness verifier =
                new AndroidAppPackageVerifierHarness(pm, mockWebContent);
        assertThrows(AndroidAppPackageVerifier.CouldNotVerifyPackageException.class,
                () ->verifier.verify("com.test.sample", URI.create("https://www.other.com")));
    }

    @Test
    public void testAppPackageVerificationNoMatchingPackageInAssetLinks()
            throws AndroidAppPackageVerifier.CouldNotVerifyPackageException {
        ArrayList<MockWebContentServer.Content> mockWebContent = new ArrayList<>();
        mockWebContent.add(new MockWebContentServer.Content(
                URI.create("https://www.test.com/.well-known/assetlinks.json"),
                HttpURLConnection.HTTP_OK,
                "application/json",
                ANDROID_APP_STATEMENT_LIST_CERTS_2_3));

        final PackageManager pm = mockPackageManagerFactory(
                "com.test.other", new byte[][] { CERT_2, CERT_1 }, false);

        final AndroidAppPackageVerifierHarness verifier =
                new AndroidAppPackageVerifierHarness(pm, mockWebContent);
        boolean verified = verifier.verify("com.test.other", URI.create("https://www.test.com"));
        assertFalse(verified);
    }

    @Test
    public void testAppPackageVerificationInsecureAssetLinks() {
        ArrayList<MockWebContentServer.Content> mockWebContent = new ArrayList<>();
        mockWebContent.add(new MockWebContentServer.Content(
                URI.create("http://www.test.com/.well-known/assetlinks.json"),
                HttpURLConnection.HTTP_OK,
                "application/json",
                ANDROID_APP_STATEMENT_LIST_CERTS_2_3));

        final PackageManager pm = mockPackageManagerFactory(
                "com.test.sample", new byte[][] { CERT_2, CERT_1 }, false);

        final AndroidAppPackageVerifierHarness verifier =
                new AndroidAppPackageVerifierHarness(pm, mockWebContent);
        assertThrows(AndroidAppPackageVerifier.CouldNotVerifyPackageException.class,
                () ->verifier.verify("com.test.sample", URI.create("http://www.test.com")));
    }

    @Test
    public void testAppPackageVerificationNoMatchingCertificate()
            throws AndroidAppPackageVerifier.CouldNotVerifyPackageException {
        ArrayList<MockWebContentServer.Content> mockWebContent = new ArrayList<>();
        mockWebContent.add(new MockWebContentServer.Content(
                URI.create("https://www.test.com/.well-known/assetlinks.json"),
                HttpURLConnection.HTTP_OK,
                "application/json",
                ANDROID_APP_STATEMENT_LIST_CERTS_2_3));

        final PackageManager pm = mockPackageManagerFactory(
                "com.test.sample", new byte[][] { CERT_1 }, false);

        final AndroidAppPackageVerifierHarness verifier =
                new AndroidAppPackageVerifierHarness(pm, mockWebContent);
        boolean verified = verifier.verify("com.test.sample", URI.create("https://www.test.com"));
        assertFalse(verified);
    }

    @Test
    public void testAppPackageVerificationMultipleSignersSuccess()
            throws AndroidAppPackageVerifier.CouldNotVerifyPackageException {
        ArrayList<MockWebContentServer.Content> mockWebContent = new ArrayList<>();
        mockWebContent.add(new MockWebContentServer.Content(
                URI.create("https://www.test.com/.well-known/assetlinks.json"),
                HttpURLConnection.HTTP_OK,
                "application/json",
                ANDROID_APP_STATEMENT_LIST_CERTS_2_3));

        final PackageManager pm = mockPackageManagerFactory(
                "com.test.sample", new byte[][] { CERT_2, CERT_3 }, true);

        final AndroidAppPackageVerifierHarness verifier =
                new AndroidAppPackageVerifierHarness(pm, mockWebContent);
        boolean verified = verifier.verify("com.test.sample", URI.create("https://www.test.com"));
        assertTrue(verified);
    }

    @Test
    public void testAppPackageVerificationMultipleSignersMissingSigner()
            throws AndroidAppPackageVerifier.CouldNotVerifyPackageException {
        ArrayList<MockWebContentServer.Content> mockWebContent = new ArrayList<>();
        mockWebContent.add(new MockWebContentServer.Content(
                URI.create("https://www.test.com/.well-known/assetlinks.json"),
                HttpURLConnection.HTTP_OK,
                "application/json",
                ANDROID_APP_STATEMENT_LIST_CERTS_2_3));

        final PackageManager pm = mockPackageManagerFactory(
                "com.test.sample", new byte[][] { CERT_1, CERT_2 }, true);

        final AndroidAppPackageVerifierHarness verifier =
                new AndroidAppPackageVerifierHarness(pm, mockWebContent);
        boolean verified = verifier.verify("com.test.sample", URI.create("https://www.test.com"));
        assertFalse(verified);
    }

    @Test
    public void testAppPackageVerificationNoMatchingPackageInPackageManager() {
        ArrayList<MockWebContentServer.Content> mockWebContent = new ArrayList<>();
        mockWebContent.add(new MockWebContentServer.Content(
                URI.create("https://www.test.com/.well-known/assetlinks.json"),
                HttpURLConnection.HTTP_OK,
                "application/json",
                ANDROID_APP_STATEMENT_LIST_CERTS_2_3));

        final PackageManager pm = mock(PackageManager.class);
        try {
            when(pm.getPackageInfo(eq("com.test.sample"), anyInt()))
                    .thenThrow(new PackageManager.NameNotFoundException());
        } catch (PackageManager.NameNotFoundException ignored) {}

        final AndroidAppPackageVerifierHarness verifier =
                new AndroidAppPackageVerifierHarness(pm, mockWebContent);
        assertThrows(AndroidAppPackageVerifier.CouldNotVerifyPackageException.class,
                () ->verifier.verify("com.test.sample", URI.create("https://www.test.com")));
    }

    private static PackageManager mockPackageManagerFactory(@NonNull String packageName,
                                                            @NonNull byte[][] certificates,
                                                            boolean multipleSigners) {
        if (certificates.length == 0) {
            throw new IllegalArgumentException("at least 1 certificate required");
        } else if (multipleSigners && certificates.length == 1) {
            throw new IllegalArgumentException("multipleSigners requires at least 2 certificates");
        }

        final PackageInfo pi = new PackageInfo();
        final int piFlags;
        final Signature[] certs = new Signature[certificates.length];
        for (int i = 0; i < certificates.length; i++) {
            certs[i] = new Signature(certificates[i]);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            final SigningInfo si = mock(SigningInfo.class);
            when(si.hasMultipleSigners()).thenReturn(multipleSigners);
            when(si.hasPastSigningCertificates()).thenReturn(!multipleSigners && certs.length >= 2);
            when(si.getSigningCertificateHistory()).thenReturn(multipleSigners ? null : certs);
            when(si.getApkContentsSigners())
                    .thenReturn(multipleSigners ? certs : new Signature[]{certs[0]});

            piFlags = PackageManager.GET_SIGNING_CERTIFICATES;
            pi.signingInfo = si;
            //noinspection deprecation
            pi.signatures = null;
        } else {
            piFlags = PackageManager.GET_SIGNATURES;
            pi.signatures = (multipleSigners ? certs : new Signature[] { certs[0] });
        }

        final PackageManager pm = mock(PackageManager.class);
        try {
            when(pm.getPackageInfo(eq(packageName), eq(piFlags))).thenReturn(pi);
        } catch (PackageManager.NameNotFoundException ignored) {}

        return pm;
    }

    private static class AndroidAppPackageVerifierHarness extends AndroidAppPackageVerifier {
        @NonNull
        private final MockWebContentServer server;

        public AndroidAppPackageVerifierHarness(
                @NonNull PackageManager pm,
                @NonNull List<MockWebContentServer.Content> mockWebContent) {
            super(pm);
            server = new MockWebContentServer(mockWebContent);
        }

        @NonNull
        @Override
        protected String loadDocument(@NonNull URL documentURL) throws IOException {
            try {
                return super.loadDocument(server.serve(documentURL.toURI()));
            } catch (URISyntaxException e) {
                throw new RuntimeException("Test harness error converting URL to URI", e);
            }
        }
    }
}
