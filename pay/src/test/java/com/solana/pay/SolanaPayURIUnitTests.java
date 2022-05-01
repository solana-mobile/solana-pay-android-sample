/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.pay;

import static org.junit.Assert.*;

import android.net.Uri;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk={ RobolectricConfig.MIN_SDK, RobolectricConfig.CUR_SDK })
public class SolanaPayURIUnitTests {
    @Test
    public void testSolanaPayURISimpleTransfer() {
        final String testUri = "solana:84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54" +
                "?amount=100" +
                "&memo=Test%20xfer";
        final SolanaPayURI solanaPayURI = SolanaPayURI.parse(Uri.parse(testUri));

        assertTrue(solanaPayURI instanceof SolanaPayTransferRequest);
        final SolanaPayTransferRequest xfer = (SolanaPayTransferRequest) solanaPayURI;
        assertEquals("84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54", xfer.recipient);
        assertEquals(testUri, xfer.uri.toString());
        assertEquals(testUri.substring(testUri.indexOf('?')), xfer.queryParametersUri.toString());
    }

    @Test
    public void testSolanaPayURIComplexTransfer() {
        final String testUri = "solana:84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54" +
                        "?amount=0.100" +
                        "&spl-token=EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" +
                        "&reference=GUdsgKBn9vQ9HyEmZHqJgVv3Ced1tSGNzfrFtMSyHSgm" +
                        "&reference=C3xh5q61LtJatywjXwJ1Gh3yyUjEDmpNpDmEcxyCunPU" +
                        "&label=%21%20%2A%20%27%20%28%20%29%20%3B%20%3A%20%40%20%26%20%3D%20%2B%20%24%20%2C%20%2F%20%3F%20%25%20%23%20%5B%20%5D" +
                        "&message=message" +
                        "&memo=Test%20xfer";
        final SolanaPayURI solanaPayURI = SolanaPayURI.parse(Uri.parse(testUri));

        assertTrue(solanaPayURI instanceof SolanaPayTransferRequest);
        final SolanaPayTransferRequest xfer = (SolanaPayTransferRequest) solanaPayURI;
        assertEquals("84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54", xfer.recipient);
        assertEquals(testUri, xfer.uri.toString());
        assertEquals(testUri.substring(testUri.indexOf('?')), xfer.queryParametersUri.toString());
    }

    @Test
    public void testSolanaPayURITransferNoAmount() {
        final String testUri = "solana:84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54";
        final SolanaPayURI solanaPayURI = SolanaPayURI.parse(Uri.parse(testUri));

        assertTrue(solanaPayURI instanceof SolanaPayTransferRequest);
        final SolanaPayTransferRequest xfer = (SolanaPayTransferRequest) solanaPayURI;
        assertEquals("84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54", xfer.recipient);
        assertEquals(testUri, xfer.uri.toString());
        assertEquals("", xfer.queryParametersUri.toString());
    }

    @Test
    public void testSolanaPayURITransferBadRecipient() {
        final String testUri = "solana:O4npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54" + // recipient starts with a non-base58 alphabet character
                "?amount=100" +
                "&memo=Test%20xfer";
        assertThrows(IllegalArgumentException.class,
                () -> SolanaPayURI.parse(Uri.parse(testUri)));
    }

    @Test
    public void testSolanaPayURITransferBadAmountNoLeadingZero() {
        final String testUri = "solana:84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54" +
                "?amount=.100" + // no leading zero
                "&memo=Test%20xfer";
        assertThrows(IllegalArgumentException.class,
                () -> SolanaPayURI.parse(Uri.parse(testUri)));
    }

    @Test
    public void testSolanaPayURITransferBadAmountNotNumeric() {
        final String testUri = "solana:84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54" +
                "?amount=1.0Z" + // invalid numeral
                "&memo=Test%20xfer";
        assertThrows(IllegalArgumentException.class,
                () -> SolanaPayURI.parse(Uri.parse(testUri)));
    }

    @Test
    public void testSolanaPayURITransferBadSPLTokenMint() {
        final String testUri = "solana:84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54" +
                "?amount=0.100" +
                "&spl-token=ABCDEF" + // too short
                "&memo=Test%20xfer";
        assertThrows(IllegalArgumentException.class,
                () -> SolanaPayURI.parse(Uri.parse(testUri)));
    }

