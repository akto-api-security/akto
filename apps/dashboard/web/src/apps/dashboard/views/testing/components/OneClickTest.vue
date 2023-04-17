<template>
    <div v-if="!leftView">
        <div v-for="(table,index) in tables" :key="index">
            <grid-table :headers="table.headers" :items="table.items" @clickRow="rowClicked">
                <template v-slot:custom-header>
                    <div class="table-name">{{ table.name }}</div>
                </template>
                <template v-slot:test-categories>
                    <div class="test-categories">
                        <div class="button-container">
                            <div v-for="(test,index) in testShown" :key="index">
                                <v-btn class="test-buttons">
                                    <span>{{ test.name }}</span>
                                </v-btn>
                            </div>
                            <v-btn class="test-buttons" @click="toggle()" v-if="!showAll">
                                <span>+ {{ testCategories.length - 3 }}</span>
                            </v-btn>
                            <v-btn class="test-buttons" @click="toggle()" v-else>
                                <span :style="{'color': 'var(--themeColor)'}">COLLAPSE</span>
                            </v-btn>
                            <v-icon class="icon-on-hover" size="20">$deleteIcon</v-icon>
                        </div>
                    </div>
                </template>
            </grid-table>
        </div>
    </div>
    <div v-else>
        <layout-with-left-pane title="Run test" class="left-table-view">
            <template #leftPane>
                <div class="left-pane">
                    <div class="custom-test">
                        <v-icon size="16">$fas_plus</v-icon>
                        <span>
                            Custom Test
                        </span>
                    </div>
                    <div v-for="(table,index) in tables" :key="index" class="table-container">
                        <grid-table :headers="table.headers" :items="table.items" @clickRow="rowClicked" :left-view="true">
                            <template v-slot:custom-header>
                                <div class="left-table-name">
                                    <span>{{ table.name }}</span>
                                    <v-icon size="16">$fas_angle-down</v-icon>
                                </div>
                            </template>
                        </grid-table>
                    </div>
                </div>
            </template>
            <testing-run-screens :item="currObj"/>
        </layout-with-left-pane>
    </div>
</template>

<script>
import obj from '@/util/obj'
import GridTable from '../../../shared/components/GridTable.vue'
import LayoutWithLeftPane  from '../../../layouts/LayoutWithLeftPane.vue'
import TestingRunScreens from './TestingRunScreens.vue'

export default {
    name: "OneClickTest",
    components: {
        GridTable,
        LayoutWithLeftPane,
        TestingRunScreens,
    },
    props: {

    },
    data() {
        let testHeaders =[
            {
                text: "Collections Name",
                value: "displayName"
            },
            {
                text: "Endpoints",
                value: "endpoints"
            },
            {
                text:'Status',
                value:'status'
            }
        ]

        let tableItems=[
            {
                displayName:"Test Collections 1",
                endpoints:"128 Endpoints",
                status:"Full coverage on Most Active Selection"
            },
            {
                displayName:"Test Collections 2",
                endpoints:"64 Endpoints",
                status:"Full coverage on Most Active Selection"
            },
            {
                displayName:"Test Collections 3",
                endpoints:"32 Endpoints",
                status:"Full coverage on Most Active Selection"
            },
            {
                displayName:"Test Collections 4",
                endpoints:"16 Endpoints",
                status:"Full coverage on Most Active Selection"
            },
            {
                displayName:"Test Collections 5",
                endpoints:"8 Endpoints",
                status:"Full coverage on Most Active Selection"
            },
            {
                displayName:"Test Collections 6",
                endpoints:"4 Endpoints",
                status:"Full coverage on Most Active Selection"
            },
        ]

        return{
            testCategories:[
                {
                    name:"BOLA"
                },
                {
                    name:"BFLA"
                },
                {
                    name:"BUA"
                },
                {
                    name:"NO_AUTH"
                },
                {
                    name:"SM"
                },
                {
                    name:"SSRF"
                },
            ],
            tables:[
                {
                    items:tableItems,
                    headers:testHeaders,
                    name: "One Click Test"
                },
                {
                    items:tableItems,
                    headers:testHeaders,
                    name: "Low Coverage"
                }
            ],
            showAll: false,
            leftView: false,
            currObj: {},
        }
    },
    methods: {
        rowClicked(item){
            this.currObj = item
            this.leftView = true
        },
        toggle(){
            this.showAll = !this.showAll
        }
    },
    computed:{
        testShown(){
            let arr = this.testCategories
            if(!this.showAll){
                arr = arr.slice(0 , 3);
            }
            return arr
        }
    }
}
</script>
<style scoped>
    .left-table-view >>> .akto-left-pane{
        padding: 0px !important
    }
</style>

<style lang ="scss" scoped>
    .table-name{
        margin: 32px 0 0 8px;
        font-size: 20px;
        font-weight: 600;
        color: var(--themeColorDark);
    }
    .test-categories{
        color: var(--themeColorDark);
        gap: 8px;
    }
    .button-container{
        display: flex;
        flex-wrap: wrap;
        gap:8px;
    }
    .test-buttons{
        height: 22px !important;
        min-width: 50px !important;
        box-shadow: none !important;
        background: var(--hexColor31) !important;
        padding: 2px 8px !important;
        
        span{
            font-size: 12px;
            color: var(--themeColorDark);
            font-weight: 500;
        }
    }
    .icon-on-hover{
        display: none;
    }
    .left-pane{
        width: 280px;
        display: flex;
        flex-direction: column;
        align-items: center;
        
        .custom-test{
            width: 216px;
            height: 40px;
            justify-content: center;
            display: flex;
            padding: 8px 16px;
            gap: 8px;
            border: 1px solid var(--lighten2);
            border-radius: 8px;
            margin-top: 24px;
            align-items: center;

            span{
                height: 21px;
                font-weight: 500;
                font-size: 16px;
                color:var(--themeColorDark);
            }
        }
        .table-container{
            width: 213px;
            .left-table-name{
                display: flex;
                margin: 24px 0 12px 0;
                align-items: center;
                justify-content: space-between;
                span{
                    width: 135px;
                    height: 21px;
                    font-weight: 600;
                    font-size: 18px;
                    line-height: 130%;
                    color: var(--themeColorDark);
                }
            }
        }
    }
</style>