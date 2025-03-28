<template>
    <div>
        <simple-layout title="Test editor" version="Beta">
            <template>
                <div class="d-flex test-editor-panel">
                    <div class="test-col">
                        <layout-with-left-pane>
                            <search class="py-2 pr-2" placeholder="Search Tests" @onKeystroke="setSearchText" />
                            <div class="tests-container">
                                <div class="main-list-title" @click="toggleListDisplay('custom')">
                                    <v-icon size=18
                                        :style="{ transform: customToggle ? 'rotate(90deg)' : '', transition: 'all 0.2s linear', color: 'var(--lighten2)' }">$fas_angle-right</v-icon>
                                    <span class="title-name">
                                        Custom
                                    </span>

                                    <span class="total-tests shift-right">
                                        {{ totalCustomTests }}
                                    </span>
                                </div>

                                <v-list dense nav class="tests-list" :style="{display: !customToggle ? 'none' : ''}">
                                    <v-list-group v-for="item in selectedTestCategories(customTestObj)" :key="item.displayName"
                                        class="tests-category-container" active-class="tests-category-container-active" :value="currentCategory === item.displayName">
                                        <template v-slot:prependIcon>
                                            <v-icon color="var(--lighten1)" size=16>$fas_angle-right</v-icon>
                                        </template>
                                        <template v-slot:appendIcon>
                                            <span class="total-tests">{{ customTestObj[item.name].all.length }}</span>
                                        </template>
                                        <template v-slot:activator>
                                            <v-list-item-content>
                                                <v-list-item-title>
                                                    <div class="test-category-name show-overflow">
                                                        {{ item.displayName }}
                                                    </div>
                                                </v-list-item-title>
                                            </v-list-item-content>
                                        </template>

                                        <v-list-item v-for="(test, index) in customTestObj[item.name].all" :key="index"
                                            class="test-container"  @click="setSelectedMethodOnClick(test.value)">
                                            <v-list-item-content :class="test.label === defaultTestName ? 'test-container-active': ''">
                                                <div class="test-name show-tooltip">
                                                    <v-tooltip bottom>
                                                        <template v-slot:activator="{ on, attrs }">
                                                            <span v-bind="attrs" v-on="on" >
                                                                {{test.label}}
                                                            </span>
                                                        </template>
                                                        <span>{{test.label}}</span>
                                                    </v-tooltip>
                                                </div>
                                            </v-list-item-content>
                                        </v-list-item>
                                    </v-list-group>
                                </v-list>

                                <div class="main-list-title" @click="toggleListDisplay('akto')">
                                    <v-icon size=18
                                        :style="{ transform: aktoToggle ? 'rotate(90deg)' : '', transition: 'all 0.2s linear', color: 'var(--lighten2)' }">$fas_angle-right</v-icon>
                                    <span class="title-name">
                                        Akto Default
                                    </span>

                                    <span class="total-tests shift-right">
                                        {{ totalAktoTests }}
                                    </span>
                                </div>
                                <v-list dense nav class="tests-list" :style="{display: !aktoToggle ? 'none' : ''}">
                                    <v-list-group v-for="item in selectedTestCategories(testsObj)" :key="item.displayName"
                                        class="tests-category-container" active-class="tests-category-container-active" :value="currentCategory === item.displayName">
                                        <template v-slot:prependIcon>
                                            <v-icon color="var(--lighten1)" size=16>$fas_angle-right</v-icon>
                                        </template>
                                        <template v-slot:appendIcon>
                                            <span class="total-tests">{{ testsObj[item.name].all.length }}</span>
                                        </template>
                                        <template v-slot:activator>
                                            <v-list-item-content>
                                                <v-list-item-title>
                                                    <div class="test-category-name show-overflow">
                                                        {{ item.displayName }}
                                                    </div>
                                                </v-list-item-title>
                                            </v-list-item-content>
                                        </template>

                                        <v-list-item v-for="(test, index) in testsObj[item.name].all" :key="index"
                                            class="test-container" @click="setSelectedMethodOnClick(test.value)">
                                            <v-list-item-content :class="test.label === defaultTestName ? 'test-container-active': ''">
                                                <div class="test-name show-tooltip">
                                                    <v-tooltip bottom>
                                                        <template v-slot:activator="{ on, attrs }">
                                                            <span v-bind="attrs" v-on="on" >
                                                                {{test.label}}
                                                            </span>
                                                        </template>
                                                        <span>{{test.label}}</span>
                                                    </v-tooltip>
                                                </div>
                                            </v-list-item-content>
                                        </v-list-item>
                                    </v-list-group>
                                </v-list>
                            </div>
                        </layout-with-left-pane>
                    </div>
                    <div class="editor-col">
                        <div class="editor-header-container">
                            <div class="d-flex fd-column">
                                <div class="file-name show-overflow mb-1">
                                    {{defaultTestName || "Test editor"}}
                                    <v-icon v-if="!allCustomTests[defaultTestName]"
                                        :style="{ 'cursor': 'pointer' }" 
                                        size=14 
                                        @click='openGithubLink' 
                                        :ripple="false"
                                    >
                                        $githubIcon
                                    </v-icon>
                                </div>
                                <slot name="unsavedChanges" :IsEdited="IsEdited">
                                    <span class="test-subheading">
                                    <span class="last-edited" v-if="lastEdited !== -1">last edited {{ lastEdited }}</span>
                                        <v-tooltip bottom>
                                            <template v-slot:activator='{ on, attrs }'>
                                                <v-icon 
                                                size=16 
                                                @click="setTestInactive"
                                                v-bind="attrs"
                                                v-on="on"
                                                >
                                                {{ testInactive ? "$fas_check" : "$fas_times" }}
                                                </v-icon>
                                            </template>
                                            <span>Set as {{ testInactive ? "active" : "inactive" }}</span>
                                        </v-tooltip>
                                    </span>
                                </slot>
                            </div>
                            <slot name="saveEditedTemplate" :IsEdited="IsEdited">
                                <div class="file-title" :style="{ cursor: IsEdited ? 'pointer' : '' }"
                                    @click="saveTestTemplate">
                                    <v-icon :style="{ opacity: IsEdited ? '1' : '0.4' }" size=16>$saveIcon</v-icon>
                                    <span class="file-name" :style="{ opacity: IsEdited ? '1' : '0.2' }">Save</span>
                                </div>
                            </slot>
                        </div>
                        <div ref="editor" style="height: calc(100vh - 120px);" class="monaco-editor"></div>
                        <selector-modal :show-dialog="showDialogBox" :title="titleBox" @closeDialog="closeDialog"
                            :currentParam="currentParam" :test-categories="testCategories"
                            @get_form_values="getFormValues" :custom-test="setTextId" />
                    </div>
                    <div class="req-resp-col">
                        <div class="req-box-container" v-if="messageJson.message">
                            <sample-data :json="json" requestTitle="Request" responseTitle="Response" :tabularDisplay="true"
                                @run_tests="runTestForGivenTemplate()" :isLoading="runTestObj.isLoading"/>
                        </div>
                        <div class="empty-container" v-else>
                            No Values Yet !!
                        </div>
                        <slot name="sampleDataSelector" :resetSelectedURL="resetSelectedURL" :selectedUrl="selectedUrl"
                            :setMessageJson="setMessageJson">
                            <div class="select-url" @click="openDialogBox('choose')" v-if="showSelector">
                                <v-icon size=12>$fas_check</v-icon>
                                <span class="file-name url-name show-overflow">{{ selectedUrl.url }}</span>
                            </div>
                        </slot>
                        <div class="footer-div">
                            <div class="show-run-test" v-if="!runTest">
                                Run test to see results
                            </div>
                            <div class="show-run-test" v-else-if="runTestObj.isLoading">
                                Running tests...
                            </div>
                            <div v-else>
                                <div class="show-complete-details" v-if="showAllDetails">
                                    <!-- <span class="title-name">
                                        {{ runTestObj.totalFoundAttempts }} / {{ runTestObj.totalAttempts }} Attempts Found
                                        Vulnerable
                                    </span> -->
                                    <v-btn class="show-attempts-box" @click="showAllAttempts()">
                                        Show all Attempts
                                        <v-icon size=14
                                            :style="{ 'transform': 'rotate(45deg) !important' }">$fas_arrow-up</v-icon>
                                    </v-btn>
                                </div>
                                <div class="vulnerable-container">
                                    <span>
                                        <v-icon size=14
                                            :style="{cursor: 'pointer' }"
                                            @click="showAllDetails = !showAllDetails">
                                            {{ showdetailsIcon }}
                                        </v-icon>
                                        <span :style="{ 'color': getTextColor }">{{ runTestObj.vulnerability }}</span>
                                        <span>Vulnerability Found</span>
                                    </span>
                                    <span class="show-side">

                                        <v-icon size=16>$far_clock</v-icon>
                                        <span>{{ runTestObj.runTime }}</span>
                                    </span>
                                </div>
                            </div>
                        </div>
                    </div>
                </div>
            </template>
        </simple-layout>
        <slot name="modalsOverTestEditor" :setMessageJson="setMessageJson">
        </slot>
        <issues-dialog :openDetailsDialog="dialogBox"
            :testingRunResult="testingRunResult" :subCatogoryMap="subCatogoryMap" :issue="dialogBoxIssue"
            @closeDialogBox="(dialogBox = false)">
        </issues-dialog>
    </div>
