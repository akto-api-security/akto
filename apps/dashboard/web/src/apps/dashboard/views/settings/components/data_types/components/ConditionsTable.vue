<template>
    <div style="width: 100%;">
        <div style="padding-bottom: 10px; font-weight: bold; display: inline-block">
            <span>
                {{table_header}}
            </span>
            <v-icon v-if="!conditions || !conditions.predicates || conditions.predicates.length == 0" @click="addNewRow" class="addRowIcon">
                $fas_plus
            </v-icon>
        </div>
        <div v-if="conditions && conditions.predicates">
            <div 
                v-for="(condition,index) in conditions.predicates" :key="index"
                :class="index == 0 ? 'condition-row first' : 'condition-row'"
            >
                <v-hover v-slot="{ hover }">
                    <v-row>
                        <v-col cols="1" v-if="index !== 0">
                            <operator-component :operator="conditions.operator" @operatorChanged="operatorChanged"/>
                        </v-col>
                        <v-col>
                            <simple-condition-component :initial_string="initial_string" :condition="condition"/>
                        </v-col>
                        <v-col cols="1">
                            <v-icon 
                                v-if="index === conditions.predicates.length-1"
                                @click="addNewRow" class="addRowIcon">
                                $fas_plus
                            </v-icon>
                            <v-icon v-else>
                            </v-icon>
                            <v-icon 
                                v-if="hover"
                                @click="deleteRow(index)" class="addRowIcon">
                                $fas_trash
                            </v-icon>
                        </v-col>

                    </v-row>
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
        initial_string: obj.strR,
        table_header: obj.strR
    },
    components: {
        SimpleConditionComponent,
        OperatorComponent
    },
    data() {
        return {
            operation_types: [
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
                }
            ]
        }
    },
    methods: {
        addNewRow() {
            if (!this.conditions) {
                this.conditions = {"operator": "AND", "predicates": []}
            }
            this.conditions.predicates.push({"type": "STARTS_WITH", "value": null})
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
        formatConditionType(value) {
            let finalValue = null
            for (let i=0; i < this.operation_types.length; i++) {
                let operation = this.operation_types[i]
                if (operation["value"] === value) {
                    finalValue = operation["text"]
                    break
                }
            }

            return finalValue;
        }
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
    border-color: #D3D3D3
    border-top-width: 1px
    border-bottom-width: 2px
    border-left-width: 2px
    border-right-width: 2px

    &.first
        border-top-width: 2px

.addRowIcon
    color: #6200EA
    width: 100%
    height: 100%

.condition-block
    background: #edecf0 
    width: fit-content
    padding: 0px 10px 10px 10px

</style>

