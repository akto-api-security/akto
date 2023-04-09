<template>
    <div>
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
                        <template slot="prepend-inner" v-if="selectedLabel">{{first}}</template>
                        <template slot="append">
                            <span>
                                <span  v-if="selectedLabel">{{second}}</span>
                            </span>
                        </template>
                    </v-text-field>
                </template>
            </simple-menu>
            <v-btn icon :ripple="false" color="var(--themeColor)" @click="sendToGPT" :disabled="disabledQuery" style="margin: auto 0">
                <v-icon size="14">$far_paper-plane</v-icon>
            </v-btn>
        </div>    
        <div class="prompt-body" v-if="responses || loading">
            <div class="prompt-loader" v-if="loading">
                <span class="mr-1">AktoGPT is performing magic</span>
                <spinner />
            </div>
            <div class="api-no-response" v-else-if="responses.length == 0">
                Sorry couldn't find any response with your prompt.
                Try again.
            </div>

            <div class="response-body">
                <v-list-item v-for="item in responses" :key="item" class="listItem">
                    {{ item }}
                </v-list-item>
            </div>
        </div>

    </div>
</template>

<script>

import obj from '@/util/obj';
import SimpleMenu from '../SimpleMenu'
import Spinner from '../Spinner'
import request from '@/util/request'

export default {
    name: "ChatGptInput",
    components: {
        SimpleMenu,
        Spinner
    },
    props: {
        items: obj.arrR,
        showDialog: obj.boolN
    },
    data () {
        let _this = this;
        return {
            loading: false,
            showMenuOnDraw: false,
            selectedObject: null,
            searchKey: null,
            responses: null,
            menuItems: this.items.map(x => {
                return {
                    icon: x.icon,
                    label: x.label.replaceAll("${input}", "_________"),
                    click: () => {
                        _this.responses = null
                        _this.selectedObject = x
                    }
                }
            })
        }
    },
    methods: {
        sendToGPT() {
            if (this.disabledQuery || !this.selectedObject) return
            let _this = this;
            _this.responses = null
            let queryPayload = this.selectedObject.prepareQuery(this.searchKey)
            this.loading = true;
            this.askGPT(queryPayload).then(resp => {
                _this.responses = resp.response.responses || []
                _this.loading = false
            }).catch(() => {
                _this.responses = []
                _this.loading = false
            })   
        },
        askGPT(data) {
            return request({
                url: '/api/ask_ai',
                method: 'post',
                data
            })
        }
    },
    mounted () {
        let inputEl = this.$refs.gptTextInput
        setTimeout(()=>{
            inputEl.$el.click()
        },500)

        
    },
    computed: {
        selectedLabel() {
            if (this.selectedObject) {
                return this.selectedObject.label
            } else {
                return null
            }
        },
        placeholder() {
            return this.selectedLabel ? '' : "What do you want to ask AktoGPT?"
        },
        first () {
            return this.selectedLabel ? this.selectedLabel.split("${input}")[0] : null
        },
        second () {
            return this.selectedLabel ? (this.selectedLabel.split("${input}").length > 1 ? this.selectedLabel.split("${input}")[1] : '') : null
        },
        disabled() {
            return !this.selectedLabel || this.selectedLabel.indexOf("${input}") == -1
        },
        disabledQuery() {
            let ret = !!this.selectedLabel && (this.selectedLabel.indexOf("${input}") == -1 || (this.searchKey || '').length > 0)
            return !ret
        },
        textSearch: {
            get () {
                let _this = this
                if (this.selectedLabel) {
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
    display: flex
    margin: auto

.chat-gpt-text-field
    min-width: 346px !important
    max-width: 346px !important
    width: 346px !important

.prompt-body
    min-height: 200px
    margin-top:20px
    border: 1px solid var(--black)
    border-radius:4px
    .prompt-loader,.api-no-response
        display: flex
        justify-content: center
        margin-top: 70px !important
        height: 50px
        align-items: center
        font-size: 16px
    
    .api-no-response
        border: 1px solid var(--redMetric)
        background-color: var(--white)
        width: 650px
        margin: auto
        border-radius: 6px
    
    .response-body
        padding: 24px
        .listItem
            padding: 6px 9px
            min-height: 24px !important
            font-size: 14px !important
</style>

<style scoped>
    .chat-gpt-text-field >>> .v-text-field__details {
        display: none;
    }

    .chat-gpt-text-field >>> .v-input__slot {
        margin-bottom: 0px;
        font-size: 14px;
        min-height: 45px !important;
        color: var(--black) !important;

    }

    .chat-gpt-text-field >>> input::placeholder {
        color: var(--black) !important;
    }

    .chat-gpt-text-field >>> input {
        color: var(--themeColor) !important;
        padding-top: 11px !important;
    }

    .chat-gpt-container >>> .v-text-field--outlined {
        box-shadow: 0 0 10px rgba(0,0,0,.1) !important;
        border-color: rgba(0,0,0,.1) !important;
    }

    .chat-gpt-text-field >>> .v-input__append-inner {
        margin: auto;
    }

    .chat-gpt-text-field >>> fieldset {
        border: 0px
    }

</style>