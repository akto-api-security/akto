<template>
    <div style="width: 100%;">
        <div style="padding-bottom: 10px; font-weight: bold; display: inline-block">
            <span style="color: var(--themeColorDark)">
                {{table_header}}
            </span>
            <v-icon @click="addNewRow" class="addRowIcon">
                $fas_plus
            </v-icon>
        </div>
        <div v-if="conditions && conditions.predicates">
            <div 
                v-for="(condition,index) in conditions.predicates" :key="index"
                :class="index == 0 ? 'condition-row first' : 'condition-row'"
            >
                <v-hover v-slot="{ hover }">
                    <div style="display: flex; justify-content: space-between">
                        <div style="display: flex">
                            <div style="padding-right: 20px" v-if="index !== 0">
                                <operator-component :operators="operators" :operator="conditions.operator" @operatorChanged="operatorChanged" :onlyEqual="onlyEqual ? onlyEqual : false"/>
                            </div>
                            <simple-condition-component :initial_string="initial_string" :condition="condition"
                                :requireTextInputForTypeArray="requireTextInputForTypeArray"
                                :operators="operators"
                                :operation_types="operation_types"
                                :onlyEqual="onlyEqual ? onlyEqual : false"
                                @conditionTypeChanged="(value) => {condition.type=value}"/>
                        </div>
                        <div v-if="hover">
                            <v-icon 
                                @click="deleteRow(index)" class="addRowIcon">
                                $fas_trash
                            </v-icon>
                        </div>
                    </div>
                </v-hover>
            </div>
        </div>
    </div>
    
</template>


<script>
import obj from "@/util/obj"
import SimpleConditionComponent from './SimpleConditionComponent.vue'
import OperatorComponent from './OperatorComponent.vue'
export default {
    name: "CondtionsTable",
    props: {
        conditions: obj.objN,
        onlyEqual: {
            type:Boolean
        },
        initial_string: obj.strR,
        table_header: obj.strR
    },
    components: {
        SimpleConditionComponent,
        OperatorComponent
    },
    data() {
        var requireTextInputForTypeArray = ['EQUALS_TO','REGEX', 'STARTS_WITH', 'ENDS_WITH']
        var operators = [
                "OR", "AND"
            ]
        var operation_types = [
                {
                    "text": "equals to",
                    "value": "EQUALS_TO"
                },
                {
                  "text": "starts with",
                  "value": "STARTS_WITH"
                },
                {
                    "text": "ends with",
                    "value": "ENDS_WITH"
                },
                {
                    "text": "matches regex",
                    "value": "REGEX"
                },
                {
                    "text": "is number",
                    "value": "IS_NUMBER"
                }

            ]

        return {
            requireTextInputForTypeArray,
            operators,
            operation_types
        }
    },
    methods: {
        addNewRow() {
            if (!this.conditions) {
                this.conditions = {"operator": "AND", "predicates": []}
            }
            this.conditions.predicates.push({"type": "EQUALS_TO", "value": null})
        },
        deleteRow(index) {
            this.conditions.predicates.splice(index,1)
        },
        operatorChanged(value) {
            this.conditions.operator = value
        },
        getValueStyle(value) {
            let width = !value  ? 6 : value.length + 4
            return { 'width': width+ 'ch' }
        },
    },
    created() {
    },
    computed: {

    },
    watch : {

    }

}

</script>

<style lang="sass" scoped>

.condition-row
    width: 100%
    border-style: solid
    padding: 16px
    border-color: var(--themeColorDark13)
    border-top-width: 0.5px
    border-bottom-width: 1px
    border-left-width: 1px
    border-right-width: 1px

    &.first
        border-top-width: 1px

.addRowIcon
    color: var(--themeColor)
    width: 100%
    height: 100%

.v-icon.v-icon:after
    background-color: transparent


</style>

