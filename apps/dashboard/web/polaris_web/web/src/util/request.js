import axios from 'axios'
import PersistStore from '../apps/main/PersistStore';
import func from "./func"
import { history } from './history';

const accessTokenUrl = "/dashboard/accessToken"

// create axios
const service = axios.create({
  baseURL: window.location.origin, // api base_url
  timeout: 60000, // timeout,
  headers: { 'Access-Control-Allow-Origin': '*' }
})

const err = async (error) => {
  let status
  let data
  
  if(error.response) {
    status = error.response.status
    data = error.response.data
  } else {
    status = -1
    data = {}
  }

  const { errors } = data
  const { actionErrors } = data
  const standardMessage = "OOPS! Something went wrong"
  let message = standardMessage
  if (actionErrors !== null && actionErrors !== undefined && actionErrors.length > 0) {
    message = actionErrors[0]
  }

  switch (status) {
    case -1:
      func.setToast(true, true, "Connection error. Please try again later.")
      break;
    case 400:
      func.setToast(true, true, 'Bad Request ' + data.message);
      break;
    case 422:
      func.setToast(true, true, message);
      break;
    case 401:
      if (history.location.pathname !== "/login") {
        history.navigate("/login")
      }
      func.setToast(true, true, "Please login again");
      break
    case 423:
      func.setToast(true, true, "Please confirm your email first");
      break
    case 429:
      func.setToast(true, true, "Too many requests!! Please try after 1 hour");
      break
    case 403:

      if (message.localeCompare(standardMessage) != 0) {
        func.setToast(true, true, message);
        if (window?.mixpanel?.track && error?.config?.url) {
          window.mixpanel.track("UNAUTHORIZED_API_BLOCKED", {
            "api": error.config.url
          })
        }
        break;
      }

      const originalRequest = error.config;

      if (originalRequest.url === accessTokenUrl) {
        // if done multiple times, then redirect to login.
        func.setToast(true, true, "Session expired. Redirecting you to login page in some time.")
        setTimeout(()=>{
          window.location.pathname = "/login"
        },1500)
        break
      }

      await service({
        url: accessTokenUrl,
        method: 'get',
      })

      return service(originalRequest)
    case 500:
      func.setToast(true, true, "Server Error");
      break

    default:
      break;
  }
  return Promise.reject(error)
}

// request interceptor
// For every request that is sent from the vue app, automatically attach the accessToken from the store
service.interceptors.request.use((config) => {
  config.headers['Access-Control-Allow-Origin'] = '*'
  config.headers['Content-Type'] = 'application/json'
  config.headers["access-token"] = PersistStore.getState().accessToken


  if (window.ACTIVE_ACCOUNT) {
    config.headers['account'] = window.ACTIVE_ACCOUNT
  }

  return config
}, err)

// response interceptor
// For every response that is sent to the vue app, look for access token in header and set it if not null
service.interceptors.response.use((response) => {
  if (response.headers["access-token"] != null) {
    PersistStore.getState().storeAccessToken(response.headers["access-token"])
  }

  if (['put', 'post', 'delete', 'patch'].includes(response.method) && response.data.meta) {
    func.setToast(true, false, response.data.meta.message )
  }
  if (response?.data?.error !== null && response?.data?.error !== undefined) {
    func.setToast(true, true, response.data.error )
  } else {
    if ( window?.mixpanel?.track && response?.config?.url) {
      raiseMixpanelEvent(response.config.url);
    }
  }

  return response.data
}, err)

const black_list_apis = ['dashboard/accessToken', 'api/fetchBurpPluginInfo', 'api/fetchActiveLoaders', 'api/fetchAllSubCategories', 'api/fetchVulnerableRequests']
async function raiseMixpanelEvent(api) {
  if (window?.Intercom) {
    if (api?.startsWith("/api/ingestPostman")) {
        window.Intercom("trackEvent", "created-api-collection", {"type": "Postman"})
    }

    if (api?.startsWith("/api/importSwaggerLogs")) {
        window.Intercom("trackEvent", "created-api-collection", {"type": "Swagger"})
    }

    if (api?.startsWith("/api/uploadHar")) {
        window.Intercom("trackEvent", "created-api-collection", {"type": "Har"})
    }

    if (api?.startsWith("/api/startTest")) {
        window.Intercom("trackEvent", "started-test")
    }

    if (api?.startsWith("/api/runTestForGivenTemplate")) {
        window.Intercom("trackEvent", "tested-editor")
    }

    if (api?.startsWith("/api/skipOnboarding") || api?.startsWith("/api/fetchTestSuites")) {
        window.Intercom("trackEvent", "onboarding-started")
    }
  }
  if (api && !black_list_apis.some(black_list_api => api.includes(black_list_api))) {
    window.mixpanel.track(api)
  }
}

export default service
