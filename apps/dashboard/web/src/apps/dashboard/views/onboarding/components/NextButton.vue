<template>
    <div class="ma-4" style="padding-top: 12px">
        <v-btn block color="var(--themeColorDark)" height="48px" @click="next" :disabled="disabled">
            <div v-if="loading"><spinner :size="30" color="white"/></div>
            <div v-else>
                <v-icon left color="white" v-if="prepend">
                    {{ icon }}
                </v-icon>
                <span style="color: white"> {{ text }} </span>
                <v-icon right color="white" v-if="!prepend">
                    {{ icon }}
                </v-icon>
            </div>
        </v-btn>
    </div>
</template>


<script>
import obj from "@/util/obj";
import Spinner from '@/apps/dashboard/shared/components/Spinner'

export default {
    name: "NextButton",
    components: { 
        Spinner, 
    },
    data () {
        return {
        }
    },
    props: {
        text: obj.strR,
        icon: obj.strR,
        prepend: obj.boolR,
        disabled: obj.boolR,
        loading: obj.boolR
    },
    methods: {
        next() {
            if (!this.loading) {
                this.$emit("next")
            }
        }
    }
}
</script>

<style lang="sass">


</style>