</template>

<script>

import { editor, KeyCode, KeyMod } from "monaco-editor/esm/vs/editor/editor.api"
import Search from '../shared/components/inputs/Search.vue';
import LayoutWithLeftPane from '../layouts/LayoutWithLeftPane.vue';
import SampleData from '../shared/components/SampleData.vue';
import SimpleLayout from '../layouts/SimpleLayout.vue';
import SelectorModal from './SelectorModal.vue';
import IssuesDialog from '../../dashboard/views/issues/components/IssuesDialog'

import issuesApi from "../views/issues/api"
import inventoryApi from "../views/observe/inventory/api"
import testingApi from "../views/testing/api"

import func from "@/util/func"
import obj from "@/util/obj"
import editorSetup from "./editorSetup"

import 'monaco-editor/esm/vs/editor/contrib/find/browser/findController';
import 'monaco-editor/esm/vs/editor/contrib/folding/browser/folding';
import 'monaco-editor/esm/vs/editor/contrib/bracketMatching/browser/bracketMatching';
import 'monaco-editor/esm/vs/editor/contrib/comment/browser/comment';
import 'monaco-editor/esm/vs/editor/contrib/codelens/browser/codelensController';
import 'monaco-editor/esm/vs/editor/contrib/colorPicker/browser/color';
import 'monaco-editor/esm/vs/editor/contrib/format/browser/formatActions';
import 'monaco-editor/esm/vs/editor/contrib/lineSelection/browser/lineSelection';
import 'monaco-editor/esm/vs/editor/contrib/indentation/browser/indentation';
import 'monaco-editor/esm/vs/editor/contrib/inlineCompletions/browser/inlineCompletionsController';
import 'monaco-editor/esm/vs/editor/contrib/snippet/browser/snippetController2'
import 'monaco-editor/esm/vs/editor/contrib/suggest/browser/suggestController';
import 'monaco-editor/esm/vs/editor/contrib/wordHighlighter/browser/wordHighlighter';

