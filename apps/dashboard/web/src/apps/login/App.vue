<template>
  <v-app class="layout-auth primary">
    <v-main class="akto-background">
      <v-container class="page-login" fill-height>
        <v-row>
          <v-col>
            <v-card class="pa-3 page-login__card" plain :outlined="false">
              <v-card-title>
                <h1 class="display-1 page-login_title">
                  <img src="/public/favicon.svg" style="height: 30px;" />akto
                </h1>
              </v-card-title>

              <div v-if="(isLocalDeploy && isSaas)">
              <google-auth scope="" purpose="signin" class="ma-4">
                <v-btn class="sign-up-third-party" plain width="100%" style="height: 56px">
                  <div>
                    <img src="/assets/logo_google.svg" alt="Google" class="logo" />
                    <span class="text">Sign in with Google</span>
                  </div>
                </v-btn>
              </google-auth>
              <!-- 
              <div class="d-flex ma-4">
                <div class="d-flex all-logos-container">
                  <div class="d-flex flex-column justify-space-around text">
                    Log in with 
                  </div>   
                  <div class="d-flex"> 
                    <google-auth scope="" purpose="signin">
                      <v-btn large plain class="sign-in__button">
                          <div class="logo-container">
                            <img src='@/assets/logo_google.svg' alt="Google" class="logo"/>
                          </div>
                      </v-btn>
                    </google-auth>

                    <slack-auth :login-only="true" purpose="signin" class="pl-2">
                      <v-btn large plain class="sign-in__button">
                          <div class="logo-container">
                            <img src='@/assets/logo_slack.svg' alt="Slack" class="logo"/>
                          </div>
                      </v-btn>
                    </slack-auth>
                  </div>
                </div>
              </div>
 -->
              <div class="my-8 mx-4 divider-rule"><span class="primary--text">or</span></div>
            </div>
            <div v-if="githubClientId">
              <v-btn class="sign-up-third-party" plain width="100%" style="height: 56px" @click="goToGithub">
                <div>
                  <v-icon>$githubIcon</v-icon>
                  <span class="text">Sign in with GitHub</span>
                </div>
              </v-btn>

              <div class="my-8 mx-4 divider-rule"><span class="primary--text">or</span></div>
            </div>
            <div v-if="oktaAuthorisationUrl">
              <v-btn class="sign-up-third-party" plain width="100%" style="height: 56px" @click="loginViaOkta">
                <div>
                  <img src="/public/okta_logo.svg" alt="Okta" class="logo" />
                  <span class="text">Sign in with Okta</span>
                </div>
              </v-btn>

              <div class="my-8 mx-4 divider-rule"><span class="primary--text">or</span></div>
            </div>
            <div v-if="azureRequestUrl">
              <v-btn class="sign-up-third-party" plain width="100%" style="height: 56px" @click="loginViaAzure">
                <div>
                  <img src="/public/azure_logo.svg" alt="Okta" class="logo" />
                  <span class="text">Sign in with Azure SSO</span>
                </div>
              </v-btn>

              <div class="my-8 mx-4 divider-rule"><span class="primary--text">or</span></div>
            </div>
              <div class="ma-4">
                <login-fields @fieldsChanged="fieldsChanged" @enterPressed="login" :isSignUp="false" />
              </div>
              <v-card-actions>
                <v-btn large tile class="sign-in__button primary" @click="login" :loading="loadingLogin"
                  :disabled="disableButtons">
                  <span class="ma-4">Login</span>
                </v-btn>
                <div class="ma-4" style="font-size: 13px">Don't have an account? <span class="primary--text clickable"
                    @click="register">Sign up</span></div>
              </v-card-actions>


            </v-card>
          </v-col>
        </v-row>
      </v-container>
    </v-main>
  </v-app>
</template>

