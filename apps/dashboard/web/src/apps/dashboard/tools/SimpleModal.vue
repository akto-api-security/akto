<template>
    <v-dialog :width="width" v-model="dialog" persistent>
        <v-card>
            <v-card-title class="modal-title">
                {{ title }}
            </v-card-title>
            <v-card-text class="modal-body">{{ body }}</v-card-text>
            <v-card-actions>
                <v-btn color="var(--white)" :class='["elevation-0"]' @click=" dialog = false">
                    Cancel
                </v-btn>
                <v-btn primary dark depressed color="var(--themeColor)" class="var(--white)--text" @click="dialog = false"
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
        width: { default: "450px" }
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
</style>