<template>
    <div>
        <template
            v-for="(action, index) in actions" 
        >
            <v-btn plain 
                :key="index"        
                @click="actionClickedFunc(action)"
                v-if="action.isValid(subject)"
            >

                <v-tooltip bottom>
                    <template v-slot:activator='{ on, attrs }'>
                        <v-icon 
                            size=16 
                            class="tray-button"
                            v-bind="attrs"
                            v-on="on"
                        >
                            {{action.icon(subject)}}
                        </v-icon>
                    </template>
                    <span>{{action.text(subject)}}</span>
                </v-tooltip>

                
            </v-btn>
        </template>
    </div>
</template>

<script>

import obj from '@/util/obj'

export default {
    name: "ActionsTray",
    props: {
        actions: obj.arrR,
        subject: obj.objR
    },
    methods: {
        actionClickedFunc (action) {
            let _subject = this.subject
            action.func(this.subject).then((resp) => {
                action.success(resp, _subject)
            }).catch((e) => {
                action.failure(e, _subject)
            })
        }
    }    
}
</script>

<style lang="sass" scoped>
.tray-button   
    color: var(--themeColor) !important
</style>