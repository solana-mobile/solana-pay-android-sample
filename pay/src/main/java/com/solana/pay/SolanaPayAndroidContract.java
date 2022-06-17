/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.pay;

import android.app.Activity;

import androidx.annotation.IntDef;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

public final class SolanaPayAndroidContract {
    // =============================================================================================
    // Solana Pay URI constants
    //   See https://github.com/solana-labs/solana-pay/blob/master/SPEC.md for the most up-to-date
    //   Solana Pay specification.
    // =============================================================================================

    /** Solana Pay URI scheme */
    public static final String URI_SCHEME = "solana";

    /** (Transfer) transfer request amount */
    public static final String QUERY_PARAMTER_AMOUNT = "amount";

    /** (Transfer) base58-encoded public key of an SPL Token mint account */
    public static final String QUERY_PARAMETER_SPL_TOKEN = "spl-token";

    /** (Transfer) base58-encoded public keys to include as read-only non-signer keys */
    public static final String QUERY_PARAMETER_REFERENCE = "reference";

    /** (Transfer) a URL-encoded UTF-8 string describing the source of the request */
    public static final String QUERY_PARAMETER_LABEL = "label";

    /** (Transfer) a URL-encoded UTF-8 string describing the nature of the request */
    public static final String QUERY_PARAMETER_MESSAGE = "message";

    /** (Transfer) URL-encoded UTF-8 string to include in a SPL Memo instruction */
    public static final String QUERY_PARAMETER_MEMO = "memo";

    // =============================================================================================
    // Activity result codes
    // =============================================================================================

    // The Solana Pay transaction was processed successfully
    // The result Intent will contain {@link #EXTRA_SIGNATURE}
    // Activity.RESULT_OK = -1;

    // The Solana Pay transaction was cancelled without being attempted
    // Activity.RESULT_CANCELED = 0;

    /** The validity of the calling app or Solana Pay URI contents could not be verified */
    public static final int RESULT_NOT_VERIFIED = 501;

    /**
     * The Solana Pay transaction was not successfully processed.
     * The result Intent may contain {@link #EXTRA_SIGNATURE}, if the transaction was submitted to
     * the network, but either not confirmed or not successful.
     */
    public static final int RESULT_FAILED = 502;

    /** The user declined to authorize the Solana Pay transaction */
    public static final int RESULT_DECLINED = 503;

    /** The allowed result values of Solana Pay Activity result codes */
    @Retention(RetentionPolicy.SOURCE)
    @IntDef({Activity.RESULT_OK, Activity.RESULT_CANCELED, RESULT_NOT_VERIFIED, RESULT_FAILED, RESULT_DECLINED})
    public @interface ActivityResult {}

    // =============================================================================================
    // Activity result extras
    // =============================================================================================

    /**
     * The base58-encoded transaction signature for the submitted transaction (whether or not it was
     * confirmed or successful).
     */
    public static final String EXTRA_SIGNATURE = "com.solana.pay.SIGNATURE";

    /** Not constructable */
    private SolanaPayAndroidContract() {}
}
