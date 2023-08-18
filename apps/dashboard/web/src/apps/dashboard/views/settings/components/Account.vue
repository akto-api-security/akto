<template>
    <div class="pa-8 d-flex" style="gap: 100px">
      <div>
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


      </div>
      <div>


        <div class="toggle-redact-feature">
            <div class="entry-text">Traffic filters</div>
            <div>

                <div v-if="filterHeaderValueMap">
                    <div v-for="(val, key) in filterHeaderValueMap" :key="key">
                            <div>
                                <span class="fs-14 fw-500">{{key}}</span>
                                <span class="ml-4 fs-14">{{val}}</span>
                                <v-btn icon @click="() => removeFilterHeaderValueMap(key)">
                                    <v-icon size="12">$fas_trash</v-icon>
                                </v-btn>
                            </div>
                    </div>
                </div>

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

        <div class="toggle-redact-feature mt-8">
            <div class="entry-text">Replace collection</div>
            <div>

                <div v-if="apiCollectionNameMapper">
                    <div v-for="({newName, regex, headerName}) in Object.values(apiCollectionNameMapper)">
                            <div>
                                <span class="fs-14 fw-500">{{headerName ? headerName: "host"}}={{regex}}</span>
                                <span class="ml-4 fs-14">{{newName}}</span>
                                <v-btn icon @click="() => deleteApiCollectionNameMapper(regex)">
                                    <v-icon size="12">$fas_trash</v-icon>
                                </v-btn>
                            </div>
                    </div>
                </div>

                <div class="d-flex traffic-filter-div">
                    <div class="input-value-key">
                        <v-text-field v-model="newApiCollectionNameMapperHeaderName" style="width: 200px">
                            <template slot="label">
                                <div class="d-flex">
                                    Header key
                                    <help-tooltip :size="12"
                                        text="Please enter the header name eg host" />
                                </div>
                            </template>
                        </v-text-field>

                    </div>
                    <div class="input-value-key">
                        <v-text-field v-model="newApiCollectionNameMapperKey" style="width: 200px">
                            <template slot="label">
                                <div class="d-flex">
                                    Regex to match header value
                                    <help-tooltip :size="12"
                                        text="Please enter the regex to match " />
                                </div>
                            </template>
                        </v-text-field>

                    </div>
                    <div class="input-value-value">
                        <v-text-field v-model="newApiCollectionNameMapperValue" style="width: 500px">
                            <template slot="label">
                                <div class="d-flex">
                                    Replaced collection name
                                    <help-tooltip :size="12" text="Please enter the name of new collection" />
                                </div>
                            </template>

                        </v-text-field>
                    </div>

                    <div class="filter-save-btn">
                        <v-btn primary dark color="var(--hexColor9)" @click="addApiCollectionNameMapper" v-if="apiCollectionNameMapperChanged">
                            Save
                        </v-btn>
                    </div>
                </div>
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
                newApiCollectionNameMapperHeaderName: null,
                newApiCollectionNameMapperKey: null,
                newApiCollectionNameMapperValue: null
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
                this.filterHeaderValueMap[this.newKey] = this.newVal
                this.$store.dispatch('team/addFilterHeaderValueMap', this.filterHeaderValueMap)
                this.newKey = null
                this.newVal = null
            },
            removeFilterHeaderValueMap(key){
                delete this.filterHeaderValueMap[key]
                console.log('Updated map', this.filterHeaderValueMap)
                this.$store.dispatch('team/addFilterHeaderValueMap', this.filterHeaderValueMap)
            },
            async addApiCollectionNameMapper() {
                await this.$store.dispatch('team/addApiCollectionNameMapper', {"regex" : this.newApiCollectionNameMapperKey, "newName": this.newApiCollectionNameMapperValue, headerName: this.newApiCollectionNameMapperHeaderName})
                this.newApiCollectionNameMapperHeaderName = null
                this.newApiCollectionNameMapperKey = null
                this.newApiCollectionNameMapperValue = null
            },
            deleteApiCollectionNameMapper(regex) {
                this.$store.dispatch('team/deleteApiCollectionNameMapper', {regex})
            }
        },
        mounted() {
            this.$store.dispatch('team/fetchAdminSettings')
            this.$store.dispatch('team/fetchUserLastLoginTs')
        },
        computed: {
            ...mapState('team', ['redactPayload', 'setupType', 'dashboardVersion', 'apiRuntimeVersion', 'mergeAsyncOutside', 'lastLoginTs', 'privateCidrList', 'urlRegexMatchingEnabled', 'enableDebugLogs', 'filterHeaderValueMap', 'apiCollectionNameMapper']),
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
            },
            apiCollectionNameMapperChanged() {
                return this.newApiCollectionNameMapperKey && this.newApiCollectionNameMapperValue
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
    height: 30px
    vertical-align: middle
    line-height: 30px


.toggle-redact-feature
    display: flex
    height: 50px
    line-height: 50px

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


