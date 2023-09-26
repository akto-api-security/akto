<template>
    <v-dialog :width="width" v-model="dialog">
        <v-card style="">
            <v-card-title class="modal-title">
                {{ title }}
            </v-card-title>
            <v-card-text class="modal-body">{{ body }}</v-card-text>
            <v-card-actions class="button-class">
                <v-btn width="45%" class="button-border-class" :ripple="false" :class='["elevation-0"]' @click=" dialog = false">
                    Cancel
                </v-btn>
                <v-btn primary dark depressed width="45%" :ripple="false" color="var(--themeColor)" class="white-color button-border-class" @click="dialog = false"
                    :href="redirectPath" target="_blank">
                    Sign up now
                </v-btn>
            </v-card-actions>
        </v-card>
    </v-dialog>
</template>

<script>

import obj from "@/util/obj"
export default {
    name: "SimpleModal",
    props: {
        title: "",
        body: "",
        parentDialog: { default: false },
        width: { default: "410px" },
        redirectPath: {
            type: String,
            required: false,
            default: "https://app.akto.io/dashboard/test-editor"
        }
    },
    data() {
        return {
        }
    },
    computed: {
        dialog: {
            get() {
                return this.parentDialog
            },
            set(value) {
                this.$emit('closeSimpleDialog')
            }
        }
    }
}

</script>

<style scoped>
.modal-title {
    font-weight: 600 !important;
    font-size: 18px !important;
    color: var(--themeColorDark);
}

.modal-body {
    font-weight: 400 !important;
    font-size: 14px !important;
}

.button-class {
    display: flex;
    justify-content: space-around;
    padding-bottom: 24px !important;

}

.button-border-class {
    border-color: var(--black);
    border-radius: 8px;
    font-size: 16px;
    font-weight: 600;
    color: var(--themeColorDark);
    height: 40px !important;
}

.white-color {
    color: var(--white) !important;
}
</style>
