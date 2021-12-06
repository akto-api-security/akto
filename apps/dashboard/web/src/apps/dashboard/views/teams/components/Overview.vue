<template>
    <div class="pa-4">
        <v-row no-gutters>
            <v-col
                cols="12"
                sm="12"
                md="4"
            >
                <a-card title="Members" icon="$fas_users">
                    <div class="user-add">
                        <v-icon class="user-add-icon">$fas_plus</v-icon>
                        <span class="user-add-cta">Add member</span>
                    </div>
                    <div class="team-overview-card">
                        <div
                            v-for="user in users"
                            :key="user.id"
                            class="user-details d-flex justify-space-between pa-4"
                        >
                            <owner-name
                                    :owner-name="user.name"
                                    :owner-id="user.id"
                                    :show-name="false"
                                    class="user-details-avatar"
                            />
                            <div class="user-container">
                                <div class="user-details-name">{{user.name}}</div>
                                <div class="user-details-login">{{user.login}}</div>
                            </div>
                            <div class="user-details-type">
                                User
                            </div>
                        </div>
                    </div>
                </a-card>
            </v-col>
            <v-col
                cols="12"
                sm="12"
                md="8"
            >
                <a-card title="Dashboards" icon="$fas_chart-line">
                    <div class="dashboard-add" plain>
                        <v-icon class="dashboard-add-icon">$fas_plus</v-icon>
                        <span class="dashboard-add-cta">Add dashboard</span>
                    </div>
                    <div class="team-overview-card">
                    </div>
                </a-card>
            </v-col>
        </v-row>
    </div>
</template>

<script>
    import ACard from "@/apps/dashboard/shared/components/ACard";
    import {mapState} from "vuex";
    import OwnerName from "@/apps/dashboard/shared/components/OwnerName";
    export default {
        name: "Overview",
        components: {
            ACard,
            OwnerName
        },
        methods: {

        },
        computed: {
            ...mapState('team', ['users', 'details'])
        }
    }
</script>

<style scoped lang="sass">

.team-overview-card
    height: 390px
    overflow-y: auto

.user
    &-add
        cursor: pointer
        margin: auto
        width: 100%
        padding-top: 12px
        display: flex
        justify-content: center

        &-icon
            font-size: 16px !important
            color: #304ffe
            border: 2px solid #304ffe
            border-radius: 50%
            width: 30px !important
            height: 30px !important
            margin-right: 12px

        &-cta
            font-size: 16px
            color: #304ffe
            font-weight: 400
            margin-top: auto
            margin-bottom: auto


    &-container
        width: 65%

    &-details
        &-avatar
            margin-top: auto
            margin-bottom: auto
            width: 15%

        &-name
            font-size: 12px
            color: #47466A
            font-weight: 500

        &-login
            font-size: 12px
            color: #2D243480
            font-weight: 400

        &-type
            font-size: 12px
            color: #2D243480
            font-weight: 400
            width: 20%

.dashboard
    &-add
        cursor: pointer
        margin: auto
        width: 100%
        padding-top: 12px
        display: flex
        justify-content: center

        &-icon
            font-size: 16px !important
            color: #304ffe
            border: 2px solid #304ffe
            border-radius: 50%
            width: 30px !important
            height: 30px !important
            margin-right: 12px

        &-cta
            font-size: 16px
            color: #304ffe
            font-weight: 400
            margin-top: auto
            margin-bottom: auto

    &-details
        background-color: #47466A0d
        border-radius: 4px
        height: 60px

        &-cards
            font-size: 12px
            color: #2D243480
            font-weight: 400
            margin-top: auto
            margin-bottom: auto
            padding-right: 16px


        &-name
            font-size: 13px
            color: #47466A !important
            font-weight: 500
            margin-top: auto
            margin-bottom: auto
            padding-left: 16px

</style>