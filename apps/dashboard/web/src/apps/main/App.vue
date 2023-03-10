<template>
    <v-app>
        <router-view></router-view>
        <v-snackbar
                :timeout="100000"
                app
                top
                centered
                :color="snackbar.color"
                v-model="snackbar.show"
        >
            {{ snackbar.text }}
            <a v-if="snackbar.url" :href="snackbar.url"> Follow Here </a>
            <template v-slot:action="{ attrs }">
                <v-btn icon v-bind="attrs" @click="snackbar.show = false">
                    <v-icon>$fas_times</v-icon>
                </v-btn>
            </template>
        </v-snackbar>
    </v-app>
</template>

<script>
    export default {
        name: 'App',
        data () {
            return {
                snackbar: {
                    show: false,
                    text: '',
                    color: ''
                }
            }
        },
        methods : {
          isPublicRoute() {
            var route = this.$route.fullPath
            return route === '/' || route === '/login' || route === '/signup'
          }
        },
        async mounted () {
            if (typeof window !== undefined && window._AKTO === undefined) {
                window._AKTO = this
            }
        },
        created() {
            this.$on('SHOW_SNACKBAR', (e) => {
                this.snackbar = {
                    show: true,
                    text: e.text,
                    color: e.color,
                    url: e.url
                }
            })
            this.$on('AUTH_FAILED', () => {
                this.snackbar = {
                    show: true,
                    text: 'Auth Failed',
                    color: 'error'
                }
                this.$router.push({
                    path: '/',
                    query: {
                        redirect: this.$route.path
                    }
                })
            })
            this.$on('SERVER_ERROR', () => {
                this.snackbar = {
                    show: true,
                    text: 'Server Error',
                    color: 'error'
                }
            }),
            this.$on('HIDE_SNACKBAR', () => {
              this.snackbar = {
                show: false
              }
          })
        }
    }
</script>

<style>
    @import url('https://fonts.googleapis.com/css?family=Poppins:400,500,600&display=swap');
    .v-application {
        font-family: Poppins, sans-serif !important;
        color: #2d2434;
        letter-spacing: unset !important;
    }

    .clickable {
        cursor: pointer;
    }
    
    .clickable-line {
        cursor: pointer;
    }

    .clickable-line:hover {
        text-decoration: underline;
        color: var(--v-themeColor-base);
    }

    .checkbox-primary {
        accent-color: var(--v-themeColor-base);
    }

    .clickable-bg {
        cursor: pointer;
    }

    .clickable-bg:hover {
        cursor: pointer;
        background-color: #47466A0D;
    }

</style>

<style lang="sass">
.v-application a
    color: unset !important
    text-decoration: auto

.v-application .primary--text
    color: #6200EA !important

.active-tab
    color: #2d2434
    font-weight: 500

.tabs-container
    padding-left: 16px

.right-pane-tab
    text-transform: unset
    letter-spacing: normal
    padding: 0

.brda
  border: 1px solid rgba(71, 70, 106, 0.2) !important

.brdb
  border-bottom: 1px solid rgba(71, 70, 106, 0.2) !important

.brdt
  border-top: 1px solid rgba(71, 70, 106, 0.2) !important

.brdl
  border-left: 1px solid rgba(71, 70, 106, 0.2) !important

.brdr
  border-right: 1px solid rgba(71, 70, 106, 0.2) !important

.highcharts-credits
  display: none

.v-tooltip__content
  font-size: 10px !important
  opacity: 1 !important
  background-color: #7e7e97
  border-radius: 2px
  transition: all 0s !important

.v-icon
  font-size: 16px !important
  width: 24px !important

.v-tooltip__content:after
  border-left: solid transparent 4px
  border-right: solid transparent 4px
  border-bottom: solid #7e7e97 4px
  top: -4px
  content: " "
  height: 0
  left: 50%
  margin-left: -5px
  position: absolute
  width: 0

.v-btn
  cursor: pointer !important
  text-transform: unset !important
  letter-spacing: normal

.coming-soon
    height: 271px
    margin-top: auto
    margin-bottom: auto
    color: #47466A3D
    font-size: 13px

.jc-end
    justify-content: end    

.jc-sb
    justify-content: space-between    

.jc-sa
    justify-content: space-around

.ma-auto
    margin: auto

.fd-column
    flex-direction: column
    display: flex

.no-border
    border: unset !important

.float-right
    float: right

.float-left
    float: right

.grey-text
    color: #949597

.white-background
    background-color: #FFFFFF   
     
.fs-12
    font-size: 12px

.fs-14
    font-size: 14px

.text-primary    
    color: #47466A

.fw-500   
    font-weight: 500
</style>