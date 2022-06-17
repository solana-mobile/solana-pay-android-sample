/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.digitalassetlinks;

import static org.junit.Assert.*;

import android.content.pm.PackageManager;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.net.URI;

/** NOTE: these tests are not hermetic - they use the state of the emulator, plus the network */
@RunWith(AndroidJUnit4.class)
public class DigitalAssetLinksIntegrationTests {
    @Test
    public void testGmsPackageIsVerifiedByWwwAndroidCom()
            throws AndroidAppPackageVerifier.CouldNotVerifyPackageException {
        final PackageManager pm = ApplicationProvider.getApplicationContext().getPackageManager();
        assertNotNull(pm);

        final AndroidAppPackageVerifier verifier = new AndroidAppPackageVerifier(pm);
        final boolean verified = verifier.verify(
                "com.google.android.gms", URI.create("https://www.android.com"));
        assertTrue(verified);
    }

    @Test
    public void testGmsPackageIsNotVerifiedByDeveloperAndroidCom()
            throws AndroidAppPackageVerifier.CouldNotVerifyPackageException {
        final PackageManager pm = ApplicationProvider.getApplicationContext().getPackageManager();
        assertNotNull(pm);

        final AndroidAppPackageVerifier verifier = new AndroidAppPackageVerifier(pm);
        final boolean verified = verifier.verify(
                "com.google.android.gms", URI.create("https://developer.android.com"));
        assertFalse(verified);
    }

    @Test
    public void testGmsPackageIsNotVerifiedByWwwSolanaCom() {
        final PackageManager pm = ApplicationProvider.getApplicationContext().getPackageManager();
        assertNotNull(pm);

        final AndroidAppPackageVerifier verifier = new AndroidAppPackageVerifier(pm);
        assertThrows(AndroidAppPackageVerifier.CouldNotVerifyPackageException.class,
                () -> verifier.verify(
                        "com.google.android.gms", URI.create("https://www.solana.com")));
        // NOTE: verifier throws, rather than returns false, because www.solana.com does not host
        // any Digital Asset Links content.
    }

    @Test
    public void testGmsPackageIsNotVerifiedByHttpWwwAndroidCom() {
        final PackageManager pm = ApplicationProvider.getApplicationContext().getPackageManager();
        assertNotNull(pm);

        final AndroidAppPackageVerifier verifier = new AndroidAppPackageVerifier(pm);
        assertThrows(AndroidAppPackageVerifier.CouldNotVerifyPackageException.class,
                () -> verifier.verify(
                        "com.google.android.gms", URI.create("http://www.android.com")));
    }

    @Test
    public void testCamera2PackageIsNotVerifiedByWwwAndroidCom() throws AndroidAppPackageVerifier.CouldNotVerifyPackageException {
        final PackageManager pm = ApplicationProvider.getApplicationContext().getPackageManager();
        assertNotNull(pm);

        final AndroidAppPackageVerifier verifier = new AndroidAppPackageVerifier(pm);
        final boolean verified = verifier.verify(
                "com.android.camera2", URI.create("https://www.android.com"));
        assertFalse(verified);
    }
}
