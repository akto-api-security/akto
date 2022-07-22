<template>
    <div>
        <json-viewer
            v-if="content"
            :contentJSON="content"
            :errors="{}"
        />                    
        <div>
            <div>
                <span class="log-title">Latest logs</span>
                <v-select
                    :items="['akto-dashboard', 'akto-runtime']"
                    v-model= "logGroupName"
                    label="Select log group"
                    solo
                    outlined
                    flat
                    hide-details
                />

                <span>
                    <v-tooltip bottom>
                        <template v-slot:activator='{on, attrs}'>
                            <v-btn 
                                icon 
                                color="#47466A" 
                                @click="refreshLogs(null, true, Date.now())"
                                v-on="on"
                                v-bind="attrs"
                            >
                                    <v-icon>$fas_redo</v-icon>
                            </v-btn>
                        </template>
                        Refresh
                    </v-tooltip>
                </span>                
            </div>
            <div class="log-content">
                {{logContent}}
            </div>   
        </div>
    </div>
</template>

<script>
import api from '../api'
import JsonViewer from "@/apps/dashboard/shared/components/JSONViewer"
export default {
    name: "Health",
    components: {
        JsonViewer
    },
    data() {
        return {
            content: {},
            logGroupName: 'akto-dashboard',
            logContent: '',
        }
    },
    methods: {
        refreshLogs(nextToken, firstCall, startEpoch) {
            if (firstCall) {
                this.logContent = '';
            } else if (!nextToken) {
                return
            }

            let fiveMins = 1000 * 60 * 5
            let startTime = parseInt(startEpoch/fiveMins) * fiveMins
            let endTime = startTime + fiveMins
            let _this = this
            api.fetchLogs(this.logGroupName, startTime, endTime, 1000, "-WARN -AnnotationParser").then(resp => {
                _this.logContent += resp.output
                _this.refreshLogs(resp.nextToken, false, startEpoch)
            }).catch(e => {
                console.log(e)
            })
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
    font-size: 14px
    color: #47466A

.log-content
    font-size: 12px
    color: #47466A
    width: 100%
    height: 100%
    background: rgba(0, 0, 0, 0.2)
</style>