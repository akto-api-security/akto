<template>
    <div>
        <json-viewer
            v-if="content"
            :contentJSON="content"
            :errors="{}"
        />                    
        <div>
            <div class="d-flex mt-5">
                <div class="log-title mr-2">Latest logs</div>
                <div style="width: 200px" class="mr-2">
                    <form-field-menu
                        :items="[{text: 'akto-dashboard', value: 'akto-dashboard'}, {text: 'akto-runtime', value: 'akto-runtime'}]"
                        v-model="logGroupName"
                        label="Select log group"
                    />
                </div>

                <span class="mr-2">
                    <v-tooltip bottom>
                        <template v-slot:activator='{on, attrs}'>
                            <v-btn 
                                icon 
                                color="#47466A" 
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
                                color="#47466A" 
                                @click="previousClick()"
                                v-on="on"
                                v-bind="attrs"
                                :disabled="initTime == -1"
                            >
                                    -5m
                            </v-btn>
                        </template>
                        Previous 5 mins
                    </v-tooltip>
                </span>                
            </div>
            <div class="log-content">
                <div v-for="(line, index) in logContent" :key="index">{{line}}
                </div>
            </div>   
        </div>
    </div>
</template>

<script>
import api from '../api'
import JsonViewer from "@/apps/dashboard/shared/components/JSONViewer"
import FormFieldMenu from "@/apps/dashboard/shared/components/integrations/FormFieldMenu"

export default {
    name: "Health",
    components: {
        JsonViewer,
        FormFieldMenu
    },
    data() {
        return {
            content: {},
            logGroupName: 'akto-dashboard',
            logContent: [],
            initTime: -1,
            fiveMins: 1000 * 60 * 5
        }
    },
    methods: {
        async refreshClick() {
            if (this.initTime == -1) {
                this.initTime = Date.now();
            } 
            this.logContent = await this.refreshLogs(null, true, Date.now(), [])
        },
        async previousClick() {
            this.logContent = (await this.refreshLogs(null, true, this.initTime - this.fiveMins, [])).concat(this.logContent)
            this.initTime = this.initTime - this.fiveMins
        },
        async refreshLogs(nextToken, firstCall, startEpoch, logContent) {
            // console.log("refreshLogs", nextToken, firstCall, startEpoch, logContent)
            if (!firstCall && !nextToken) {
                // console.log("returning", logContent)
                return logContent
            }

            let startTime = parseInt(startEpoch/this.fiveMins) * this.fiveMins
            let endTime = startTime + this.fiveMins
            let _this = this
            let resp = await api.fetchLogs(this.logGroupName, startTime, endTime, 1000, "-WARN -AnnotationParser")
            // console.log("resp", resp)
            logContent = logContent.concat(resp.output.split("\n"))
            logContent = await _this.refreshLogs(resp.nextToken, false, startEpoch, logContent)
            // console.log("final", logContent)
            return logContent
        }
    },
    created() {
        api.health().then(resp => {
            this.content = resp
        })
    }
}
</script>

<style lang="sass" scoped>
.log-title
    font-weight: 500
    font-size: 16px
    color: #47466A

.log-content
    font-size: 12px
    color: #47466A
    width: 100%
    height: 100%
    font-family: monospace
    background: #FFFFFF
</style>