package com.akto.action.gpt.data_extractors.filters;

import java.util.List;

public interface Filter<T> {

    public List<T> filterData(List<T> data);
}
