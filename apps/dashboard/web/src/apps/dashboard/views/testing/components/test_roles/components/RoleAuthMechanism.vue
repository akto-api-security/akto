<template>
    <div>
      <div class="d-flex jc-end py-2">
        <secondary-button @click="addNewAuth" text="Add auth" color="var(--themeColorDark)"></secondary-button>
      </div>
  
      <div v-if="showEmptyAuth">
        <role-single-auth :auth="null" @closeNewAuth="closeNewAuth" @addAuthToRole="addAuthToRole"/>
      </div>
  
      <div v-for="(auth, index) in auths" :key="index">
        <role-single-auth :def-auth="auth" @deleteAuthFromRole="() => deleteAuthFromRole(index)"/>
      </div>

    </div>
  </template>
  
  <script>
  
  import RoleHardcodedAuth from './RoleHardcodedAuth'
  import RoleSingleAuth from './RoleSingleAuth'
  import TokenAutomation from '../../token/TokenAutomation'

  import { mapState } from "vuex";
  import SecondaryButton from "@/apps/dashboard/shared/components/buttons/SecondaryButton";
  export default {
    name: "RoleAuthMechanism",
    components: {
      SecondaryButton,
      RoleHardcodedAuth,
      RoleSingleAuth,
      TokenAutomation
    },
    data() {
      return {
        showEmptyAuth: false
      }
    },
    methods: {
      addNewAuth() {
        this.showEmptyAuth = true
      },
      async addAuthToRole({authAutomationType, newKey, newVal, headerKey, headerVal, reqData, authParamsList}) {
        let apiCond = {}
        if (headerKey && headerVal) {
          apiCond[headerKey] = headerVal
        }
        
        let authParamData = []
        if (authAutomationType === "Hardcoded") {

          for (let index = 0; index < newKey.length; index++) {
              authParamData.push({
                  "key": newKey[index],
                  "value": newVal[index],
                  "where": "HEADER"
              })
          }

        } else {
          authParamData = authParamsList
        }

        await this.$store.dispatch('test_roles/addAuthToRole', {roleName: this.curr.name, apiCond, authParamData, authAutomationType, reqData})
        this.showEmptyAuth = false
      },
      async deleteAuthFromRole(index) {
        await this.$store.dispatch('test_roles/deleteAuthFromRole', {roleName: this.curr.name, index})
      },
      closeNewAuth() {
        this.showEmptyAuth = false
      }
    },
    computed: {
      ...mapState('test_roles', ['selectedRole']),
      curr() {
        return this.selectedRole
      },
      auths() {
        return this.curr.authWithCondList || []
      }
    }
  }
  </script>
  
  <style scoped lang="sass">
  
  </style>