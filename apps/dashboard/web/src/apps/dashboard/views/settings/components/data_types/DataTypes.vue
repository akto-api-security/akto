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
                        <data-type-card :item="data_type"/>
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
        }
    },
    mounted() {
        this.$store.dispatch("data_types/fetchDataTypes").then((resp) => {
            if (this.data_types.length > 0) {
                this.data_type = this.data_types[0]
            }
        })
    },
    methods: {
        createNewDataType() {
            this.$store.state.data_types.data_type = {
                "name": "",
                "operator": "AND",
                "keyConditions": {"operator": "AND", "predicates": []},
                "sensitiveAlways": true,
                "valueConditions": {"operator": "AND", "predicates": []},
                "active": true,
                "sensitivePosition": [],
                "createNew": true
            }
        }
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