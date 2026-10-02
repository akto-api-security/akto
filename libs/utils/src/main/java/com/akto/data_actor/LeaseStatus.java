package com.akto.data_actor;

/**
 * Outcome of a lease-bearing write against the abstractor.
 *
 * The distinction between REJECTED and UNKNOWN is the point of this type. A rejected write means
 * the server compared our token and it no longer matches, so another pod owns the attempt and we
 * must stop. A failed write means we simply did not get an answer. Collapsing the two - which is
 * what every other DataActor call does by swallowing exceptions and returning null - would make a
 * transient abstractor blip abandon a perfectly healthy run.
 *
 * UNKNOWN needs no retry policy, because it decays into rejection by arithmetic: a lease that
 * could not be renewed within its TTL has expired server-side whether or not we heard about it.
 * Callers hold on to the last APPLIED timestamp and self-fence once the TTL is past.
 */
public enum LeaseStatus {
    APPLIED,
    REJECTED,
    UNKNOWN;

    public boolean lost() {
        return this == REJECTED;
    }
}
