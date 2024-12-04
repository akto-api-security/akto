package com.akto.action.threat_detection;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.bson.conversions.Bson;

import com.akto.action.UserAction;
import com.akto.dao.SuspectSampleDataDao;
import com.akto.dao.context.Context;
import com.akto.dto.traffic.SuspectSampleData;
import com.akto.util.Constants;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.Sorts;

public class SuspectSampleDataAction extends UserAction {

  List<SuspectSampleData> sampleData;
  int skip;
  static final int LIMIT = 50;
  List<String> ips;
  List<String> urls;
  List<Integer> apiCollectionIds;
  long total;
  Map<String, Integer> sort;
  int startTimestamp, endTimestamp;

  public String fetchSampleDataV2() {
    throw new UnsupportedOperationException("Not implemented yet");
  }

  public String fetchSuspectSampleData() {

    List<Bson> filterList = new ArrayList<>();

    /*
     * In case time filters are empty,
     * using default filter as 2 months.
     */

    if (startTimestamp <= 0) {
      startTimestamp = Context.now() - 2 * 30 * 24 * 60 * 60;
    }
    if (endTimestamp <= 0) {
      endTimestamp = Context.now() + 10 * 60;
    }

    filterList.add(Filters.gte(SuspectSampleData._DISCOVERED, startTimestamp));
    filterList.add(Filters.lte(SuspectSampleData._DISCOVERED, endTimestamp));

    if (ips != null && !ips.isEmpty()) {
      filterList.add(Filters.in(SuspectSampleData.SOURCE_IPS, ips));
    }
    if (urls != null && !urls.isEmpty()) {
      filterList.add(Filters.in(SuspectSampleData.MATCHING_URL, urls));
    }
    if (apiCollectionIds != null && !apiCollectionIds.isEmpty()) {
      filterList.add(Filters.in(SuspectSampleData.API_COLLECTION_ID, apiCollectionIds));
    }

    Bson finalFilter = Filters.empty();

    finalFilter = Filters.and(filterList);

    String sortKey = SuspectSampleData._DISCOVERED;
    int sortDirection = -1;
    /*
     * add any new sort key here,
     * for validation and sanity.
     */
    Set<String> sortKeys = new HashSet<>();
    sortKeys.add(SuspectSampleData._DISCOVERED);

    if (sort != null && !sort.isEmpty()) {
      Entry<String, Integer> sortEntry = sort.entrySet().iterator().next();
      sortKey = sortEntry.getKey();
      if (!sortKeys.contains(sortKey)) {
        sortKey = SuspectSampleData._DISCOVERED;
      }
      sortDirection = sortEntry.getValue();
      if (!(sortDirection == -1 || sortDirection == 1)) {
        sortDirection = -1;
      }
    }

    /*
     * In case timestamp is same, then id acts as tie-breaker,
     * to avoid repeating the same documents again.
     */
    Bson sort =
        sortDirection == -1
            ? Sorts.descending(sortKey, Constants.ID)
            : Sorts.ascending(sortKey, Constants.ID);
    sampleData = SuspectSampleDataDao.instance.findAll(finalFilter, skip, LIMIT, sort);
    total = SuspectSampleDataDao.instance.count(finalFilter);

    return SUCCESS.toUpperCase();
  }

  public String fetchFilters() {
    ips =
        new ArrayList<>(
            SuspectSampleDataDao.instance.findDistinctFields(
                SuspectSampleData.SOURCE_IPS, String.class, Filters.empty()));
    urls =
        new ArrayList<>(
            SuspectSampleDataDao.instance.findDistinctFields(
                SuspectSampleData.MATCHING_URL, String.class, Filters.empty()));
    return SUCCESS.toUpperCase();
  }

  public List<SuspectSampleData> getSampleData() {
    return sampleData;
  }

  public void setSampleData(List<SuspectSampleData> sampleData) {
    this.sampleData = sampleData;
  }

  public int getSkip() {
    return skip;
  }

  public void setSkip(int skip) {
    this.skip = skip;
  }

  public static int getLimit() {
    return LIMIT;
  }

  public List<String> getIps() {
    return ips;
  }

  public void setIps(List<String> ips) {
    this.ips = ips;
  }

  public List<String> getUrls() {
    return urls;
  }

  public void setUrls(List<String> urls) {
    this.urls = urls;
  }

  public List<Integer> getApiCollectionIds() {
    return apiCollectionIds;
  }

  public void setApiCollectionIds(List<Integer> apiCollectionIds) {
    this.apiCollectionIds = apiCollectionIds;
  }

  public long getTotal() {
    return total;
  }

  public void setTotal(long total) {
    this.total = total;
  }

  public Map<String, Integer> getSort() {
    return sort;
  }

  public void setSort(Map<String, Integer> sort) {
    this.sort = sort;
  }

  public int getStartTimestamp() {
    return startTimestamp;
  }

  public void setStartTimestamp(int startTimestamp) {
    this.startTimestamp = startTimestamp;
  }

  public int getEndTimestamp() {
    return endTimestamp;
  }

  public void setEndTimestamp(int endTimestamp) {
    this.endTimestamp = endTimestamp;
  }
}
