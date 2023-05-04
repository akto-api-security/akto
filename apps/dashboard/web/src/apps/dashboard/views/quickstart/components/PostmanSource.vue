<template>
    <div>
        <div>
            Use postman to send traffic to Akto and realize quick value. If you like what you see, we highly recommend using AWS or GCP traffic mirroring to get real user data for a smooth, automated and minimum false positive experience.
        </div>
        <spinner v-if="loading" />
        <div v-else>
            <div>
                <v-radio-group v-model="uploadPostmanFile" row>
                    <v-radio
                        :value="0"
                        off-icon="$far_circle"
                        on-icon="$far_dot-circle"
                        :ripple="false"
                        class="radio"

                    >
                        <template slot="label">
                            <div class="radio-button-label">Import using Postman API Key</div>
                        </template>
                    </v-radio>
                    <v-radio
                        :value="1"
                        off-icon="$far_circle"
                        on-icon="$far_dot-circle"
                        :ripple="false"
                    >
                        <template slot="label">
                            <div class="radio-button-label">Import Postman collection file</div>
                        </template>
                    </v-radio>
                </v-radio-group>
                

                <div v-if="uploadPostmanFile === 0">
                    <step-builder :stepNumber="1">
                        <template slot="content">
                            <div>Open Postman. Click on Profile in the top-right corner of the screen.</div>
                        </template>
                    </step-builder>

                    <step-builder :stepNumber="2">
                        <template slot="content">
                            <div>From the drop-down menu, select "Settings" and then "API Keys."</div>
                        </template>
                    </step-builder>

                    <step-builder :stepNumber="3">
                        <template slot="content">
                            <div>Click the "Generate API Key" button. Copy the newly generated API key.</div>
                        </template>
                    </step-builder>

                    <step-builder :stepNumber="4">
                        <template slot="content">
                            <div>Paste Postman API key: </div>
                            <div style="width: 520px;" class="inline-text-field">
                                <v-text-field 
                                    height="13px" 
                                    v-model="postman_api_key" 
                                    :type="showKey? 'text' : 'password'"
                                    :append-icon="showKey ? '$fas_eye' : '$fas_eye-slash'"
                                    @click:append="showKey = !showKey"
                                />
                            </div>
                            <div class="postman-status">
                                <div v-if="this.workspace_loading" style="padding-left: 4px">
                                    <v-progress-circular indeterminate color="var(--lightGrey)" :size="12" :width="1.5"></v-progress-circular>
                                </div>
                                <div v-else>
                                    <div v-if="this.validApiKey && this.workspaces && this.workspaces.length>0">
                                        <v-icon color="var(--hexColor8)">$fas_check-circle</v-icon>
                                    </div>
                                    <div v-else>
                                        <v-icon color="var(--errorBtn)">$fas_times</v-icon>
                                    </div>
                                </div>
                            </div>
                        </template>
                    </step-builder>

                    <step-builder :stepNumber="5">
                        <template slot="content">
                            <div>Select workspace you wish to import: </div>

                            <v-menu offset-y>
                                <template v-slot:activator="{ on, attrs }">
                                    <div style="width: 200px" class="inline-text-field">
                                        <v-text-field 
                                            height="14px" 
                                            :value="workspace ? workspace['name'] : null" 
                                            v-bind="attrs" 
                                            v-on="on"
                                            append-icon="$fas_angle-down"
                                            readonly
                                        />
                                    </div>
                                </template>
                                <v-list>
                                    <v-list-item v-for="(item, index) in this.workspaces" :key="index" @click="workspaceSelected(item)">
                                        <v-list-item-title class="options">{{ item['name']}}</v-list-item-title>
                                    </v-list-item>
                                </v-list>
                            </v-menu>

                            <span v-if="this.workspace_loading" style="padding-left: 4px">
                                <v-progress-circular indeterminate color="var(--lightGrey)" :size="12" :width="1.5"></v-progress-circular>
                            </span>
                            <span v-else>
                                <v-icon color="var(--hexColor8)" v-if="this.workspace">$fas_check-circle</v-icon>
                            </span>
                        </template>
                    </step-builder>
                </div>
                

                <div v-if="uploadPostmanFile === 1">
                    <step-builder :stepNumber="1">
                        <template slot="content">
                            <div>Open Postman. Click on the collection you want to export from the left-hand sidebar.</div>
                        </template>
                    </step-builder>

                    <step-builder :stepNumber="2">
                        <template slot="content">
                            <div>Click the "Export" button in the top-right corner of the screen.</div>
                        </template>
                    </step-builder>

                    <step-builder :stepNumber="3">
                        <template slot="content">
                            <div>Select the "Collection v2.1" format and click export.</div>
                        </template>
                    </step-builder>

                    <step-builder :stepNumber="4">
                        <template slot="content">
                            <div>Upload postman collection: </div>

                            <div v-if="this.file">
                                <v-chip
                                    class="ma-2"
                                    color="var(--themeColorDark)"
                                    text-color="white"
                                    small
                                >
                                    {{ file.name }}
                                    <v-icon @click="deletePostmanFile">$fas_times</v-icon>
                                </v-chip>
                            </div>

                            <upload-file fileFormat=".json" @fileChanged="handleFileChange" tooltipText="upload" label="" type="uploadTraffic"/>
                        </template>
                    </step-builder>
                </div>
            </div>

            <div class="mt-3">
                <div class="replay-div">
                    <v-checkbox
                        v-model="allowReplay"
                        on-icon="$far_check-square" off-icon="$far_square"
                        :ripple="false"
                        class="align-center justify-center"
                    />
                    Allow Akto to replay API requests if responses are not found.
                </div>
                <v-btn
                    color="var(--themeColor)"
                    @click="importPostmanWorkspace"
                    :disabled="!postman_setup_complete"
                    style="color:var(--white)">
                    {{ import_button }}
                </v-btn>
            </div>
        </div>
    </div>
