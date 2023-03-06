<template>
    <div class="pa-2">
        <inventory/>
    </div>
</template>

<script>

    import catalog from '@/apps/chrome_plugin/util/catalog'
    import Inventory from '@/apps/dashboard/views/observe/inventory/Inventory'

    export default {
        name: 'App',
        components: {
            Inventory
        },
        data () {
            return {
                endpoints: {}
            }
        },
        methods : {
            getOrCreatePath (dict, keys) {
                let curr = dict
                keys.forEach(k => {
                    if (k.indexOf(".") === 0) {
                        k = k.substr(1)
                    }
                    let v = curr[k]
                    if (!v) {
                        v = {}
                        curr[k] = v
                    }

                    curr = v
                })

                return curr
            },

            addInfoForSingleObject(obj, reqOrResp, headerOrPayload, url, method) {
                let _getOrCreatePath = this.getOrCreatePath
                let _endpoints = this.endpoints
                Object.entries(obj).forEach(x => {
                    Object.entries(x[1]).forEach(y => {
                        let v = _getOrCreatePath(_endpoints, [url, method, reqOrResp, headerOrPayload, x[0], y[0]])

                        if (!v.values) {
                            v.values = new Set()
                        }

                        y[1].values.forEach(z => v.values.add(z))
                    })
                })
            },

            aggregateInfo(method, url, reqHeaders, reqBody, respHeaders, respBody) {
                this.addInfoForSingleObject(reqHeaders, "Request", "Headers", url, method)
                this.addInfoForSingleObject(reqBody, "Request", "Payload", url, method)
                this.addInfoForSingleObject(respHeaders, "Response", "Headers", url, method)
                this.addInfoForSingleObject(respBody, "Response", "Payload", url, method)
            },

            createParamsList() {
                let params =  []
                let now = parseInt(+(new Date())/1000)
                Object.entries(this.endpoints).map(urlAndMethods => 
                    Object.entries(urlAndMethods[1]).map(methodAndReqResp => 
                        Object.entries(methodAndReqResp[1]).map(reqRespAndHeaderPayload => 
                            Object.entries(reqRespAndHeaderPayload[1]).map(headerPayloadAndParamNames => 
                                Object.entries(headerPayloadAndParamNames[1]).map(paramNamesAndType => 
                                    Object.entries(paramNamesAndType[1]).map(typeAndValues => 
                                        params.push({
                                            examples: typeAndValues[1],
                                            isHeader: headerPayloadAndParamNames[0] === "Headers",
                                            method: methodAndReqResp[0],
                                            param: paramNamesAndType[0],
                                            responseCode: reqRespAndHeaderPayload[0] === "Response" ? 200 : -1,
                                            subType: headerPayloadAndParamNames[0] === "Headers" ? catalog.toSuperType(typeAndValues[0]) : typeAndValues[0],
                                            timestamp: now,
                                            url: urlAndMethods[0],
                                            userIds: []
                                        })
                                    )
                                )
                            )
                        )
                    )
                )                
                
                let ret = {
                    name: "API calls",
                    endpoints: params
                }
                return ret
                
            }
        },
        created () {
            let _aggregateInfo = this.aggregateInfo
            let _endpoints = this.endpoints
            let _store = this.$store
            let _createParamsList = this.createParamsList
            // When a network request has finished this function will be called.
            chrome.devtools.network.onRequestFinished.addListener(request => {
                const response = request.response;
                // Find the Content-Type header.

                if (response && response.content.size > 0) {

                    request.getContent((body) => {
                        if (request.request && request.request.url) {
                            try { 
                                let reqHeaders = Object.fromEntries(request.request.headers.map(x => [x.name, x.value]))
                                let reqBody = {}

                                if (request.request.postData) {
                                    if (request.request.postData.mimeType.indexOf("application/json") === 0) {
                                        reqBody = JSON.parse(request.request.postData.text)
                                    } else if (request.request.postData.mimeType.indexOf("application/x-www-form-urlencoded") === 0) {
                                        reqBody = Object.fromEntries(request.request.postData.params.map(x => [x.name, x.value]))
                                    } else {
                                        return 
                                    }
                                }
                                

                                if (request.request.queryString && request.request.queryString.length > 0) {
                                    reqBody = {...reqBody, ...Object.fromEntries(request.request.queryString.map(x => [x.name, x.value]))}
                                }  


                                let respHeaders = Object.fromEntries(response.headers.map(x => [x.name, x.value]))

                                let url = request.request.url

                                if (url.indexOf("?") > -1) {
                                    url = url.substr(0, url.indexOf("?"))
                                }

                                let respBody = JSON.parse(body)

                                let currCount = Object.entries(_endpoints).length

                                _aggregateInfo(
                                    request.request.method, 
                                    url, 
                                    catalog.tryParamsOrJson(reqHeaders), 
                                    catalog.tryParamsOrJson(reqBody), 
                                    catalog.tryParamsOrJson(respHeaders), 
                                    catalog.tryParamsOrJson(respBody)
                                )

                                if (Object.entries(_endpoints).length> currCount) {
                                    _store.commit('inventory/SAVE_API_COLLECTION', {data: _createParamsList(), apiCollectionId: 0})
                                }
                            } catch(e) {

                            }
                        }
                    });
                }

            });
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

</style>

<style lang="sass">
.v-application a
    color: unset !important
    text-decoration: auto

.active-tab
    color: var(--base)
    font-weight: 500

.tabs-container
    padding-left: 16px

.right-pane-tab
    text-transform: unset
    letter-spacing: normal
    padding: 0

.brdb
  border-bottom: 1px solid rgba(71, 70, 106, 0.2) !important

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
    color: #47466A3D
    font-size: 13px


.d-flex
    display: flex    

.d-grid-2
    display: grid    
    grid-template-columns: 1fr 1fr
    column-gap: 40px

.pa-2
    padding: 8px

.v-breadcrumbs
    padding: 0px !important    
</style>