    @Test
    public void testSolanaPayURITransferBadReference() {
        final String testUri = "solana:84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54" +
                "?amount=0.100" +
                "&reference=AGUdsgKBn9vQ9HyEmZHqJgVv3Ced1tSGNzfrFtMSyHSgm" + // too long
                "&memo=Test%20xfer";
        assertThrows(IllegalArgumentException.class,
                () -> SolanaPayURI.parse(Uri.parse(testUri)));
    }

    @Test
    public void testSolanaPayURITransferAmountIncludedTwice() {
        final String testUri = "solana:84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54" +
                "?amount=100" +
                "?amount=100" +
                "&memo=Test%20xfer";
        assertThrows(IllegalArgumentException.class,
                () -> SolanaPayURI.parse(Uri.parse(testUri)));
    }

    @Test
    public void testSolanaPayURITransferSplTokenIncludedTwice() {
        final String testUri = "solana:84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54" +
                "?amount=100" +
                "&spl-token=EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" +
                "&spl-token=EPjFWdd5AufqSSqeM2qN1xzybapC8G4wEGGkZwyTDt1v" +
                "&memo=Test%20xfer";
        assertThrows(IllegalArgumentException.class,
                () -> SolanaPayURI.parse(Uri.parse(testUri)));
    }

    @Test
    public void testSolanaPayURITransferLabelIncludedTwice() {
        final String testUri = "solana:84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54" +
                "?amount=100" +
                "&label=label" +
                "&label=label" +
                "&memo=Test%20xfer";
        assertThrows(IllegalArgumentException.class,
                () -> SolanaPayURI.parse(Uri.parse(testUri)));
    }

    @Test
    public void testSolanaPayURITransferMessageIncludedTwice() {
        final String testUri = "solana:84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54" +
                "?amount=100" +
                "&message=message" +
                "&message=message" +
                "&memo=Test%20xfer";
        assertThrows(IllegalArgumentException.class,
                () -> SolanaPayURI.parse(Uri.parse(testUri)));
    }

    @Test
    public void testSolanaPayURITransferMemoIncludedTwice() {
        final String testUri = "solana:84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54" +
                "?amount=100" +
                "&memo=Test%20xfer" +
                "&memo=Test%20xfer";
        assertThrows(IllegalArgumentException.class,
                () -> SolanaPayURI.parse(Uri.parse(testUri)));
    }

    @Test
    public void testSolanaPayURIransaction() {
        final String testUri = "solana:https%3A%2F%2Fwww.test.com";
        final SolanaPayURI solanaPayURI = SolanaPayURI.parse(Uri.parse(testUri));

        assertTrue(solanaPayURI instanceof SolanaPayTransactionRequest);
        final SolanaPayTransactionRequest txn = (SolanaPayTransactionRequest) solanaPayURI;
        assertEquals("https://www.test.com", txn.link.toString());
        assertEquals(testUri, txn.uri.toString());
    }

    @Test
    public void testSolanaPayURITransactionWithParameters() {
        final String testUri = "solana:https%3A%2F%2Fwww.test.com%3Fqty%3D6%26reason%3Dtest";
        final SolanaPayURI solanaPayURI = SolanaPayURI.parse(Uri.parse(testUri));

        assertTrue(solanaPayURI instanceof SolanaPayTransactionRequest);
        final SolanaPayTransactionRequest txn = (SolanaPayTransactionRequest) solanaPayURI;
        assertEquals("https://www.test.com?qty=6&reason=test", txn.link.toString());
        assertEquals(testUri, txn.uri.toString());
    }

    @Test
    public void testSolanaPayURITransactionWithHTTPLink() {
        final String testUri = "solana:http%3A%2F%2Fwww.test.com";
        assertThrows(IllegalArgumentException.class,
                () -> SolanaPayURI.parse(Uri.parse(testUri)));
    }

    @Test
    public void testSolanaPayURITransactionWithInvalidLink() {
        final String testUri = "solana:abc%3ATEST";
        assertThrows(IllegalArgumentException.class,
                () -> SolanaPayURI.parse(Uri.parse(testUri)));
    }
}
