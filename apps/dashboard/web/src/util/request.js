import axios from 'axios'
import store from "@/apps/main/store/module";
import router from "@/apps/main/router";

// create axios
const service = axios.create({
  baseURL: window.location.origin, // api base_url
  timeout: 50000, // timeout,
  headers: { 'Access-Control-Allow-Origin': '*' }
})

const err = async (error) => {
  const { status, data } = error.response
  const { errors } = data
  let message = []
  for (let field in errors) {
    message.push(errors[field])
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
        router.push('/login').then(() => {
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

    case 403:
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
        url: '/auth/accessToken',
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
  }

  return response.data
}, err)

export default service
