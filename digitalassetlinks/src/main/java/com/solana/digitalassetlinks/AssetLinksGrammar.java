/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.digitalassetlinks;

/**
 * The defined grammar for <a href="http://digitalassetlinks.org/">Digital Asset Links</a>
 */
public final class AssetLinksGrammar {
    public static final int MAX_ASSET_LINKS_URIS = 11; // 1 source URI + max 10 include statements

    public static final String GRAMMAR_INCLUDE = "include";

    public static final String GRAMMAR_RELATION = "relation";
    public static final String GRAMMAR_TARGET = "target";
    public static final String GRAMMAR_NAMESPACE = "namespace";

    public static final String GRAMMAR_RELATION_HANDLE_ALL_URLS = "delegate_permission/common.handle_all_urls";
    public static final String GRAMMER_RELATION_GET_LOGIN_CREDS = "delegate_permission/common.get_login_creds";

    public static final String GRAMMAR_NAMESPACE_WEB = "web";
    public static final String GRAMMAR_WEB_SITE = "site";

    public static final String GRAMMAR_NAMESPACE_ANDROID_APP = "android_app";
    public static final String GRAMMAR_ANDROID_APP_PACKAGE_NAME = "package_name";
    public static final String GRAMMAR_SHA256_CERT_FINGERPRINTS = "sha256_cert_fingerprints";

    private AssetLinksGrammar() {}
}
