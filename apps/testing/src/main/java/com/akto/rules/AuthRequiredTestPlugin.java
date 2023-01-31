package com.akto.rules;

import com.akto.dto.ApiInfo;
import com.akto.dto.RawApi;
import com.akto.store.SampleMessageStore;
import com.akto.store.TestingUtil;

import java.util.List;

public abstract class AuthRequiredTestPlugin extends TestPlugin {

    @Override
    public Result start(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil) {
        List<RawApi> messages = SampleMessageStore.fetchAllOriginalMessages(apiInfoKey, testingUtil.getSampleMessages());
        if (messages.isEmpty()) return null;
        List<RawApi> filteredMessages = SampleMessageStore.filterMessagesWithAuthToken(messages, testingUtil.getAuthMechanism());
        if (filteredMessages.isEmpty()) return null;

        return exec(apiInfoKey, testingUtil, filteredMessages);
    }

    public abstract Result exec(ApiInfo.ApiInfoKey apiInfoKey, TestingUtil testingUtil, List<RawApi> filteredMessages);

}
