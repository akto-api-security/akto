<template>
    <a-card title="Now you can ask AktoGPT" 
            icon="$aktoWhite" 
            icon_right="$fas_times" 
            @icon_right_clicked="closeDialog"
            class="prompts-container"
    >
        <div class="response-ui">
            <div class="prompts-text">
                <div v-for="(item,index) in prompts" :key="index">
                    <v-btn @click="callFunc(item.keyword)">
                        <span>Please tell me all the {{ item.keyword }} APIs</span>
                    </v-btn>
                </div>
            </div>

            <div class="prompts-response-container" v-if="promptText.length > 0">
                <span class="prompt-chosen">
                    <v-icon>$chatGPT</v-icon>
                    {{ promptText }}
                </span>
                <div class="prompt-body">
                    <div class="prompt-loader" v-if="!getResponse">
                        <span>AktoGPT is performing magic</span>
                        <spinner />
                    </div>
                    <div class="api-no-response" v-else-if="responseArr.length == 0">
                        Sorry couldn't find any response with your prompt.
                        Try again.
                    </div>

                    <div class="response-body">
                        <v-list-item v-for="item in responseArr" :key="item" class="listItem">
                            {{ item }}
                        </v-list-item>
                    </div>
                </div>
            </div>
        </div>
    </a-card>
</template>

<script>
import obj from '@/util/obj'
import Spinner from './Spinner.vue'
import ACard from './ACard.vue'
export default {
    name: "AktoAi",
    props:{
        responseArr:obj.arrR,
        getResponse:obj.boolR,
        prompts:obj.arrR,
        promptText:obj.strR,
    },
    components:{
        Spinner,
        ACard
    },
    methods:{
        closeDialog(){
            this.$emit('closeDialog')
        },
        callFunc(keyword){
            this.$emit('callFunc',keyword)
        }
    }
}
</script>

<style lang="sass" scoped>
    .prompts-container
        width: 800px
        background-color: var(--white)
        margin: 0px !important
        color: var(--themeColorDark)
        min-height: 400px !important
</style>

<style lang="scss" scoped>
    .response-ui{
        display: flex;
        flex-direction: column;
        gap:20px;
        .prompts-text{
            order:2;
        }
        .prompts-response-container{
            padding: 20px 0px;
            .prompt-chosen{
                color: var(--themeColor);
                display: flex;
                align-items: center;
                padding-left: 24px;
                font-size: 20px;
                font-weight: 500;
                height: 40px;
                gap: 8px;
            }
            .prompt-body{
                background: var(--gptBackground);
                border-top: 1px solid var(--borderColor);
                border-bottom: 1px solid var(--borderColor);
                min-height: 200px;
                .prompt-loader,.api-no-response{
                    display: flex;
                    justify-content: center;
                    margin-top: 70px !important;
                    height: 50px;
                    align-items: center;
                    font-size: 18px;
                }
                .api-no-response{
                    border: 1px solid var(--redMetric);
                    background-color: var(--white);
                    width: 650px;
                    margin: auto;
                    border-radius: 6px;
                }
                .response-body{
                    padding: 24px;
                    .listItem{
                        padding: 6px 9px;
                        min-height: 24px !important;
                        font-size: 14px !important;
                    }
                }
            }
        }
    }
</style>