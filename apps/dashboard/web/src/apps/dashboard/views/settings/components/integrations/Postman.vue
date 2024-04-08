<template>
  <div style="min-height:200px;margin-bottom:30px">
    <div class="d-flex" style="padding-bottom:10px">
      <v-avatar size="40px" style="margin-right: 15px">
        <v-icon :size="50" style="width: 50px !important">$postman</v-icon>
      </v-avatar>
      <h2 style="color: var(--themeColorDark); font-size: 16px; font-weight: 500" class="fd-column jc-sa">Postman</h2>
    </div>
    <div>
      <div class="d-flex">
        <div style="width: 150px" class="fd-column jc-sa">
          <h3 style="color: var(--themeColorDark); font-size: 13px">API Key</h3>
        </div>
        <div style="width: 550px">
          <v-text-field
              @click:append="openPassword = !openPassword"
              :type="openPassword ? 'text' : 'password'"
              v-model="postman_api_key"  
              clearable
              @change="postman_key_changed"
          >
            <template v-slot:append>  
              <v-icon class="icon-nav-drawer" @click="openPassword = !openPassword">{{openPassword ? '$fas_eye' : '$fas_eye-slash'}}</v-icon>
            </template>
          </v-text-field>
        </div>
      </div>

      <div class="d-flex"  v-if="postman_api_key && postman_api_key.length > 0">
        <div style="width: 150px">
          <h3 style="color: var(--themeColorDark); font-size: 13px">Workspace</h3>
        </div>
        <div style="width: 550px">
          <v-select
              :items="workspaces"
              item-text="name"
              item-value="id"
              v-model= "workspace"
              :loading="workspace_loading"
              label="Select workspace"
              loader-height="5"
              solo
              outlined
              flat
              hide-details
          />
        </div>
      </div>

      <div style="padding-top:20px">
        <v-btn
            color="var(--themeColor)"
            @click="save"
            :disabled="!can_be_saved"
            :loading="save_loading"
            style="color:white" 
        >Save
        </v-btn>
      </div>
    </div>
  </div>


</template>

<script>
import api from "../../api.js"
export default {
  name: "Postman.vue",
  data() {
    return {
      openPassword: false,
      workspaces: [],
      workspace: "",
      workspace_loading: false,
      postman_api_key: "",
      save_loading: false
    }
  },
  methods: {
    async fetchWorkspaces() {
      this.workspace_loading = true
      let resp = await api.fetchPostmanWorkspaces(this.postman_api_key)
      this.workspaces = resp.workspaces
      this.workspace_loading = false
    },
    postman_key_changed() {
      this.workspace = ""
      this.fetchWorkspaces()
    },
    async save() {
      if (this.can_be_saved) {
        this.save_loading = true
        await api.addOrUpdatePostmanCred(this.postman_api_key, this.workspace)
        this.save_loading = false
      }
    }
  },
  computed: {
    can_be_saved: function() {
      return this.workspace && this.postman_api_key
    }
  },
  mounted() {
    // TODO: get postman creds
    api.getPostmanCredentials().then((resp) => {
      if (resp.postmanCred.api_key !== null) {
        this.postman_api_key = resp.postmanCred.api_key
        this.workspace = resp.postmanCred.workspace_id
        this.fetchWorkspaces()
      }
    })
  }
}
</script>

<style>

.v-text-field .v-input__control .v-input__slot {
  max-width: 660px;
  min-height: auto !important;
  display: flex !important;
  align-items: center !important;
}

.v-input__append-inner {
  display: flex !important;
  align-items: center !important;
  height: 1px;
  width: 1px;
  margin-right: 15px;
}

</style>

<style lang="sass" scoped>
.icon-nav-drawer
  padding-top: 25px  

</style>