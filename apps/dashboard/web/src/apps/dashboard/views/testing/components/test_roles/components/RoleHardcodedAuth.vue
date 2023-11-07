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
      <v-radio-group v-model="authAutomationType" row>
        <v-radio
          v-for='n in ["Hardcoded", "LOGIN_REQUEST"]'
          :key="n"
          :label='n'
          :value="n"
          off-icon="$far_circle"
          on-icon="$far_dot-circle"
          :ripple="false"
        ></v-radio>
      </v-radio-group>
      <template v-if="authAutomationType === 'Hardcoded'">
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
    
      </template>
      <template v-else>
        <v-btn primary dark color="var(--themeColor)" @click="toggleLoginStepBuilder">Create</v-btn>
        <v-dialog v-model="showTokenAutomation" class="token-automation-modal">
          <token-automation :originalDbState="originalDbState" @closeLoginStepBuilder=toggleLoginStepBuilder @toggleOriginalStateDb=toggleOriginalStateDb />
        </v-dialog>
      </template>

      <div class="d-flex jc-end">
        <secondary-button @click="addAuthToRole" text="Save" color="var(--themeColor)" :disabled="disableSave"/>
        <secondary-button @click="closeNewAuth" text="Cancel" color="var(--themeColorDark)"/>
      </div>

    </div>
    </div>
  </template>
  
  <script>
  import SecondaryButton from "@/apps/dashboard/shared/components/buttons/SecondaryButton.vue";
  import TokenAutomation from '../../token/TokenAutomation'

  export default {
    name: "RoleHardcodedAuth",
    components: {
      SecondaryButton, 
      TokenAutomation
    },
    data() {
      return {
        newKey: [],
        newVal: [],
        headerKey: "",
        headerVal: "",
        showTokenAutomation: false,
        authAutomationType: "Hardcoded",
        originalDbState: null,
        reqData: null,
        authParamsList: null
      }
    },
    methods: {
      toggleLoginStepBuilder() {
        this.showTokenAutomation = !this.showTokenAutomation
      },
      toggleOriginalStateDb({reqData, authParamsList}) {
        this.reqData = reqData
        this.authParamsList = authParamsList

      },
      addAuthToRole() {
        if (this.authAutomationType === "Hardcoded") {
          this.$emit('addAuthToRole', {
            authAutomationType: this.authAutomationType, 
            newKey: this.newKey, 
            newVal: this.newVal, 
            headerKey: this.headerKey, 
            headerVal: this.headerVal
          })
        } else {
          this.$emit('addAuthToRole', {
            authAutomationType: this.authAutomationType, 
            reqData: this.reqData, 
            authParamsList: this.authParamsList
          })
        }
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
        if (this.authAutomationType === "Hardcoded") {
          if (this.newKey.length == 0 || this.newVal.length == 0) return true
          for (let index = 0; index < this.newKey.length; index++) {
              if (!this.newKey[index] || !this.newVal[index]) return true
          }
          
        } else {
          if (!this.reqData || !this.authParamsList) return true
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