</template>

<script>
import api from '../api.js'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import UploadFile from '@/apps/dashboard/shared/components/UploadFile'
import SimpleTextField from '@/apps/dashboard/shared/components/SimpleTextField'
import StepBuilder from '@/apps/dashboard/views/quickstart/components/StepBuilder'

export default {
    name: "PostmanSource",
    components: {
        Spinner,
        UploadFile,
        SimpleTextField,
        StepBuilder
    },
    data() {
        return {
            workspaces: [],
            workspace_id: null,
            workspace: null,
            workspace_loading: false,
            postman_api_key: "",
            loading: true,
            importing_workspace: false,
            uploadPostmanFile: 0,
            showKey: true,
            allowReplay: true,
            file: null
        }
    },
    mounted () {
        api.getPostmanCredentials().then((resp) => {
            this.loading = false;
            if (resp.postmanCred.api_key) {
                this.postman_api_key = resp.postmanCred.api_key
                this.workspace_id = resp.postmanCred.workspace_id
            }
        })
    },
    methods: {
        async fetchWorkspaces() {
            this.workspace_loading = true
            this.workspaces = []
            this.workspace = null
            let resp = await api.fetchPostmanWorkspaces(this.postman_api_key)

            let foundWorkspaces = resp.workspaces && resp.workspaces.length > 0;
            if(foundWorkspaces) {
                this.workspaces = resp.workspaces
                this.workspaces.forEach((x)=>{
                    if (x.id === this.workspace_id) {
                        this.workspace = x
                        this.validateWorkspace = 2
                    }
                })

                if (!this.workspace) {
                    this.workspace = this.workspaces[0]   
                }

            }

            this.workspace_loading = false
        },
        async importPostmanWorkspace(){
            this.importing_workspace = true;
            if (this.uploadPostmanFile === 0) {
                let resp = await api.importPostmanWorkspace(this.workspace['id'], this.allowReplay, this.postman_api_key);
            } else {
                console.log(this.file);
                let resp = await api.importDataFromPostmanFile(this.file['content'], this.allowReplay);
            }
            this.importing_workspace = false;
        },
        workspaceSelected(item) {
            this.workspace = item
            this.workspace_id = item['id']
        },
        deletePostmanFile() {
            this.file = null
        },
        handleFileChange({file, label, type}) {
            var reader = new FileReader();
            reader.readAsText(file)
            reader.onload = async () => {
                this.file = {"content": reader.result, "name": file['name']}
            }
        },
        validatePostmanApiKey(value) {
            return value && value.length === 64 
        }
    },
    computed: {
        validApiKey: function() {
            return this.validatePostmanApiKey(this.postman_api_key)
        },
        postman_setup_complete: function() {
            if (this.uploadPostmanFile === 0) {
                return this.postman_api_key && this.workspace
            } else {
                return Boolean(this.file)
            }
        },
        import_button: function() {
            return this.uploadPostmanFile === 0 ? "Import" : "Upload"
        }
    },
    watch: {
        postman_api_key(newValue, oldValue) {
            this.workspaces = []
            this.workspace = null
            if (!this.validatePostmanApiKey(newValue)) return
            this.fetchWorkspaces()
        }
    }
}
</script>

<style lang="sass" scoped>
.clickable-docs
    cursor: pointer
    color: var(--quickStartTheme) !important
    text-decoration: underline 

.replay-div
    display: flex
    align-items: center
    height: 40px

.radio-button-label
    font-weight: 800 !important
    font-size: 13px !important
    color: var(--hexColor19)

.postman-status
    height: 20px
    display: flex
    align-items: center
    padding-left: 4px

</style>

<style scoped>
.inline-text-field >>> .v-text-field .v-input__control .v-input__slot {
    margin: 0px 4px 0px 4px !important;
    color: var(--hexColor19) !important;
}

.inline-text-field >>> .v-text-field input {
    color: var(--hexColor19) !important;
    font-weight: 400 !important;
    font-size: 13px !important;
}

.inline-text-field >>> .v-text-field .v-input__append-inner, .v-text-field .v-input__prepend-inner {
    align-self: auto;
    display: inline-flex;
    margin-top: 0px;
    line-height: 0;
    -webkit-user-select: none;
    -moz-user-select: none;
    -ms-user-select: none;
    user-select: none;
}

div >>> .v-icon__component, .v-icon__svg {
    height: 12px;
    width: 12px;
}


</style>