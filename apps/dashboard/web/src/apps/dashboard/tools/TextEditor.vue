<template>
    <simple-layout title="Text Editor">
        <template>
            <div class="d-flex">
                <div class="test-col">
                    <layout-with-left-pane>
                        <search class="search-box" placeholder="Search Tests" @changed="setSearchText" />
                        <div class="tests-container">
                            <div class="main-list-title" @click="toggleListDisplay('custom')">
                                <v-icon size=18 :style="{transform: customToggle ? 'rotate(90deg)' : '', transition: 'all 0.2s linear' , color : 'var(--lighten2)'}">$fas_angle-right</v-icon>
                                <span class="title-name">
                                    <v-icon size=18 :style="customToggle ? {'color' : 'var(--themeColor)'} : {'color' : 'var(--black)'}">$far_folder</v-icon>
                                    Custom
                                </span>

                                <span class="total-tests">
                                    {{ totalCustomTests }}
                                </span>
                            </div>

                            <div class="main-list-title" @click="toggleListDisplay('akto')">
                                <v-icon size=18 :style="{transform: aktoToggle ? 'rotate(90deg)' : '', transition: 'all 0.2s linear' , color : 'var(--lighten2)'}">$fas_angle-right</v-icon>
                                <span class="title-name">
                                    <v-icon size=18 :style="aktoToggle ? {'color' : 'var(--themeColor)'} : {'color' : 'var(--black)'}">$far_folder</v-icon>
                                    Akto Default
                                </span>

                                <span class="total-tests">
                                    {{ totalTests }}
                                </span>
                            </div>
                            <v-list dense nav v-if="aktoToggle" class="tests-list">
                                <v-list-group
                                    v-for="item in selectedTestCategories"
                                    :key="item.displayName"
                                    class="tests-category-container"
                                    active-class="tests-category-container-active"
                                >
                                    <template v-slot:prependIcon>
                                        <v-icon color="var(--lighten1)" size=16>$fas_angle-right</v-icon>
                                    </template>
                                    <template v-slot:appendIcon>
                                        <span class="total-tests">{{ testsObj[item.name].all.length }}</span>
                                    </template>
                                    <template v-slot:activator>
                                        <v-list-item-content>
                                            <v-list-item-title>
                                                <div class="test-category-name">
                                                    <v-icon class="test-icons">$far_folder</v-icon>
                                                    {{ item.name }}
                                                </div>
                                            </v-list-item-title>
                                        </v-list-item-content>
                                    </template>

                                    <v-list-item
                                        v-for="(test,index) in testsObj[item.name].all"
                                        :key="index"
                                        class="test-container"
                                    >
                                        <v-list-item-content>
                                            <v-list-item-title v-text="test.label" class="test-name" @click="changeValue(test.label)"></v-list-item-title>
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
                                Lorem Ipsum
                            </span>
                            <v-icon :style="{'cursor' : 'pointer'}" size=14 @click="openTestLink()">$githubIcon</v-icon>
                        </div>
                        <div class="file-title">
                            <v-icon :style="{opacity: IsEdited ? '1' : '0.4'}" size=14>$aktoWhite</v-icon>
                            <span class="file-name" :style="{opacity: IsEdited ? '1' : '0.2' ,'cursor' : 'pointer'}">Save</span>
                        </div>
                    </div>
                    <div ref="editor" style="width: 625px; height: 800px;" class="monaco-editor"></div>
                </div>
                <div :style="{'width': '650px' , 'height' : '800px' , 'overflow-y' : 'scroll'}" class="req-resp-col">
                    <sample-data :json="json" requestTitle="Request" responseTitle="Response" :tabularDisplay="true"/>
                    <div class="select-url">
                        <v-icon size=12>$fas_check</v-icon>
                        <span class="file-name url-name">Show Url Name here</span>
                    </div>
                </div>
            </div>
        </template>
    </simple-layout>
</template>

<script>

