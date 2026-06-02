let getEditorContent = () => ""

const editorContentBridge = {
    setContentGetter(getter) {
        getEditorContent = getter
    },
    getContent() {
        return getEditorContent()
    },
}

export default editorContentBridge
