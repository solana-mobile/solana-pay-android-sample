/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.digitalassetlinks;

import static org.junit.Assert.*;

import androidx.annotation.NonNull;

import com.google.digitalassetlinks.v1.MessagesProto;
import com.google.digitalassetlinks.v1.testproto.TestProto;
import com.google.protobuf.AbstractMessage;
import com.google.protobuf.Message;
import com.google.protobuf.TextFormat;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Assume;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.ParameterizedRobolectricTestRunner;
import org.robolectric.annotation.Config;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

@RunWith(ParameterizedRobolectricTestRunner.class)
@Config(sdk={ RobolectricConfig.MIN_SDK, RobolectricConfig.CUR_SDK })
public class DigitalAssetLinksCompatibilityTestSuite {
    // Change this value to false to run against the real hosted Digital Asset Links content
    private static final boolean RUN_LOCALLY = true;

    private static final File BASE_PATH = new File("external/digitalassetlinks/compatibility-tests/v1");

    private static final File[] SOURCES = new File[] {
            new File(BASE_PATH, "1000-query-parsing/1000-list-source.pb"),
            new File(BASE_PATH, "1000-query-parsing/1100-list-relation.pb"),
            new File(BASE_PATH, "1000-query-parsing/1200-check-source.pb"),
            new File(BASE_PATH, "1000-query-parsing/1300-check-relation.pb"),
            new File(BASE_PATH, "1000-query-parsing/1400-check-target.pb"),
            new File(BASE_PATH, "2000-web-statement-list-parsing/2000-general.pb"),
            new File(BASE_PATH, "2000-web-statement-list-parsing/2100-relations.pb"),
            new File(BASE_PATH, "2000-web-statement-list-parsing/2200-web-targets.pb"),
            new File(BASE_PATH, "2000-web-statement-list-parsing/2300-android-targets.pb"),
            new File(BASE_PATH, "3000-android-statement-list-parsing/3000-general.pb"),
            new File(BASE_PATH, "3000-android-statement-list-parsing/3100-relations.pb"),
            new File(BASE_PATH, "3000-android-statement-list-parsing/3200-web-targets.pb"),
            new File(BASE_PATH, "3000-android-statement-list-parsing/3300-android-targets.pb"),
            new File(BASE_PATH, "4000-query-matching/4000-list-source.pb"),
            new File(BASE_PATH, "4000-query-matching/4100-list-relation.pb"),
            new File(BASE_PATH, "4000-query-matching/4200-check-source.pb"),
            new File(BASE_PATH, "4000-query-matching/4300-check-relation.pb"),
            new File(BASE_PATH, "4000-query-matching/4400-check-target.pb"),
            new File(BASE_PATH, "5000-include-file-processing/5000-include-file-processing.pb"),
    };

    @NonNull
    @ParameterizedRobolectricTestRunner.Parameters(name="{0} -> {1}")
    public static Iterable<Object[]> data() {
        final ArrayList<Object[]> tests = new ArrayList<>();

        for (File f : SOURCES) {
            final TestProto.CompatibilityTestSuite.Builder builder =
                    TestProto.CompatibilityTestSuite.newBuilder();
            loadTextProtobuf(f, builder);
            final TestProto.CompatibilityTestSuite cts = builder.build();

            for (TestProto.TestGroup testGroup : cts.getTestGroupList()) {
                final String testGroupName = testGroup.getName();

                for (TestProto.ListTestCase list : testGroup.getListStatementsTestsList()) {
                    final String testName = list.getName();
                    tests.add(new Object[] { testGroupName, testName, testGroup, list });
                }

                for (TestProto.CheckTestCase check : testGroup.getCheckStatementsTestsList()) {
                    final String testName = check.getName();
                    tests.add(new Object[] { testGroupName, testName, testGroup, check });
                }
            }
        }
        return tests;
    }

    private static void loadTextProtobuf(@NonNull File file, @NonNull Message.Builder messageBuilder) {
        try {
            loadTextProtobuf(new FileReader(file), messageBuilder);
        } catch (FileNotFoundException e) {
            throw new IllegalArgumentException("Failed to load protobuf message from " + file, e);
        }
    }

