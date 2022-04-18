<template>
    <div style="width: 100%;">
        <div style="padding-bottom: 10px; font-weight: bold; display: inline-block">
            <span style="color: #47466a">
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
                                <operator-component :operator="conditions.operator" @operatorChanged="operatorChanged"/>
                            </div>
                            <simple-condition-component :initial_string="initial_string" :condition="condition"/>
                        </div>
                        <div>
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
        initial_string: obj.strR,
        table_header: obj.strR
    },
    components: {
        SimpleConditionComponent,
        OperatorComponent
    },
    data() {
        return {

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


.v-icon.v-icon:after
    background-color: transparent


</style>

