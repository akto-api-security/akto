<template>
    <div>
        <div>
            Use postman to send traffic to Akto and realize quick value. If you like what you see, we highly recommend using AWS or GCP traffic mirroring to get real user data for a smooth, automated and minimum false positive experience.
        </div>
        <spinner v-if="loading" />
        <div v-else>
            <div v-if="postman_setup_complete">
                <spinner v-if="workspace_loading" />
                <div v-else class="mt-3">
                    
                    <div>
                        <v-select v-model="workspace" :items="workspaces" item-text="name" item-value="id"
                            label="Select workspace" />
                            <v-btn
                                color="#6200EA"
                                @click="importPostmanWorkspace"
                                :disabled="importing_workspace"
                                style="color:white">
                                Import
                            </v-btn>
                    </div>
                </div>
            </div>
            <div v-else class="mt-3">
                Navigate to the Settings > Integrations page by clicking <a class="clickable-docs" href="/dashboard/settings" >here</a>  and add Postman key to start importing data
            </div>
        </div>
    </div>
</template>

<script>
import api from '../api.js'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
export default {
    name: "PostmanSource",
    components: {
        Spinner
    },
    data() {
        return {
            openPassword: false,
            workspaces: [],
            workspace: "",
            workspace_loading: false,
            postman_api_key: "",
            loading: true,
            postman_setup_complete: false,
            workspace_loading: true,
            importing_workspace: false
        }
    },
    mounted () {
        api.getPostmanCredentials().then((resp) => {
            this.loading = false;
            if (resp.postmanCred.api_key !== undefined) {
                this.postman_setup_complete = true
                this.postman_api_key = resp.postmanCred.api_key
                this.workspace = resp.postmanCred.workspace_id
                this.workspace_loading = true;
                this.fetchWorkspaces()
            }
        })
    },
    methods: {
        async fetchWorkspaces() {
            this.workspace_loading = true
            let resp = await api.fetchPostmanWorkspaces(this.postman_api_key)
            if(resp.workspaces){
                this.workspaces = resp.workspaces
                this.workspace_loading = false
            }
        },
        async importPostmanWorkspace(){
            this.importing_workspace = true;
            let resp = await api.importPostmanWorkspace(this.workspace);
            this.importing_workspace = false;
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `Imported! Go to API inventory and refresh to view your postman collection.`,
                color: 'green'
            })
        }
    }
}
</script>

<style lang="sass" scoped>
.clickable-docs
    cursor: pointer
    color: #6200B0 !important
    text-decoration: underline 
</style>