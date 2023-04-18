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
                <v-list-item-title v-text="item.title" class="title-nav-drawer" />
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
  </v-app>
</template>

<script>
import { mapGetters } from 'vuex';
import api from "./appbar/api"
import OwnerName from "./shared/components/OwnerName";
import SimpleTextField from "./shared/components/SimpleTextField";
import SimpleMenu from "./shared/components/SimpleMenu"
import LoadingSnackBar from './shared/components/LoadingSnackBar';
import {mapState} from 'vuex'

export default {
  name: 'PageDashboard',
  components: {
    SimpleTextField,
    OwnerName,
    SimpleMenu,
    LoadingSnackBar
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
      },
      {
        title: 'Tests library',
        icon: '$bookBookmark',
        link: '/dashboard/library'
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
      myAccountItems: [
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
        },
        {
          label: "Settings",
          click: () => this.$router.push('/dashboard/settings')
        },
        {
          label: "Terms and Policies",
          click: () => window.open("https://www.akto.io/terms-and-policies", "_blank")
        },
        {
          label: "Logout",
          click: () => {
            api.logout().then((resp) => {
              window.location.href = "/login"
            })
          }
        }
      ]
    }
  },
  mounted() {
    window.Beamer.init();
    let i = setInterval(() => {
        this.$store.dispatch('dashboard/fetchActiveLoaders')
    }, 1000)
  },
  methods: {
    ...mapGetters('auth', ['getUsername', 'getAvatar', 'getActiveAccount', 'getAccounts']),
    openDiscordCommunity() {
      return window.open("https://discord.gg/Wpc6xVME4s")
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
    }
  },

  computed: {
      ...mapState('dashboard', ['loadingSnackBars']),
  }
}
</script>

<style lang="sass" scoped>
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
  margin-left: 8px

.discord-btn
  background: #ffffff !important
  color: #24292f !important
  border: 1px solid rgba(27,31,36,.15)

.akto-external-links
  position: absolute
  right: 0px
  top: 0px  
  padding: 18px 24px
</style>