    private static void loadTextProtobuf(@NonNull Reader reader, @NonNull Message.Builder messageBuilder) {
        final TextFormat.Parser textParser = TextFormat.getParser();
        try {
            textParser.merge(reader, messageBuilder);
        } catch (IOException e) {
            throw new IllegalArgumentException("Failed to load protobuf message", e);
        }
    }

    @NonNull
    private final String testGroupName;

    @NonNull
    private final String testName;

    @NonNull
    private final TestProto.TestGroup testGroup;

    @NonNull
    private final AbstractMessage testCase;

    public DigitalAssetLinksCompatibilityTestSuite(@NonNull String testGroupName,
                                                   @NonNull String testName,
                                                   @NonNull TestProto.TestGroup testGroup,
                                                   @NonNull AbstractMessage testCase) {
        this.testGroupName = testGroupName;
        this.testName = testName;
        this.testGroup = testGroup;
        this.testCase = testCase;
    }

    @Test
    public void compatibilitySuiteTestCase() {
        skippedTests();

        if (testCase instanceof TestProto.ListTestCase) {
            listRequestTestCase((TestProto.ListTestCase) testCase);
        } else if (testCase instanceof TestProto.CheckTestCase) {
            checkRequestTestCase((TestProto.CheckTestCase) testCase);
        } else {
            throw new IllegalArgumentException("Unknown test case type");
        }
    }

    private void skippedTests() {
        Assume.assumeTrue(
                "Ignore stub_environment_only tests when running against real hosted content",
                RUN_LOCALLY || !testGroup.getStubEnvironmentOnly());

        // N.B. list statement test in comptest2002/'Parses assetlinks.json correctly.' appears to
        // be incorrect. It looks like the same test as comptest1101/'Missing relation query`. which
        // is marked as success. Force-skip this test.
        Assume.assumeFalse("Skipping broken test",
                testGroupName.startsWith("comptest2002")
                        && testName.equals("Parses assetlinks.json correctly."));
    }

    // =============================================================================================
    // List request test case
    // =============================================================================================

    private void listRequestTestCase(@NonNull TestProto.ListTestCase listTestCase) {
        final MessagesProto.ListRequest request = listTestCase.getRequest();
        final MessagesProto.Asset source = request.getSource();
        final MessagesProto.Asset.AssetCase assetCase = source.getAssetCase();

        Assume.assumeFalse("We don't currently handle Android app sources",
                assetCase == MessagesProto.Asset.AssetCase.ANDROID_APP);

        final String site = source.getWeb().getSite();
        final TestProto.QueryOutcome outcome = listTestCase.getOutcome();
        final String relation = request.getRelation();
        final List<MessagesProto.Statement> expectedResponse = listTestCase.getResponseList();

        URI siteURI = URI.create(site);
        Assume.assumeTrue("Ignore tests with a non-empty URI path; we replace the path element from the URI with the well-known path",
                siteURI.getPath() == null || siteURI.getPath().isEmpty());
        Assume.assumeTrue("Ignore tests with a non-empty URI query; we remove the query element from the URI",
                siteURI.getQuery() == null || siteURI.getQuery().isEmpty());
        Assume.assumeTrue("Ignore tests with a non-empty URI fragment; we remove the fragment element from the URI",
                siteURI.getFragment() == null || siteURI.getFragment().isEmpty());

        final ListRequestVerifierHarness lrv = new ListRequestVerifierHarness(
                testGroup.getWebContentList());

        try {
            List<MessagesProto.Statement> response = lrv.list(siteURI, relation);

            assertSame(outcome, lrv.hasParserWarnings() ?
                    TestProto.QueryOutcome.FETCH_ERROR : TestProto.QueryOutcome.SUCCESS);
            assertStatementsListsMatch(expectedResponse, response);
        } catch (URISourceVerifier.CouldNotVerifyException e) {
            assertEquals("Actual: " + e, outcome, TestProto.QueryOutcome.FETCH_ERROR);
            if (!expectedResponse.isEmpty()) {
                throw new IllegalArgumentException("On failure, expected responses should be empty");
            }
        } catch (QueryParsingException e) {
            assertEquals("Actual: " + e, outcome, TestProto.QueryOutcome.QUERY_PARSING_ERROR);
            if (!expectedResponse.isEmpty()) {
                throw new IllegalArgumentException("On failure, expected responses should be empty");
            }
        }
    }

