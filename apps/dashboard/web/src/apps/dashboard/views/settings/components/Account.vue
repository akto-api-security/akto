<template>
    <div class="pa-8">
        <div class="account-id">
            <div class="entry-text">Account ID</div>
            <div class="entry-value">{{getActiveAccount()}}</div>
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
                    color="#6200EA"
                    v-model="localRedactPayload"
                />
            </div>
        </div>

    </div>
</template>

<script>
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
        },
        mounted() {
            this.$store.dispatch('team/fetchAdminSettings')
        },
        computed: {
            ...mapState('team', ['redactPayload', 'setupType']),
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
            }
        }
    }
</script>

<style lang="sass" scoped>
.entry-text
    font-weight: 500
    margin-right: 16px


.account-id
    display: flex
    height: 40px

.toggle-redact-feature
    display: flex
    height: 60px
    vertical-align: middle
    text-align: center
    line-height: 60px
</style>

<style>
     .toggle-redact-feature .v-input--selection-controls {
        margin-top:20px;
        padding-top: 0px;
    }
</style>


