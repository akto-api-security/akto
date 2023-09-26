<template>
  <v-app class="akto-app">
    <v-navigation-drawer v-model="drawer" app width="200px" :mini-variant.sync="mini" class="akto-nav" dark permanent>

      <v-dialog v-model="showCreateAccountDialog" max-width="600px">
        <v-card>
          <v-card-title class="akto-app">Create new account</v-card-title>
          <v-card-text>
            <v-text-field label="Enter name" v-model="newName" dense class="e2e-new-account-name" />
            <v-spacer />
            <v-card-actions>
              <v-spacer></v-spacer>
              <v-btn color="#6200EA" text @click="createNewAccount" :loading="loading"
                class="e2e-save-account">Save</v-btn>
            </v-card-actions>
          </v-card-text>
        </v-card>
      </v-dialog>

      <span class="expand-nav clickable" @click="mini = !mini">
        <v-icon v-if="mini" color="var(--themeColor)">$fas_angle-right</v-icon>
        <v-icon v-else color="var(--themeColor)">$fas_angle-left</v-icon>
      </span>

      <template #prepend>
        <v-list-item>
          <v-list-item-icon class="prepend-akto-icon">
            <img src="@/assets/logo_nav.svg" alt="Akto" class="logo" />
          </v-list-item-icon>
          <v-list-item-title class="akto-title">akto</v-list-item-title>
        </v-list-item>
      </template>

      <div class="nav-section">
        <v-list dense nav class="left-nav">
          <v-list-item v-for="(item, index) in this.myItems" :key="index" :to="item.link || ''"
            :class="item.children ? 'group-nav-drawer' : 'row-nav-drawer'">
            <template v-if="item.children">
              <v-list-group :value="false" no-action :key="index" class="nav-item-group"
                active-class="active-team-group">
                <template v-slot:prependIcon>
                  <v-icon class="icon-nav-drawer">{{ item.icon }}</v-icon>
                </template>
                <template v-slot:activator>
                  <div class="content-nav-drawer">
                    <v-list-item-title v-text="item.title" class="title-nav-drawer" />
                  </div>
                </template>

                <v-list-item v-for="(child, cIndex) in item.children" :key="cIndex" :to="child.link"
                  class="row-dashboard" active-class="active-team-group">

                  <v-list-item-icon class="icon-nav-drawer dashboard-icon">
                    <v-icon size="10">{{child.icon}}</v-icon>
                  </v-list-item-icon>
                  <v-list-item-content>
                    <v-list-item-title class="title-nav-drawer" v-text="child.title"></v-list-item-title>
                  </v-list-item-content>
                </v-list-item>
              </v-list-group>
            </template>
            <template v-else>
              <v-list-item-icon class="icon-nav-drawer">
                <v-icon>{{ item.icon }}</v-icon>
              </v-list-item-icon>
              <v-list-item-content class="content-nav-drawer">
                <v-list-item-title class="title-nav-drawer">
                    {{ item.title }}
                    <div v-if="item.title === 'Test Editor'" class="beta-version">
                      Beta
                    </div>
                </v-list-item-title>
              </v-list-item-content>
            </template>
          </v-list-item>
          <v-spacer />
        </v-list>
      </div>

      <v-list dense nav class="left-nav" style="margin-top: auto">
        <v-list-item class='row-nav-drawer' id="beamer-btn">
          <v-list-item-icon class="icon-nav-drawer">
            <v-icon>$fas_gift</v-icon>
          </v-list-item-icon>
          <v-list-item-content class="content-nav-drawer">
            <v-list-item-title class="title-nav-drawer">Updates</v-list-item-title>
          </v-list-item-content>
        </v-list-item>

        <v-list-item class='row-nav-drawer' @click="openDocs">
          <v-list-item-icon class="icon-nav-drawer">
            <v-icon>$fas_user-secret</v-icon>
          </v-list-item-icon>
          <v-list-item-content class="content-nav-drawer">
            <v-list-item-title class="title-nav-drawer">Help</v-list-item-title>
          </v-list-item-content>
        </v-list-item>

        <simple-menu :items="myAccountItems">
          <template v-slot:activator2>
            <v-list-item class='row-nav-drawer e2e-show-accounts'>
              <v-list-item-icon class="icon-nav-drawer">
                <owner-name :owner-name="getUsername()" :owner-id="0" :show-name="false" />
              </v-list-item-icon>
              <v-list-item-content class="content-nav-drawer">
                <v-list-item-title class="title-nav-drawer">My accounts</v-list-item-title>
              </v-list-item-content>
            </v-list-item>
          </template>
        </simple-menu>
      </v-list>
    </v-navigation-drawer>

    <v-main class="akto-background" :style="{ 'padding-left': mini ? '56px' : '200px'}">
      <div class="page-wrapper">
        <router-view>
          <template slot="universal-ctas">
          </template>
        </router-view>
        <div class="akto-external-links">
          <v-btn dark depressed color="var(--gptColor)" v-if="renderAktoButton" @click="showGPTScreen()">
                Ask AktoGPT
                <v-icon size="16">$chatGPT</v-icon>
          </v-btn>

          <v-btn primary dark depressed color="var(--hexColor40)" @click='openDiscordCommunity'>
            Ask us on <v-icon size="16">$fab_discord</v-icon>
          </v-btn>

          <v-btn primary dark depressed class="github-btn" @click='openGithubRepoPage'>    
            <v-icon size="16">$githubIcon</v-icon>  
            Star
          </v-btn>
        </div>
      </div>
    </v-main>

    <loading-snack-bar 
      v-for="(data,index) in this.loadingSnackBars"
      :key="'snackBar_' + index"
      :title=data.title
      :subTitle=data.subTitle
      :percentage=data.percentage
      :index="index"
      @close="closeLoadingSnackBar(data)"
    />
    <v-dialog
        v-model="showGptDialog"
        width="fit-content" 
        content-class="dialog-no-shadow"
        overlay-opacity="0.7"
      >
        <div class="gpt-dialog-container ma-0">
            <chat-gpt-input
                v-if="showGptDialog"
                :items="computeChatGptPrompts"
                :apiCollectionId="apiCollectionId"
                @addRegexToAkto="addRegexToAkto"
                @runTestsViaAktoGpt="runTestsViaAktoGpt"
                :owner-name="getUsername()"
            />
        </div>

    </v-dialog>
  </v-app>
