package com.akto.action;
import com.akto.dto.ApiInfo;
import com.akto.dto.sql.SampleDataAlt;
import com.akto.dto.sql.SampleDataAltCopy;
import com.akto.dto.type.URLMethods;
import com.akto.sql.SampleDataAltDb;
import com.mongodb.BasicDBList;
import com.opensymphony.xwork2.ActionSupport;
import com.google.common.hash.BloomFilter;
import com.google.common.hash.Funnels;

import java.nio.charset.StandardCharsets;
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

    // Bloom filter to track unique APIs across all bulk insert calls
    // Expected insertions: 1 million APIs, false positive probability: 0.01
    private static final BloomFilter<CharSequence> apiBloomFilter = BloomFilter.create(
        Funnels.stringFunnel(StandardCharsets.UTF_8),
        1_000_000,
        0.01
    );

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

            int totalSamples = samplesCopy.size();
            int duplicatesSkipped = 0;

            long thirtyMinutesMs = 30 * 60 * 1000L;

            for (SampleDataAltCopy sampleDataAltCopy: samplesCopy) {
                // Calculate 30-minute time bucket from payload timestamp
                // Round up to next 30-minute interval
                long payloadTimestamp = sampleDataAltCopy.getTimestamp();
                long timeBucket = ((payloadTimestamp / thirtyMinutesMs) + 1) * thirtyMinutesMs;

                // Create a unique key for the API with time bucket: collectionId:method:url:timeBucket
                String apiKey = sampleDataAltCopy.getApiCollectionId() + ":" +
                               sampleDataAltCopy.getMethod() + ":" +
                               sampleDataAltCopy.getUrl() + ":" +
                               timeBucket;

                // Check if this API has already been seen in this 30-minute window
                if (apiBloomFilter.mightContain(apiKey)) {
                    duplicatesSkipped++;
                    logger.debug("Skipping duplicate sample for API in current time bucket: collectionId={}, method={}, url={}, timeBucket={}",
                                sampleDataAltCopy.getApiCollectionId(),
                                sampleDataAltCopy.getMethod(),
                                sampleDataAltCopy.getUrl(),
                                timeBucket);
                    continue;
                }

                // Add to Bloom filter
                apiBloomFilter.put(apiKey);

                // Add to insertion list
                sampleDataList.add(new SampleDataAlt(UUID.fromString(sampleDataAltCopy.getId()),
                sampleDataAltCopy.getSample(), sampleDataAltCopy.getApiCollectionId(),
                sampleDataAltCopy.getMethod(), sampleDataAltCopy.getUrl(),
                sampleDataAltCopy.getResponseCode(), sampleDataAltCopy.getTimestamp(),
                sampleDataAltCopy.getAccountId()));
            }

            logger.info("bulkInsertSamples: total samples processed={}, unique samples to insert={}, duplicates skipped={}",
                       totalSamples, sampleDataList.size(), duplicatesSkipped);

            if (!sampleDataList.isEmpty()) {
                SampleDataAltDb.bulkInsert(sampleDataList);
                logger.info("bulkInsertSamples: successfully inserted {} unique samples", sampleDataList.size());
            } else {
                logger.info("bulkInsertSamples: no unique samples to insert");
            }
        } catch (Exception e) {
            e.printStackTrace();
            logger.error("error in bulkInsertSamples " + e.getMessage());
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
