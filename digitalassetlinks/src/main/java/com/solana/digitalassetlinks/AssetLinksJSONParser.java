/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.digitalassetlinks;

import android.net.Uri;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A <a href="http://digitalassetlinks.org/">Digital Asset Links</a> JSON parser
 */
public class AssetLinksJSONParser {
    private final ArrayList<Uri> mUris = new ArrayList<>(1);
    private int mLoadingIndex = -1;

    private final ArrayList<Pair<StatementMatcher, StatementMatcherCallback>> mStatementMatchers = new ArrayList<>();

    private boolean mIsSourceUriSecure;

    private boolean mIsError;

    /**
     * Construct a new {@link AssetLinksJSONParser}
     */
    public AssetLinksJSONParser() {}

    /**
     * Retrieve all URIs referenecd by this Asset Links tree. If {@link #isComplete()}, this set
     * represents the full Asset Links tree derived from the first entry in the returned list.
     * @return a {@link List} of {@link Uri}s in the order discovered. The first entry is the source
     *      {@link Uri} for this Asset Links tree.
     */
    @NonNull
    public List<Uri> getUris() {
        return Collections.unmodifiableList(mUris);
    }

    /**
     * Whether parsing has completed successfully (including the full tree of include directives).
     * If {@link #isError()} is true, this will always return false.
     * @return true if parsing completed successfully, else false
     */
    public boolean isComplete() {
        return !mIsError && (mLoadingIndex == mUris.size());
    }

    /**
     * Whether an error was encountered during parsing. If this is true, no more parsing can occur,
     * and any {@link StatementMatcherCallback} results should be ignored.
     * @return true if an error was encountered during parsing, else false
     */
    public boolean isError() {
        return mIsError;
    }

    /**
     * Add a {@link StatementMatcher} and corresponding {@link StatementMatcherCallback} to this
     * {@link AssetLinksJSONParser}. All statement matchers should be added before
     * {@link #start(Uri)} is called.
     * @param statementMatcher the {@link StatementMatcher} to add
     * @param callback the {@link StatementMatcherCallback} to invoke when a match occurs
     */
    public void addStatementMatcher(@NonNull StatementMatcher statementMatcher,
                                    @NonNull StatementMatcherCallback callback) {
        mStatementMatchers.add(Pair.create(statementMatcher, callback));
    }

    /**
     * Start this {@link AssetLinksJSONParser} for the specified Asset Links {@link Uri}
     * @param sourceUri the initial Asset Links {@link Uri}. It must be an absolute {@link Uri}.
     * @return the first {@link Uri} to fetch for {@link #onDocumentLoaded(Uri, String)}
     */
    @NonNull
    public Uri start(@NonNull Uri sourceUri) {
        if (!sourceUri.isAbsolute()) {
            throw new IllegalArgumentException("Source URI must be absolute");
        }

        if (mLoadingIndex != -1) {
            throw new IllegalStateException("Already started");
        }

        mLoadingIndex = 0;
        mUris.add(sourceUri);
        mIsSourceUriSecure = !"http".equalsIgnoreCase(sourceUri.getScheme());
        return sourceUri;
    }

    /**
     * This method should be invoked with the document contents for the {@link Uri}s returned from
     * {@link #start(Uri)} and {@link #onDocumentLoaded(Uri, String)}. It will continue to return
     * {@link Uri}s until the entire Asset Links include tree has been parsed, or until a parsing
     * error occurs.
     * @param documentUri the {@link Uri} for document
     * @param document a {@link String} containing the contents of document, interpreted as a UTF-8
     *      string
     * @return the next {@link Uri} to fetch, or null if parsing is complete
     * @throws IllFormattedStatementException if parsing fails due to a semantically invalid
     *      document
     * @throws TooManyIncludesException if parsing fails due to exceeding the limit on the size of
     *      the Asset Links include tree
     */
    @Nullable
    public Uri onDocumentLoaded(@NonNull Uri documentUri, @NonNull String document)
            throws IllFormattedStatementException, TooManyIncludesException {
        if (mIsError) {
            throw new IllegalStateException("AssetLinksJSONParser already in the error state");
        }

        try {
            final Uri expectedUri = mUris.get(mLoadingIndex);
            if (!documentUri.equals(expectedUri)) {
                throw new IllegalArgumentException("Document URI does not match expected value: expected=" + expectedUri + ", actual=" + documentUri);
            }
            parseAssetLinksDocument(documentUri, document);
            mLoadingIndex++;
            return (isComplete() ? null : mUris.get(mLoadingIndex));
        } catch (Exception e) {
            mIsError = true;
            throw e;
        }
    }

    private void parseAssetLinksDocument(@NonNull Uri documentUri, @NonNull String document)
            throws IllFormattedStatementException, TooManyIncludesException {
        try {
            final JSONArray statements = new JSONArray(document); // mandatory top-level array
            final int numStatements = statements.length();
            for (int i = 0; i < numStatements; i++) {
                final JSONObject statement = statements.getJSONObject(i); // mandatory object

                if (statement.has(AssetLinksGrammar.GRAMMAR_INCLUDE)) {
                    parseIncludeStatement(documentUri, statement);
                } else if (statement.has(AssetLinksGrammar.GRAMMAR_RELATION)) {
                    parseAssetLinkStatement(statement);
                } else {
                    throw new IllFormattedStatementException("statement is neither an include nor a relation statement");
                }
            }
        } catch (JSONException e) {
            throw new IllFormattedStatementException("Error while parsing Asset Links JSON", e);
        }
    }

