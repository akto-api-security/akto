package com.akto.behaviour_modelling.model;

import com.akto.dto.ApiInfo.ApiInfoKey;

import java.util.Arrays;

/**
 * Immutable key representing an ordered sequence of ApiInfoKeys (n-gram).
 * For sequence length 2: [api1, api2]
 * For sequence length N: [api1, ..., apiN]
 */
public final class TransitionKey {

    private final ApiInfoKey[] sequence;

    public TransitionKey(ApiInfoKey[] sequence) {
        this.sequence = sequence.clone();
    }

    public ApiInfoKey[] getSequence() {
        return sequence.clone();
    }

    public int length() {
        return sequence.length;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof TransitionKey)) return false;
        return Arrays.equals(sequence, ((TransitionKey) o).sequence);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(sequence);
    }

    @Override
    public String toString() {
        return Arrays.toString(sequence);
    }
}
