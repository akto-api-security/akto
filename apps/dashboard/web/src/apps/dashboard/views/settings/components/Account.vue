<template>
    <div class="pa-8">
        <div class="account-id">
            <div class="entry-text">Account ID</div>
            <div class="entry-value">{{getActiveAccount()}}</div>
        </div>

        <div class="account-id">
            <div class="entry-text">Dashboard Version</div>
            <div class="entry-value">{{this.dashboardVersion}}</div>
        </div>

        <div class="account-id">
            <div class="entry-text">Runtime Version</div>
            <div class="entry-value">{{this.apiRuntimeVersion}}</div>
        </div>

        <div class="account-id" v-if="this.lastLoginTs">
            <div class="entry-text">Last Login time</div>
            <div class="entry-value">{{epochToDateTime(this.lastLoginTs)}}</div>
        </div>

        <div class="account-id">
            <div class="entry-text">VPC CIDR</div>
            <div class="entry-value">{{this.cidr}}</div>
        </div>

        <div class="toggle-redact-feature" v-if="localSetupType !== null">
            <div class="entry-text">Setup Type</div>
            <div class="entry-value">
                <div class="text-center">
                    <v-menu offset-y>
                        <template v-slot:activator="{ on, attrs }">
                            <span
                                v-bind="attrs"
                                v-on="on"
                                style="text-decoration: underline"
                            >
                                {{localSetupType}}
                            </span>
                        </template>
                        <v-list>
                            <v-list-item
                                v-for="(item, index) in setup_types"
                                :key="index"
                                @click="localSetupType = item"
                            >
                                <v-list-item-title>{{ item }}</v-list-item-title>
                            </v-list-item>
                        </v-list>
                    </v-menu>
                </div>
            </div>
        </div>

        <div class="toggle-redact-feature" v-if="localRedactPayload !== null">
            <div class="entry-text">Redact sample data</div>
            <div class="entry-value">
                <v-switch
                    color="var(--themeColor)"
                    v-model="localRedactPayload"
                />
            </div>
        </div>

        <div class="toggle-redact-feature" v-if="localEnableDebugLogs !== null">
            <div class="entry-text">Enable debug logs</div>
            <div class="entry-value">
                <v-switch
                    color="var(--themeColor)"
                    v-model="localEnableDebugLogs"
                />
            </div>
        </div>

        <div class="toggle-redact-feature" v-if="!localMergeAsyncOutside">
            <div class="entry-text">Activate new merging</div>
            <div class="entry-value">
                <v-switch
                    color="var(--themeColor)"
                    v-model="localMergeAsyncOutside"
                />
            </div>
        </div>

        <div class="toggle-redact-feature">
            <div class="entry-text">Enable New Merging</div>
            <div class="entry-value">
                <v-switch
                    color="var(--themeColor)"
                    v-model="newMerging"
                />
            </div>
        </div>

        <div class="toggle-redact-feature">
            <div class="entry-text">Traffic filters</div>
            <div class="d-flex traffic-filter-div">
                <div class="input-value-key">
                    <v-text-field v-model="newKey" style="width: 200px">
                        <template slot="label">
                            <div class="d-flex">
                                Header key
                                <help-tooltip :size="12"
                                    text="Please enter the name of header key" />
                            </div>
                        </template>
                    </v-text-field>

                </div>
                <div class="input-value-value">
                    <v-text-field v-model="newVal" style="width: 500px">
                        <template slot="label">
                            <div class="d-flex">
                                Header value
                                <help-tooltip :size="12" text="Please enter the name of header value" />
                            </div>
                        </template>

                    </v-text-field>
                </div>

                <div class="filter-save-btn">
                    <v-btn primary dark color="var(--hexColor9)" @click="addFilterHeaderValueMap" v-if="filterHeaderValueMapChanged">
                        Save
                    </v-btn>
                </div>

            </div>
        </div>

    </div>
</template>

<script>
import func from "@/util/func";
import HelpTooltip from '@/apps/dashboard/shared/components/help/HelpTooltip'

