<template>
    <div>
    <div class="pt-4">
      <div class="fw-500">
        Api header conditions
      </div>
  
      <v-row>
        <v-col md="2" class="autocomplete-options fw-500 input-value">
          <v-text-field v-model="headerKey">
            <template slot="label">
              <div class="d-flex">
                Header key
              </div>
            </template>
          </v-text-field>
  
        </v-col>
        <v-col md="6" class="autocomplete-options input-value">
          <v-text-field v-model="headerVal">
            <template slot="label">
              <div class="d-flex">
                Header value
              </div>
            </template>
  
          </v-text-field>
        </v-col>
      </v-row>
    </div>
    <div class="pt-4">
  
      <div class="fw-500">
        Hardcoded token
      </div>
  
  
      <v-row>
        <v-col md="2" class="autocomplete-options fw-500 input-value">
          <v-text-field v-model="newKey">
            <template slot="label">
              <div class="d-flex">
                Header key
              </div>
            </template>
          </v-text-field>
  
        </v-col>
        <v-col md="6" class="autocomplete-options input-value">
          <v-text-field v-model="newVal">
            <template slot="label">
              <div class="d-flex">
                Header value
              </div>
            </template>
  
          </v-text-field>
        </v-col>
      </v-row>
  
      <div class="d-flex jc-end">
        <secondary-button @click="addAuthToRole" text="Save" color="var(--themeColor)" :disabled="disableSave"/>
        <secondary-button @click="closeNewAuth" text="Cancel" color="var(--themeColorDark)"/>
      </div>
    </div>
    </div>
  </template>
  
  <script>
  import SecondaryButton from "@/apps/dashboard/shared/components/buttons/SecondaryButton.vue";
  
  export default {
    name: "RoleHardcodedAuth",
    components: {SecondaryButton},
    data() {
      return {
        newKey: "",
        newVal: "",
        headerKey: "",
        headerVal: ""
      }
    },
    methods: {
      addAuthToRole() {
        this.$emit('addAuthToRole', {newKey: this.newKey, newVal: this.newVal, headerKey: this.headerKey, headerVal: this.headerVal})
      },
      closeNewAuth() {
        this.$emit('closeNewAuth')
      }
    },
    computed: {
      disableSave() {
        if (!this.newKey || !this.newVal) return true
  
        if (this.headerKey && !this.headerVal) return true
  
        if (!this.headerKey && this.headerVal) return true
  
        return false
      }
    }
  }
  </script>
  
  <style scoped lang="sass">
  .input-value
    padding-right: 8px
    color: var(--themeColorDark)
  </style>