<template>
    <v-dialog width="500px" v-model="showDialog">
        <a-card 
            :title="title" 
            class="ma-0 dialog-card" 
            icon_right="$fas_times" 
            @icon_right_clicked="$emit('closeDialog')" 
            icon_right_color="var(--themeColorDark)"
        >
            <div class="items-container">
                <div class="field-container">
                    <span class="field-name">{{ field1.span_text }}</span>
                    <v-select 
                        class="dropdown-menu" 
                        v-if="field1.items && field1.items.length > 0"
                        :items="field1.items"
                        attach
                        append-icon="$fas_angle-down"
                        @change="showApiEndpoints"
                        v-model="newCollection.name"
                    />
                    <div v-else>
                        <v-text-field
                            v-model="newTest.name"
                            dense
                            class="form-field-text"                     
                        />
                    </div>
                </div>
                <div class="field-container">
                    <span class="field-name">{{ field2.span_text }}</span>
                    <v-select
                        class="dropdown-menu" 
                        :items="field2.items"
                        attach
                        v-model="newTest.category"
                        append-icon="$fas_angle-down"
                        @change="saveInfo"
                    />
                </div>

                <div class="button-container">
                    <v-btn class="save-cancel-button light-btn" color="var(--white)" @click="$emit('closeDialog')">
                        Cancel
                    </v-btn>
                    <v-btn class="save-cancel-button" primary dark depressed color="var(--themeColor)" @click="emitFunc(currentParam)">
                        Save
                    </v-btn>
                </div>
            </div>
        </a-card>
    </v-dialog>
</template>

<script>
import obj from '@/util/obj'
import ACard from '../shared/components/ACard.vue'
import testApi from "../views/testing/components/test_roles/api"

export default {
    name: 'SelectorModel',
    components:{
        ACard,
    },
    props:{
        showDialog:obj.boolR,
        title:obj.strR,
        currentParam: obj.strR,
        testCategories: obj.arrR,
    },
    data(){
        return{
            field1:{},
            field2:{},
            newTest:{
                name:'your_test_name',
                category: "BOLA"
            },
            newCollection:{
                name: 0,
                url: {},
            }
        }
    },
    methods:{
        saveInfo(){
            this.newCollection.url = this.newTest.category
        },
        showApiEndpoints(){
            testApi.fetchCollectionWiseApiEndpoints(this.newCollection.name).then((resp)=>{
                this.field2.items=this.getApiEndpoints(resp.listOfEndpointsInCollection)
            })
        },
        businessCategoryShortNames() {
            return [...new Set(this.testCategories.map(category => {return { text: category.shortName, value: category.name}}))]
        },
        mapCollectionIdToName() {
            let collections = this.$store.state.collections.apiCollections
            return [...new Set(collections.map(collection => {return { text: collection.displayName, value: collection.id}}))]
        },
        getApiEndpoints(arr){
            return [...new Set(arr.map(urlObj => {return { text: urlObj.url, value: {"method":urlObj.method, "url": urlObj.url}}}))]
        },
        emitFunc(param){
            if(param !== 'save'){
                this.$emit('get_form_values',param,this.newCollection)
            }else{
                this.$emit('get_form_values',param,this.newTest)
            }
            this.$emit('closeDialog')
        },
    },
    watch:{
        currentParam(newVal){
            if(newVal === 'save'){
                this.field1 = {
                    span_text: 'Test Id',
                }
                this.field2 = {
                    span_text: 'Category',
                    items: this.businessCategoryShortNames()
                }
            }else{
                this.field1 = {
                    span_text: 'Collection',
                    items: this.mapCollectionIdToName(),
                }
                this.field2 = {
                    span_text: 'API',
                    items: this.showApiEndpoints(),
                }
            }
        }
    }
}
</script>
<style scoped>
    .dialog-card >>> .acard-title{
        font-size: 18px !important;
    }
    .form-field-text >>> .v-text-field__details{
        display: none !important;
    }

    .dropdown-menu >>> .v-text-field__details{
        display: none;
    }
    .form-field-text >>> input {
        font-size: 14px !important;
        color: var(--themeColorDark) !important; 
        font-weight: 400;   
    }
    .dropdown-menu >>> .v-list-item__title {
        font-size: 14px !important;
        color: var(--themeColorDark) !important;
    }
    .dropdown-menu >>> .v-select__selection {
        font-size: 14px !important;
        color: var(--themeColorDark) !important;
        margin: 0px !important;
    }

    .dropdown-menu >>> .v-list-item__content {
        padding: 0px !important;
        color: var(--themeColorDark) !important;
    }

    .dropdown-menu >>> .v-list-item {
        min-height: 30px !important;
    }
    .light-btn{
        border: 1px solid var(--borderColor) !important;
        color: var(--themeColorDark) !important;
    }
</style>
<style lang="scss" scoped>
    .dialog-card{
        min-height: 350px;
        overflow-y: hidden;
    }
    .items-container{
        padding: 12px 24px;
        display: flex;
        flex-direction: column;
        gap: 24px;
    }
    .field-name{
        font-size: 14px;
        color: var(--themeColorDark);
        font-weight: 500;
    }
    .form-field-text , .dropdown-menu{
        padding: 10px 14px;
        border: 1px solid var(--hexColor22);
        max-height: 48px;
        border-radius: 8px;
        box-shadow: 0px 1px 2px rgba(16, 24, 40, 0.05);
    }
    .button-container {
        display: flex;
        gap: 20px;
        margin-top: 16px;
        .save-cancel-button{
            width: 210px !important;
            box-shadow: none !important;
            padding: 10px 18px !important;
            font-size: 16px !important;
            border-radius: 8px !important;
            font-weight: 500;
        }
    }
</style>