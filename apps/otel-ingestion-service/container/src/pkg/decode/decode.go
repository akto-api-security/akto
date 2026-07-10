package decode

import (
	"encoding/json"
	"fmt"
	"strings"

	"go.opentelemetry.io/collector/pdata/plog"
	"go.opentelemetry.io/collector/pdata/plog/plogotlp"
)

func DecodeLogs(contentType string, body []byte) (plog.Logs, error) {
	req := plogotlp.NewExportRequest()
	ct := strings.ToLower(strings.TrimSpace(contentType))

	switch {
	case strings.Contains(ct, "json"):
		if err := req.UnmarshalJSON(body); err != nil {
			return plog.NewLogs(), fmt.Errorf("unmarshal json: %w", err)
		}
		logs := req.Logs()
		patchJSONEventNames(body, logs)
		return logs, nil
	case strings.Contains(ct, "protobuf"), strings.Contains(ct, "x-protobuf"), ct == "":
		if err := req.UnmarshalProto(body); err != nil {
			return plog.NewLogs(), fmt.Errorf("unmarshal protobuf: %w", err)
		}
	default:
		if err := req.UnmarshalProto(body); err != nil {
			if errJSON := req.UnmarshalJSON(body); errJSON != nil {
				return plog.NewLogs(), fmt.Errorf("unmarshal protobuf: %w", err)
			}
			logs := req.Logs()
			patchJSONEventNames(body, logs)
			return logs, nil
		}
	}
	return req.Logs(), nil
}

// pdata JSON unmarshaling drops log record eventName (Cowork sends eventName per
// https://claude.com/docs/cowork/monitoring). Patch from raw JSON when missing.
func patchJSONEventNames(body []byte, logs plog.Logs) {
	var parsed struct {
		ResourceLogs []struct {
			ScopeLogs []struct {
				LogRecords []struct {
					EventName string `json:"eventName"`
				} `json:"logRecords"`
			} `json:"scopeLogs"`
		} `json:"resourceLogs"`
	}
	if err := json.Unmarshal(body, &parsed); err != nil {
		return
	}

	for i := 0; i < logs.ResourceLogs().Len() && i < len(parsed.ResourceLogs); i++ {
		rl := logs.ResourceLogs().At(i)
		scopeParsed := parsed.ResourceLogs[i].ScopeLogs
		for j := 0; j < rl.ScopeLogs().Len() && j < len(scopeParsed); j++ {
			sl := rl.ScopeLogs().At(j)
			recsParsed := scopeParsed[j].LogRecords
			for k := 0; k < sl.LogRecords().Len() && k < len(recsParsed); k++ {
				name := recsParsed[k].EventName
				if name == "" {
					continue
				}
				rec := sl.LogRecords().At(k)
				if rec.EventName() == "" {
					rec.SetEventName(name)
				}
			}
		}
	}
}
