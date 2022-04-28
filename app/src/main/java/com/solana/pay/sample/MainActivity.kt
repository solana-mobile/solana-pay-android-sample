/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.pay.sample

import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.solana.pay.sample.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {
    private lateinit var viewBinding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.buttonInternalUri.setOnClickListener {
            val i = Intent()
            i.component = ComponentName(packageName, "com.solana.pay.sample.SolanaPayActivityViaInternal")
            i.data = Uri.parse("solana:84npKJKZy8ixjdq8UChZULDUea2Twt8ThxjiqKd7QZ54?amount=100&memo=Test%20xfer")
            startActivity(i)
        }
    }
}