</template>

<script>
import { mapGetters , mapState } from 'vuex';
import CreateAccountDialog from "./shared/components/CreateAccountDialog"
import api from "./appbar/api"
import OwnerName from "./shared/components/OwnerName";
import SimpleTextField from "./shared/components/SimpleTextField";
import SimpleMenu from "./shared/components/SimpleMenu"
import LoadingSnackBar from './shared/components/LoadingSnackBar';
import ChatGptInput from './shared/components/inputs/ChatGptInput.vue';
import apiFunc from "../dashboard/views/observe/inventory/api"
import func from "@/util/func"

export default {
  name: 'PageDashboard',
  components: {
    SimpleTextField,
    OwnerName,
    SimpleMenu,
    LoadingSnackBar,
    ChatGptInput
  },
  data() {
    const listAccounts = Object.entries(window.ACCOUNTS).map(x => {
      return {
        label: x[1],
        click: () => this.goToAccount(+(x[0])),
        class: +x[0] === window.ACTIVE_ACCOUNT ? "underline" : ""
      }
    })

    const myItems = [
      {
        title: 'Quick start',
        icon: '$fas_thumbs-up',
        link: '/dashboard/quick-start'
      },
      {
        title: 'Testing',
        icon: '$fas_home',
        link: '/dashboard/testing'
      },
      {
        title: 'Observe',
        icon: '$fas_search',
        children: [
          {
            title: 'API Inventory',
            icon: '$fas_exchange-alt',
            link: '/dashboard/observe/inventory'
          },
          {
            title: 'API Changes',
            icon: '$fas_sync-alt',
            link: '/dashboard/observe/changes'
          },
          {
            title: 'Sensitive Data',
            icon: '$fas_user-lock',
            link: '/dashboard/observe/sensitive'
          }
        ]
      },
      {
        title: 'Issues',
        icon: '$fas_exclamation-triangle',
        link: '/dashboard/issues'
      }
    ]

    return {
      msg: '',
      drawer: null,
      mini: true,
      profileMenu: false,
      myItems,
      myOrgs: this.getAccounts(),
      showField: {},
      showTeamField: false,
      newName: '',
      showCreateAccountDialog: false,
      loading: false,
      api_inventory_route: "/dashboard/observe/inventory",
      settings_route: "/dashboard/settings",
      showGptDialog:false,
      regexRequired:false,
      renderAktoButton:false,
      apiCollectionId:-1,
      isLocalDeploy: window.DASHBOARD_MODE && window.DASHBOARD_MODE.toLowerCase() === 'local_deploy',
      isSaas: window.IS_SAAS && window.IS_SAAS.toLowerCase() == 'true',
      myAccountLocalItems:[
      ...listAccounts,
        {
          label: '',
          click: () => { }
        },
        {
          label: 'Create new account',
          click: () => { this.showCreateAccountDialog = true }
        },
        {
          label: '',
          click: () => { }
        }
      ],
      myAccountItems: [
      {
          label: "Settings",
          click: () => this.$router.push('/dashboard/settings')
        },
        {
          label: "Switch to new UI",
          click: () => {
            api.updateAktoUIMode({'aktoUIMode':'VERSION_2'}).then((resp) => {
              let currPath = window.location.pathname
              if(currPath.includes("inventory")){
                let paths = currPath.split("/");
                if(paths.length > 5){
                  let path = ""
                  for(var i = 0 ; i < 5 ; i++){
                    path = path + paths[i] + '/' ;
                  }
                  window.location.pathname = path
                }else{
                  window.location.reload() ;
                }
              }else{
                window.location.reload()
              }
            })
          }
        },
        {
          label: "Terms and Policies",
          click: () => window.open("https://www.akto.io/terms-and-policies", "_blank")
        },
        {
          label: "Logout",
          click: () => {
            api.logout().then((resp) => {
              if(resp.logoutUrl){
                window.location.href = resp.logoutUrl;
                return;
              }
              window.location.href = "/login"
            })
          }
        }
      ],
      settingsPrompt: [
        {
          icon: "$fas_layer-group",
          label: "Write regex to find ${input}",
          prepareQuery: (filterApi) => { return {
              type: "generate_regex",
              meta: {
                  "input_query": filterApi
              }                        
          }},
          callback: (data) => console.log("callback Tell me all the apis", data)
        }
      ],
      collectionsGptPrompts: [
          {
              icon: "$fas_magic",
              label: "Create API groups",
              prepareQuery: () => { return {
                  type: "group_apis_by_functionality",
                  meta: {
                      "urls": this.filteredItems.map(x => x.endpoint),
                      "apiCollectionId": this.apiCollectionId
                  }                        
              }},
              callback: (data) => console.log("callback create api groups", data)
          },
          {
              icon: "$fas_layer-group",
              label: "Tell me APIs related to ${input}",
              prepareQuery: (filterApi) => { return {
                  type: "list_apis_by_type",
                  meta: {
                      "urls": this.filteredItems.map(x => x.endpoint),
                      "type_of_apis": filterApi,
                      "apiCollectionId": this.apiCollectionId
                  }                        
              }},
              callback: (data) => console.log("callback Tell me all the apis", data)
          }
      ],
      parameterPrompts: [
        {
            icon: "$fas_user-lock",
            label: "Fetch Sensitive Params",
            prepareQuery: () => { return {
                type: "list_sensitive_params",
                meta: {
                    "sampleData": this.parseMsg(this.allSamples[0].message),
                    "apiCollectionId": this.apiCollectionId
                }                        
            }},
            callback: (data) => console.log("callback create api groups", data)
        },
        {
            icon: "$fas_user-lock",
            label: "Generate curl for testing SSRF vulnerability",
            prepareQuery: () => { return {
                type: "generate_curl_for_test",
                meta: {
                    "sample_data": this.allSamples[0].message,
                    "response_details": this.parseMsgForGenerateCurl(this.allSamples[0].message),
                    "test_type": "ssrf",
                    "apiCollectionId": this.apiCollectionId
                }                        
            }},
            callback: (data) => console.log("callback create api groups", data)
        },
        {
            icon: "$fas_user-lock",
            label: "Generate curl for testing SQLI vulnerability",
            prepareQuery: () => { return {
                type: "generate_curl_for_test",
                meta: {
                    "sample_data": this.allSamples[0].message,
                    "response_details": this.parseMsgForGenerateCurl(this.allSamples[0].message),
                    "test_type": "sqlinjection",
                    "apiCollectionId": this.apiCollectionId
                }                        
            }},
            callback: (data) => console.log("callback create api groups", data)
        },
        {
            icon: "$fas_user-lock",
            label: "Suggest API Security tests for this API",
            prepareQuery: () => { return {
                type: "suggest_tests",
                meta: {
                    "sample_data": this.allSamples[0].message,
                    "response_details": this.parseMsgForGenerateCurl(this.allSamples[0].message),
                    "apiCollectionId": this.apiCollectionId
                }                        
            }},
            callback: (data) => console.log("callback create api groups", data)
        }
      ],
    }
  },
  async mounted() {
      this.myItems.push({
        title: 'Test Editor',
        icon: '$testEditorIcon',
        link: '/dashboard/test-editor'
      })
    this.myAccountItems.unshift(... ( this.isLocalDeploy ? this.myAccountLocalItems : [] ))
    window.Beamer.init();
    let i = setInterval(() => {
        this.$store.dispatch('dashboard/fetchActiveLoaders')
    }, 1000)
    await this.showAskAktoGPTButton()
  },
  methods: {
    ...mapGetters('auth', ['getUsername', 'getAvatar', 'getActiveAccount', 'getAccounts']),
    ...mapGetters('inventory', ['getUrl', 'getMethod']),
    openDiscordCommunity() {
      return window.open("https://discord.gg/Wpc6xVME4s")
    },
    showGPTScreen(){
      this.showGptDialog = true
    },
    addRegexToAkto(payload){
        if(this.regexRequired){
          this.showGptDialog = false
          return this.$store.dispatch('data_types/setNewDataTypeByAktoGpt', payload)
        }
    },
    async showAskAktoGPTButton(){
      let collectionId = -1;
      if(this.$route.params['apiCollectionId']){
        collectionId = this.$route.params['apiCollectionId']
        this.apiCollectionId = collectionId;
      }
      if(collectionId !== -1){
        apiFunc.fetchAktoGptConfig(collectionId).then((resp)=>{
            this.renderAktoButton = resp.currentState[0].state === "ENABLED";
            if(!this.renderAktoButton){
              return false
            }
            if(this.$route.path.includes(this.api_inventory_route) && this.$route.params['apiCollectionId']){
              this.renderAktoButton = true
            }
            else if(this.$route.path.includes(this.settings_route) && this.$route.hash === "#Data-types"){
              this.renderAktoButton = true
            }
            else{
              this.renderAktoButton = false
            } 
        })
      }else {
        if(this.$route.path.includes(this.api_inventory_route) && this.$route.params['apiCollectionId']){
          this.renderAktoButton = true
        }
        else if(this.$route.path.includes(this.settings_route) && this.$route.hash === "#Data-types"){
          this.renderAktoButton = true
        }
        else{
          this.renderAktoButton = false
        } 
      }
    },
    async runTestsViaAktoGpt(payload){
      console.log("caught payload", payload)
      payload['testName'] = "akto_gpt_test";
      payload['apiCollectionId'] = this.apiCollectionId;
      payload['recurringDaily'] = false;
      payload['testRunTime'] = -1;
      payload['maxConcurrentRequests'] = -1;
      payload['startTimestamp'] = func.timeNow();
      payload['apiInfoKeyList'] = [
          {
              "url": this.getUrl(),
              "method": this.getMethod(),
              "apiCollectionId": this.apiCollectionId
          }
      ]
      payload['source'] = "AKTO_GPT";
      await this.$store.dispatch('testing/scheduleTestForCustomEndpoints', payload)
      this.showGptDialog = false;
      window._AKTO.$emit('SHOW_SNACKBAR', {
          show: true,
          text: 'Triggered tests successfully!',
          color: 'green'
      });
    },
    parseMsg(jsonStr) {
        let json = JSON.parse(jsonStr)
        return {
            request: JSON.parse(json.requestPayload),
            response: JSON.parse(json.responsePayload)
        }
    },
    parseMsgForGenerateCurl(jsonStr) {
        let json = JSON.parse(jsonStr)
        let responsePayload = {}
        let responseHeaders = {}
        let statusCode = 0

        if (json) {
            responsePayload = json["response"] ?  json["response"]["body"] : json["responsePayload"]
            responseHeaders = json["response"] ?  json["response"]["headers"] : json["responseHeaders"]
            statusCode = json["response"] ?  json["response"]["statusCode"] : json["statusCode"]
        }

        return {
            "responsePayload": responsePayload,
            "responseHeaders": responseHeaders,
            "statusCode": statusCode
        };
    },
    openGithubRepoPage() {
      return window.open("https://github.com/akto-api-security/community-edition/")
    },
    openDocs() {
      window.open('https://docs.akto.io/deploy/self-hosted-deployment/aws-deploy', '_blank')
    },
    goToAccount(accId) {
      api.goToAccount(+accId)
    },
    createNewAccount() {
      let newAccountName = this.newName;
      this.newName = '';
      this.showCreateAccountDialog = false;
      api.saveToAccount(newAccountName).then(resp => {
        window.location.href="/dashboard/onboarding"
        window.location.reload()
      })
    },    saveNewTeam(name) {
      if (name.length > 0) {
        this.$store.dispatch('auth/addNewTeam', { name }).then(resp => {
          this.showTeamField = false
          this.$router.push('/dashboard/teams/' + resp.id)
        })
      }
    },
    closeLoadingSnackBar(data) {
      this.$store.dispatch('dashboard/closeLoader', data['hexId'])
    },
  },
  async beforeRouteUpdate(to, from, next) {
    next();
    await this.showAskAktoGPTButton();
  },
  computed: {
      ...mapState('dashboard', ['loadingSnackBars']),
      ...mapState('inventory',['filteredItems', 'allSamples']),
      computeChatGptPrompts(){
        if(this.$route.path.includes(this.settings_route) && this.$route.hash === "#Data-types"){
          this.regexRequired = true
          return this.settingsPrompt
        }
        else if(this.$route.path.includes(this.api_inventory_route) && this.$route.params['apiCollectionId']){
          this.regexRequired = false
          let tempArr = this.parameterPrompts
          if(this.$route.params['urlAndMethod']){
            if(this.allSamples.length == 0){
              tempArr = tempArr.slice(1)
              return tempArr
            }
            let json = JSON.parse(this.allSamples[0].message)

            let type = ""
            let payload = ""

            if(json.contentType){type = json.contentType.toString()}
            if(json.requestPayload){payload = json.responsePayload.toString()}

            const pattern = /^\{.*\}$/;
            if(!(type.indexOf('application/json') !== -1 ||  pattern.test(payload))){
              tempArr = tempArr.slice(1)
            }
            return tempArr
          }
          else{
            return this.collectionsGptPrompts
          }
        }
      },
  },
}
</script>

