<template>
    <div class="d-flex">
        <div class="test-col">
            <layout-with-left-pane>
                <search placeholder="Search Tests" @changed="setSearchText" />
                <div class="tests-container">
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

import {editor , Uri} from "monaco-editor"
import Search from '../dashboard/shared/components/inputs/Search.vue';
import LayoutWithLeftPane from '../dashboard/layouts/LayoutWithLeftPane.vue';
// import { setDiagnosticsOptions } from "monaco-yaml"

const modelUri = Uri.parse('a://b/foo.yaml');

// setDiagnosticsOptions({
//   enableSchemaRequest: true,
//   hover: true,
//   completion: true,
//   validate: true,
//   format: true,
//   schemas: [
//     {
//       // Id of the first schema
//       uri: 'http://myserver/foo-schema.json',
//       // Associate with our model
//       fileMatch: [String(modelUri)],
//       schema: {
//         type: 'object',
//         properties: {
//           p1: {
//             enum: ['v1', 'v2'],
//           },
//           p2: {
//             // Reference the second schema
//             $ref: 'http://myserver/bar-schema.json',
//           },
//         },
//       },
//     },
//     {
//       // Id of the first schema
//       uri: 'http://myserver/bar-schema.json',
//       schema: {
//         type: 'object',
//         properties: {
//           q1: {
//             enum: ['x1', 'x2'],
//           },
//         },
//       },
//     },
//   ],
// });

// const value = 'p1: \np2: \n';

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
                // model:editor.createModel(value , 'yaml' , modelUri)
            },
            textEditor:null,
            searchText: "",
        }
    },
    methods: {
        createEditor(){
            this.textEditor = editor.create(this.$refs.editor,this.editorOptions)
        },
        getEditorValue(){
            console.log(this.textEditor.getValue())
            return this.textEditor.getValue()
        },
        setSearchText(val){
            this.searchText = val
        }
    },
    async mounted() {
        this.createEditor()
    },
}
</script>
<style lang="scss" scoped>

</style>