<template>
    <simple-layout title="Settings">    
        <div class="pa-8">
            <div class="d-flex">
                <div class="entry-text">Account ID</div>
                <div class="entry-value">{{getActiveAccount()}}</div>
            </div>
        </div>
    </simple-layout>
</template>

<script>
import SimpleLayout from '@/apps/dashboard/layouts/SimpleLayout'

export default {
    name: "PageSettings",
    components: { 
        SimpleLayout 
    },
    methods: {
        getActiveAccount() {
            return this.$store.state.auth.activeAccount
        }
    }
}
</script>

<style lang="sass" scoped>
.entry-text
    font-weight: 500
    margin-right: 16px
</style>