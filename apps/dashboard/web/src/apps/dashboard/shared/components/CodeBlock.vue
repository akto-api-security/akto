<template>
    <div class="master_div mt-2 mb-2">
        <v-icon class="copy_btn mt-1 mr-1" v-on:click="copyContent">$fas_copy</v-icon>
        <div class="codeblock">
            <div class="mt-2  pt-1"></div>
            <div class="" v-for="line in lines">
                <pre>{{line}}</pre>
            </div>
            <div class="mb-2  pt-1"></div>
        </div>
    </div>
</template>

<script>
export default {
    name: 'CodeBlock',
    props: {
        'lines': {
            type: Array,
            required: true,
        },
        'onCopyBtnClickText':{
            type: String,
            required: false,
        }
    },
    methods: {
        copyContent() {
            let info = '';
            const len = this.lines.length;
            this.lines.forEach((line, i) => {
                info += line;
                if (i <= len - 1) {
                    info += "\n";
                }
            });
            if (info.length > 0 && this.onCopyBtnClickText.length > 0) {
                this.copyToClipboard(info);
                window._AKTO.$emit('SHOW_SNACKBAR', {
                    show: true,
                    text: this.onCopyBtnClickText,
                    color: 'green'
                });
            }
        },
        copyToClipboard(text) {
            // main reason to use domElement like this instead of document.body is that execCommand works only if current
            // component is not above normal document. For example in testing page, we show SampleSingleSide.vue in a v-dialog
            // NOTE: Do not use navigator.clipboard because it only works for HTTPS sites
            let domElement = this.$el;
            if (window.clipboardData && window.clipboardData.setData) {
                // Internet Explorer-specific code path to prevent textarea being shown while dialog is visible.
                return window.clipboardData.setData("Text", text);
            }
            else if (document.queryCommandSupported && document.queryCommandSupported("copy")) {
                var textarea = document.createElement("textarea");
                textarea.textContent = text;
                textarea.style.position = "fixed";  // Prevent scrolling to bottom of page in Microsoft Edge.
                domElement.appendChild(textarea);
                textarea.select();
                try {
                    return document.execCommand("copy");  // Security exception may be thrown by some browsers.
                }
                catch (ex) {
                    // console.warn("Copy to clipboard failed.", ex);
                    // return prompt("Copy to clipboard: Ctrl+C, Enter", text);
                }
                finally {
                    domElement.removeChild(textarea);
                }
            }
        },
    }
}
</script>

<style scoped>
.master_div {
    position: relative;
}

.codeblock {
    background-color: var(--hexColor39);
    min-width: 500px;
    max-width: 100%;
    max-height: 300px;
    overflow: scroll;
    white-space: nowrap;
    font-size: 12px;
    border-radius: 5px;
    padding-bottom: 10px;
    font-family: monospace;
}

.copy_btn {
    display: none;
    position: absolute;
    right: 5px;
}

.master_div:hover .copy_btn {
    display: block;
}
</style>