/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.pay;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.List;
import java.util.Objects;

/**
 * A {@link SolanaPayURI} representing a Transfer Request
 */
public class SolanaPayTransferRequest extends SolanaPayURI {
    // An amount should be one or more digits, followed by an optional . and one or more digits
    private static final String AMOUNT_FORMAT_REGEX = "^\\d+(?:\\.\\d+)?$";

    /**
     * The transfer request recipient address.
     * <p/>NOTE: this has been validated to be a base58-encoded public key-like value, but it has
     * not been verified to be an actual valid public key.
     */
    @NonNull
    public final String recipient;

    /**
     * Construct a new {@link SolanaPayTransferRequest}
     * @param uri the {@link Uri} to parse as a Transfer Request
     * @throws IllegalArgumentException if uri cannot be parsed as a valid Transfer Request
     */
    public SolanaPayTransferRequest(@NonNull Uri uri) {
        super(uri);
        recipient = validate();
    }

    @NonNull
    private String validate() {
        final String ssp = uri.getEncodedSchemeSpecificPart();

        // Recipient is everything from the start of the scheme-specific part through the first '?'
        final int querySeparatorIndex = ssp.indexOf('?');
        final String recipient;
        if (querySeparatorIndex == -1) {
            recipient = ssp;
        } else {
            recipient = ssp.substring(0, querySeparatorIndex);
        }

        if (!isBase58EncodedPublicKeyLikeValue(recipient)) {
            throw new IllegalArgumentException("Recipient must be a base58-encoded public key");
        }

        final List<String> amount = queryParametersUri.getQueryParameters(SolanaPayAndroidContract.QUERY_PARAMTER_AMOUNT);
        if (amount.size() > 1) {
            throw new IllegalArgumentException(SolanaPayAndroidContract.QUERY_PARAMTER_AMOUNT + " query parameter should appear at most once");
        } else if (amount.size() == 1) {
            if (!amount.get(0).matches(AMOUNT_FORMAT_REGEX)) {
                throw new IllegalArgumentException(SolanaPayAndroidContract.QUERY_PARAMTER_AMOUNT + " must be a positive integer or decimal value");
            }
        }

        final List<String> splToken = queryParametersUri.getQueryParameters(SolanaPayAndroidContract.QUERY_PARAMETER_SPL_TOKEN);
        if (splToken.size() > 1) {
            throw new IllegalArgumentException(SolanaPayAndroidContract.QUERY_PARAMETER_SPL_TOKEN + " query parameter should appear at most once");
        } else if (splToken.size() == 1) {
            if (!isBase58EncodedPublicKeyLikeValue(splToken.get(0))) {
                throw new IllegalArgumentException(SolanaPayAndroidContract.QUERY_PARAMETER_SPL_TOKEN + " query parameter must be a base58-encoded public key");
            }
        }

        final List<String> reference = queryParametersUri.getQueryParameters(SolanaPayAndroidContract.QUERY_PARAMETER_REFERENCE);
        for (String s : reference) {
            if (!isBase58EncodedPublicKeyLikeValue(s)) {
                throw new IllegalArgumentException(SolanaPayAndroidContract.QUERY_PARAMETER_REFERENCE + " query parameter must be a base58-encoded public key");
            }
        }

        final List<String> label = queryParametersUri.getQueryParameters(SolanaPayAndroidContract.QUERY_PARAMETER_LABEL);
        if (label.size() > 1) {
            throw new IllegalArgumentException(SolanaPayAndroidContract.QUERY_PARAMETER_LABEL + " query parameter should appear at most once");
        }

        final List<String> message = queryParametersUri.getQueryParameters(SolanaPayAndroidContract.QUERY_PARAMETER_MESSAGE);
        if (message.size() > 1) {
            throw new IllegalArgumentException(SolanaPayAndroidContract.QUERY_PARAMETER_MESSAGE + " query parameter should appear at most once");
        }

        final List<String> memo = queryParametersUri.getQueryParameters(SolanaPayAndroidContract.QUERY_PARAMETER_MEMO);
        if (memo.size() > 1) {
            throw new IllegalArgumentException(SolanaPayAndroidContract.QUERY_PARAMETER_MEMO + " query parameter should appear at most once");
        }

        return recipient;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SolanaPayTransferRequest that = (SolanaPayTransferRequest) o;
        return recipient.equals(that.recipient) && uri.equals(that.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(recipient, uri);
    }

    @NonNull
    @Override
    public String toString() {
        return "SolanaPayTransferRequest{" +
                "recipient='" + recipient + '\'' +
                ", uri=" + uri +
                '}';
    }
}
