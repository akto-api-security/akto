<template>
    <div class="gpt-main-div">
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
            <v-btn icon :ripple="false" color="var(--themeColor)" @click="sendToGPT" :disabled="disabledQuery" class="gpt-button">
                <v-icon size="14">$far_paper-plane</v-icon>
            </v-btn>
            
                <v-tooltip bottom>
                    <template v-slot:activator='{ on, attrs }'>
                        <v-btn 
                            icon 
                            :ripple="false" 
                            color="var(--themeColorDark)" 
                            @click="openDocsOnAktoGPT" 
                            class="gpt-button"
                            v-on="on"
                            v-bind="attrs"                            
                        >
                            <v-icon size="14">$far_question-circle</v-icon>
                        </v-btn>
                    </template>
                    <span>Learn what data is sent and how we use it</span>
                </v-tooltip>


        </div>    
        <div class="prompt-body" v-if="responses || loading">
            <div class="gpt-prompt">
                <span class="gpt-prompt-text">
                    <v-icon :size=14 :color='("var(--hexColor"+Math.floor(Math.random() * 13 + 1)+")")'>$fas_user-graduate</v-icon>
                    {{ computedLabel }}
                </span>
            </div>
            <div class="prompt-loader" v-if="loading">
                <spinner :size=28 />
            </div>
            <div class="response-body" v-else-if="responses.length == 0">
                <v-icon class="gpt-icon" :size="14">$aktoWhite</v-icon>
                <span class="listItem">
                    Sorry couldn't find any response with your prompt.
                    Try again.
                </span>
            </div>

            <div class="response-body" v-else>
                <v-icon class="gpt-icon" :size="16">$aktoWhite</v-icon>
                    <v-list-item v-for="(item, index) in responses" :key="index" class="listItem">
                        <div v-if="item.functionality">
                            <div class="fw-500" style="text-transform: uppercase">{{item.functionality}}</div>
                            <div v-for="(api, ii) in item.apis" :key="'api_'+ii">- {{api}}</div>
                        </div>
                        <span v-else>{{ item }}</span>
                        
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
            }),
        }
    },
    methods: {
        openDocsOnAktoGPT() {
            return window.open("https://docs.akto.io/aktogpt")
        },
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
        computedLabel(){
            if(this.selectedObject){
                let arr = this.selectedObject.label.split("$")
                if(arr.length > 1){
                    let str = arr[0] + " " + this.searchKey
                    return str
                }
                else{
                    return arr[0]
                }
            }
        },
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
.gpt-button
    margin: auto 0
.gpt-main-div
    display: flex
    flex-direction: column
.chat-gpt-container
    display: flex
    margin: auto
    justify-content: center
    order: 99
.gpt-icon
    color: var(--themeColor)
.gpt-prompt
    display: flex
    align-items: center
    padding: 0 20px
    .gpt-prompt-text
        font-size: 12px


.chat-gpt-text-field
    min-width: 346px !important
    max-width: 346px !important
    width: 346px !important

.prompt-body
    min-height: 200px
    margin-top:20px
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
        padding: 20px
        .listItem
            min-height: 16px !important
            font-size: 12px !important
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
    }

    .chat-gpt-container >>> .v-text-field--outlined {
        box-shadow: 0 0 10px rgba(0,0,0,.1) !important;
        border-color: rgba(0,0,0,.1) !important;
    }

    .chat-gpt-text-field >>> .v-input__prepend-inner {
        margin: auto;
        width: 380px;
        display: flex;
        justify-content: center; 
    }
    .chat-gpt-text-field >>> .v-input__append-inner {
        margin: auto;
    }

    .chat-gpt-text-field >>> fieldset {
        border: 0px
    }

</style>