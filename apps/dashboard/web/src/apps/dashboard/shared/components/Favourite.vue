<template>
    <v-tooltip bottom>
        <template v-slot:activator="{ on: tooltip, attrs }">
            <v-icon
                    v-if="!value"
                    class="star-favourite"
                    v-bind="attrs"
                    v-on="tooltip"
                    @click="toggleFavourite"
            >
                $far_star
            </v-icon>
            <v-icon
                    v-else
                    class="star-favourite"
                    color="#f88f00"
                    v-bind="attrs"
                    v-on="tooltip"
                    @click="toggleFavourite"
            >
                $fas_star
            </v-icon>
        </template>
        <span>{{value ? 'Unmark': 'Mark'}} favourite</span>
    </v-tooltip>
</template>

<script>
    import obj from "@/util/obj";
    import api from "../api";

    export default {
        name: "Favourite",
        props: {
            value: obj.boolR,
            id: obj.numR,
            type: obj.strR
        },
        methods: {
            toggleFavourite () {
              this.$emit('input', !this.value)
              api.toggleFavourite(!this.value, this.id, this.type)
            }
        }
    }
</script>

<style scoped lang="sass">
.star-favourite
    cursor: pointer
</style>