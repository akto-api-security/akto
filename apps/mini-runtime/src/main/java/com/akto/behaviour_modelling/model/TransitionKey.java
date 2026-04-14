package com.akto.behaviour_modelling.model;

import java.util.Arrays;

/**
 * Immutable key representing an ordered sequence of API IDs (n-gram).
 * For sequence length 2: [api1Id, api2Id]
 * For sequence length N: [api1Id, ..., apiNId]
 */
public final class TransitionKey {

    private final int[] sequence;

    public TransitionKey(int[] sequence) {
        this.sequence = sequence.clone();
    }

    public int[] getSequence() {
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
