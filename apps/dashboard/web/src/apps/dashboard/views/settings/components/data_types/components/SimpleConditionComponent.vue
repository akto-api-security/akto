<template>
    <div class='condition-block'>
        <span class="inline-block-child" style="color: #6200EA">
            {{ initial_string }}
        </span>
        <div class="inline-block-child" style="padding-left: 20px">
            <div class="text-center">
                <v-menu offset-y>
                    <template v-slot:activator="{ on, attrs }">
                        <span v-if="condition.type" v-bind="attrs" v-on="on" style="text-decoration: underline">
                            {{ formatConditionType(condition.type) }}
                        </span>
                        <span v-else v-bind="attrs" v-on="on" style="text-decoration: italic; color: grey">
                            click
                        </span>
                    </template>
                    <v-list v-if="(onlyEqual==false)">
                        <v-list-item 
                            v-for="(item, index) in operation_types" 
                            :key="index"
                            @click="$emit('conditionTypeChanged', item.value)"
                        >
                            <v-list-item-title>{{ item.text}}</v-list-item-title>
                        </v-list-item>
                    </v-list>
                </v-menu>
            </div>
        </div>
        <div class="inline-block-child" style="padding-left: 20px"
            v-if="requireTextInputForTypeArray.includes(condition.type)">
            <v-text-field height="15px" placeholder="value" flat :style="getValueStyle(condition.value)"
                v-model="condition.value" class="value_predicate" :rules="[value => !!value || 'Required']" />
        </div>
        <div class="inline-block-child" style="padding-left: 20px"
            v-else-if="requireMapInputForTypeArray && requireMapInputForTypeArray.includes(condition.type)">
            <v-menu offset-y>
                <template v-slot:activator="{ on, attrs }">
                    <v-btn class="filter-button" v-bind="attrs" v-on="on" primary>
                        <span>{{ selectedStatusName }}</span>
                        <v-icon>$fas_angle-down</v-icon>
                    </v-btn>
                </template>
                <filter-list :title="selectedStatusName" :items="statusItems" @clickedItem="clickedStatusItem($event)"
                    hideOperators hideListTitle selectExactlyOne />
            </v-menu>
        </div>
    </div>
</template>

<script>
import obj from "@/util/obj"

export default {
    name: "SimpleConditionComponent",
    props: {
        condition: obj.objR,
        initial_string: obj.strR,
        operators: obj.arrR,
        operation_types: obj.arrR,
        requireTextInputForTypeArray: obj.arrR,
        requireMapInputForTypeArray: obj.arrN
        onlyEqual: {
            type:Boolean
        },
    },
    components: {
    },
    data() {
        return {

        }
    },
    methods: {
        getValueStyle(value) {
            let width = !value ? 6 : value.length + 4
            return { 'width': width + 'ch' }
        },
        getValueStyleOperator(value) {
            let width = !value ? 6 : value.length + 4
            return { 'width': width + 'ch', "color": "grey" }
        },
        formatConditionType(value) {
            let finalValue = null
            for (let i = 0; i < this.operation_types.length; i++) {
                let operation = this.operation_types[i]
                if (operation["value"] === value) {
                    finalValue = operation["text"]
                    break
                }
            }

            return finalValue;
        }
    },
    created() {

    },
    computed: {

    },
    watch: {

    }

}
</script>

<style lang="sass" scoped>

.condition-block
    background: #edecf0 
    width: fit-content
    padding: 0px 10px 10px 10px
    height: 40px
    line-height: 47px

.inline-block-child 
  display: inline-block


</style>

<style >
.filter-button {
    box-sizing: border-box;

    width: fit-content;
    height: 40px;

    background: #FFFFFF;
    border: 1px solid #D0D5DD;

    font-weight: 500;
    font-size: 14px;

    box-shadow: 0px 1px 2px rgba(16, 24, 40, 0.05);
    border-radius: 4px;
}

.inline-block-child .v-text-field .v-input__control .v-input__slot {
    min-height: auto !important;
    display: flex !important;
    align-items: center !important;
    background-color: transparent;
}

.inline-block-child .v-input__slot::before {
    border-style: none !important;
}

.inline-block-child .value_predicate .v-text-field__slot input {
    color: #00f !important;
}
</style>