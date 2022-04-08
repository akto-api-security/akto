<template>
    <div>
        <v-row class="pa-4">
            <v-col md="3" class="data-types-container d-flex">
                <a-card title="Data Types" color="rgba(33, 150, 243)" style="min-height: 600px">
                    <div v-for="data_type in prettyDataTypes" :key="data_type.name">
                        <data-type-card :item="data_type" @rowClicked="openDetails"/>
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
            data_type:null,
        }
    },
    mounted() {
        this.$store.dispatch("data_types/fetchDataTypes").then((resp) => {
            if (this.data_types.length > 0) {
                console.log(this.data_types);
                this.data_type = this.data_types[0]
            }
        })
    },
    methods: {
        openDetails(item) {
            this.data_type = item
        }
    },
    computed: {
        ...mapState('data_types', ['data_types']),
        prettyDataTypes() {
            if (this.data_types) {
                console.log(this.data_types);
                this.data_types.map((x) => {
                    x["color"] = x["sensitiveAlways"] || x["sensitivePosition"].length > 0 ? "red": "rgba(71, 70, 106, 0.03)"
                    x["prefix"] = x["id"] ? "[custom]" : ""
                })
                return this.data_types
            }
        }
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