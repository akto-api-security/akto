<template>
    <v-dialog ref="dialogABC" :width="width" v-model="dialog" persistent eager>
        <v-card>
            <v-card-title class="modal-title">
                {{ title }}
            </v-card-title>
            <v-card-text  class="modal-body">Request</v-card-text>
            <div ref="editorRequest" style="height: 150px;" class="monaco-editor"></div>
            <!-- <v-textarea solo no-resize rows="6" label="Ctrl-V to paste" v-model="requestValue"></v-textarea> -->
            <v-card-text class="modal-body">Response</v-card-text>
            <div ref="editorResponse" style="height: 150px;" class="monaco-editor"></div>
            <!-- <v-textarea solo no-resize rows="6" label="Ctrl-V to paste" v-model="responseValue"></v-textarea> -->
            <v-card-actions class="button-class">
                <v-btn width="46%" class="button-border-class" :class='["elevation-0"]' @click=" dialog = false">
                    Cancel
                </v-btn>
                <v-btn primary dark depressed width="46%" class="button-border-class var(--white)--text" color="var(--themeColor)" @click="saveTextValues()"
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
        width: {default : '600px'}
    },
    data() {
        return {
            editorRequest: null,
            editorResponse: null,
            editorOptions: {
                language: "yaml",
                minimap: { enabled: false },
                wordWrap: true,
                automaticLayout: true,
                colorDecorations: true,
                scrollBeyondLastLine: false,
                lineNumbers: "off",
                scrollbar: {
                    vertical: "hidden",
                    horizontal: "hidden",
                    verticalScrollbarSize: 0,
                    horizontalScrollbarSize: 0
                },
                autoClosingBrackets : "never",
                areaLabel: "Sample Data API",
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
            this.editorRequest.setValue("Ctrl-V to paste")
            this.editorResponse.setValue("Ctrl-V to paste")
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
}

.modal-title {
    font-weight: 600 !important;
    font-size: 18px !important;
}

.monaco-editor {
    border: 1px solid rgba(71, 70, 106, 0.12);
    box-shadow: 0px 1px 2px rgba(16, 24, 40, 0.05);
    border-radius: 8px;
    margin-left: 24px;
    margin-right: 24px;
}

.button-class {
    display: flex;
    justify-content: space-around;
    margin-top: 24px;
    margin-bottom: 24px;
}

.button-border-class {
    border-color: var(--black);
    border-radius: 8px;
    font-size: 16px;
    font-weight: 600;
}
</style>