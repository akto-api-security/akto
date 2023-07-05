<template>
    <div>
        <text-editor :defaultTestId="defaultTestId" :isAnonymousPage="true" :refreshTestTemplatesApiCall="refreshTestTemplatesApiCall" 
        :makeJsonApiCall="makeJsonApiCall" :runTestForGivenTemplateApiCall="runTestForGivenTemplateApiCall" :setSelectedMethodOnClick="setSelectedMethodOnClick">
            <template #unsavedChanges="{ IsEdited }">
                <div>
                    <span v-if="IsEdited" class="unsaved-changes">unsaved changes</span>
                </div>
            </template>
            <template #saveEditedTemplate="{ IsEdited }">
                <v-menu left offset-y rounded="lg">
                    <template v-slot:activator="{ on, attrs }">
                        <div class="file-title" :style="{ cursor: IsEdited ? 'pointer' : '' }" v-on="on" v-bind="attrs">
                            <v-icon :style="{ opacity: IsEdited ? '1' : '0.4' }" size=16>$saveIcon</v-icon>
                            <span class="file-name" :style="{ opacity: IsEdited ? '1' : '0.2' }">Save</span>
                        </div>
                    </template>
                    <v-card>
                        <v-list class="pa-0">
                            <v-list-item class="save-option-class">
                                <div class="d-flex flex-column">
                                    <v-list-item-content class="d-flex flex-column">
                                        <v-list-item-subtitle>In order to unlock the </v-list-item-subtitle>
                                        <v-list-item-subtitle>power to save your tests.</v-list-item-subtitle>
                                    </v-list-item-content>
                                    <v-list-item-action class="ml-0">
                                        <v-btn width="100%" class="white-color" primary dark depressed
                                            color="var(--themeColor)" :href="getRedirectPath()" target="_blank">
                                            Sign up now
                                        </v-btn>
                                    </v-list-item-action>
                                </div>
                            </v-list-item>
                        </v-list>
                    </v-card>
                </v-menu>
            </template>
            <template #sampleDataSelector="{ resetSelectedURL, selectedUrl, setMessageJson }">
                <v-menu offset-y rounded="lg" nudge-bottom="12" nudge-left="100" class="options-menu">
                    <template v-slot:activator="{ on, attrs }">
                        <div class="d-flex jc-sb select-url" v-on="on" v-bind="attrs">
                            <span class="file-name url-name show-overflow ml-2">{{ selectedAnonymousOption }}</span>
                            <v-icon size=12>$fas_angle-down</v-icon>
                        </div>
                    </template>
                    <v-list class="pa-0">
                        <v-list-item
                            @click="() => {setSelectedAnomymousOption('Sample data'); resetSelectedURL(); makeJSONWithSelectedURL(selectedUrl).then(resp=>{setMessageJson(resp)})}">
                            <v-list-item-title>
                                <div class="d-flex jc-sb">
                                    <span class="menu-list-font">Sample data</span>
                                    <v-icon v-if="selectedAnonymousOption == 'Sample data'">$fas_check</v-icon>
                                </div>
                            </v-list-item-title>
                        </v-list-item>
                        <v-list-item
                            @click="setSelectedAnomymousOption('Copy/paste data'), (customSampleDataDialogBox = true)">
                            <v-list-item-title>
                                <div class="d-flex jc-sb">
                                    <span class="menu-list-font">Use custom API</span>
                                    <v-icon v-if="selectedAnonymousOption == 'Copy/paste data'">$fas_check</v-icon>
                                </div>
                            </v-list-item-title>
                        </v-list-item>
                        <v-list-item class="theme-color" :href="getRedirectPath()" target="_blank">
                            <v-list-item-title><span class="menu-list-font" style="color: #FFFFFF !important">Add automated collection</span><v-icon
                                    class="white-color">$fas_arrow-right</v-icon></v-list-item-title>
                        </v-list-item>
                    </v-list>
                </v-menu>
            </template>
            <template #modalsOverTestEditor="{setMessageJson}">
                <simple-modal :parentDialog="simpleModalDialogBox" title="Get started with API Testing today" body="Our API test templates can help you save time and effort for
                     testing your APIs. Sign up for Akto today a
                     nd start using them!"
                    @closeSimpleDialog="(simpleModalDialogBox = false)" :redirectPath="getRedirectPath()"></simple-modal>
                <custom-sample-data-api-modal title="Add your own API" :parentDialog="customSampleDataDialogBox"
                    @closeCustomSampleDataDialog="(customSampleDataDialogBox = false)" @setSampleDataApi="(values) => {setSampleDataApi(values).then(resp => {setMessageJson(resp)})}">
                </custom-sample-data-api-modal>
            </template>
        </text-editor>
        <div class="akto-external-links">
            <v-btn primary dark depressed class="white-color" color="var(--themeColor)" :href="getRedirectPath()" target="_blank">
                Try on your APIs
                <v-icon size="11">$fas_external-link-alt</v-icon>
            </v-btn>
        </div>
    </div>
