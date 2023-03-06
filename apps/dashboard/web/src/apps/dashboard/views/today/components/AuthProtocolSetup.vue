<template>
    <div class="overflow-scroll">
        <div class="dialog-title">Authentication details</div>
        <div v-for="([name, protocol]) in Object.entries(authProtocol)" :key="name">
            <div class="auth-setting">
                <div class="heading">{{name}} <span class="protocol-type">({{protocol.type.toLowerCase()}})</span>
                    <template v-if="protocol.type.toLowerCase() === 'basic'">
                        <v-text-field 
                            class="form-field-text"
                            label="Username"
                            v-model="authProtocolsSettings[name].username"
                        ></v-text-field>
                        <v-text-field 
                            class="form-field-text"
                            label="Password"
                            v-model="authProtocolsSettings[name].password"
                        ></v-text-field>
                    </template>
                    <template v-if="protocol.type.toLowerCase() === 'oauth2'">
                        <v-text-field 
                            class="form-field-text"
                            label="Token"
                            v-model="authProtocolsSettings[name].token"
                        ></v-text-field>

                    </template>
                    <template v-if="protocol.type.toLowerCase() === 'apikey'">
                        <v-text-field 
                            class="form-field-text"
                            label="API Key"
                            v-model="authProtocolsSettings[name].apiKey"
                        ></v-text-field>
                    </template>
                </div>
            </div>
        </div>

        <div>
            <v-btn color="var(--themeColor)" @click="completed" :disabled="!allFieldsFilled" style="float: right">
                <span class="info-text">{{btnText}}</span>
            </v-btn>
        </div>
    </div>
</template>

<script>

    import obj from "@/util/obj"
import { mapState } from 'vuex'

    export default {
        name: "AuthProtocolSetup",
        props: {
            btnText: obj.strR
        },
        data () {
            let authProtocolsSettings = {}
            Object.entries(this.$store.state.today.authProtocol).forEach(([x, y]) => {
                authProtocolsSettings[x] = {}
                switch(y.type.toLowerCase()) {
                    case "basic":
                        authProtocolsSettings[x].username = ''
                        authProtocolsSettings[x].password = ''
                        break;
                    case "oauth2": 
                        authProtocolsSettings[x].token = ''
                        break;
                    case "apikey":
                        authProtocolsSettings[x].apiKey = ''
                        break;
                }
            })
            return {
                authProtocolsSettings: authProtocolsSettings
            }
        },
        methods: {
            completed () {
                this.$emit('completed', this.authProtocolsSettings)
            },
            checkMissingFields () {
                let ret = Object.values(authProtocol).every(([x,y]) => {
                    let fields = authProtocolsSettings[x]
                    switch(y.type.toLowerCase()) {
                        case "basic":
                            return fields.username && fields.password
                        case "oauth2": 
                            return fields.token
                        case "apikey":
                            return fields.apiKey    
                    }
                })

                return ret
            }
        },
        computed: {
            ...mapState('today', ['authProtocol']),
            allFieldsFilled: {
                get () {
                    return Object.values(this.authProtocolsSettings).every(x => Object.values(x).every(x => x && x.length > 0))
                }
            }            
        }
    }
</script>

<style scoped lang="sass">
.info-text
    color: var(--white)
    
.dialog-title
    font-size: 16px
    font-weight: 600
    color: var(--themeColorDark)
    margin-bottom: 16px

.heading
    font-size: 14px
    font-weight: 500
    color: var(--themeColorDark)

.protocol-type
    font-size: 14px
    font-weight: 500   
    color: var(--themeColorDark)
    opacity: 0.5

.form-field-text
    padding-top: 8px !important
    margin-top: 0px !important
    margin-left: 20px
</style>

<style scoped>
.form-field-text >>> .v-label {
  font-size: 12px;
  color: var(--themeColor);
  font-weight: 400;
}

.form-field-text >>> input {
  font-size: 14px;
  color: var(--themeColor);
  font-weight: 500;
}
</style>