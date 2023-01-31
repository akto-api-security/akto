<template>
    <json-viewer
        v-if="content"
        :contentJSON="content"
        :errors="errors"
    />
    
</template>

<script>

    import JsonViewer from "@/apps/dashboard/shared/components/JSONViewer"
    import { mapState } from 'vuex'

    export default {
        name: "APIDetails",
        components: {
            JsonViewer
        },
        computed: {
            ...mapState('today', ['content', 'errors'])
        }
    }
</script>


<style scoped>

</style>