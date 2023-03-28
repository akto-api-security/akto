<template>
    <v-snackbar
        :timeout="-1"
        app
        color="white"
        v-model="loading_bar.show"
        :style="{padding: `0px 16px ${index * 150 + 80}px 0px` }"
        rounded="lg"
    >
        <div class="notification">
            <div style="display: flex;">
                <div>
                    <img src="/public/notif_icon.svg"/>
                </div>
                <div style="padding: 0px 12px 0px 20px; ">
                    <div style="display: flex; justify-content: space-between;margin-bottom: 4px;">
                        <div style="font-weight: 500;font-size: 14px; color: #47466A"> {{ title }}</div>
                    </div>
                    <div style="font-weight: 400;font-size: 14px; color: #47466A">{{ subTitle }} </div>
                    <div style="padding: 16px 0px 0px 0px">
                        <v-progress-linear :value="percentage" height="8" color="#6200EA" rounded></v-progress-linear>
                    </div>
                    <div style="font-weight: 500; font-size: 14px; color: #344054; padding: 8px 0px 0px 0px; display: flex; justify-content: end;">
                        <div>{{ percentage }}% uploaded</div>
                    </div>
                </div>
            </div>
        </div>

        <template v-slot:action="{ attrs }">
            <div style=" height: 110px; cursor: pointer" @click="close">
                <v-icon color="#47466A">$fas_times</v-icon>
            </div>
        </template>

    </v-snackbar>
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
            loading_bar: {
                show: true,
                text: '',
                color: 'green'
            }
        }
    },
    methods: {
        close() {
            this.$emit("close")
        }
    }

}
</script>


<style lang="sass">
.notification
    width: 360px
    height: 110px
    padding: 4px 0px 0px 0px

</style>

<style>
.v-snack {
    justify-content: end;

}

.v-snack__content {
    padding-right: 0px !important;
}

.v-snack__action {
    margin-right: 16px !important;
}

</style>