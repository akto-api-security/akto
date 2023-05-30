<template>
    <div>
        <simple-layout title="Test Editor" version="Beta">
            <template>
                <div class="d-flex test-editor-panel">
                    <div class="test-col">
                        <layout-with-left-pane>
                            <search class="search-box" placeholder="Search Tests" @changed="setSearchText" />
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
                                        class="tests-category-container" active-class="tests-category-container-active">
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
                                            class="test-container">
                                            <v-list-item-content>
                                                <v-list-item-title v-text="test.label" class="test-name show-overflow"
                                                    @click="changeValue(test.label), setSelectedMethod(test.value)" />
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
                                        class="tests-category-container" active-class="tests-category-container-active">
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
                                            class="test-container">
                                            <v-list-item-content>
                                                <v-list-item-title v-text="test.label" class="test-name show-overflow"
                                                    @click="changeValue(test.label), setSelectedMethod(test.value)" />
                                            </v-list-item-content>
                                        </v-list-item>
                                    </v-list-group>
                                </v-list>
                            </div>
                        </layout-with-left-pane>
                    </div>
                    <div class="editor-col">
                        <div class="editor-header-container">
                            <div class="file-title">
                                <span class="file-name">
                                    Test editor
                                </span>
                                <v-icon :style="{ 'cursor': 'pointer' }" size=14>$githubIcon</v-icon>
                                <span class="last-edited" v-if="lastEdited !== -1"> last edited {{ lastEdited }}</span>
                            </div>
                            <div class="file-title" :style="{ cursor: IsEdited ? 'pointer' : '' }"
                                @click="openDialogBox('save')">
                                <v-icon :style="{ opacity: IsEdited ? '1' : '0.4' }" size=16>$saveIcon</v-icon>
                                <span class="file-name" :style="{ opacity: IsEdited ? '1' : '0.2' }">Save</span>
                            </div>
                        </div>
                        <div ref="editor" style="height: calc(100vh - 120px);" class="monaco-editor"></div>
                        <selector-modal :show-dialog="showDialogBox" :title="titleBox" @closeDialog="closeDialog"
                            :currentParam="currentParam" :test-categories="testCategories"
                            @get_form_values="getFormValues" />
                    </div>
                    <div class="req-resp-col">
                        <div class="req-box-container" v-if="messageJson.message">
                            <sample-data :json="json" requestTitle="Request" responseTitle="Response" :tabularDisplay="true"
                                @run_tests="runTestForGivenTemplate()" :isLoading="runTestObj.isLoading"/>
                        </div>
                        <div class="empty-container" v-else>
                            No Values Yet !!
                        </div>
                        <div class="select-url" @click="openDialogBox('choose')">
                            <v-icon size=12>$fas_check</v-icon>
                            <span class="file-name url-name show-overflow">{{ selectedUrl.url }}</span>
                        </div>
                        <div class="footer-div">
                            <div class="show-run-test" v-if="!runTest">
                                Run test to see Results
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
                                            :style="{ transform: showAllDetails ? 'rotate(180deg)' : '', transition: 'all 0.2s linear', cursor: 'pointer' }"
                                            @click="showAllDetails = !showAllDetails">
                                            $fas_angle-double-down
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
        <issues-dialog :similarlyAffectedIssues="similarlyAffectedIssues" :openDetailsDialog="dialogBox"
            :testingRunResult="testingRunResult" :subCatogoryMap="subCatogoryMap" :issue="dialogBoxIssue"
            @closeDialogBox="(dialogBox = false)">
        </issues-dialog>
    </div>
</template>

<script>

