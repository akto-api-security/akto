<template>
    <div class="pa-4 automation-container">
        <div>
            <v-btn primary plain color="var(--themeColor)" @click='fetchPreviousLogs' >
                Previous
            </v-btn>
            <div class="log-content">
                <div v-for="(line, index) in modifiedTestingLogs" :key="index">{{line}}</div>
            </div>

            <div style="font-weight: bold; font-size: 14px;">
                Logs Fetched Between {{logsFetchBetween}}
            </div>
            <v-btn primary plain color="var(--themeColor)" @click='fetchNextLogs' >
                Next
            </v-btn>

        </div>  
    </div>
</template>

<script>

import api from './api'
import func from "@/util/func"

export default {
    name: "LogFetch",
    components: {
    },
    props: {
    },
    data() {
        
        return {
            logFetchStartTime: -1,
            testingLogs: [],
            modifiedTestingLogs: [],
            fiveMins: 1000 * 60 * 5
        }
    },
    methods: {
        fetchPreviousLogs() {
            if (this.logFetchStartTime == -1) {
                this.logFetchStartTime = Date.now();
            }
            this.logFetchStartTime = this.logFetchStartTime - this.fiveMins;
            var logFetchEndTime = this.logFetchStartTime + this.fiveMins;
            this.fetchLogs(this.logFetchStartTime, logFetchEndTime)
        },
        fetchNextLogs() {
            if (this.logFetchStartTime == -1) {
                this.logFetchStartTime = Date.now();
            }
            if (this.logFetchStartTime + this.fiveMins < Date.now()) {
                this.logFetchStartTime = this.logFetchStartTime + this.fiveMins;
            }
            var logFetchEndTime = this.logFetchStartTime + this.fiveMins;
            this.fetchLogs(this.logFetchStartTime, logFetchEndTime)
        },
        fetchLogs(startTime, endTime) {
            startTime = Math.floor(startTime / 1000)
            endTime = Math.floor(endTime / 1000)
            api.fetchTestingLogs(startTime, endTime).then((resp) => {
                this.testingLogs = resp.testingLogs;
                this.modifiedTestingLogs = [];
                for (var i = 0; i < resp.testingLogs.length; i++) {
                    var timeStamp = resp.testingLogs[i].timestamp;
                    var d1 = func.epochToDateTime(timeStamp)
                    var log = resp.testingLogs[i].log;
                    this.modifiedTestingLogs.push("[" + d1 + "]" + " " + log);
                }
            })
        }
    },

    mounted () {
        this.logFetchStartTime = Date.now() - this.fiveMins;
        this.fetchLogs(this.logFetchStartTime, Date.now());
    },

    
    computed: {
        logsFetchBetween() {
            var d1 = func.epochToDateTime(Math.floor(this.logFetchStartTime/1000))
            var d2 = func.epochToDateTime(Math.floor((this.logFetchStartTime + this.fiveMins)/1000))
            return " " + d1 + " - " + d2
        }
    }
}
</script>

<style scoped>

.log-content {
    font-size: 14px;
    color: var(--themeColorDark);
    width: 100%;
    height: 100%;
    font-family: monospace;
    background: var(--white);
}

</style>