    private void parseIncludeStatement(@NonNull Uri documentUri, @NonNull JSONObject statement)
            throws JSONException, IllFormattedStatementException, TooManyIncludesException {
        final Uri includeUri = Uri.parse(statement.getString(AssetLinksGrammar.GRAMMAR_INCLUDE));
        if (statement.length() > 1) {
            throw new IllFormattedStatementException("Include statement should not contain any other keys");
        }

        final Uri resolvedUri;
        if (includeUri.isRelative()) {
            if (!documentUri.isHierarchical()) {
                throw new IllFormattedStatementException("Include statement is relative when document URI is non-hierarchical; document=" + documentUri);
            }
            // Switch to java.net.URI for it's relative path resolution methods
            try {
                final URI baseURI = new URI(documentUri.toString());
                final URI resolvedIncludeURI = baseURI.resolve(includeUri.toString());
                resolvedUri = Uri.parse(resolvedIncludeURI.toString());
            } catch (URISyntaxException e) {
                throw new IllFormattedStatementException("Include statement relative URI was not resolvable; base=" + documentUri + ", relative=" + includeUri);
            }
        } else {
            resolvedUri = includeUri;
        }

        if (mIsSourceUriSecure && !"https".equalsIgnoreCase(resolvedUri.getScheme())) {
            throw new IllFormattedStatementException("Include statement must reference a secure location when Asset Links source URI is secure; uri=" + resolvedUri);
        }

        mUris.add(resolvedUri);

        if (mUris.size() > AssetLinksGrammar.MAX_ASSET_LINKS_URIS) {
            throw new TooManyIncludesException();
        }
    }

    private void parseAssetLinkStatement(@NonNull JSONObject statement)
            throws JSONException, IllFormattedStatementException {
        final JSONArray relations = statement.getJSONArray(AssetLinksGrammar.GRAMMAR_RELATION);
        if (relations.length() == 0) {
            throw new IllFormattedStatementException("relation array must contain at least one relation");
        }
        final JSONObject target = statement.getJSONObject(AssetLinksGrammar.GRAMMAR_TARGET);
        if (!target.has(AssetLinksGrammar.GRAMMAR_NAMESPACE)) {
            throw new IllFormattedStatementException("target must contain namespace");
        }
        if (statement.length() > 2) {
            throw new IllFormattedStatementException("Relation statement should not contain any other keys");
        }

        // Statement contents verified; now run the registered StatementMatchers against it

        for (Pair<StatementMatcher, StatementMatcherCallback> pair : mStatementMatchers) {
            if (pair.first.compare(statement)) {
                try {
                    pair.second.onMatch(pair.first, statement);
                } catch (Exception e) {
                    throw new IllFormattedStatementException("Matcher failed, terminating parsing of statements", e);
                }
            }
        }
    }

    /**
     * Given an HTTP or HTTPS absolute {@link Uri}, constructs the corresponding well-known Asset
     * Links {@link Uri}. For e.g., https://www.solana.com/example will return
     * https://www.solana.com/.well-known/assetlinks.json.
     * @param baseUri the base HTTP or HTTPS {@link Uri}
     * @return the corresponding well-known Asset Links {@link Uri}
     */
    @NonNull
    public static Uri getWellKnownAssetLinksUri(@NonNull Uri baseUri) {
        if (!(baseUri.isAbsolute() && baseUri.isHierarchical())) {
            throw new IllegalArgumentException("Source URI must be absolute and hierarchical");
        }

        final String scheme = baseUri.getScheme();
        if (!("http".equalsIgnoreCase(scheme) || "https".equalsIgnoreCase(scheme))) {
            throw new IllegalArgumentException("Source URI must be HTTP or HTTPS");
        }

        return new Uri.Builder()
                .scheme(scheme)
                .encodedAuthority(baseUri.getEncodedAuthority())
                .appendEncodedPath(".well-known/assetlinks.json")
                .build();
    }

    /** A callback to be invoked when a {@link StatementMatcher} match occurs */
    public interface StatementMatcherCallback {
        void onMatch(@NonNull StatementMatcher matcher, @NonNull JSONObject o) throws JSONException;
    }

    /** The base type for all {@link AssetLinksJSONParser} exceptions */
    public static abstract class AssetLinksJSONParserException extends Exception {
        protected AssetLinksJSONParserException() {}
        protected AssetLinksJSONParserException(String message) { super(message); }
        protected AssetLinksJSONParserException(String message, Throwable t) { super(message, t); }
    }

    /** Thrown when the Asset Links JSON is not well-formed */
    public static class IllFormattedStatementException extends AssetLinksJSONParserException {
        public IllFormattedStatementException(String message) { super(message); }
        public IllFormattedStatementException(String message, Throwable t) { super(message, t); }
    }

    /** Thrown when the number of includes in the Asset Links tree exceeds the allowed limit */
    public static class TooManyIncludesException extends AssetLinksJSONParserException {}
}
