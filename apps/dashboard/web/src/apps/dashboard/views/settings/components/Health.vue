<template>
    <div>
        <json-viewer
            v-if="content"
            :contentJSON="content"
            :errors="{}"
        />                    
    </div>
</template>

<script>
import api from '../api'
import JsonViewer from "@/apps/dashboard/shared/components/JSONViewer"
export default {
    name: "Health",
    components: {
        JsonViewer
    },
    data() {
        return {
            content: {},
        }
    },
    created() {
        api.health().then(resp => {
            console.log(resp);
            this.content = resp
        })
    }
}
</script>

<style lang="sass" scoped>

</style>