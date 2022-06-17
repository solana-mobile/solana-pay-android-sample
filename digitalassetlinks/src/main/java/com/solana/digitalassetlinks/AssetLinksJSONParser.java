/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.digitalassetlinks;

import android.util.Log;
import android.util.Pair;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * A <a href="http://digitalassetlinks.org/">Digital Asset Links</a> JSON parser
 */
public class AssetLinksJSONParser {
    private static final String TAG = AssetLinksJSONParser.class.getSimpleName();

    private final ArrayList<URI> mURIs = new ArrayList<>(1);
    private int mLoadingIndex = -1;

    private final ArrayList<Pair<StatementMatcher, StatementMatcherCallback>> mStatementMatchers = new ArrayList<>();

    private boolean mIsSourceURISecure;

    private boolean mIsError;
    private boolean mIsWarning;

    /**
     * Construct a new {@link AssetLinksJSONParser}
     */
    public AssetLinksJSONParser() {}

    /**
     * Retrieve all URIs referenecd by this Asset Links tree. If {@link #isComplete()}, this set
     * represents the full Asset Links tree derived from the first entry in the returned list.
     * @return a {@link List} of {@link URI}s in the order discovered. The first entry is the source
     *      {@link URI} for this Asset Links tree.
     */
    @NonNull
    public List<URI> getURIs() {
        return Collections.unmodifiableList(mURIs);
    }