<script>
import LoginFields from "@/apps/login/LoginFields";
import GoogleAuth from "@/apps/dashboard/shared/components/GoogleAuth";
import SlackAuth from "@/apps/dashboard/shared/components/SlackAuth";
export default {
  name: 'PageLogin',
  components: {
    LoginFields,
    GoogleAuth,
    SlackAuth
  },
  data() {
    return {
      loadingLogin: false,
      loadingSignup: false,
      disableButtons: true,
      formModel: null,
      isLocalDeploy: window.DASHBOARD_MODE && window.DASHBOARD_MODE.toLowerCase() === 'local_deploy',
      isSaas: window.IS_SAAS && window.IS_SAAS.toLowerCase() == 'true',
      githubClientId: window.GITHUB_CLIENT_ID,
      oktaAuthorisationUrl: window.OKTA_AUTH_URL,
      azureRequestUrl: window.AZURE_REQUEST_URL,
    }
  },
  methods: {
    goToGithub() {
      window.location.href = ("https://github.com/login/oauth/authorize?client_id=" + this.githubClientId);
    },
    login() {
      this.loadingLogin = true
      var a = this.$store.dispatch('auth/login', this.formModel).catch((e) => {
        this.loadingLogin = false
      })
    },
    register() {
      this.$router.push('/signup')
    },
    fieldsChanged(formModel) {
      if (formModel.valid) {
        this.formModel = { ...formModel }
      }
      this.disableButtons = !formModel.valid
    },
    loginViaOkta(){
      window.location.href = this.oktaAuthorisationUrl
    },
    loginViaAzure(){
      window.location.href = this.azureRequestUrl
    }
  },
  async mounted() {
    if(window.IS_SAAS && window.IS_SAAS==="true"){
      window.location.href = "/";
    }
  }
}
</script>

<style lang="sass" scoped>
.layout-auth
  height: 50%
  width: 100%
  position: absolute
  top: 0
  left: 0
  content: ""
  z-index: 0

.page-login__card
  max-width: 400px
  margin: 0 auto
  border-radius: 8px !important
  border: 0px
  position: relative
  box-shadow: unset

.sign-up-third-party
  margin-bottom: 16px
  border: 1px solid var(--themeColorDark15)
  box-sizing: border-box
  border-radius: 4px
  text-align: center
  height: 56px
  display: flex
  flex-direction: column
  justify-content: space-around

.logo
  height:  30px
  vertical-align: middle

.text
  font-size: 16px
  font-weight: 600
  margin-left: 5px

.page-login_title
    font-weight: 600
    font-size: 30px !important
    margin-left: 5px
    color: var(--themeColor)
    font-family: Poppins, sans-serif !important

.akto-background
  background: linear-gradient(180deg, var(--backgroundColor1) -7.13%, var(--themeColor) 16.86%, var(--backgroundColor2) 64.29%)

.sign-in__button
  border-radius: 4px
  padding: 0px !important
  min-width: 0px !important
  background: linear-gradient(100deg,var(--backgroundColor1) -17.13%,var(--themeColor) 99.86%)

  &.v-btn--disabled
    background: var(--themeColorDark)
  

.logo-container  
  background: var(--white)
  height: 44px
  width: 44px
  border-radius: 4px
  padding: 6px

.all-logos-container
  background: var(--themeColor)  
  padding: 8px
  border-radius: 4px
  width: 100%
  justify-content: space-between

.divider-rule
  display: flex
  justify-content: space-around
  position: relative
  &::before
    border-top: 1px solid var(--themeColor)
    content: ""
    position: absolute
    inset: 50% 0px 0px

  > span
    background: var(--white)
    z-index: 20
    padding: 0px 8px

.signup-container
    background: #FFFFFF
    box-shadow: 0px 8px 12px rgba(0, 0, 0, 0.25)
    border-radius: 8px
    padding: 32px
    color: #47466A
    width: 450px
    margin: auto
    &.sign-up-third-party
      margin-bottom: 16px
      border: 1px solid rgba(71, 70, 106, 0.15)
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
</style>
