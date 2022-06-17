/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.digitalassetlinks;

import android.os.Build;

public final class RobolectricConfig {
    public static final int MIN_SDK = Build.VERSION_CODES.M;
    public static final int CUR_SDK = Build.VERSION_CODES.S;

    private RobolectricConfig() {}
}
