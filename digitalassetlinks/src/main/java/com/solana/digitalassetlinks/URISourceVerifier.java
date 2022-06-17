/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.digitalassetlinks;

import androidx.annotation.NonNull;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

public abstract class URISourceVerifier {
    private static final int HTTP_TIMEOUT_MS = 1000; // 1.0s
    private static final int MAX_DOCUMENT_LENGTH = 50 * 1024; // 100KB (2 bytes per char)

    private final AtomicBoolean mVerificationInProgress = new AtomicBoolean(false);
    private final AtomicReference<HttpURLConnection> mHttpURLConnection = new AtomicReference<>(null);

    /**
     * Given an HTTP or HTTPS absolute {@link URI}, constructs the corresponding well-known Asset
     * Links {@link URI}. For e.g., https://www.solana.com/example will return
     * https://www.solana.com/.well-known/assetlinks.json.
     * @param baseURI the base HTTP or HTTPS {@link URI}
     * @return the corresponding well-known Asset Links {@link URI}
     * @throws IllegalArgumentException if baseURI is not an hierarchical HTTP or HTTPS URI
     */
    @NonNull
    public static URI getWellKnownAssetLinksURI(@NonNull URI baseURI) {
        if (!baseURI.isAbsolute() || baseURI.isOpaque()) {
            throw new IllegalArgumentException("Source URI must be absolute and hierarchical");
        }

        final String scheme = baseURI.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Source URI must be HTTP or HTTPS");
        }

        try {
            return new URI(baseURI.getScheme(), baseURI.getAuthority(), "/.well-known/assetlinks.json", null, null);
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("Unable to construct well-known URI", e);
        }
    }

    /**
     * Load the Asset Links tree from sourceURI, verify its contents, and run the provided
     * {@link StatementMatcher}s with accompanying
     * {@link AssetLinksJSONParser.StatementMatcherCallback}s against it. This function does not
     * establish any relations between the source URI and any target; sub-classes should employ
     * suitable {@link StatementMatcher}s to establish those relationships.
     * @param sourceURI the {@link URI} from which to begin Asset Links parsing
     * @param matchers the set of {@link StatementMatcherWithCallback}s to match against the Asset
     *      Links tree
     * @return true if verification completed without any warnings, or false if non-fatal warnings
     *      occurred during parsing. This value does not imply that verification failed; just
     *      whether there were syntax or parsing errors that do not impact the validity of the
     *      verification result. It is primarily of interest for automated testing.
     * @throws IllegalArgumentException if sourceURI is not a HTTP or HTTPS hierarchical URI
     * @throws CouldNotVerifyException if verification could not be performed for any reason (such
     *      as malformed Asset Links files, network unavailability, too many includes, etc).
     */
    protected boolean verify(@NonNull URI sourceURI, StatementMatcherWithCallback... matchers)
            throws CouldNotVerifyException {
        if (!mVerificationInProgress.compareAndSet(false, true)) {
            throw new IllegalStateException("verification already in progress");
        }

        // Create and configure an AssetLinksJSONParser object
        final AssetLinksJSONParser parser = new AssetLinksJSONParser();
        for (StatementMatcherWithCallback matcher : matchers) {
            parser.addStatementMatcher(matcher.statementMatcher, matcher.callback);
        }

        // Derive the initial Asset Links JSON URI
        final URI assetLinksURI = getWellKnownAssetLinksURI(sourceURI);

        // Start the AssetLinksJSONParser, and respond to document requests
        URI loadDocumentURI = parser.start(assetLinksURI);
        try {
            while (loadDocumentURI != null) {
                throwIfCancelled();

                final String document;
                try {
                    document = loadDocument(loadDocumentURI.toURL());
                } catch (IOException | UnsupportedOperationException e) {
                    throw new CouldNotVerifyException("Failed loading an Asset Links document " + loadDocumentURI, e);
                }

                throwIfCancelled();

                loadDocumentURI = parser.onDocumentLoaded(loadDocumentURI, document);
            }
        } catch (AssetLinksJSONParser.IllFormattedStatementException |
                AssetLinksJSONParser.TooManyIncludesException |
                AssetLinksJSONParser.MatcherException e) {
            throw new CouldNotVerifyException("Asset Links statement list processing failed", e);
        }

        mVerificationInProgress.set(false);
        return !parser.isWarning();
    }

    /**
     * Attempt to cancel a running {@link #verify(URI, StatementMatcherWithCallback...)} for this
     * {@link URISourceVerifier}. This will return immediately, however the verification may
     * continue for a short amount of time (until the next cancellation point).
     */
    public void cancel() {
        mVerificationInProgress.set(false);
        final HttpURLConnection URIConnection = mHttpURLConnection.get();
        if (URIConnection != null) {
            URIConnection.disconnect();
        }
    }

    private void throwIfCancelled() throws CouldNotVerifyException {
        if (!mVerificationInProgress.get()) {
            throw new CouldNotVerifyException("Asset Links verification cancelled");
        }
    }

    // N.B. protected only so compatibility test suite harnesses can override this to provide mock
    // URL objects
    @NonNull
    protected String loadDocument(@NonNull URL documentURL) throws IOException {
        final HttpURLConnection URIConnection = (HttpURLConnection)documentURL.openConnection();
        mHttpURLConnection.set(URIConnection);

        try {
            URIConnection.setConnectTimeout(HTTP_TIMEOUT_MS);
            URIConnection.setReadTimeout(HTTP_TIMEOUT_MS);
            URIConnection.setInstanceFollowRedirects(false);
            URIConnection.connect();
            final int responseCode = URIConnection.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK) {
                throw new UnsupportedOperationException("Asset Links document fetch failed; actual=" + responseCode + ", expected=" + HttpURLConnection.HTTP_OK);
            }
            final String mimeType = URIConnection.getContentType();
            if (!"application/json".equals(mimeType)) {
                throw new UnsupportedOperationException("Asset Links document type incorrect; actual=" + mimeType + ", expected=application/json");
            }
            StringBuilder documentBuilder = new StringBuilder();
            final InputStream rawInputStream = URIConnection.getInputStream();
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
            URIConnection.disconnect();
            mHttpURLConnection.set(null);
        }
    }

    /**
     * A data class wrapper to bind a {@link StatementMatcher} and a
     * {@link AssetLinksJSONParser.StatementMatcherCallback} together, for matching against an
     * Asset Links tree.
     */
    protected static final class StatementMatcherWithCallback {
        @NonNull
        public final StatementMatcher statementMatcher;

        @NonNull
        public final AssetLinksJSONParser.StatementMatcherCallback callback;

        public StatementMatcherWithCallback(@NonNull StatementMatcher statementMatcher,
                                            @NonNull AssetLinksJSONParser.StatementMatcherCallback callback) {
            this.statementMatcher = statementMatcher;
            this.callback = callback;
        }
    }

    /** Indicates that the Asset Links tree could not be verified for any reason */
    public static class CouldNotVerifyException extends Exception {
        public CouldNotVerifyException(String message) { super(message); }
        public CouldNotVerifyException(String message, Throwable t) { super(message, t); }
    }
}
