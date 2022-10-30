<template>
    <div v-if="messagesBasic && messagesBasic.length > 0">
        <div>
            <div class="d-flex jc-sb mr-3">
                <div v-if="jsonBasic.title" style="margin: auto 8px; color: #47466A">{{jsonBasic.title}}</div>
                <v-btn v-if="messagesBasic.length > 1" icon @click="nextClicked">
                    <v-icon>$fas_angle-double-right</v-icon>
                </v-btn>
            </div>
            <div>
                <layout-with-tabs title="" :tabs="['Attempt', 'Details']" ref="layoutWithTabs">
                    <template slot="Attempt">
                        <div>
                            <div v-if="jsonBasic['errors']" class="test-errors-class">
                                {{this.jsonBasic["errors"]}}
                            </div>
                            <div style="margin: 24px">
                                <sample-data 
                                    v-if="jsonBasic && jsonBasic['message']"
                                    :json="jsonBasic"
                                    requestTitle="Test Request"
                                    responseTitle="Test Response"
                                />
                            </div>
                        </div>
                    </template>
                    <template slot="Details" v-if="jsonAdvance && jsonAdvance['message']">
                        <div style="margin: 24px">
                            <span>Test response matches {{percentageMatch}}% with original API response</span>
                            <sample-data :json="jsonAdvance" requestTitle="Original Request" responseTitle="Original Response"/>
                        </div>
                    </template>
                </layout-with-tabs>
            </div>
        </div>
    </div>
    <div v-else class="empty-container">
        No samples values saved yet!
    </div>
</template>

<script>
import obj from "@/util/obj";
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import SampleData from "../../../shared/components/SampleData";
import TestResultDetails from "./TestResultDetails";
import func from "@/util/func";

export default {
  name: "TestResultsDialog",
  components: {
    SampleData,
    LayoutWithTabs,
    TestResultDetails
},
  props: {
    testingRunResult: obj.objR
  },
  data() {
    return  {
        currentIndex: 0
    }
  },
  watch: {
    testingRunResult(n,o) {
        this.currentIndex = 0
    }
  },
  methods: {
    nextClicked() {
        this.currentIndex = (++this.currentIndex) % this.messagesBasic.length
        this.$refs.layoutWithTabs.reset()
    },
    buildHighlightPaths(paramInfoList) {
        if (!paramInfoList) paramInfoList = []
        let highlightPaths = paramInfoList.map((x) => {
            let asterisk = x.isPrivate
            x["highlightValue"] = {
                "value": "unique: " + x.uniqueCount + " public: " + x.publicCount,
                "asterisk": asterisk,
                "highlight": false
            }
            return x
        })

        return highlightPaths
    }
  },
  computed: {
    messagesBasic() {
        let testSubType = this.testingRunResult["testSubType"]
        return this.testingRunResult["testResults"].map(x => {return {message: x.message, title: testSubType, highlightPaths:[], errors: x.errors}}) 
    },
    messagesAdvance() {
        let testSubType = this.testingRunResult["testSubType"]
        let singleTypeInfos = this.testingRunResult["singleTypeInfos"]
        let highlightPaths = this.buildHighlightPaths(singleTypeInfos);
        return this.testingRunResult["testResults"].map(x => {return {message: x.originalMessage, title: testSubType, highlightPaths: highlightPaths, errors: x.errors, percentageMatch: x.percentageMatch}}) 
    },
    jsonBasic: function() {
        if (this.testingRunResult == null) return null
        let currentMessage = this.messagesBasic[this.currentIndex]
        return {
            "message": JSON.parse(currentMessage["message"]),
            title: currentMessage["title"],
            "highlightPaths": currentMessage["highlightPaths"],
            "errors": currentMessage["errors"].map(x => x.message).join(", ")
        }
    },
    percentageMatch: function() {
        if (this.testingRunResult == null) return null
        let currentMessage = this.messagesAdvance[this.currentIndex]
        try {
            return func.prettifyShort(currentMessage["percentageMatch"])
        } catch (e) {
            console.log(e);
            return null
        }
    },
    jsonAdvance: function() {
        if (this.testingRunResult == null) return null
        let currentMessage = this.messagesAdvance[this.currentIndex]
        if (!currentMessage) return null
        return {
            "message": JSON.parse(currentMessage["message"]),
            title: currentMessage["title"],
            "highlightPaths": currentMessage["highlightPaths"],
        }
    }
  }
}
</script>

<style lang="sass" scoped>
.test-errors-class
  padding: 24px 0px 0px 24px
</style>

<style scoped>
</style>
