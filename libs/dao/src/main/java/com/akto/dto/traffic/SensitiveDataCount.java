package com.akto.dto.traffic;

import java.util.Date;

/**
 * How many messages carried a given custom sensitive data type during one hour, for one pair of
 * services.
 *
 * Kept centrally so that thresholds over long windows (a week, a month) survive runtime restarts:
 * a mini-runtime only holds the counts since its last flush, and adds them here with $inc. That makes
 * the stored count the sum across every runtime instance, with no coordination between them.
 *
 * The same shape is used on the wire, where {@link #count} is the increment to apply rather than a
 * total and {@link #expireAt} is unset.
 *
 * Both ends are API collection ids rather than names. {@link #apiCollectionId} is the callee, which
 * is what a message's collection already is, and {@link #senderCollectionId} is the caller. Ids keep
 * the two ends resolvable through the same collection, so one service reads as one name whichever
 * side of a call it is on. The endpoint stays out of the key: including it would multiply every
 * bucket by the number of distinct URLs.
 */
public class SensitiveDataCount {

    /** No caller could be identified, so this bucket has no sender to alert on. */
    public static final int UNKNOWN_COLLECTION_ID = 0;

    /**
     * The callee's collection - the service that received the message.
     *
     * This is the collection the value was detected in. Collection routing is keyed on the Host
     * header for outbound traffic precisely so that it names the callee, so nothing extra is needed
     * to know the receiving end.
     */
    public static final String API_COLLECTION_ID = "apiCollectionId";
    private int apiCollectionId;

    /** The caller's collection, or {@link #UNKNOWN_COLLECTION_ID}. */
    public static final String SENDER_COLLECTION_ID = "senderCollectionId";
    private int senderCollectionId;

    /** The data type's name, e.g. VERIFIED_CUSTOMER_EMAIL. */
    public static final String SUB_TYPE = "subType";
    private String subType;

    /** Start of the hour, in epoch seconds (UTC). */
    public static final String HOUR = "hour";
    private int hour;

    public static final String COUNT = "count";
    private long count;

    /** When the TTL index removes this bucket. Set on insert, from the bucket's own hour. */
    public static final String EXPIRE_AT = "expireAt";
    private Date expireAt;

    public SensitiveDataCount() {
    }

    public SensitiveDataCount(int apiCollectionId, String subType, int hour, long count) {
        this(apiCollectionId, UNKNOWN_COLLECTION_ID, subType, hour, count);
    }

    public SensitiveDataCount(int apiCollectionId, int senderCollectionId, String subType, int hour, long count) {
        this.apiCollectionId = apiCollectionId;
        this.senderCollectionId = senderCollectionId;
        this.subType = subType;
        this.hour = hour;
        this.count = count;
    }

    /** Start of the hour containing this epoch-seconds timestamp. */
    public static int hourOf(int epochSeconds) {
        return epochSeconds - Math.floorMod(epochSeconds, 3600);
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public int getSenderCollectionId() {
        return senderCollectionId;
    }

    public void setSenderCollectionId(int senderCollectionId) {
        this.senderCollectionId = senderCollectionId;
    }

    public String getSubType() {
        return subType;
    }

    public void setSubType(String subType) {
        this.subType = subType;
    }

    public int getHour() {
        return hour;
    }

    public void setHour(int hour) {
        this.hour = hour;
    }

    public long getCount() {
        return count;
    }

    public void setCount(long count) {
        this.count = count;
    }

    public Date getExpireAt() {
        return expireAt;
    }

    public void setExpireAt(Date expireAt) {
        this.expireAt = expireAt;
    }

    @Override
    public String toString() {
        return "{ apiCollectionId=" + apiCollectionId + ", senderCollectionId=" + senderCollectionId
                + ", subType='" + subType + "', hour=" + hour + ", count=" + count + " }";
    }
}
