/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.pay;

import android.net.Uri;

import androidx.annotation.NonNull;

/**
 * Base class for parsed Solana Pay {@link Uri}
 */
public abstract class SolanaPayURI {
    // Match a base58-encoded Ed25519 public key-like value (32 bytes)
    // Base58-encoded min length of N bytes = N
    // Base58-encoded max length of N bytes = roundup(log(256)/log(58)*N)
    private static final String BASE58_ENCODED_ED25519_PUBLIC_KEY_LIKE_VALUE_REGEX =
            "^[123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz]{32,44}$";

    /** The unmodified Solana Pay {@link Uri} */
    @NonNull
    public final Uri uri;

    /**
     * The query parameters from {@link #uri}, encoded as a relative {@link Uri}. The Solana Pay
     * URI format is opaque, and the Android {@link Uri} class won't parse the query parameters from
     * it. This field is a hierarchical {@link Uri}, and thus can be used to easily extract query
     * parameters.
     */
    @NonNull
    public final Uri queryParametersUri;

    /**
     * Construct a new {@link SolanaPayURI}
     * @param uri the Solana Pay {@link Uri} to parse
     * @throws IllegalArgumentException if uri is not a valid Solana Pay {@link Uri}
     */
    protected SolanaPayURI(@NonNull Uri uri) {
        validate(uri);
        this.uri = uri;
        queryParametersUri = Uri.parse("?" + uri.getQuery());
    }

    private static void validate(@NonNull Uri uri) {
        if (!SolanaPayAndroidContract.URI_SCHEME.equals(uri.getScheme())) {
            throw new IllegalArgumentException(uri + " is not a valid Solana Pay URI");
        }
    }

    /**
     * Try and parse the given {@link Uri} as any of the known Solana Pay formats:
     * {@link SolanaPayTransferRequest} or {@link SolanaPayTransactionRequest}
     * @param uri the Solana Pay {@link Uri} to parse
     * @return a {@link SolanaPayURI} concrete subclass
     * @throws IllegalArgumentException if uri cannot be parsed as a Solana Pay {@link Uri}
     */
    public static SolanaPayURI parse(@NonNull Uri uri) {
        try {
            return new SolanaPayTransactionRequest(uri);
        } catch (IllegalArgumentException ignored) {}

        try {
            return new SolanaPayTransferRequest(uri);
        } catch (IllegalArgumentException ignored) {}

        throw new IllegalArgumentException("Unable to parse " + uri + " as a Solana Pay URI");
    }

    /**
     * Utility method to verify that the provided string is a valid base58-encoded Ed25519 public
     * key-like value. Note that this only checks that it is a key-like value; it doesn't verify
     * that the value represents a point on any particular elliptic curve.
     * @param s the candidate value to verify
     * @return true if s is a valid base58-encoded Ed25519 public key-like value
     */
    protected static boolean isBase58EncodedPublicKeyLikeValue(@NonNull String s) {
        return s.matches(BASE58_ENCODED_ED25519_PUBLIC_KEY_LIKE_VALUE_REGEX);
    }
}

