import axios from 'axios'
import Store from "../apps/dashboard/store";
import func from "./func"
import { history } from './history';

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
  let message = "OOPS! Something went wrong"
  if (actionErrors !== null && actionErrors !== undefined && actionErrors.length > 0) {
    message = actionErrors[0]
  }

  switch (status) {
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
      const originalRequest = error.config;
      if (originalRequest._retry) {
        console.log("access token error");
      }
      originalRequest._retry = true
      await service({
        url: '/dashboard/accessToken',
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
  //   config.headers["access-token"] = store.getters["auth/getAccessToken"]
  config.headers["access-token"] = Store.getState().accessToken
  // config.headers["access-token"] = localStorage.getItem("access_token")


  if (window.ACTIVE_ACCOUNT) {
    config.headers['account'] = window.ACTIVE_ACCOUNT
  }

  return config
}, err)

// response interceptor
// For every response that is sent to the vue app, look for access token in header and set it if not null
service.interceptors.response.use((response) => {
  if (response.headers["access-token"] != null) {
    // store.commit('auth/SET_ACCESS_TOKEN',response.headers["access-token"])
    // localStorage.setItem("access_token", response.headers["access-token"])
    Store.getState().storeAccessToken(response.headers["access-token"])

  }

  if (['put', 'post', 'delete', 'patch'].includes(response.method) && response.data.meta) {
    // window._AKTO.$emit('SHOW_SNACKBAR', {
    //   text: response.data.meta.message,
    //   color: 'success'
    // })
  }
  if (response.data.error !== undefined) {
    // window._AKTO.$emit('API_FAILED', response.data.error)
  } else {
    if (window.mixpanel && window.mixpanel.track && response.config && response.config.url) {
      raiseMixpanelEvent(response.config.url);
    }
  }

  return response.data
}, err)

async function raiseMixpanelEvent(api) {
  if (api && api.indexOf("/api/fetchActiveLoaders") == -1) {
    window.mixpanel.track(api)
  }
}

export default service
