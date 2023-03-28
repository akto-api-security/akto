<template>
    <div class="main-loading-bar">
        <v-snackbar
            :timeout="-1"
            app
            color="var(--white)"
            v-model="loading_bar"
            :style="{padding: `0px 16px ${index * 150 + 80}px 0px` }"
            rounded="lg"
        >
            <div class="notification">
                <div class="d-flex">
                    <div>
                        <img src="/public/notif_icon.svg"/>
                    </div>
                    <div class="content">
                        <div class="title-main">
                            <div class="title-content"> {{ title }}</div>
                        </div>
                        <div class="subTitle">{{ subTitle }} </div>
                        <div class="progress">
                            <v-progress-linear :value="percentage" height="8" color="var(--themeColor)" rounded></v-progress-linear>
                        </div>
                        <div class="percentage">
                            <div>{{ percentage }}% uploaded</div>
                        </div>
                    </div>
                </div>
            </div>

            <template v-slot:action="{ attrs }">
                <div class="close-snackbar" @click="close">
                    <v-icon color="var(--themeColorDark)">$fas_times</v-icon>
                </div>
            </template>

        </v-snackbar>
    </div>
</template>

<script>
import obj from "@/util/obj";
export default {
    name: "LoadingSnackBar",
    props: {
        title: obj.strR,
        subTitle: obj.strR,
        percentage: obj.numR,
        index: obj.numR
    },
    data() {
        return {
            loading_bar: true
        }
    },
    methods: {
        close() {
            this.$emit("close")
        }
    }

}
</script>


<style lang="sass" scoped>
.notification
    width: 360px
    height: 110px
    padding: 4px 0px 0px 0px

.content
    padding: 0px 12px 0px 20px

.title-main
    display: flex
    justify-content: space-between
    margin-bottom: 4px

.title-content
    font-weight: 500
    font-size: 14px
    color: var(--themeColorDark)

.subTitle
    font-weight: 400
    font-size: 14px
    color: var(--themeColorDark)

.progress
    padding: 16px 0px 0px 0px

.percentage
    font-weight: 500
    font-size: 14px
    color: var(--themeColorDark)
    padding: 8px 0px 0px 0px
    display: flex
    justify-content: end

.close-snackbar
    height: 110px
    cursor: pointer

</style>

<style scoped>
.main-loading-bar >>> .v-snack {
    justify-content: end;

}

.main-loading-bar >>> .v-snack__content {
    padding-right: 0px !important;
}

.main-loading-bar >>> .v-snack__action {
    margin-right: 16px !important;
}

</style>