package com.akto.action;
import com.akto.apps.Main;
import com.akto.dto.ApiInfo;
import com.akto.dto.sql.SampleDataAlt;
import com.akto.sql.SampleDataAltDb;
import com.opensymphony.xwork2.ActionSupport;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DbLayerAction extends ActionSupport {

    ApiInfo.ApiInfoKey apiInfoKey;
    List<String> samples;
    String sample;
    int records;
    long dbSize;
    List<SampleDataAlt> unfilteredSamples;

    private static final Logger logger = LoggerFactory.getLogger(Main.class);

    public String fetchSamples() {
        try {
            samples = SampleDataAltDb.findSamplesByApiInfoKey(apiInfoKey);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("error in fetchSamples " + e.getMessage());
        }
        return SUCCESS.toUpperCase();
    }

    public String fetchLatestSample() {
        try {
            sample = SampleDataAltDb.findLatestSampleByApiInfoKey(apiInfoKey);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("error in fetchLatestSample " + e.getMessage());
        }
        return SUCCESS.toUpperCase();
    }

    public String fetchTotalRecords() {
        try {
            records =SampleDataAltDb.totalNumberOfRecords();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("error in fetchTotalRecords " + e.getMessage());
        }
        return SUCCESS.toUpperCase();
    }

    public String fetchTotalSize() {
        try {
            dbSize = SampleDataAltDb.getDbSizeInMb();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("error in fetchTotalSize " + e.getMessage());
        }
        return SUCCESS.toUpperCase();
    }

    public String deleteOldRecords() {
        try {
            records = SampleDataAltDb.deleteOld();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("error in deleteOldRecords " + e.getMessage());
        }
        return SUCCESS.toUpperCase();
    }

    public String bulkInsert() {
        try {
            SampleDataAltDb.bulkInsert(unfilteredSamples);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("error in deleteOldRecords " + e.getMessage());
        }
        return SUCCESS.toUpperCase();
    }

    public List<String> getSamples() {
        return samples;
    }

    public void setSamples(List<String> samples) {
        this.samples = samples;
    }

    public String getSample() {
        return sample;
    }

    public void setSample(String sample) {
        this.sample = sample;
    }

    public ApiInfo.ApiInfoKey getApiInfoKey() {
        return apiInfoKey;
    }

    public void setApiInfoKey(ApiInfo.ApiInfoKey apiInfoKey) {
        this.apiInfoKey = apiInfoKey;
    }

    public int getRecords() {
        return records;
    }

    public void setRecords(int records) {
        this.records = records;
    }

    public long getDbSize() {
        return dbSize;
    }

    public void setDbSize(long dbSize) {
        this.dbSize = dbSize;
    }

    public List<SampleDataAlt> getUnfilteredSamples() {
        return unfilteredSamples;
    }

    public void setUnfilteredSamples(List<SampleDataAlt> unfilteredSamples) {
        this.unfilteredSamples = unfilteredSamples;
    }

}
