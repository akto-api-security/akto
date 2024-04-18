<template>
    <div>
        <!-- <json-viewer
            v-if="content"
            :contentJSON="content"
            :errors="{}"
        />                     -->
        <div>
            <div class="d-flex mt-5">
                <div class="log-title mr-2">Latest logs</div>
                <div style="width: 200px" class="mr-2">
                    <form-field-menu
                        :items="[
                            {text: 'TESTING', value: 'TESTING'},
                            {text: 'RUNTIME', value: 'RUNTIME'},
                            {text: 'DASHBOARD', value: 'DASHBOARD'},
                            {text: 'ANALYSER', value: 'ANALYSER'}
                        ]"
                        v-model="logGroupName"
                        label="Select log group"
                    />
                </div>

                <span class="mr-2">
                    <v-tooltip bottom>
                        <template v-slot:activator='{on, attrs}'>
                            <v-btn 
                                icon 
                                color="var(--themeColorDark)" 
                                @click="refreshClick()"
                                v-on="on"
                                v-bind="attrs"
                            >
                                    <v-icon>$fas_redo</v-icon>
                            </v-btn>
                        </template>
                        Refresh
                    </v-tooltip>
                </span>                
                <span class="mr-2">
                    <v-tooltip bottom>
                        <template v-slot:activator='{on, attrs}'>
                            <v-btn 
                                icon 
                                color="var(--themeColorDark)" 
                                @click="previousClick()"
                                v-on="on"
                                v-bind="attrs"
                                :disabled="startTime == -1"
                            >
                                    -5m
                            </v-btn>
                        </template>
                        Previous 5 mins
                    </v-tooltip>
                </span>                
                <span>
                    <v-tooltip bottom>
                <template v-slot:activator="{ on, attrs }">
                    <v-btn 
                        v-on="on"
                        v-bind="attrs" 
                        icon color="var(--themeColorDark)" @click="downloadData">
                            <v-icon>$fas_file-csv</v-icon>
                    </v-btn>
                </template>
                Download as CSV
            </v-tooltip>
                </span>
            </div>
            <div class="log-content">
                <div v-for="(line, index) in logContent" :key="index">{{line}}
                </div>
            </div>   
            <div v-if="startTime!=-1">
                    {{logsFetchBetween}}
            </div>
        </div>
    </div>
</template>

<script>
import api from '../api'
import JsonViewer from "@/apps/dashboard/shared/components/JSONViewer"
import func from "@/util/func"
import obj from "@/util/obj"
import FormFieldMenu from "@/apps/dashboard/shared/components/integrations/FormFieldMenu"

export default {
    name: "Health",
    components: {
        JsonViewer,
        FormFieldMenu
    },
    props: {
        defaultStartTimestamp: obj.strN,
        defaultEndTimestamp: obj.strN
    },
    data() {
        return {
            content: {},
            logGroupName: 'TESTING',
            logContent: [],
            logs: [],
            startTime: (this.defaultStartTimestamp*1000) || -1,
            endTime: (this.defaultEndTimestamp*1000) || -1,
            fiveMins: 1000 * 60 * 5        
        }
    },
    methods: {
        async refreshClick() {
            this.startTime = Date.now()-this.fiveMins;
            this.endTime = Date.now();
            this.logContent = [];
            await this.refreshLogs(this.startTime, this.endTime);
        },
        async previousClick() {
            this.endTime = this.startTime
            this.startTime = this.startTime - this.fiveMins
            await this.refreshLogs(this.startTime, this.endTime);
        },
        async refreshLogs(startTime, endTime) {
            startTime = Math.floor(startTime / 1000);
            endTime = Math.floor(endTime / 1000)
            await api.fetchLogsFromDb(startTime, endTime, this.logGroupName).then((resp) => {
                this.logs.push(...resp.logs);
                for (var i = 0; i < resp.logs.length; i++) {
                    var timeStamp = resp.logs[i].timestamp;
                    var d1 = func.epochToDateTime(timeStamp)
                    var log = resp.logs[i].log;
                    this.logContent.push("[" + d1 + "]" + " " + log);
                }
            })
        },
        downloadData() {
            let headers = ['timestamp', 'log'];
            let csv = headers.join(",")+"\r\n"
            this.logs.forEach(log => {
                csv += func.epochToDateTime(log.timestamp) +","+ log.log + "\r\n"
            })
            let blob = new Blob([csv], {
                type: "application/csvcharset=UTF-8"
            });
            saveAs(blob, (this.name || "file") + ".csv");
        }
    },
    created() {
        api.health().then(resp => {
            this.content = resp
        })
    },
    computed: {
        logsFetchBetween() {
            var d1 = func.epochToDateTime(Math.floor(this.startTime/1000))
            var d2 = func.epochToDateTime(Math.floor((this.endTime)/1000))
            return "Fetched logs from " + d1 + " to " + d2;
        }
    },
    async mounted () {
        console.log(this.startTime);
        if(this.startTime!=-1 && this.endTime!=-1){
            await this.refreshLogs(this.startTime,this.endTime);
        }
    },
}
</script>

<style lang="sass" scoped>
.log-title
    font-weight: 500
    font-size: 16px
    color: var(--themeColorDark)

.log-content
    font-size: 12px
    color: var(--themeColorDark)
    width: 100%
    height: 100%
    font-family: monospace
    background: var(--white)
</style>