<template>
    <div class="ma-4" style="padding-top: 12px">
        <v-btn block color="#47466A" height="48px" @click="next">
            <v-icon left color="white" v-if="prepend">
                {{ icon }}
            </v-icon>
            <span style="color: white"> {{ text }} </span>
            <v-icon right color="white" v-if="!prepend">
                {{ icon }}
            </v-icon>
        </v-btn>
    </div>
</template>


<script>
import obj from "@/util/obj";

export default {
    name: "NextButton",
    data () {
        return {
        }
    },
    props: {
        text: obj.strR,
        icon: obj.strR,
        prepend: obj.boolR
    },
    methods: {
        next() {
            this.$emit("next")
        }
    }
}
</script>

<style lang="sass">


</style>