import "monaco-editor/esm/vs/basic-languages/yaml/yaml.contribution"

export default {
    name: "TextEditor",
    components: {
        Search,
        LayoutWithLeftPane,
        SampleData,
        SimpleLayout,
        SelectorModal,
        IssuesDialog
    },
    props: {
        defaultTestId: obj.strN,
        refreshTestTemplatesApiCall: {
            type: Function,
            required: false,
            default: async function() {
                let result = {}
                await issuesApi.fetchAllSubCategories().then(resp => {
                    result = resp
                })
                return result
            }

        },
        runTestForGivenTemplateApiCall: {
            type: Function,
            required: false,
            default: async function(textEditor, selectedUrl, sampleDataListForTestRun) {
                let result = {}
                await testingApi.runTestForTemplate(textEditor, selectedUrl, sampleDataListForTestRun).then(resp => {
                    result = resp
                })
                return result
            }
        },
        makeJsonApiCall: {
            type: Function,
            required: false,
            default: async function(selectedUrl) {
                let result = {}
                await inventoryApi.fetchSampleData(selectedUrl.url, selectedUrl.apiCollectionId, selectedUrl.method).then(resp => {
                    result = resp
                })
                return result
            }
        },
        setSelectedMethodOnClick: {
            type: Function,
            required: false,
            default: function(testId) {
                this.setSelectedMethod(testId)
            }
        }
    },
    data() {
        return {
            editorOptions: {
                language: "custom_yaml",
                minimap: { enabled: false },
                wordWrap: true,
                automaticLayout: true,
                colorDecorations: true,
                scrollBeyondLastLine: false,
                theme: "customTheme"
            },
            // UPDATE THIS LIST WHILE ADDING ANY NEW KEY
            keywords: [
                "id", "info", 
                "name", "description", "details", "impact", "category", "shortName", "displayName", "subCategory", "severity", "tags", "references",
                "response_code", "method", "url", "request_payload", "response_payload", "request_headers", "response_headers", "query_param", "api_collection_id", "source_ip", "destination_ip", "country_code",
                "regex", "eq", "neq", "gt", "gte", "lt", "lte", 
                "key", "value", "requests", "req", "res",
                "not_contains", "not_contains_either", "contains_jwt", "contains_all", "contains_either", "contains_either_cidr", "not_contains_cidr",
                "for_one", "or", "and", "extract", 
                "add_body_param", "modify_body_param", "delete_body_param", "add_query_param", "modify_query_param", "delete_query_param", 
                "modify_url", "modify_method", "replace_body", "add_header", "modify_header", "delete_header", "remove_auth_header", "follow_redirect", "replace_auth_header", 
                "api_selection_filters", "execute", "type", "auth", "validate", "authenticated", 
                "private_variable_context", "param_context", "endpoint_in_traffic_context",
                "sample_request_payload", "sample_response_payload", "sample_request_headers", "sample_response_headers", 
                "test_request_payload", "test_response_payload", "test_request_headers", "test_response_headers", "cwe", "cve"
            ],
            textEditor: null,
            testCategories: [],
            testsObj: {},
            customTestObj: {},
            businessLogicSubcategories: [],
            vulnerableRequests: [],
            mapTestToYaml: {},
            mapTestToStamp:{},
            totalAktoTests: 0,
            aktoToggle: false,
            customToggle: false,
            totalCustomTests: 0,
            IsEdited: false,
            defaultValue: '',
            messageJson: {},
            showDialogBox: false,
            titleBox: '',
            currentParam: '',
            runTest: false,
            showAllDetails: false,
            mapRequestsToId: {},
            selectedUrl: {},
            runTestObj: {
                isLoading: false,
                totalAttempts: 16,
                totalFoundAttempts: 12,
                runTime: "",
                vulnerability: "",
            },
            dialogBoxIssue: {},
            dialogBox: false,
            testingRunResult: {},
            testingRunHexId: null,
            lastEdited: -1,
            copyTestObj: {},
            copyCustomObj: {},
            defaultTest: this.defaultTestId || "REMOVE_TOKENS",
            defaultTestName: null,
            testInactive: false,
            currentCategory: '',
            allCustomTests: {},
            setTextId: {},
            subCatogoryMap: {},
            sampleDataListForTestRun: null
        }
    },
    methods: {
        findTestFromTestValue(testValue) {
            let aktoTest = Object.values(this.testsObj).map (x => x.all).flat().find(x=>x.value === testValue)
            let customTest = Object.values(this.customTestObj).map (x => x.all).flat().find(x=>x.value === testValue)

            if (aktoTest) {
                this.aktoToggle = true
                this.customToggle = false
                this.currentCategory = aktoTest.category
                return aktoTest
            }

            if (customTest) {
                this.aktoToggle = false
                this.customToggle = true
                this.currentCategory = customTest.category
                return customTest
            }

            return null
        },
        openGithubLink() {
            return window.open("https://github.com/akto-api-security/akto/tree/master/apps/dashboard/src/main/resources/inbuilt_test_yaml_files", "_blank")
        },
        async setTestInactive() {
            this.testInactive = !this.testInactive;
            this.lastEdited = func.prettifyEpoch(Date.now()/1000);
            await testingApi.setTestInactive(this.defaultTest, this.testInactive).then((res) => {
                window._AKTO.$emit('SHOW_SNACKBAR', {
                    show: true,
                    text: `Test set as ${this.testInactive ? "inactive" : "active"}`,
                    color: 'green'
                });
            })
            Object.values(this.testsObj).map (x => x.all).flat().forEach((x, i) => {
                if(x.value==this.defaultTest){
                    x.inactive = this.testInactive
                    this.mapTestToStamp[x.label] = this.lastEdited
                }
            })
            Object.values(this.customTestObj).map (x => x.all).flat().forEach((x, i) => {
                if(x.value==this.defaultTest){
                    x.inactive = this.testInactive
                    this.mapTestToStamp[x.label] = this.lastEdited
                }
            })
            this.copyTestObj = JSON.parse(JSON.stringify(this.testsObj))
            this.copyCustomObj = JSON.parse(JSON.stringify(this.customTestObj))
        },
        getFormValues(param, formValues) {
            if (param === 'choose') {
                this.selectedUrl = {
                    apiCollectionId: formValues.id,
                    url: formValues.url.url,
                    method: formValues.url.method
                }
                this.makeJson()
            } else if (param === 'save') {
                this.$store.dispatch('testing/addTestTemplate', { content: this.textEditor.getValue(), originalTestId: this.defaultTest }).then(async (resp)=>{
                    window._AKTO.$emit('SHOW_SNACKBAR', {
                        show: true,
                        text: "Test template added successfully!",
                        color: 'green'
                    });
                    await this.refreshTestTemplates()
                    this.setSelectedMethod(resp.finalTestId)
                })
            }
        },
        resetSelectedURL() {
            this.selectedUrl = {}
            this.messageJson = {}
            let testId = this.defaultTest
            if (this.mapRequestsToId[testId] && this.mapRequestsToId[testId][0]) {
                let obj = {
                    apiCollectionId: this.mapRequestsToId[testId][0].apiCollectionId,
                    url: this.mapRequestsToId[testId][0].url,
                    method: this.mapRequestsToId[testId][0].method._name
                }
                this.selectedUrl = obj
            }
            return
        },
        resetSampleData() {
            this.selectedUrl = {}
            this.messageJson = {}
            let testId = this.defaultTest
            if (this.mapRequestsToId[testId] && this.mapRequestsToId[testId][0]) {

                let obj = {
                    apiCollectionId: this.mapRequestsToId[testId][0].apiCollectionId,
                    url: this.mapRequestsToId[testId][0].url,
                    method: this.mapRequestsToId[testId][0].method._name
                }
                this.selectedUrl = obj
                this.makeJson()
            }
            this.selectedAnonymousOption = 'Sample data'
        },
        setMessageJson(result, doNotUpdateAPIjson) {
            this.messageJson = result.messageJson
            this.sampleDataListForTestRun = result.sampleDataListForTestRun
        },
        setSelectedMethod(testId, doNotUpdateAPIjson) {
            let test = this.findTestFromTestValue(testId)
            this.changeValue(test?.label ? test.label : null)
            this.defaultTest = testId
            this.testInactive = test?.inactive ? test.inactive : null

            this.runTest = false

            let pathname = window.location.pathname
            pathname = pathname.slice(0, pathname.lastIndexOf('/') + 1)
            window.history.pushState({ urlPath: pathname + testId }, "", pathname + testId)

            if (!(this.mapRequestsToId[testId] && this.mapRequestsToId[testId].length > 0)) {
                testId = Object.keys(this.mapRequestsToId)[0]
            }
            if (this.mapRequestsToId[testId] && this.mapRequestsToId[testId][0] && !doNotUpdateAPIjson) {
                this.selectedUrl = {}
                this.messageJson = {}
                let obj = {
                    apiCollectionId: this.mapRequestsToId[testId][0].apiCollectionId,
                    url: this.mapRequestsToId[testId][0].url,
                    method: this.mapRequestsToId[testId][0].method._name
                }
                this.selectedUrl = obj
                this.makeJson()
            }
        },
        async makeJson() {
            let resp = await this.makeJsonApiCall(this.selectedUrl)
            if (resp.sampleDataList.length > 0 && resp.sampleDataList[0].samples && resp.sampleDataList[0].samples.length > 0) {
                this.messageJson = { "message": resp.sampleDataList[0].samples[0], "highlightPaths": [] }
                this.sampleDataListForTestRun = resp.sampleDataList
            }
        },
        async runTestForGivenTemplate() {
            this.runTest = true
            let testStartTime = new Date()
            this.runTestObj.isLoading = true
            let resp = await this.runTestForGivenTemplateApiCall(this.textEditor.getValue(), this.selectedUrl, this.sampleDataListForTestRun)
            this.testingRunResult = resp["testingRunResult"]
            this.testingRunHexId = resp["testingRunHexId"]
            this.dialogBoxIssue = resp["testingRunIssues"]
            this.subCatogoryMap = resp["subCategoryMap"]

            if (this.dialogBoxIssue) {
                this.runTestObj.vulnerability = this.dialogBoxIssue.severity
                this.runTestObj.isLoading = false
            } else {//No issues found
                this.runTestObj.vulnerability = "No "
                this.runTestObj.isLoading = false
                this.dialogBoxIssue = {}
            }
            this.runTime = Math.round((new Date().getTime() - testStartTime.getTime()) / 1000) + " seconds"
        },
        showAllAttempts() {
            this.dialogBox = true
        },
        saveTestTemplate() {
            if (this.IsEdited) {
                this.$store.dispatch('testing/addTestTemplate', { content: this.textEditor.getValue(), originalTestId: this.defaultTest }).then(async (resp) => {
                    window._AKTO.$emit('SHOW_SNACKBAR', {
                        show: true,
                        text: "Test template added successfully!",
                        color: 'green'
                    });
                    await this.refreshTestTemplates()
                    this.setSelectedMethod(resp.finalTestId, true)
                })
            }
        },
        openDialogBox(param) {
            this.currentParam = param
            if (param === "save") {
                if (this.IsEdited) {
                    // show collections
                    this.showDialogBox = true
                    this.titleBox = "Update your test details"
                }
            } else {
                // show urls
                this.showDialogBox = true
                this.titleBox = "Select API from inventory"
            }
        },
        closeDialog() {
            this.showDialogBox = false
            this.titleBox = ''
        },
        toggleListDisplay(param) {
            if (param == 'akto') {
                this.aktoToggle = !this.aktoToggle
            } else {
                this.customToggle = !this.customToggle
            }
        },
        createEditor() {
            editorSetup.registerLanguage()
            editorSetup.setTokenizer()
            editorSetup.setEditorTheme()
            editorSetup.setAutoComplete(this.keywords)
            this.textEditor = editor.create(this.$refs.editor, this.editorOptions)
            this.textEditor.addAction({
                id: "giveTypingEffect",
                label: "Give typing effect",
                keybindings: [KeyMod.Ctrl | KeyCode.KeyB],
                run: () => {
                    this.giveTypingEffect(false, true);
                },
            });
            editorSetup.findErrors(this.textEditor, this.keywords)
        },
        giveTypingEffect() {
            let str = this.textEditor.getValue()
            let prevStr = "";
            this.textEditor.setValue(prevStr)
            let i = 0;
            let intI = setInterval(() => {
                prevStr += str[i]
                i++;
                
                this.textEditor.setValue(prevStr + "\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n\n")
                if (i == str.length) {
                    clearInterval(intI)
                }
            }, 10);

        },
        findSuffixForNewTest(testId) {
            let aktoTests = Object.values(this.testsObj).map (x => x.all).flat().filter(x=>x.value.indexOf(testId) == 0)            
            let customTests = Object.values(this.testsObj).map (x => x.all).flat().filter(x=>x.value.indexOf(testId) == 0)            

            let currMaxIndex = Math.max(0, ...[...aktoTests, ...customTests].map(x=>+(x.value.replace(testId+"_CUSTOM_", ""))).filter(isFinite))

            

            return currMaxIndex+1
        },
        changeValue(testName) {
            this.setTextId = {}
            this.lastEdited = -1
            this.defaultTestName = testName
            if(this.allCustomTests[testName]){
                this.setTextId = this.allCustomTests[testName]
            }
            if (!this.mapTestToYaml[testName]) {
                this.textEditor.setValue('')
            } else {
                this.textEditor.setValue(this.mapTestToYaml[testName])
                this.defaultValue = this.textEditor.getValue()
                this.IsEdited = false
                this.lastEdited = this.mapTestToStamp[testName]
            }
        },
        setSearchText(val) {
            this.testsObj = JSON.parse(JSON.stringify(this.copyTestObj))
            this.customTestObj = JSON.parse(JSON.stringify(this.copyCustomObj))
            this.totalCustomTests = this.searchObjects(this.customTestObj, val.toLowerCase())
            this.totalAktoTests = this.searchObjects(this.testsObj, val.toLowerCase())
        },
        searchObjects(objects,searchString) {
            let totalTests = 0
            for (let key in objects) {
                if (objects.hasOwnProperty(key)) {
                    let obj = objects[key]
                    let arr = obj.all.filter((test)=>{
                        let name = test.label.toString().toLowerCase().replace(/ /g, "")
                        let category = test.category.toString().toLowerCase().replace(/ /g, "")
                        let content = this.mapTestToYaml[test.label].toString().toLowerCase();
                        if(name.includes(searchString) || category.includes(searchString) || content.includes(searchString)){
                            totalTests++
                            return true
                        }
                    })
                    objects[key].all = arr
                }
            }
            return totalTests
        },
        populateMapCategoryToSubcategory() {
            let ret = {}
            this.customTestObj={}
            this.mapTestToYaml = {}
            this.totalCustomTests = 0
            this.allCustomTests = {}
            this.businessLogicSubcategories.forEach(x => {
                if (!ret[x.superCategory.name]) {
                    ret[x.superCategory.name] = { all: [] }
                }
                let obj = {
                    label: x.testName,
                    value: x.name,
                    icon: "$aktoWhite",
                    category: x.superCategory.displayName,
                    inactive: x.inactive
                }
                this.mapTestToYaml[x.testName] = x.content
                this.mapTestToStamp[x.testName] = func.prettifyEpoch(x.updatedTs)
                if(x.templateSource._name === "CUSTOM"){
                    let customVal = {
                        name: x._name,
                        category: x.superCategory.name,
                        inactive: x.inactive
                    }
                    this.allCustomTests[x.testName] = customVal
                    this.totalCustomTests++
                    if (!this.customTestObj[x.superCategory.name]) {
                        this.customTestObj[x.superCategory.name] = { all: [] }
                    }
                    this.customTestObj[x.superCategory.name].all.push(obj)
                }else{
                    ret[x.superCategory.name].all.push(obj)
                }
                this.copyCustomObj = JSON.parse(JSON.stringify(this.customTestObj))
            })
            this.totalAktoTests = this.businessLogicSubcategories.length - this.totalCustomTests
            return ret
        },
        mapRequests() {
            this.mapRequestsToId = {}
            this.vulnerableRequests.forEach((x) => {
                let methodArr = x.templateIds
                methodArr.forEach((method) => {
                    if (!this.mapRequestsToId[method]) {
                        this.mapRequestsToId[method] = []
                    }

                    this.mapRequestsToId[method].push(x.id)
                })
            })
        },
        selectedTestCategories(object) {
            let arr = []
            this.testCategories.forEach((test) => {
                if (object[test.name] && object[test.name].all.length > 0) {
                    arr.push(test)
                }
            })
            return arr
        },
        async refreshTestTemplates() {
            let _this = this
            let resp = await this.refreshTestTemplatesApiCall()
            _this.testCategories = resp.categories
            _this.businessLogicSubcategories = resp.subCategories
            _this.vulnerableRequests = resp.vulnerableRequests
            _this.testsObj = _this.populateMapCategoryToSubcategory()
            _this.copyTestObj = JSON.parse(JSON.stringify(_this.testsObj))
            _this.mapRequests()
        }
    },
    async mounted() {
        this.createEditor()
        let _this = this
        await this.refreshTestTemplates()
        this.setSelectedMethod(this.defaultTest)
        _this.textEditor.onDidChangeModelContent(() => {
            _this.IsEdited = _this.textEditor.getValue() !== _this.defaultValue
        })
    },
    computed: {
        json() {
            return {
                "message": JSON.parse(this.messageJson["message"]),
                title: this.messageJson["title"],
                "highlightPaths": this.messageJson["highlightPaths"],
            }
        },
        showSelector(){
            return this.defaultValue.length > 0
        },
        showdetailsIcon(){
            return this.showAllDetails ? "$fas_angle-double-down" : "$fas_angle-double-up"
        },
        getTextColor() {
            switch (this.runTestObj.vulnerability) {
                case 'CRITICAL':
                case 'HIGH':
                    return 'var(--hexColor3)';
                case 'MEDIUM':
                    return 'var(--hexColor2)';
                case 'LOW':
                    return 'var(--hexColor1)';
                default:
                    return 'var(--hexColor5)';
            }

        },
    }
}
</script>