import {mapState} from 'vuex'
    export default {
        name: "Account",
        components: { 
            HelpTooltip
        },
        data () {
            return {
                setup_types: ["STAGING", "PROD", "QA", "DEV"],
                newKey: null,
                newVal: null,
            }
        },
        methods: {
            getActiveAccount() {
                return this.$store.state.auth.activeAccount
            },
            epochToDateTime(timestamp) {
                return func.epochToDateTime(timestamp)
            },
            addFilterHeaderValueMap() {
                this.$store.dispatch('team/addFilterHeaderValueMap', {"filterHeaderValueMapKey" : this.newKey, "filterHeaderValueMapValue": this.newVal})
            }
        },
        mounted() {
            this.$store.dispatch('team/fetchAdminSettings')
            this.$store.dispatch('team/fetchUserLastLoginTs')
        },
        computed: {
            ...mapState('team', ['redactPayload', 'setupType', 'dashboardVersion', 'apiRuntimeVersion', 'mergeAsyncOutside', 'lastLoginTs', 'privateCidrList', 'urlRegexMatchingEnabled', 'enableDebugLogs', 'filterHeaderValueMap']),
            localRedactPayload: {
                get() {
                    return this.redactPayload
                },
                set(v) {
                    this.$store.dispatch('team/toggleRedactFeature', v)
                }
            },
            localEnableDebugLogs: {
                get() {
                    return this.enableDebugLogs
                },
                set(v) {
                    this.$store.dispatch('team/toggleDebugLogsFeature', v)
                }
            },
            localSetupType: {
              get() {
                return this.setupType
              },
              set(v) {
                this.$store.dispatch('team/updateSetupType', v)
              }
            },
            localMergeAsyncOutside: {
                get() {
                    return this.mergeAsyncOutside
                },
                set(v) {
                    this.$store.dispatch('team/updateMergeAsyncOutside')
                }
            },
            cidr: {
                get() {
                    return this.privateCidrList && this.privateCidrList.length > 0 ? func.prettifyArray(this.privateCidrList) : "No values stored"
                },
            },
            newMerging: {
                get() {
                    return this.urlRegexMatchingEnabled
                },
                set(v) {
                    this.$store.dispatch('team/updateEnableNewMerge', v)
                }
            },
            nonNullAuth() {
                return this.filterHeaderValueMap
            },
            filterHeaderValueMapChanged() {
                let nonNullData = this.newKey != null && this.newVal != null && this.newKey != "" && this.newVal != ""
                if (this.nonNullAuth) {
                    return nonNullData && this.filterHeaderValueMap && (Object.keys(this.filterHeaderValueMap)[0] !== this.newKey || Object.values(this.filterHeaderValueMap)[0] !== this.newVal)
                } else {
                    return nonNullData
                }
            }
        },
        watch: {
            filterHeaderValueMap(a) {
                if (!a) return
                this.newKey = Object.keys(a)[0] 
                this.newVal = Object.values(a)[0]
            }
        }
    }
</script>

<style lang="sass" scoped>
.entry-text
    font-weight: 500
    margin-right: 16px
    width: 200px


.account-id
    display: flex
    height: 50px
    vertical-align: middle
    line-height: 50px


.toggle-redact-feature
    display: flex
    height: 60px
    line-height: 60px

.input-value-key
    padding-right: 8px
    color: var(--themeColorDark)

.input-value-value
    padding-right: 8px
    color: var(--themeColorDark)
    max-width: 200px

.filter-save-btn
    display: flex
    align-items: center
</style>

<style scoped>
     .toggle-redact-feature .v-input--selection-controls {
        margin-top:20px;
        padding-top: 0px;
    }

    .traffic-filter-div>>>.v-label {
        font-size: 12px;
        color: var(--themeColor);
        font-weight: 400;
    }

    .traffic-filter-div>>>input {
        font-size: 12px;
        font-weight: 400;
    }
</style>


