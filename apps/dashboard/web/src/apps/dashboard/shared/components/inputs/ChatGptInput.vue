<template>
    <div style="height: 200px">
        <div class="chat-gpt-container">
            <simple-menu :items="menuItems" tooltipTriangle="up" :showMenuOnDraw="showMenuOnDraw">
                <template v-slot:activator2>
                    <v-text-field
                        class="chat-gpt-text-field"
                        ref="gptTextInput"
                        outlined
                        :disabled="disabled"
                        :placeholder="placeholder"
                        append-icon="$far_paper-plane"
                        v-model="textSearch"
                        @keydown.native.13="sendToGPT"
                    >
                        <template slot="prepend-inner" v-if="selectedItem">{{first}}</template>
                        <template slot="append">
                            <span>
                                <span  v-if="selectedItem">{{second}}</span>
                            </span>
                        </template>
                    </v-text-field>
                </template>
            </simple-menu>
            <v-btn icon :ripple="false" color="#6200EA" @click="sendToGPT" :disabled="disabledQuery" style="margin: auto 0">
                <v-icon size="14">$far_paper-plane</v-icon>
            </v-btn>
        </div>    
    </div>
</template>

<script>

import obj from '@/util/obj';
import SimpleMenu from '../SimpleMenu'

export default {
    name: "ChatGptInput",
    components: {
        SimpleMenu
    },
    props: {
        items: obj.arrR,
        showDialog: obj.boolN
    },
    data () {
        let _this = this;
        return {
            showMenuOnDraw: false,
            selectedItem: null,
            searchKey: null,
            menuItems: this.items.map(x => {
                return {
                    icon: x.icon,
                    label: x.label.replaceAll("${input}", "_________"),
                    click: () => {
                        _this.selectedItem = x.label
                    }
                }
            })
        }
    },
    methods: {
        sendToGPT() {
            if (this.disabledQuery) return
            console.log("sendToGPT")
        }
    },
    mounted () {
        let inputEl = this.$refs.gptTextInput
        setTimeout(()=>{
            inputEl.$el.click()
        },500)

        
    },
    computed: {
        placeholder() {
            return this.selectedItem ? '' : "What do you want to ask AktoGPT?"
        },
        first () {
            return this.selectedItem ? this.selectedItem.split("${input}")[0] : null
        },
        second () {
            return this.selectedItem ? (this.selectedItem.split("${input}").length > 1 ? this.selectedItem.split("${input}")[1] : '') : null
        },
        disabled() {
            return !this.selectedItem || this.selectedItem.indexOf("${input}") == -1
        },
        disabledQuery() {
            let ret = !!this.selectedItem && (this.selectedItem.indexOf("${input}") == -1 || (this.searchKey || '').length > 0)
            return !ret
        },
        textSearch: {
            get () {
                let _this = this
                if (this.selectedItem) {
                    if (this.disabled) return null
                    if (!this.searchKey)  {
                        this.searchKey = ''
                    }
                    let inputEl = _this.$refs.gptTextInput.$el.querySelector('.chat-gpt-text-field input')
                    setTimeout(()=>{
                        inputEl.focus()
                    },200)

                    return this.searchKey
                } else {
                    return null
                }
            },
            set (value) {
                this.searchKey = value
            }
        }
    }
}
</script>

<style lang="sass" scoped>
.chat-gpt-container
    min-width: 400px !important
    max-width: 400px !important
    width: 400px !important
    display: flex
    margin: auto

.chat-gpt-text-field
    min-width: 346px !important
    max-width: 346px !important
    width: 346px !important
</style>

<style scoped>
    .chat-gpt-text-field >>> .v-text-field__details {
        display: none;
    }

    .chat-gpt-text-field >>> .v-input__slot {
        margin-bottom: 0px;
        font-size: 14px;
        min-height: 45px !important;
        color: #000000 !important;

    }

    .chat-gpt-text-field >>> input::placeholder {
        color: #000000 !important;
    }

    .chat-gpt-text-field >>> input {
        color: #6200EA !important;
        padding-top: 11px !important;
    }

    .chat-gpt-container >>> .v-text-field--outlined {
        box-shadow: 0 0 10px rgba(0,0,0,.1) !important;
        border-color: rgba(0,0,0,.1) !important;
    }

    .chat-gpt-text-field >>> .v-input__append-inner {
        margin: auto;
    }


</style>