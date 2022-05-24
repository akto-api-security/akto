<template>
  <v-main class="akto-background">
    <div class="page-wrapper ma-9 d-flex flex-column">
        <div>
          <img src="@/assets/logo_nav.svg" alt="logo" class="brand-logo"/>
          <img src="@/assets/brand_name.svg" alt="akto" class="brand-name"/>
        </div>
        <div class="signup-container">
          <v-stepper v-model="step">
            <div class="stepper-header">
              <span style="position: absolute; left: 0" v-if="step > 1">
                <v-btn
                  @click="step--"
                  icon
                  color="#FFFFFF"
                ><v-icon>$fas_arrow-left</v-icon></v-btn>
              </span>
              <span>Step {{step}}/{{shouldAskName ? 3 : 2}}</span>
            </div>

            <v-stepper-items class="stepper-content">
              <v-stepper-content step="1" v-if="shouldAskName">
                <div class="heading">Tell us more about your work</div>
                <div class="subheading">
                  <v-text-field
                      required
                      v-model="companyName"
                      label="What is your  full name?"
                      height="45px"
                      dark
                  />
                </div>
                <v-btn
                  class="signup-btn"
                  :disabled="!companyName"
                  :style="{
                    'background-color': companyName ? '#FFFFFF !important' : 'rgb(255, 255, 255, 0.3) !important',
                    'color': companyName ? '#6200EA !important' : '#FFFFFF !important'
                  }"
                  @click="step++"
                >
                  Next
                </v-btn>
              </v-stepper-content>
              <v-stepper-content :step="shouldAskName ? 2 : 1">
                <div class="heading">Tell us more about your team</div>
                <div class="subheading">
                  <v-select
                      required
                      :items="['DevOps', 'Frontend', 'Security', 'Backend', 'Other']"
                      v-model="teamName"
                      label="Which team do you belong to in your organization?"
                      dark
                  />
                  <v-btn
                    class="signup-btn"
                    :disabled="!teamName"
                    @click="goToToday()"
                    :style="{
                      'background-color': teamName ? '#FFFFFF !important' : 'rgb(255, 255, 255, 0.3) !important',
                      'color': teamName ? '#6200EA !important' : '#FFFFFF !important'
                    }"
                  >
                    Next
                  </v-btn>
                </div>
              </v-stepper-content>

              <v-stepper-content  :step="shouldAskName ? 3 : 2">
                <div class="heading">Who do you work closely with?</div>
                <div>
                  <v-combobox
                    v-model="allEmails"
                    :items="[]"
                    multiple
                    chips
                    dark
                    label="Invite your team members"
                    :placeholder="`name@${domainName}`"
                  >
                    <template v-slot:selection="{ attrs, item, parent, selected }">
                      <v-chip
                          v-bind="attrs"
                          :class="['chips-base', usernameRules[0](item) ? 'email-valid' : 'email-invalid']"
                          :input-value="selected"
                          label
                          small
                      >
                        <span>
                          {{ item }}
                        </span>
                        <v-icon
                            @click="parent.selectItem(item)"
                            :class="usernameRules[0](item) ? 'email-valid' : 'email-invalid'"
                        >
                          $fas_times
                        </v-icon>
                      </v-chip>
                    </template>
                  </v-combobox>
                </div>
                <v-btn
                  class="signup-btn"
                  :disabled="!allEmails || allEmails.length == 0"
                  @click="goToToday()"
                  :style="{
                    'background-color': !allEmails || allEmails.length == 0 ? 'rgb(255, 255, 255, 0.3) !important' : '#FFFFFF !important',
                    'color': !allEmails || allEmails.length == 0 ? '#FFFFFF !important' : '#6200EA !important'
                  }"
                >
                  Invite
                </v-btn>
                <v-btn class="signup-btn" outlined @click="goToToday()" style="background-color:  #FFFFFF00 !important; color: #FFFFFF !important">
                  Skip for now
                </v-btn>
              </v-stepper-content>
            </v-stepper-items>
          </v-stepper>
        </div>
    </div>
  </v-main>
</template>

<script>
import request from "@/util/request";

export default {
  name: "PageOnboard",
  data  () {
    var initData = {...window.SIGNUP_INFO}
    initData.companyName = initData.companyName || ''
    initData.step = initData.step || ''
    initData.teamName = initData.teamName || ''
    initData.allEmails = []
    initData.newEmail = null
    initData.domainName = initData.email.split("@")[1]
    initData.shouldAskName = !initData.username || (initData.email === initData.username)
    initData.usernameRules = [
      (v) => {
        const pattern = /^(([^<>()[\]\\.,;:\s@"]+(\.[^<>()[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/
        var ret = pattern.test(v) && v.indexOf("@") != -1 && v.split("@")[1]===initData.domainName
        this.disableButtons = !ret
        return ret
      }
    ]
    return initData
  },
  methods: {
    goToToday () {
      request({
        url: '/dashboard/setup/add-signup-info',
        method: 'post',
        data: {
          companyName: this.companyName,
          teamName: this.teamName,
          allEmails: this.allEmails
        }
      }).then((resp) => {
        window.location.href = '/dashboard/testing'
      })
    }
  }
}
</script>

<style scoped lang="sass">

.signup-container
  max-width: 500px
  width: 500px
  padding: 150px 15px 200px 15px
  margin-left: auto
  margin-right: auto

.page-wrapper
  height: 100%

.akto-background
  background: linear-gradient(180deg, #D500F9 -7.13%, #6200EA 16.86%, #2E006D 64.29%)

.brand-logo
  height:  24px
  color: #FFFFFF

.brand-name
  height:  24px
  color: #FFFFFF
  padding-left: 5px

.heading
  font-weight: 600
  font-size: 28px
  color: #FFFFFF
  margin-bottom: 20px

.subheading
  margin: 24px 0px
  font-size: 16px
  color: #FFFFFF

.signup-btn
  font-size: 16px
  font-weight: 600
  vertical-align: middle
  border-radius: 4px
  text-transform: none
  letter-spacing: normal
  width: 100%
  height: 48px !important
  margin-bottom: 24px

.legal-docs
  font-size: 12px

.stepper-content
  text-align: left

.stepper-header
  text-align: center
  font-size: 16px
  font-weight:  600
  color: rgba(255, 255, 255, 0.5)

.v-stepper
  box-shadow: none !important
  background-color: unset !important

.v-stepper__content
  padding: 24px 0px

.chips-base
  border-radius: 4px !important
  padding: 8px 0px 8px 8px
  height: 32px
  margin: 8px 8px 4px 0 !important
  background-color: #FFFFFF !important

.v-select__slot
  border: 1px solid #000000

  & .v-label-active
    top: -10px

.email-valid
  color: var(--v-greenMetric-base)  !important
  font-size: 12px !important

.email-invalid
  color: var(--v-redMetric-base) !important
  font-size: 12px !important

.v-select__selections
  & input
    font-size: 12px !important

</style>