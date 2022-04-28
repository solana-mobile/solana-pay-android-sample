/*
 * Copyright (c) 2022 Solana Labs, Inc.
 */

package com.solana.digitalassetlinks;

import android.util.ArrayMap;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import org.jetbrains.annotations.Contract;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Map;
import java.util.Objects;

/**
 * A {@link StatementMatcher} is used to test a set of conditions against a
 * <a href="http://digitalassetlinks.org/"> Digital Asset Links</a> statement
 */
public class StatementMatcher {
    @Nullable private final String mRelation;
    @Nullable private final String mTargetNamespace;
    @NonNull private final ArrayMap<String, String> mKeyValues;

    /**
     * Construct a new {@link StatementMatcher}
     * @param relation if specified, the relation string condition
     * @param targetNamespace if specified, the target namespace condition
     * @param keyValues if specified, a map of target object names and corresponding value
     *      conditions
     */
    protected StatementMatcher(@Nullable String relation,
                               @Nullable String targetNamespace,
                               @NonNull ArrayMap<String, String> keyValues) {
        this.mRelation = relation;
        this.mTargetNamespace = targetNamespace;
        this.mKeyValues = new ArrayMap<>(keyValues);
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

            if (mTargetNamespace != null || !mKeyValues.isEmpty()) {
                final JSONObject target = o.getJSONObject(AssetLinksGrammar.GRAMMAR_TARGET); // mandatory field

                if (mTargetNamespace != null) {
                    final String namespace = target.getString(AssetLinksGrammar.GRAMMAR_NAMESPACE); // mandatory field
                    if (!mTargetNamespace.equals(namespace)) {
                        return false;
                    }
                }

                for (Map.Entry<String, String> entry : mKeyValues.entrySet()) {
                    if (!target.has(entry.getKey())) {
                        return false;
                    }
                    final String targetValue = target.getString(entry.getKey());
                    if (!entry.getValue().equals(targetValue)) {
                        return false;
                    }
                }
            }
        } catch (JSONException e) {
            throw new IllegalArgumentException("Asset Links JSON object '" + o + "' is not well-formed", e);
        }

        return true;
    }

    /**
     * Create a new {@link Builder}
     * @return a new {@link Builder}
     */
    @NonNull
    @Contract(" -> new")
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
                mKeyValues.equals(that.mKeyValues);
    }

    @Override
    public int hashCode() {
        return Objects.hash(mRelation, mTargetNamespace, mKeyValues);
    }

    @NonNull
    @Override
    public String toString() {
        return "StatementMatcher{" +
                "mRelation='" + mRelation + '\'' +
                ", mTargetNamespace='" + mTargetNamespace + '\'' +
                ", mKeyValues=" + mKeyValues +
                '}';
    }

    /**
     * Implements the Builder pattern for {@link StatementMatcher}
     */
    public static class Builder {
        private String mRelation;
        private String mTargetNamespace;
        private final ArrayMap<String, String> mKeyValues = new ArrayMap<>();

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
         * Sets a target key-value condition for this {@link Builder}
         * @param key the target name for which to apply this condition
         * @param value the target value of this condition
         * @return this {@link Builder}
         */
        @NonNull
        public Builder setTargetKeyValue(@NonNull String key, @Nullable String value) {
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
}
