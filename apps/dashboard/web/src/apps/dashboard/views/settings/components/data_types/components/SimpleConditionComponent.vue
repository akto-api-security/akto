<template>
    <div class='condition-block' >
        <span class="inline-block-child" style="color: #6200EA">
            {{initial_string}}
        </span>
        <div class="inline-block-child" style="padding-left: 20px">
            <div class="text-center">
                <v-menu offset-y>
                    <template v-slot:activator="{ on, attrs }">
                        <span
                            v-if="condition.type"
                            v-bind="attrs"
                            v-on="on"
                            style="text-decoration: underline"
                            >
                            {{formatConditionType(condition.type)}}
                        </span>
                        <span
                            v-else
                            v-bind="attrs"
                            v-on="on"
                            style="text-decoration: italic; color: grey"
                            >
                                click
                        </span>
                    </template>
                    <v-list>
                        <v-list-item 
                            v-for="(item, index) in operation_types" 
                            :key="index"
                            @click="condition.type = item.value"
                        >
                            <v-list-item-title>{{ item.text}}</v-list-item-title>
                        </v-list-item>
                    </v-list>
                </v-menu>
            </div>
        </div>
        <div 
            class="inline-block-child"
            style="padding-left: 20px"
            v-if="['REGEX', 'STARTS_WITH', 'ENDS_WITH'].includes(condition.type)"
        >
            <v-text-field
                height="15px"
                placeholder="value"
                flat
                :style="getValueStyle(condition.value)"
                v-model="condition.value"
                class="value_predicate"
                :rules="[value => !!value || 'Required']"
            />
        </div>
    </div>
</template>

<script>
import obj from "@/util/obj"

export default {
    name: "SimpleConditionComponent",
    props: {
        condition: obj.objR,
        initial_string: obj.strR
    },
    components: {
    },
    data() {
        return {
            operators: [
                "OR", "AND"
            ],
            operation_types: [
                {
                    "text": "starts with",
                    "value": "STARTS_WITH"
                },
                {
                    "text": "ends with",
                    "value": "ENDS_WITH"
                },
                {
                    "text": "matches regex",
                    "value": "REGEX"
                },
                {
                    "text": "is number",
                    "value": "IS_NUMBER"
                }

            ]
        }
    },
    methods: {
        getValueStyle(value) {
            let width = !value  ? 6 : value.length + 4
            return { 'width': width+ 'ch' }
        },
        getValueStyleOperator(value) {
            let width = !value  ? 6 : value.length + 4
            return { 'width': width+ 'ch', "color": "grey" }
        },
        formatConditionType(value) {
            let finalValue = null
            for (let i=0; i < this.operation_types.length; i++) {
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
    watch : {

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
    .v-text-field .v-input__control .v-input__slot {
        min-height: auto !important;
        display: flex !important;
        align-items: center !important;
        background-color: transparent;
    }
    .v-input__slot::before {
        border-style: none !important;
    }
    .value_predicate .v-text-field__slot input {
        color: #00f !important;
    }

</style>