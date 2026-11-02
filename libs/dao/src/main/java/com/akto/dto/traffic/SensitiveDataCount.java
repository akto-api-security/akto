package com.akto.dto.traffic;

import java.util.Date;

/**
 * How many messages in one API collection carried a given custom sensitive data type during one
 * hour.
 *
 * Kept centrally so that thresholds over long windows (a week, a month) survive runtime restarts:
 * a mini-runtime only holds the counts since its last flush, and adds them here with $inc. That makes
 * the stored count the sum across every runtime instance, with no coordination between them.
 *
 * The same shape is used on the wire, where {@link #count} is the increment to apply rather than a
 * total and {@link #expireAt} is unset.
 */
public class SensitiveDataCount {

    public static final String API_COLLECTION_ID = "apiCollectionId";
    private int apiCollectionId;

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
        this.apiCollectionId = apiCollectionId;
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
        return "{ apiCollectionId=" + apiCollectionId + ", subType='" + subType + "', hour=" + hour
                + ", count=" + count + " }";
    }
}
