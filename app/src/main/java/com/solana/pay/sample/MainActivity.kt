/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.pay.sample

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContract
import com.solana.pay.SolanaPayAndroidContract
import com.solana.pay.sample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

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

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.buttonInternalUri.setOnClickListener {
            // NOTE: this is an arbitrary address - please don't perform any real transfers to it!
            solanaPayActivityResult.launch(Uri.parse("solana:84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54?amount=100&memo=Test%20xfer"))
        }
    }
}