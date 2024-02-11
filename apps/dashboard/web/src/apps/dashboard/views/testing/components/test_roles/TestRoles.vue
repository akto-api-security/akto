<template>
    <div class="data-types-container d-flex">
        <a-card :title="title" color="var(--rgbaColor2)" style="min-height: 600px; flex: 1 1 20%"
            icon_right="$fas_plus" icon_right_color="var(--themeColor)" @icon_right_clicked="openCreateRoleDialog">
            <div v-for="(testRole, index) in testRoles" :key="index">
                <test-role-card :item="testRole" @selectedEntry="entryUpdated" @refreshRoles="refreshRoles"/>
            </div>
        </a-card>
        <div class="details-container">
            <slot name="details-container" />
        </div>
    </div>
</template>

<script>
import ACard from "@/apps/dashboard/shared/components/ACard"
import TestRoleCard from "./components/TestRoleCard"
import obj from '@/util/obj'
export default {
    name: "TestRoles",
    components: {
        ACard,
        TestRoleCard
    },
    props: {
        title: obj.strR,
        testRoles: obj.arrR
    },
    methods: {
        refreshRoles() {
            this.$emit("refreshRoles")
        },
        async fetchAllEndpointsForCollection(collectionId) {
            await this.$store.dispatch('test_roles/fetchApiInfoKeyForCollection', {collectionId})
            let collectionsMap = this.$store.state.test_roles.collectionWiseApiInfoKeyMap
            let collection = {}
            collection[collectionId] = collectionsMap[collectionId]
            return collection

        },
        async fillConditions(conditions, predicates, operator) {
            predicates.forEach(async (e, i) => {
                let valueFromPredicate = e.value
                if (Array.isArray(valueFromPredicate) && valueFromPredicate.length > 0) {
                    let valueForCondition = {}
                    let collectionId = valueFromPredicate[0]['apiCollectionId']
                    await this.fetchAllEndpointsForCollection(collectionId).then((value) => {
                        let collectionMap = value
                        let apiInfoKeyList = []
                        collectionMap[collectionId].forEach(element => {
                            let found = false
                            for (var index = 0; index < valueFromPredicate.length; index++) {
                                if (element['url'] === valueFromPredicate[index]['url']
                                    && element['method'] === valueFromPredicate[index]['method']
                                    && element['apiCollectionId'] === valueFromPredicate[index]['apiCollectionId']) {//Found one 

                                    apiInfoKeyList.push({
                                        url: element['url'],
                                        method: element['method'],
                                        apiCollectionId: element['apiCollectionId'],
                                        'checked': true,
                                        title: element['method'] + element['url'],
                                        value: element['method'] + element['url']
                                    })

                                    found = true
                                }
                            }
                            if (!found) {
                                apiInfoKeyList.push({
                                    url: element['url'],
                                    method: element['method'],
                                    apiCollectionId: element['apiCollectionId'],
                                    'checked': false,
                                    title: element['method'] + element['url'],
                                    value: element['method'] + element['url']
                                })
                            }
                        })
                        valueForCondition[collectionId] = apiInfoKeyList
                        conditions.push({ operator: operator, type: e.type, value: valueForCondition })

                    })
                } else {
                    conditions.push({ operator: operator, type: e.type, value: valueFromPredicate })
                }
            })
        },
        updateConditionsOnRoleSelection(selectedRole) {
            let testingEndpoint = selectedRole.endpointLogicalGroup.testingEndpoints
            let conditions = []
            if (testingEndpoint.andConditions) {
                this.fillConditions(conditions, testingEndpoint.andConditions.predicates, 'AND')
            }
            if (testingEndpoint.orConditions) {
                this.fillConditions(conditions, testingEndpoint.orConditions.predicates, 'OR')
            }

            this.$store.commit('test_roles/SAVE_CONDITIONS', { conditions })

        },
        async entryUpdated(item) {
            this.$store.commit('test_roles/SAVE_CREATE_NEW', { createNew: false })
            this.$store.commit('test_roles/SAVE_SELECTED_ROLE', { selectedRole: item })
            this.updateConditionsOnRoleSelection(this.$store.state.test_roles.selectedRole)
        },
        openCreateRoleDialog() {
            //When create dialog opens, create new is true, and selected is empty
            this.$store.commit('test_roles/SAVE_CREATE_NEW', { createNew: true })
            this.$store.commit('test_roles/SAVE_CONDITIONS', { conditions: [] })
            this.$store.commit('test_roles/SAVE_SELECTED_ROLE', { selectedRole: {} })
        },
    }
}

</script>

<style lang="sass" scoped>
.main
    background-color: var(--themeColorDark)
    
.details-container
    flex: 1 1 80%
    margin: 12px 10px 12px 0px
    border: 1px solid var(--themeColorDark13)
    border-radius: 4px
    box-shadow: none !important

</style>