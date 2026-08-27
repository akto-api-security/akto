package com.akto.action.threat_detection;

public class ThreatCategoryCount {
  private String category;
  private String subCategory;
  private int count;
  private String url;

  public ThreatCategoryCount(String category, String subCategory, int count) {
    this(category, subCategory, count, null);
  }

  public ThreatCategoryCount(String category, String subCategory, int count, String url) {
    this.category = category;
    this.subCategory = subCategory;
    this.count = count;
    this.url = url;
  }

  public String getCategory() {
    return category;
  }

  public void setCategory(String category) {
    this.category = category;
  }

  public String getSubCategory() {
    return subCategory;
  }

  public void setSubCategory(String subCategory) {
    this.subCategory = subCategory;
  }

  public int getCount() {
    return count;
  }

  public void setCount(int count) {
    this.count = count;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }
}
