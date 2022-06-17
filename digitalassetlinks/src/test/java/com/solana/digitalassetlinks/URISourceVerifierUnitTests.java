/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.digitalassetlinks;

import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

import androidx.annotation.NonNull;

import org.junit.Ignore;
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
import java.util.concurrent.Semaphore;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={ RobolectricConfig.MIN_SDK, RobolectricConfig.CUR_SDK })
public class URISourceVerifierUnitTests {

    // NOTE: this class will not test the parsing correctness of this class - there's a whole
    // compatibility suite that covers that in detail. This will focus on runtime behaviors.

    @Test
    public void testGetWellKnownAssetLinksURISuccess() {
        final URI baseURI = URI.create("https://www.test.com:1234/foo/bar");
        final URI assetLinksURI = URISourceVerifier.getWellKnownAssetLinksURI(baseURI);
        assertEquals("https", assetLinksURI.getScheme());
        assertEquals("www.test.com:1234", assetLinksURI.getAuthority());
        assertEquals("/.well-known/assetlinks.json", assetLinksURI.getPath());
        assertNull(assetLinksURI.getQuery());
        assertNull(assetLinksURI.getFragment());
    }

    @Test
    public void testGetWellKnownAssetLinksURIBlankURIError() {
        final URI blankURI = URI.create("");
        assertThrows(IllegalArgumentException.class,
                () -> URISourceVerifier.getWellKnownAssetLinksURI(blankURI));
    }

    @Test
    public void testGetWellKnownAssetLinksURIUnknownProtocolURIError() {
        final URI badProtocolURI = URI.create("foo://www.test.com");
        assertThrows(IllegalArgumentException.class,
                () -> URISourceVerifier.getWellKnownAssetLinksURI(badProtocolURI));
    }

    @Test
    public void testGetWellKnownAssetLinksURIOpaqueHTTPSURIError() {
        final URI opaqueHTTPSURI = URI.create("https:www.test.com");
        assertThrows(IllegalArgumentException.class,
                () -> URISourceVerifier.getWellKnownAssetLinksURI(opaqueHTTPSURI));
    }

    @Test
    public void testGetWellKnownAssetLinksURIRelativeURIError() {
        final URI relativeURI = URI.create("www.test.com");
        assertThrows(IllegalArgumentException.class,
                () -> URISourceVerifier.getWellKnownAssetLinksURI(relativeURI));
    }

    @Test
    public void testHTTP200() throws URISourceVerifier.CouldNotVerifyException {
        final ArrayList<MockWebContentServer.Content> mockWebContents = new ArrayList<>();
        mockWebContents.add(new MockWebContentServer.Content(
                URI.create("https://www.test.com/.well-known/assetlinks.json"),
                HttpURLConnection.HTTP_OK, "application/json", "[]"));
        final URISourceVerifierMockContentHarness uriSourceVerifier =
                new URISourceVerifierMockContentHarness(mockWebContents);

        uriSourceVerifier.verify(URI.create("https://www.test.com"));
    }

    @Test
    public void testHTTP204() {
        final ArrayList<MockWebContentServer.Content> mockWebContents = new ArrayList<>();
        mockWebContents.add(new MockWebContentServer.Content(URI.create(
                "https://www.test.com/.well-known/assetlinks.json"),
                HttpURLConnection.HTTP_NO_CONTENT, "application/json", ""));
        final URISourceVerifierMockContentHarness uriSourceVerifier =
                new URISourceVerifierMockContentHarness(mockWebContents);

        assertThrows(URISourceVerifier.CouldNotVerifyException.class,
                () -> uriSourceVerifier.verify(URI.create("https://www.test.com")));
    }

    @Test
    @Ignore("Need a better mechansim that mock URLs to test HTTP redirect behavior")
    public void testHTTP302() {
        fail();
    }

    @Test
    public void testHTTP404() {
        final ArrayList<MockWebContentServer.Content> mockWebContents = new ArrayList<>();
        final URISourceVerifierMockContentHarness uriSourceVerifier =
                new URISourceVerifierMockContentHarness(mockWebContents);

        assertThrows(URISourceVerifier.CouldNotVerifyException.class,
                () -> uriSourceVerifier.verify(URI.create("https://www.test.com")));
    }

    @Test
    public void testNonJSONContentType() {
        final ArrayList<MockWebContentServer.Content> mockWebContents = new ArrayList<>();
        mockWebContents.add(new MockWebContentServer.Content(
                URI.create("https://www.test.com/.well-known/assetlinks.json"),
                HttpURLConnection.HTTP_OK, "application/text", "[]"));
        final URISourceVerifierMockContentHarness uriSourceVerifier =
                new URISourceVerifierMockContentHarness(mockWebContents);

        assertThrows(URISourceVerifier.CouldNotVerifyException.class,
                () -> uriSourceVerifier.verify(URI.create("https://www.test.com")));
    }

    @Test
    public void testNonJSONContent() {
        final ArrayList<MockWebContentServer.Content> mockWebContents = new ArrayList<>();
        mockWebContents.add(new MockWebContentServer.Content(
                URI.create("https://www.test.com/.well-known/assetlinks.json"),
                HttpURLConnection.HTTP_OK, "application/json", "BAD"));
        final URISourceVerifierMockContentHarness uriSourceVerifier =
                new URISourceVerifierMockContentHarness(mockWebContents);

        assertThrows(URISourceVerifier.CouldNotVerifyException.class,
                () -> uriSourceVerifier.verify(URI.create("https://www.test.com")));
    }

    @Test(timeout=5000)
    public void testCancellation() {
        final URISourceVerifierCancellationHarness uriSourceVerifier =
                new URISourceVerifierCancellationHarness();

        uriSourceVerifier.launchAsyncAfterConnect(() -> {
            try {
                Thread.sleep(200);
                uriSourceVerifier.cancel();
            } catch (InterruptedException ignored) {}
        });

        assertThrows(URISourceVerifier.CouldNotVerifyException.class,
                () -> uriSourceVerifier.verify(URI.create("https://www.test.com")));
    }

    private static class URISourceVerifierMockContentHarness extends URISourceVerifier {
        @NonNull
        private final MockWebContentServer server;

        public URISourceVerifierMockContentHarness(
                @NonNull List<MockWebContentServer.Content> mockWebContent) {
            server = new MockWebContentServer(mockWebContent);
        }

        public void verify(URI sourceURI) throws CouldNotVerifyException {
            super.verify(sourceURI);
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

    private static class URISourceVerifierCancellationHarness extends URISourceVerifier {
        private final Semaphore connectCalledSem = new Semaphore(0);
        private final Semaphore connectCancelledSem = new Semaphore(0);

        @NonNull
        @Override
        protected String loadDocument(@NonNull URL documentURL) throws IOException {
            // Busy-wait until disconnect is called (on another thread), then throw an IOException
            final HttpURLConnection mockConn = mock(HttpURLConnection.class);
            doAnswer(invocation -> {
                connectCalledSem.release();
                connectCancelledSem.acquire();
                throw new IOException("Connect cancelled due to disconnect");
            }).when(mockConn).connect();
            doAnswer(invocation -> {
                connectCancelledSem.release();
                return null;
            }).when(mockConn).disconnect();
            final URL mockURL = mock(URL.class);
            when(mockURL.openConnection()).thenReturn(mockConn);
            return super.loadDocument(mockURL);
        }

        public void launchAsyncAfterConnect(@NonNull Runnable r) {
            new Thread(() -> {
                try {
                    connectCalledSem.acquire();
                    r.run();
                } catch (InterruptedException ignored) {
                }
            }).start();
        }
    }
}
