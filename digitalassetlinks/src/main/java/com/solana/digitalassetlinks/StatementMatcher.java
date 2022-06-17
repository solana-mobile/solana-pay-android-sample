/*
 * Copyright (c) 2022 Solana Mobile Inc.
 */

package com.solana.digitalassetlinks;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArrayMap;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Map;
import java.util.Objects;

/**
 * A {@link StatementMatcher} is used to test a set of conditions against a
 * <a href="http://digitalassetlinks.org/"> Digital Asset Links</a> statement
 */
public class StatementMatcher {
    @Nullable private final String mRelation;
    @Nullable private final String mTargetNamespace;
    @NonNull private final ArrayMap<String, Object> mTargetKeyValues;

    /**
     * Construct a new {@link StatementMatcher}
     * @param relation if specified, the relation string condition
     * @param targetNamespace if specified, the target namespace condition
     * @param targetKeyValues if specified, a map of target object names and corresponding value
     *      conditions
     */
    protected StatementMatcher(@Nullable String relation,
                               @Nullable String targetNamespace,
                               @NonNull ArrayMap<String, Object> targetKeyValues) {
        if (relation != null && !relation.matches(AssetLinksGrammar.RELATION_PATTERN)) {
            throw new IllegalArgumentException("relation does not match the expected format");
        }

        this.mRelation = relation;
        this.mTargetNamespace = targetNamespace;
        this.mTargetKeyValues = new ArrayMap<>(targetKeyValues);
    }

