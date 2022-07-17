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
            ...mapState('team', ['redactPayload', 'dashboardVersion', 'apiRuntimeVersion']),
            localRedactPayload: {
                get() {
                    return this.redactPayload
                },
                set(v) {
                    this.$store.dispatch('team/toggleRedactFeature', v)
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


