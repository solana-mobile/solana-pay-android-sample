/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.digitalassetlinks;

import androidx.annotation.NonNull;

import java.net.URI;

/**
 * The defined grammar for <a href="http://digitalassetlinks.org/">Digital Asset Links</a>
 */
public final class AssetLinksGrammar {
    public static final int MAX_ASSET_LINKS_URIS = 11; // 1 source URI + max 10 include statements

    public static final String GRAMMAR_INCLUDE = "include";

    public static final String GRAMMAR_RELATION = "relation";
    public static final String GRAMMAR_TARGET = "target";
    public static final String GRAMMAR_NAMESPACE = "namespace";

    public static final String GRAMMAR_DELEGATE_PERMISSION_PREFIX = "delegate_permission";
    public static final String GRAMMAR_RELATION_HANDLE_ALL_URLS = GRAMMAR_DELEGATE_PERMISSION_PREFIX + "/common.handle_all_urls";
    public static final String GRAMMER_RELATION_GET_LOGIN_CREDS = GRAMMAR_DELEGATE_PERMISSION_PREFIX + "/common.get_login_creds";

    public static final String GRAMMAR_NAMESPACE_WEB = "web";
    public static final String GRAMMAR_WEB_SITE = "site";

    public static final String GRAMMAR_NAMESPACE_ANDROID_APP = "android_app";
    public static final String GRAMMAR_ANDROID_APP_PACKAGE_NAME = "package_name";
    public static final String GRAMMAR_ANDROID_APP_SHA256_CERT_FINGERPRINTS = "sha256_cert_fingerprints";

    public static final String RELATION_PATTERN = "^[a-z0-9_.]+/[a-z0-9_.]+$";
    public static final String PACKAGE_NAME_PATTERN = "^(?:[a-zA-Z0-9_]+\\.)+[a-zA-Z0-9_]+$";
    public static final String SHA256_CERT_FINGERPRINT_PATTERN = "^(?:[0-9A-F]{2}:){31}[0-9A-F]{2}$";

    /** Tests if the specified URI is valid for {@link #GRAMMAR_WEB_SITE} */
    public static boolean isValidSiteURI(@NonNull URI uri) {
        return (uri.isAbsolute() &&
                ("http".equalsIgnoreCase(uri.getScheme()) ||
                        "https".equalsIgnoreCase(uri.getScheme())) &&
                (uri.getRawUserInfo() == null || uri.getRawUserInfo().isEmpty()) &&
                (uri.getRawPath() == null || uri.getRawPath().isEmpty()) &&
                (uri.getRawQuery() == null || uri.getRawQuery().isEmpty()) &&
                (uri.getRawFragment() == null || uri.getRawFragment().isEmpty()) &&
                (uri.getPort() == -1 || (uri.getPort() >= 1 && uri.getPort() <= 65535)));
    }

    private AssetLinksGrammar() {}
}
