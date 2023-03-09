<template>
    <div style="width: 550px;height: 250px; margin-bottom: 36px;" class="pa-3">
        <div style="font-weight: 500;font-size: 20px; color: #47466A">Attacker Token</div>
        <div style="display: flex; justify-content: center; height: 100%; align-items: center;" v-if="authMechanismLoading">
            <spinner :size="50"/>
        </div>
        <div v-else>
            <div>
                <div style="display: flex; align-items: center; margin-top: 20px;font-weight: 400; font-size: 14px; color: #47466A">
                    <div>Auth header key</div>
                    <hint-icon value="Attacker token header key"/>
                </div>
                <div style="margin-top: 4px">
                    <v-text-field
                        placeholder=""
                        type="text"
                        required
                        dense
                        outlined
                        class="auth-text-field"
                        background-color="#F9F9FA"
                        v-model="authKey"
                    />
                </div>
            </div>
            <div>
                <div style="display: flex; align-items: center;font-weight: 400; font-size: 14px; color: #47466A">
                    <div>Auth header value</div>
                    <hint-icon value="Attacker token value"/>
                </div>
                <div style="margin-top: 4px">
                    <v-textarea
                        placeholder=""
                        type="text"
                        required
                        dense
                        multi
                        outlined
                        class="auth-text-field"
                        rows="3"
                        background-color="#F9F9FA"
                        v-model="authValue"
                    />
                </div>
            </div>
        </div>
    </div>
</template>

<script>
import {mapState} from 'vuex'
import Spinner from '@/apps/dashboard/shared/components/Spinner'
import HintIcon from "./HintIcon";

export default {
    name: "SetConfig",
    components: {
        Spinner,
        HintIcon
    },
    data () {
        return {
        }
    },
    mounted() {
    },
    computed: {
        ...mapState('onboarding', ['authMechanismLoading']),
        authKey:{
            get() {
                return this.$store.state.onboarding.authKey
            },
            set(v) {
                this.$store.state.onboarding.authKey = v
            }
        },
        authValue:{
            get() {
                return this.$store.state.onboarding.authValue
            },
            set(v) {
                this.$store.state.onboarding.authValue = v
            }
        }
    },
}


</script>

<style lang="sass" style="scoped">

.auth-text-field.v-input .v-input__slot 
    border-radius: 8px

.auth-text-field.v-text-field--outlined fieldset 
    color: #D0D5DD !important





</style>