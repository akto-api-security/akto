<template>
    <div v-if="content">

        <v-row no-gutters>
            <v-col sm="12" md="4">
                <a-card 
                    title="Data errors" 
                    icon="$fas_clipboard-list" 
                    :subtitle="getAuditTs(auditId)"
                    color="var(--rgbaColor1)"
                >        
                    <div class="height-1">
                        <div><span>{{getCriticalDataErrors()}}</span> <span>critical</span> </div>
                        <div><span>{{getOtherDataErrors()}}</span> <span>other</span> </div>
                    </div>    
                </a-card>   
            </v-col>
            <v-col sm="12" md="4">
                <a-card 
                    title="Security errors" 
                    icon="$fas_unlock-alt" 
                    :subtitle="getAuditTs(auditId)"
                    color="var(--rgbaColor17)"
                >        
                    <div class="height-1">
                        <div><span>{{getCriticalSecurityErrors()}}</span> <span>critical</span> </div>
                        <div><span>{{getOtherSecurityErrors()}}</span> <span>other</span> </div>
                    </div>    
                </a-card>   
            </v-col>
            <v-col sm="12" md="4">
                <a-card 
                    title="Test report" 
                    icon="$fas_stethoscope" 
                    :subtitle="testDetails ? getAuditTs(testDetails.id) : ''"
                    color="var(--rgbaColor2)"
                >        
                    <div class="height-1">
                        <div v-if="testDetails && testDetails.attempts">
                            <v-progress-linear class="mb-4" :value="progress" color="var(--themeColor)EA"></v-progress-linear>
                            <div> {{failedTests}} failed</div>
                            <div> {{testDetails.attempts.length}} attempted</div>
                        </div>
                        <div v-else>
                            <v-btn block dark @click="showTestingSetupModal" class="testing-setup-btn">
                                <div class="info-text">Configure</div>
                            </v-btn>
                        </div>
                    </div>    
                </a-card>   
            </v-col>
        </v-row>

        <div class="pa-3">
            <div class="heading-key">Endpoint</div>
            <div class="heading-value">{{endpoint}}</div>    

            <div class="heading-key">Description</div>
            <div class="heading-value">{{description}}</div>    

            <div class="heading-key">Paths</div>
            <div class="heading-value">{{numberOfPaths}}</div>    

            <div class="heading-key">Authentication Protocol</div>
            <div class="heading-value">{{Object.values(authProtocol || {}).map(x => x.type).join(', ')}}</div>    
        </div>   

        <v-dialog v-model="testingSetupModal" content-class="test-setup-dialog">
            <testing-setup-modal @close="close">
            </testing-setup-modal> 
        </v-dialog>
    </div>    
</template>

<script>
    import { mapState } from "vuex"
    import ACard from "@/apps/dashboard/shared/components/ACard"
    import TestingSetupModal from "./TestingSetupModal"
    import func from "@/util/func"

    export default {
        components: { 
            ACard,
            TestingSetupModal
        },
        name: "APISummary",
        data () {
            return {
                testingSetupModal: false,
                polling: null
            }
        },
        methods: {
            close ({poll}) {
                this.testingSetupModal = false

                if (!poll) return

                if (this.polling) {
                    clearInterval(this.polling)
                }

                this.polling = setInterval(() => {
                    this.$store.dispatch('today/fetchTestResults')
                }, 2000)
            },
            getAuditTs(ts) {
                return func.prettifyEpoch(ts)
            },
            showTestingSetupModal() {
                this.testingSetupModal = true
            },
            getFilterErrors(type, critical) {
                return Object.values(this.errors || []).flat().filter(x => x.type === type && (critical ? x.severity === 'High' : x.severity !== 'High') ).length
            },
            getCriticalSecurityErrors () {
                return this.getFilterErrors('SECURITY', true)
            },
            getOtherSecurityErrors () {
                return this.getFilterErrors('SECURITY', false)
            },
            getCriticalDataErrors () {
                return this.getFilterErrors('DATA_VALIDATION', true)

            },
            getOtherDataErrors () {
                return this.getFilterErrors('DATA_VALIDATION', false)

            }
        },
        computed: {
            ...mapState('today', ['contentId', 'content', 'auditId', 'errors', 'endpoint', 'description', 'numberOfPaths', 'authProtocol', 'testDetails', 'testResults']),
            progress () {
                if (this.testResults && Object.keys(this.testResults).length > 0) {
                    let ret = Math.round(100*(this.testResults.filter(x => x.attemptResult.errorCode > 0).length)/this.testResults.length)
                    if (ret > 99) {
                        clearInterval (this.polling)
                    }
                    return ret
                } else {
                    return 0
                }
            },
            failedTests () {
                if (this.testResults && Object.keys(this.testResults).length > 0) {
                    let happyFailed = this.testResults.filter(x => x.isHappy && x.attemptResult.errorCode > 299).length
                    let restPass = this.testResults.filter(x => !x.isHappy && x.attemptResult.errorCode < 300 && x.attemptResult.errorCode > 0).length
                    return happyFailed + restPass
                } else {
                    return 0
                }

            }
        }
    }
</script>


<style scoped lang="sass">
.heading-key
    color: var(--themeColorDark)
    font-weight: 600
    font-size: 13px

.heading-value
    color: var(--themeColorDark4)
    font-weight: 500
    font-size: 13px   
    margin-bottom: 24px 

.testing-setup-btn
    background-color: var(--themeColor)EA !important
    & .info-text
        color: var(--white)

.height-1
    height: 200px    
    padding: 16px
    display: flex
    flex-direction: column
    justify-content: center

</style>

<style lang="sass">
.test-setup-dialog
    box-shadow: unset !important
    display: flex
    flex-direction: row
    justify-content: center
</style>