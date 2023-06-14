<template>
    <v-dialog :width="width" v-model="dialog" persistent>
        <v-card>
            <v-card-title class="modal-title">
                {{ title }}
            </v-card-title>
            <v-card-text class="modal-body">{{ body }}</v-card-text>
            <v-card-actions class="button-class">
                <v-btn width="45%" class="button-border-class" :class='["elevation-0"]' @click=" dialog = false">
                    Cancel
                </v-btn>
                <v-btn primary dark depressed width="45%" color="var(--themeColor)" class="white-color button-border-class" @click="dialog = false"
                    href="https://app.akto.io/login" target="_blank">
                    Sign-up now
                </v-btn>
            </v-card-actions>
        </v-card>
    </v-dialog>
</template>

<script>


export default {
    name: "SimpleModal",
    props: {
        title: "",
        body: "",
        parentDialog: { default: false },
        width: { default: "410px" }
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
    font-weight: 600;
    font-size: 18px;
}

.modal-body {
    font-weight: 400;
    font-size: 14px;
}

.button-class {
    display: flex;
    justify-content: space-around;
    padding-top: 32px;
    padding-bottom: 24px;

}

.button-border-class {
    border-color: var(--black);
    border-radius: 8px;
    font-size: 16px;
    font-weight: 600;
}

.white-color {
    color: var(--white) !important;
}
</style>
