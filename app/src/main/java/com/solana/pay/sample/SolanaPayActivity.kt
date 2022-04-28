/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.pay.sample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.solana.digitalassetlinks.AndroidAppPackageVerifier
import com.solana.pay.SolanaPayAndroidContract
import com.solana.pay.SolanaPayTransactionRequest
import com.solana.pay.SolanaPayURI
import com.solana.pay.sample.databinding.ActivitySolanaPayBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.random.Random

class SolanaPayActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivitySolanaPayBinding

    private lateinit var entrypoint: Entrypoint
    private lateinit var sourceVerificationStatus: SourceVerification
    private lateinit var solanaPayUri: SolanaPayURI

    private var verifier: AndroidAppPackageVerifier? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = ActivitySolanaPayBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        setResult(Activity.RESULT_CANCELED)

        val uri = intent.data
        if (uri != null) {
            try {
                solanaPayUri = SolanaPayURI.parse(uri)
            } catch (e: IllegalArgumentException) {
                Log.e(TAG, "Invalid Solana Pay URI provided", e)
                setResult(SolanaPayAndroidContract.RESULT_FAILED)
                finish()
                return
            }
        } else {
            Log.e(TAG, "No Solana Pay URI provided")
            finish()
            return
        }
        Log.d(TAG, "Received Solana Pay URI=$solanaPayUri")

        deriveEntrypoint()
        doSourceVerification()

        viewBinding.apply {
            btnSimulateAuthorizeSubmit.setOnClickListener {
                Log.d(TAG, "Simulating authorization and successful submission of transaction")
                val result = Intent().putExtra(SolanaPayAndroidContract.EXTRA_TXID, createFakeTransactionId())
                setResult(Activity.RESULT_OK, result)
                finish()
            }
            btnSimulateAuthorizeButSubmitError.setOnClickListener {
                Log.d(TAG, "Simulating authorization and unsuccessful submission of transaction")
                val result = Intent().putExtra(SolanaPayAndroidContract.EXTRA_TXID, createFakeTransactionId())
                setResult(SolanaPayAndroidContract.RESULT_FAILED, result)
                finish()
            }
            btnSimulateNotAuthorized.setOnClickListener {
                Log.d(TAG, "Simulating user declined to authorize transaction")
                setResult(SolanaPayAndroidContract.RESULT_DECLINED)
                finish()
            }
            btnSimulateWalletRejectsTransaction.setOnClickListener {
                Log.d(TAG, "Simulating wallet failed to verify transaction validity")
                setResult(SolanaPayAndroidContract.RESULT_NOT_VERIFIED)
                finish()
            }
        }

        updateUI()
    }

    override fun onDestroy() {
        super.onDestroy()

        verifier?.let {
            it.cancel()
            verifier = null
        }
    }

    private fun deriveEntrypoint() {
        entrypoint = when (componentName!!.shortClassName) {
            ".SolanaPayActivityViaNFC" -> Entrypoint.NFC
            ".SolanaPayActivityViaInternal" -> Entrypoint.INTERNAL
            else -> Entrypoint.URI
        }
    }

    private fun doSourceVerification() {
        sourceVerificationStatus = when (entrypoint) {
            Entrypoint.URI -> {
                callingPackage?.let {
                    // callingPackage is present; we can perform additional source verification
                    if (callingPackage == packageName) {
                        // If callingPackage is ourself, source is implicitly verified
                        SourceVerification.VERIFIED
                    } else {
                        if (solanaPayUri is SolanaPayTransactionRequest) {
                            // Attempt to verify Transaction Requests using Digital Asset Links
                            doDigitalAssetLinksVerification(it)
                            SourceVerification.VERIFICATION_IN_PROGRESS
                        } else {
                            // Transfer requests have no source metadata to verify
                            SourceVerification.NOT_VERIFIABLE
                        }
                    }
                } ?: SourceVerification.NOT_VERIFIABLE // no calling package; source not verifiable
            }
            Entrypoint.NFC -> {
                // NFC is verified by a permission check to come from the system
                SourceVerification.VERIFIED
            }
            Entrypoint.INTERNAL -> {
                // Internal entrypoints are implicitly verified
                SourceVerification.VERIFIED
            }
        }
    }

    private fun doDigitalAssetLinksVerification(packageName: String) {
        lifecycleScope.launch {
            val verifier = AndroidAppPackageVerifier(packageManager)
            this@SolanaPayActivity.verifier = verifier
            val verified = try {
                // Verification call is synchronous, and uses the network; move to an IO thread
                withContext (Dispatchers.IO) {
                    val link = (solanaPayUri as SolanaPayTransactionRequest).link
                    Log.d(TAG, "Starting Digital Asset Links verification of $packageName against $link")
                    verifier.verify(packageName, link)
                }
            } catch (e: AndroidAppPackageVerifier.CouldNotVerifyPackageException) {
                Log.w(TAG, "Unable to verify package $packageName against $solanaPayUri", e)
                false
            } finally {
                this@SolanaPayActivity.verifier = null
            }

            sourceVerificationStatus = when (verified) {
                true -> {
                    Log.d(TAG, "Verification of $packageName succeeded")
                    SourceVerification.VERIFIED
                }
                else -> SourceVerification.VERIFICATION_FAILED
            }
            updateUI()
        }
    }

    private fun updateUI() {
        viewBinding.apply {
            tvEntrypointType.text = entrypoint.toString()
            tvSourceVerification.text = sourceVerificationStatus.toString()
            tvSolanaPayUri.text = solanaPayUri.uri.toString()
            val en = sourceVerificationStatus in listOf(
                SourceVerification.VERIFIED, SourceVerification.NOT_VERIFIABLE
            )
            btnSimulateAuthorizeSubmit.isEnabled = en
            btnSimulateAuthorizeButSubmitError.isEnabled = en
        }
    }

    private fun createFakeTransactionId(): ByteArray = Random.Default.nextBytes(64)

    private enum class Entrypoint {
        URI, NFC, INTERNAL
    }

    private enum class SourceVerification {
        VERIFICATION_IN_PROGRESS, VERIFICATION_FAILED, VERIFIED, NOT_VERIFIABLE
    }

    companion object {
        private val TAG = SolanaPayActivity::class.simpleName
    }
}