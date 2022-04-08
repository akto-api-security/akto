<template>
    <div style="padding: 24px; height: 100%" v-if="data_type_copy">
        <v-container>
            <v-row style="height: 50px">
                <v-col cols="2">
                    <span style="color:grey; font-size: 16px; font-weight: bold">Name</span>
                </v-col>
                <v-col cols="9">
                    <v-text-field
                        height="20px"
                        placeholder="Add data type name"
                        flat solo class="ma-0 pa-0" hide-details
                        v-model="data_type_copy.name"
                    ></v-text-field>
                </v-col>
            </v-row>

            <div v-if="data_type_copy.id">
                <v-row style="padding: 12px"  >
                    <conditions-table
                    :conditions="data_type_copy.keyConditions"
                    initial_string="param_name"
                    table_header="Key conditions"
                    />
                </v-row>
                <v-row
                    style="padding: 36px 12px 36px 12px"
                    v-if="showMainOperator" 
                >
                    <div> AND </div>
                </v-row>

                <v-row style="padding: 12px"  >
                    <conditions-table
                    :conditions="data_type_copy.valueConditions"
                    initial_string="param_value"
                    table_header="Value conditions"
                    />
                </v-row>
            </div>
            <v-row style="height: 50px">
                <v-col cols="2">
                    <span style="color:grey; font-size: 16px; font-weight: bold">Sensitive</span>
                </v-col>
                <v-col 
                    cols="9"
                    :class="data_type_copy.sensitiveAlways? 'sensitive-text true' : 'sensitive-text'"
                    @click="toggleSensitive"
                >
                    {{computeSensitiveValue}}
                </v-col>
            </v-row>
        </v-container>
    </div>
</template>


<script>
import obj from "@/util/obj"
import ConditionsTable from './ConditionsTable.vue'
export default {
    name: "DataTypeDetails",
    props: {
        data_type: obj.ObjR
    },
    components: {
        ConditionsTable
    },
    data() {
        return {
            data_type_copy: JSON.parse(JSON.stringify(this.data_type))
        }
    },
    methods: {
        toggleSensitive() {
            this.data_type_copy.sensitiveAlways = !this.data_type_copy.sensitiveAlways
        }
    },
    mounted() {
    },
    computed: {
        computeSensitiveValue() {
            if (this.data_type_copy) {
                return this.data_type_copy.sensitiveAlways ? "Yes" : "No"
            }
        },
        showMainOperator() {
            return this.data_type_copy.keyConditions && this.data_type_copy.keyConditions.predicates &&
            this.data_type_copy.keyConditions.predicates.length > 0 && 
            this.data_type_copy.valueConditions && this.data_type_copy.valueConditions.predicates &&
            this.data_type_copy.valueConditions.predicates.length > 0 
        }
    },
    watch: {
        data_type: function(newVal, oldVal) {
            this.data_type_copy = JSON.parse(JSON.stringify(newVal))
        }
    }
}

</script>

<style lang="sass" scoped>
    .sensitive-text
        &.true
            color: red
        &:hover
            cursor: pointer




</style>