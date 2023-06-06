<template>
  <div class="akto-background d-flex justify-space-around">
<!--    <div>-->
<!--      <img src="@/assets/logo_nav.svg" alt="logo" class="brand-logo"/>-->
<!--      <img src="@/assets/brand_name.svg" alt="akto" class="brand-name"/>-->
<!--    </div>-->
    <div class="signup-container">
      <div v-if="step == 1">
        <div class="heading">
          <img src="/public/favicon.svg" height="32" class="mr-2"/>
          <span style="vertical-align: text-bottom">Sign up for</span>
          <span style="vertical-align: text-bottom; color: var(--themeColor)"> akto </span>
        </div>
        
        <div v-if="(isLocalDeploy && isSaas)">
         <google-auth @signInCallback="signInCallback" scope="" purpose="signup">
          <v-btn class="sign-up-third-party" plain width="100%">
            <div>
              <img src="@/assets/logo_google.svg" alt="Google" class="logo"/>
              <span class="text">Sign up with Google</span>
            </div>
          </v-btn>
        </google-auth>
      </div>

        <!--<slack-auth @signInCallback="signInCallback" :login-only="true">
          <v-btn class="sign-up-third-party" plain width="100%">
            <div>
              <img src='@/assets/logo_slack.svg' alt="Slack" class="logo"/>
              <span class="text">Sign up with Slack</span>
            </div>
          </v-btn>
        </slack-auth> -->

<!--        <v-btn class="sign-up-third-party" plain width="100%" v-for="(item,index) in signupOptions" :key="index">-->
<!--          <div>-->
<!--            <img :src="'/dist/images/logo_'+item.toLowerCase()+'.svg'" :alt="item" class="logo"/>-->
<!--            <span class="text">Sign up with {{item}}</span>-->
<!--          </div>-->
<!--        </v-btn>-->
        <!-- <div class="or">or</div> -->

        <div v-if="username || (!isSaas)">
          <login-fields @fieldsChanged="fieldsChanged" @enterPressed="signupUser" :isSignUp ="true" class="mt-4"/>
          <v-btn class="signup-btn" :disabled="disableButtons" :loading="signupLoading" @click="signupUser" style="background-color:  var(--themeColor) !important; color: var(--white) !important">
            Sign up
          </v-btn>
        </div>
        <div class="legal-docs">
          By clicking on "Sign Up" you are agreeing to Akto's <a class="clickable-docs" href="https://www.akto.io/terms-of-service" target="_blank" >Terms of Service</a>, <a class="clickable-docs" href="https://www.akto.io/privacy" target="_blank">Privacy Policy</a> and <a class="clickable-docs" href="https://www.akto.io/cookie" target="_blank">Cookie Policy</a>.
        </div>
      </div>
    </div>
  </div>
</template>

<script>
import GoogleAuth from "@/apps/dashboard/shared/components/GoogleAuth";
import api from "@/apps/dashboard/shared/api";
import SlackAuth from "@/apps/dashboard/shared/components/SlackAuth";
import LoginFields from "@/apps/login/LoginFields";
import request from "@/util/request";

export default {
  name: "PageSignup",
  components: {SlackAuth, GoogleAuth, LoginFields},
  data() {
    return {
      signupOptions: ['Facebook'],
      formModel: null,
      disableButtons: true,
      step: 1,
      substep: 1,
      password1: null,
      password2: null,
      usernameRules: [
          (v) => {
            const pattern = /^(([^<>()[\]\\.,;:\s@"]+(\.[^<>()[\]\\.,;:\s@"]+)*)|(".+"))@((\[[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}\.[0-9]{1,3}])|(([a-zA-Z\-0-9]+\.)+[a-zA-Z]{2,}))$/
            var ret = pattern.test(v)
            this.disableButtons = !ret
            return ret
          }
      ],
      username: window.SIGNUP_EMAIL_ID || null,
      isLocalDeploy: window.DASHBOARD_MODE && window.DASHBOARD_MODE.toLowerCase() === 'local_deploy',
      isSaas: window.IS_SAAS && window.IS_SAAS.toLowerCase() == 'true',
      fullName: null,
      companyName: null,
      role: null,
      employees: null,
      newEmail: null,
      allEmails: [],
      signupLoading: false
    }
  },
  methods: {
    fieldsChanged(formModel) {
      if (formModel.valid) {
        this.formModel = {...formModel}
      }
      this.disableButtons = !formModel.valid
    },
    goToToday() {
      this.$router.push('/testing')
    },
    signInCallback(a) {
      if (a && a.code) {
        var objThis = this
        api.sendGoogleAuthCodeToServer(a.code).then(resp => {
          this.driveNamesToFiles = {...this.driveNamesToFiles, ...resp.files}
          objThis.driveNames.unshift(Object.keys(resp.files)[0])
        })
      }
    },
    signupUser () {
      this.signupLoading = true
      request({
        url: '/signup-email',
        method: 'post',
        data: {email: this.formModel.username, password: this.formModel.password, invitationCode:window.SIGNUP_INVITATION_CODE}
      }).then((resp) => {
        this.signupLoading = false
        if (resp && resp.indexOf("<")== -1) {
          window._AKTO.$emit('SHOW_SNACKBAR', {
            show: true,
            text: resp,
            color: 'red'
          })
        } else {
          window.location.href = '/dashboard/onboarding'
        }
      })
    }
  }
}
</script>

<style scoped lang="sass">
.brand-logo
  height:  48px
  color: var(--white)

.brand-name
  font-size: 48px
  color: var(--white)
  font-size: 48px
  vertical-align: super
  padding-left: 5px

.akto-background
  background: linear-gradient(180deg, var(--backgroundColor1) -7.13%, var(--themeColor) 16.86%, var(--backgroundColor2) 64.29%)
  height: 100%
  text-align: center

.signup-container
  background: var(--white)
  box-shadow: 0px 8px 12px var(--rgbaColor18)
  border-radius: 8px
  padding: 32px
  color: var(--themeColorDark)
  width: 450px
  margin: auto

  & .heading
    font-weight: 600
    font-size: 28px

  & .subheading
    margin: 24px 0px
    font-size: 16px

  & .sign-up-third-party
    margin-bottom: 16px
    border: 1px solid var(--themeColorDark15)
    box-sizing: border-box
    border-radius: 4px
    text-align: center
    height: 56px
    display: flex
    flex-direction: column
    justify-content: space-around

    & .logo
      height:  20px
      vertical-align: middle

    & .text
      font-size: 16px
      font-weight: 600
      margin-left: 5px

  & .or
    margin-bottom: 16px
    font-size: 16px

.signup-btn
  background-color: var(--themeColor) !important
  font-size: 16px
  font-weight: 600
  vertical-align: middle
  border-radius: 4px
  text-transform: none
  letter-spacing: normal
  width: 100%
  height: 48px !important
  margin-bottom: 24px

  &.v-btn--disabled
    opacity: 0.3

.legal-docs
  font-size: 12px

.clickable-docs
  cursor: pointer
  color: var(--themeColor) !important
  text-decoration: underline
</style>