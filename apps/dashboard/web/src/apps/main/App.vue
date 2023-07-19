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
                    color: e.color
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
        color: var(--base);
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
        background-color: var(--themeColorDark17);
    }

</style>

<style lang="sass">
.underline
    text-decoration: underline 
.v-application a
    color: unset !important
    text-decoration: auto

.v-application .primary--text
    color: var(--themeColor) !important

.active-tab
    color: var(--base)
    font-weight: 500

.tabs-container
    padding-left: 16px

.right-pane-tab
    text-transform: unset
    letter-spacing: normal
    padding: 0

.brda
  border: 1px solid var(--themeColorDark13) !important

.brdb
  border-bottom: 1px solid var(--themeColorDark13) !important

.brdt
  border-top: 1px solid var(--themeColorDark13) !important

.brdl
  border-left: 1px solid var(--themeColorDark13) !important

.brdr
  border-right: 1px solid var(--themeColorDark13) !important

.highcharts-credits
  display: none

.v-tooltip__content
  font-size: 10px !important
  opacity: 1 !important
  background-color: var(--lightGrey)
  border-radius: 2px
  transition: all 0s !important

.v-icon
  font-size: 16px !important
  width: 24px !important

.v-tooltip__content:after
  border-left: solid transparent 4px
  border-right: solid transparent 4px
  border-bottom: solid var(--lightGrey) 4px
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
    color: var(--themeColorDark12)
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
    color: var(--hexColor19)

.white-background
    background-color: var(--white)   
     
.fs-12
    font-size: 12px

.fs-14
    font-size: 14px

.text-primary    
    color: var(--themeColorDark)

.fw-500   
    font-weight: 500

.z-80
    zoom: 0.8
        
.mlp-74
    margin-left: 74% !important
            
.no-shadow
    box-shadow: none !important


.arrow-up
    width: 0
    height: 0 
    border-left: 5px solid transparent
    border-right: 5px solid transparent
    border-bottom: 5px solid var(--hexColor15)
    margin-left: 20%

.arrow-down
    width: 0
    height: 0 
    border-left: 5px solid transparent
    border-right: 5px solid transparent
    border-top: 5px solid var(--hexColor15)
    margin-left: 20%

.arrow-left
    width: 0
    height: 0 
    border-top: 5px solid transparent
    border-bottom: 5px solid transparent
    border-right: 5px solid var(--hexColor15)
    margin-top: 20%

.arrow-right
    width: 0
    height: 0 
    border-top: 5px solid transparent
    border-bottom: 5px solid transparent
    border-left: 5px solid var(--hexColor15)
    margin-top: 20%

    
</style>

