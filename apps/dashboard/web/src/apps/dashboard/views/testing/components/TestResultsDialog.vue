<template>
    <div v-if="messagesBasic && messagesBasic.length > 0">
        <div>
            <div v-if="messagesBasic.length > 1" class="d-flex jc-sb mr-3">
                <div v-if="jsonBasic.title" style="margin: auto 8px; color: #47466A">{{jsonBasic.title}}</div>
                
                <v-btn icon @click="nextClicked">
                    <v-icon>$fas_angle-double-right</v-icon>
                </v-btn>
            </div>
            <div v-if="jsonBasic['message']">
                <layout-with-tabs title="" :tabs="['Attempt', 'Details']" ref="layoutWithTabs">
                    <template slot="Attempt">
                        <sample-data :json="jsonBasic" requestTitle="Test Request" responseTitle="Test Response"/>
                    </template>
                    <template slot="Details">
                        <test-result-details :jsonAdvance="jsonAdvance" :percentageMatch="percentageMatch"/>
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
    testingRunResult: obj.arrR
  },
  data() {
    return  {
        currentIndex: 0
    }
  },
  methods: {
    nextClicked() {
        this.currentIndex = (++this.currentIndex) % this.messagesBasic.length
        this.$refs.layoutWithTabs.reset()
    }
  },
  computed: {
    messagesBasic() {
        return this.testingRunResult.map(x => {return {message: x[1].message, title: x[0], highlightPaths:[], errors: x[1].errors}}) 
    },
    messagesAdvance() {
        return this.testingRunResult.map(x => {return {message: x[1].originalMessage, title: x[0], highlightPaths:[], errors: x[1].errors, percentageMatch: x[1].percentageMatch}}) 
    },
    jsonBasic: function() {
        if (this.testingRunResult == null) return null
        let currentMessage = this.messagesBasic[this.currentIndex]
        return {
            "message": JSON.parse(currentMessage["message"]),
            title: currentMessage["title"],
            "highlightPaths": currentMessage["highlightPaths"],
        }
    },
    percentageMatch: function() {
        if (this.testingRunResult == null) return null
        let currentMessage = this.messagesAdvance[this.currentIndex]
        return func.prettifyShort(currentMessage["percentageMatch"])
    },
    jsonAdvance: function() {
        if (this.testingRunResult == null) return null
        let currentMessage = this.messagesAdvance[this.currentIndex]
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
</style>

<style scoped>
</style>
