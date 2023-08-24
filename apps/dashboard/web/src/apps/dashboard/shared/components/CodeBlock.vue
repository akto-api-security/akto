<template>
    <div class="master_div mb-2">
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

import func from '@/util/func'
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
                func.copyToClipboard(info, this.onCopyBtnClickText, this.$el);
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