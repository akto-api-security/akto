<template>
    <simple-layout title="Settings">    
        <div class="pa-8">
            <div class="d-flex">
                <div class="entry-text">Account ID</div>
                <div class="entry-value">{{getActiveAccount()}}</div>
            </div>
             <v-btn @click="sendInvitationEmail">   
                 Invite
             </v-btn>
                  
            
        </div>
    </simple-layout>
</template>

<script>
import SimpleLayout from '@/apps/dashboard/layouts/SimpleLayout'
import api from './api'
export default {
    name: "PageSettings",
    components: { 
        SimpleLayout 
    },
    methods: {
        getActiveAccount() {
            return this.$store.state.auth.activeAccount
        },
        sendInvitationEmail(){
            let spec={
                inviteeName: "Ankush",
                inviteeEmail: "ankush@akto.io",
                websiteHostName: window.location.origin
            }
            api.inviteUsers(spec)
        }
    }
}
</script>

<style lang="sass" scoped>
.entry-text
    font-weight: 500
    margin-right: 16px
</style>