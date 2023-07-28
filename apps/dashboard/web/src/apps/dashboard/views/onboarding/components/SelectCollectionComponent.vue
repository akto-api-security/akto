<template>
    <div class="select-component-main">
        <div class="ma-4 api-collection-label">
            API COLLECTION
        </div>
        <v-menu offset-y v-if="selectedCollection">
            <template v-slot:activator="{ on, attrs }">
                <div class="ma-4">
                    <v-card class="pa-3 collection-prompt" plain :outlined="true" v-bind="attrs" v-on="on">
                        <div class="collection-name">
                            <v-icon left>$fas_globe</v-icon>
                            {{ selectedCollection.displayName }}
                        </div>
                    </v-card>
                </div>
            </template>
            <v-list>
                <v-list-item v-for="(item, index) in this.demoCollections" :key="index" @click="collectionSelected(item)">
                    <v-list-item-title class="options">{{ item.displayName}}</v-list-item-title>
                </v-list-item>
            </v-list>
        </v-menu>
        <div class="spinner-div" v-else>
            <spinner :size="50"/>
        </div>
    </div>
</template>

<script>
import {mapState} from 'vuex'
import Spinner from '@/apps/dashboard/shared/components/Spinner'

export default {
    name: "SelectCollectionComponent",
    components: { 
        Spinner
    },
    data () {
        return {
            timer : null
        }
    },
    mounted() {
        
        this.timer = setInterval(async () => {
            if (this.apiCollections.length <=1 ){
                await this.$store.dispatch('collections/loadAllApiCollections')
            }
            if (this.demoCollections.length > 0) {
                this.$store.dispatch('onboarding/collectionSelected', this.demoCollections[0])
                clearInterval(this.timer)
            }
        }, 1000)        
    },

    methods: {
        collectionSelected(item) {
            this.$store.dispatch('onboarding/collectionSelected', item)
        }
    },

    computed: {
        ...mapState('collections', ['apiCollections']),
        ...mapState('onboarding', ['selectedCollection']),
        demoCollections() {
            return this.apiCollections.filter((x) => x.displayName.toLowerCase() !== "default")
        }
    }
}

</script>

<style lang="sass">
.select-component-main
    height:120px
    width: 482px

.api-collection-label
    font-weight: 600
    font-size: 14px
    color: var(--themeColorDark)
.spinner-div
    display: flex
    justify-content: center
.page-login__card
    max-width: 482px
    margin: 0 auto
    border-radius: 12px !important
    border: 0px
    position: relative
    box-shadow: unset
    height: 240px
.collection-prompt
    height: 72px
    border: 1px solid #DADAE1
    font-weight: 600
    font-size: 14px
    color: #344054
    border: 2px
    border-radius: 12px !important
.collection-name
    font-weight: 500
    color: #344054
    font-size: 16px
    height: 72px
    line-height: 44px
    padding-left: 16px
</style>