    /**
     * Apply this {@link StatementMatcher} against the specified
     * @param o the Asset Links statement object against which to perform matching
     * @return true if all conditions of this {@link StatementMatcher} are satisfied, else false
     * @throws IllegalArgumentException if o is not a well-formed Asset Links statement object
     */
    public boolean compare(@NonNull JSONObject o) {
        try {
            if (mRelation != null) {
                final JSONArray relations = o.getJSONArray(AssetLinksGrammar.GRAMMAR_RELATION); // mandatory field
                final int numRelations = relations.length();
                if (numRelations == 0) {
                    throw new IllegalArgumentException("At least one relation must be present");
                }
                boolean relationMatched = false;
                for (int i = 0; i < numRelations; i++) {
                    final String relation = relations.getString(i);
                    if (mRelation.equals(relation)) {
                        relationMatched = true;
                        break;
                    }
                }
                if (!relationMatched) {
                    return false;
                }
            }

            if (mTargetNamespace != null || !mTargetKeyValues.isEmpty()) {
                final JSONObject target = o.getJSONObject(AssetLinksGrammar.GRAMMAR_TARGET); // mandatory field

                if (mTargetNamespace != null) {
                    final String namespace = target.getString(AssetLinksGrammar.GRAMMAR_NAMESPACE); // mandatory field
                    if (!mTargetNamespace.equals(namespace)) {
                        return false;
                    }
                }

                for (Map.Entry<String, Object> entry : mTargetKeyValues.entrySet()) {
                    if (!target.has(entry.getKey())) {
                        return false;
                    }

                    final Object valueObj = entry.getValue();
                    if (valueObj instanceof String) {
                        final String value = (String)valueObj;
                        final String targetValue = target.getString(entry.getKey());
                        if (!value.equals(targetValue)) {
                            return false;
                        }
                    } else if (valueObj instanceof AllowMatchInArray) {
                        final String value = ((AllowMatchInArray)valueObj).value;
                        final Object targetValueObj = target.get(entry.getKey());
                        if (targetValueObj instanceof JSONArray) {
                            final JSONArray targetValues = (JSONArray) targetValueObj;
                            boolean any = false;
                            for (int i = 0; i < targetValues.length(); i++) {
                                final String targetValue = targetValues.getString(i);
                                if (value.equals(targetValue)) {
                                    any = true;
                                    break;
                                }
                            }
                            if (!any) {
                                return false;
                            }
                        } else {
                            if (!value.equals(targetValueObj.toString())) {
                                return false;
                            }
                        }
                    } else if (valueObj instanceof URI) {
                        final URI value = canonicalizeURI((URI)valueObj);
                        final String targetValue = target.getString(entry.getKey());
                        try {
                            final URI targetURI = canonicalizeURI(URI.create(targetValue));
                            if (!value.equals(targetURI)) {
                                return false;
                            }
                        } catch (IllegalArgumentException e) {
                            // target value not a URI; skip it. Matchers aren't responsible for
                            // syntax validation of the model.
                        }
                    }
                }
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException("Asset Links JSON object '" + o + "' is not well-formed", e);
        }

        return true;
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

    /**
     * Create a {@link StatementMatcher} for the {@link AssetLinksGrammar#GRAMMAR_NAMESPACE_WEB}
     * namespace
     * @param relation the {@link AssetLinksGrammar#GRAMMAR_RELATION} to match
     * @param site if non-null, the {@link AssetLinksGrammar#GRAMMAR_WEB_SITE} to match. This will
     *      be validated against the rules for the web domain.
     * @return a {@link StatementMatcher}
     * @throws IllegalArgumentException if site is non-null and fails validation
     */
    public static StatementMatcher createWebStatementMatcher(@NonNull String relation,
                                                             @Nullable URI site) {
        final Builder builder = StatementMatcher.newBuilder()
                .setRelation(relation)
                .setTargetNamespace(AssetLinksGrammar.GRAMMAR_NAMESPACE_WEB);
        if (site != null) {
            if (!AssetLinksGrammar.isValidSiteURI(site)) {
                throw new IllegalArgumentException("site is not a valid site URI");
            }
            builder.setTargetKeyValue(AssetLinksGrammar.GRAMMAR_WEB_SITE, site);
        }
        return builder.build();
    }

    /**
     * Create a {@link StatementMatcher} for the
     * {@link AssetLinksGrammar#GRAMMAR_NAMESPACE_ANDROID_APP} namespace
     * @param relation the {@link AssetLinksGrammar#GRAMMAR_RELATION} to match
     * @param packageName if non-null, the
     *      {@link AssetLinksGrammar#GRAMMAR_ANDROID_APP_PACKAGE_NAME} to match. This will be
     *      validated against the rules for the android_app domain.
     * @param sha256CertFingerprint if non-null, the
     *      {@link AssetLinksGrammar#GRAMMAR_ANDROID_APP_SHA256_CERT_FINGERPRINTS} to match. This
     *      will be validated against the rules for the android_app domain.
     * @return a {@link StatementMatcher}
     * @throws IllegalArgumentException if packageName is non-null and fails validation, or if
     *      sha256CertFingerprint is non-null and fails validation.
     */
    public static StatementMatcher createAndroidAppStatementMatcher(@NonNull String relation,
                                                                    @Nullable String packageName,
                                                                    @Nullable String sha256CertFingerprint) {
        final Builder builder = StatementMatcher.newBuilder()
                .setRelation(relation)
                .setTargetNamespace(AssetLinksGrammar.GRAMMAR_NAMESPACE_ANDROID_APP);
        if (packageName != null) {
            if (!packageName.matches(AssetLinksGrammar.PACKAGE_NAME_PATTERN)) {
                throw new IllegalArgumentException("Invalid Android app package name");
            }
            builder.setTargetKeyValue(
                    AssetLinksGrammar.GRAMMAR_ANDROID_APP_PACKAGE_NAME, packageName);
        }
        if (sha256CertFingerprint != null) {
            if (!sha256CertFingerprint.matches(AssetLinksGrammar.SHA256_CERT_FINGERPRINT_PATTERN)) {
                throw new IllegalArgumentException("Invalid Android app certificate fingerprint");
            }
            builder.setTargetKeyValue(
                    AssetLinksGrammar.GRAMMAR_ANDROID_APP_SHA256_CERT_FINGERPRINTS,
                    sha256CertFingerprint, true);
        }
        return builder.build();
    }

    /**
     * Create a new {@link Builder}
     * @return a new {@link Builder}
     */
    @NonNull
    public static Builder newBuilder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        StatementMatcher that = (StatementMatcher) o;
        return Objects.equals(mRelation, that.mRelation) &&
                Objects.equals(mTargetNamespace, that.mTargetNamespace) &&
                mTargetKeyValues.equals(that.mTargetKeyValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRelation, mTargetNamespace, mTargetKeyValues);
    }

    @NonNull
    @Override
    public String toString() {
        return "StatementMatcher{" +
                "mRelation='" + mRelation + '\'' +
                ", mTargetNamespace='" + mTargetNamespace + '\'' +
                ", mKeyValues=" + mTargetKeyValues +
                '}';
    }

    /**
     * Implements the Builder pattern for {@link StatementMatcher}
     */
    public static class Builder {
        private String mRelation;
        private String mTargetNamespace;
        private final ArrayMap<String, Object> mKeyValues = new ArrayMap<>();

        /**
         * Construct a new {@link Builder}
         */
        public Builder() {}

        /**
         * Sets the relation condition for this {@link Builder}
         * @param relation the relation condition for the {@link StatementMatcher}
         * @return this {@link Builder}
         */
        @NonNull
        public Builder setRelation(@Nullable String relation) {
            mRelation = relation;
            return this;
        }

        /**
         * Sets the target namespace condition for this {@link Builder}
         * @param targetNamespace the target namespace condition for the {@link StatementMatcher}
         * @return this {@link Builder}
         */
        @NonNull
        public Builder setTargetNamespace(@Nullable String targetNamespace) {
            mTargetNamespace = targetNamespace;
            return this;
        }

        /**
         * Sets a target String key-value condition for this {@link Builder}
         * @param key the target name for which to apply this condition
         * @param value the target String value of this condition
         * @return this {@link Builder}
         */
        @NonNull
        public Builder setTargetKeyValue(@NonNull String key, @Nullable String value) {
            return setTargetKeyValue(key, value, false);
        }

        /**
         * Sets a target String key-value condition for this {@link Builder}
         * @param key the target name for which to apply this condition
         * @param value the target String value of this condition
         * @param allowMatchInArray whether this match is allowed to occur within an array of values
         *      for key
         * @return this {@link Builder}
         */
        @NonNull
        public Builder setTargetKeyValue(@NonNull String key,
                                         @Nullable String value,
                                         boolean allowMatchInArray) {
            if (value != null) {
                if (allowMatchInArray) {
                    mKeyValues.put(key, new AllowMatchInArray(value));
                } else {
                    mKeyValues.put(key, value);
                }
            } else {
                mKeyValues.remove(key);
            }
            return this;
        }

        /**
         * Sets a target {@link URI} key-value condition for this {@link Builder}
         * @param key the target name for which to apply this condition
         * @param value the target {@link URI} value of this condition
         * @return this {@link Builder}
         */
        @NonNull
        public Builder setTargetKeyValue(@NonNull String key, @Nullable URI value) {
            if (value != null) {
                mKeyValues.put(key, value);
            } else {
                mKeyValues.remove(key);
            }
            return this;
        }

        /**
         * Construct a new {@link StatementMatcher} from the current state of this {@link Builder}
         * @return a new {@link StatementMatcher}
         */
        @NonNull
        public StatementMatcher build() {
            return new StatementMatcher(mRelation, mTargetNamespace, mKeyValues);
        }
    }

    private static final class AllowMatchInArray {
        @NonNull
        public final String value;

        public AllowMatchInArray(@NonNull String value) {
            this.value = value;
        }
    }
}
