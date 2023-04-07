<template>
     <div class="pa-8">
        <slot name="stop-all-tests" />
        <div>
            <div class="di-flex-bottom">
                <div class="col_1">
                    <p> 1 </p>
                </div>
                <div>
                    <h3> Inject hard-coded attacker auth token </h3>
                </div>
            </div>
            <div class="d-flex">
                <div class="input-value">
                    <v-text-field v-model="newKey" style="width: 200px">
                        <template slot="label">
                            <div class="d-flex">
                                Auth header key
                                <help-tooltip :size="12"
                                    text="Please enter name of the header which contains your auth token. This field is case-sensitive. eg Authorization" />
                            </div>
                        </template>
                    </v-text-field>

                </div>
                <div class="input-value">
                    <v-text-field v-model="newVal" style="width: 500px">
                        <template slot="label">
                            <div class="d-flex">
                                Auth header value
                                <help-tooltip :size="12" text="Please enter the value of the auth token." />
                            </div>
                        </template>

                    </v-text-field>
                </div>

                <v-btn primary dark color="var(--hexColor9)" @click="saveAuthMechanism" v-if="someAuthChanged">
                    Save changes
                </v-btn>
            </div>

            <div class="di-flex-bottom">
                <div class="col_1">
                    <p> 2 </p>
                </div>
                
                <div>
                    <h3> Automate attacker auth token generation </h3>
                </div>
            </div>
            
            <div class="di-flex">
                <div class="input-value">
                    <div  v-if="authTokenUrl != null && authTokenDate != null">
                        <span class="auth-token-title">URL: </span>
                        <span class="auth-token-text">{{authTokenUrl}}</span>
                        <br/>
                        <span class="auth-token-title">Created on: </span>
                        <span class="auth-token-text">{{authTokenDate}}</span>
                    </div>
                    <div  v-else-if="authTokenDate != null">
                        <span class="auth-token-title">Created on: </span>
                        <span class="auth-token-text">{{authTokenDate}}</span>
                    </div>
                </div>
                <v-btn primary dark color="var(--themeColor)" @click="toggleLoginStepBuilder">
                    <span v-if="originalDbState">Edit</span>
                    <span v-else>Create</span>
                </v-btn>
            </div>
        </div>


        <v-dialog v-model="showTokenAutomation" class="token-automation-modal">
            <token-automation :originalDbState="originalDbState" @closeLoginStepBuilder=toggleLoginStepBuilder @toggleOriginalStateDb=toggleOriginalStateDb />
        </v-dialog>    
        
    </div>
</template>

<script>
import { mapState } from 'vuex'
import api from "../../api"
import func from '@/util/func'

import HelpTooltip from '../../../../shared/components/help/HelpTooltip.vue'
import TokenAutomation from './TokenAutomation.vue'

export default {
    name:'UserConfig',
    components:{
        TokenAutomation,
        HelpTooltip,
    },
    data() {
        return {
            originalDbState: null,
            stepBuilder: false,
            newKey: this.nonNullAuth ? this.nonNullAuth.key : null,
            newVal: this.nonNullAuth ? this.nonNullAuth.value : null,
            showLoginSaveOption: false,
            authMechanismData: {},
            showTokenAutomation: false
        }
    },
    methods:{
        setAuthHeaderKey(newKey) {
            this.newKey = newKey
            this.saveAuth()
        },
        setAuthHeaderValue(newVal) {
            this.newVal = newVal
            this.saveAuth()
        },
        saveAuthMechanism() {
            this.$store.dispatch('testing/addAuthMechanism', {
                type: "HARDCODED", requestData: [], authParamData: [{
                    "key": this.newKey,
                    "value": this.newVal,
                    "where": "HEADER"
                }]
            })
        },
        toggleLoginStepBuilder() {
            this.showTokenAutomation = !this.showTokenAutomation
        },
        toggleOriginalStateDb() {
            this.fetchAuthMechanismData()
        },
        fetchAuthMechanismData() {
            api.fetchAuthMechanismData().then((resp) => {
                this.authMechanismData = resp.authMechanism;
                if (!this.authMechanismData) return
                
                let requestData = this.authMechanismData["requestData"]
                if (!requestData || requestData.length === 0) return
                let authParamData = this.authMechanismData["authParams"]
                if (!authParamData || authParamData.length === 0) return

                this.originalDbState = this.authMechanismData
            })
        }
    },
    computed:{
        ...mapState('testing', ['authMechanism']),
        nonNullAuth() {
            return this.authMechanism && this.authMechanism.authParams && this.authMechanism.type == "HARDCODED" && this.authMechanism.authParams[0]
        },
        someAuthChanged() {
            let nonNullData = this.newKey != null && this.newVal != null && this.newKey != "" && this.newVal != ""
            if (this.nonNullAuth) {
                return nonNullData && (this.authMechanism.authParams[0].key !== this.newKey || this.authMechanism.authParams[0].value !== this.newVal)
            } else {
                return nonNullData
            }
        },
        authTokenUrl: function () {
            if (!this.authMechanismData) return null
            let requestData = this.authMechanismData["requestData"]
            if (!requestData || requestData.length === 0) return null
            return requestData[0]["url"]
        },
        authTokenDate: function () {
            if (!this.authMechanismData || this.authMechanismData.type == "HARDCODED") return null
            let id = this.authMechanismData["id"]
            if (!id) return null

            let date = id["date"]

            if (!date || date == "") return null

            let dayStartEpochMs = func.toDate(parseInt(date.slice(0, 10).replaceAll("-", "")))
            let dayStr = func.toDateStr(new Date(dayStartEpochMs), false)

            return dayStr + " " + date.slice(11)
        }
    },
    mounted() {
        this.fetchAuthMechanismData()
        let now = func.timeNow()
        this.$store.dispatch('testing/loadTestingDetails', { startTimestamp: now - func.recencyPeriod, endTimestamp: now })
    },
    watch: {
        authMechanism: {
            handler() {
                this.newKey = this.nonNullAuth ? this.nonNullAuth.key : null
                this.newVal = this.nonNullAuth ? this.nonNullAuth.value : null
            }
        }
    }
}

</script>

<style scoped>
    .input-value{
        padding-right: 8px;
        color: var(--themeColorDark);
    }

    .col_1 {
        box-sizing: border-box;
        width: 24px;
        height: 24px;
        left: 0px;
        top: 0px;
        border: 2px solid var(--themeColor);
        border-radius: 50%;
        text-align: center;
        font-style: normal;
        font-weight: 600;
        font-size: 16px;
        line-height: 22px;
        color: var(--themeColor);
    }

    .di-flex {
        display: flex;
        gap: 16px;
        padding-bottom: 11px;
    }

    .di-flex-bottom {
        display: flex;
        gap: 8px;
        padding-top: 20px;
        padding-bottom: 11px;
    }
    .token-automation-modal {
        width: 600px;
        height: 400px
    }

    .auth-token-title {
        font-size: 14px;
        font-weight: 600;
    }

    .auth-token-text {
        font-size: 14px;
    }

</style>