<style lang="css">
    :root{
        --themeColor: #6200EA;
        --themeColor2: #6200EADF;
        --themeColor3: #6200EABF;
        --themeColor4: #6200EA9F;
        --themeColor5: #6200EA99;
        --themeColor6: #6200EA7F;
        --themeColor7: #6200EA5F;
        --themeColor8: #6200EA3F;
        --themeColor9: #6200EA3D;
        --themeColor10: #6200EA19;
        --themeColor11: #6200EA1F;

        --themeColorDark: #47466A ;
        --themeColorDark2: #47466AD9;
        --themeColorDark3: #47466ACC ;
        --themeColorDark4: #47466ABF; 
        --themeColorDark5: #47466AB3;
        --themeColorDark6: #47466AB2 ;
        --themeColorDark7: #47466A99 ;
        --themeColorDark8: #47466A8C;
        --themeColorDark9: #47466A80 ;
        --themeColorDark10: #47466A73;
        --themeColorDark11: #47466A59;
        --themeColorDark12: #47466a3D ;
        --themeColorDark13: #47466A33;
        --themeColorDark14: #47466A2F ;
        --themeColorDark15: #47466A26;
        --themeColorDark16: #47466A19 ;
        --themeColorDark17: #47466A0D ;
        --themeColorDark18: #47466A08;
        --themeColorDark19: #474667;
         
        --hexColor1: #1790FF;
        --hexColor2: #FF8717;
        --hexColor3: #FF1717;
        --hexColor4: #101828;
        --hexColor5: #12B76A;
        --hexColor6: #24292f;
        --hexColor7: #2193ef33;
        --hexColor8: #33a852;
        --hexColor9: #3366ff;
        --hexColor10: #475467;
        --hexColor11: #49cc90;
        --hexColor12: #5c04d5;
        --hexColor13: #50e3C2;
        --hexColor14: #61AFFe;
        --hexColor15: #73728D;
        --hexColor16: #7D787838;
        --hexColor17: #949494;
        --hexColor18: #9012fe;
        --hexColor19: #949597;
        --hexColor20: #d2d3d4;
        --hexColor21: #d6d3da;
        --hexColor22: #d0d5dd;
        --hexColor23: #ebf0f4;
        --hexColor24: #e8d8f8;
        --hexColor25: #e8fff4;
        --hexColor26: #f6f8fa;
        --hexColor27: #f9f9fa;
        --hexColor28: #f04438;
        --hexColor29: #f4f4f4;
        --hexColor30: #FF000033;
        --hexColor31: #f6f6f6;
        --hexColor32: #ffe9e8;
        --hexColor33: #FF000080;
        --hexColor34: #FF5C0080;
        --hexColor35: #F9B30080;
        --hexColor36: #fca130;
        --hexColor37: #f93e3e;
        --hexColor38: #00f;
        --hexColor39: #f2f2f2;
        --hexColor40: #5865F2;
        --hexColor41: #F7F7F7;
        --hexColor42: #fe7b5b;
        --hexColor43: #424A51;
        --hexColor44: #EDEDF0;

        --primary: #126BFF;
        --quickStartTheme:#6200B0;
        --secondary: #434761;
        --secondaryBtn: #4900AF;
        --errorBtn: #FF5353;
        --toggleBtn: #ECEDF2;
        --error: #FF5252;
        --info: #2196F3;
        --success: #4CAF50;
        --warning: #FFC107;
        --base: #2d2434;
        --darken1: #63698F;
        --lighten2: #DADAE1;
        --lighten1: #D0D2E1;
        --anchor: #085ce7;
        --redMetric: #f44336;
        --greenMetric: #00bfa5;
        --white: #ffffff;
        --black: #000000;
        --lightGrey: #7e7e97 ;
        --askUs: #4A154B ;
        --backgroundColor1: #D500F9;
        --backgroundColor2: #2E006D;
        --errorBackgroundColor: #FAFBFB;
        --borderColor: #e4e3e5;
        --colTableBackground:#EDECF0;
        --white2: #fcfcfd;
        --teamColor: #304ffe;
        --teamColor2: #2D243480;
        --transparent: rgba(0,0,0,0.0);
        --gptColor: rgb(16, 163, 127);
        --gptBackground: rgb(247, 247, 248);

        --rgbaColor1: rgba(246, 190, 79);
        --rgbaColor2: rgba(33, 150, 243);
        --rgbaColor3: rgba(27,31,36,.15);
        --rgbaColor4: rgba(255, 255, 255, 0.4);
        --rgbaColor5: rgba(45, 36, 52, 0.15);
        --rgbaColor6: rgba(239, 239, 239, 0.5);
        --rgbaColor7: rgba(98, 0, 234, 1);
        --rgbaColor8: rgba(246, 190, 79, 0.2);
        --rgbaColor9: rgba(243, 107, 107, 0.2);
        --rgbaColor10: rgba(0, 191, 165, 0.2);
        --rgbaColor11: rgba(0, 0, 0, 0.08);
        --rgbaColor12: rgba(16, 24, 40, 0.05);
        --rgbaColor13: rgba(98, 0, 234, 0.2);
        --rgbaColor14: rgba(45, 44, 87, 0.08);
        --rgbaColor15: rgba(255, 0, 0, 0.5);
        --rgbaColor16: rgba(16, 24, 40, 0.05);
        --rgbaColor17: rgba(243, 107, 107);
        --rgbaColor18: rgba(0, 0, 0, 0.25);
        --rgbaColor19: rgba(255, 255, 255, 0.5);
        --rgbaColor20: rgb(45 44 87 / 10%);
        --rgbaColor21: rgba(255, 255, 255, 0.3);
    }
</style>