import {editor} from "monaco-editor/esm/vs/editor/editor.api"
import Search from '../shared/components/inputs/Search.vue';
import LayoutWithLeftPane from '../layouts/LayoutWithLeftPane.vue';
import issuesApi from "../views/issues/api"
import SampleData from '../shared/components/SampleData.vue';
import SimpleLayout from '../layouts/SimpleLayout.vue';

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
    components:{
        Search,
        LayoutWithLeftPane,
        SampleData,
        SimpleLayout
    },
    data(){
        let message = {"method":"OPTIONS","requestPayload":"","responsePayload":null,"ip":"null","source":"HAR","type":"http/2.0","akto_vxlan_id":"1677246598","path":"https://metrics.api.drift.com/monitoring/metrics/widget/init/v2","requestHeaders":"{\"sec-fetch-mode\":\"cors\",\"referer\":\"https://js.driftt.com/\",\"sec-fetch-site\":\"cross-site\",\"accept-language\":\"en-US,en;q=0.9\",\"origin\":\"https://js.driftt.com\",\"access-control-request-method\":\"POST\",\"accept\":\"*/*\",\":method\":\"OPTIONS\",\":scheme\":\"https\",\"access-control-request-headers\":\"authorization,content-type\",\":path\":\"/monitoring/metrics/widget/init/v2\",\":authority\":\"metrics.api.drift.com\",\"accept-encoding\":\"gzip, deflate, br\",\"sec-fetch-dest\":\"empty\",\"user-agent\":\"Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/109.0.0.0 Safari/537.36\"}","responseHeaders":"{\"allow\":\"POST,OPTIONS\",\"date\":\"Fri, 24 Feb 2023 13:51:34 GMT\",\"content-length\":\"13\",\"server\":\"istio-envoy\",\"x-envoy-upstream-service-time\":\"1\",\"access-control-allow-headers\":\"origin, content-type, accept, authorization, auth-token, uber-trace-id, x-amzn-oidc-data, x-version\",\"access-control-allow-methods\":\"GET, POST, PUT, DELETE, OPTIONS, HEAD, PATCH\",\"strict-transport-security\":\"max-age=31536000; includeSubDomains\",\"access-control-expose-headers\":\"X-Results-Total-Count,X-Page-Info\",\"access-control-allow-origin\":\"*\",\"access-control-allow-credentials\":\"true\",\"access-control-max-age\":\"1209600\",\"requestid\":\"driftd26a1234f4392c3e80c91029004\",\"content-type\":\"text/plain\"}","time":"1677246694","contentType":"text/plain","akto_account_id":"1000000","statusCode":"200","status":""}
        return{
            editorOptions : {
                language: "yaml",
                minimap: { enabled: false },
                wordWrap: true,
                automaticLayout:true,
                colorDecorations:true,
                scrollBeyondLastLine:false,
            },
            textEditor:null,
            searchText: "",
            testCategories: [],
            testsObj:{},
            businessLogicSubcategories:[],
            mapTestToYaml : {},
            totalTests: 0,
            aktoToggle: false,
            customToggle: false,
            totalCustomTests: 0,
            IsEdited: false,
            defaultValue: '',
            messageJson: message
        }
    },
    methods: {
        openTestLink(){
            window.open("https://chat.openai.com/")
        },
        toggleListDisplay(param){
            if(param == 'akto'){
                this.aktoToggle = !this.aktoToggle
            }else{
                this.customToggle = !this.customToggle
            }
        },
        createEditor(){
            this.textEditor = editor.create(this.$refs.editor,this.editorOptions)
        },
        getEditorValue(){
            this.$store.dispatch('testing/addTestTemplate',{content:this.textEditor.getValue()})
            return this.textEditor.getValue()
        },
        changeValue(testName){
            if(!this.mapTestToYaml[testName]){
                this.textEditor.setValue('')
            }else{
                this.textEditor.setValue(this.mapTestToYaml[testName])
                this.defaultValue = this.textEditor.getValue()
                this.IsEdited = false
            }
        },
        setSearchText(val){
            this.searchText = val
        },
        populateMapCategoryToSubcategory() {
            let ret = {}
            this.mapTestToYaml = {}
            this.businessLogicSubcategories.forEach(x => {
                if (!ret[x.superCategory.name]) {
                    ret[x.superCategory.name] = { all: []}
                }
                let obj = {
                    label: x.testName,
                    value: x.name,
                    icon: "$aktoWhite"
                }
                ret[x.superCategory.name].all.push(obj)
            })
            this.totalTests = this.businessLogicSubcategories.length
            return ret
        },
        mapTestToYamlFunc(){
            this.businessLogicSubcategories.forEach(x => {
                this.mapTestToYaml[x.testName] = x.content
            })
        }
    },
    async mounted() {
        this.createEditor()
        let _this = this
        issuesApi.fetchAllSubCategories().then(resp => {
            _this.testCategories = resp.categories
            _this.businessLogicSubcategories = resp.subCategories
            _this.testsObj = _this.populateMapCategoryToSubcategory()
            this.mapTestToYamlFunc()
        })
        _this.textEditor.onDidChangeModelContent(() =>{
            _this.IsEdited = _this.textEditor.getValue() !== _this.defaultValue
        })
    },
    computed:{
        json(){
            return {
                "message": this.messageJson,
                title: "Aryan",
                "highlightPaths": [],
            }
        },
        selectedTestCategories(){
            let arr = []
            this.testCategories.forEach((test)=>{
                if(this.testsObj[test.name]){
                    arr.push(test)
                }
            })
            return arr
        }
    }
}
</script>

