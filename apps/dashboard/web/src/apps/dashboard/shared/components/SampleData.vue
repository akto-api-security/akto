<template>
    <div v-if="messages && messages.length > 0">
        <div>
            <div v-if="messages.length > 1" class="d-flex jc-sb mr-3">
                <div v-if="json.title" style="margin: auto 8px; color: #47466A">{{json.title}}</div>
                
                <v-btn icon @click="currentIndex = (++currentIndex)%messages.length">
                    <v-icon>$fas_angle-double-right</v-icon>
                </v-btn>
            </div>
            <div class="d-flex" v-if="json['message']">
                <sample-single-side
                    class="flex-equal"
                    title="Request" 
                    :firstLine='requestFirstLine'
                    :headers="{}" 
                    :data="requestJson"
                    :complete-data="json['message']"
                    :simpleCopy="false"
                />
                <sample-single-side 
                    class="flex-equal"                
                    title="Response" 
                    :firstLine='responseFirstLine' 
                    :headers="{}" 
                    :data="responseJson"
                    :simpleCopy="true"
                    :complete-data="json['message']"
                />
            </div>
            <div v-else style="font-size: 13px; margin: auto 8px; color: #47466A">
                {{testResultErrors}}
            </div>
        </div>
    </div>
    <div v-else class="empty-container">
        No samples values saved yet!
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
        messages: obj.arrR
    },
    data () {
        return {
            currentIndex: 0
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
                responsePayloadString = message["responseHeaders"] || "{}"
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

        json: function() {
            let currentMessage = this.messages[this.currentIndex]
            return {
                "message": JSON.parse(currentMessage["message"]),
                title: currentMessage["title"],
                "highlightPaths": currentMessage["highlightPaths"],
            }
        },

        testResultErrors: function() {
            let currentMessage = this.messages[this.currentIndex]
            let errors = currentMessage["errors"]
            if (!errors) return ""

            return "Error while executing the test: " + errors.join(" ")
        }
    }
}
</script>

<style lang="sass" scoped>
.flex-equal
    width: 50%

.empty-container
    font-size: 13px
    color: #47466A
    margin: 8px 8px 0 0 

</style>