/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.digitalassetlinks;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;

public class MockWebContentServer {
    public static final class Content {
        public final URI url;
        public final int responseCode;
        public final String contentType;
        public final String responseBody;

        public Content(@NonNull URI url,
                       int responseCode,
                       @Nullable String contentType,
                       @Nullable String responseBody) {
            this.url = url;
            this.responseCode = responseCode;
            this.contentType = contentType;
            this.responseBody = responseBody;
        }
    }

    private static final URL MOCK_404 = createMockURLForContent(
            new Content(URI.create(""), HttpURLConnection.HTTP_NOT_FOUND, null, null));

    private final HashMap<URI, URL> mockURLs;

    public MockWebContentServer(List<Content> contents) {
        mockURLs = new HashMap<>(contents.size());
        for (Content c : contents) {
            final URL mockURL = createMockURLForContent(c);
            mockURLs.put(c.url, mockURL);
        }
    }

    @NonNull
    private static URL createMockURLForContent(@NonNull Content content) {
        try {
            final HttpURLConnection mockConn = mock(HttpURLConnection.class);
            when(mockConn.getResponseCode()).thenReturn(content.responseCode);
            when(mockConn.getContentType()).thenReturn(content.contentType);
            if (content.responseBody != null) {
                when(mockConn.getInputStream()).thenReturn(new ByteArrayInputStream(
                        content.responseBody.getBytes(StandardCharsets.UTF_8)));
            } else {
                when(mockConn.getInputStream()).thenThrow(new IOException());
            }
            final URL mockURL = mock(URL.class);
            when(mockURL.openConnection()).thenReturn(mockConn);
            return mockURL;
        } catch (IOException e) {
            throw new RuntimeException("Test harness error when mocking web content", e);
        }
    }

    @NonNull
    public URL serve(URI source) {
        URL url = mockURLs.get(source);
        if (url == null) {
            url = MOCK_404;
        }
        return url;
    }
}
