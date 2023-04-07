<template>
    <div style="height:120px ;width: 482px">
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
        }
    },
    mounted() {
        if (this.demoCollections || this.demoCollections.size > 0) {
            this.$store.dispatch('onboarding/collectionSelected', this.demoCollections[0])
        }
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
.api-collection-label
    font-weight: 600
    font-size: 14px
    color: var(--themeColorDark)

.spinner-div
    display: flex
    justify-content: center
</style>