    private void assertStatementsListsMatch(@NonNull List<MessagesProto.Statement> ref,
                                            @NonNull List<MessagesProto.Statement> check) {
        assertEquals(ref.size(), check.size());

        final ArrayList<MessagesProto.Statement> mutCheck = new ArrayList<>(check);
        for (MessagesProto.Statement s1 : ref) {
            final String s1r = s1.getRelation();
            final MessagesProto.Asset s1t = s1.getTarget();
            final MessagesProto.Asset.AssetCase s1ac = s1t.getAssetCase();
            for (int i = 0; i < mutCheck.size(); i++) {
                final MessagesProto.Statement s2 = mutCheck.get(i);
                final String s2r = s2.getRelation();
                final MessagesProto.Asset s2t = s2.getTarget();
                final MessagesProto.Asset.AssetCase s2ac = s2t.getAssetCase();
                if (!s1r.equals(s2r) || s1ac != s2ac) continue;
                switch (s1ac) {
                    case WEB:
                        final String s1s = s1t.getWeb().getSite();
                        final String s2s = s2t.getWeb().getSite();
                        if (!s1s.equals(s2s)) continue;
                        break;
                    case ANDROID_APP:
                        final MessagesProto.AndroidAppAsset s1a = s1t.getAndroidApp();
                        final MessagesProto.AndroidAppAsset s2a = s2t.getAndroidApp();
                        final String s1p = s1a.getPackageName();
                        final String s2p = s2a.getPackageName();
                        final String s1c = s1a.getCertificate().getSha256Fingerprint();
                        final String s2c = s2a.getCertificate().getSha256Fingerprint();
                        if (!s1p.equals(s2p) || !s1c.equals(s2c)) continue;
                        break;
                    case ASSET_NOT_SET:
                    default:
                        throw new IllegalArgumentException("Asset type must be valid");
                }

                mutCheck.remove(i);
                break;
            }
        }

        assertTrue("Expected mutCheck to be empty. mutCheck=" + mutCheck + ", ref=" + ref +
                ", check=" + check, mutCheck.isEmpty());
    }

    // NOTE: this test harness contains a small amount of logic, adapting from the expectations of
    // the Digital Asset Links Compatibility Test Suite to our verifier implementation.
    private static class ListRequestVerifierHarness extends MockInjectingURISourceVerifier {
        public ListRequestVerifierHarness(
                @NonNull List<TestProto.HostedWebContent> hostedWebContents) {
            super(hostedWebContents);
        }

        public List<MessagesProto.Statement> list(@NonNull URI uri, @NonNull String relation)
                throws CouldNotVerifyException, QueryParsingException {
            if (!AssetLinksGrammar.isValidSiteURI(uri)) {
                throw new QueryParsingException("Invalid source URI");
            }

            final ArrayList<MessagesProto.Statement> statements = new ArrayList<>();
            final StatementMatcher matcher;
            try {
                matcher = StatementMatcher.newBuilder()
                        .setRelation(!relation.isEmpty() ? relation : null)
                        .build();
            } catch (IllegalArgumentException e) {
                throw new QueryParsingException("Failing verification due to invalid matcher");
            }
            final AssetLinksJSONParser.StatementMatcherCallback callback = (statement, o) -> {
                final JSONArray relations = o.getJSONArray(AssetLinksGrammar.GRAMMAR_RELATION);
                for (int i = 0; i < relations.length(); i++) {
                    final String r = relations.getString(i);
                    if (!relation.isEmpty() && !relation.equals(r)) continue;
                    final JSONObject t = o.getJSONObject(AssetLinksGrammar.GRAMMAR_TARGET);
                    final String s = t.getString(AssetLinksGrammar.GRAMMAR_WEB_SITE);
                    final String namespace = t.getString(AssetLinksGrammar.GRAMMAR_NAMESPACE);
                    if (AssetLinksGrammar.GRAMMAR_NAMESPACE_WEB.equals(namespace)) {
                        final MessagesProto.WebAsset wa = MessagesProto.WebAsset.newBuilder()
                                .setSite(s + ".").build();
                        final MessagesProto.Asset a = MessagesProto.Asset.newBuilder()
                                .setWeb(wa).build();
                        final MessagesProto.Statement st = MessagesProto.Statement.newBuilder()
                                .setRelation(r).setTarget(a).build();
                        statements.add(st);
                    } else if (AssetLinksGrammar.GRAMMAR_NAMESPACE_ANDROID_APP.equals(namespace)) {
                        final String p = t.getString(AssetLinksGrammar.GRAMMAR_ANDROID_APP_PACKAGE_NAME);
                        final JSONArray c = t.getJSONArray(AssetLinksGrammar.GRAMMAR_ANDROID_APP_SHA256_CERT_FINGERPRINTS);
                        for (int j = 0; j < c.length(); j++) {
                            final String fp = c.getString(j);
                            final MessagesProto.AndroidAppAsset.CertificateInfo ci =
                                    MessagesProto.AndroidAppAsset.CertificateInfo.newBuilder()
                                            .setSha256Fingerprint(fp).build();
                            final MessagesProto.AndroidAppAsset aaa = MessagesProto.AndroidAppAsset.newBuilder()
                                    .setPackageName(p).setCertificate(ci).build();
                            final MessagesProto.Asset a = MessagesProto.Asset.newBuilder().
                                    setAndroidApp(aaa).build();
                            final MessagesProto.Statement st = MessagesProto.Statement.newBuilder()
                                    .setRelation(r).setTarget(a).build();
                            statements.add(st);
                        }
                    } else {
                        throw new IllegalArgumentException("namespace " + namespace + " not recognized");
                    }
                }
            };
            try {
                verify(uri, new StatementMatcherWithCallback(matcher, callback));
            } catch (IllegalArgumentException e) {
                throw new QueryParsingException("Failed verification due to bad URI " + uri, e);
            }

            return statements;
        }
    }

