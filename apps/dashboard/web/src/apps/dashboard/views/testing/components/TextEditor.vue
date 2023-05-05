<template>
    <div ref="editor" style="width: 800px; height: 800px;">
        <v-btn @click="getEditorValue">Test</v-btn>
    </div>
</template>

<script>
import loader from "@monaco-editor/loader"
export default {
    name: "TextEditor",
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
        }
    },
    methods: {
        createEditor(monaco){
            this.textEditor = monaco.editor.create(this.$refs.editor,this.editorOptions)
        },
        getEditorValue(){
            console.log(this.textEditor.getValue())
            return this.textEditor.getValue()
        }
    },
    async mounted() {
        loader.init().then((monaco)=>{
            this.createEditor(monaco)
        })
    },
}
</script>