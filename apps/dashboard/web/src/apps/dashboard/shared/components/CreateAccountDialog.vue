<template>
    <v-row justify="center">
        <v-dialog
            v-model="show"
            max-width="600px"
        >
            <template v-slot:activator="{ on, attrs }" v-bind='{label: this.label}'>
                <v-btn color="primary"  v-bind="attrs"  v-on="on">
                    <v-icon>$fas_times</v-icon>
                    Create {{label}}
                </v-btn>
            </template>
            <v-card>
                <v-card-title>Create new {{this.label}}</v-card-title>
                <v-card-text>
                        <v-textarea
                            label="Enter name"
                            v-model="newName"
                            required
                            rows="1"
                            autofocus
                            no-resize
                        >
                        </v-textarea>
                    <v-spacer/>
                    <v-card-actions>
                            <v-spacer></v-spacer>
                            <v-btn
                                    color="blue darken-1"
                                    text
                                    @click="show = false"
                            >
                                Close
                            </v-btn>
                            <v-btn
                                    color="blue darken-1"
                                    text
                                    @click="createNew"
                                    :loading="loading"
                            >
                                Save
                            </v-btn>
                        </v-card-actions>
                </v-card-text>
            </v-card>
        </v-dialog>
    </v-row>
</template>

<script>

    import obj from '@/util/obj'

    export default {
        name: "CreateAccountDialog",
        props: {
            label: obj.strR
        },
        data () {
            return {
                show: false,
                loading: false,
                newName: ''
            }
        },
        methods: {
            createNew () {
                this.$emit('createNew', this.newName)
            }
        }
    }
</script>

<style scoped>

</style>