<style scoped>

.tests-category-container.v-list-group--active >>> .v-list-group__header__prepend-icon {
    transform: rotate(90deg);
}
.req-resp-col >>> .copy-icon {
  display: none;
}

.req-resp-col >>> .sample-data-title {
  display: none;
}

.req-resp-col >>> .sample-data-container {
  background: #FFFFFF;
}

.test-editor-panel >>> .akto-left-pane {
    padding-left: 4px;
    padding-right: 4px;
}

.tests-category-container>>>.v-list-item__icon {
    display: flex !important;
    align-items: center !important;
}

.monaco-editor>>>.margin-view-overlays {
    background: var(--hexColor44);
}

.req-resp-col::-webkit-scrollbar {
    display: none;
}

.test-col::-webkit-scrollbar {
    display: none;
}

.req-box-container>>>.sample-data-container::-webkit-scrollbar {
    display: none;
}

.req-box-container>>>.sample-data-container {
    max-height: 60vh;
    overflow-y: auto;
}

.test-editor-panel >>> .akto-left-pane {
    border: none !important;
}

</style>

<style lang="scss" scoped>
.test-col {
    flex: 1.5;
    max-height: calc(100vh - 72px);
    overflow-y: scroll;
}

.editor-col {
    flex: 3;
    border-right: 4px solid var(--hexColor43);
    border-left: 4px solid var(--hexColor43);
    max-width: 50vw;

    .editor-header-container {
        display: flex;
        padding: 4px 24px;
        justify-content: space-between;
        height: 48px;

        .file-title {
            display: flex;
            gap: 4px;
            align-items: center;
        }

        .test-subheading{
            font-size: 12px;
        }

        .last-edited {
            color: var(--themeColorDark10);
        }
    }

    .monaco-editor {
        border-top: 0.2px solid var(--lighten2);
    }
}

