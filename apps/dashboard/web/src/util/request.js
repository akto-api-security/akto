import axios from 'axios'
import store from "@/apps/main/store/module";
import router from "@/apps/main/router";

// create axios
const service = axios.create({
  baseURL: window.location.origin, // api base_url
  timeout: 60000, // timeout,
  headers: { 'Access-Control-Allow-Origin': '*' }
})

const err = async (error) => {
  const { status, data } = error.response
  const { errors } = data
  const { actionErrors } = data
  const standardMessage = "OOPS! Something went wrong"
  let message = standardMessage
  if (actionErrors !== null && actionErrors !== undefined && actionErrors.length > 0) {
    message = actionErrors[0]
  }
  switch (status) {
    case 400:
      window._AKTO.$emit('SHOW_SNACKBAR', {
        show: true,
        text: 'Bad Request ' + data.message,
        color: 'red'
      })
      break

    case 422:
      window._AKTO.$emit('SHOW_SNACKBAR', {
        show: true,
        text: message,
        color: 'red'
      })

      break

    case 401:
      if (router.currentRoute.path !== "/login") {
        router.push('/').then(() => {
              window._AKTO.$emit('SHOW_SNACKBAR', {
                show: true,
                text: "Please login again",
                color: 'red'
              });
            }
        )
      }
      window._AKTO.$emit('SHOW_SNACKBAR', {
        show: true,
        text: "Please login again",
        color: 'red'
      })
      break

    case 423:
      window._AKTO.$emit('SHOW_SNACKBAR', {
        show: true,
        text: "Confirm your email first",
        color: 'red'
      })
      break

    case 429:
      window._AKTO.$emit('SHOW_SNACKBAR', {
        show: true,
        text: "Too many requests!! Please try after 1 hour",
        color: 'red'
      })
      break

    case 403:

      if (message.localeCompare(standardMessage) != 0) {
        window._AKTO.$emit('SHOW_SNACKBAR', {
          show: true,
          text: message,
          color: 'red'
        })
        if (window?.mixpanel?.track && error?.config?.url) {
          window.mixpanel.track("UNAUTHORIZED_API_BLOCKED", {
            "api": error.config.url
          })
        }
        break;
      }

      const originalRequest = error.config;
      if (originalRequest._retry) {
        await window._AKTO.$emit('SHOW_SNACKBAR', {
          show: true,
          text: "Something went wrong",
          color: 'red'
        })
        break
      }
      originalRequest._retry = true
      await service({
        url: '/dashboard/accessToken',
        method: 'get',
      })
      return service(originalRequest)

    case 500:
      window._AKTO.$emit('SERVER_ERROR')
      break

    default:
      break
  }

  return Promise.reject(error)
}

// request interceptor
// For every request that is sent from the vue app, automatically attach the accessToken from the store
service.interceptors.request.use((config) => {
  config.headers['Access-Control-Allow-Origin'] = '*'
  config.headers['Content-Type'] = 'application/json'
  config.headers["access-token"] = store.getters["auth/getAccessToken"]

  if (window.ACTIVE_ACCOUNT) {
    config.headers['account'] = window.ACTIVE_ACCOUNT
  }

  return config
}, err)

// response interceptor
// For every response that is sent to the vue app, look for access token in header and set it if not null
service.interceptors.response.use((response) => {
  if (response.headers["access-token"] != null) {
    store.commit('auth/SET_ACCESS_TOKEN',response.headers["access-token"])
  }

  if (['put', 'post', 'delete', 'patch'].includes(response.method) && response.data.meta) {
    window._AKTO.$emit('SHOW_SNACKBAR', {
      text: response.data.meta.message,
      color: 'success'
    })
  }
  if (response.data.error !== undefined) {
    window._AKTO.$emit('API_FAILED', response.data.error)
  } else {
    if (window.mixpanel && window.mixpanel.track && response.config && response.config.url){
        raiseMixpanelEvent(response.config.url);
    }
  }

  return response.data
}, err)

const black_list_apis = ['dashboard/accesstoken', 'api/fetchBurpPluginInfo', 'api/fetchActiveLoaders', 'api/fetchAllSubCategories']
async function raiseMixpanelEvent(api){
  if (api && !black_list_apis.some(black_list_api => api.includes(black_list_api))) {
    window.mixpanel.track(api);
  }
}

export default service
