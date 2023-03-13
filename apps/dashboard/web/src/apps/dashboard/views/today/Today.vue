<template>
    <layout-with-right-pane title="Import API" @paneAdjusted="paneAdjusted">
        <div>
                <v-file-input
                    :rules=rules
                    show-size
                    label="Upload JSON file"
                    prepend-icon="$curlyBraces"
                    accept=".json"
                    @change="handleFileChange"
                    v-model=file
                ></v-file-input>

                <template v-if="content && Object.keys(content).length > 0 && !loading">
                    <v-tabs
                        active-class="active-tab"
                        slider-color="var(--themeColor)"
                        height="40px"
                        v-model="tabName"
                        :show-arrows="false"
                        class="brdb"
                    >
                        <v-tab class="right-pane-tab" v-for="tab in tabs" :key="tab">{{tab}}</v-tab>
                    </v-tabs>

                    <v-tabs-items v-model="tabName">
                        <v-tab-item v-for="tab in tabs" :key="tab">
                            <component :is="getComponentName(tab)" class="pa-4"/>
                        </v-tab-item>
                    </v-tabs-items>
                </template>
                <template v-if="loading">
                    <spinner/>
                </template>    
        </div>    
        <template #rightPane>
        </template>
        <template slot="universal-ctas">
          <slot name="universal-ctas"></slot>
        </template>
    </layout-with-right-pane>
</template>

<script>
    import LayoutWithRightPane from "../../layouts/LayoutWithRightPane"
    import APISummary from "./components/APISummary"
    import APISecurityAudit from "./components/APISecurityAudit"
    import APIDetails from "./components/APIDetails"
    import APITestReport from "./components/APITestReport"

    import {mapState} from 'vuex'
    import Spinner from '@/apps/dashboard/shared/components/Spinner'


    export default {
        name: 'PageToday',
        components: {
            'layout-with-right-pane': LayoutWithRightPane,
            APISummary,
            APISecurityAudit,
            APITestReport,
            APIDetails,
            Spinner
        },
        data () {
            return {
                rules: [
                    value => !value || value.size < 2e6 || 'JSON file size should be less than 2 MB!',
                ],
                tabName: null,
                showRightPane: true,
                file: null,
                tabs: [
                    'Summary', 
                    'Security Audit',
                    'Test Report',
                    'Details'
                ],
                errors: {}
            }
        },
        methods: {
            paneAdjusted(to) {
              this.showRightPane = to
            },

            getComponentName(tab) {
                return 'API' + tab.replaceAll(" ","")
            },

            handleFileChange() {
                if (!this.file) {this.content = null}
                var reader = new FileReader();
                
                // Use the javascript reader object to load the contents
                // of the file in the v-model prop
                reader.readAsText(this.file);
                reader.onload = () => {
                    this.$store.dispatch('today/saveContentAndFetchAuditResults', { content: JSON.parse(reader.result), filename: this.file.name})
                }
            }
        },
        computed: {
            ...mapState('today', ['loading', 'content', 'auditId', 'contentId'])
        },
        mounted() {
            this.$store.dispatch('today/fetchAuditResults', { apiCollectionId: this.apiCollectionId})
        }
    }
</script>

<style lang="sass" scoped>
.right-pane-tab
    padding: 0 20px !important
</style>