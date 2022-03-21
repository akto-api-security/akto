<template>
    <div v-if="messages && messages.length > 0">
        <div>
            <div v-if="messages.length > 1" class="d-flex jc-end mr-3">
                <v-btn icon @click="currentIndex = (++currentIndex)%messages.length">
                    <v-icon>$fas_angle-double-right</v-icon>
                </v-btn>
            </div>
            <div class="d-flex">
                <sample-single-side
                    class="flex-equal"
                    title="Request" 
                    :firstLine='json["message"].method + " " + json["message"].path + " " + json["message"].type'
                    :headers="{}" 
                    :data="requestJson"
                    :complete-data="json['message']"
                    :simpleCopy="false"
                />
                <sample-single-side 
                    class="flex-equal"                
                    title="Response" 
                    :firstLine='json["message"].statusCode + " " + json["message"].status' 
                    :headers="{}" 
                    :data="responseJson"
                    :simpleCopy="true"
                    :complete-data="json['message']"
                />
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
        requestJson: function() {
            let result = {}
            result["json"] = {"requestHeaders":JSON.parse(this.json["message"]["requestHeaders"] || "{}"), "requestPayload": JSON.parse(this.json["message"]["requestPayload"] || "{}")}
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
                    result["highlightPaths"][key] = x["subType"]
              }
            }
            return result
        },
        responseJson: function() {
            let result = {}
            result["json"] = {"responseHeaders":JSON.parse(this.json["message"]["responseHeaders"] || "{}"), "responsePayload": JSON.parse(this.json["message"]["responsePayload"] || "{}")}
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
                    result["highlightPaths"][key] = x["subType"]
                }
            }
            return result
        },

        json: function() {
            return {
                "message": JSON.parse(this.messages[this.currentIndex]["message"]),
                "highlightPaths": this.messages[this.currentIndex]["highlightPaths"]
                }
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