.req-resp-col {
    flex: 3;
    height: calc(100vh - 120px);
    display: flex;
    overflow: hidden;
    flex-direction: column;

    .empty-container {
        padding: 42px 14px;
        font-size: 18px;
        font-weight: 600;
        color: var(--themeColorDark);
        flex: 16;
    }

    .req-box-container {
        flex: 16;
    }

    .footer-div {
        flex: 1;

        .show-run-test,
        .vulnerable-container {
            position: absolute;
            bottom: 0;
            padding: 10px 14px;
            border-top: 1px solid var(--hexColor44);
            width: -webkit-fill-available;
            font-weight: 500;
            font-size: 14px;
            color: var(--themeColorDark);
        }

        .vulnerable-container {
            display: flex;
            justify-content: space-between;
            align-items: center;

            .show-side {
                display: flex;
                align-items: center;
                gap: 4px;
            }
        }

        .show-complete-details {
            display: flex;
            flex-direction: column;
            padding: 12px 20px;
            gap: 16px;
            border-top: 1px solid var(--hexColor44);
            margin-top: 12px;
        }

        .show-attempts-box {
            box-shadow: none;
            border: 1px solid var(--black);
            background: var(--white) !important;
            color: var(--themeColorDark) !important;
            width: 180px;
        }
    }
}

