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
</template>

<script>
import func from "@/util/func";
import {mapState} from 'vuex'
    export default {
        name: "Account",
        components: { 
        },
        data () {
            return {
                setup_types: ["STAGING", "PROD", "QA", "DEV"]
            }
        },
        methods: {
            getActiveAccount() {
                return this.$store.state.auth.activeAccount
            },
            epochToDateTime(timestamp) {
                return func.epochToDateTime(timestamp)
            }
        },
        mounted() {
            this.$store.dispatch('team/fetchAdminSettings')
            this.$store.dispatch('team/fetchUserLastLoginTs')
        },
        computed: {
            ...mapState('team', ['redactPayload', 'setupType', 'dashboardVersion', 'apiRuntimeVersion', 'mergeAsyncOutside', 'lastLoginTs', 'privateCidrList', 'urlRegexMatchingEnabled']),
            localRedactPayload: {
                get() {
                    return this.redactPayload
                },
                set(v) {
                    this.$store.dispatch('team/toggleRedactFeature', v)
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
</style>

<style>
     .toggle-redact-feature .v-input--selection-controls {
        margin-top:20px;
        padding-top: 0px;
    }
</style>


