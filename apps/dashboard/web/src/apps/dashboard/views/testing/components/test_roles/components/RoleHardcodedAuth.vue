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
        <v-btn icon plain @click="addAuthParam">
            <v-icon color="var(--themeColorDark)">$fas_plus</v-icon>
        </v-btn>  

      </div>
  
  
      <v-row v-for="(nK, index) in newKey" :key="'auth_param_'+index">
        <v-col md="2" class="autocomplete-options fw-500 input-value">
          <v-text-field v-model="newKey[index]">
            <template slot="label">
              <div class="d-flex">
                Header key
              </div>
            </template>
          </v-text-field>
  
        </v-col>
        <v-col md="6" class="autocomplete-options input-value">
          <v-text-field v-model="newVal[index]">
            <template slot="label">
              <div class="d-flex">
                Header value
              </div>
            </template>
  
          </v-text-field>
        </v-col>
        <v-col md="1">
            <v-btn icon plain @click="() => deleteAuthParam(index)">
                <v-icon color="var(--themeColorDark)">$fas_trash</v-icon>
            </v-btn>  
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
        newKey: [],
        newVal: [],
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
      },
      deleteAuthParam(index) {
        this.newKey.splice(index, 1)
        this.newVal.splice(index, 1)
        this.newKey = [...this.newKey]
        this.newVal = [...this.newVal]
      },
      addAuthParam() {
        this.newKey.push("")
        this.newVal.push("")
      }
    },
    computed: {
      disableSave() {
        if (this.newKey.length == 0 || this.newVal.length == 0) return true
        for (let index = 0; index < this.newKey.length; index++) {
            if (!this.newKey[index] || !this.newVal[index]) return true
        }
        
  
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