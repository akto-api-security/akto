<template>
    <div>
        <div v-if="tabularDisplay">
            <layout-with-tabs :tabs="['Request', 'Response']" class="req-resp-tab" :disableHash="!!disableHash">
                <template slot="Request">
                    <sample-single-side
                        :title="requestTitle" 
                        :firstLine='requestFirstLine'
                        :firstLineToolTipValue="requestFirstLineToolTipValue"
                        :headers="{}" 
                        :data="requestJson"
                        :complete-data="json['message']"
                        :simpleCopy="false"
                        style="margin: 8px"
                    />
                    <div class="d-flex jc-end" :style="{'margin' :'24px 36px 0 0'}">
                        <v-btn 
                            class="run-btn" primary dense dark color="var(--themeColor)" 
                            @click="$emit('run_tests')" 
                            :disabled="isLoading"
                            :style="isLoading ? {'background' : 'var(--themeColor) !important', color: 'var(--white) !important'} : {}"
                        >
                            <spinner v-if="isLoading" :style="{'color' : 'white !important' , 'margin-right' : '8px'}"/>
                            <v-icon v-else size=16>$fas_play</v-icon>
                            Run Test
                        </v-btn>
                    </div>
                </template>
                <template slot="Response">
                    <sample-single-side               
                        :title="responseTitle" 
                        :firstLine='responseFirstLine' 
                        :headers="{}" 
                        :data="responseJson"
                        :simpleCopy="true"
                        :complete-data="json['message']"
                        style="margin: 8px"
                    />
                    <div class="d-flex jc-end" :style="{'margin' :'24px 36px 0 0'}">
                        <v-btn 
                            class="run-btn" primary dense dark color="var(--themeColor)" 
                            @click="$emit('run_tests')" 
                            :disabled="isLoading"
                            :style="isLoading ? {'background' : 'var(--themeColor) !important', color: 'var(--white) !important'} : {}"
                        >
                            <spinner v-if="isLoading" :style="{'color' : 'white !important' , 'margin-right' : '8px'}"/>
                            <v-icon v-else size=16>$fas_play</v-icon>
                            Run Test
                        </v-btn>
                    </div>
                </template>
            </layout-with-tabs>
        </div>
        <div class="d-flex" v-else>
        <div class="flex-equal">
            <sample-single-side
                :title="requestTitle"
                :firstLine='requestFirstLine'
                :firstLineToolTipValue="requestFirstLineToolTipValue"
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
</div>
</template>

<script>
import obj from "@/util/obj"
import api from "../api"

import SampleSingleSide from './SampleSingleSide'
import LayoutWithTabs from '../../layouts/LayoutWithTabs.vue'
import Spinner from "./Spinner.vue"

export default {
    name: "SampleData",
    components: {
        SampleSingleSide,
        LayoutWithTabs,
        Spinner
    },
    props: {
        json: obj.objR,
        requestTitle: obj.ObjR,
        responseTitle: obj.ObjR,
        tabularDisplay: obj.boolN,
        isLoading: obj.boolN,
        disableHash: obj.boolN
    },
    computed: {
        requestFirstLine: function() {
            let message = this.json["message"]
            if (message["request"]) {
                let url = message["request"]["url"]
                return message["request"]["method"] + " " + url + " " + message["request"]["type"]
            } else {
                return message.method + " " + message.path.split("?")[0] + " " + message.type
            }
        },
        requestFirstLineToolTipValue: function() {
            let result = ""
            for (const x of this.json["highlightPaths"]) {
              if (x["isUrlParam"]) {
                if (result) result += ", "
                result += "position " + x["param"] + ": " + x["highlightValue"]["value"]
              }
            }
            return result
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
            let queryParamsString = ""
            if (message["request"]) {
                queryParamsString = message["request"]["queryParams"]
                requestHeadersString = message["request"]["headers"] || "{}"
                requestPayloadString = message["request"]["body"] || "{}"
            } else {
                let url = message["path"]
                let urlSplit = url.split("?")
                queryParamsString = urlSplit.length > 1 ? urlSplit[1] : ""

                requestHeadersString = message["requestHeaders"] || "{}"
                requestPayloadString = message["requestPayload"] || "{}"
            }

            const queryParams = {}
            if (queryParamsString) {
                let urlSearchParams = new URLSearchParams(queryParamsString)
                for(const [key, value] of urlSearchParams) { 
                    queryParams[key] = value;
                }
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
                requestPayload = requestPayloadString
            }

            result["json"] = {"queryParams": queryParams, "requestHeaders": requestHeaders, "requestPayload": requestPayload}
            result["highlightPaths"] = {}
            for (const x of this.json["highlightPaths"]) {
              if (x["responseCode"] === -1) {
                    let keys = []
                    if (x["header"]) {
                        keys.push("root#"+"requestheaders#"+x["param"])
                    } else {
                        keys.push("root#"+"requestpayload#"+x["param"])
                        keys.push("root#"+"queryParams#"+x["param"])
                    }

                    keys.forEach((key) => {
                        key = key.toLowerCase()
                        result["highlightPaths"][key] = x["highlightValue"]
                    })
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
                responsePayload = responsePayloadString
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
<style scoped>
.flex-equal{
    width: 50%;
}
.req-resp-tab >>> .active-tab{
    color: var(--themeColor) !important;
}
.req-resp-tab >>> .control-padding{
    padding-top: 8px !important;
}
.run-btn {
    box-shadow: none !important;
    display: flex;
    width: 120px;
    align-items: center;
    gap: 8px;
}

</style>