/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.pay.sample

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.lifecycleScope
import com.solana.digitalassetlinks.AndroidAppPackageVerifier
import com.solana.pay.SolanaPayAndroidContract
import com.solana.pay.SolanaPayTransactionRequest
import com.solana.pay.SolanaPayURI
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URI
import kotlin.random.Random

class SolanaPayActivity : ComponentActivity() {

    private lateinit var entrypoint: Entrypoint
    private lateinit var sourceVerificationStatus: SourceVerification
    private lateinit var solanaPayUri: SolanaPayURI

    private var verifier: AndroidAppPackageVerifier? = null

    private val uiState = mutableStateOf(SolanaPayUiState())

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

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

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    SolanaPayScreen(
                        uiState = uiState.value,
                        onSimulateAuthorizeSubmit = {
                            Log.d(TAG, "Simulating authorization and successful submission of transaction")
                            val result = Intent().putExtra(SolanaPayAndroidContract.EXTRA_SIGNATURE, createFakeTransactionSignatureBase58())
                            setResult(Activity.RESULT_OK, result)
                            finish()
                        },
                        onSimulateAuthorizeButSubmitError = {
                            Log.d(TAG, "Simulating authorization and unsuccessful submission of transaction")
                            val result = Intent().putExtra(SolanaPayAndroidContract.EXTRA_SIGNATURE, createFakeTransactionSignatureBase58())
                            setResult(SolanaPayAndroidContract.RESULT_FAILED, result)
                            finish()
                        },
                        onSimulateNotAuthorized = {
                            Log.d(TAG, "Simulating user declined to authorize transaction")
                            setResult(SolanaPayAndroidContract.RESULT_DECLINED)
                            finish()
                        },
                        onSimulateWalletRejectsTransaction = {
                            Log.d(TAG, "Simulating wallet failed to verify transaction validity")
                            setResult(SolanaPayAndroidContract.RESULT_NOT_VERIFIED)
                            finish()
                        }
                    )
                }
            }
        }
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
                val link = (solanaPayUri as SolanaPayTransactionRequest).link
                val linkURI = try {
                    URI.create(link.toString())
                } catch (e: IllegalArgumentException) {
                    Log.w(TAG, "Transaction request URI is not valid", e)
                    null
                }

                linkURI?.let {
                    // Verification call is synchronous, and uses the network; move to an IO thread
                    withContext (Dispatchers.IO) {
                        Log.d(TAG, "Starting Digital Asset Links verification of $packageName against $link")
                        verifier.verify(packageName, linkURI)
                    }
                } ?: false
            } catch (e: java.lang.IllegalArgumentException) {
                Log.w(TAG, "")
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
        uiState.value = SolanaPayUiState(
            entrypointType = entrypoint.toString(),
            sourceVerification = sourceVerificationStatus.toString(),
            solanaPayUri = solanaPayUri.uri.toString(),
            isButtonsEnabled = sourceVerificationStatus in listOf(
                SourceVerification.VERIFIED, SourceVerification.NOT_VERIFIABLE
            )
        )
    }

    private fun createFakeTransactionSignatureBase58(): String = Base58EncodeUseCase(Random.Default.nextBytes(64))

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

data class SolanaPayUiState(
    val entrypointType: String = "",
    val sourceVerification: String = "",
    val solanaPayUri: String = "",
    val isButtonsEnabled: Boolean = false
)


@Composable
fun SolanaPayScreen(
    uiState: SolanaPayUiState,
    onSimulateAuthorizeSubmit: () -> Unit,
    onSimulateAuthorizeButSubmitError: () -> Unit,
    onSimulateNotAuthorized: () -> Unit,
    onSimulateWalletRejectsTransaction: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(8.dp)
    ) {
        // Entrypoint Type Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_entrypoint_type),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = uiState.entrypointType,
                fontSize = 26.sp,
                modifier = Modifier.weight(1f)
            )
        }

        // Source Verification Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.label_source_verification),
                fontSize = 26.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = uiState.sourceVerification,
                fontSize = 26.sp,
                modifier = Modifier.weight(1f)
            )
        }

        // Solana Pay URI Section
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(R.string.label_solana_pay_uri),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(
            text = uiState.solanaPayUri,
            fontSize = 26.sp,
            modifier = Modifier.fillMaxWidth()
        )

        // Simulation Options Section
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = stringResource(R.string.label_simulation_options),
            fontSize = 26.sp,
            fontWeight = FontWeight.Bold
        )

        // Simulation Buttons
        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onSimulateAuthorizeSubmit,
            enabled = uiState.isButtonsEnabled
        ) {
            Text(
                text = stringResource(R.string.label_simulate_authorize_submit),
                fontSize = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onSimulateAuthorizeButSubmitError,
            enabled = uiState.isButtonsEnabled
        ) {
            Text(
                text = stringResource(R.string.label_simulate_authorize_but_submit_error),
                fontSize = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onSimulateNotAuthorized
        ) {
            Text(
                text = stringResource(R.string.label_simulate_not_authorized),
                fontSize = 22.sp
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        Button(
            onClick = onSimulateWalletRejectsTransaction
        ) {
            Text(
                text = stringResource(R.string.label_simulate_wallet_rejects_transaction),
                fontSize = 22.sp
            )
        }
    }
}