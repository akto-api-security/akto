<template>
    <div style="width: 100%;">
        <div style="padding-bottom: 10px; font-weight: bold; display: inline-block">
            <span style="color: var(--themeColorDark)">
                {{ table_header }}
            </span>
            <v-icon @click="addNewRow" class="addRowIcon">
                $fas_plus
            </v-icon>
        </div>        
        <div v-if="conditions">
            <div v-for="(condition, index) in conditions" :key="index"
                :class="index == 0 ? 'condition-row first' : 'condition-row'">
                <spinner v-if="loading"></spinner>
                <v-hover v-else v-slot="{ hover }">
                    <div style="display: flex; justify-content: space-between">
                        <div style="display: flex">
                            <div style="padding-right: 20px" v-if="index !== 0">
                                <operator-component :operator="condition.operator"
                                    :operators="getOperatorsForCondition(condition)"
                                    @operatorChanged="(item) => {operatorChanged(item, index)}" />
                            </div>
                            
                            <simple-condition-component :requireTextInputForTypeArray="requireTextInputForTypeArray"
                                :requireMapInputForTypeArray="requireMapInputForTypeArray"
                                :operators="getOperatorsForCondition(condition)" :operation_types="operation_types"
                                :initial_string="initial_string" :condition="condition"
                                @collectionSelected="(event) => {fetchAllEndpointsForCollection(event, index)}"
                                @clickedApiEndpoint="(event) => {changeCheckedStatus(event, index)}"
                                @selectedAllApiEndpoints="(event) => {changeGlobalCheckedStatus(event, index)}"
                                @conditionTypeChanged="(value) => {conditionTypeChanged(value, index)}" />
                        </div>
                        <div v-if="hover">
                            <v-icon @click="deleteRow(index)" class="addRowIcon">
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
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import SimpleConditionComponent from '../../../../settings/components/data_types/components/SimpleConditionComponent.vue'
import OperatorComponent from '../../../../settings/components/data_types/components/OperatorComponent.vue'
import api from '../api'
export default {
    name: "TestRoleConditionsTable",
    props: {
        selectedRole: obj.objR,
        initial_string: obj.strR,
        table_header: obj.strR,
        operators: obj.arrR,
        requireTextInputForTypeArray: obj.arrR,
        requireMapInputForTypeArray: obj.arrR,
        operation_types: obj.arrR
    },
    data() {
        return {
            loading: false
        }
    },
    components: {
        SimpleConditionComponent,
        OperatorComponent,
        Spinner
    },
    methods: {
        async fetchAllEndpointsForCollection(event, index) {
            this.loading = true
            let collectionId = event.item.value
            await api.fetchCollectionWiseApiEndpoints(parseInt(collectionId)).then((resp) => {
                let listOfEndpointsInCollection = resp['listOfEndpointsInCollection']
                listOfEndpointsInCollection.forEach(element => {
                    element['title'] = element['method'] + ' ' + element['url']
                    element['value'] = element['method'] + ' ' + element['url']
                    element['checked'] = false
                })
                this.conditions[index]['value']={}
                this.conditions[index]['value'][collectionId] =  listOfEndpointsInCollection
                this.loading=false
            }).catch(() => {
                this.loading = false
            })
        },
        changeCheckedStatus(event, index) {
            if (this.conditions[index].value) {
                let collectionId = Object.keys(this.conditions[index].value)[0]
                this.conditions[index].value[collectionId].forEach((element, i) => {
                    if (element['url'] === event.item['url'] && element['method'] === event.item['method']) {
                        this.conditions[index].value[collectionId][i]['checked'] = event['checked']
                    }
                })
            }
        },
        changeGlobalCheckedStatus(event, index) {
            if (this.conditions[index].value) {
                let collectionId = Object.keys(this.conditions[index].value)[0]
                this.conditions[index].value[collectionId].forEach((element, i) => {
                    event.items.forEach(e => {
                        if (element['url'] === e['url'] && element['method'] === e['method']) {
                            this.conditions[index].value[collectionId][i]['checked'] = event['checked']
                    }
                    })
                })
            }
        },
        conditionTypeChanged(value, index) {
            this.conditions[index].type = value
            let operators = this.getOperatorsForCondition(this.conditions[index])
            if (!operators.includes(this.conditions[index].operator)) {
                this.conditions[index].operator=operators[0]
            }
            this.conditions[index].value = null
        },
        getOperatorsForCondition(condition) {
            let finalValue = null
            for (let i = 0; i < this.operation_types.length; i++) {
                let operation = this.operation_types[i]
                if (operation["value"] === condition.type) {
                    finalValue = operation['operators']
                    break
                }
            }

            return finalValue;
        },
        addNewRow() {
            if (!this.conditions) {
                this.conditions = [{ operator: "AND", type: 'CONTAINS', value : null }]
            } else {
                this.conditions.push({ operator: "OR", type: 'CONTAINS', value : null })
            }
        },
        deleteRow(index) {
            this.conditions.splice(index, 1)
        },
        operatorChanged(value, index) {
            this.conditions[index].operator = value
        },
        getValueStyle(value) {
            let width = !value ? 6 : value.length + 4
            return { 'width': width + 'ch' }
        },
    },
    created() {
    },
    computed: {
        conditions : {
            get() {
                return this.$store.state.test_roles.conditions
            },
            set(newValue) {
                this.$store.commit('test_roles/SAVE_CONDITIONS', {conditions:newValue})
            }
        },
        mapCollectionIdToName() {
            return this.$store.state.collections.apiCollections.reduce((m, e) => {
                m[e.id] = e.displayName
                return m
            }, {})
        }
    },
    watch: {
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

