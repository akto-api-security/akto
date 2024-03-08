package com.akto.dto.loaders;

public abstract class NormalLoader extends Loader {

    private int currentCount;
    public static final String CURRENT_COUNT = "currentCount";
    private int totalCount;
    public static final String TOTAL_COUNT = "totalCount";


    public NormalLoader(Type type) {
        super(type);
    }

    public NormalLoader(Type type, int userId, int currentCount, int totalCount, boolean show) {
        super(type, userId, show);
        this.currentCount = currentCount;
        this.totalCount = totalCount;
    }

    @Override
    public int getPercentage() {
        if (totalCount == 0) return 0;
        return (int) (100.0*currentCount/totalCount);
    }

    public int getCurrentCount() {
        return currentCount;
    }

    public void setCurrentCount(int currentCount) {
        this.currentCount = currentCount;
    }

    public int getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(int totalCount) {
        this.totalCount = totalCount;
    }
}
