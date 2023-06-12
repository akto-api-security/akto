<template>
    <v-dialog :width="width" v-model="dialog" persistent>
        <v-card>
            <v-card-title class="modal-title">
                {{ title }}
            </v-card-title>
            <v-card-text  class="modal-body">Request</v-card-text>
            <v-textarea solo no-resize rows="6" label="Ctrl-V to paste" v-model="requestValue"></v-textarea>
            <v-card-text class="modal-body">Response</v-card-text>
            <v-textarea solo no-resize rows="6" label="Ctrl-V to paste" v-model="responseValue"></v-textarea>
            <v-card-actions>
                <v-btn color="var(--white)" :class='["elevation-0"]' @click=" dialog = false">
                    Cancel
                </v-btn>
                <v-btn primary dark depressed color="var(--themeColor)" class="var(--white)--text" @click="saveTextValues()"
                    >
                    Save
                </v-btn>
            </v-card-actions>
        </v-card>
    </v-dialog>
</template>

<script>

export default {
    name: "CustomSampleDataApiModal",
    props: {
        title: "",
        parentDialog: {default: false},
        width: {default : '600px'}
    },
    data() {
        return {
            requestValue: "",
            responseValue: ""
        }
    },
    methods: {
        saveTextValues() {
            this.$emit("setSampleDataApi",{requestValue: this.requestValue, responseValue: this.responseValue})
            this.dialog = false
        }
    },
    computed: {
        reqVal() {
            this.requestValue
        },
        dialog: {
            get() {
                return this.parentDialog
            },
            set(value) {
                this.$emit('closeCustomSampleDataDialog')
            }
        }
    }

}

</script>

<style scoped>
</style>