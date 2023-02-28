<template>
    <div style="height:120px ;width: 482px">
        <div class="ma-4" style="font-weight: 600; font-size: 14px; color: #47466A">
            API COLLECTION
        </div>
        <v-menu offset-y>
            <template v-slot:activator="{ on, attrs }">
                <div class="ma-4">
                    <v-card class="pa-3 collection-prompt" plain :outlined="true" v-bind="attrs" v-on="on">
                        <div class="collection-name">
                            <v-icon left>$fas_globe</v-icon>
                            juiceshop.akto.io
                        </div>
                    </v-card>
                </div>
            </template>
            <v-list>
                <v-list-item v-for="(item, index) in this.collections" :key="index">
                    <v-list-item-title>{{ item.name}}</v-list-item-title>
                </v-list-item>
            </v-list>
        </v-menu>
    </div>
</template>

<script>
export default {
    name: "SelectCollectionComponent",
    data () {
        return {
            collections: [
                {
                    "name": "juiceshop.akto.io"
                },
                {
                    "name": "brokencrystals.akto.io"
                },
            ]
        }
    },
}

</script>