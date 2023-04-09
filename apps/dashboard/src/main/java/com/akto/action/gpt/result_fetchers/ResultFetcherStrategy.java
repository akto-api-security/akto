package com.akto.action.gpt.result_fetchers;


public interface ResultFetcherStrategy<T> {

    T fetchResult(T data);
}
