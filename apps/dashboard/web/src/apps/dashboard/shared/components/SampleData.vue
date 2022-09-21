<template>
    <div class="d-flex" style="margin: 24px">
        <div class="flex-equal" >
            <sample-single-side
                :title="requestTitle" 
                :firstLine='requestFirstLine'
                :headers="{}" 
                :data="requestJson"
                :complete-data="json['message']"
                :simpleCopy="false"
                style="margin: 8px 4px 0px 0px"
            />
        </div>
        <div class="flex-equal" >
            <sample-single-side               
                :title="responseTitle" 
                :firstLine='responseFirstLine' 
                :headers="{}" 
                :data="responseJson"
                :simpleCopy="true"
                :complete-data="json['message']"
                style="margin: 8px 0px 0px 4px"
            />
        </div>
    </div>
</template>

<script>
import obj from "@/util/obj"

import SampleSingleSide from './SampleSingleSide'

export default {
    name: "SampleData",
    components: {
        SampleSingleSide
    },
    props: {
        json: obj.objR,
        requestTitle: obj.ObjR,
        responseTitle: obj.ObjR
    },
    data () {
        return {
        }
    },
    computed: {
        requestFirstLine: function() {
            let message = this.json["message"]
            if (message["request"]) {
                let url = message["request"]["url"]
                let queryParams = message["request"]['queryParams']
                if (queryParams) {
                    url += "?" + queryParams
                }
                return message["request"]["method"] + " " + url + " " + message["request"]["type"]
            } else {
                return message.method + " " + message.path + " " + message.type
            }
        },
        responseFirstLine: function() {
            let message = this.json["message"]
            if (message["response"]) {
                return message["response"]["statusCode"] + ""
            } else {
                return message.statusCode + " " + message.status
            }
        },
        requestJson: function() {
            let result = {}
            let requestHeaders = {}
            let message = this.json["message"]

            let requestHeadersString = "{}"
            let requestPayloadString = "{}"
            if (message["request"]) {
                requestHeadersString = message["request"]["headers"] || "{}"
                requestPayloadString = message["request"]["body"] || "{}"
            } else {
                requestHeadersString = message["requestHeaders"] || "{}"
                requestPayloadString = message["requestPayload"] || "{}"
            }

            try {
              requestHeaders = JSON.parse(requestHeadersString)
            } catch (e) {
              // eat it
            }

            let requestPayload = {}
            try {
                requestPayload = JSON.parse(requestPayloadString)
            } catch (e) {
                // eat it
            }

            result["json"] = {"requestHeaders": requestHeaders, "requestPayload": requestPayload}
            result["highlightPaths"] = {}
            for (const x of this.json["highlightPaths"]) {
              if (x["responseCode"] === -1) {
                    let key = ""
                    if (x["header"]) {
                        key = "root#"+"requestheaders#"+x["param"]
                    } else {
                        key = "root#"+"requestpayload#"+x["param"]
                    }

                    key = key.toLowerCase()
                    result["highlightPaths"][key] = x["highlightValue"]
              }
            }
            return result
        },
        responseJson: function() {
            let result = {}
            let message = this.json["message"]

            let responseHeadersString = "{}"
            let responsePayloadString = "{}"
            if (message["request"]) {
                responseHeadersString = message["response"]["headers"] || "{}"
                responsePayloadString = message["response"]["body"] || "{}"
            } else {
                responseHeadersString = message["responseHeaders"] || "{}"
                responsePayloadString = message["responsePayload"] || "{}"
            }

            let responseHeaders = {};
            try {
              responseHeaders = JSON.parse(responseHeadersString)
            } catch (e) {
              // eat it
            }
            let responsePayload = {}
            try {
              responsePayload = JSON.parse(responsePayloadString)
            } catch (e) {
              // eat it
            }
            result["json"] = {"responseHeaders": responseHeaders, "responsePayload": responsePayload}
            result["highlightPaths"] = {}
            for (const x of this.json["highlightPaths"]) {
                if (x["responseCode"] !== -1) {
                    let key = ""
                    if (x["header"]) {
                        key = "root#"+"responseheaders#"+x["param"]
                    } else {
                        key = "root#"+"responsepayload#"+x["param"];
                    }
                    key = key.toLowerCase();
                    result["highlightPaths"][key] = x["highlightValue"]
                }
            }
            return result
        },

    }
}
</script>

<style lang="sass" scoped>
.flex-equal
    width: 50%

</style>