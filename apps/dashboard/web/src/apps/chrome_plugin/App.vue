<template>
    <div class="main-app-container" data-app>
        <v-app-bar
            color="#6200EA"
            dense
            dark
            flat
        >
            <v-icon size="20">$akto</v-icon>

            <v-toolbar-title class="akto-title">akto</v-toolbar-title>

            <v-spacer></v-spacer>

            <v-tooltip 
                v-for="(btn, index) in btnActions"
                :key="index"
                bottom
            >
                <template v-slot:activator="{ on: tooltip, attrs }">
                    <v-btn 
                        @click="btn.action"
                        v-bind="attrs"
                        v-on="tooltip"
                        icon 
                        primary 
                        plain 
                        :ripple="false" 
                        width="20" 
                        :class="['settings-btn', btn.showIf ? 'show-btn': 'hide-btn']">
                        <v-icon size="16">{{btn.icon}}</v-icon>
                    </v-btn>
                </template>
                <span>{{btn.label}}</span>
            </v-tooltip>
        </v-app-bar>
        <div
            v-if="showDeleteDialogBox"
            class="dialog-box"
        >
            <div>
                <div class="question"> 
                    Delete data from 
                    <span class="question-website">{{storedWebsiteHostName}} </span> 
                    and start recording for 
                    <span class="question-website">{{tempWebsiteHostName}}</span> 
                    ? 
                </div>
            <div class="ma-2">
                <v-btn primary dark color="#6200EA" @click="emptyData(true)"  :ripple="false" class="ml-2">
                    Yes
                </v-btn>
                <v-btn primary @click="closePopup()"  :ripple="false"  class="ml-2">
                    No
                </v-btn> 
            </div>
            </div>           
        </div>
        <div v-if="!showDeleteDialogBox">
            <div class="ma-2" v-if="!userSignedIn">
                <v-icon size="12" color="#6200EA">$fas_info-circle</v-icon>
                You are not signed in. Click 
                <span class="clickable underline" @click=openDashboardInNewTab>here</span> 
                to sign in.
            </div>
        </div>
        <router-view v-if="!showDeleteDialogBox"/>
    </div>
</template>

<script>

import {mapState} from 'vuex'

export default {
    name: "App",
    data() {
        return {
            tabId: 0,
            port: null,
            showDeleteDialogBox: false,
            storedWebsiteHostName: '',
            tempWebsiteHostName: '',
            userSignedIn: false
        }
    },
    methods: {        
        emptyData(startListeningToNew) {
            let currWebsite = this.websiteHostName
            this.$store.commit('endpoints/EMPTY_STATE')  
            this.port.postMessage({"emptyState": currWebsite || true, startListeningToNew})
            this.showDeleteDialogBox = false
            if (startListeningToNew) {
                chrome.tabs.query({ active: true }, function (tabs) {
                    let tab = tabs[0];
                    chrome.tabs.sendMessage(
                        tab.id,
                        {"new_message_akto": true}
                    )
                });
            }
        },
        closePopup() {
            window.close()
        },
        login() {
            chrome.runtime.sendMessage({ action: 'login' }, function (params) {
                console.log('login', params)
            })
        },
        logout() {
            chrome.runtime.sendMessage({ action: 'logout' }, function (params) {
                console.log('logout', params)
            })
        },
        openDashboardInNewTab() {
            chrome.tabs.create({url: "https://app.akto.io/dashboard/observe/inventory"});
        }        
    },
    created () {
        let _this = this
        setInterval(() => {
            chrome.runtime.sendMessage({ action: 'user_signed_in' }, function (params) {
                _this.userSignedIn = params.user_signed_in
            })            
        }, 1000);

        chrome.tabs.query({ currentWindow: true, active: true }, function (tabs) {
            _this.tabId = tabs[0].id
        });

        this.port = chrome.extension.connect({
            name: "Akto Communication"
        });

        let _store = this.$store
        let _port = this.port
        _port.onMessage.addListener(function(msg) {
            if (msg.storedWebsiteHostNames && msg.storedWebsiteHostNames.length == 1) {
                _this.storedWebsiteHostName = msg.storedWebsiteHostNames[0]
                _this.tempWebsiteHostName = msg.currHostname
                _this.showDeleteDialogBox = true
            } else {
                _store.commit('endpoints/SAVE_ENDPOINTS', {endpoints: msg.endpoints, websiteHostName: msg.origin})
            }

            _port.postMessage({"received_msg": msg})
        });
    },
    computed: {
        ...mapState('endpoints', ['websiteHostName']),
        btnActions() {
            
            let ret = [
                {
                    icon: "$fas_redo",
                    label: "Clear data",
                    action: () => this.emptyData(false),
                    showIf: true
                },
                {
                    icon: "$fas_external-link-alt",
                    label: "Open dashboard",
                    action: () => {
                        chrome.tabs.create({url: "https://app.akto.io/dashboard/observe/inventory"});
                    },
                    showIf: this.userSignedIn
                }
            ]

            return ret
        }

    }
}
</script>

<style lang="sass" scoped>

.show-btn
    display: block !important

.hide-btn
    display: none

.main-app-container
    width: 449px
    min-height: 200px
    color: #47466A

.akto-title
  font-weight: 600
  font-size: 20px
  margin-left: 8px !important

.question
  font-size: 16px

.question-website
  font-weight: 600    

.settings-btn
    margin-right: 8px

.dialog-box
    padding: 30px 
</style>
.brdb
  border-bottom: 1px solid rgba(71, 70, 106, 0.2) !important

<style lang="sass">
.d-flex
    display: flex

.ml-2
    margin-left: 8px

.ma-2
    margin: 8px

.grey-text
    color: #47466A99

.underline
  text-decoration: underline

.v-tooltip__content
  font-size: 10px !important
  opacity: 1 !important
  background-color: #7e7e97
  border-radius: 2px
  transition: all 0s !important

.v-icon
  font-size: 16px !important
  width: 24px !important

.v-tooltip__content:after
  border-left: solid transparent 4px
  border-right: solid transparent 4px
  border-bottom: solid #7e7e97 4px
  top: -4px
  content: " "
  height: 0
  left: 50%
  margin-left: -5px
  position: absolute
  width: 0

.v-btn
  cursor: pointer !important
  text-transform: unset !important
  letter-spacing: normal

.highcharts-credits
  display: none
  
.coming-soon
    height: 271px
    margin-top: auto
    margin-bottom: auto
    color: #47466A3D
    font-size: 13px


.d-flex
    display: flex    

.d-grid-2
    display: grid    
    grid-template-columns: 1fr 1fr
    column-gap: 40px

.pa-2
    padding: 8px

.v-breadcrumbs
    padding: 0px !important    
</style>