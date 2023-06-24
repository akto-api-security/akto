<template>
    <v-dialog ref="dialogABC" :width="width" v-model="dialog" eager>
        <v-card>
            <v-card-title class="modal-title">
                {{ title }}
            </v-card-title>
            <v-card-text  class="modal-body">
              Request
              <span class="grey--text">(Paste API request here...)</span>

            </v-card-text>
            <div ref="editorRequest" style="height: 150px;" class="monaco-editor"></div>
            <!-- <v-textarea solo no-resize rows="6" label="Ctrl-V to paste" v-model="requestValue"></v-textarea> -->
            <v-card-text class="modal-body">
              Response
              <span class="grey--text">(Paste API response here...)</span>
            </v-card-text>
            <div ref="editorResponse" style="height: 150px;" class="monaco-editor"></div>
            <!-- <v-textarea solo no-resize rows="6" label="Ctrl-V to paste" v-model="responseValue"></v-textarea> -->
            <v-card-actions class="button-class">
                <v-btn primary dark depressed width="200px" class="button-border-class var(--white)--text" color="var(--themeColor)" @click="saveTextValues()"
                       :ripple="false"
                    >
                    Save
                </v-btn>
            </v-card-actions>
        </v-card>
    </v-dialog>
</template>

<script>
import { editor } from "monaco-editor/esm/vs/editor/editor.api"

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
    name: "CustomSampleDataApiModal",
    props: {
        title: "",
        parentDialog: {default: false},
        width: {default : '800px'}
    },
    data() {
        return {
            editorRequest: null,
            editorResponse: null,
            editorOptions: {
              language: "yaml",
              minimap: { enabled: false },
              wordWrap: false,
              automaticLayout: true,
              colorDecorations: true,
              scrollBeyondLastLine: false,
              lineDecorationsWidth: 3,
            },
            requestValue: "",
            responseValue: "",
            trimAutoWhitespace: false,
        }
    },
    methods: {
        saveTextValues() {
            this.$emit("setSampleDataApi",{requestValue: this.editorRequest.getValue(), responseValue: this.editorResponse.getValue()})
            this.dialog = false
        },
        createEditor() {
            this.editorRequest = editor.create(this.$refs.editorRequest, this.editorOptions)
            this.editorResponse = editor.create(this.$refs.editorResponse, this.editorOptions)
            this.editorRequest.setValue("")
            this.editorResponse.setValue("")
        }
    },
    mounted() {
        this.createEditor()
    },
    computed: {
        reqVal() {
            this.requestValue
        },
        dialog: {
            get() {
                return this.parentDialog
            },
            set(value) {
                this.$emit('closeCustomSampleDataDialog')
            }
        }
    }

}

</script>

<style scoped>

.modal-body {
    font-weight: 500 !important;
    font-size: 14px !important;
    margin-top: 12px;
    margin-bottom: 6px;
    padding-bottom: 4px !important;
}

.modal-title {
    font-weight: 600 !important;
    font-size: 18px !important;
    color: var(--themeColorDark);
}

.monaco-editor {
    border: 1px solid rgba(71, 70, 106, 0.12);
    box-shadow: 0px 1px 2px rgba(16, 24, 40, 0.05);
    margin-left: 24px;
    margin-right: 24px;
}

.button-class {
    display: flex;
    justify-content: flex-end;
    margin-top: 24px ;
    margin-bottom: 0px !important;
    padding-bottom: 24px !important;
}

.button-border-class {
    border-color: var(--black);
    font-size: 16px;
    height: 40px !important;
}
</style>