.file-name {
    font-size: 14px;
    color: var(--themeColorDark);
    font-weight: 500;
}

.tests-container {
    display: flex;
    flex-direction: column;
    gap: 20px;
}

.main-list-title {
    // width: 260px;
    padding: 0px 2px;
    display: flex;
    gap: 2px;
    align-items: center;
    cursor: pointer;
    .shift-right{
        position: absolute;
        right: 20px;
    }
}

.title-name {
    font-size: 14px;
    font-weight: 500;
    color: var(--themeColorDark);
    display: flex;
    align-items: center;
    gap: 7px;
}

.tests-list {
    margin: -20px 0px 10px 0px;

    .tests-category-container {
        padding-left: 2px;

        .test-category-name {
            display: flex;
            gap: 7px;
            align-items: center;
            margin-left: 6px;
            font-size: 12px;
            color: var(--themeColorDark);
            font-weight: 500;

            .test-icons {
                color: var(--black);
            }
        }

        .test-container {
            min-height: 22px !important;
            cursor: pointer;
            margin: 0 !important;

            .test-container-active {
                background: var(--hexColor44);
            }

            .test-name {
                color: var(--themeColorDark);
                font-size: 12px;
                font-weight: 500;
                padding-left: 36px;
            }
        }
    }

    .tests-category-container-active {
        .test-icons {
            color: var(--themeColor) !important;
        }
    }
}

.total-tests {
    font-size: 12px;
    color: var(--themeColorDark);
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

    .url-name {
        font-size: 12px !important;
        word-break: break-all;
    }
}
.show-overflow {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    line-height: 120%;
    max-width: 400px;
}

.show-tooltip {
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
}
</style>
