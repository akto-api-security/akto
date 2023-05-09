<template>
    <div class="d-flex">
        <div class="test-col">
            <layout-with-left-pane>
                <search placeholder="Search Tests" @changed="setSearchText" />
                <div class="tests-container">
                    <v-list dense nav>
                        <v-list-group
                            v-for="item in selectedTestCategories"
                            :key="item.displayName"
                            class="tests-category-container"
                            active-class="tests-category-container-active"
                        >
                            <template v-slot:appendIcon>
                                <v-icon >$fas_angle-down</v-icon>
                            </template>
                            <template v-slot:activator>
                                <v-list-item-content>
                                    <v-list-item-title v-text="item.displayName"></v-list-item-title>
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
            <div ref="editor" style="width: 800px; height: 800px;" >
                <v-btn @click="getEditorValue">Test</v-btn>
            </div>
        </div>
        <div class="req-resp-col">

        </div>
    </div>
</template>

<script>

import {editor} from "monaco-editor/esm/vs/editor/editor.api"
import Search from '../dashboard/shared/components/inputs/Search.vue';
import LayoutWithLeftPane from '../dashboard/layouts/LayoutWithLeftPane.vue';

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

import issuesApi from "../dashboard/views/issues/api"

export default {
    name: "TextEditor",
    components:{
        Search,
        LayoutWithLeftPane
    },
    data(){
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
            testSourceConfig: [],
            testsObj:{},
            businessLogicSubcategories:[],
            mapTestToYaml : {},
        }
    },
    methods: {
        createEditor(){
            this.textEditor = editor.create(this.$refs.editor,this.editorOptions)
        },
        getEditorValue(){
            console.log(this.textEditor.getValue())
            this.$store.dispatch('testing/addTestTemplate',{content:this.textEditor.getValue()})
            return this.textEditor.getValue()
        },
        changeValue(testName){
            this.textEditor.setValue(this.mapTestToYaml[testName])
        },
        setSearchText(val){
            this.searchText = val
        },
        populateMapCategoryToSubcategory() {
            let ret = {}
            this.testSourceConfigs.forEach(x => {
                if (!ret[x.category.name]) {
                    ret[x.category.name] = {all: []}
                }

                let obj = {
                    label: x.id.substring(x.id.lastIndexOf("/")+1, x.id.lastIndexOf(".")), 
                    value: x.id,
                    icon: "$fab_github"
                }
                ret[x.category.name].all.push(obj);
            })

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
            _this.testSourceConfigs = resp.testSourceConfigs
            _this.businessLogicSubcategories = resp.subCategories
            _this.testsObj = _this.populateMapCategoryToSubcategory()
            this.mapTestToYamlFunc()
        })
        
    },
    computed:{
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
<style lang="scss" scoped>

</style>