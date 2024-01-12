package com.akto.util;

public class IssueTrendType {

    private int count ;
    private String subCategory ;

    public IssueTrendType(){
    }

    public IssueTrendType(int count, String subCategory){
        this.count = count;
        this.subCategory = subCategory;
    }


    public int getCount() {
        return count;
    }
    public void setCount(int count) {
        this.count = count;
    }
    
    public String getSubCategory() {
        return subCategory;
    }
    public void setSubcategory(String subCategory) {
        this.subCategory = subCategory;
    }
}