<style scoped>
    .search-box >>> .theme--light.v-text-field>.v-input__control>.v-input__slot:before{
        border: none !important;
    }
    .search-box >>> .v-text-field input {
        font-size: 16px !important;
    }
    .tests-category-container >>> .v-list-item__icon{
        display: flex !important;
        align-items: center !important;
    }

    .tests-category-container >>> .v-list-group__header__append-icon {
        position: absolute;
        right: -35px;
    }
    .monaco-editor >>> .margin-view-overlays{
        background: var(--hexColor44);
    }
    .req-resp-col::-webkit-scrollbar {
        display: none;
    }
    .test-col::-webkit-scrollbar {
        display: none;
    }
</style>

<style lang="scss" scoped>
    .test-col {
        width: 330px;
        height: 800px;
    }
    .editor-col {
        width: 640px;
        border-right: 4px solid var(--hexColor43);
        border-left: 4px solid var(--hexColor43);
        .editor-header-container {
            display: flex;
            padding: 18px 24px 8px 24px;
            justify-content: space-between;
            .file-title{
                display: flex;
                gap: 4px;
                align-items: center;
            } 
        }
        .monaco-editor{
            border-top: 0.2px solid var(--lighten2);
        }
    }
    .file-name{
        font-size: 14px;
        color: var(--themeColorDark);
        font-weight: 500;
    }
    .search-box{
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
        width: 260px;
        padding: 0px 6px;
        display: flex;
        gap: 10px;
        align-items:center;
        cursor: pointer;

        .title-name{
            font-size: 16px;
            font-weight: 500;
            color: var(--themeColorDark);
            display: flex;
            align-items: center;
            gap: 7px;
        }
    }

    .tests-list{
        margin: -20px 0px 10px 0px ;
        .tests-category-container {
            padding-left: 22px;
            .test-category-name {
                display: flex;
                gap: 7px;
                align-items: center;
                margin-left: 6px;
                font-size: 14px;
                color: var(--themeColorDark);
                font-weight: 500;
                .test-icons{
                    color: var(--black) ;
                }
            }
            .test-container{
                min-height: 22px !important;
                cursor: pointer;
                margin: 0 !important;

                .test-name{
                    color: var(--themeColorDark) ;
                    font-size: 12px ;
                    font-weight: 500;
                    padding-left: 62px;
                    overflow:hidden;
                    white-space: nowrap;

                    &:hover{
                        overflow: visible; 
                        white-space: normal; 
                    }
                }
            }
        }
        .tests-category-container-active{
            .test-icons{
                color:var(--themeColor) !important;
            }
        }
    }
    .total-tests{
        position: absolute;
        right: 34px;
        font-size: 16px;
        color: var(--themeColorDark);
    }
    .select-url {
        max-width: 250px;
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
        gap:4px;
        cursor: pointer;
        .url-name{
            font-size: 12px !important;
            overflow:hidden;
            white-space: nowrap;
            text-overflow: ellipsis;
            word-break: break-all;
            line-height: 120%;
            &:hover{
                overflow: visible; 
                white-space: normal; 
            }
        }
    }
</style>