import { editor } from "monaco-editor/esm/vs/editor/editor.api"
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
    data() {
        return {
            editorOptions: {
                language: "yaml",
                minimap: { enabled: false },
                wordWrap: true,
                automaticLayout: true,
                colorDecorations: true,
                scrollBeyondLastLine: false
            },
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
            similarlyAffectedIssues: [],
            dialogBoxIssue: {},
            dialogBox: false,
            testingRunResult: {},
            testingRunHexId: null,
            lastEdited: -1,
            copyTestObj: {},
            copyCustomObj: {},
            defaultTest: "REMOVE_TOKENS"
        }
    },
    methods: {
        getFormValues(param, formValues) {
            if (param === 'choose') {
                this.selectedUrl = {
                    apiCollectionId: formValues.name,
                    url: formValues.url.url,
                    method: formValues.url.method
                }
                this.makeJson()
            } else if (param === 'save') {
                this.$store.dispatch('testing/addTestTemplate', { content: this.textEditor.getValue(), testId: formValues.name, testCategory: formValues.category })
            }
        },
        setSelectedMethod(testId) {
            this.selectedUrl = {}
            this.messageJson = {}
            if (!(this.mapRequestsToId[testId] && this.mapRequestsToId[testId].length > 0)) {
                testId = this.defaultTest
            }
            let obj = {
                apiCollectionId: this.mapRequestsToId[testId][0].apiCollectionId,
                url: this.mapRequestsToId[testId][0].url,
                method: this.mapRequestsToId[testId][0].method._name
            }
            this.selectedUrl = obj
            this.makeJson()
        },
        async makeJson() {
            await inventoryApi.fetchSampleData(this.selectedUrl.url, this.selectedUrl.apiCollectionId, this.selectedUrl.method).then((resp) => {
                if (resp.sampleDataList.length > 0 && resp.sampleDataList[0].samples && resp.sampleDataList[0].samples.length > 0) {
                    this.messageJson = { "message": resp.sampleDataList[0].samples[0], "highlightPaths": [] }
                }
            })
        },
        async runTestForGivenTemplate() {
            this.runTest = true
            let testStartTime = new Date()
            this.runTestObj.isLoading = true
            await testingApi.runTestForTemplate(this.textEditor.getValue(), this.selectedUrl).then(resp => {
                this.testingRunHexId = resp["testingRunHexId"]
                let __topThis = this
                if (this.testingRunHexId) {//Test run started
                    let stopInterval = false
                    let interval = setInterval(async () => {
                        if (stopInterval) {
                            clearInterval(interval)
                        } else {
                            let _this = this
                            await testingApi.fetchTestingRunResultFromTestingRun(__topThis.testingRunHexId).then(async respResult => {
                                let run = respResult["testingRunResult"]
                                if (run) {
                                    _this.testingRunResult = run
                                    stopInterval = true
                                    __topThis.runTestObj.runTime =  Math.round((new Date().getTime() - testStartTime.getTime())/1000) + " seconds"
                                    await testingApi.fetchIssueFromTestRunResultDetails(_this.testingRunResult.hexId).then(async respIssue => {
                                        __topThis.dialogBoxIssue = respIssue['runIssues']
                                        if (__topThis.dialogBoxIssue) {
                                            await issuesApi.fetchAffectedEndpoints(__topThis.dialogBoxIssue.id).then(affectedResp => {
                                                __topThis.similarlyAffectedIssues = affectedResp['similarlyAffectedIssues']
                                                __topThis.runTestObj.vulnerability = "HIGH"
                                                __topThis.runTestObj.isLoading = false
                                            })
                                        } else {//No issues found
                                            __topThis.runTestObj.vulnerability = "No "
                                            __topThis.runTestObj.isLoading = false
                                        }
                                    })
                                }
                            })
                        }
                    }, 2000)
                }
            })
        },
        showAllAttempts() {
            this.dialogBox = true
        },
        openDialogBox(param) {
            this.currentParam = param
            if (param === "save") {
                if (this.IsEdited) {
                    // show collections
                    this.showDialogBox = true
                    this.titleBox = "Update Your Test Details"
                }
            } else {
                // show urls
                this.showDialogBox = true
                this.titleBox = "Select API from Inventory"
            }
        },
        closeDialog() {
            this.showDialogBox = false
            this.titleBox = ''
        },
        openTestLink() {
            window.open("https://chat.openai.com/")
        },
        toggleListDisplay(param) {
            if (param == 'akto') {
                this.aktoToggle = !this.aktoToggle
            } else {
                this.customToggle = !this.customToggle
            }
        },
        createEditor() {
            this.textEditor = editor.create(this.$refs.editor, this.editorOptions)
        },
        getEditorValue() {
            this.$store.dispatch('testing/addTestTemplate', { content: this.textEditor.getValue(), apiKeyInfo: this.selectedUrl })
            return this.textEditor.getValue()
        },
        changeValue(testName) {
            this.lastEdited = -1
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
            this.businessLogicSubcategories.forEach(x => {
                if (!ret[x.superCategory.name]) {
                    ret[x.superCategory.name] = { all: [] }
                }
                let obj = {
                    label: x.testName,
                    value: x.name,
                    icon: "$aktoWhite",
                    category: x.superCategory.displayName
                }
                this.mapTestToYaml[x.testName] = x.content
                this.mapTestToStamp[x.testName] = func.prettifyEpoch(x.updatedTs)
                if(x.templateSource._name === "CUSTOM"){
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
    },
    async mounted() {
        this.createEditor()
        await this.$store.dispatch('issues/fetchAllSubCategories')
        let _this = this
        await issuesApi.fetchAllSubCategories().then(resp => {
            _this.testCategories = resp.categories
            _this.businessLogicSubcategories = resp.subCategories
            _this.vulnerableRequests = resp.vulnerableRequests
            _this.testsObj = _this.populateMapCategoryToSubcategory()
            _this.copyTestObj = JSON.parse(JSON.stringify(_this.testsObj))
            _this.mapRequests()
        })
        _this.textEditor.onDidChangeModelContent(() => {
            _this.IsEdited = _this.textEditor.getValue() !== _this.defaultValue
        })
    },
    computed: {
        subCatogoryMap: {
            get() {
                return this.$store.state.issues.subCatogoryMap
            }
        },
        json() {
            return {
                "message": JSON.parse(this.messageJson["message"]),
                title: this.messageJson["title"],
                "highlightPaths": this.messageJson["highlightPaths"],
            }
        },
        getTextColor() {
            switch (this.runTestObj.vulnerability) {
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
.search-box>>>.theme--light.v-text-field>.v-input__control>.v-input__slot:before {
    border: none !important;
}

.test-editor-panel >>> .akto-left-pane {
    padding-left: 4px;
    padding-right: 4px;
}

.search-box>>>.v-text-field input {
    font-size: 16px !important;
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
    overflow-y: scroll;
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
        padding: 18px 24px 8px 24px;
        justify-content: space-between;

        .file-title {
            display: flex;
            gap: 4px;
            align-items: center;
        }

        .last-edited {
            font-size: 12px;
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
            font-size: 16px;
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

.search-box {
    border-radius: 8px;
    border: 1px solid var(--borderColor);
    height: 44px;
    align-items: center;
    justify-content: center;
    padding: 10px 12px;
    margin-bottom: 20px;
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
.show-overflow{
    overflow: hidden;
    white-space: nowrap;
    text-overflow: ellipsis;
    line-height: 120%;

    &:hover {
        overflow: visible;
        white-space: normal;
    }
}
</style>