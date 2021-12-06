package com.akto.dto.reports;

public class ReportSchedule {

    public enum Freq {
        ONE_TIME, DAILY, WEEKLY, FORTNIGHTLY, MONTHLY, QUARTERLY, HALF_YEARLY, YEARLY
    }

    int startDate;
    Freq freq;
    int minuteOfDay;
    int lastDate;
    int nextDate;

    public ReportSchedule() {}

    public ReportSchedule(int startDate, Freq freq, int minuteOfDay) {
        this.startDate = startDate;
        this.freq = freq;
        this.minuteOfDay = minuteOfDay;
        this.lastDate = -1;
        this.nextDate = startDate;
    }

    public int getStartDate() {
        return startDate;
    }

    public void setStartDate(int startDate) {
        this.startDate = startDate;
    }

    public Freq getFreq() {
        return freq;
    }

    public void setFreq(Freq freq) {
        this.freq = freq;
    }

    public int getMinuteOfDay() {
        return minuteOfDay;
    }

    public void setMinuteOfDay(int minuteOfDay) {
        this.minuteOfDay = minuteOfDay;
    }

    public int getLastDate() {
        return lastDate;
    }

    public void setLastDate(int lastDate) {
        this.lastDate = lastDate;
    }

    public int getNextDate() {
        return nextDate;
    }

    public void setNextDate(int nextDate) {
        this.nextDate = nextDate;
    }
}
