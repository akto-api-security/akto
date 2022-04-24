<template>
    <div>
        <v-row class="pa-4">
            <v-col md="3" class="data-types-container d-flex">
                <a-card
                    title="Data Types" 
                    color="rgba(33, 150, 243)"
                    style="min-height: 600px"
                    icon_right="$fas_plus"
                    icon_right_color="#6200EA"
                    @icon_right_clicked="createNewDataType"
                >
                    <div v-for="data_type in data_types" :key="data_type.name">
                        <data-type-card :item="data_type" :actions="data_type_card_actions"/>
                    </div>
                </a-card>
            </v-col>
            <v-col md="9" class="details-container d-flex" >
                <a-card title="Details" color="rgba(33, 150, 243)" style="min-height: 600px">
                    <data-type-details :data_type="data_type"/>
                </a-card>
            </v-col>
        </v-row>
    </div>
</template>

<script>
import ACard from "@/apps/dashboard/shared/components/ACard"
import DataTypeCard from "./components/DataTypeCard.vue"
import DataTypeDetails from './components/DataTypeDetails.vue'
import {mapState} from 'vuex'
export default {
    name: "DataTypes",
    components: {
        ACard,
        DataTypeCard,
        DataTypeDetails
    },
    data() {
        return {
            data_type_card_actions: [
                {
                    isValid: item => item.id,
                    icon: item => item.active? '$fas_trash' : '$fas_check',
                    text: item => item.active? 'Deactivate' : 'Activate',
                    func: item => this.toggleActivateFieldFunc(item),
                    success: (resp, item) => this.toggleSuccessFunc(resp, item),
                    failure: (err, item) => this.toggleFailureFunc(err, item)
                }
            ],
        }
    },
    mounted() {
      this.$store.dispatch("data_types/fetchDataTypes")
    },
    methods: {
        createNewDataType() {
            this.$store.dispatch('data_types/setNewDataType')
        },
        toggleActivateFieldFunc (item) {
            return this.$store.dispatch('data_types/toggleActiveParam', item)
        },

        toggleSuccessFunc (resp, item) {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `${item.name} `+ (item.active? 'de' : '') +`activated successfully!`,
                color: 'green'
            })
        },
        toggleFailureFunc (err, item) {
            window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: `An error occurred while `+ (item.active? 'de' : '')+`activating ${item.name}!`,
                color: 'red'
            })
        },
    },
    computed: {
        ...mapState('data_types', ['data_types', 'data_type']),
    }
}

</script>

<style lang="sass" scoped>
.main
    background-color: #47466A
.data-types-container .v-card
    margin-right: 0px
.details-container .v-card
    margin-left: 0px
</style>