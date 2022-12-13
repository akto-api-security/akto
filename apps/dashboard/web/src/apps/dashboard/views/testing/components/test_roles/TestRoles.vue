<template>
    <div class="data-types-container d-flex">
                <a-card
                    :title="title" 
                    color="rgba(33, 150, 243)"
                    style="min-height: 600px; flex: 1 1 20%"
                    icon_right="$fas_plus"
                    icon_right_color="#6200EA"
                    @icon_right_clicked="createNewRole"
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
        testRoles: obj.arrR,
        createNewRole: obj.funcR
    },
    methods: {
        entryUpdated(item) {
            this.$emit("selectedEntry", item)
        },
        toggleSuccessFunc (resp, item) {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `${item.name} `+ (item.active? 'de' : '') +`activated successfully!`,
                color: 'green'
            })
        },
        toggleFailureFunc (err, item) {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `An error occurred while `+ (item.active? 'de' : '')+`activating ${item.name}!`,
                color: 'red'
            })
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