</template>
<script>
import obj from "@/util/obj"
import TextEditor from './TextEditor.vue'
import SimpleModal from './SimpleModal.vue'
import CustomSampleDataApiModal from "./CustomSampleDataApiModal.vue";
import testingApi from "../views/testing/api"
import request from '@/util/request'


export default {
    name: "TestEditorForWebsite",
    components: {
        TextEditor,
        SimpleModal,
        CustomSampleDataApiModal
    },
    props: {
        defaultTestId: obj.strR
    },
    data() {
        return {
            selectedAnonymousOption: "Sample data",
            simpleModalDialogBox: false,
            customSampleDataDialogBox: false,
            refreshTestTemplatesApiCall: async function() {
                let result = {}
                await request({
                    url: '/tools/fetchAllSubCategories',
                    method: 'POST',
                    data: {}
                }).then(resp => {
                    result = resp
                })
                return result
            },
            runTestForGivenTemplateApiCall:  async function(textEditor, selectedUrl, sampleDataListForTestRun) {
                let result = {}
                await testingApi.runTestForTemplateAnonymous(textEditor, selectedUrl, sampleDataListForTestRun).then(resp => {
                    result = resp
                })
                return result
            },
            makeJsonApiCall: async function(selectedUrl) {
                let result = {}
                await request({
                    url: '/tools/fetchSampleData',
                    method: 'POST',
                    data: {
                        url: selectedUrl.url,
                        apiCollectionId: selectedUrl.apiCollectionId,
                        method: selectedUrl.method
                    }
                }).then((resp) => {
                    result = resp
                })
                return result
            }
        }
    },
    methods: {
        setSelectedMethodOnClick(testId) {
            this.simpleModalDialogBox = true
        },
        setSelectedAnomymousOption(text) {
            this.selectedAnonymousOption = text
        },
        getRedirectPath () {
            let pathname = window.location.pathname
            pathname = pathname.replace("tools","dashboard")
            return "https://app.akto.io" + pathname
        },
        async makeJSONWithSelectedURL(selectedUrl) {
            let result = {}

            await request({
                url: '/tools/fetchSampleData',
                method: 'POST',
                data: {
                    url: selectedUrl.url,
                    apiCollectionId: selectedUrl.apiCollectionId,
                    method: selectedUrl.method
                }
            }).then((resp) => {
                if (resp.sampleDataList.length > 0 && resp.sampleDataList[0].samples && resp.sampleDataList[0].samples.length > 0) {
                    result['messageJson'] = { "message": resp.sampleDataList[0].samples[0], "highlightPaths": [] }
                    result['sampleDataListForTestRun'] = resp.sampleDataList
                }
            })
            return result
        },
        async setSampleDataApi(test) {
            let result = {}
            await testingApi.setCustomSampleApi(test.requestValue, test.responseValue).then((resp) => {
                if (resp.sampleDataList.length > 0 && resp.sampleDataList[0].samples && resp.sampleDataList[0].samples.length > 0) {
                    result['messageJson'] = { "message": resp.sampleDataList[0].samples[0], "highlightPaths": [] }
                    result['sampleDataListForTestRun'] = resp.sampleDataList
                }
            })
            return result
        },

    }
}

</script>
<style scoped>
.unsaved-changes {
    background: var(--warning);
    font-size: 12px;
    border-radius: 4px;
    color: var(--white);
    padding: 1px 8px 1px 8px;
}

.file-title {
    display: flex;
    gap: 4px;
    align-items: center;
}

.file-name {
    font-size: 14px;
    color: var(--themeColorDark);
    font-weight: 500;
}

.save-option-class {
    font-size: 12px !important;
    font-weight: 500 !important;
    color: var(--themeColorDark) !important;
    background-color: var(--hexColor29) !important;
}

.white-color {
    color: var(--white) !important;
}

.show-overflow {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    line-height: 120%;
    max-width: 400px;
}

.select-url {
    max-width: 310px;
    position: absolute;
    top: 87px;
    right: 20px;
    display: flex;
    align-items: center;
    justify-content: center;
    padding: 4px;
    background: var(--themeColorDark17);
    border: 1px solid var(--themeColorDark18);
    height: 30px;
    gap: 4px;
    cursor: pointer;
    border-radius: 4px;

}

.url-name {
    font-size: 12px !important;
    word-break: break-all;
}

.menu-list-font {
    font-size: 14px !important;
    font-weight: 500 !important;
    margin: auto 0px;
    color: var(--themeColorDark) !important;
}

.theme-color {
    background-color: var(--themeColor) !important;
    color: var(--white) !important;
}

.akto-external-links {
    position: absolute;
    right: 0px;
    top: 0px;
    padding: 24px 32px;
    display: flex;
    gap: 8px;

}
</style>