    // =============================================================================================
    // Check request test case
    // =============================================================================================

    private void checkRequestTestCase(@NonNull TestProto.CheckTestCase checkTestCase) {
        final MessagesProto.CheckRequest request = checkTestCase.getRequest();
        final MessagesProto.Asset source = request.getSource();
        final MessagesProto.Asset.AssetCase assetCase = source.getAssetCase();
        final MessagesProto.Asset target = request.getTarget();

        Assume.assumeFalse("We don't currently handle Android app sources",
                assetCase == MessagesProto.Asset.AssetCase.ANDROID_APP);

        final String site = source.getWeb().getSite();
        final TestProto.QueryOutcome outcome = checkTestCase.getOutcome();
        final String relation = request.getRelation();
        final boolean response = checkTestCase.getResponse();

        URI siteURI = URI.create(site);

        final CheckRequestVerifierHarness crv = new CheckRequestVerifierHarness(
                testGroup.getWebContentList());
        try {
            final boolean linked = crv.check(siteURI, relation, target);
            assertSame(outcome, crv.hasParserWarnings() ?
                    TestProto.QueryOutcome.FETCH_ERROR : TestProto.QueryOutcome.SUCCESS);
            assertEquals(response, linked);
        } catch (URISourceVerifier.CouldNotVerifyException e) {
            assertEquals("Actual: " + e, outcome, TestProto.QueryOutcome.FETCH_ERROR);
            assertFalse(response);
        } catch (QueryParsingException e) {
            assertEquals("Actual: " + e, outcome, TestProto.QueryOutcome.QUERY_PARSING_ERROR);
            assertFalse(response);
        }
    }

    // NOTE: this test harness contains a small amount of logic, adapting from the expectations of
    // the Digital Asset Links Compatibility Test Suite to our verifier implementation.
    private static class CheckRequestVerifierHarness extends MockInjectingURISourceVerifier {
        public CheckRequestVerifierHarness(
                @NonNull List<TestProto.HostedWebContent> hostedWebContents) {
            super(hostedWebContents);
        }

