package com.akto.action;
import com.akto.dto.ApiInfo;
import com.akto.dto.sql.SampleDataAlt;
import com.akto.dto.sql.SampleDataAltCopy;
import com.akto.dto.type.URLMethods;
import com.akto.sql.SampleDataAltDb;
import com.akto.util.SampleDeduplicationFilter;
import com.mongodb.BasicDBList;
import com.opensymphony.xwork2.ActionSupport;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


public class DbLayerAction extends ActionSupport {

    ApiInfo.ApiInfoKey apiInfoKey;
    List<String> samples;
    String sample;
    int records;
    long totalSize;
    List<SampleDataAlt> unfilteredSamples;
    List<SampleDataAltCopy> samplesCopy;
    String command;
    BasicDBList respList;
    List<String> uuidList;
    String newUrl;
    int apiCollectionId;
    String method;
    String url;
    int skip;
    // MergingLocal samples logic should be run from only a single mini runtime.
    boolean dbMergingMode;

    private static final int fetchLimit = 1000;

    private static final Logger logger = LoggerFactory.getLogger(DbLayerAction.class);

    public String fetchSamples() {
        try {
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(apiCollectionId, url, URLMethods.Method.fromString(method));
            samples = SampleDataAltDb.findSamplesByApiInfoKey(apiInfoKey);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("error in fetchSamples " + e.getMessage());
        }
        return SUCCESS.toUpperCase();
    }

    public String fetchLatestSample() {
        try {
            ApiInfo.ApiInfoKey apiInfoKey = new ApiInfo.ApiInfoKey(apiCollectionId, url, URLMethods.Method.fromString(method));
            sample = SampleDataAltDb.findLatestSampleByApiInfoKey(apiInfoKey);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("error in fetchLatestSample " + e.getMessage());
        }
        return SUCCESS.toUpperCase();
    }

    public String fetchTotalRecords() {
        try {
            records = SampleDataAltDb.totalNumberOfRecords();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("error in fetchTotalRecords " + e.getMessage());
        }
        return SUCCESS.toUpperCase();
        
    }

    public String fetchTotalSize() {
        try {
            totalSize = SampleDataAltDb.getDbSizeInMb();
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("error in fetchTotalSize " + e.getMessage());
        }
        return SUCCESS.toUpperCase();
    }

    public String bulkInsertSamples() {
        if (System.getenv().getOrDefault("SKIP_BULK_INSERT", "false").equals("true")) {
            return SUCCESS.toUpperCase();
        }

        try {
            List<SampleDataAlt> sampleDataList = new ArrayList<>();
            int batchDuplicates = 0;

            for (SampleDataAltCopy sampleDataAltCopy: samplesCopy) {
                // Check if sample should be inserted using deduplication filter
                boolean shouldInsert = SampleDeduplicationFilter.shouldInsertSample(
                    sampleDataAltCopy.getApiCollectionId(),
                    sampleDataAltCopy.getMethod(),
                    sampleDataAltCopy.getUrl()
                );

                if (!shouldInsert) {
                    batchDuplicates++;
                    continue;
                }

                // Add to insertion list for database
                sampleDataList.add(new SampleDataAlt(
                    UUID.fromString(sampleDataAltCopy.getId()),
                    sampleDataAltCopy.getSample(),
                    sampleDataAltCopy.getApiCollectionId(),
                    sampleDataAltCopy.getMethod(),
                    sampleDataAltCopy.getUrl(),
                    sampleDataAltCopy.getResponseCode(),
                    sampleDataAltCopy.getTimestamp(),
                    sampleDataAltCopy.getAccountId()
                ));
            }

            // Log batch results
            logger.info("bulkInsertSamples: batch processed={}, unique to insert={}, duplicates skipped={}",
                       samplesCopy.size(), sampleDataList.size(), batchDuplicates);

            // Insert unique samples into database
            if (!sampleDataList.isEmpty()) {
                SampleDataAltDb.bulkInsert(sampleDataList);
                logger.info("bulkInsertSamples: successfully inserted {} unique samples into database",
                           sampleDataList.size());
            } else {
                logger.info("bulkInsertSamples: no unique samples to insert (all duplicates)");
            }

        } catch (Exception e) {
            logger.error("error in bulkInsertSamples: {}", e.getMessage(), e);
        }

        return SUCCESS.toUpperCase();
    }

