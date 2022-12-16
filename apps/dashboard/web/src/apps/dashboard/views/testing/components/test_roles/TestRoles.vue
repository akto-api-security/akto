<template>
    <div class="data-types-container d-flex">
                <a-card
                    :title="title" 
                    color="rgba(33, 150, 243)"
                    style="min-height: 600px; flex: 1 1 20%"
                    icon_right="$fas_plus"
                    icon_right_color="#6200EA"
                    @icon_right_clicked="openCreateRoleDialog"
                >
                    <div v-for="(testRole, index) in testRoles" :key="index">
                        <test-role-card :item="testRole" @selectedEntry="entryUpdated"/>
                    </div>
                </a-card>
            <div class="details-container" >
                <slot name="details-container"/>
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
        entryUpdated(item) {
            this.$store.commit('test_roles/SAVE_CREATE_NEW', {createNew: false})
            this.$store.commit('test_roles/SAVE_SELECTED_ROLE', {selectedRole: item})
        },
        openCreateRoleDialog() {
            //When create dialog opens, create new is true, and selected is empty
            this.$store.commit('test_roles/SAVE_CREATE_NEW', {createNew: true})
            this.$store.commit('test_roles/SAVE_SELECTED_ROLE', {selectedRole: {}})
        },
    }
}

</script>

<style lang="sass" scoped>
.main
    background-color: #47466A
    
.details-container
    flex: 1 1 80%

</style>