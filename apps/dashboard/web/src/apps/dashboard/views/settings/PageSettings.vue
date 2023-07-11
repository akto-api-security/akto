<template>
    <div>
        <layout-with-tabs title="Settings" :tabs="getTabs()">
            <template slot="Data types">
                <data-types title="Data types" :data_types="data_types" :toggleActivateFieldFunc='toggleActivateDataTypes'
                    :createNewDataType="createNewDataType" @selectedEntry="selectedDataType">
                    <template #details-container="{}">
                        <a-card title="Details" color="var(--rgbaColor2)" style="min-height: 600px">
                            <data-type-details :data_type="data_type" />
                        </a-card>
                    </template>
                </data-types>
            </template>
            <template slot="Auth types">
              <div>
                <div class="d-flex jc-end pr-3 pt-3">
                  <v-dialog v-model="showRestAuthTypesDialog" width="500">
                    <template v-slot:activator="{ on, attrs }">
                      <v-btn outlined color="var(--themeColor)" @click="showRestAuthTypesDialog = true" v-bind="attrs" v-on="on">
                        Reset
                      </v-btn>
                    </template>
                    <div class="dialog-box">
                      <div class="entry-text">Are you sure you want to reset all custom auth types in your API inventory? </div>
                      <div class="d-flex jc-end">
                        <v-btn primary dark color="var(--themeColor)" @click="resetAllAuthTypes">
                          Proceed
                        </v-btn>
                      </div>
                    </div>
                  </v-dialog>
                </div>
                <data-types title="Auth types" :data_types="auth_types" :toggleActivateFieldFunc='toggleActivateAuthTypes'
                            :createNewDataType="createNewAuthType" @selectedEntry="selectedAuthType">
                  <template #details-container="{}">
                    <a-card title="Details" color="var(--rgbaColor2)" style="min-height: 600px">
                      <auth-type-details :auth_type_copy="auth_type" />
                    </a-card>
                  </template>
                </data-types>

              </div>
            </template>
            <template slot="Tags">
                <data-types title="Tags" :data_types="tag_configs" :toggleActivateFieldFunc='toggleActivateTagConfig'
                    :createNewDataType="createNewTagConfig" @selectedEntry="selectedTagConfig">
                    <template #details-container="{}">
                        <a-card title="Details" color="var(--rgbaColor2)" style="min-height: 600px">
                            <tag-config-details :tag_config_copy="tag_config" />
                        </a-card>
                    </template>
                </data-types>
            </template>
            <template slot="Integrations">
                <integration-center />
            </template>
            <template slot="Account">
                <div>
                    <account />
                    <div class="px-8">
                        <div class="py-4">
                            <v-dialog v-model="showDialog" width="500">
                                <template v-slot:activator="{ on, attrs }">
                                    <v-btn primary dark color="var(--themeColor)" @click="showDialog = true" v-bind="attrs" v-on="on">
                                        Update Akto
                                    </v-btn>
                                </template>
                                <div class="dialog-box">
                                    <div class="entry-text"> Please note that this will incur a downtime of 10 mins to
                                        update the system. </div>
                                    <div class="d-flex jc-end">
                                        <v-btn primary dark color="var(--themeColor)" @click="takeUpdate">
                                            Proceed
                                        </v-btn>
                                    </div>
                                </div>
                            </v-dialog>
                        </div>
                    </div>
                </div>
            </template>
            <template slot="Users">
                <team-overview />
            </template>
            <template slot="Health">
                <health :defaultStartTimestamp="defaultStartTimestamp" :defaultEndTimestamp="defaultEndTimestamp"/>
            </template>
            <template slot="Metrics">
                <traffic-metrics/>
            </template>
        </layout-with-tabs>
    </div>
</template>

<script>
import LayoutWithTabs from '@/apps/dashboard/layouts/LayoutWithTabs'
import TeamOverview from './components/TeamOverview'
import Health from './components/Health'
import api from './api'
import DataTypes from './components/data_types/DataTypes.vue'
import Account from './components/Account'
import DataTypeDetails from './components/data_types/components/DataTypeDetails.vue'
import TagSettings from './components/tag_configs/TagSettings.vue'
import TagConfigDetails from './components/tag_configs/TagConfigDetails.vue'
import ACard from '@/apps/dashboard/shared/components/ACard'
import IntegrationCenter from './components/integrations/IntegrationCenter'
import AuthTypeDetails from './components/auth_types/AuthTypeDetails.vue'
import TrafficMetrics from './components/traffic_metrics/TrafficMetrics.vue'
import obj from "@/util/obj"

import { mapState } from 'vuex'
export default {
    name: "PageSettings",
    components: {
        LayoutWithTabs,
        TeamOverview,
        Health,
        Account,
        IntegrationCenter,
        DataTypes,
        TagSettings,
        TagConfigDetails,
        DataTypeDetails,
        AuthTypeDetails,
        ACard,
        TrafficMetrics,
    },
    props:{
        defaultStartTimestamp: obj.strN,
        defaultEndTimestamp: obj.strN,
        tab: obj.strN
    },
    mounted() {
        this.$store.dispatch("data_types/fetchDataTypes")
        this.$store.dispatch("tag_configs/fetchTagConfigs")
        this.$store.dispatch("auth_types/fetchCustomAuthTypes")
    },
    data() {
        return {
            showDialog: false,
            showRestAuthTypesDialog: false
        }
    },
    methods: {
        async resetAllAuthTypes() {
          this.showRestAuthTypesDialog = false
          await api.resetAllCustomAuthTypes()
        },
        createNewDataType() {
            return this.$store.dispatch('data_types/setNewDataType')
        },
        toggleActivateDataTypes(item) {
            return this.$store.dispatch('data_types/toggleActiveParam', item)
        },
        selectedDataType(item) {
            this.$store.state.data_types.data_type = item
        },
        createNewTagConfig() {
            return this.$store.dispatch('tag_configs/setNewTagConfig')
        },
        toggleActivateTagConfig(item) {
            return this.$store.dispatch('tag_configs/toggleActiveTagConfig', item)
        },
        selectedTagConfig(item) {
            this.$store.state.tag_configs.tag_config = item
        },
        createNewAuthType() {
            return this.$store.dispatch('auth_types/setNewAuthType')
        },
        toggleActivateAuthTypes(item) {
            return this.$store.dispatch('auth_types/toggleActivateAuthType',item)
        },
        selectedAuthType(item) {
            this.$store.state.auth_types.auth_type = item
        },
        getActiveAccount() {
            return this.$store.state.auth.activeAccount
        },
        takeUpdate() {
            this.showDialog = false
            api.takeUpdate()

        },
        getTabs() {
            if (window.IS_SAAS && window.IS_SAAS.toLowerCase() == 'true') {
                return ['Data types', 'Auth types', 'Tags', 'Users', 'Integrations'];
            } else if (window.DASHBOARD_MODE && window.DASHBOARD_MODE.toLowerCase() === 'local_deploy') {
                return ['Data types', 'Auth types', 'Tags', 'Users', 'Health', 'Integrations'];
            }
            return ['Data types', 'Auth types', 'Tags', 'Account', 'Users', 'Health', 'Integrations', 'Metrics'];
        }
    },
    computed: {
        ...mapState('data_types', ['data_types', 'data_type']),
        ...mapState('tag_configs', ['tag_configs', 'tag_config']),
        ...mapState('auth_types', ['auth_types', 'auth_type'])
    }
}
</script>
<style lang="sass" scoped>
.entry-text
    font-weight: 500
    margin-right: 16px
    color: var(--themeColorDark)

.dialog-box
    padding: 16px
    background: var(--white) 

</style>