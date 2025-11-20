/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.pay.sample

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.solana.pay.SolanaPayAndroidContract

class MainActivity : ComponentActivity() {

    private data class SolanaPayResult(
        val result: Int,
        val transactionSignatureBase58: String?
    )

    private val solanaPayActivityResult = registerForActivityResult(object : ActivityResultContract<Uri, SolanaPayResult>() {
        override fun createIntent(context: Context, solanaPayUri: Uri): Intent {
            val i = Intent()
            i.component = ComponentName(packageName, "com.solana.pay.sample.SolanaPayActivityViaInternal")
            i.data = solanaPayUri
            return i
        }

        override fun parseResult(resultCode: Int, intent: Intent?): SolanaPayResult {
            return SolanaPayResult(resultCode, intent?.getStringExtra(SolanaPayAndroidContract.EXTRA_SIGNATURE))
        }
    }) { result ->
        val str = "Result=${result.result}, sig=${result.transactionSignatureBase58}"
        Toast.makeText(this, str, Toast.LENGTH_SHORT).show()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MainScreen(
                        onLaunchSolanaPay = {
                            // NOTE: this is an arbitrary address - please don't perform any real transfers to it!
                            solanaPayActivityResult.launch(Uri.parse("solana:84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54?amount=100&memo=Test%20xfer"))
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun MainScreen(onLaunchSolanaPay: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(8.dp),
        contentAlignment = Alignment.TopStart
    ) {
        Button(
            onClick = onLaunchSolanaPay
        ) {
            Text(
                text = stringResource(R.string.label_internal_solana_pay_uri),
                fontSize = 22.sp
            )
        }
    }
}