package models

// IngestDataBatch represents the batch data received from traffic sources
// Similar to IngestDataBatch.java from mini-runtime-service
type IngestDataBatch struct {
	Path               string   `json:"path"`
	RequestHeaders     string   `json:"requestHeaders"`
	ResponseHeaders    string   `json:"responseHeaders"`
	Method             string   `json:"method"`
	RequestPayload     string   `json:"requestPayload"`
	ResponsePayload    string   `json:"responsePayload"`
	IP                 string   `json:"ip"`
	DestIP             string   `json:"destIp"`
	Time               string   `json:"time"`
	StatusCode         string   `json:"statusCode"`
	Type               string   `json:"type"`
	Status             string   `json:"status"`
	AktoAccountID      string   `json:"akto_account_id"`
	AktoVxlanID        string   `json:"akto_vxlan_id"`
	IsPending          string   `json:"is_pending"`
	Source             string   `json:"source"`
	Direction          string   `json:"direction"`
	ProcessID          string   `json:"process_id"`
	SocketID           string   `json:"socket_id"`
	DaemonsetID        string   `json:"daemonset_id"`
	EnabledGraph       string   `json:"enabled_graph"`
	Tag                string   `json:"tag"`
	ParentMcpToolNames []string `json:"parentMcpToolNames"`
}

// HttpRequestParams represents HTTP request parameters
// Similar to HttpRequestParams.java
type HttpRequestParams struct {
	Method          string              `json:"method"`
	URL             string              `json:"url"`
	Type            string              `json:"type"`
	Headers         map[string][]string `json:"headers"`
	Payload         string              `json:"payload"`
	ApiCollectionID int                 `json:"apiCollectionId"`
}

// HttpResponseParams represents HTTP response parameters
// Similar to HttpResponseParams.java
type HttpResponseParams struct {
	AccountID          string              `json:"accountId"`
	Type               string              `json:"type"`
	StatusCode         int                 `json:"statusCode"`
	Status             string              `json:"status"`
	Headers            map[string][]string `json:"headers"`
	Payload            string              `json:"payload"`
	Time               int                 `json:"time"`
	RequestParams      *HttpRequestParams  `json:"requestParams"`
	IsPending          bool                `json:"isPending"`
	Source             string              `json:"source"`
	Orig               string              `json:"orig"`
	SourceIP           string              `json:"sourceIP"`
	DestIP             string              `json:"destIP"`
	Direction          string              `json:"direction"`
	Tags               string              `json:"tags"`
	ParentMcpToolNames []string            `json:"parentMcpToolNames"`
}

// ValidateRequestParams holds context fields for single request validation
// Matches the IngestDataBatch format sent by traffic sources
type ValidateRequestParams struct {
	Path            string `json:"path,omitempty"`
	RequestHeaders  string `json:"requestHeaders,omitempty"`
	ResponseHeaders string `json:"responseHeaders,omitempty"`
	Method          string `json:"method,omitempty"`
	RequestPayload  string `json:"requestPayload" binding:"required"`
	ResponsePayload string `json:"responsePayload,omitempty"`
	IP              string `json:"ip,omitempty"`
	DestIP          string `json:"destIp,omitempty"`
	Time            string `json:"time,omitempty"`
	StatusCode      string `json:"statusCode,omitempty"`
	Type            string `json:"type,omitempty"`
	Status          string `json:"status,omitempty"`
	AktoAccountID   string `json:"akto_account_id,omitempty"`
	AktoVxlanID     string `json:"akto_vxlan_id,omitempty"`
	IsPending       string `json:"is_pending,omitempty"`
	Source          string `json:"source,omitempty"`
	Direction       string `json:"direction,omitempty"`
	ProcessID       string `json:"process_id,omitempty"`
	SocketID        string `json:"socket_id,omitempty"`
	DaemonsetID     string `json:"daemonset_id,omitempty"`
	EnabledGraph    string `json:"enabled_graph,omitempty"`
	Tag             string `json:"tag,omitempty"`
	Metadata        string `json:"metadata,omitempty"`
	ContextSource   string `json:"contextSource,omitempty"`
}

// ValidationRequest represents the request to validate payloads
type ValidationRequest struct {
	BatchData     []IngestDataBatch `json:"batchData"`
	ContextSource string            `json:"contextSource,omitempty"` // Optional context source for policy filtering
	SkipThreat    *bool             `json:"skipThreat,omitempty"`     // Optional: skip threat reporting to TBS (default: false)
}

// ValidationResponse represents the response from validation
type ValidationResponse struct {
	Success bool     `json:"success"`
	Result  string   `json:"result"`
	Errors  []string `json:"errors,omitempty"`
}