    /**
     * Whether parsing has completed successfully (including the full tree of include directives).
     * If {@link #isError()} is true, this will always return false.
     * @return true if parsing completed successfully, else false
     */
    public boolean isComplete() {
        return !mIsError && (mLoadingIndex == mURIs.size());
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
     * Whether any non-fatal parsing errors were encountered during parsing. If this is true, the
     * parsing can still complete successfully, and the verification results can still be trusted.
     * NOTE: this exists primarily to validate the behavior of this parser with the Asset Links
     * compatibility test suite.
     * @return true if any non-fatal parsing errors were encountered, else false
     */
    public boolean isWarning() { return mIsWarning; }

    /**
     * Add a {@link StatementMatcher} and corresponding {@link StatementMatcherCallback} to this
     * {@link AssetLinksJSONParser}. All statement matchers should be added before
     * {@link #start(URI)} is called.
     * @param statementMatcher the {@link StatementMatcher} to add
     * @param callback the {@link StatementMatcherCallback} to invoke when a match occurs
     */
    public void addStatementMatcher(@NonNull StatementMatcher statementMatcher,
                                    @NonNull StatementMatcherCallback callback) {
        mStatementMatchers.add(Pair.create(statementMatcher, callback));
    }

    /**
     * Start this {@link AssetLinksJSONParser} for the specified Asset Links {@link URI}
     * @param sourceURI the initial Asset Links {@link URI}. It must be an absolute {@link URI}.
     * @return the first {@link URI} to fetch for {@link #onDocumentLoaded(URI, String)}
     */
    @NonNull
    public URI start(@NonNull URI sourceURI) {
        if (!sourceURI.isAbsolute()) {
            throw new IllegalArgumentException("Source URI must be absolute");
        }

        if (mLoadingIndex != -1) {
            throw new IllegalStateException("Already started");
        }

        mLoadingIndex = 0;
        mURIs.add(sourceURI);
        mIsSourceURISecure = !"http".equalsIgnoreCase(sourceURI.getScheme());
        return sourceURI;
    }

    /**
     * This method should be invoked with the document contents for the {@link URI}s returned from
     * {@link #start(URI)} and {@link #onDocumentLoaded(URI, String)}. It will continue to return
     * {@link URI}s until the entire Asset Links include tree has been parsed, or until a parsing
     * error occurs.
     * @param documentURI the {@link URI} for document
     * @param document a {@link String} containing the contents of document, interpreted as a UTF-8
     *      string
     * @return the next {@link URI} to fetch, or null if parsing is complete
     * @throws IllFormattedStatementException if parsing fails due to a semantically invalid
     *      document
     * @throws TooManyIncludesException if parsing fails due to exceeding the limit on the size of
     *      the Asset Links include tree
     * @throws MatcherException if any registered {@link StatementMatcher} throws an exception
     */
    @Nullable
    public URI onDocumentLoaded(@NonNull URI documentURI, @NonNull String document)
            throws IllFormattedStatementException, TooManyIncludesException, MatcherException {
        if (mIsError) {
            throw new IllegalStateException("AssetLinksJSONParser already in the error state");
        }

        try {
            final URI expectedURI = mURIs.get(mLoadingIndex);
            if (!documentURI.equals(expectedURI)) {
                throw new IllegalArgumentException("Document URI does not match expected value: expected=" + expectedURI + ", actual=" + documentURI);
            }

            final ArrayList<JSONObject> statementList = new ArrayList<>();
            final ArrayList<URI> includeList = new ArrayList<>();

            boolean doPostParsing = true;
            try {
                parseAssetLinksDocument(documentURI, document, statementList, includeList);
            } catch (IllFormattedStatementException e) {
                if (mLoadingIndex == 0) {
                    throw e;
                }
                Log.w(TAG, "Parsing error on an included statement list; discarding and continuing processing");
                doPostParsing = false;
                mIsWarning = true;
            }

            if (doPostParsing) {
                // Process include directives
                if (includeList.removeAll(mURIs)) {
                    Log.w(TAG, "Discarded include URIs to avoid recursion");
                    mIsWarning = true;
                }
                mURIs.addAll(includeList);
                if (mURIs.size() > AssetLinksGrammar.MAX_ASSET_LINKS_URIS) {
                    throw new TooManyIncludesException();
                }

                // Dispatch statements to matchers
                for (JSONObject statement : statementList) {
                    for (Pair<StatementMatcher, StatementMatcherCallback> pair : mStatementMatchers) {
                        if (pair.first.compare(statement)) {
                            try {
                                pair.second.onMatch(pair.first, statement);
                            } catch (Exception e) {
                                throw new MatcherException("Matcher failed, terminating parsing", e);
                            }
                        }
                    }
                }
            }

            mLoadingIndex++;
            return (isComplete() ? null : mURIs.get(mLoadingIndex));
        } catch (Exception e) {
            mIsError = true;
            throw e;
        }
    }

    private void parseAssetLinksDocument(@NonNull URI documentURI,
                                         @NonNull String document,
                                         @NonNull ArrayList<JSONObject> statementList,
                                         @NonNull ArrayList<URI> includeList)
            throws IllFormattedStatementException {
        try {
            final JSONArray statements = new JSONArray(document); // mandatory top-level array
            final int numStatements = statements.length();
            for (int i = 0; i < numStatements; i++) {
                final JSONObject statement = statements.getJSONObject(i); // mandatory object

                if (statement.has(AssetLinksGrammar.GRAMMAR_INCLUDE)) {
                    parseIncludeStatement(documentURI, statement, includeList);
                } else if (statement.has(AssetLinksGrammar.GRAMMAR_RELATION)) {
                    parseAssetLinkStatement(statement, statementList);
                } else if (statement.has(AssetLinksGrammar.GRAMMAR_TARGET)) {
                    // Has target but no relation - error!
                    throw new IllFormattedStatementException("statement has target without relation");
                } else {
                    // Some other JSON object; it has none of include, relation, or target. Skip it.
                    Log.w(TAG, "Skipping an unknown JSON object in the statement list");
                    mIsWarning = true;
                }
            }
        } catch (JSONException e) {
            throw new IllFormattedStatementException("Error while parsing Asset Links JSON", e);
        }
    }

    private void parseIncludeStatement(@NonNull URI documentURI,
                                       @NonNull JSONObject statement,
                                       @NonNull ArrayList<URI> includeList)
            throws JSONException, IllFormattedStatementException {
        if (statement.has(AssetLinksGrammar.GRAMMAR_RELATION) ||
                statement.has(AssetLinksGrammar.GRAMMAR_TARGET)) {
            throw new IllFormattedStatementException("Include statement should not contain relation or target keys");
        }

        final URI includeURI;
        try {
            includeURI = URI.create(statement.getString(AssetLinksGrammar.GRAMMAR_INCLUDE));
        } catch (IllegalArgumentException e) {
            throw new IllFormattedStatementException("Include statement URI is not well-formed", e);
        }
        final URI resolvedURI = documentURI.resolve(includeURI);

        if (mIsSourceURISecure && !"https".equalsIgnoreCase(resolvedURI.getScheme())) {
            throw new IllFormattedStatementException("Include statement must reference a secure location when Asset Links source URI is secure; URI=" + resolvedURI);
        }

        includeList.add(resolvedURI);
    }

    private void parseAssetLinkStatement(@NonNull JSONObject statement,
                                         @NonNull ArrayList<JSONObject> statementList)
            throws JSONException, IllFormattedStatementException {
        final JSONArray relations = statement.getJSONArray(AssetLinksGrammar.GRAMMAR_RELATION);
        if (relations.length() == 0) {
            throw new IllFormattedStatementException("relation array must contain at least one relation");
        }
        for (int i = 0; i < relations.length(); i++) {
            final String relation = relations.getString(i);
            if (!relation.matches(AssetLinksGrammar.RELATION_PATTERN)) {
                throw new IllFormattedStatementException("relation does not match expected format");
            }
        }
        final JSONObject target = statement.getJSONObject(AssetLinksGrammar.GRAMMAR_TARGET);
        final String namespace = target.getString(AssetLinksGrammar.GRAMMAR_NAMESPACE);
        if (AssetLinksGrammar.GRAMMAR_NAMESPACE_WEB.equals(namespace)) {
            validateWebTarget(target);
        } else if (AssetLinksGrammar.GRAMMAR_NAMESPACE_ANDROID_APP.equals(namespace)) {
            validateAndroidAppTarget(target);
        }

        statementList.add(statement);
    }

    private void validateWebTarget(@NonNull JSONObject webTarget)
            throws JSONException, IllFormattedStatementException {
        final String site = webTarget.getString(AssetLinksGrammar.GRAMMAR_WEB_SITE);
        final URI uri;
        try {
            uri = URI.create(site);
        } catch (IllegalArgumentException e) {
            throw new IllFormattedStatementException("site URL format is invalid: " + site, e);
        }

        if (!AssetLinksGrammar.isValidSiteURI(uri)) {
            throw new IllFormattedStatementException("site URL format is invalid: " + site);
        }
    }

    private void validateAndroidAppTarget(@NonNull JSONObject androidAppTarget)
            throws JSONException, IllFormattedStatementException {
        final String packageName = androidAppTarget.getString("package_name");
        if (!packageName.matches(AssetLinksGrammar.PACKAGE_NAME_PATTERN)) {
            throw new IllFormattedStatementException("package_name is invalid: " + packageName);
        }

        final JSONArray certs = androidAppTarget.getJSONArray(
                AssetLinksGrammar.GRAMMAR_ANDROID_APP_SHA256_CERT_FINGERPRINTS);
        if (certs.length() == 0) {
            throw new IllFormattedStatementException("At least one certificate must be present");
        }
        for (int i = 0; i < certs.length(); i++) {
            final String cert = certs.getString(i);
            if (!cert.matches(AssetLinksGrammar.SHA256_CERT_FINGERPRINT_PATTERN)) {
                throw new IllFormattedStatementException("Ill-formatted certificate fingerprint: " + cert);
            }
        }
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

    /** Thrown when a matcher generates an Exception during processing */
    public static class MatcherException extends AssetLinksJSONParserException {
        public MatcherException(String message, Throwable t) { super(message, t); }
    }
}