        public boolean check(@NonNull URI uri,
                             @NonNull String relation,
                             @NonNull MessagesProto.Asset target)
                throws CouldNotVerifyException, QueryParsingException {
            if (!AssetLinksGrammar.isValidSiteURI(uri)) {
                throw new QueryParsingException("Invalid source URI");
            }

            final boolean[] linked = { false };

            final StatementMatcher matcher;

            final MessagesProto.Asset.AssetCase assetCase = target.getAssetCase();
            switch (assetCase) {
                case WEB:
                    final URI targetSite;
                    try {
                        targetSite = URI.create(target.getWeb().getSite());
                        matcher = StatementMatcher.createWebStatementMatcher(relation, targetSite);
                    } catch (IllegalArgumentException e) {
                        throw new QueryParsingException("target site is not a valid URI", e);
                    }
                    break;
                case ANDROID_APP:
                    final String packageName = target.getAndroidApp().getPackageName();
                    final String certFingerprint = target.getAndroidApp().getCertificate()
                            .getSha256Fingerprint();
                    try {
                        matcher = StatementMatcher.createAndroidAppStatementMatcher(relation,
                                packageName, certFingerprint);
                    } catch (IllegalArgumentException e) {
                        throw new QueryParsingException("package name or cert fingerprint is not valid", e);
                    }
                    break;
                case ASSET_NOT_SET:
                default:
                    throw new QueryParsingException("Unknown target asset case");
            }

            final AssetLinksJSONParser.StatementMatcherCallback callback =
                    ((m, o) -> linked[0] = true);

            try {
                verify(uri, new StatementMatcherWithCallback(matcher, callback));
            } catch (IllegalArgumentException e) {
                throw new QueryParsingException("Failed verification due to bad URI " + uri, e);
            }

            return linked[0];
        }
    }

    // =============================================================================================
    // Common
    // =============================================================================================

    private static final class QueryParsingException extends Exception {
        public QueryParsingException(String message) { super(message); }
        public QueryParsingException(String message, Throwable t) { super(message, t); }
    }

    private static class MockInjectingURISourceVerifier extends URISourceVerifier {
        private final MockWebContentServer server;

        private boolean hasParserWarnings;

        public MockInjectingURISourceVerifier(
                @NonNull List<TestProto.HostedWebContent> hostedWebContents) {
            if (RUN_LOCALLY) {
                ArrayList<MockWebContentServer.Content> mockWebContents =
                        new ArrayList<>(hostedWebContents.size());
                for (TestProto.HostedWebContent hwc : hostedWebContents) {
                    mockWebContents.add(new MockWebContentServer.Content(
                            canonicalizeURI(URI.create(hwc.getUrl())),
                            HttpURLConnection.HTTP_OK,
                            "application/json",
                            hwc.getBody()));
                }
                server = new MockWebContentServer(mockWebContents);
            } else {
                server = null;
            }
        }

        // Removes default port numbers and the trailing FQDN '.' for comparison purposes
        @NonNull
        private URI canonicalizeURI(@NonNull URI uri) {
            final boolean hasExplicitDefaultPort;
            final String scheme = uri.getScheme();
            final int port = uri.getPort();
            if ("http".equals(scheme)) {
                hasExplicitDefaultPort = (port == 80);
            } else if ("https".equals(scheme)) {
                hasExplicitDefaultPort = (port == 443);
            } else {
                hasExplicitDefaultPort = false;
            }

            final String host = uri.getHost();
            final boolean isFullyQualifiedDomainName = (host != null && host.length() > 1
                    && host.charAt(host.length() - 1) == '.');

            if (!hasExplicitDefaultPort && !isFullyQualifiedDomainName) {
                return uri;
            }

            try {
                return new URI(scheme,
                        uri.getUserInfo(),
                        isFullyQualifiedDomainName ? host.substring(0, host.length() - 1) : host,
                        hasExplicitDefaultPort ? -1 : port,
                        uri.getPath(),
                        uri.getQuery(),
                        uri.getFragment());
            } catch (URISyntaxException e) {
                throw new RuntimeException("Impossible when only modifying the port");
            }
        }

        @Override
        protected boolean verify(@NonNull URI sourceURI, StatementMatcherWithCallback... matchers)
                throws CouldNotVerifyException {
            final boolean result = super.verify(sourceURI, matchers);
            hasParserWarnings = !result;
            return result;
        }

        public boolean hasParserWarnings() {
            return this.hasParserWarnings;
        }

        @NonNull
        @Override
        protected String loadDocument(@NonNull URL documentURL) throws IOException {
            URL url;
            if (RUN_LOCALLY) {
                try {
                    url = server.serve(canonicalizeURI(documentURL.toURI()));
                } catch (URISyntaxException e) {
                    throw new RuntimeException("Test harness error converting URL to URI", e);
                }
            } else {
                url = documentURL;
            }
            return super.loadDocument(url);
        }
    }
}
