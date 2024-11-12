package com.akto.malicious_request.notifier;

import com.akto.malicious_request.Request;

public interface MaliciousRequestsNotifier {

    // This method is used to notify the system about a malicious request
    // Notification can be of any type, like email, SMS, logging it somewhere
    // or save it to a database
    void notifyRequest(Request request);
}
