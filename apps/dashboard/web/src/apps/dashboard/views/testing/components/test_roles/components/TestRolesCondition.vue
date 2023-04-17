<template>
    <div>
        <div>
            <v-row style="padding: 36px 12px 12px 12px">
                <test-role-conditions-table initial_string="Endpoint" :selectedRole="selectedRole"
                    table_header="Conditions" :operators="operators"
                    :requireTextInputForTypeArray="requireTextInputForTypeArray"
                    :requireMapInputForTypeArray="requireMapInputForTypeArray" :operation_types="operation_types" />
            </v-row>
        </div>

        <v-row style="padding-top: 30px">
            <div style="padding: 12px">
                <v-btn @click="calculateMatchingEndpoints" dark color="var(--themeColor)" class="save-btn" height="40px" width="100px">
                    Test
                </v-btn>
            </div>
            <div style="padding: 12px">
                <v-btn @click="createCollection" dark color="var(--themeColor)" class="save-btn" height="40px" width="100px">
                    Save
                </v-btn>
            </div>
        </v-row>
    </div>
</template>

<script>
import TestRoleConditionsTable from './TestRoleConditionsTable.vue'
import { mapState } from 'vuex'
import api from "../../../../observe/collections/api"
import obj from "@/util/obj"
export default {
    name:"TestRolesCondition",
    components:{
        TestRoleConditionsTable
    },
    props:{
        collectionName: obj.strR,
    },
    data(){
        var operators = [
            'OR',
            'AND'
        ]
        var requireTextInputForTypeArray = [
            'CONTAINS'
        ]
        var requireMapInputForTypeArray = [
            'BELONGS_TO',
            'NOT_BELONGS_TO'
        ]
        var operation_types = [
            { value: 'CONTAINS', text: 'contains', operators: ['OR', 'AND'] },
            { value: 'BELONGS_TO', text: 'belongs to', operators: ['OR'] },
            { value: 'NOT_BELONGS_TO', text: 'does not belongs to', operators: ['AND'] }
        ]
        return {
            operators,
            operation_types,
            requireTextInputForTypeArray,
            requireMapInputForTypeArray,
            showNewRow:false,
        }
    },
    methods: {
        async calculateMatchingEndpoints() {
            let andConditions = this.filterContainsConditions('AND') || null
            let orConditions = this.filterContainsConditions('OR') || null
            let resp = await api.getLogicalEndpointMatchingCount(andConditions,orConditions)
            this.$emit('matchedEndpoints',resp.matchingEndpointCount)
        },
        async createCollection() {
            let andConditions = this.filterContainsConditions('AND') || null
            let orConditions = this.filterContainsConditions('OR') || null
            let name = this.collectionName

            await this.$store.dispatch('collections/createCollection', {
                name, andConditions, orConditions
            }).then((resp) => {
                this.$emit('collections-selected',andConditions,orConditions)
                window._AKTO.$emit('SHOW_SNACKBAR', {
                    show: true,
                    text: `Collection created successfully!`,
                    color: 'green'
                })
            }).catch((err) => {
                window._AKTO.$emit('SHOW_SNACKBAR', {
                    show: true,
                    text: `Error creating collection!` + err,
                    color: 'red'
                })
            })

            //this.$store.dispatch('collections/createCollection', {name})
            this.showNewRow = false
            },
        filterContainsConditions(operator) {//operator is string as 'OR' or 'AND'
            let filteredCondition = {}
            let found = false
            filteredCondition['operator'] = operator
            filteredCondition['predicates'] = []
            this.conditions.forEach(element => {
                if (element.value && element.operator === operator) {
                    if (element.type === 'CONTAINS') {
                        filteredCondition['predicates'].push({ type: element.type, value: element.value })
                        found = true
                    } else if (element.type === 'BELONGS_TO' || element.type === 'NOT_BELONGS_TO') {
                        let collectionMap = element.value
                        let collectionId = Object.keys(collectionMap)[0]

                        if (collectionMap[collectionId]) {
                            let apiKeyInfoList = []
                            collectionMap[collectionId].forEach(apiKeyInfo => {
                                if (apiKeyInfo['checked']) {
                                    apiKeyInfoList.push({ 'url': apiKeyInfo['url'], 'method': apiKeyInfo['method'], 'apiCollectionId': apiKeyInfo['apiCollectionId'] })
                                    found = true
                                }
                            })
                            if (apiKeyInfoList.length > 0) {
                                filteredCondition['predicates'].push({ type: element.type, value: apiKeyInfoList })
                            }
                        }
                    }
                }
            });
            if (found) {
                return filteredCondition;
            }
        },
    },
    computed:{
        ...mapState('test_roles', ['testRoles', 'loading', 'selectedRole', 'listOfEndpointsInCollection', 'createNew', 'conditions']),
    }
}
</script>