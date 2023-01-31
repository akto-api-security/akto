<template>
    <div class='operator-block' style="height: 40px; line-height: 47px">
        <div class="text-center">
            <v-menu offset-y>
                <template v-slot:activator="{ on, attrs }">
                    <span
                        v-if="operator"
                        v-bind="attrs"
                        v-on="on"
                        style="color: grey"
                        >
                        {{operator}}
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
                <v-list v-if="(onlyEqual==false)">
                    <v-list-item 
                        v-for="(item, index) in operators" 
                        :key="index"
                        @click="$emit('operatorChanged', item)"
                    >
                        <v-list-item-title>{{ item }}</v-list-item-title>
                    </v-list-item>
                </v-list>
            </v-menu>
        </div>
    </div>
</template>


<script>
import obj from "@/util/obj"

export default {
    name: "OperatorComponent",
    props: {
        operator: obj.strR,
        operators: obj.arrR,
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
        getValueStyleOperator(value) {
            let width = !value  ? 6 : value.length + 4
            return { 'width': width+ 'ch', "color": "grey" }
        },
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

.operator-block
    display: inline-block
    color: grey

</style>

