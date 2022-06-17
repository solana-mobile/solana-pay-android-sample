/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.pay;

import android.net.Uri;

import androidx.annotation.NonNull;

import java.util.Objects;

/**
 * A {@link SolanaPayURI} representing a Transaction Request transaction
 */

public class SolanaPayTransactionRequest extends SolanaPayURI {
    /** The transfer request link (an HTTPS {@link Uri}) */
    @NonNull
    public final Uri link;

    /**
     * Construct a new {@link SolanaPayTransactionRequest}
     * @param uri the {@link Uri} to parse as a Transaction Request
     * @throws IllegalArgumentException if uri cannot be parsed as a valid Transaction Request
     */
    public SolanaPayTransactionRequest(@NonNull Uri uri) {
        super(uri);
        link = validate();
    }

    @NonNull
    private Uri validate() {
        final String ssp = uri.getEncodedSchemeSpecificPart();

        // Link URI is everything from the start of the scheme-specific part through the first '?'
        final int querySeparatorIndex = ssp.indexOf('?');
        final Uri link;
        if (querySeparatorIndex == -1) {
            link = Uri.parse(Uri.decode(ssp));
        } else {
            link = Uri.parse(Uri.decode(ssp.substring(0, querySeparatorIndex)));
        }
        final String linkScheme = link.getScheme();
        if (!"https".equals(linkScheme)) {
            throw new IllegalArgumentException("Link scheme must be an https URL");
        }
        return link;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        SolanaPayTransactionRequest that = (SolanaPayTransactionRequest) o;
        return uri.equals(that.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(uri);
    }

    @NonNull
    @Override
    public String toString() {
        return "SolanaPayTransactionRequest{" +
                "uri=" + uri +
                ", link=" + link +
                '}';
    }
}