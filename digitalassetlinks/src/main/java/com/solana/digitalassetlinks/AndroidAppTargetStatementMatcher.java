/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.digitalassetlinks;

import android.util.ArrayMap;

import androidx.annotation.NonNull;

/**
 * A {@link StatementMatcher} for Android app Asset Links
 */
public class AndroidAppTargetStatementMatcher extends StatementMatcher {
    /**
     * Construct a new {@link AndroidAppTargetStatementMatcher}
     * @param packageName the Android app package name to match
     */
    public AndroidAppTargetStatementMatcher(@NonNull String packageName) {
        super(AssetLinksGrammar.GRAMMAR_RELATION_HANDLE_ALL_URLS,
                AssetLinksGrammar.GRAMMAR_NAMESPACE_ANDROID_APP,
                makePackageNameArrayMap(packageName));
    }

    @NonNull
    private static ArrayMap<String, String> makePackageNameArrayMap(@NonNull String packageName) {
        if (packageName.isEmpty()) {
            throw new IllegalArgumentException("Android app package name must not be empty");
        }
        final ArrayMap<String, String> arrayMap = new ArrayMap<>(1);
        arrayMap.put(AssetLinksGrammar.GRAMMAR_ANDROID_APP_PACKAGE_NAME, packageName);
        return arrayMap;
    }
}