    public String triggerPostgresCommand() {
        try {
             respList = SampleDataAltDb.runCommand(command);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("error in triggerPostgresCommand " + e.getMessage());
        }
        return SUCCESS.toUpperCase();
    }

    public String updateUrl() {
        try {
            SampleDataAltDb.updateUrl(uuidList, newUrl);
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("error in updateUrl " + e.getMessage());
        }
        return SUCCESS.toUpperCase();
    }

    public String fetchSampleData() {
        if(!dbMergingMode) {
            unfilteredSamples = new ArrayList<>();
            samplesCopy = new ArrayList<>();
            return SUCCESS.toUpperCase();
        }
        try {
            unfilteredSamples = SampleDataAltDb.iterateAndGetAll(apiCollectionId, fetchLimit, skip);
            samplesCopy = new ArrayList<>();
            for (SampleDataAlt sampleData: unfilteredSamples) {
                samplesCopy.add(new SampleDataAltCopy(sampleData.getId().toString(), 
                sampleData.getSample(), sampleData.getApiCollectionId(), 
                sampleData.getMethod(), sampleData.getUrl(), 
                sampleData.getResponseCode(), sampleData.getTimestamp(), 
                sampleData.getAccountId()));
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("error in fetchSampleData " + e.getMessage());
        }
        return SUCCESS.toUpperCase();
    }

    public String createSampleDataTable() {
        try {
            logger.info("initiating createSampleDataTable call");
            com.akto.sql.Main.createSampleDataTable();
            SampleDataAltDb.createIndex();
        } catch(Exception e){
            e.printStackTrace();
            logger.error("error in createSampleDataTable " + e.getMessage());
            return ERROR.toUpperCase();
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

    public long getTotalSize() {
        return totalSize;
    }

    public void setTotalSize(long totalSize) {
        this.totalSize = totalSize;
    }

    public List<SampleDataAlt> getUnfilteredSamples() {
        return unfilteredSamples;
    }

    public void setUnfilteredSamples(List<SampleDataAlt> unfilteredSamples) {
        this.unfilteredSamples = unfilteredSamples;
    }

    public String getCommand() {
        return command;
    }

    public void setCommand(String command) {
        this.command = command;
    }

    public BasicDBList getRespList() {
        return respList;
    }

    public void setRespList(BasicDBList respList) {
        this.respList = respList;
    }

    public List<String> getUuidList() {
        return uuidList;
    }

    public void setUuidList(List<String> uuidList) {
        this.uuidList = uuidList;
    }

    public String getNewUrl() {
        return newUrl;
    }

    public void setNewUrl(String newUrl) {
        this.newUrl = newUrl;
    }

    public int getApiCollectionId() {
        return apiCollectionId;
    }

    public void setApiCollectionId(int apiCollectionId) {
        this.apiCollectionId = apiCollectionId;
    }

    public int getSkip() {
        return skip;
    }

    public void setSkip(int skip) {
        this.skip = skip;
    }

    public boolean getDbMergingMode() {
        return dbMergingMode;
    }

    public void setDbMergingMode(boolean dbMergingMode) {
        this.dbMergingMode = dbMergingMode;
    }

    public List<SampleDataAltCopy> getSamplesCopy() {
        return samplesCopy;
    }

    public void setSamplesCopy(List<SampleDataAltCopy> samplesCopy) {
        this.samplesCopy = samplesCopy;
    }

    public String getMethod() {
        return method;
    }

    public void setMethod(String method) {
        this.method = method;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }


}