<style lang="sass" scoped>

.gpt-dialog-container
    background-color: var(--gptBackground)
.akto-toolbar
  & .logo
    height: 25px

  & .title
    margin-left: 10px
    font-size: 1.5rem !important
    font-weight: 600

.page-wrapper
  background-color: var(--white)
  border-radius: 8px 0 0 8px
  height: 100%
  position: relative

.group-nav-drawer
  padding: 0px

.akto-app
  color: var(--themeColorDark)

.akto-background
  background: linear-gradient(180deg, var(--backgroundColor1) -7.13%, var(--themeColor) 16.86%, var(--backgroundColor2) 64.29%)

.akto-nav
  background: linear-gradient(180deg, var(--backgroundColor1) -7.13%, var(--themeColor) 16.86%, var(--backgroundColor2) 64.29%)
  z-index: 20
  overflow: unset

.v-list-item__icon:first-child
  margin-right: unset !important

.prepend-akto-icon
  justify-content: space-around
  display: flex
  margin-right: 0px !important

.icon-nav-drawer
  justify-content: center
  margin-top: 2px !important
  margin-bottom: 2px !important

  & .v-icon
    color: white
    font-size: 16px

  & img
    max-width: 16px

.title-nav-drawer
  color:  var(--white)
  margin-left: 8px !important
  font-weight: 400 !important
  display: flex
  gap: 8px
  align-items: center

