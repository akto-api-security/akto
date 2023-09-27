<template>
    <div class="pa-2 brda my-2">
      <div v-if="readOnly">
        <div class="d-flex jc-end">
          <secondary-button @click="showDeleteAuthConfirmation = true" text="" icon="$fas_trash" color="var(--themeColorDark)"/>
        </div>
  
        <v-dialog v-model="showDeleteAuthConfirmation" width="400px">
          <div class="pa-4" style="background: #FFF">
            <div class="fw-500">
              Are you sure you want to delete this auth mechanism?
            </div>
  
            <div class="d-flex jc-end">
              <secondary-button @click="showDeleteAuthConfirmation = false" text="No" color="var(--themeColorDark)"/>
              <secondary-button @click="deleteThisAuth" text="Yes" color="var(--themeColor)"/>
            </div>
          </div>
        </v-dialog>
  
        <div class="fw-500">
          Api header conditions
        </div>
  
        <div v-for="(headerV, headerK, index) in headerKVPairs" :key="'header_'+index">
          <v-row>
            <v-col md="2" class="clipped-text fw-500">{{headerK}}</v-col>
            <v-col md="3" class="clipped-text">{{headerV}}</v-col>
          </v-row>
        </div>
        <div v-if="Object.keys(headerKVPairs).length == 0">
          -
        </div>
  
        <div class="fw-500 pt-4">
          {{automationType}} token
        </div>
  
        <div v-for="(authParam, index) in authParams" :key="'auth_'+index">
          <v-row>
            <v-col md="2" class="clipped-text fw-500">{{authParam.key}}</v-col>
            <v-col md="6" class="clipped-text">{{authParam.value}}</v-col>
          </v-row>
        </div>
      </div>
      <div v-else>
        <role-hardcoded-auth @addAuthToRole="addAuthToRole" @closeNewAuth="closeNewAuth"/>
      </div>
    </div>
  </template>
  
  <script>
  
  import obj from "@/util/obj";
  import SecondaryButton from "@/apps/dashboard/shared/components/buttons/SecondaryButton.vue";
  import ACard from "@/apps/dashboard/shared/components/ACard.vue";
  import RoleHardcodedAuth from "@/apps/dashboard/views/testing/components/test_roles/components/RoleHardcodedAuth.vue";

  export default {
    name: "RoleSingleAuth",
    components: {
      RoleHardcodedAuth, 
      ACard, 
      SecondaryButton
    },
    props: {
      defAuth: obj.objN
    },
    data() {
      return {
        showDeleteAuthConfirmation: false
      }
    },
    methods: {
      deleteThisAuth() {
        this.showDeleteAuthConfirmation = false
        this.$emit('deleteAuthFromRole')
  
      },
      addAuthToRole(funcProps) {
        console.log(funcProps);
        this.$emit('addAuthToRole', funcProps)
      },
      closeNewAuth() {
        this.$emit('closeNewAuth')
      }
    },
    computed: {
      headerKVPairs() {
        return this.defAuth?.headerKVPairs || {}
      },
      authParams() {
        return this.defAuth?.authMechanism.authParams || []
      },
      automationType() {
        return this.defAuth?.authMechanism.type || "Hardcoded"
      },
      readOnly() {
        return !!this.defAuth
      }
    }
  }
  </script>
  
  <style scoped lang="sass">
  .clipped-text
    font-size: 12px !important
    color: var(--themeColorDark) !important
    text-overflow: ellipsis
    overflow: hidden
    white-space: nowrap
    max-width: 600px !important
  
  
  </style>