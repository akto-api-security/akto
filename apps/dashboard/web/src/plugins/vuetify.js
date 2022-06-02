import Vue from 'vue'
import Vuetify from 'vuetify'
import 'vuetify/dist/vuetify.min.css'
import { library } from '@fortawesome/fontawesome-svg-core' // Core SVG
import { FontAwesomeIcon } from '@fortawesome/vue-fontawesome' // Integration
import CurlyBraces from "../apps/dashboard/shared/icons/CurlyBraces"
import Burpsuite from "../apps/dashboard/shared/icons/Burpsuite"
import Slack from "../apps/dashboard/shared/icons/Slack"
import Postman from "../apps/dashboard/shared/icons/Postman"
import Restapi from "../apps/dashboard/shared/icons/Restapi"

Vue.component('font-awesome-icon', FontAwesomeIcon)

import {
  faClipboardList,
  faLongArrowAltDown,
  faLongArrowAltUp,
  faUnlockAlt,
  faStethoscope,
  faBars,
  faUser,
  faPlus,
  faTimes,
  faEnvelope,
  faPhoneAlt,
  faLock,
  faLockOpen,  
  faAngleUp,
  faAngleDown,
  faAngleRight,
  faAngleLeft,
  faComments,
  faEye,
  faEllipsisV,
  faStar,
  faCalendar,
  faCog,
  faShare,
  faEdit,
  faCheck,
  faTrash,
  faSearch,
  faBell,
  faChartBar,
  faFileAlt,
  faHandPointRight,
  faChartArea,
  faExclamationTriangle,
  faInfoCircle,
  faDownload,
  faFileCsv,
  faCircle,
  faThLarge,
  faStopwatch,
  faCheckCircle,
  faEyeSlash,
  faArrowLeft,
  faUsers,
  faChartLine,
  faHome,
  faBullseye,
  faBullhorn,
  faCheckDouble,
  faSquare,
  faBolt,
  faDatabase,
  faAngleDoubleRight,
  faAngleDoubleLeft,
  faAngleDoubleDown,
  faAngleDoubleUp,
  faSyncAlt,
  faExchangeAlt,
  faUserLock,
  faExclamation,
  faCreditCard,
  faAddressCard,
  faChalkboard,
  faFilter,
  faKey,
  faUpload,
  faRedo,
  faGlobe,
  faUserSecret,
  faCopy,
  faPlay,
  faStop,
  faPen
} from '@fortawesome/free-solid-svg-icons'

import {
  faComments as farComments,
  faCalendar as farCalendar,
  faHandPointRight as farHandPointRight,
  faBuilding as farBuilding,
  faStar as farStar,
  faSquare as farSquare,
  faCheckSquare as farCheckSquare
} from '@fortawesome/free-regular-svg-icons'

import {
  faSalesforce as fabSalesforce,
  faHubspot as fabHubspot
} from '@fortawesome/free-brands-svg-icons'

const faIcons = [
  faBars,faUser,faPlus,faTimes,faEnvelope,faLock,faLockOpen,faAngleUp,faAngleRight,faAngleLeft,faComments,faEye,faEllipsisV,faStar,faCalendar,
  faCog,faShare,faEdit,faCheck,faTrash,faSearch,faBell,faChartBar,faFileAlt,faHandPointRight,faChartArea,faExclamationTriangle,
  faInfoCircle,faDownload,faFileCsv,faCircle,faThLarge,faStopwatch,faCheckCircle,faEyeSlash,faArrowLeft,faUsers,faChartLine,
  faHome,faBullseye,faBullhorn,faCheckDouble,faSquare,faBolt,faDatabase,faAngleDoubleRight, faAngleDoubleLeft,faClipboardList,
  faUnlockAlt,faStethoscope,faLongArrowAltDown,faLongArrowAltUp,faSyncAlt, faExchangeAlt, faUserLock, faExclamation,faPhoneAlt,
  faCreditCard,faAddressCard,faChalkboard,faFilter,faKey,faAngleDoubleDown,faAngleDoubleUp,faUpload,faRedo,faGlobe,faAngleDown,
  faUserSecret,faCopy,faPlay,faStop,faPen,
  
  farComments,farCalendar, farHandPointRight, farBuilding, farStar,farSquare, farCheckSquare,

  fabSalesforce,fabHubspot
]

library.add(faIcons)

Vue.use(Vuetify)

const ctTheme = {
  primary: '#126BFF',
  secondary: '#434761',
  secondaryBtn: '#4900AF',
  errorBtn: '#FF5353',
  negativeBtn: '#63698f',
  toggleBtn: '#ECEDF2',
  accent: '#2d2434',
  error: '#FF5252',
  info: '#2196F3',
  success: '#4CAF50',
  warning: '#FFC107',
  base: '#2d2434',
  darken1: '#63698F',
  darken2: '#434761',
  lighten1: '#D0D2E1',
  anchor: '#085ce7',
  redMetric: '#f44336',
  greenMetric: '#00bfa5',
  themeColor: '#6200EA'
}

const faIconsOpts = {}

faIconsOpts.curlyBraces = {
  component: CurlyBraces
}

faIconsOpts.burpsuite = {
  component: Burpsuite
}

faIconsOpts.postman = {
  component: Postman
}

faIconsOpts.slack = {
  component: Slack
}

faIconsOpts.restapi = {
  component: Restapi
}

faIcons.forEach(x => faIconsOpts[x.prefix+"_"+x.iconName] = {
  component: FontAwesomeIcon,
  props: {
    icon: [x.prefix, x.iconName]
  }
})

const opts = {
  theme: {
    themes: {
      light: ctTheme,
      dark: ctTheme
    },
	options: { customProperties: true },
  },
  icons: {
    values: faIconsOpts
  }
}

export default new Vuetify(opts)