.beta-version 
  border-radius: 4px
  border: 1px solid
  padding: 0px 4px
  font-size: 10px

.nav-item-group
  width: 100%

.subtitle-nav-drawer
  color:  var(--white)
  margin-left: 8px !important

.v-card__title
  letter-spacing: normal !important

.v-list-item
  flex: unset

.left-nav
  display: flex
  flex-direction: column

.akto-title
  font-weight: 600
  font-size: 20px
  margin-left: 8px !important

.expand-nav
  position: absolute
  top: 25px
  right: -15px
  background-color: var(--white)
  z-index: 2
  width: 24px
  height: 24px
  background: var(--white)
  box-shadow: 0px 2px 7px var(--themeColorDark10)
  border-radius: 4px
  justify-content: space-around
  display: flex

.row-nav-drawer
  min-height: 32px

.content-nav-drawer
  padding: 4px 0

.nav-section
  border-bottom: 1px solid var(--rgbaColor4)

.add-teams-row
  cursor: pointer
  color: var(--rgbaColor19)
  font-size: 12px
  align-items: center
  padding: 8px 16px
  width: 100%
  justify-content: space-between

.add-teams-icon
  color: var(--rgbaColor19)
  font-size: 10px
  height: 100% !important

.team-icon
  width: 100%
  text-align: center
  font-weight: 600

.row-dashboard
  padding-left: 24px !important
  min-height: 0px !important

.dashboard-icon
  display: flex
  flex-direction: column
  justify-content: space-around
  margin-top: unset !important
  margin-bottom: unset !important
  height: 32px !important

.active-team-group
  color: var(--white) !important

  & .v-icon
    transform: rotate(90deg) !important
</style>

<style lang="sass">
.v-list-group__header__prepend-icon
  margin-right: 0px !important

.active-team-group
  color: var(--white) !important

  & .v-icon
    transform: rotate(90deg) !important

.v-navigation-drawer__content
  display: flex
  flex-direction: column

.github-btn
  background: linear-gradient(180deg, var(--hexColor26), var(--hexColor23) 90%)  
  color: var(--hexColor6) !important
  border: 1px solid var(--rgbaColor3)

.discord-btn
  background: var(--white) !important
  color: var(--hexColor6) !important
  border: 1px solid var(--rgbaColor3)

.akto-external-links
  position: absolute
  right: 0px
  top: 0px  
  padding: 12px 18px
  display: flex
  gap: 8px
</style>
