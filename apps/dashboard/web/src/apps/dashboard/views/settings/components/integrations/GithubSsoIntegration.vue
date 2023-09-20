<template>
  <div style="max-width: 400px;">
    <div>
      <span class="heading">Client Id</span>
      <div v-if="githubClientIdOrig" class="field-value">{{ githubClientIdOrig }}</div>
      <simple-text-field 
        :readOutsideClick="false"
        placeholder="Add Github App's Client Id"
        @onKeystroke="addGithubClientId"
        v-else
      />          
    </div>
    <div class="pt-4">
      <span class="heading">Client Secret</span>
      <div v-if="githubClientIdOrig" class="field-value">***********************************</div>
      <simple-text-field 
        :readOutsideClick="false"
        placeholder="Add Github App's Client Secret"
        @onKeystroke="addGithubClientSecret"
        v-else
      />          
    </div>

    <div class="pt-8" v-if="githubClientIdOrig">
      <v-dialog v-model="showDeleteGithubSsoPopup" max-width="400px">

        <v-card>
          <v-card-title class="akto-app">Are you sure?</v-card-title>
          <v-card-text>
            Are you sure you want to remove Github SSO Integration? This might take away access from existing Akto users.
            This action cannot be undone.
            <v-spacer />
            <v-card-actions>
              <v-spacer></v-spacer>
              <v-btn primary color="var(--redMetric)" @click="deleteGithubSso" dark>Delete Github SSO</v-btn>
              <v-btn secondary color="var(--themeColorDark)" @click="() => {showDeleteGithubSsoPopup = false}" dark>Cancel</v-btn>
            </v-card-actions>
          </v-card-text>
        </v-card>

        
      </v-dialog>
      <v-btn primary color="var(--themeColor)" @click="() => {showDeleteGithubSsoPopup = true}" dark>Delete Github SSO</v-btn>
    </div>

    <div class="pt-8"  v-else>
      <v-dialog v-model="showAddGithubSsoPopup" max-width="400px">

        <v-card>
          <v-card-title class="akto-app">Are you sure?</v-card-title>
          <v-card-text>
            Are you sure you want to add Github SSO Integration? This will enable all members of your GitHub account to access Akto dashboard.
            <v-spacer />
            <v-card-actions>
              <v-spacer></v-spacer>
              <v-btn primary color="var(--greenMetric)" @click="addGithubSso" dark>Add Github SSO</v-btn>
              <v-btn secondary color="var(--themeColorDark)" @click="() => {showAddGithubSsoPopup = false}" dark>Cancel</v-btn>
            </v-card-actions>
          </v-card-text>
        </v-card>


        </v-dialog>
        <v-btn primary color="var(--themeColor)" @click="() => {showAddGithubSsoPopup = true}" dark :disabled="!githubClientId || !githubClientSecret">Save Github SSO</v-btn>

    </div>
  </div>
</template>

<script>

import SimpleTextField from '@/apps/dashboard/shared/components/SimpleTextField'
import api from "../../api.js"

export default {
    name: "GithubSsoIntegration",
    components: {
      SimpleTextField
    },
    data () {
      return {
        githubClientId: null,
        githubClientSecret: null,
        githubClientIdOrig: null,
        showDeleteGithubSsoPopup: false,
        showAddGithubSsoPopup: false
      }
    },
    methods: {
      addGithubClientId(clientId) {
        this.githubClientId = clientId
      },

      addGithubClientSecret(clientSecret) {
        this.githubClientSecret = clientSecret
      },

      addGithubSso() {
        api.addGithubSso(this.githubClientId, this.githubClientSecret).then(resp => {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: "Github SSO added successfully",
                color: 'green'
            })
            window.location.reload()
        })
      },
      deleteGithubSso() {
        api.deleteGithubSso().then(resp => {
          window._AKTO.$emit('SHOW_SNACKBAR', {
              show: true,
              text: "Github SSO removed successfully",
              color: 'green'
          })
          this.githubClientIdOrig = null
          this.githubClientId = null
        })
      }
    },
    async mounted() {
      let {githubClientId} = await api.fetchGithubSso();
      this.githubClientIdOrig = githubClientId
    }
}
</script>

<style lang="sass" scoped>
.heading
  font-size: 14px
  font-weight: 500

.field-value
  font-size: 14px
  font-weight: 400

</style>