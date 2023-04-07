<template>
    <div :class="['screens-container', !showTestScreen ? 'no-display' : '']">
        <div class="screen-header">
            <span>
                {{ item.displayName }}
                <v-icon size="16">$fas_pen</v-icon>
            </span>
            <secondary-button class="screen-buttons" text="Close" @click="toggle()" />
        </div>

        <div class="screen-body">
            <v-stepper v-model="currIndex" class="stepper">
                <v-stepper-header class="stepper-header">
                    <template v-for="(item,index) in stepperData">
                        <v-stepper-step
                            :complete="currIndex > (index + 1)"
                            :step="index + 1"
                            complete-icon="$fas_check"
                            :key="item"
                            class="stepper-title"
                        >
                            {{ item }}
                        </v-stepper-step>
                        <v-divider v-if="index < (stepperData.length - 1)" :key="index + 1" class="divider"/>
                    </template>
                </v-stepper-header>

                <v-stepper-content step="1">
                    <div>
                        Hello 1
                    </div>
                </v-stepper-content>

                <v-stepper-content step="2">
                    <div>
                        Hello 2
                    </div>
                </v-stepper-content>

                <v-stepper-content step="3">
                    <div>
                        Hello 3
                    </div>
                </v-stepper-content>

                <v-stepper-content step="4">
                    <div>Summary</div>
                </v-stepper-content>
                
            </v-stepper>
        </div>

        <div class="screen-footer">
            <div class="d-flex jc-end">
                <secondary-button text="Back" class="screen-buttons" @click="back" :color="color"/>
                <secondary-button 
                    :text="nextText" 
                    class="screen-buttons next-button" 
                    color="var(--white)" 
                    @click="next"
                    :icon="runIcon"
                />
            </div>
        </div>
    </div>
</template>

<script>
import obj from '@/util/obj'
import SecondaryButton from '../../../shared/components/buttons/SecondaryButton.vue'

export default{
    name: "TestingRunScreens",
    props:{
        item:obj.objR,
        showTestScreen:obj.boolR,
    },
    components:{
        SecondaryButton,
    },
    data(){
        return{
            currIndex:1,
            summaryActive:false,
            stepperData:[
                'COLLECTION','TEST SUITES','USER CONFIG'
            ]
        }
    },
    methods:{
        toggle(){
            this.showTestScreen = false
        },
        back(){
            if(this.currIndex > 1){
                this.currIndex--
            }
        },
        next(){
            this.currIndex++
            if(this.currIndex == 4){
                this.summaryActive = true
            }
            else if(this.currIndex == 5){
                this.runTest()
            }
            else{
                this.summaryActive = false
            }
        },
        runTest(){
            console.log("RunTest")
        },
    },
    computed:{
        color(){
            if(this.currIndex > 1){return "var(--themeColorDark)"}       
            return "var(--lighten2)"
        },
        nextText(){
            if(this.currIndex > 3){return "Run Test"}       
            return "Next"
        },
        runIcon(){
            if(this.currIndex > 3){return "$fas_play"}      
        }
    }
}
</script>
<style scoped>
    .screen-buttons >>> .show-buttons{
        min-width: 72px !important;
        min-height: 34px !important;
    }
    .next-button >>> .show-buttons{
        background-color: var(--themeColorDark) !important;
    }
    .stepper-title >>> .v-stepper__step__step{
        border: 4px solid var(--borderColor) !important;
        background-color: var(--white) !important;

    }
    .v-stepper__step--complete >>> .v-stepper__step__step{
        background-color: var(--themeColor) !important;
    }
    .stepper-title >>> .primary{
        border-color: var(--themeColor) !important;
    }
</style>
<style lang="scss" scoped>
    .no-display{
        display: none !important;
    }
    .screens-container{
        display: flex;
        flex-direction: column;
        height: 100%;
        .screen-header{
            height: 80px;
            padding: 16px 32px;
            border-bottom: 1px solid var(--borderColor);
            display: flex;
            align-items: center;
            justify-content: space-between;

            span{
                display: flex;
                gap: 4px;
                align-items: center;
                height: 31px;
                font-weight: 500;
                font-size: 24px;
                color: var(--themeColorDark);
            }
        }
        .screen-body{
            margin: 20px 8px;
            padding:4px;
            .stepper,.stepper-header{
                box-shadow: none !important;
            }
            .divider{
                border: 2px solid var(--borderColor) !important;
            }
        }
        .screen-footer{
            position: absolute;
            bottom: 72px;
            width: 100%;
            border-top: 1px solid var(--borderColor);
            padding: 16px 32px 8px 32px;
        }
    }
</style>